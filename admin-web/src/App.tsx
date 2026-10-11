import { useState } from 'react';
import { adminApi } from './services/adminApi';
import type { AdminAuthSession } from './types/admin';
import { LoginScreen } from './components/LoginScreen';
import { DashboardLayout } from './components/DashboardLayout';

export function App() {
  const [session, setSession] = useState<AdminAuthSession | null>(() => adminApi.getSession());

  const handleLogout = () => {
    adminApi.clearSession();
    setSession(null);
  };

  if (!session) {
    return <LoginScreen onLoginSuccess={setSession} />;
  }

  return <DashboardLayout session={session} onLogout={handleLogout} />;
}

export default App;
