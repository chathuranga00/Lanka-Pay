import React, { useState, useEffect } from 'react';
import {
  BookOpen,
  Plus,
  Eye,
  CheckCircle2,
  Archive,
  RefreshCw,
  Search,
  ExternalLink,
  X,
  Sparkles,
  MousePointerClick,
  Users,
} from 'lucide-react';
import type {
  Story,
  StoryAudience,
  StoryStatus,
  StoryPage,
  CreateStoryPayload,
} from '../types/admin';
import { adminApi } from '../services/adminApi';

const ALLOWED_DEEP_LINKS = [
  { label: 'Link Account (/accounts/add)', value: '/accounts/add' },
  { label: 'Privacy Settings (/settings/privacy)', value: '/settings/privacy' },
  { label: 'Security & PIN (/settings/security)', value: '/settings/security' },
  { label: 'Scan QR (/qr)', value: '/qr' },
  { label: 'Activity History (/history)', value: '/history' },
  { label: 'Rewards Program (/rewards)', value: '/rewards' },
  { label: 'Send Money (/send)', value: '/send' },
  { label: 'Explore Super App (/explore)', value: '/explore' },
];

const GRADIENT_PRESETS = [
  { label: 'Indigo & Blue', value: 'gradient:linear(135deg, #4F46E5, #3B82F6)' },
  { label: 'Emerald & Teal', value: 'gradient:linear(135deg, #10B981, #06B6D4)' },
  { label: 'Crimson & Amber', value: 'gradient:linear(135deg, #EF4444, #F59E0B)' },
  { label: 'Violet & Fuchsia', value: 'gradient:linear(135deg, #8B5CF6, #EC4899)' },
  { label: 'Sunset Orange', value: 'gradient:linear(135deg, #F97316, #EAB308)' },
  { label: 'Midnight Slate', value: 'gradient:linear(135deg, #1E293B, #0F172A)' },
];

export const StoriesManagerView: React.FC = () => {
  const [stories, setStories] = useState<Story[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [audienceFilter, setAudienceFilter] = useState<string>('ALL');

  // Preview Modal
  const [previewStory, setPreviewStory] = useState<Story | null>(null);
  const [previewPageIndex, setPreviewPageIndex] = useState(0);

  // Create Modal
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newCaption, setNewCaption] = useState('');
  const [newImageOrGradient, setNewImageOrGradient] = useState(GRADIENT_PRESETS[0].value);
  const [newAudience, setNewAudience] = useState<StoryAudience>('ALL');
  const [newPriority, setNewPriority] = useState<number>(100);
  const [newPublishNow, setNewPublishNow] = useState<boolean>(true);
  const [newPages, setNewPages] = useState<StoryPage[]>([
    {
      text: '',
      actionLabel: 'Learn More',
      deepLink: '/explore',
    },
  ]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const fetchStories = async () => {
    setLoading(true);
    try {
      const data = await adminApi.getStories();
      setStories(data);
    } catch (err: unknown) {
      console.error('Failed to load stories:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStories();
  }, []);

  const handlePublish = async (id: string) => {
    try {
      await adminApi.publishStory(id);
      await fetchStories();
    } catch (err: unknown) {
      alert(`Publish failed: ${(err as Error).message}`);
    }
  };

  const handleArchive = async (id: string) => {
    if (!confirm('Are you sure you want to archive this story? It will no longer be visible to users.')) {
      return;
    }
    try {
      await adminApi.archiveStory(id);
      await fetchStories();
    } catch (err: unknown) {
      alert(`Archive failed: ${(err as Error).message}`);
    }
  };

  const handleAddPage = () => {
    setNewPages([
      ...newPages,
      {
        text: '',
        actionLabel: 'Continue',
        deepLink: '/explore',
      },
    ]);
  };

  const handleRemovePage = (index: number) => {
    if (newPages.length <= 1) return;
    setNewPages(newPages.filter((_, i) => i !== index));
  };

  const handlePageChange = (index: number, field: keyof StoryPage, value: string) => {
    const updated = [...newPages];
    updated[index] = { ...updated[index], [field]: value };
    setNewPages(updated);
  };

  const handleCreateStory = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionError(null);

    // Validation
    if (!newTitle.trim()) {
      setActionError('Story title is required.');
      return;
    }
    if (!newCaption.trim()) {
      setActionError('Story caption is required.');
      return;
    }
    for (let i = 0; i < newPages.length; i++) {
      if (!newPages[i].text.trim()) {
        setActionError(`Page ${i + 1} text is required.`);
        return;
      }
    }

    const payload: CreateStoryPayload = {
      title: newTitle.trim(),
      caption: newCaption.trim(),
      imageOrGradient: newImageOrGradient,
      contentPages: newPages,
      audience: newAudience,
      priority: Number(newPriority) || 100,
      publishNow: newPublishNow,
    };

    setIsSubmitting(true);
    try {
      await adminApi.createStory(payload);
      setIsCreateOpen(false);
      // Reset form
      setNewTitle('');
      setNewCaption('');
      setNewPages([{ text: '', actionLabel: 'Learn More', deepLink: '/explore' }]);
      await fetchStories();
    } catch (err: unknown) {
      setActionError((err as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const filtered = stories.filter((s) => {
    const matchesSearch =
      s.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.caption.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.id.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || s.status === statusFilter;
    const matchesAudience = audienceFilter === 'ALL' || s.audience === audienceFilter;
    return matchesSearch && matchesStatus && matchesAudience;
  });

  const getStatusColor = (status: StoryStatus) => {
    switch (status) {
      case 'PUBLISHED':
        return { bg: 'rgba(27, 135, 85, 0.2)', text: 'var(--success)' };
      case 'DRAFT':
        return { bg: 'rgba(234, 179, 8, 0.2)', text: 'var(--warning)' };
      case 'ARCHIVED':
        return { bg: 'rgba(100, 116, 139, 0.2)', text: 'var(--text-secondary)' };
    }
  };

  const getGradientCss = (val?: string) => {
    if (!val) return 'linear-gradient(135deg, #1E293B, #0F172A)';
    if (val.startsWith('gradient:linear(')) {
      return val.replace('gradient:linear(', 'linear-gradient(');
    }
    if (val.startsWith('http://') || val.startsWith('https://')) {
      return `url(${val}) center/cover no-repeat`;
    }
    return 'linear-gradient(135deg, #4F46E5, #3B82F6)';
  };

  return (
    <div>
      {/* Top Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '8px' }}>
            <BookOpen size={22} color="var(--accent-gold)" /> In-App Stories & Nudges
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            Curate engaging full-screen stories, educate users on security & features, and target specific audiences.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '12px' }}>
          <button
            onClick={fetchStories}
            disabled={loading}
            style={{
              padding: '10px 16px',
              backgroundColor: 'var(--surface-dark)',
              border: '1px solid var(--border-dark)',
              borderRadius: '10px',
              color: 'var(--text-primary)',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '13px',
              fontWeight: 600,
            }}
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
          <button
            onClick={() => setIsCreateOpen(true)}
            style={{
              padding: '10px 16px',
              backgroundColor: 'var(--primary-green)',
              border: 'none',
              borderRadius: '10px',
              color: '#fff',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '13px',
              fontWeight: 700,
            }}
          >
            <Plus size={16} /> Create Story
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <div style={{
        display: 'flex',
        gap: '12px',
        marginBottom: '20px',
        backgroundColor: 'var(--surface-dark)',
        padding: '16px',
        borderRadius: '16px',
        border: '1px solid var(--border-dark)',
        alignItems: 'center',
      }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <Search size={18} color="var(--text-muted)" style={{ position: 'absolute', left: '12px', top: '12px' }} />
          <input
            type="text"
            placeholder="Search stories by title, caption, or ID..."
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
            padding: '10px 14px',
            backgroundColor: 'var(--card-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: '10px',
            fontSize: '13px',
            color: 'var(--text-primary)',
          }}
        >
          <option value="ALL">All Statuses</option>
          <option value="PUBLISHED">Published</option>
          <option value="DRAFT">Draft</option>
          <option value="ARCHIVED">Archived</option>
        </select>

        <select
          value={audienceFilter}
          onChange={(e) => setAudienceFilter(e.target.value)}
          style={{
            padding: '10px 14px',
            backgroundColor: 'var(--card-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: '10px',
            fontSize: '13px',
            color: 'var(--text-primary)',
          }}
        >
          <option value="ALL">All Audiences</option>
          <option value="NEW_USERS">New Users</option>
          <option value="HAS_CARD">Has Card</option>
          <option value="NO_CARD">No Card</option>
          <option value="HAS_BANK_ACCOUNT">Has Bank Account</option>
          <option value="NO_BANK_ACCOUNT">No Bank Account</option>
        </select>
      </div>

      {/* Stories Table */}
      <div style={{
        backgroundColor: 'var(--surface-dark)',
        borderRadius: '16px',
        border: '1px solid var(--border-dark)',
        overflow: 'hidden',
      }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border-dark)', color: 'var(--text-secondary)' }}>
              <th style={{ padding: '16px' }}>Story</th>
              <th style={{ padding: '16px' }}>Status</th>
              <th style={{ padding: '16px' }}>Audience</th>
              <th style={{ padding: '16px' }}>Pages</th>
              <th style={{ padding: '16px' }}>Clicks</th>
              <th style={{ padding: '16px' }}>Priority</th>
              <th style={{ padding: '16px', textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ padding: '32px', textAlign: 'center', color: 'var(--text-secondary)' }}>
                  {loading ? 'Loading stories...' : 'No stories found matching filters.'}
                </td>
              </tr>
            ) : (
              filtered.map((story) => {
                const sColor = getStatusColor(story.status);
                return (
                  <tr
                    key={story.id}
                    style={{
                      borderBottom: '1px solid var(--border-dark)',
                      transition: 'background-color 0.2s',
                    }}
                  >
                    <td style={{ padding: '16px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div
                          style={{
                            width: '40px',
                            height: '40px',
                            borderRadius: '50%',
                            background: getGradientCss(story.imageOrGradient),
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
                            flexShrink: 0,
                          }}
                        >
                          <Sparkles size={18} color="#ffffff" />
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, color: 'var(--text-primary)' }}>{story.title}</div>
                          <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                            {story.caption} • <code style={{ color: 'var(--accent-gold)' }}>{story.id}</code>
                          </div>
                        </div>
                      </div>
                    </td>

                    <td style={{ padding: '16px' }}>
                      <span
                        style={{
                          padding: '4px 10px',
                          borderRadius: '12px',
                          fontSize: '11px',
                          fontWeight: 700,
                          backgroundColor: sColor.bg,
                          color: sColor.text,
                        }}
                      >
                        {story.status}
                      </span>
                    </td>

                    <td style={{ padding: '16px' }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                          fontSize: '12px',
                          color: 'var(--text-primary)',
                        }}
                      >
                        <Users size={14} color="var(--accent-gold)" />
                        {story.audience.replace(/_/g, ' ')}
                      </span>
                    </td>

                    <td style={{ padding: '16px', color: 'var(--text-secondary)' }}>
                      {story.contentPages?.length || 0} page(s)
                    </td>

                    <td style={{ padding: '16px' }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                          fontWeight: 600,
                          color: 'var(--text-primary)',
                        }}
                      >
                        <MousePointerClick size={14} color="var(--primary-green-light)" />
                        {story.actionClickCount || 0}
                      </span>
                    </td>

                    <td style={{ padding: '16px', color: 'var(--text-secondary)', fontWeight: 600 }}>
                      {story.priority}
                    </td>

                    <td style={{ padding: '16px', textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '8px' }}>
                        <button
                          onClick={() => {
                            setPreviewStory(story);
                            setPreviewPageIndex(0);
                          }}
                          title="Preview Story in Mobile View"
                          style={{
                            padding: '6px 10px',
                            backgroundColor: 'rgba(59, 130, 246, 0.15)',
                            color: '#60A5FA',
                            border: '1px solid rgba(59, 130, 246, 0.3)',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                            fontSize: '12px',
                            fontWeight: 600,
                          }}
                        >
                          <Eye size={14} /> Preview
                        </button>

                        {story.status !== 'PUBLISHED' && (
                          <button
                            onClick={() => handlePublish(story.id)}
                            title="Publish Story"
                            style={{
                              padding: '6px 10px',
                              backgroundColor: 'rgba(27, 135, 85, 0.15)',
                              color: 'var(--success)',
                              border: '1px solid rgba(27, 135, 85, 0.3)',
                              borderRadius: '8px',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '4px',
                              fontSize: '12px',
                              fontWeight: 600,
                            }}
                          >
                            <CheckCircle2 size={14} /> Publish
                          </button>
                        )}

                        {story.status !== 'ARCHIVED' && (
                          <button
                            onClick={() => handleArchive(story.id)}
                            title="Archive Story"
                            style={{
                              padding: '6px 10px',
                              backgroundColor: 'rgba(239, 68, 68, 0.15)',
                              color: 'var(--error)',
                              border: '1px solid rgba(239, 68, 68, 0.3)',
                              borderRadius: '8px',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '4px',
                              fontSize: '12px',
                              fontWeight: 600,
                            }}
                          >
                            <Archive size={14} /> Archive
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* ── Interactive Mobile Preview Modal ─────────────────────────────── */}
      {previewStory && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.85)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          backdropFilter: 'blur(8px)',
        }}>
          <div style={{ position: 'relative' }}>
            {/* Close Button */}
            <button
              onClick={() => setPreviewStory(null)}
              style={{
                position: 'absolute',
                top: '-48px',
                right: '0',
                backgroundColor: 'rgba(255, 255, 255, 0.15)',
                border: 'none',
                color: '#fff',
                borderRadius: '50%',
                width: '36px',
                height: '36px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
              }}
            >
              <X size={20} />
            </button>

            {/* Simulated Phone Shell */}
            <div style={{
              width: '340px',
              height: '680px',
              backgroundColor: '#0F172A',
              borderRadius: '44px',
              border: '10px solid #1E293B',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.8), 0 0 40px rgba(16, 185, 129, 0.2)',
              position: 'relative',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
              background: getGradientCss(previewStory.imageOrGradient),
            }}>
              {/* Phone Notch */}
              <div style={{
                position: 'absolute',
                top: 0,
                left: '50%',
                transform: 'translateX(-50%)',
                width: '120px',
                height: '24px',
                backgroundColor: '#1E293B',
                borderBottomLeftRadius: '16px',
                borderBottomRightRadius: '16px',
                zIndex: 20,
              }} />

              {/* Story Header & Segmented Progress Bars */}
              <div style={{ padding: '36px 16px 12px', zIndex: 10 }}>
                <div style={{ display: 'flex', gap: '4px', marginBottom: '12px' }}>
                  {previewStory.contentPages.map((_, idx) => (
                    <div
                      key={idx}
                      style={{
                        flex: 1,
                        height: '3px',
                        backgroundColor: idx <= previewPageIndex ? '#ffffff' : 'rgba(255, 255, 255, 0.3)',
                        borderRadius: '2px',
                        transition: 'background-color 0.3s',
                      }}
                    />
                  ))}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <div style={{
                      width: '28px',
                      height: '28px',
                      borderRadius: '50%',
                      backgroundColor: 'rgba(255,255,255,0.2)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}>
                      <Sparkles size={14} color="#fff" />
                    </div>
                    <div>
                      <div style={{ fontSize: '12px', fontWeight: 700, color: '#fff' }}>{previewStory.title}</div>
                      <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.8)' }}>{previewStory.caption}</div>
                    </div>
                  </div>
                  <button
                    onClick={() => setPreviewStory(null)}
                    style={{ background: 'none', border: 'none', color: '#fff', cursor: 'pointer', padding: '4px' }}
                  >
                    <X size={16} />
                  </button>
                </div>
              </div>

              {/* Story Content Area with Left/Right Tap Areas */}
              <div style={{ flex: 1, position: 'relative', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '24px' }}>
                {/* Left Tap Zone */}
                <div
                  onClick={() => setPreviewPageIndex((p) => Math.max(0, p - 1))}
                  style={{
                    position: 'absolute',
                    top: 0,
                    bottom: 0,
                    left: 0,
                    width: '35%',
                    cursor: 'pointer',
                    zIndex: 5,
                  }}
                  title="Tap left to rewind"
                />

                {/* Right Tap Zone */}
                <div
                  onClick={() => setPreviewPageIndex((p) => Math.min(previewStory.contentPages.length - 1, p + 1))}
                  style={{
                    position: 'absolute',
                    top: 0,
                    bottom: 0,
                    right: 0,
                    width: '65%',
                    cursor: 'pointer',
                    zIndex: 5,
                  }}
                  title="Tap right to advance"
                />

                {/* Text Content */}
                <div style={{ position: 'relative', zIndex: 6, pointerEvents: 'none' }}>
                  <p style={{
                    fontSize: '18px',
                    fontWeight: 600,
                    lineHeight: '1.5',
                    color: '#ffffff',
                    textShadow: '0 2px 8px rgba(0,0,0,0.5)',
                  }}>
                    {previewStory.contentPages[previewPageIndex]?.text}
                  </p>
                </div>
              </div>

              {/* Story Action Button */}
              {previewStory.contentPages[previewPageIndex]?.actionLabel && (
                <div style={{ padding: '20px 24px 32px', zIndex: 10 }}>
                  <button
                    onClick={() => {
                      alert(`Action triggered!\nDeep-link target: ${previewStory.contentPages[previewPageIndex]?.deepLink || 'N/A'}`);
                    }}
                    style={{
                      width: '100%',
                      padding: '14px',
                      backgroundColor: '#ffffff',
                      color: '#0F172A',
                      border: 'none',
                      borderRadius: '16px',
                      fontSize: '14px',
                      fontWeight: 800,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '8px',
                      boxShadow: '0 4px 14px rgba(0,0,0,0.4)',
                    }}
                  >
                    <span>{previewStory.contentPages[previewPageIndex]?.actionLabel}</span>
                    <ExternalLink size={15} />
                  </button>
                  <div style={{ fontSize: '10px', textAlign: 'center', color: 'rgba(255,255,255,0.7)', marginTop: '8px' }}>
                    Route: <code>{previewStory.contentPages[previewPageIndex]?.deepLink}</code>
                  </div>
                </div>
              )}
            </div>

            {/* Instruction tooltip below preview */}
            <div style={{
              textAlign: 'center',
              color: 'var(--text-secondary)',
              fontSize: '12px',
              marginTop: '12px',
            }}>
              Page {previewPageIndex + 1} of {previewStory.contentPages.length} • Tap left to rewind, tap right to advance
            </div>
          </div>
        </div>
      )}

      {/* ── Create Story Modal ───────────────────────────────────────────── */}
      {isCreateOpen && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.75)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: '20px',
        }}>
          <div style={{
            backgroundColor: 'var(--surface-dark)',
            borderRadius: '20px',
            border: '1px solid var(--border-dark)',
            width: '100%',
            maxWidth: '680px',
            maxHeight: '90vh',
            display: 'flex',
            flexDirection: 'column',
            boxShadow: '0 20px 40px rgba(0,0,0,0.5)',
          }}>
            {/* Modal Header */}
            <div style={{
              padding: '20px 24px',
              borderBottom: '1px solid var(--border-dark)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}>
              <div>
                <h3 style={{ fontSize: '18px', fontWeight: 800 }}>Create New Story</h3>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                  Plain text only. Safe allowlisted deep links and gradient assets enforced.
                </p>
              </div>
              <button
                onClick={() => setIsCreateOpen(false)}
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Body */}
            <form onSubmit={handleCreateStory} style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
              <div style={{ padding: '24px', overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {actionError && (
                  <div style={{
                    padding: '12px',
                    borderRadius: '8px',
                    backgroundColor: 'rgba(239, 68, 68, 0.15)',
                    border: '1px solid var(--error)',
                    color: 'var(--error)',
                    fontSize: '13px',
                  }}>
                    {actionError}
                  </div>
                )}

                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                    Title *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Try QR Payments"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    style={{
                      width: '100%',
                      padding: '10px 12px',
                      backgroundColor: 'var(--card-dark)',
                      border: '1px solid var(--border-dark)',
                      borderRadius: '8px',
                      color: 'var(--text-primary)',
                      fontSize: '13px',
                    }}
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                    Caption *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Instant LankaQR merchant pay"
                    value={newCaption}
                    onChange={(e) => setNewCaption(e.target.value)}
                    style={{
                      width: '100%',
                      padding: '10px 12px',
                      backgroundColor: 'var(--card-dark)',
                      border: '1px solid var(--border-dark)',
                      borderRadius: '8px',
                      color: 'var(--text-primary)',
                      fontSize: '13px',
                    }}
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                      Visual Style / Preset
                    </label>
                    <select
                      value={newImageOrGradient}
                      onChange={(e) => setNewImageOrGradient(e.target.value)}
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        backgroundColor: 'var(--card-dark)',
                        border: '1px solid var(--border-dark)',
                        borderRadius: '8px',
                        color: 'var(--text-primary)',
                        fontSize: '13px',
                      }}
                    >
                      {GRADIENT_PRESETS.map((p) => (
                        <option key={p.value} value={p.value}>
                          {p.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                      Target Audience
                    </label>
                    <select
                      value={newAudience}
                      onChange={(e) => setNewAudience(e.target.value as StoryAudience)}
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        backgroundColor: 'var(--card-dark)',
                        border: '1px solid var(--border-dark)',
                        borderRadius: '8px',
                        color: 'var(--text-primary)',
                        fontSize: '13px',
                      }}
                    >
                      <option value="ALL">All Users</option>
                      <option value="NEW_USERS">New Users (Registered ≤ 7 days)</option>
                      <option value="HAS_CARD">Has Card</option>
                      <option value="NO_CARD">No Card</option>
                      <option value="HAS_BANK_ACCOUNT">Has Bank Account</option>
                      <option value="NO_BANK_ACCOUNT">No Bank Account</option>
                    </select>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                      Priority Order (Lower = First)
                    </label>
                    <input
                      type="number"
                      value={newPriority}
                      onChange={(e) => setNewPriority(parseInt(e.target.value, 10))}
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        backgroundColor: 'var(--card-dark)',
                        border: '1px solid var(--border-dark)',
                        borderRadius: '8px',
                        color: 'var(--text-primary)',
                        fontSize: '13px',
                      }}
                    />
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', paddingTop: '24px' }}>
                    <input
                      type="checkbox"
                      id="publishNowCheck"
                      checked={newPublishNow}
                      onChange={(e) => setNewPublishNow(e.target.checked)}
                      style={{ width: '16px', height: '16px' }}
                    />
                    <label htmlFor="publishNowCheck" style={{ fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
                      Publish Immediately
                    </label>
                  </div>
                </div>

                {/* Content Pages Section */}
                <div style={{ borderTop: '1px solid var(--border-dark)', paddingTop: '16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <label style={{ fontSize: '13px', fontWeight: 700 }}>Story Content Pages ({newPages.length})</label>
                    <button
                      type="button"
                      onClick={handleAddPage}
                      style={{
                        fontSize: '12px',
                        color: 'var(--accent-gold)',
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        fontWeight: 700,
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                      }}
                    >
                      <Plus size={14} /> Add Another Page
                    </button>
                  </div>

                  {newPages.map((page, index) => (
                    <div
                      key={index}
                      style={{
                        backgroundColor: 'var(--card-dark)',
                        borderRadius: '12px',
                        padding: '16px',
                        marginBottom: '12px',
                        border: '1px solid var(--border-dark)',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                        <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--accent-gold)' }}>
                          Page {index + 1}
                        </span>
                        {newPages.length > 1 && (
                          <button
                            type="button"
                            onClick={() => handleRemovePage(index)}
                            style={{
                              background: 'none',
                              border: 'none',
                              color: 'var(--error)',
                              cursor: 'pointer',
                              fontSize: '11px',
                            }}
                          >
                            Remove
                          </button>
                        )}
                      </div>

                      <div style={{ marginBottom: '10px' }}>
                        <textarea
                          placeholder="Page text content (plain text only)..."
                          required
                          value={page.text}
                          onChange={(e) => handlePageChange(index, 'text', e.target.value)}
                          rows={2}
                          style={{
                            width: '100%',
                            padding: '8px 10px',
                            backgroundColor: 'var(--surface-dark)',
                            border: '1px solid var(--border-dark)',
                            borderRadius: '8px',
                            color: 'var(--text-primary)',
                            fontSize: '12px',
                            resize: 'vertical',
                          }}
                        />
                      </div>

                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                        <div>
                          <input
                            type="text"
                            placeholder="Button Label (e.g. Try Now)"
                            value={page.actionLabel || ''}
                            onChange={(e) => handlePageChange(index, 'actionLabel', e.target.value)}
                            style={{
                              width: '100%',
                              padding: '8px 10px',
                              backgroundColor: 'var(--surface-dark)',
                              border: '1px solid var(--border-dark)',
                              borderRadius: '8px',
                              color: 'var(--text-primary)',
                              fontSize: '12px',
                            }}
                          />
                        </div>

                        <div>
                          <select
                            value={page.deepLink || '/explore'}
                            onChange={(e) => handlePageChange(index, 'deepLink', e.target.value)}
                            style={{
                              width: '100%',
                              padding: '8px 10px',
                              backgroundColor: 'var(--surface-dark)',
                              border: '1px solid var(--border-dark)',
                              borderRadius: '8px',
                              color: 'var(--text-primary)',
                              fontSize: '12px',
                            }}
                          >
                            {ALLOWED_DEEP_LINKS.map((d) => (
                              <option key={d.value} value={d.value}>
                                {d.label}
                              </option>
                            ))}
                          </select>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Modal Footer */}
              <div style={{
                padding: '16px 24px',
                borderTop: '1px solid var(--border-dark)',
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '12px',
              }}>
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  style={{
                    padding: '10px 16px',
                    backgroundColor: 'var(--surface-dark)',
                    border: '1px solid var(--border-dark)',
                    borderRadius: '8px',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer',
                    fontSize: '13px',
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  style={{
                    padding: '10px 20px',
                    backgroundColor: 'var(--primary-green)',
                    border: 'none',
                    borderRadius: '8px',
                    color: '#fff',
                    fontWeight: 700,
                    cursor: 'pointer',
                    fontSize: '13px',
                  }}
                >
                  {isSubmitting ? 'Saving...' : 'Create & Save Story'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
