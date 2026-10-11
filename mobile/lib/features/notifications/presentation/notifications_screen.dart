import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.navNotifications ?? 'Notifications'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(16.0),
                children: [
                  Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: AppColors.primaryGreen.withOpacity(0.12),
                        child: const Icon(Icons.notifications_active_rounded, color: AppColors.primaryGreen),
                      ),
                      title: const Text(
                        'Transfer Successful',
                        style: TextStyle(fontWeight: FontWeight.w600),
                      ),
                      // Privacy-safe text per Section 12: no amounts or account digits on notification text
                      subtitle: const Text('Your outgoing fund transfer has been completed safely.'),
                      trailing: const Text('10m ago', style: TextStyle(fontSize: 12, color: Colors.grey)),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: AppColors.info.withOpacity(0.12),
                        child: const Icon(Icons.shield_outlined, color: AppColors.info),
                      ),
                      title: const Text(
                        'Security Notice',
                        style: TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: const Text('New sign-in detected on a trusted device.'),
                      trailing: const Text('2h ago', style: TextStyle(fontSize: 12, color: Colors.grey)),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
