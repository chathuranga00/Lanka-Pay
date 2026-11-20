import { useState, useEffect } from 'react';
import { adminApi } from './services/adminApi';
import type { AdminAuthSession } from './types/admin';
import { LoginScreen } from './components/LoginScreen';
import { DashboardLayout } from './components/DashboardLayout';

export function App() {
  const [session, setSession] = useState<AdminAuthSession | null>(() => adminApi.getSession());
  const [sessionExpiredMsg, setSessionExpiredMsg] = useState<string | null>(null);

  useEffect(() => {
    const handleUnauthorized = () => {
      adminApi.clearSession();
      setSession(null);
      setSessionExpiredMsg('Your administrator session has expired. Please sign in again.');
    };

    window.addEventListener('lankapay:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('lankapay:unauthorized', handleUnauthorized);
  }, []);

  const handleLogout = () => {
    adminApi.clearSession();
    setSession(null);
    setSessionExpiredMsg(null);
  };

  const handleLoginSuccess = (s: AdminAuthSession) => {
    setSessionExpiredMsg(null);
    setSession(s);
  };

  if (!session) {
    return <LoginScreen onLoginSuccess={handleLoginSuccess} initialMessage={sessionExpiredMsg} />;
  }

  return <DashboardLayout session={session} onLogout={handleLogout} />;
}

export default App;
