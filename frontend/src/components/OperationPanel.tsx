import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api';
import { formatMoney, isUuid, parseAmount } from '../format';

type Kind = 'deposit' | 'withdraw' | 'transfer';

const TABS: { kind: Kind; label: string }[] = [
  { kind: 'deposit', label: 'Depositar' },
  { kind: 'withdraw', label: 'Sacar' },
  { kind: 'transfer', label: 'Transferir' },
];

const SUCCESS_MESSAGES: Record<Kind, (value: number) => string> = {
  deposit: (value) => `Depósito de ${formatMoney(value)} realizado.`,
  withdraw: (value) => `Saque de ${formatMoney(value)} realizado.`,
  transfer: (value) => `Transferência de ${formatMoney(value)} realizada.`,
};

interface Props {
  walletId: string;
  onDone: () => void;
}

interface Feedback {
  type: 'success' | 'error';
  text: string;
}

export function OperationPanel({ walletId, onDone }: Props) {
  const [kind, setKind] = useState<Kind>('deposit');
  const [amount, setAmount] = useState('');
  const [target, setTarget] = useState('');
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  function changeKind(next: Kind) {
    setKind(next);
    setFeedback(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFeedback(null);

    const value = parseAmount(amount);
    if (value === null) {
      setFeedback({
        type: 'error',
        text: 'Informe um valor maior que zero, com até 2 casas decimais (ex.: 100,50).',
      });
      return;
    }
    const targetId = target.trim();
    if (kind === 'transfer' && !isUuid(targetId)) {
      setFeedback({
        type: 'error',
        text: 'Informe o ID da carteira de destino (algo como 123e4567-e89b-12d3-a456-426614174000).',
      });
      return;
    }

    setLoading(true);
    try {
      // Uma chave nova a cada envio: se a rede falhar e o servidor repetir a requisição, a operação não duplica.
      const idempotencyKey = crypto.randomUUID();
      if (kind === 'deposit') {
        await api.deposit(walletId, value, idempotencyKey);
      } else if (kind === 'withdraw') {
        await api.withdraw(walletId, value, idempotencyKey);
      } else {
        await api.transfer(walletId, targetId, value, idempotencyKey);
        setTarget('');
      }
      setAmount('');
      setFeedback({ type: 'success', text: SUCCESS_MESSAGES[kind](value) });
      onDone();
    } catch (error) {
      setFeedback({ type: 'error', text: errorMessage(error) });
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="card">
      <div className="tabs" role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.kind}
            type="button"
            role="tab"
            aria-selected={kind === tab.kind}
            className={kind === tab.kind ? 'tab active' : 'tab'}
            onClick={() => changeKind(tab.kind)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="operation-form">
        {kind === 'transfer' && (
          <label>
            ID da carteira de destino
            <input
              type="text"
              value={target}
              onChange={(event) => setTarget(event.target.value)}
              placeholder="123e4567-e89b-12d3-a456-426614174000"
              spellCheck={false}
              required
            />
          </label>
        )}
        <label>
          Valor (R$)
          <input
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            placeholder="100,50"
            required
          />
        </label>
        <button className="primary" type="submit" disabled={loading}>
          {loading ? 'Enviando...' : TABS.find((tab) => tab.kind === kind)?.label}
        </button>
      </form>

      {feedback && (
        <p className={feedback.type === 'success' ? 'alert success' : 'alert error'} role="alert">
          {feedback.text}
        </p>
      )}
    </section>
  );
}
