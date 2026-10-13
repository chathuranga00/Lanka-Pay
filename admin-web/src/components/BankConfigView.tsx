import React, { useState } from 'react';
import { Activity, Server, Clock, AlertTriangle, CheckCircle2 } from 'lucide-react';
import type { BankConfig, IntegrationHealth } from '../types/admin';
import { adminApi } from '../services/adminApi';

interface BankConfigViewProps {
  banks: BankConfig[];
  health: IntegrationHealth | null;
  onRefresh: () => void;
}

export const BankConfigView: React.FC<BankConfigViewProps> = ({ banks, health, onRefresh }) => {
  const [updatingCode, setUpdatingCode] = useState<string | null>(null);

  const handleToggleActive = async (bank: BankConfig) => {
    const code = bank.bankCode ?? bank.code ?? '';
    setUpdatingCode(code);
    try {
      await adminApi.updateBankConfig(code, { active: !bank.active });
      onRefresh();
    } catch (err: any) {
      alert(`Bank update failed: ${err.message}`);
    } finally {
      setUpdatingCode(null);
    }
  };

  const handleAdjustLatency = async (bank: BankConfig) => {
    const code = bank.bankCode ?? bank.code ?? '';
    const newLatency = prompt(`Enter simulated latency in milliseconds for ${bank.displayName ?? bank.name}:`, (bank.baseLatencyMs ?? 0).toString());
    if (newLatency === null) return;
    const latencyVal = parseInt(newLatency, 10);
    if (isNaN(latencyVal) || latencyVal < 0) return;

    setUpdatingCode(code);
    try {
      await adminApi.updateBankConfig(code, { baseLatencyMs: latencyVal });
      onRefresh();
    } catch (err: any) {
      alert(`Latency update failed: ${err.message}`);
    } finally {
      setUpdatingCode(null);
    }
  };

  const handleAdjustFailureRate = async (bank: BankConfig) => {
    const code = bank.bankCode ?? bank.code ?? '';
    const newRate = prompt(`Enter failure rate percentage (0 - 100) for ${bank.displayName ?? bank.name}:`, (bank.failureRatePercent ?? 0).toString());
    if (newRate === null) return;
    const rateVal = parseFloat(newRate);
    if (isNaN(rateVal) || rateVal < 0 || rateVal > 100) return;

    setUpdatingCode(code);
    try {
      await adminApi.updateBankConfig(code, { failureRatePercent: rateVal });
      onRefresh();
    } catch (err: any) {
      alert(`Failure rate update failed: ${err.message}`);
    } finally {
      setUpdatingCode(null);
    }
  };

  return (
    <div>
      <div style={{ marginBottom: '24px' }}>
        <h2 style={{ fontSize: '20px', fontWeight: 800 }}>Simulated Bank Config & Health</h2>
        <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
          Configure chaos engineering parameters (latency, synthetic failure rates) and monitor CEFTS adapter health.
        </p>
      </div>

      {health && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '16px',
          marginBottom: '28px',
        }}>
          <div style={{
            backgroundColor: 'var(--surface-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: '16px',
            padding: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
          }}>
            <div style={{
              padding: '12px',
              borderRadius: '12px',
              backgroundColor: health.overallStatus === 'UP' ? 'rgba(27, 135, 85, 0.2)' : 'rgba(217, 119, 6, 0.2)',
              color: health.overallStatus === 'UP' ? 'var(--success)' : 'var(--warning)',
            }}>
              <Activity size={24} />
            </div>
            <div>
              <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Overall Integration Health</div>
              <div style={{ fontSize: '18px', fontWeight: 800, color: health.overallStatus === 'UP' ? 'var(--success)' : 'var(--warning)' }}>
                {health.overallStatus}
              </div>
            </div>
          </div>

          <div style={{
            backgroundColor: 'var(--surface-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: '16px',
            padding: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
          }}>
            <div style={{
              padding: '12px',
              borderRadius: '12px',
              backgroundColor: 'rgba(212, 175, 55, 0.2)',
              color: 'var(--accent-gold)',
            }}>
              <Server size={24} />
            </div>
            <div>
              <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Active Participating Banks</div>
              <div style={{ fontSize: '18px', fontWeight: 800 }}>
                {banks.filter((b) => b.active).length} / {banks.length} Online
              </div>
            </div>
          </div>
        </div>
      )}

      <div style={{
        backgroundColor: 'var(--surface-dark)',
        borderRadius: '16px',
        border: '1px solid var(--border-dark)',
        overflow: 'hidden',
      }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
          <thead>
            <tr style={{ backgroundColor: 'var(--card-dark)', borderBottom: '1px solid var(--border-dark)', color: 'var(--text-secondary)' }}>
              <th style={{ padding: '14px 18px' }}>Bank Code</th>
              <th style={{ padding: '14px 18px' }}>Institution Name</th>
              <th style={{ padding: '14px 18px' }}>Status</th>
              <th style={{ padding: '14px 18px' }}>Base Latency</th>
              <th style={{ padding: '14px 18px' }}>Failure Rate</th>
              <th style={{ padding: '14px 18px' }}>Simulation</th>
              <th style={{ padding: '14px 18px', textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {banks.map((b) => (
              <tr key={b.code} style={{ borderBottom: '1px solid var(--border-dark)' }}>
                <td style={{ padding: '14px 18px', fontFamily: 'monospace', fontWeight: 700 }}>{b.code}</td>
                <td style={{ padding: '14px 18px' }}>{b.name}</td>
                <td style={{ padding: '14px 18px' }}>
                  <span style={{
                    padding: '4px 10px',
                    borderRadius: '20px',
                    fontSize: '11px',
                    fontWeight: 700,
                    backgroundColor: b.active ? 'rgba(27, 135, 85, 0.2)' : 'rgba(220, 38, 38, 0.2)',
                    color: b.active ? 'var(--success)' : 'var(--error)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}>
                    {b.active ? <CheckCircle2 size={12} /> : <AlertTriangle size={12} />}
                    {b.active ? 'ACTIVE' : 'DISABLED'}
                  </span>
                </td>
                <td style={{ padding: '14px 18px' }}>
                  <button
                    onClick={() => handleAdjustLatency(b)}
                    style={{
                      padding: '4px 8px',
                      backgroundColor: 'rgba(255, 255, 255, 0.05)',
                      borderRadius: '6px',
                      color: 'var(--text-primary)',
                      fontSize: '12px',
                    }}
                  >
                    <Clock size={12} style={{ display: 'inline', marginRight: '4px' }} />
                    {b.baseLatencyMs ?? 0} ms ✎
                  </button>
                </td>
                <td style={{ padding: '14px 18px' }}>
                  <button
                    onClick={() => handleAdjustFailureRate(b)}
                    style={{
                      padding: '4px 8px',
                      backgroundColor: (b.failureRatePercent ?? 0) > 0 ? 'rgba(217, 119, 6, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                      borderRadius: '6px',
                      color: (b.failureRatePercent ?? 0) > 0 ? 'var(--warning)' : 'var(--text-primary)',
                      fontSize: '12px',
                    }}
                  >
                    {b.failureRatePercent ?? 0}% ✎
                  </button>
                </td>
                <td style={{ padding: '14px 18px' }}>
                  <span style={{ fontSize: '11px', color: 'var(--accent-gold)' }}>SIMULATED</span>
                </td>
                <td style={{ padding: '14px 18px', textAlign: 'right' }}>
                  <button
                    disabled={updatingCode === b.code}
                    onClick={() => handleToggleActive(b)}
                    style={{
                      padding: '6px 12px',
                      borderRadius: '8px',
                      fontSize: '12px',
                      fontWeight: 600,
                      backgroundColor: b.active ? 'rgba(220, 38, 38, 0.2)' : 'rgba(27, 135, 85, 0.2)',
                      color: b.active ? 'var(--error)' : 'var(--success)',
                    }}
                  >
                    {b.active ? 'Disable' : 'Enable'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
