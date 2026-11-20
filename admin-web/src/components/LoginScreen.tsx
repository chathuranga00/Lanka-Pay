import React, { useState } from 'react';
import { ShieldCheck, Lock, Phone } from 'lucide-react';
import { adminApi } from '../services/adminApi';
import type { AdminAuthSession } from '../types/admin';

interface LoginScreenProps {
  onLoginSuccess: (session: AdminAuthSession) => void;
  initialMessage?: string | null;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ onLoginSuccess, initialMessage }) => {
  const [phone, setPhone] = useState('+94770000001');
  const [pin, setPin] = useState('123456');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(initialMessage || null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const session = await adminApi.login(phone, pin);
      onLoginSuccess(session);
    } catch (err: any) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px',
      background: 'radial-gradient(circle at top, #132A1F 0%, #0D1612 100%)',
    }}>
      <div style={{
        width: '100%',
        maxWidth: '420px',
        backgroundColor: 'var(--surface-dark)',
        borderRadius: '24px',
        border: '1px solid var(--border-dark)',
        padding: '36px',
        boxShadow: '0 20px 40px rgba(0,0,0,0.4)',
      }}>
        <div style={{ textAlign: 'center', marginBottom: '28px' }}>
          <div style={{
            display: 'inline-flex',
            padding: '16px',
            backgroundColor: 'rgba(10, 92, 54, 0.2)',
            borderRadius: '20px',
            border: '1px solid var(--primary-green-light)',
            marginBottom: '16px',
          }}>
            <ShieldCheck size={36} color="var(--accent-gold)" />
          </div>
          <h1 style={{ fontSize: '22px', fontWeight: 800, color: 'var(--text-primary)' }}>
            LankaPay Operations
          </h1>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '4px' }}>
            Administrative Control Center • Role-Protected
          </p>
        </div>

        {error && (
          <div style={{
            padding: '12px 16px',
            backgroundColor: 'rgba(220, 38, 38, 0.15)',
            border: '1px solid var(--error)',
            borderRadius: '12px',
            color: '#FCA5A5',
            fontSize: '13px',
            marginBottom: '20px',
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '18px' }}>
            <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
              Administrator Phone
            </label>
            <div style={{ position: 'relative' }}>
              <Phone size={18} color="var(--text-muted)" style={{ position: 'absolute', left: '14px', top: '14px' }} />
              <input
                type="text"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+94770000001"
                required
                style={{
                  width: '100%',
                  padding: '12px 14px 12px 42px',
                  backgroundColor: 'var(--card-dark)',
                  border: '1px solid var(--border-dark)',
                  borderRadius: '12px',
                  color: 'var(--text-primary)',
                  fontSize: '14px',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          <div style={{ marginBottom: '24px' }}>
            <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
              Security PIN
            </label>
            <div style={{ position: 'relative' }}>
              <Lock size={18} color="var(--text-muted)" style={{ position: 'absolute', left: '14px', top: '14px' }} />
              <input
                type="password"
                maxLength={6}
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                placeholder="••••••"
                required
                style={{
                  width: '100%',
                  padding: '12px 14px 12px 42px',
                  backgroundColor: 'var(--card-dark)',
                  border: '1px solid var(--border-dark)',
                  borderRadius: '12px',
                  color: 'var(--text-primary)',
                  fontSize: '14px',
                  outline: 'none',
                  letterSpacing: '3px',
                }}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              padding: '14px',
              backgroundColor: 'var(--primary-green)',
              color: 'white',
              fontWeight: 700,
              fontSize: '14px',
              borderRadius: '14px',
              boxShadow: '0 4px 12px rgba(10, 92, 54, 0.4)',
              opacity: loading ? 0.7 : 1,
            }}
          >
            {loading ? 'Authenticating...' : 'Sign In as Administrator'}
          </button>
        </form>
      </div>
    </div>
  );
};
