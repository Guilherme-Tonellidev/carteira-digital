# Carteira Digital

API REST de carteira digital: o usuário cria uma conta, deposita, saca e transfere dinheiro para outros usuários, e consulta o extrato. O foco do projeto é **consistência financeira**: saldo nunca negativo, histórico imutável e transferências seguras mesmo com requisições simultâneas.

**Status:** em desenvolvimento.

## Stack

Java 21 · Spring Boot · Spring Data JPA · PostgreSQL · Flyway · Docker Compose

## Como rodar

Pré-requisitos: JDK 21+, Maven e Docker.

```bash
# 1. subir o PostgreSQL (porta 5433 no host)
docker compose up -d

# 2. rodar a aplicação (o Flyway cria as tabelas na primeira execução)
mvn spring-boot:run

# 3. conferir se subiu
curl http://localhost:8080/actuator/health
```

A resposta esperada é `{"status":"UP"}`. A configuração do banco pode ser trocada pelas variáveis `DB_URL`, `DB_USER` e `DB_PASSWORD`.

## Modelo de dados

```
users 1 ──── 1 wallets 1 ──── N transactions
                 (saldo)        (origem e/ou destino)
```

| Tabela | Observação |
|---|---|
| `users` | email único; senha guardada apenas como hash |
| `wallets` | uma por usuário; `balance NUMERIC(19,2)` com `CHECK (balance >= 0)`; coluna `version` para lock otimista |
| `transactions` | tipos `DEPOSIT`, `WITHDRAWAL` e `TRANSFER`; imutável (trigger bloqueia UPDATE/DELETE) |

## Decisões de projeto

- **Dinheiro em `BigDecimal` / `NUMERIC(19,2)`**, nunca `double`.
- **Regras também no banco**: saldo não negativo, valor positivo e coerência origem/destino por tipo ficam em `CHECK`, como segunda linha de defesa além do código.
- **Histórico imutável**: correções são feitas com novas transações, nunca editando as antigas.
- **Idempotência**: índice único por (carteira, `idempotency_key`) para que um reenvio não duplique a operação.
- **Concorrência**: toda operação trava a(s) carteira(s) com `SELECT ... FOR UPDATE` (lock pessimista) antes de mexer no saldo, então requisições simultâneas na mesma carteira são serializadas. Na transferência as duas carteiras são travadas sempre em ordem de id, o que evita deadlock em transferências cruzadas (A para B e B para A ao mesmo tempo). O `@Version` fica como rede de segurança.
- **Teste de concorrência manual**: com a aplicação rodando, `powershell -ExecutionPolicy Bypass -File .\scripts\teste-concorrencia.ps1` dispara requisições simultâneas e confere saldos e códigos HTTP.
- **Schema versionado com Flyway**; o Hibernate roda em `ddl-auto: validate`.

## API

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/users` | Cadastra o usuário e cria a carteira dele |
| `GET` | `/wallets/{id}` | Consulta o saldo |
| `POST` | `/wallets/{id}/deposit` | Deposita (`{"amount": 100.50}`) |
| `POST` | `/wallets/{id}/withdraw` | Saca; `422` se o saldo for insuficiente |
| `POST` | `/wallets/{id}/transfer` | Transfere para outra carteira (`{"targetWalletId": "...", "amount": 20}`) |

Depósito, saque e transferência aceitam o cabeçalho opcional `Idempotency-Key`: reenviar a mesma operação com a mesma chave devolve a transação original, sem mexer no saldo outra vez. Erros seguem o formato `ProblemDetail` (RFC 7807). O arquivo `requests.http` tem exemplos prontos para a extensão REST Client do VS Code.

## Roadmap

- [x] Estrutura do projeto, Docker Compose e migration inicial
- [x] Depósito e saque
- [x] Transferência com controle de concorrência
- [ ] Autenticação com JWT
- [ ] Extrato paginado
- [ ] Testes de integração e teste de concorrência
- [ ] Documentação da API (Swagger)
- [ ] Front-end em React
