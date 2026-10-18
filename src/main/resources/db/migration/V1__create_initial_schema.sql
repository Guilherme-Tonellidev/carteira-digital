-- Usuários
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- Carteiras (uma por usuário). "version" é usada no lock otimista (@Version).
CREATE TABLE wallets (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL REFERENCES users (id),
    balance    NUMERIC(19,2) NOT NULL DEFAULT 0,
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance >= 0)
);

-- Livro-razão: cada movimentação vira uma linha e nunca é alterada.
CREATE TABLE transactions (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    type             VARCHAR(20)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    source_wallet_id UUID          REFERENCES wallets (id),
    target_wallet_id UUID          REFERENCES wallets (id),
    amount           NUMERIC(19,2) NOT NULL,
    idempotency_key  VARCHAR(100),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactions_type   CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_transactions_status CHECK (status IN ('COMPLETED', 'FAILED')),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_wallets CHECK (
        (type = 'DEPOSIT'    AND source_wallet_id IS NULL     AND target_wallet_id IS NOT NULL) OR
        (type = 'WITHDRAWAL' AND source_wallet_id IS NOT NULL AND target_wallet_id IS NULL)     OR
        (type = 'TRANSFER'   AND source_wallet_id IS NOT NULL AND target_wallet_id IS NOT NULL
                             AND source_wallet_id <> target_wallet_id)
    )
);

-- Idempotência: a mesma chave não pode ser usada duas vezes pela mesma carteira.
CREATE UNIQUE INDEX uk_transactions_idempotency
    ON transactions (COALESCE(source_wallet_id, target_wallet_id), idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Extrato: busca por carteira ordenada pela data mais recente.
CREATE INDEX idx_transactions_source_created ON transactions (source_wallet_id, created_at DESC);
CREATE INDEX idx_transactions_target_created ON transactions (target_wallet_id, created_at DESC);

-- Imutabilidade: o banco impede UPDATE e DELETE no histórico.
CREATE FUNCTION prevent_transaction_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A tabela transactions é imutável (operação % bloqueada)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_immutable
    BEFORE UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION prevent_transaction_changes();
