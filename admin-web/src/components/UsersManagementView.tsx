import React, { useState } from 'react';
import { UserCheck, ShieldAlert, ShieldX, Search } from 'lucide-react';
import type { AdminUser, UserStatus } from '../types/admin';
import { adminApi } from '../services/adminApi';

interface UsersManagementViewProps {
  users: AdminUser[];
  onRefresh: () => void;
}

export const UsersManagementView: React.FC<UsersManagementViewProps> = ({ users, onRefresh }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [updatingId, setUpdatingId] = useState<number | null>(null);

  const filtered = users.filter((u) => {
    const matchesSearch =
      u.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.phone.includes(searchTerm) ||
      u.nic.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || u.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const handleStatusChange = async (userId: number, newStatus: UserStatus) => {
    const reason = prompt(`Enter reason for updating user #${userId} status to ${newStatus}:`);
    if (!reason) return;

    setUpdatingId(userId);
    try {
      await adminApi.updateUserStatus(userId, newStatus, reason);
      onRefresh();
    } catch (err: any) {
      alert(`Status update failed: ${err.message}`);
    } finally {
      setUpdatingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800 }}>User Status Management</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Inspect registered users, manage lock/freeze statuses, and audit security compliance.
          </p>
        </div>
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
            placeholder="Search by name, phone, or NIC..."
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
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{
            padding: '10px 16px',
            backgroundColor: 'var(--card-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: '10px',
            fontSize: '13px',
            color: 'var(--text-primary)',
          }}
        >
          <option value="ALL">All Statuses</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="FROZEN">FROZEN</option>
          <option value="SUSPENDED">SUSPENDED</option>
          <option value="LOCKED">LOCKED</option>
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
              <th style={{ padding: '14px 18px' }}>User ID</th>
              <th style={{ padding: '14px 18px' }}>Full Name</th>
              <th style={{ padding: '14px 18px' }}>Mobile Phone</th>
              <th style={{ padding: '14px 18px' }}>NIC</th>
              <th style={{ padding: '14px 18px' }}>Role</th>
              <th style={{ padding: '14px 18px' }}>Status</th>
              <th style={{ padding: '14px 18px', textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((user) => (
              <tr key={user.id} style={{ borderBottom: '1px solid var(--border-dark)' }}>
                <td style={{ padding: '14px 18px', fontFamily: 'monospace' }}>#{user.id}</td>
                <td style={{ padding: '14px 18px', fontWeight: 600 }}>{user.fullName}</td>
                <td style={{ padding: '14px 18px' }}>{user.phone}</td>
                <td style={{ padding: '14px 18px', fontFamily: 'monospace' }}>{user.nic}</td>
                <td style={{ padding: '14px 18px' }}>
                  <span style={{
                    padding: '4px 8px',
                    borderRadius: '6px',
                    fontSize: '11px',
                    fontWeight: 700,
                    backgroundColor: user.role === 'ADMIN' ? 'rgba(212, 175, 55, 0.2)' : 'rgba(255, 255, 255, 0.08)',
                    color: user.role === 'ADMIN' ? 'var(--accent-gold)' : 'var(--text-primary)',
                  }}>
                    {user.role}
                  </span>
                </td>
                <td style={{ padding: '14px 18px' }}>
                  <span style={{
                    padding: '4px 10px',
                    borderRadius: '20px',
                    fontSize: '11px',
                    fontWeight: 700,
                    backgroundColor:
                      user.status === 'ACTIVE'
                        ? 'rgba(27, 135, 85, 0.2)'
                        : user.status === 'FROZEN'
                        ? 'rgba(217, 119, 6, 0.2)'
                        : 'rgba(220, 38, 38, 0.2)',
                    color:
                      user.status === 'ACTIVE'
                        ? 'var(--success)'
                        : user.status === 'FROZEN'
                        ? 'var(--warning)'
                        : 'var(--error)',
                  }}>
                    {user.status}
                  </span>
                </td>
                <td style={{ padding: '14px 18px', textAlign: 'right' }}>
                  <div style={{ display: 'inline-flex', gap: '8px' }}>
                    {user.status !== 'ACTIVE' && (
                      <button
                        disabled={updatingId === user.id}
                        onClick={() => handleStatusChange(user.id, 'ACTIVE')}
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
                        <UserCheck size={14} /> Unfreeze
                      </button>
                    )}
                    {user.status !== 'FROZEN' && (
                      <button
                        disabled={updatingId === user.id}
                        onClick={() => handleStatusChange(user.id, 'FROZEN')}
                        style={{
                          padding: '6px 10px',
                          backgroundColor: 'rgba(217, 119, 6, 0.2)',
                          color: 'var(--warning)',
                          borderRadius: '8px',
                          fontSize: '12px',
                          fontWeight: 600,
                          display: 'flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <ShieldAlert size={14} /> Freeze
                      </button>
                    )}
                    {user.status !== 'SUSPENDED' && (
                      <button
                        disabled={updatingId === user.id}
                        onClick={() => handleStatusChange(user.id, 'SUSPENDED')}
                        style={{
                          padding: '6px 10px',
                          backgroundColor: 'rgba(220, 38, 38, 0.2)',
                          color: 'var(--error)',
                          borderRadius: '8px',
                          fontSize: '12px',
                          fontWeight: 600,
                          display: 'flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <ShieldX size={14} /> Suspend
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
