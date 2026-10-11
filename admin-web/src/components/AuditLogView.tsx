import React, { useState } from 'react';
import { Search } from 'lucide-react';
import type { AuditLogEntry } from '../types/admin';

interface AuditLogViewProps {
  logs: AuditLogEntry[];
}

export const AuditLogView: React.FC<AuditLogViewProps> = ({ logs }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [eventFilter, setEventFilter] = useState('ALL');

  const filtered = logs.filter((log) => {
    const matchesSearch =
      log.eventType.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.entityType.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.details.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.clientIp.includes(searchTerm);
    const matchesEvent = eventFilter === 'ALL' || log.eventType === eventFilter;
    return matchesSearch && matchesEvent;
  });

  const eventTypes = Array.from(new Set(logs.map((l) => l.eventType)));

  return (
    <div>
      <div style={{ marginBottom: '24px' }}>
        <h2 style={{ fontSize: '20px', fontWeight: 800 }}>Immutable Audit Log Search</h2>
        <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
          Comprehensive operational logs for all administrative actions, reconciliation events, and security status alterations.
        </p>
      </div>

      <div style={{
        display: 'flex',
        gap: '12px',
        marginBottom: '20px',
        backgroundColor: 'var(--surface-dark)',
        padding: '16px',
        borderRadius: '16px',
        border: '1px solid var(--border-dark)',
      }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <Search size={18} color="var(--text-muted)" style={{ position: 'absolute', left: '12px', top: '12px' }} />
          <input
            type="text"
            placeholder="Search by IP, details, or entity..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 12px 10px 38px',
              backgroundColor: 'var(--card-dark)',
              border: '1px solid var(--border-dark)',
              borderRadius: '10px',
              fontSize: '13px',
              color: 'var(--text-primary)',
            }}
          />
        </div>

        <select
          value={eventFilter}
          onChange={(e) => setEventFilter(e.target.value)}
          style={{
            padding: '10px 16px',
            backgroundColor: 'var(--card-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: '10px',
            fontSize: '13px',
            color: 'var(--text-primary)',
          }}
        >
          <option value="ALL">All Event Types</option>
          {eventTypes.map((et) => (
            <option key={et} value={et}>{et}</option>
          ))}
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
              <th style={{ padding: '14px 18px' }}>Log ID</th>
              <th style={{ padding: '14px 18px' }}>Timestamp</th>
              <th style={{ padding: '14px 18px' }}>Actor ID</th>
              <th style={{ padding: '14px 18px' }}>Event Type</th>
              <th style={{ padding: '14px 18px' }}>Entity</th>
              <th style={{ padding: '14px 18px' }}>Client IP</th>
              <th style={{ padding: '14px 18px' }}>Audit Details</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ padding: '36px', textAlign: 'center', color: 'var(--text-muted)' }}>
                  No audit entries found.
                </td>
              </tr>
            ) : (
              filtered.map((log) => (
                <tr key={log.id} style={{ borderBottom: '1px solid var(--border-dark)' }}>
                  <td style={{ padding: '14px 18px', fontFamily: 'monospace' }}>#{log.id}</td>
                  <td style={{ padding: '14px 18px', color: 'var(--text-muted)', fontSize: '12px' }}>
                    {log.occurredAt.substring(0, 19).replace('T', ' ')}
                  </td>
                  <td style={{ padding: '14px 18px', fontFamily: 'monospace' }}>Admin #{log.actorId}</td>
                  <td style={{ padding: '14px 18px' }}>
                    <span style={{
                      padding: '4px 8px',
                      borderRadius: '6px',
                      fontSize: '11px',
                      fontWeight: 700,
                      backgroundColor: 'rgba(212, 175, 55, 0.15)',
                      color: 'var(--accent-gold)',
                    }}>
                      {log.eventType}
                    </span>
                  </td>
                  <td style={{ padding: '14px 18px' }}>
                    {log.entityType} #{log.entityId}
                  </td>
                  <td style={{ padding: '14px 18px', fontFamily: 'monospace', color: 'var(--text-secondary)' }}>
                    {log.clientIp}
                  </td>
                  <td style={{ padding: '14px 18px', color: 'var(--text-secondary)', maxWidth: '300px' }}>
                    {log.details}
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
