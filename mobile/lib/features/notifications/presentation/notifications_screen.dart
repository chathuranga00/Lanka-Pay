import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../data/notification_service.dart';
import '../domain/notification_models.dart';

class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen> with SingleTickerProviderStateMixin {
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
    setState(() => _prefs = newPrefs);
    try {
      final updated = await ref.read(notificationServiceProvider).updatePreferences(newPrefs);
      if (mounted) setState(() => _prefs = updated);
    } catch (_) {
      // Revert if failed
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to update notification settings')),
        );
      }
    }
  }

  Future<void> _markAllRead() async {
    try {
      await ref.read(notificationServiceProvider).markAllAsRead();
      _loadNotifications();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('All notifications marked as read')),
        );
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications & Alerts'),
        actions: [
          IconButton(
            key: const Key('notifs_mark_all_read_btn'),
            icon: const Icon(Icons.done_all_rounded),
            tooltip: 'Mark All as Read',
            onPressed: _markAllRead,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: isDark ? AppColors.accentGoldLight : AppColors.primaryGreen,
          unselectedLabelColor: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
          indicatorColor: AppColors.primaryGreen,
          tabs: const [
            Tab(text: 'Inbox'),
            Tab(text: 'Preferences'),
          ],
        ),
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: TabBarView(
                controller: _tabController,
                children: [
                  _buildInboxTab(isDark),
                  _buildPreferencesTab(isDark),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInboxTab(bool isDark) {
    if (_isLoadingNotifs) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_notifsError != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_notifsError!),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: _loadNotifications,
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    if (_notifications.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.notifications_off_outlined, size: 54, color: isDark ? Colors.white24 : Colors.black26),
            const SizedBox(height: 12),
            Text(
              'No new alerts or notifications',
              style: TextStyle(
                color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
              ),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16.0),
      itemCount: _notifications.length,
      itemBuilder: (context, index) {
        final item = _notifications[index];
        final isSecurity = item.type == 'SECURITY';

        return Card(
          margin: const EdgeInsets.only(bottom: 10),
          child: ListTile(
            leading: CircleAvatar(
              backgroundColor: isSecurity
                  ? AppColors.info.withOpacity(0.12)
                  : AppColors.primaryGreen.withOpacity(0.12),
              child: Icon(
                isSecurity ? Icons.shield_outlined : Icons.notifications_active_rounded,
                color: isSecurity ? AppColors.info : AppColors.primaryGreen,
              ),
            ),
            title: Text(
              item.title,
              style: TextStyle(
                fontWeight: item.read ? FontWeight.w500 : FontWeight.w700,
                fontSize: 14,
              ),
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 4),
                // Lockscreen privacy safe: no amount or account digits
                Text(item.message, style: const TextStyle(fontSize: 13)),
                if (item.createdAt != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    item.createdAt!.substring(0, 10),
                    style: TextStyle(
                      fontSize: 11,
                      color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                    ),
                  ),
                ],
              ],
            ),
            trailing: item.read
                ? null
                : Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: AppColors.primaryGreen,
                      shape: BoxShape.circle,
                    ),
                  ),
            onTap: () async {
              if (!item.read) {
                await ref.read(notificationServiceProvider).markAsRead(item.id);
                _loadNotifications();
              }
            },
          ),
        );
      },
    );
  }

  Widget _buildPreferencesTab(bool isDark) {
    if (_isLoadingPrefs) {
      return const Center(child: CircularProgressIndicator());
    }

    return ListView(
      padding: const EdgeInsets.all(20.0),
      children: [
        Text(
          'Notification Channels',
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
            color: isDark ? Colors.white : AppColors.textPrimaryLight,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          'Choose how you receive transfer alerts and security notifications.',
          style: TextStyle(
            fontSize: 13,
            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 16),
        Card(
          child: Column(
            children: [
              SwitchListTile(
                key: const Key('prefs_in_app_switch'),
                title: const Text('In-App Notifications'),
                subtitle: const Text('Receive inbox banners and in-app alerts'),
                value: _prefs.inAppEnabled,
                activeColor: AppColors.primaryGreen,
                onChanged: (val) => _updatePreference(_prefs.copyWith(inAppEnabled: val)),
              ),
              const Divider(height: 1),
              SwitchListTile(
                key: const Key('prefs_push_switch'),
                title: const Text('Push Notifications'),
                subtitle: const Text('Privacy-safe lock screen alerts'),
                value: _prefs.pushEnabled,
                activeColor: AppColors.primaryGreen,
                onChanged: (val) => _updatePreference(_prefs.copyWith(pushEnabled: val)),
              ),
              const Divider(height: 1),
              SwitchListTile(
                key: const Key('prefs_sms_switch'),
                title: const Text('SMS Alerts (SIMULATED)'),
                subtitle: const Text('Critical transaction confirmation via SMS'),
                value: _prefs.smsEnabled,
                activeColor: AppColors.primaryGreen,
                onChanged: (val) => _updatePreference(_prefs.copyWith(smsEnabled: val)),
              ),
              const Divider(height: 1),
              SwitchListTile(
                key: const Key('prefs_email_switch'),
                title: const Text('Email Receipts (SIMULATED)'),
                subtitle: const Text('Monthly statements and formal receipts'),
                value: _prefs.emailEnabled,
                activeColor: AppColors.primaryGreen,
                onChanged: (val) => _updatePreference(_prefs.copyWith(emailEnabled: val)),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text(
          'Alert Types',
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
            color: isDark ? Colors.white : AppColors.textPrimaryLight,
          ),
        ),
        const SizedBox(height: 16),
        Card(
          child: Column(
            children: [
              SwitchListTile(
                key: const Key('prefs_tx_alerts_switch'),
                title: const Text('Transaction Alerts'),
                subtitle: const Text('Debit and credit transfer status updates'),
                value: _prefs.transactionAlerts,
                activeColor: AppColors.primaryGreen,
                onChanged: (val) => _updatePreference(_prefs.copyWith(transactionAlerts: val)),
              ),
              const Divider(height: 1),
              SwitchListTile(
                key: const Key('prefs_sec_alerts_switch'),
                title: const Text('Security Alerts'),
                subtitle: const Text('New logins, password/PIN changes, risk flags'),
                value: _prefs.securityAlerts,
                activeColor: AppColors.primaryGreen,
                onChanged: (val) => _updatePreference(_prefs.copyWith(securityAlerts: val)),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
