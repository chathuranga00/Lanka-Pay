import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/storage/token_storage.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

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
                          leading: const Icon(Icons.language_rounded, color: AppColors.primaryGreen),
                          title: const Text('Language / භාෂාව / மொழி'),
                          subtitle: const Text('English (Default)'),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () {},
                        ),
                        const Divider(height: 1),
                        ListTile(
                          leading: const Icon(Icons.dark_mode_outlined, color: AppColors.primaryGreen),
                          title: const Text('Theme Mode'),
                          subtitle: Text(isDark ? 'Dark Mode' : 'Light Mode'),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () {},
                        ),
                        const Divider(height: 1),
                        ListTile(
                          leading: const Icon(Icons.lock_outline_rounded, color: AppColors.primaryGreen),
                          title: const Text('Security & Transfer PIN'),
                          subtitle: const Text('Change 6-digit transaction PIN'),
                          trailing: const Icon(Icons.chevron_right_rounded),
                          onTap: () {},
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.logout_rounded, color: AppColors.error),
                      title: const Text(
                        'Sign Out',
                        style: TextStyle(
                          color: AppColors.error,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      onTap: () async {
                        final tokenStorage = ref.read(tokenStorageProvider);
                        await tokenStorage.clearAll();
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
