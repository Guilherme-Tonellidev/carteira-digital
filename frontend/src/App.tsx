import { useEffect, useState } from 'react';
import { getAuthToken, setAuthToken, setUnauthorizedHandler } from './api';
import { AuthScreen } from './components/AuthScreen';
import { Dashboard } from './components/Dashboard';

export default function App() {
  const [loggedIn, setLoggedIn] = useState(() => getAuthToken() !== null);
  const [notice, setNotice] = useState<string | null>(null);

  // Se a API recusar o token (expirado, por exemplo), volta para a tela de login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setAuthToken(null);
      setLoggedIn(false);
      setNotice('Sua sessão expirou. Entre novamente.');
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  function handleAuthenticated() {
    setNotice(null);
    setLoggedIn(true);
  }

  function handleLogout() {
    setAuthToken(null);
    setLoggedIn(false);
    setNotice(null);
  }

  return loggedIn ? (
    <Dashboard onLogout={handleLogout} />
  ) : (
    <AuthScreen notice={notice} onAuthenticated={handleAuthenticated} />
  );
}
