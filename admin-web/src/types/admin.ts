// ── User ───────────────────────────────────────────────────────────────────
export type UserRole   = 'CUSTOMER' | 'MERCHANT' | 'ADMIN';
export type UserStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'FROZEN' | 'SUSPENDED' | 'LOCKED' | 'DELETED';

/** Matches AdminUserSummaryResponse from the backend. */
export interface AdminUser {
  userId: number;          // backend field name
  id?: number;             // alias for backward-compat
  fullName: string;
  maskedPhone: string;     // backend never returns full phone
  maskedNic: string;       // backend masks NIC
  role: UserRole;
  status: UserStatus;
  failedLoginAttempts?: number;
  createdAt: string;
}

// ── Bank / Integration ─────────────────────────────────────────────────────
/** Matches BankConfigResponse from the backend. */
export interface BankConfig {
  id: number;
  bankCode: string;        // backend field name (was "code")
  code?: string;           // alias
  displayName: string;     // backend field name (was "name")
  name?: string;           // alias
  simulated: boolean;
  active: boolean;
  createdAt?: string;
  // Frontend-only simulation fields (not from backend, used for display)
  baseLatencyMs?: number;
  failureRatePercent?: number;
  timeoutRatePercent?: number;
  healthStatus?: 'UP' | 'DOWN' | 'INACTIVE';
}

/** Matches IntegrationHealthResponse from the backend. */
export interface IntegrationHealth {
  overallStatus: 'UP' | 'DEGRADED' | 'DOWN';
  checkedAt: string;
  components: ComponentHealth[];
}

export interface ComponentHealth {
  name: string;
  status: string;
  simulated: boolean;
  latencyMs: number;
  message: string;
}

// ── Transfer ───────────────────────────────────────────────────────────────
export type TransferStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'REQUIRES_RECONCILIATION'
  | 'REVERSAL_PENDING'
  | 'REVERSED';

/** Matches AdminTransferSummaryResponse from the backend. */
export interface AdminTransfer {
  transferId: number;      // backend field name
  id?: number;             // alias
  reference: string;
  senderId?: number;
  senderFullName?: string;
  senderMaskedPhone?: string;
  receiverId?: number;
  receiverFullName?: string;
  receiverMaskedPhone?: string;
  amount: number;
  currency: string;
  status: TransferStatus;
  senderBankCode?: string;
  receiverBankCode?: string;
  bankTxRef?: string;
  providerReference?: string;
  failureReason?: string;
  simulated: boolean;
  createdAt: string;
}

// ── Fraud Alerts ───────────────────────────────────────────────────────────
export type FraudAlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type FraudAlertStatus   = 'OPEN' | 'UNDER_REVIEW' | 'RESOLVED' | 'DISMISSED';

/** Matches FraudAlertResponse from the backend. */
export interface FraudAlert {
  id: number;
  userId?: number;
  userMaskedPhone?: string;
  transferId?: number;
  transferReference?: string;
  alertType?: string;
  severity: FraudAlertSeverity;
  status: FraudAlertStatus;
  description: string;
  metadataJson?: string;
  createdAt: string;
  resolvedAt?: string;
  resolvedBy?: number;
  resolutionNotes?: string;
  // Alias fields for backward-compat with frontend components
  ruleTriggered?: string;
}

// ── Audit Log ──────────────────────────────────────────────────────────────
/** Matches AdminAuditLogResponse from the backend. */
export interface AuditLogEntry {
  id: number;
  actorId: number;
  eventType: string;
  entityType: string;
  entityId: number;
  detailJson?: string;    // backend field name (was "details")
  details?: string;       // alias
  ipAddress?: string;     // backend field name (was "clientIp")
  clientIp?: string;      // alias
  occurredAt: string;
}

// ── Auth Session ───────────────────────────────────────────────────────────
export interface AdminAuthSession {
  token: string;
  userId: number;
  fullName: string;
  role: UserRole;
}

// ── Stories ───────────────────────────────────────────────────────────────
export type StoryAudience =
  | 'ALL'
  | 'NEW_USERS'
  | 'HAS_CARD'
  | 'NO_CARD'
  | 'HAS_BANK_ACCOUNT'
  | 'NO_BANK_ACCOUNT';

export type StoryStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface StoryPage {
  text: string;
  imageRef?: string;
  actionLabel?: string;
  deepLink?: string;
}

export interface Story {
  id: string;
  title: string;
  caption: string;
  imageOrGradient: string;
  contentPages: StoryPage[];
  audience: StoryAudience;
  startsAt?: string;
  endsAt?: string;
  priority: number;
  status: StoryStatus;
  seen?: boolean;
  actionClickCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateStoryPayload {
  id?: string;
  title: string;
  caption: string;
  imageOrGradient?: string;
  contentPages: StoryPage[];
  audience?: StoryAudience;
  startsAt?: string;
  endsAt?: string;
  priority?: number;
  publishNow?: boolean;
}

export interface UpdateStoryPayload {
  title?: string;
  caption?: string;
  imageOrGradient?: string;
  contentPages?: StoryPage[];
  audience?: StoryAudience;
  startsAt?: string;
  endsAt?: string;
  priority?: number;
}

// ── Support ─────────────────────────────────────────────────────────────
export type SupportStatus = 'OPEN' | 'WAITING' | 'RESOLVED';
export type MessageSender = 'USER' | 'AGENT' | 'BOT';

export interface SupportConversation {
  id: string;
  userId: number;
  status: SupportStatus;
  category: string;
  subject: string;
  attachedTransactionRef?: string;
  createdAt: string;
  updatedAt: string;
  unreadCount?: number;
  lastMessage?: SupportMessage;
}

export interface SupportMessage {
  id: string;
  conversationId: string;
  sender: MessageSender;
  text: string;
  createdAt: string;
  readAt?: string;
  attachedTransactionRef?: string;
}

export interface MaskedTransactionSummary {
  reference: string;
  amount: number;
  currency: string;
  status: string;
  senderMasked: string;
  receiverMasked: string;
  createdAt: string;
  isAttachedByCustomer: boolean;
}

export interface SupportConversationDetail {
  conversation: SupportConversation;
  messages: SupportMessage[];
  attachedTransaction?: MaskedTransactionSummary | null;
  safetyNotice: string;
}

export interface CannedReply {
  id: string;
  title: string;
  text: string;
}

