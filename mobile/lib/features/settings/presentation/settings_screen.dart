import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';
import '../../auth/data/auth_service.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.navSettings ?? 'Settings'),
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
                    child: Column(
                      children: [
                        ListTile(
                          key: const Key('settings_privacy_tile'),
                          leading: const Icon(Icons.privacy_tip_outlined, color: AppColors.primaryGreen),
                          title: const Text('Privacy & Payment-ID'),
                          subtitle: const Text('Directory discovery & emergency freeze'),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () => context.push('/settings/privacy'),
                        ),
                        const Divider(height: 1),
                        ListTile(
                          key: const Key('settings_security_tile'),
                          leading: const Icon(Icons.security_rounded, color: AppColors.primaryGreen),
                          title: const Text('Security & Device Management'),
                          subtitle: const Text('Biometrics, PIN, active device sessions'),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () => context.push('/settings/security'),
                        ),
                        const Divider(height: 1),
                        ListTile(
                          leading: const Icon(Icons.account_balance_outlined, color: AppColors.primaryGreen),
                          title: const Text('Manage Bank Accounts'),
                          subtitle: const Text('Default payment account & receiving status'),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () => context.push('/accounts'),
                        ),
                        const Divider(height: 1),
                        ListTile(
                          leading: const Icon(Icons.language_rounded, color: AppColors.primaryGreen),
                          title: const Text('Language / භාෂාව / மொழி'),
                          subtitle: const Text('English (Default)'),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () {},
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  Card(
                    child: ListTile(
                      key: const Key('settings_sign_out_tile'),
                      leading: const Icon(Icons.logout_rounded, color: AppColors.error),
                      title: const Text(
                        'Sign Out',
                        style: TextStyle(
                          color: AppColors.error,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      onTap: () async {
                        await ref.read(authStateProvider.notifier).logout();
                        if (context.mounted) {
                          context.go('/welcome');
                        }
                      },
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
