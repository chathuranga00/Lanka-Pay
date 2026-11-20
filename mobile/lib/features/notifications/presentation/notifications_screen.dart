import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/error_retry.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../data/notification_service.dart';
import '../domain/notification_models.dart';

class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  List<NotificationItemModel> _notifications = [];
  bool _isLoadingNotifs = false;
  String? _notifsError;

  NotificationPreferencesModel _prefs = const NotificationPreferencesModel();
  bool _isLoadingPrefs = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadNotifications();
    _loadPreferences();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadNotifications() async {
    setState(() {
      _isLoadingNotifs = true;
      _notifsError = null;
    });

    try {
      final items = await ref.read(notificationServiceProvider).getNotifications();
      if (mounted) {
        setState(() {
          _notifications = items;
          _isLoadingNotifs = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoadingNotifs = false;
          _notifsError = 'Could not load notifications.';
        });
      }
    }
  }

  Future<void> _loadPreferences() async {
    setState(() => _isLoadingPrefs = true);
    try {
      final p = await ref.read(notificationServiceProvider).getPreferences();
      if (mounted) {
        setState(() {
          _prefs = p;
          _isLoadingPrefs = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isLoadingPrefs = false);
    }
  }

  Future<void> _updatePreference(NotificationPreferencesModel newPrefs) async {
    HapticFeedback.lightImpact();
    setState(() => _prefs = newPrefs);
    try {
      final updated = await ref.read(notificationServiceProvider).updatePreferences(newPrefs);
      if (mounted) setState(() => _prefs = updated);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to update notification settings')),
        );
      }
    }
  }

  Future<void> _markAllRead() async {
    HapticFeedback.lightImpact();
    try {
      await ref.read(notificationServiceProvider).markAllAsRead();
      _loadNotifications();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('All notifications marked as read')),
        );
      }
    } catch (_) {
      // Ignored
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: Column(
        children: [
          const SimulatedBanner(),
          Expanded(
            child: GradientHeaderScaffold(
              scrollable: false,
              bodyPadding: EdgeInsets.zero,
              header: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          if (Navigator.of(context).canPop())
                            IconButton(
                              icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
                              tooltip: 'Back',
                              onPressed: () => Navigator.of(context).pop(),
                            ),
                          Text(
                            'Notifications',
                            style: TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 24,
                              fontWeight: FontWeight.w800,
                              color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                            ),
                          ),
                        ],
                      ),
                      IconButton(
                        key: const Key('notifs_mark_all_read_btn'),
                        icon: const Icon(Icons.done_all_rounded),
                        tooltip: 'Mark all as read',
                        onPressed: _markAllRead,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  // Segmented Tabs Pill
                  Container(
                    height: 44,
                    decoration: BoxDecoration(
                      color: isDark
                          ? AppTokens.mutedSurfaceDark.withOpacity(0.6)
                          : Colors.white.withOpacity(0.5),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    padding: const EdgeInsets.all(4),
                    child: TabBar(
                      controller: _tabController,
                      indicator: BoxDecoration(
                        color: isDark ? AppTokens.cardDark : Colors.white,
                        borderRadius: BorderRadius.circular(10),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.08),
                            blurRadius: 4,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      labelColor: isDark ? Colors.white : AppTokens.textPrimaryLight,
                      unselectedLabelColor:
                          isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      labelStyle: const TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                      ),
                      tabs: const [
                        Tab(text: 'Inbox'),
                        Tab(text: 'Preferences'),
                      ],
                    ),
                  ),
                ],
              ),
              body: TabBarView(
                controller: _tabController,
                children: [
                  _buildInboxTab(isDark),
                  _buildPreferencesTab(isDark),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInboxTab(bool isDark) {
    if (_isLoadingNotifs) {
      return const Center(
        child: CircularProgressIndicator(color: AppTokens.primaryViolet),
      );
    }

    if (_notifsError != null) {
      return ErrorRetry(
        message: _notifsError!,
        onRetry: _loadNotifications,
      );
    }

    if (_notifications.isEmpty) {
      return const EmptyState(
        icon: Icons.notifications_none_rounded,
        title: 'No new notifications',
        description: 'You are all caught up with your transaction alerts.',
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.symmetric(
        horizontal: AppTokens.screenHorizontalPadding,
        vertical: 16.0,
      ),
      itemCount: _notifications.length,
      separatorBuilder: (_, index) => Divider(
        height: 1,
        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
      ),
      itemBuilder: (context, index) {
        final item = _notifications[index];
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 10.0),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: AppTokens.navyIconTile,
                  borderRadius: BorderRadius.circular(14),
                ),
                alignment: Alignment.center,
                child: Icon(
                  item.type == 'TRANSFER'
                      ? Icons.swap_horiz_rounded
                      : Icons.security_rounded,
                  color: Colors.white,
                  size: 22,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Text(
                            item.title,
                            style: TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                            ),
                          ),
                        ),
                        if (!item.read)
                          Container(
                            width: 8,
                            height: 8,
                            decoration: const BoxDecoration(
                              color: AppTokens.primaryViolet,
                              shape: BoxShape.circle,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      item.message,
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 13,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        height: 1.3,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      item.createdAt ?? '',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 11,
                        color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildPreferencesTab(bool isDark) {
    if (_isLoadingPrefs) {
      return const Center(
        child: CircularProgressIndicator(color: AppTokens.primaryViolet),
      );
    }

    return ListView(
      padding: const EdgeInsets.symmetric(
        horizontal: AppTokens.screenHorizontalPadding,
        vertical: 20.0,
      ),
      children: [
        Card(
          elevation: 0,
          color: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: BorderSide(color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Notification Channels',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: Colors.grey,
                  ),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  key: const Key('prefs_push_switch'),
                  contentPadding: EdgeInsets.zero,
                  activeColor: AppTokens.primaryViolet,
                  title: const Text(
                    'Push Notifications',
                    style: TextStyle(fontFamily: AppTokens.fontFamily, fontWeight: FontWeight.w700),
                  ),
                  subtitle: const Text(
                    'Instant alert pops when receiving transfers or requests',
                    style: TextStyle(fontFamily: AppTokens.fontFamily, fontSize: 12),
                  ),
                  value: _prefs.pushEnabled,
                  onChanged: (val) {
                    _updatePreference(_prefs.copyWith(pushEnabled: val));
                  },
                ),
                Divider(height: 1, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                SwitchListTile(
                  key: const Key('prefs_sms_switch'),
                  contentPadding: EdgeInsets.zero,
                  activeColor: AppTokens.primaryViolet,
                  title: const Text(
                    'SMS Notifications',
                    style: TextStyle(fontFamily: AppTokens.fontFamily, fontWeight: FontWeight.w700),
                  ),
                  subtitle: const Text(
                    'Receive SMS confirmation for completed debits and credits',
                    style: TextStyle(fontFamily: AppTokens.fontFamily, fontSize: 12),
                  ),
                  value: _prefs.smsEnabled,
                  onChanged: (val) {
                    _updatePreference(_prefs.copyWith(smsEnabled: val));
                  },
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Card(
          elevation: 0,
          color: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: BorderSide(color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'ALERT CATEGORIES',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: Colors.grey,
                  ),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  key: const Key('prefs_tx_alerts_switch'),
                  contentPadding: EdgeInsets.zero,
                  activeColor: AppTokens.primaryViolet,
                  title: const Text(
                    'Transaction Alerts',
                    style: TextStyle(fontFamily: AppTokens.fontFamily, fontWeight: FontWeight.w700),
                  ),
                  subtitle: const Text(
                    'Real-time alerts for payment sent and funds received',
                    style: TextStyle(fontFamily: AppTokens.fontFamily, fontSize: 12),
                  ),
                  value: _prefs.transactionAlerts,
                  onChanged: (val) {
                    _updatePreference(_prefs.copyWith(transactionAlerts: val));
                  },
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
