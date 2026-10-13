import type {
  AdminUser,
  BankConfig,
  IntegrationHealth,
  AdminTransfer,
  FraudAlert,
  FraudAlertStatus,
  AuditLogEntry,
  AdminAuthSession,
  Story,
  CreateStoryPayload,
  UpdateStoryPayload,
  SupportConversation,
  SupportConversationDetail,
  SupportMessage,
  CannedReply,
} from '../types/admin';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

class AdminApiClient {

  // ── Session ──────────────────────────────────────────────────

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
    try { return JSON.parse(raw) as AdminAuthSession; }
    catch { return null; }
  }

  setSession(session: AdminAuthSession) {
    localStorage.setItem('lankapay_admin_session', JSON.stringify(session));
  }

  clearSession() {
    localStorage.removeItem('lankapay_admin_session');
  }

  // ── Auth ─────────────────────────────────────────────────────

  async login(phone: string, pin: string): Promise<AdminAuthSession> {
    const res = await fetch(`${BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, pin }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({})) as Record<string, unknown>;
      throw new Error((err['message'] as string) || 'Authentication failed. Check phone and PIN.');
    }

    const data = await res.json() as Record<string, unknown>;

    // Decode JWT to extract role and userId
    let tokenRole = 'ADMIN';
    let tokenUserId = 0;
    try {
      const payloadBase64 = (data['accessToken'] as string).split('.')[1];
      const payload = JSON.parse(atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/'))) as Record<string, unknown>;
      if (payload['role']) tokenRole = payload['role'] as string;
      if (payload['sub']) tokenUserId = Number(payload['sub']);
    } catch { /* fallback */ }

    const session: AdminAuthSession = {
      token:    data['accessToken'] as string,
      userId:   tokenUserId,
      fullName: (data['fullName'] as string) || 'Operations Admin',
      role:     tokenRole as AdminAuthSession['role'],
    };

    if (session.role !== 'ADMIN') {
      throw new Error('Access denied. Administrator privileges required.');
    }

    this.setSession(session);
    return session;
  }

  // ── Users ─────────────────────────────────────────────────────

  async getUsers(params?: { phone?: string; nic?: string; status?: string }): Promise<AdminUser[]> {
    const query = new URLSearchParams();
    if (params?.phone)  query.set('phone', params.phone);
    if (params?.nic)    query.set('nic', params.nic);
    if (params?.status) query.set('status', params.status);

    const res = await fetch(`${BASE_URL}/admin/users?${query}`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch users');

    const data = await res.json() as Record<string, unknown>;
    const items: AdminUser[] = (Array.isArray(data)
      ? data
      : ((data['content'] as AdminUser[]) || []));

    // Normalise: backend returns userId but components may read id
    return items.map((u) => ({ ...u, id: u.id ?? u.userId }));
  }

  async updateUserStatus(userId: number, status: string, reason: string): Promise<void> {
    // Backend expects { status, reason } body — matches UpdateUserStatusRequest
    const res = await fetch(`${BASE_URL}/admin/users/${userId}/status`, {
      method: 'PATCH',
      headers: this.getHeaders(),
      body: JSON.stringify({ status, reason }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({})) as Record<string, unknown>;
      throw new Error((err['message'] as string) || 'Failed to update user status');
    }
  }

  // ── Banks ─────────────────────────────────────────────────────

  async getBanks(): Promise<BankConfig[]> {
    const res = await fetch(`${BASE_URL}/admin/banks`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch bank configs');

    const data = await res.json() as BankConfig[] | { content: BankConfig[] };
    const items: BankConfig[] = Array.isArray(data) ? data : (data as { content: BankConfig[] }).content || [];

    // Normalise field name aliases
    return items.map((b) => ({
      ...b,
      code: b.code ?? b.bankCode,
      name: b.name ?? b.displayName,
    }));
  }

  async updateBankConfig(bankCode: string, payload: Partial<BankConfig>): Promise<void> {
    const res = await fetch(`${BASE_URL}/admin/banks/${bankCode}`, {
      method: 'PATCH',
      headers: this.getHeaders(),
      body: JSON.stringify({
        displayName: payload.displayName ?? payload.name,
        active: payload.active,
      }),
    });
    if (!res.ok) throw new Error('Failed to update bank configuration');
  }

  // ── Integration Health ────────────────────────────────────────

  async getIntegrationHealth(): Promise<IntegrationHealth> {
    const res = await fetch(`${BASE_URL}/admin/integrations/health`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch integration health');
    return res.json() as Promise<IntegrationHealth>;
  }

  // ── Transfers ─────────────────────────────────────────────────

  async getUncertainQueue(): Promise<AdminTransfer[]> {
    const res = await fetch(`${BASE_URL}/admin/transfers/uncertain-queue`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch uncertain queue');

    const data = await res.json() as Record<string, unknown>;
    const items: AdminTransfer[] = Array.isArray(data)
      ? (data as AdminTransfer[])
      : ((data['content'] as AdminTransfer[]) || []);

    return items.map((t) => ({ ...t, id: t.id ?? t.transferId }));
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

  // ── Fraud Alerts ──────────────────────────────────────────────

  async getFraudAlerts(): Promise<FraudAlert[]> {
    const res = await fetch(`${BASE_URL}/admin/risk/fraud-alerts`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch fraud alerts');

    const data = await res.json() as Record<string, unknown>;
    const items: FraudAlert[] = Array.isArray(data)
      ? (data as FraudAlert[])
      : ((data['content'] as FraudAlert[]) || []);

    // Normalise alias fields
    return items.map((a) => ({
      ...a,
      ruleTriggered: a.ruleTriggered ?? a.alertType ?? 'UNKNOWN',
    }));
  }

  async updateFraudAlertStatus(
    alertId: number,
    status: FraudAlertStatus,
    notes?: string,
  ): Promise<void> {
    const res = await fetch(`${BASE_URL}/admin/risk/fraud-alerts/${alertId}/status`, {
      method: 'PATCH',
      headers: this.getHeaders(),
      body: JSON.stringify({ status, resolutionNotes: notes }),
    });
    if (!res.ok) throw new Error('Failed to update fraud alert status');
  }

  // ── Audit Log ─────────────────────────────────────────────────

  async getAuditLogs(params?: { eventType?: string; entityType?: string }): Promise<AuditLogEntry[]> {
    const query = new URLSearchParams();
    if (params?.eventType)  query.set('eventType', params.eventType);
    if (params?.entityType) query.set('entityType', params.entityType);

    const res = await fetch(`${BASE_URL}/admin/audit-log?${query}`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch audit logs');

    const data = await res.json() as Record<string, unknown>;
    const items: AuditLogEntry[] = Array.isArray(data)
      ? (data as AuditLogEntry[])
      : ((data['content'] as AuditLogEntry[]) || []);

    // Normalise field name aliases
    return items.map((e) => ({
      ...e,
      details:  e.details  ?? e.detailJson  ?? '',
      clientIp: e.clientIp ?? e.ipAddress   ?? '',
    }));
  }

  // ── Stories ───────────────────────────────────────────────────

  async getStories(): Promise<Story[]> {
    const res = await fetch(`${BASE_URL}/admin/stories`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch stories');
    return (await res.json()) as Story[];
  }

  async getStory(id: string): Promise<Story> {
    const res = await fetch(`${BASE_URL}/admin/stories/${encodeURIComponent(id)}`, { headers: this.getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch story');
    return (await res.json()) as Story;
  }

  async createStory(payload: CreateStoryPayload): Promise<Story> {
    const res = await fetch(`${BASE_URL}/admin/stories`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({})) as Record<string, unknown>;
      throw new Error((err['message'] as string) || 'Failed to create story');
    }
    return (await res.json()) as Story;
  }

  async updateStory(id: string, payload: UpdateStoryPayload): Promise<Story> {
    const res = await fetch(`${BASE_URL}/admin/stories/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: this.getHeaders(),
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({})) as Record<string, unknown>;
      throw new Error((err['message'] as string) || 'Failed to update story');
    }
    return (await res.json()) as Story;
  }

  async publishStory(id: string): Promise<Story> {
    const res = await fetch(`${BASE_URL}/admin/stories/${encodeURIComponent(id)}/publish`, {
      method: 'POST',
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to publish story');
    return (await res.json()) as Story;
  }

  async archiveStory(id: string): Promise<Story> {
    const res = await fetch(`${BASE_URL}/admin/stories/${encodeURIComponent(id)}/archive`, {
      method: 'POST',
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to archive story');
    return (await res.json()) as Story;
  }

  // ── Support ──────────────────────────────────────────────────

  async getSupportConversations(status?: string, search?: string): Promise<SupportConversation[]> {
    const params = new URLSearchParams();
    if (status && status !== 'ALL') params.append('status', status);
    if (search && search.trim()) params.append('search', search.trim());
    const query = params.toString() ? `?${params.toString()}` : '';

    const res = await fetch(`${BASE_URL}/admin/support/conversations${query}`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch support conversations');
    return (await res.json()) as SupportConversation[];
  }

  async getSupportConversation(id: string): Promise<SupportConversationDetail> {
    const res = await fetch(`${BASE_URL}/admin/support/conversations/${encodeURIComponent(id)}`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch support conversation details');
    return (await res.json()) as SupportConversationDetail;
  }

  async replySupportConversation(id: string, text: string): Promise<SupportMessage> {
    const res = await fetch(`${BASE_URL}/admin/support/conversations/${encodeURIComponent(id)}/reply`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify({ text }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({})) as Record<string, unknown>;
      throw new Error((err['message'] as string) || 'Failed to send reply');
    }
    return (await res.json()) as SupportMessage;
  }

  async resolveSupportConversation(id: string): Promise<SupportConversation> {
    const res = await fetch(`${BASE_URL}/admin/support/conversations/${encodeURIComponent(id)}/resolve`, {
      method: 'POST',
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to resolve conversation');
    return (await res.json()) as SupportConversation;
  }

  async getCannedReplies(): Promise<CannedReply[]> {
    const res = await fetch(`${BASE_URL}/admin/support/canned-replies`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch canned replies');
    return (await res.json()) as CannedReply[];
  }
}

export const adminApi = new AdminApiClient();
