import React, { useState, useEffect } from 'react';
import {
  Users,
  Building2,
  AlertOctagon,
  ShieldAlert,
  FileText,
  LogOut,
  RefreshCw,
  LayoutDashboard,
  ShieldCheck,
} from 'lucide-react';
import type {
  AdminAuthSession,
  AdminUser,
  BankConfig,
  IntegrationHealth,
  AdminTransfer,
  FraudAlert,
  AuditLogEntry,
} from '../types/admin';
import { adminApi } from '../services/adminApi';
import { UsersManagementView } from './UsersManagementView';
import { BankConfigView } from './BankConfigView';
import { UncertainQueueView } from './UncertainQueueView';
import { FraudAlertsView } from './FraudAlertsView';
import { AuditLogView } from './AuditLogView';

interface DashboardLayoutProps {
  session: AdminAuthSession;
  onLogout: () => void;
}

type TabType = 'overview' | 'users' | 'banks' | 'uncertain' | 'fraud' | 'audit';

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({ session, onLogout }) => {
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const [loading, setLoading] = useState(false);

  // Data states
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [banks, setBanks] = useState<BankConfig[]>([]);
  const [health, setHealth] = useState<IntegrationHealth | null>(null);
  const [queue, setQueue] = useState<AdminTransfer[]>([]);
  const [alerts, setAlerts] = useState<FraudAlert[]>([]);
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);

  const fetchAllData = async () => {
    setLoading(true);
    try {
      const [u, b, h, q, a, l] = await Promise.allSettled([
        adminApi.getUsers(),
        adminApi.getBanks(),
        adminApi.getIntegrationHealth(),
        adminApi.getUncertainQueue(),
        adminApi.getFraudAlerts(),
        adminApi.getAuditLogs(),
      ]);

      if (u.status === 'fulfilled') setUsers(u.value);
      if (b.status === 'fulfilled') setBanks(b.value);
      if (h.status === 'fulfilled') setHealth(h.value);
      if (q.status === 'fulfilled') setQueue(q.value);
      if (a.status === 'fulfilled') setAlerts(a.value);
      if (l.status === 'fulfilled') setLogs(l.value);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAllData();
  }, []);

  const openAlertsCount = alerts.filter((a) => a.status === 'OPEN').length;
  const uncertainCount = queue.length;

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Top Banner */}
      <div className="simulated-banner">
        <span>⚠ SIMULATED - Test money only. Connected to LankaPay clearing sandbox.</span>
      </div>

      <div style={{ display: 'flex', flex: 1 }}>
        {/* Sidebar */}
        <aside style={{
          width: '260px',
          backgroundColor: 'var(--surface-dark)',
          borderRight: '1px solid var(--border-dark)',
          display: 'flex',
          flexDirection: 'column',
          padding: '24px 16px',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '0 8px 24px', borderBottom: '1px solid var(--border-dark)' }}>
            <div style={{
              padding: '8px',
              backgroundColor: 'rgba(10, 92, 54, 0.2)',
              borderRadius: '10px',
              border: '1px solid var(--primary-green-light)',
            }}>
              <ShieldCheck size={22} color="var(--accent-gold)" />
            </div>
            <div>
              <div style={{ fontSize: '15px', fontWeight: 800, color: 'var(--text-primary)' }}>LankaPay Admin</div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>Operations Portal</div>
            </div>
          </div>

          <nav style={{ marginTop: '24px', display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <NavItem
              active={activeTab === 'overview'}
              onClick={() => setActiveTab('overview')}
              icon={<LayoutDashboard size={18} />}
              label="Dashboard Overview"
            />
            <NavItem
              active={activeTab === 'users'}
              onClick={() => setActiveTab('users')}
              icon={<Users size={18} />}
              label="User Management"
            />
            <NavItem
              active={activeTab === 'banks'}
              onClick={() => setActiveTab('banks')}
              icon={<Building2 size={18} />}
              label="Bank Configurations"
            />
            <NavItem
              active={activeTab === 'uncertain'}
              onClick={() => setActiveTab('uncertain')}
              icon={<AlertOctagon size={18} />}
              label="Uncertain Queue"
              badge={uncertainCount > 0 ? uncertainCount : undefined}
            />
            <NavItem
              active={activeTab === 'fraud'}
              onClick={() => setActiveTab('fraud')}
              icon={<ShieldAlert size={18} />}
              label="Fraud & Risk Alerts"
              badge={openAlertsCount > 0 ? openAlertsCount : undefined}
              badgeColor="var(--error)"
            />
            <NavItem
              active={activeTab === 'audit'}
              onClick={() => setActiveTab('audit')}
              icon={<FileText size={18} />}
              label="Audit Logs"
            />
          </nav>

          <div style={{ paddingTop: '16px', borderTop: '1px solid var(--border-dark)' }}>
            <div style={{ padding: '8px', marginBottom: '8px' }}>
              <div style={{ fontSize: '13px', fontWeight: 700 }}>{session.fullName}</div>
              <div style={{ fontSize: '11px', color: 'var(--accent-gold)' }}>Role: {session.role}</div>
            </div>
            <button
              onClick={onLogout}
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: '10px',
                backgroundColor: 'rgba(220, 38, 38, 0.1)',
                color: 'var(--error)',
                fontSize: '13px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
              }}
            >
              <LogOut size={16} /> Sign Out
            </button>
          </div>
        </aside>

        {/* Main Content Area */}
        <main style={{ flex: 1, padding: '32px 40px', overflowY: 'auto', backgroundColor: 'var(--bg-dark)' }}>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '16px' }}>
            <button
              disabled={loading}
              onClick={fetchAllData}
              style={{
                padding: '8px 14px',
                backgroundColor: 'var(--surface-dark)',
                border: '1px solid var(--border-dark)',
                borderRadius: '10px',
                fontSize: '12px',
                fontWeight: 600,
                color: 'var(--text-secondary)',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
              }}
            >
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
              {loading ? 'Refreshing...' : 'Refresh Data'}
            </button>
          </div>

          {activeTab === 'overview' && (
            <div>
              <div style={{ marginBottom: '28px' }}>
                <h1 style={{ fontSize: '24px', fontWeight: 800 }}>Welcome back, {session.fullName}</h1>
                <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '4px' }}>
                  Central Bank of Sri Lanka (CBSL) & LankaPay CEFTS Simulated Network Dashboard.
                </p>
              </div>

              {/* Metric Cards */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                gap: '16px',
                marginBottom: '32px',
              }}>
                <MetricCard title="Registered Users" value={users.length.toString()} icon={<Users size={22} />} />
                <MetricCard
                  title="Participating Banks"
                  value={`${banks.filter((b) => b.active).length} / ${banks.length}`}
                  icon={<Building2 size={22} />}
                  highlightColor="var(--accent-gold)"
                />
                <MetricCard
                  title="Uncertain Queue"
                  value={uncertainCount.toString()}
                  icon={<AlertOctagon size={22} />}
                  highlightColor={uncertainCount > 0 ? 'var(--warning)' : 'var(--success)'}
                />
                <MetricCard
                  title="Active Fraud Alerts"
                  value={openAlertsCount.toString()}
                  icon={<ShieldAlert size={22} />}
                  highlightColor={openAlertsCount > 0 ? 'var(--error)' : 'var(--success)'}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                {/* Recent Uncertain Transactions */}
                <div style={{
                  backgroundColor: 'var(--surface-dark)',
                  borderRadius: '16px',
                  border: '1px solid var(--border-dark)',
                  padding: '24px',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                    <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Uncertain Queue Preview</h3>
                    <button
                      onClick={() => setActiveTab('uncertain')}
                      style={{ fontSize: '12px', color: 'var(--accent-gold)', fontWeight: 600 }}
                    >
                      View All →
                    </button>
                  </div>
                  {queue.length === 0 ? (
                    <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>No pending reconciliations.</p>
                  ) : (
                    queue.slice(0, 3).map((tx) => (
                      <div key={tx.id} style={{
                        padding: '12px',
                        borderRadius: '10px',
                        backgroundColor: 'var(--card-dark)',
                        marginBottom: '8px',
                        display: 'flex',
                        justifyContent: 'space-between',
                      }}>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: '13px' }}>#{tx.id} - LKR {tx.amount.toFixed(2)}</div>
                          <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>{tx.status}</div>
                        </div>
                        <span style={{ fontSize: '11px', color: 'var(--warning)', fontWeight: 700 }}>Reconcile Required</span>
                      </div>
                    ))
                  )}
                </div>

                {/* Integration Health Preview */}
                <div style={{
                  backgroundColor: 'var(--surface-dark)',
                  borderRadius: '16px',
                  border: '1px solid var(--border-dark)',
                  padding: '24px',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                    <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Integration Adapter Status</h3>
                    <button
                      onClick={() => setActiveTab('banks')}
                      style={{ fontSize: '12px', color: 'var(--accent-gold)', fontWeight: 600 }}
                    >
                      Configure →
                    </button>
                  </div>
                  {banks.slice(0, 3).map((b) => (
                    <div key={b.code} style={{
                      padding: '12px',
                      borderRadius: '10px',
                      backgroundColor: 'var(--card-dark)',
                      marginBottom: '8px',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                    }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: '13px' }}>{b.name}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>Latency: {b.baseLatencyMs}ms • Fail: {b.failureRatePercent}%</div>
                      </div>
                      <span style={{
                        padding: '2px 8px',
                        borderRadius: '12px',
                        fontSize: '11px',
                        fontWeight: 700,
                        backgroundColor: b.active ? 'rgba(27, 135, 85, 0.2)' : 'rgba(220, 38, 38, 0.2)',
                        color: b.active ? 'var(--success)' : 'var(--error)',
                      }}>
                        {b.active ? 'ONLINE' : 'OFFLINE'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'users' && <UsersManagementView users={users} onRefresh={fetchAllData} />}
          {activeTab === 'banks' && <BankConfigView banks={banks} health={health} onRefresh={fetchAllData} />}
          {activeTab === 'uncertain' && <UncertainQueueView queue={queue} onRefresh={fetchAllData} />}
          {activeTab === 'fraud' && <FraudAlertsView alerts={alerts} onRefresh={fetchAllData} />}
          {activeTab === 'audit' && <AuditLogView logs={logs} />}
        </main>
      </div>
    </div>
  );
};

const NavItem: React.FC<{
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  badge?: number;
  badgeColor?: string;
}> = ({ active, onClick, icon, label, badge, badgeColor }) => (
  <button
    onClick={onClick}
    style={{
      padding: '10px 14px',
      borderRadius: '12px',
      backgroundColor: active ? 'rgba(10, 92, 54, 0.25)' : 'transparent',
      color: active ? 'var(--text-primary)' : 'var(--text-secondary)',
      border: active ? '1px solid var(--primary-green-light)' : '1px solid transparent',
      fontSize: '13px',
      fontWeight: active ? 700 : 500,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      width: '100%',
      textAlign: 'left',
    }}
  >
    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
      <span style={{ color: active ? 'var(--accent-gold)' : 'inherit' }}>{icon}</span>
      {label}
    </div>
    {badge !== undefined && (
      <span style={{
        padding: '2px 8px',
        borderRadius: '10px',
        fontSize: '11px',
        fontWeight: 700,
        backgroundColor: badgeColor || 'var(--warning)',
        color: 'white',
      }}>
        {badge}
      </span>
    )}
  </button>
);

const MetricCard: React.FC<{
  title: string;
  value: string;
  icon: React.ReactNode;
  highlightColor?: string;
}> = ({ title, value, icon, highlightColor }) => (
  <div style={{
    backgroundColor: 'var(--surface-dark)',
    borderRadius: '16px',
    border: '1px solid var(--border-dark)',
    padding: '20px',
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  }}>
    <div style={{
      padding: '12px',
      borderRadius: '12px',
      backgroundColor: 'rgba(255, 255, 255, 0.05)',
      color: highlightColor || 'var(--primary-green-light)',
    }}>
      {icon}
    </div>
    <div>
      <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{title}</div>
      <div style={{ fontSize: '22px', fontWeight: 800, color: highlightColor || 'var(--text-primary)' }}>
        {value}
      </div>
    </div>
  </div>
);
