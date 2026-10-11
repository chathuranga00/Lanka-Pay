import React, { useState } from 'react';
import { RefreshCw, Play, CheckCircle, AlertOctagon } from 'lucide-react';
import type { AdminTransfer } from '../types/admin';
import { adminApi } from '../services/adminApi';

interface UncertainQueueViewProps {
  queue: AdminTransfer[];
  onRefresh: () => void;
}

export const UncertainQueueView: React.FC<UncertainQueueViewProps> = ({ queue, onRefresh }) => {
  const [reconcilingId, setReconcilingId] = useState<number | null>(null);
  const [reconcilingAll, setReconcilingAll] = useState(false);

  const handleReconcileOne = async (id: number) => {
    setReconcilingId(id);
    try {
      await adminApi.reconcileTransfer(id);
      alert(`Transfer #${id} reconciled successfully.`);
      onRefresh();
    } catch (err: any) {
      alert(`Reconciliation error: ${err.message}`);
    } finally {
      setReconcilingId(null);
    }
  };

  const handleReconcileAll = async () => {
    if (!confirm(`Reconcile all ${queue.length} uncertain transactions with clearing adapters?`)) return;
    setReconcilingAll(true);
    try {
      await adminApi.reconcileAll();
      alert('Batch reconciliation completed.');
      onRefresh();
    } catch (err: any) {
      alert(`Bulk reconciliation error: ${err.message}`);
    } finally {
      setReconcilingAll(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800 }}>Failed / Uncertain Transactions Queue</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Transactions in REQUIRES_RECONCILIATION or TIMEOUT state. Run adapter status inquiry to settle pending legs.
          </p>
        </div>

        {queue.length > 0 && (
          <button
            disabled={reconcilingAll}
            onClick={handleReconcileAll}
            style={{
              padding: '10px 18px',
              backgroundColor: 'var(--primary-green)',
              color: 'white',
              borderRadius: '12px',
              fontSize: '13px',
              fontWeight: 700,
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              boxShadow: '0 4px 12px rgba(10, 92, 54, 0.4)',
            }}
          >
            <Play size={16} /> {reconcilingAll ? 'Reconciling All...' : `Reconcile All (${queue.length})`}
          </button>
        )}
      </div>

      {queue.length === 0 ? (
        <div style={{
          backgroundColor: 'var(--surface-dark)',
          borderRadius: '16px',
          border: '1px solid var(--border-dark)',
          padding: '48px',
          textAlign: 'center',
        }}>
          <CheckCircle size={48} color="var(--success)" style={{ margin: '0 auto 16px' }} />
          <h3 style={{ fontSize: '18px', fontWeight: 700 }}>Uncertain Queue is Clean</h3>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '4px' }}>
            No transactions currently require manual or scheduled bank reconciliation.
          </p>
        </div>
      ) : (
        <div style={{
          backgroundColor: 'var(--surface-dark)',
          borderRadius: '16px',
          border: '1px solid var(--border-dark)',
          overflow: 'hidden',
        }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
            <thead>
              <tr style={{ backgroundColor: 'var(--card-dark)', borderBottom: '1px solid var(--border-dark)', color: 'var(--text-secondary)' }}>
                <th style={{ padding: '14px 18px' }}>Tx ID</th>
                <th style={{ padding: '14px 18px' }}>Reference</th>
                <th style={{ padding: '14px 18px' }}>Status</th>
                <th style={{ padding: '14px 18px' }}>Amount</th>
                <th style={{ padding: '14px 18px' }}>Sender</th>
                <th style={{ padding: '14px 18px' }}>Receiver</th>
                <th style={{ padding: '14px 18px' }}>Reason</th>
                <th style={{ padding: '14px 18px', textAlign: 'right' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {queue.map((tx) => (
                <tr key={tx.id} style={{ borderBottom: '1px solid var(--border-dark)' }}>
                  <td style={{ padding: '14px 18px', fontFamily: 'monospace' }}>#{tx.id}</td>
                  <td style={{ padding: '14px 18px', fontFamily: 'monospace' }}>{tx.reference.substring(0, 8)}...</td>
                  <td style={{ padding: '14px 18px' }}>
                    <span style={{
                      padding: '4px 8px',
                      borderRadius: '6px',
                      fontSize: '11px',
                      fontWeight: 700,
                      backgroundColor: 'rgba(217, 119, 6, 0.2)',
                      color: 'var(--warning)',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}>
                      <AlertOctagon size={12} /> {tx.status}
                    </span>
                  </td>
                  <td style={{ padding: '14px 18px', fontWeight: 700 }}>
                    LKR {tx.amount.toFixed(2)}
                  </td>
                  <td style={{ padding: '14px 18px' }}>{tx.senderPhone || 'N/A'}</td>
                  <td style={{ padding: '14px 18px' }}>{tx.receiverDisplayName || 'N/A'}</td>
                  <td style={{ padding: '14px 18px', color: 'var(--text-secondary)', fontSize: '12px' }}>
                    {tx.failureReason || 'Bank Adapter Timeout / State Unknown'}
                  </td>
                  <td style={{ padding: '14px 18px', textAlign: 'right' }}>
                    <button
                      disabled={reconcilingId === tx.id}
                      onClick={() => handleReconcileOne(tx.id)}
                      style={{
                        padding: '6px 12px',
                        backgroundColor: 'rgba(212, 175, 55, 0.2)',
                        color: 'var(--accent-gold)',
                        borderRadius: '8px',
                        fontSize: '12px',
                        fontWeight: 600,
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                      }}
                    >
                      <RefreshCw size={12} className={reconcilingId === tx.id ? 'animate-spin' : ''} />
                      {reconcilingId === tx.id ? 'Reconciling...' : 'Reconcile'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
