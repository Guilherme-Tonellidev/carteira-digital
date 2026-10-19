import { useState, type FormEvent } from 'react';
import { api, errorMessage, setAuthToken } from '../api';

type Mode = 'login' | 'register';

interface Props {
  notice: string | null;
  onAuthenticated: () => void;
}

export function AuthScreen({ notice, onAuthenticated }: Props) {
  const [mode, setMode] = useState<Mode>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      if (mode === 'register') {
        await api.register(name.trim(), email.trim(), password);
      }
      const token = await api.login(email.trim(), password);
      setAuthToken(token.accessToken);
      onAuthenticated();
    } catch (err) {
      setError(errorMessage(err));
      setLoading(false);
    }
  }

  return (
    <main className="auth">
      <section className="card auth-card">
        <h1>Carteira Digital</h1>
        <p className="muted">Deposite, saque e transfira com segurança.</p>

        <div className="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            className={mode === 'login' ? 'tab active' : 'tab'}
            onClick={() => switchMode('login')}
          >
            Entrar
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            className={mode === 'register' ? 'tab active' : 'tab'}
            onClick={() => switchMode('register')}
          >
            Criar conta
          </button>
        </div>

        {notice && <p className="alert info">{notice}</p>}

        <form onSubmit={handleSubmit}>
          {mode === 'register' && (
            <label>
              Nome
              <input
                type="text"
                value={name}
                onChange={(event) => setName(event.target.value)}
                autoComplete="name"
                maxLength={120}
                required
              />
            </label>
          )}
          <label>
            E-mail
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          </label>
          <label>
            Senha
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              minLength={mode === 'register' ? 8 : undefined}
              required
            />
            {mode === 'register' && <span className="hint">Mínimo de 8 caracteres.</span>}
          </label>

          {error && (
            <p className="alert error" role="alert">
              {error}
            </p>
          )}

          <button className="primary" type="submit" disabled={loading}>
            {loading ? 'Aguarde...' : mode === 'login' ? 'Entrar' : 'Criar conta'}
          </button>
        </form>
      </section>
    </main>
  );
}
