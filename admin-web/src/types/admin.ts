export type UserRole = 'CUSTOMER' | 'MERCHANT' | 'ADMIN';
export type UserStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'FROZEN' | 'SUSPENDED' | 'LOCKED';

export interface AdminUser {
  id: number;
  phone: string;
  fullName: string;
  nic: string;
  role: UserRole;
  status: UserStatus;
  failedLoginAttempts: number;
  createdAt: string;
}

export interface BankConfig {
  code: string;
  name: string;
  adapterClass: string;
  active: boolean;
  simulated: boolean;
  baseLatencyMs: number;
  failureRatePercent: number;
  timeoutRatePercent: number;
  healthStatus: 'HEALTHY' | 'DEGRADED' | 'DOWN';
  lastHealthCheck: string;
}

export interface IntegrationHealth {
  overallStatus: 'HEALTHY' | 'DEGRADED' | 'DOWN';
  activeBanksCount: number;
  totalBanksCount: number;
  banks: {
    bankCode: string;
    bankName: string;
    status: 'HEALTHY' | 'DEGRADED' | 'DOWN';
    latencyMs: number;
    errorRate: number;
    lastChecked: string;
  }[];
}

export interface AdminTransfer {
  id: number;
  reference: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REQUIRES_RECONCILIATION' | 'REVERSAL_PENDING' | 'REVERSED';
  amount: number;
  currency: string;
  senderPhone?: string;
  receiverDisplayName?: string;
  bankTxRef?: string;
  providerReference?: string;
  failureReason?: string;
  simulated: boolean;
  createdAt: string;
}

export interface FraudAlert {
  id: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'DISMISSED';
  ruleTriggered: string;
  description: string;
  userId?: number;
  transferId?: number;
  createdAt: string;
}

export interface AuditLogEntry {
  id: number;
  actorId: number;
  eventType: string;
  entityType: string;
  entityId: number;
  clientIp: string;
  details: string;
  occurredAt: string;
}

export interface AdminAuthSession {
  token: string;
  userId: number;
  fullName: string;
  role: UserRole;
}
