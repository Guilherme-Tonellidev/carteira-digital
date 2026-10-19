import { useCallback, useEffect, useState } from 'react';
import { api, errorMessage, type WalletInfo } from '../api';
import { formatMoney } from '../format';
import { OperationPanel } from './OperationPanel';
import { StatementPanel } from './StatementPanel';

interface Props {
  onLogout: () => void;
}

export function Dashboard({ onLogout }: Props) {
  const [wallet, setWallet] = useState<WalletInfo | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [copied, setCopied] = useState(false);

  const loadWallet = useCallback(async () => {
    try {
      setWallet(await api.myWallet());
      setLoadError(null);
    } catch (error) {
      setLoadError(errorMessage(error));
    }
  }, []);

  useEffect(() => {
    void loadWallet();
  }, [loadWallet]);

  // Depois de qualquer operação: atualiza o saldo e recarrega o extrato.
  function handleOperationDone() {
    void loadWallet();
    setRefreshKey((key) => key + 1);
  }

  async function copyWalletId() {
    if (!wallet) return;
    try {
      await navigator.clipboard.writeText(wallet.id);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // sem permissão para a área de transferência: o ID continua visível para copiar à mão
    }
  }

  return (
    <div className="container">
      <header className="topbar">
        <h1>Carteira Digital</h1>
        <button type="button" className="ghost" onClick={onLogout}>
          Sair
        </button>
      </header>

      {loadError && (
        <p className="alert error" role="alert">
          {loadError}{' '}
          <button type="button" className="link" onClick={() => void loadWallet()}>
            Tentar de novo
          </button>
        </p>
      )}

      {wallet && (
        <>
          <section className="card balance">
            <span className="muted">Saldo disponível</span>
            <strong className="balance-value">{formatMoney(wallet.balance)}</strong>
            <div className="wallet-id">
              <span className="muted">ID da carteira (use para receber transferências):</span>
              <code>{wallet.id}</code>
              <button type="button" className="ghost small" onClick={() => void copyWalletId()}>
                {copied ? 'Copiado!' : 'Copiar'}
              </button>
            </div>
          </section>

          <OperationPanel walletId={wallet.id} onDone={handleOperationDone} />
          <StatementPanel walletId={wallet.id} refreshKey={refreshKey} />
        </>
      )}

      {!wallet && !loadError && <p className="muted">Carregando...</p>}
    </div>
  );
}
