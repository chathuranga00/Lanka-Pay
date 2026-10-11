import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { LoginScreen } from '../components/LoginScreen';
import { DashboardLayout } from '../components/DashboardLayout';
import type { AdminAuthSession } from '../types/admin';

// Mock adminApi
vi.mock('../services/adminApi', () => ({
  adminApi: {
    login: vi.fn().mockResolvedValue({
      token: 'mock-jwt-token',
      userId: 1,
      fullName: 'Chief Operations Officer',
      role: 'ADMIN',
    }),
    getSession: vi.fn().mockReturnValue(null),
    getUsers: vi.fn().mockResolvedValue([
      {
        id: 42,
        phone: '+94771234567',
        fullName: 'Kamal Perera',
        nic: '199012345678',
        role: 'CUSTOMER',
        status: 'ACTIVE',
        failedLoginAttempts: 0,
        createdAt: '2026-10-10T10:00:00Z',
      },
    ]),
    getBanks: vi.fn().mockResolvedValue([
      {
        code: 'SIMULATED_BOC',
        name: 'Bank of Ceylon (Simulated)',
        adapterClass: 'BocAdapter',
        active: true,
        simulated: true,
        baseLatencyMs: 50,
        failureRatePercent: 0,
        timeoutRatePercent: 0,
        healthStatus: 'HEALTHY',
        lastHealthCheck: '2026-10-11T10:00:00Z',
      },
    ]),
    getIntegrationHealth: vi.fn().mockResolvedValue({
      overallStatus: 'HEALTHY',
      activeBanksCount: 1,
      totalBanksCount: 1,
      banks: [],
    }),
    getUncertainQueue: vi.fn().mockResolvedValue([
      {
        id: 99,
        reference: '550e8400-e29b-41d4-a716-446655440000',
        status: 'REQUIRES_RECONCILIATION',
        amount: 2500.0,
        currency: 'LKR',
        senderPhone: '+9477***1234',
        receiverDisplayName: 'Nimal Silva',
        simulated: true,
        createdAt: '2026-10-11T09:00:00Z',
      },
    ]),
    getFraudAlerts: vi.fn().mockResolvedValue([
      {
        id: 7,
        severity: 'HIGH',
        status: 'OPEN',
        ruleTriggered: 'HIGH_VELOCITY_TRANSFERS',
        description: 'Exceeded 5 transfers in 60 seconds',
        userId: 42,
        createdAt: '2026-10-11T09:30:00Z',
      },
    ]),
    getAuditLogs: vi.fn().mockResolvedValue([
      {
        id: 1,
        actorId: 1,
        eventType: 'USER_FROZEN',
        entityType: 'USER',
        entityId: 42,
        clientIp: '127.0.0.1',
        details: 'Emergency freeze triggered',
        occurredAt: '2026-10-11T08:00:00Z',
      },
    ]),
  },
}));

describe('Admin Web Components', () => {
  it('renders Admin Login Screen with role protection title and inputs', () => {
    const handleLogin = vi.fn();
    render(<LoginScreen onLoginSuccess={handleLogin} />);

    expect(screen.getByText('LankaPay Operations')).toBeInTheDocument();
    expect(screen.getByText('Administrative Control Center • Role-Protected')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('+94770000001')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign In as Administrator/i })).toBeInTheDocument();
  });

  it('renders DashboardLayout with navigation tabs and simulated warning banner', async () => {
    const session: AdminAuthSession = {
      token: 'jwt-123',
      userId: 1,
      fullName: 'Operations Lead',
      role: 'ADMIN',
    };
    const handleLogout = vi.fn();

    render(<DashboardLayout session={session} onLogout={handleLogout} />);

    expect(screen.getByText(/SIMULATED - Test money only/i)).toBeInTheDocument();
    expect(screen.getByText('Welcome back, Operations Lead')).toBeInTheDocument();
    expect(screen.getByText('Dashboard Overview')).toBeInTheDocument();
    expect(screen.getByText('User Management')).toBeInTheDocument();
    expect(screen.getByText('Bank Configurations')).toBeInTheDocument();
    expect(screen.getAllByText('Uncertain Queue').length).toBeGreaterThan(0);
    expect(screen.getByText('Fraud & Risk Alerts')).toBeInTheDocument();
    expect(screen.getByText('Audit Logs')).toBeInTheDocument();
  });

  it('switches to Uncertain Queue and renders pending reconciliation transactions', async () => {
    const session: AdminAuthSession = {
      token: 'jwt-123',
      userId: 1,
      fullName: 'Operations Lead',
      role: 'ADMIN',
    };

    render(<DashboardLayout session={session} onLogout={vi.fn()} />);

    // Click Uncertain Queue navigation
    const queueNav = screen.getAllByText('Uncertain Queue')[0];
    fireEvent.click(queueNav);

    expect(await screen.findByText('Failed / Uncertain Transactions Queue')).toBeInTheDocument();
    expect(screen.getAllByText(/REQUIRES_RECONCILIATION/i).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: /^Reconcile$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Reconcile All/i })).toBeInTheDocument();
  });

  it('switches to Fraud Alerts and renders severity flags', async () => {
    const session: AdminAuthSession = {
      token: 'jwt-123',
      userId: 1,
      fullName: 'Operations Lead',
      role: 'ADMIN',
    };

    render(<DashboardLayout session={session} onLogout={vi.fn()} />);

    // Click Fraud & Risk Alerts navigation
    const fraudNav = screen.getByText('Fraud & Risk Alerts');
    fireEvent.click(fraudNav);

    expect(await screen.findByText('Fraud & Velocity Risk Alerts')).toBeInTheDocument();
    expect(screen.getByText('HIGH_VELOCITY_TRANSFERS')).toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeInTheDocument();
  });
});
