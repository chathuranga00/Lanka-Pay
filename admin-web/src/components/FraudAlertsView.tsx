import React, { useState } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import type { FraudAlert } from '../types/admin';
import { adminApi } from '../services/adminApi';

interface FraudAlertsViewProps {
  alerts: FraudAlert[];
  onRefresh: () => void;
}

export const FraudAlertsView: React.FC<FraudAlertsViewProps> = ({ alerts, onRefresh }) => {
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [updatingId, setUpdatingId] = useState<number | null>(null);

  const filtered = alerts.filter((a) => {
    return statusFilter === 'ALL' || a.status === statusFilter;
  });

  const handleUpdateStatus = async (alertId: number, newStatus: string) => {
    const notes = prompt(`Enter resolution notes for Fraud Alert #${alertId}:`);
    if (notes === null) return;

    setUpdatingId(alertId);
    try {
      await adminApi.updateFraudAlertStatus(alertId, newStatus as import('../types/admin').FraudAlertStatus, notes);
      onRefresh();
    } catch (err: unknown) {
      alert(`Update failed: ${(err as Error).message}`);
    } finally {
      setUpdatingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800 }}>Fraud & Velocity Risk Alerts</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Real-time fraud detections from velocity engines, high-risk threshold breaches, and rapid multi-account attempts.
          </p>
        </div>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{
            padding: '10px 16px',
            backgroundColor: 'var(--surface-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: '10px',
            fontSize: '13px',
            color: 'var(--text-primary)',
          }}
        >
          <option value="ALL">All Alert Statuses</option>
          <option value="OPEN">OPEN</option>
          <option value="UNDER_REVIEW">UNDER REVIEW</option>
          <option value="RESOLVED">RESOLVED</option>
          <option value="DISMISSED">DISMISSED</option>
        </select>
      </div>

      <div style={{
        backgroundColor: 'var(--surface-dark)',
        borderRadius: '16px',
        border: '1px solid var(--border-dark)',
        overflow: 'hidden',
      }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
          <thead>
            <tr style={{ backgroundColor: 'var(--card-dark)', borderBottom: '1px solid var(--border-dark)', color: 'var(--text-secondary)' }}>
              <th style={{ padding: '14px 18px' }}>Alert ID</th>
              <th style={{ padding: '14px 18px' }}>Severity</th>
              <th style={{ padding: '14px 18px' }}>Rule Triggered</th>
              <th style={{ padding: '14px 18px' }}>Description</th>
              <th style={{ padding: '14px 18px' }}>Status</th>
              <th style={{ padding: '14px 18px' }}>Detected At</th>
              <th style={{ padding: '14px 18px', textAlign: 'right' }}>Resolution</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ padding: '36px', textAlign: 'center', color: 'var(--text-muted)' }}>
                  No fraud alerts matching filter.
                </td>
              </tr>
            ) : (
              filtered.map((a) => (
                <tr key={a.id} style={{ borderBottom: '1px solid var(--border-dark)' }}>
                  <td style={{ padding: '14px 18px', fontFamily: 'monospace' }}>#{a.id}</td>
                  <td style={{ padding: '14px 18px' }}>
                    <span style={{
                      padding: '4px 8px',
                      borderRadius: '6px',
                      fontSize: '11px',
                      fontWeight: 700,
                      backgroundColor:
                        a.severity === 'CRITICAL' || a.severity === 'HIGH'
                          ? 'rgba(220, 38, 38, 0.2)'
                          : 'rgba(217, 119, 6, 0.2)',
                      color:
                        a.severity === 'CRITICAL' || a.severity === 'HIGH'
                          ? 'var(--error)'
                          : 'var(--warning)',
                    }}>
                      {a.severity}
                    </span>
                  </td>
                  <td style={{ padding: '14px 18px', fontWeight: 600 }}>{a.ruleTriggered}</td>
                  <td style={{ padding: '14px 18px', color: 'var(--text-secondary)' }}>{a.description}</td>
                  <td style={{ padding: '14px 18px' }}>
                    <span style={{
                      padding: '4px 8px',
                      borderRadius: '6px',
                      fontSize: '11px',
                      fontWeight: 700,
                      backgroundColor:
                        a.status === 'RESOLVED'
                          ? 'rgba(27, 135, 85, 0.2)'
                          : a.status === 'OPEN'
                          ? 'rgba(220, 38, 38, 0.2)'
                          : 'rgba(255, 255, 255, 0.1)',
                      color:
                        a.status === 'RESOLVED'
                          ? 'var(--success)'
                          : a.status === 'OPEN'
                          ? 'var(--error)'
                          : 'var(--text-primary)',
                    }}>
                      {a.status}
                    </span>
                  </td>
                  <td style={{ padding: '14px 18px', color: 'var(--text-muted)' }}>
                    {a.createdAt.substring(0, 16).replace('T', ' ')}
                  </td>
                  <td style={{ padding: '14px 18px', textAlign: 'right' }}>
                    <div style={{ display: 'inline-flex', gap: '8px' }}>
                      {a.status !== 'RESOLVED' && (
                        <button
                          disabled={updatingId === a.id}
                          onClick={() => handleUpdateStatus(a.id, 'RESOLVED')}
                          style={{
                            padding: '6px 10px',
                            backgroundColor: 'rgba(27, 135, 85, 0.2)',
                            color: 'var(--success)',
                            borderRadius: '8px',
                            fontSize: '12px',
                            fontWeight: 600,
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                          }}
                        >
                          <CheckCircle2 size={12} /> Resolve
                        </button>
                      )}
                      {a.status !== 'DISMISSED' && (
                        <button
                          disabled={updatingId === a.id}
                          onClick={() => handleUpdateStatus(a.id, 'DISMISSED')}
                          style={{
                            padding: '6px 10px',
                            backgroundColor: 'rgba(255, 255, 255, 0.05)',
                            color: 'var(--text-secondary)',
                            borderRadius: '8px',
                            fontSize: '12px',
                            fontWeight: 600,
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                          }}
                        >
                          <XCircle size={12} /> Dismiss
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
