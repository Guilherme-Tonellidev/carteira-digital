import { useEffect, useState } from 'react';
import { api, errorMessage, type Page, type StatementEntry, type TransactionType } from '../api';
import { formatDateTime, formatMoney, shortId } from '../format';

const PAGE_SIZE = 10;

interface Filters {
  type: '' | TransactionType;
  from: string;
  to: string;
}

const EMPTY_FILTERS: Filters = { type: '', from: '', to: '' };

const TYPE_OPTIONS: { value: '' | TransactionType; label: string }[] = [
  { value: '', label: 'Todos os tipos' },
  { value: 'DEPOSIT', label: 'Depósitos' },
  { value: 'WITHDRAWAL', label: 'Saques' },
  { value: 'TRANSFER', label: 'Transferências' },
];

function describe(entry: StatementEntry): { title: string; detail: string } {
  switch (entry.type) {
    case 'DEPOSIT':
      return { title: 'Depósito', detail: '' };
    case 'WITHDRAWAL':
      return { title: 'Saque', detail: '' };
    case 'TRANSFER':
      return entry.direction === 'CREDIT'
        ? { title: 'Transferência recebida', detail: `De ${shortId(entry.sourceWalletId)}` }
        : { title: 'Transferência enviada', detail: `Para ${shortId(entry.targetWalletId)}` };
  }
}

interface Props {
  walletId: string;
  /** Muda a cada operação concluída, para recarregar o extrato. */
  refreshKey: number;
}

export function StatementPanel({ walletId, refreshKey }: Props) {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<StatementEntry> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api
      .statement(walletId, {
        type: filters.type || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
        page,
        size: PAGE_SIZE,
      })
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(errorMessage(err));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [walletId, refreshKey, filters, page]);

  function updateFilters(changes: Partial<Filters>) {
    setFilters((current) => ({ ...current, ...changes }));
    setPage(0);
  }

  const hasFilters = filters.type !== '' || filters.from !== '' || filters.to !== '';
  const totalPages = data?.totalPages ?? 0;

  return (
    <section className="card">
      <h2>Extrato</h2>

      <div className="filters">
        <label>
          Tipo
          <select
            value={filters.type}
            onChange={(event) => updateFilters({ type: event.target.value as '' | TransactionType })}
          >
            {TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          De
          <input
            type="date"
            value={filters.from}
            onChange={(event) => updateFilters({ from: event.target.value })}
          />
        </label>
        <label>
          Até
          <input
            type="date"
            value={filters.to}
            onChange={(event) => updateFilters({ to: event.target.value })}
          />
        </label>
        {hasFilters && (
          <button type="button" className="ghost small" onClick={() => updateFilters(EMPTY_FILTERS)}>
            Limpar filtros
          </button>
        )}
      </div>

      {error && (
        <p className="alert error" role="alert">
          {error}
        </p>
      )}

      {data && data.content.length > 0 && (
        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr>
                <th>Data</th>
                <th>Movimentação</th>
                <th className="right">Valor</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((entry) => {
                const { title, detail } = describe(entry);
                const credit = entry.direction === 'CREDIT';
                return (
                  <tr key={entry.id}>
                    <td>{formatDateTime(entry.createdAt)}</td>
                    <td>
                      <strong>{title}</strong>
                      {detail && <span className="muted block">{detail}</span>}
                    </td>
                    <td className={credit ? 'right amount credit' : 'right amount debit'}>
                      {credit ? '+ ' : '− '}
                      {formatMoney(entry.amount)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {data && data.content.length === 0 && !error && (
        <p className="muted">Nenhuma movimentação encontrada.</p>
      )}

      {data && data.totalElements > 0 && (
        <div className="pagination">
          <button
            type="button"
            className="ghost small"
            disabled={loading || page === 0}
            onClick={() => setPage((current) => current - 1)}
          >
            Anterior
          </button>
          <span className="muted">
            Página {page + 1} de {Math.max(totalPages, 1)} · {data.totalElements} movimentações
          </span>
          <button
            type="button"
            className="ghost small"
            disabled={loading || page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            Próxima
          </button>
        </div>
      )}
    </section>
  );
}
