import type {
  AdminUser,
  BankConfig,
  IntegrationHealth,
  AdminTransfer,
  FraudAlert,
  AuditLogEntry,
  AdminAuthSession,
} from '../types/admin';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

class AdminApiClient {
  private getHeaders(): HeadersInit {
    const session = this.getSession();
    return {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(session ? { 'Authorization': `Bearer ${session.token}` } : {}),
    };
  }

  getSession(): AdminAuthSession | null {
    const raw = localStorage.getItem('lankapay_admin_session');
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AdminAuthSession;
    } catch {
      return null;
    }
  }

  setSession(session: AdminAuthSession) {
    localStorage.setItem('lankapay_admin_session', JSON.stringify(session));
  }

  clearSession() {
    localStorage.removeItem('lankapay_admin_session');
  }

  async login(phone: string, pin: string): Promise<AdminAuthSession> {
    const res = await fetch(`${BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, pin }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Authentication failed. Check phone and PIN.');
    }
    const data = await res.json();
    const session: AdminAuthSession = {
      token: data.accessToken,
      userId: data.userId || 1,
      fullName: data.fullName || 'Operations Admin',
      role: data.role || 'ADMIN',
    };
    if (session.role !== 'ADMIN') {
      throw new Error('Access denied. Administrator privileges required.');
    }
    this.setSession(session);
    return session;
  }

  async getUsers(params?: { phone?: string; nic?: string; status?: string }): Promise<AdminUser[]> {
    const query = new URLSearchParams();
    if (params?.phone) query.set('phone', params.phone);
    if (params?.nic) query.set('nic', params.nic);
    if (params?.status) query.set('status', params.status);

    const res = await fetch(`${BASE_URL}/admin/users?${query.toString()}`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch users');
    const data = await res.json();
    return data.content || data || [];
  }

  async updateUserStatus(userId: number, status: string, reason: string): Promise<void> {
    const res = await fetch(`${BASE_URL}/admin/users/${userId}/status`, {
      method: 'PATCH',
      headers: this.getHeaders(),
      body: JSON.stringify({ status, reason }),
    });
    if (!res.ok) throw new Error('Failed to update user status');
  }

  async getBanks(): Promise<BankConfig[]> {
    const res = await fetch(`${BASE_URL}/admin/banks`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch bank configs');
    return res.json();
  }

  async updateBankConfig(bankCode: string, payload: Partial<BankConfig>): Promise<void> {
    const res = await fetch(`${BASE_URL}/admin/banks/${bankCode}`, {
      method: 'PATCH',
      headers: this.getHeaders(),
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error('Failed to update bank configuration');
  }

  async getIntegrationHealth(): Promise<IntegrationHealth> {
    const res = await fetch(`${BASE_URL}/admin/integrations/health`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch integration health');
    return res.json();
  }

  async getUncertainQueue(): Promise<AdminTransfer[]> {
    const res = await fetch(`${BASE_URL}/admin/transfers/uncertain-queue`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch uncertain queue');
    const data = await res.json();
    return data.content || data || [];
  }

  async reconcileTransfer(transferId: number): Promise<void> {
    const res = await fetch(`${BASE_URL}/admin/transfers/${transferId}/reconcile`, {
      method: 'POST',
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Reconciliation failed');
  }

  async reconcileAll(): Promise<void> {
    const res = await fetch(`${BASE_URL}/admin/transfers/reconcile-all`, {
      method: 'POST',
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Bulk reconciliation failed');
  }

  async getFraudAlerts(): Promise<FraudAlert[]> {
    const res = await fetch(`${BASE_URL}/admin/risk/fraud-alerts`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch fraud alerts');
    const data = await res.json();
    return data.content || data || [];
  }

  async updateFraudAlertStatus(alertId: number, status: string, notes?: string): Promise<void> {
    const res = await fetch(`${BASE_URL}/admin/risk/fraud-alerts/${alertId}/status`, {
      method: 'PATCH',
      headers: this.getHeaders(),
      body: JSON.stringify({ status, notes }),
    });
    if (!res.ok) throw new Error('Failed to update fraud alert status');
  }

  async getAuditLogs(params?: { eventType?: string; entityType?: string }): Promise<AuditLogEntry[]> {
    const query = new URLSearchParams();
    if (params?.eventType) query.set('eventType', params.eventType);
    if (params?.entityType) query.set('entityType', params.entityType);

    const res = await fetch(`${BASE_URL}/admin/audit-log?${query.toString()}`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch audit logs');
    const data = await res.json();
    return data.content || data || [];
  }
}

export const adminApi = new AdminApiClient();
