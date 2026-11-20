import React, { useState, useEffect } from 'react';
import {
  MessageSquare,
  Search,
  Send,
  CheckCircle,
  AlertCircle,
  Clock,
  ShieldAlert,
  User,
  Bot,
  Headphones,
  Paperclip,
  RefreshCw,
} from 'lucide-react';
import type {
  SupportConversation,
  SupportConversationDetail,
  CannedReply,
  SupportStatus,
} from '../types/admin';
import { adminApi } from '../services/adminApi';

export const SupportInboxView: React.FC = () => {
  const [conversations, setConversations] = useState<SupportConversation[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<SupportConversationDetail | null>(null);
  const [cannedReplies, setCannedReplies] = useState<CannedReply[]>([]);
  const [filterStatus, setFilterStatus] = useState<SupportStatus | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [replyText, setReplyText] = useState('');
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchConversations = async () => {
    setLoading(true);
    setError(null);
    try {
      const convs = await adminApi.getSupportConversations(filterStatus, searchQuery);
      setConversations(convs);
      if (selectedId && !convs.some((c) => c.id === selectedId)) {
        // Keep selected if still in view or reset if filtered out
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load conversations');
    } finally {
      setLoading(false);
    }
  };

  const fetchCannedReplies = async () => {
    try {
      const replies = await adminApi.getCannedReplies();
      setCannedReplies(replies);
    } catch {
      // non-blocking fallback
    }
  };

  const fetchDetail = async (id: string) => {
    setDetailLoading(true);
    try {
      const d = await adminApi.getSupportConversation(id);
      setDetail(d);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load conversation details');
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    fetchConversations();
    fetchCannedReplies();
  }, [filterStatus]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    fetchConversations();
  };

  const handleSelectConversation = (id: string) => {
    setSelectedId(id);
    fetchDetail(id);
  };

  const handleSendReply = async () => {
    if (!selectedId || !replyText.trim()) return;
    setActionLoading(true);
    try {
      await adminApi.replySupportConversation(selectedId, replyText.trim());
      setReplyText('');
      await fetchDetail(selectedId);
      await fetchConversations();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to send reply');
    } finally {
      setActionLoading(false);
    }
  };

  const handleResolve = async () => {
    if (!selectedId) return;
    if (!confirm('Mark this customer conversation as RESOLVED?')) return;
    setActionLoading(true);
    try {
      await adminApi.resolveSupportConversation(selectedId);
      await fetchDetail(selectedId);
      await fetchConversations();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to resolve conversation');
    } finally {
      setActionLoading(false);
    }
  };

  const getStatusBadge = (status: SupportStatus) => {
    switch (status) {
      case 'OPEN':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '2px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: 600,
            backgroundColor: 'rgba(59, 130, 246, 0.15)',
            color: '#60a5fa',
          }}>
            <Clock size={12} /> OPEN
          </span>
        );
      case 'WAITING':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '2px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: 600,
            backgroundColor: 'rgba(245, 158, 11, 0.15)',
            color: '#fbbf24',
          }}>
            <AlertCircle size={12} /> WAITING AGENT
          </span>
        );
      case 'RESOLVED':
        return (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            padding: '2px 8px',
            borderRadius: '9999px',
            fontSize: '11px',
            fontWeight: 600,
            backgroundColor: 'rgba(34, 197, 94, 0.15)',
            color: '#4ade80',
          }}>
            <CheckCircle size={12} /> RESOLVED
          </span>
        );
    }
  };

  const openCount = conversations.filter((c) => c.status === 'OPEN').length;
  const waitingCount = conversations.filter((c) => c.status === 'WAITING').length;
  const resolvedCount = conversations.filter((c) => c.status === 'RESOLVED').length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: '16px' }}>
      {/* Header bar */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        borderBottom: '1px solid var(--border-dark)',
        paddingBottom: '16px',
      }}>
        <div>
          <h2 style={{ margin: 0, fontSize: '20px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
            <MessageSquare size={22} color="var(--primary-accent)" />
            Customer Support Inbox
          </h2>
          <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--text-muted)' }}>
            Audited customer inquiries, rule-based bot escalations, and verified resolution.
          </p>
        </div>

        <button
          onClick={fetchConversations}
          disabled={loading}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            padding: '8px 14px',
            backgroundColor: 'var(--card-dark)',
            border: '1px solid var(--border-dark)',
            color: 'var(--text-light)',
            borderRadius: '8px',
            cursor: 'pointer',
            fontSize: '13px',
          }}
        >
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
          Refresh
        </button>
      </div>

      {error && (
        <div style={{
          padding: '12px 16px',
          backgroundColor: 'rgba(239, 68, 68, 0.1)',
          border: '1px solid rgba(239, 68, 68, 0.3)',
          color: '#f87171',
          borderRadius: '8px',
          fontSize: '13px',
        }}>
          {error}
        </div>
      )}

      {/* Main 2-column layout */}
      <div style={{ display: 'grid', gridTemplateColumns: '360px 1fr', gap: '16px', flex: 1, minHeight: '600px' }}>
        {/* Left Column: List */}
        <div style={{
          backgroundColor: 'var(--card-dark)',
          border: '1px solid var(--border-dark)',
          borderRadius: '12px',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}>
          {/* Status Tabs */}
          <div style={{
            display: 'flex',
            borderBottom: '1px solid var(--border-dark)',
            padding: '8px',
            gap: '6px',
            backgroundColor: 'rgba(0,0,0,0.1)',
          }}>
            {(['ALL', 'WAITING', 'OPEN', 'RESOLVED'] as const).map((s) => (
              <button
                key={s}
                onClick={() => setFilterStatus(s)}
                style={{
                  flex: 1,
                  padding: '6px 4px',
                  borderRadius: '6px',
                  border: 'none',
                  backgroundColor: filterStatus === s ? 'var(--primary-accent)' : 'transparent',
                  color: filterStatus === s ? '#ffffff' : 'var(--text-muted)',
                  fontSize: '11px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  textAlign: 'center',
                }}
              >
                {s === 'ALL' && `All (${conversations.length})`}
                {s === 'WAITING' && `Wait (${waitingCount})`}
                {s === 'OPEN' && `Open (${openCount})`}
                {s === 'RESOLVED' && `Done (${resolvedCount})`}
              </button>
            ))}
          </div>

          {/* Search Box */}
          <form onSubmit={handleSearchSubmit} style={{ padding: '12px', borderBottom: '1px solid var(--border-dark)' }}>
            <div style={{ position: 'relative' }}>
              <Search size={14} style={{ position: 'absolute', left: '10px', top: '10px', color: 'var(--text-muted)' }} />
              <input
                type="text"
                placeholder="Search subject, user, ID..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{
                  width: '100%',
                  padding: '7px 10px 7px 32px',
                  backgroundColor: 'var(--surface-dark)',
                  border: '1px solid var(--border-dark)',
                  borderRadius: '6px',
                  color: 'var(--text-light)',
                  fontSize: '12px',
                  boxSizing: 'border-box',
                }}
              />
            </div>
          </form>

          {/* Conversation List */}
          <div style={{ flex: 1, overflowY: 'auto' }}>
            {conversations.length === 0 ? (
              <div style={{ padding: '32px 16px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                No conversations found matching filters.
              </div>
            ) : (
              conversations.map((c) => {
                const isSelected = c.id === selectedId;
                return (
                  <div
                    key={c.id}
                    onClick={() => handleSelectConversation(c.id)}
                    style={{
                      padding: '12px 16px',
                      borderBottom: '1px solid var(--border-dark)',
                      cursor: 'pointer',
                      backgroundColor: isSelected ? 'rgba(59, 130, 246, 0.1)' : 'transparent',
                      borderLeft: isSelected ? '3px solid var(--primary-accent)' : '3px solid transparent',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>
                        {c.id} • User #{c.userId}
                      </span>
                      {getStatusBadge(c.status)}
                    </div>

                    <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-light)', marginBottom: '4px' }}>
                      {c.subject}
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{
                        fontSize: '10px',
                        padding: '1px 6px',
                        backgroundColor: 'rgba(255,255,255,0.06)',
                        borderRadius: '4px',
                        color: 'var(--text-muted)',
                      }}>
                        {c.category}
                      </span>
                      {c.attachedTransactionRef && (
                        <span style={{ fontSize: '10px', color: '#60a5fa', display: 'flex', alignItems: 'center', gap: '3px' }}>
                          <Paperclip size={10} /> {c.attachedTransactionRef}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column: Conversation Thread & Actions */}
        <div style={{
          backgroundColor: 'var(--card-dark)',
          border: '1px solid var(--border-dark)',
          borderRadius: '12px',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}>
          {!selectedId || !detail ? (
            <div style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              alignItems: 'center',
              color: 'var(--text-muted)',
              gap: '12px',
            }}>
              <MessageSquare size={48} opacity={0.3} />
              <p style={{ margin: 0, fontSize: '14px' }}>Select a conversation from the left to view messages and reply.</p>
            </div>
          ) : detailLoading ? (
            <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', color: 'var(--text-muted)' }}>
              Loading conversation details...
            </div>
          ) : (
            <>
              {/* Thread Header */}
              <div style={{
                padding: '16px',
                borderBottom: '1px solid var(--border-dark)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                backgroundColor: 'rgba(0,0,0,0.1)',
              }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                    <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700 }}>
                      {detail.conversation.subject}
                    </h3>
                    {getStatusBadge(detail.conversation.status)}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    Customer #{detail.conversation.userId} • Category: {detail.conversation.category} • Thread ID: {detail.conversation.id}
                  </div>
                </div>

                {detail.conversation.status !== 'RESOLVED' && (
                  <button
                    onClick={handleResolve}
                    disabled={actionLoading}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                      padding: '8px 14px',
                      backgroundColor: 'rgba(34, 197, 94, 0.15)',
                      border: '1px solid rgba(34, 197, 94, 0.4)',
                      color: '#4ade80',
                      borderRadius: '8px',
                      cursor: 'pointer',
                      fontSize: '13px',
                      fontWeight: 600,
                    }}
                  >
                    <CheckCircle size={14} />
                    Mark Resolved
                  </button>
                )}
              </div>

              {/* Security Banner */}
              <div style={{
                padding: '10px 16px',
                backgroundColor: 'rgba(239, 68, 68, 0.08)',
                borderBottom: '1px solid rgba(239, 68, 68, 0.2)',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                color: '#f87171',
                fontSize: '12px',
              }}>
                <ShieldAlert size={16} />
                <span>
                  <strong>Compliance Warning:</strong> Agents must NEVER ask customers for passwords, PINs, OTPs, or card numbers. Every view and reply is audited.
                </span>
              </div>

              {/* Attached Transaction Summary (if attached) */}
              {detail.attachedTransaction && (
                <div style={{
                  margin: '12px 16px 0',
                  padding: '12px',
                  backgroundColor: 'rgba(59, 130, 246, 0.08)',
                  border: '1px solid rgba(59, 130, 246, 0.25)',
                  borderRadius: '8px',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                    <span style={{ fontSize: '12px', fontWeight: 700, color: '#60a5fa', display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <Paperclip size={13} /> Attached Transaction: {detail.attachedTransaction.reference}
                    </span>
                    <span style={{
                      fontSize: '10px',
                      fontWeight: 700,
                      padding: '2px 6px',
                      backgroundColor: 'rgba(34, 197, 94, 0.2)',
                      color: '#4ade80',
                      borderRadius: '4px',
                    }}>
                      {detail.attachedTransaction.status}
                    </span>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px', fontSize: '11px', color: 'var(--text-light)' }}>
                    <div>Amount: <strong>{detail.attachedTransaction.currency} {Number(detail.attachedTransaction.amount).toFixed(2)}</strong></div>
                    <div>Sender: <strong>{detail.attachedTransaction.senderMasked}</strong></div>
                    <div>Receiver: <strong>{detail.attachedTransaction.receiverMasked}</strong></div>
                  </div>
                </div>
              )}

              {/* Message Thread */}
              <div style={{ flex: 1, padding: '16px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {detail.messages.map((m) => {
                  const isAgent = m.sender === 'AGENT';
                  const isUser = m.sender === 'USER';
                  const isBot = m.sender === 'BOT';

                  return (
                    <div
                      key={m.id}
                      style={{
                        alignSelf: isAgent ? 'flex-end' : 'flex-start',
                        maxWidth: '75%',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: isAgent ? 'flex-end' : 'flex-start',
                      }}
                    >
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        marginBottom: '4px',
                        fontSize: '11px',
                        color: 'var(--text-muted)',
                      }}>
                        {isAgent && <><Headphones size={12} color="#60a5fa" /> <span>You (Agent)</span></>}
                        {isUser && <><User size={12} color="#4ade80" /> <span>Customer #{detail.conversation.userId}</span></>}
                        {isBot && <><Bot size={12} color="#fbbf24" /> <span>Automated FAQ Assistant</span></>}
                        <span>• {new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      </div>

                      <div style={{
                        padding: '10px 14px',
                        borderRadius: '12px',
                        backgroundColor: isAgent
                          ? 'var(--primary-accent)'
                          : isBot
                            ? 'rgba(245, 158, 11, 0.12)'
                            : 'var(--surface-dark)',
                        border: isAgent
                          ? 'none'
                          : isBot
                            ? '1px solid rgba(245, 158, 11, 0.3)'
                            : '1px solid var(--border-dark)',
                        color: isAgent ? '#ffffff' : 'var(--text-light)',
                        fontSize: '13px',
                        lineHeight: '1.45',
                        whiteSpace: 'pre-wrap',
                      }}>
                        {m.text}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Reply Section */}
              <div style={{
                padding: '12px 16px',
                borderTop: '1px solid var(--border-dark)',
                backgroundColor: 'rgba(0,0,0,0.15)',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
              }}>
                {/* Canned Replies picker */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>Canned Quick Replies:</span>
                  <select
                    onChange={(e) => {
                      if (e.target.value) setReplyText(e.target.value);
                    }}
                    defaultValue=""
                    style={{
                      flex: 1,
                      padding: '4px 8px',
                      backgroundColor: 'var(--surface-dark)',
                      border: '1px solid var(--border-dark)',
                      color: 'var(--text-light)',
                      borderRadius: '6px',
                      fontSize: '11px',
                    }}
                  >
                    <option value="" disabled>-- Select a standard reply template --</option>
                    {cannedReplies.map((cr) => (
                      <option key={cr.id} value={cr.text}>
                        {cr.title}: "{cr.text.slice(0, 45)}..."
                      </option>
                    ))}
                  </select>
                </div>

                {/* Textarea and Send button */}
                <div style={{ display: 'flex', gap: '8px' }}>
                  <textarea
                    rows={2}
                    placeholder="Type audited agent response to customer..."
                    value={replyText}
                    onChange={(e) => setReplyText(e.target.value)}
                    style={{
                      flex: 1,
                      padding: '8px 12px',
                      backgroundColor: 'var(--surface-dark)',
                      border: '1px solid var(--border-dark)',
                      borderRadius: '8px',
                      color: 'var(--text-light)',
                      fontSize: '13px',
                      resize: 'none',
                    }}
                  />
                  <button
                    onClick={handleSendReply}
                    disabled={actionLoading || !replyText.trim()}
                    style={{
                      padding: '0 20px',
                      backgroundColor: 'var(--primary-accent)',
                      border: 'none',
                      borderRadius: '8px',
                      color: '#ffffff',
                      fontWeight: 600,
                      cursor: replyText.trim() ? 'pointer' : 'not-allowed',
                      opacity: replyText.trim() ? 1 : 0.5,
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                      fontSize: '13px',
                    }}
                  >
                    <Send size={14} />
                    Reply
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
