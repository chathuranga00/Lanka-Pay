import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_theme.dart';
import '../../../../core/theme/app_tokens.dart';
import '../../../../core/theme/theme_controller.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../auth/data/auth_service.dart';
import '../../../auth/domain/auth_state.dart';
import '../../../home/data/home_providers.dart';
import '../../../settings/presentation/privacy_settings_screen.dart';

/// Modal bottom sheet displaying profile options, payment-ID QR, settings,
/// privacy, security, devices, language, appearance, and log out.
class AvatarSheet extends ConsumerWidget {
  const AvatarSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const AvatarSheet(),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final profile = ref.watch(paymentProfileProvider);
    final authState = ref.watch(authStateProvider);
    final phone = (authState is AuthAuthenticated ? authState.phone : null) ?? '+94 77 123 4567';
    final safeName = profile.safeName ?? 'Kasun Perera';

    return Material(
      key: const Key('avatar_sheet'),
      color: isDark ? AppTokens.cardDark : Colors.white,
      borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
      clipBehavior: Clip.antiAlias,
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Grab handle
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  decoration: BoxDecoration(
                    color: isDark ? Colors.white24 : Colors.black12,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),

              // Profile Header
              Row(
                children: [
                  AppAvatar(
                    name: safeName,
                    size: 56,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          safeName,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              decoration: BoxDecoration(
                                color: AppTokens.success.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.verified_rounded, size: 12, color: AppTokens.success),
                                  const SizedBox(width: 4),
                                  Text(
                                    phone,
                                    style: const TextStyle(
                                      fontFamily: AppTokens.fontFamily,
                                      fontSize: 11,
                                      fontWeight: FontWeight.w600,
                                      color: AppTokens.success,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Payment ID QR card
              InkWell(
                key: const Key('profile_payment_qr'),
                onTap: () {
                  Navigator.of(context).pop();
                  context.push('/qr');
                },
                borderRadius: BorderRadius.circular(16),
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: isDark ? AppTokens.cardDark : AppTokens.tileTintLight,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: isDark ? AppTokens.borderDark : AppTokens.primaryBlue.withValues(alpha: 0.2),
                    ),
                  ),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: AppTokens.primaryBlue.withValues(alpha: 0.1),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.qr_code_2_rounded, color: AppTokens.primaryBlue, size: 24),
                      ),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Payment ID & QR Code',
                              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                            ),
                            Text(
                              'Receive payments instantly with your QR',
                              style: TextStyle(fontSize: 11, color: Colors.grey),
                            ),
                          ],
                        ),
                      ),
                      const Icon(Icons.arrow_forward_ios_rounded, size: 14, color: AppTokens.primaryBlue),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 16),
              Divider(color: isDark ? AppTokens.borderDark : AppTokens.borderLight, height: 1),
              const SizedBox(height: 8),

              // Menu Items
              ListTile(
                key: const Key('avatar_sheet_settings'),
                leading: const Icon(Icons.settings_outlined, color: AppTokens.primaryBlue),
                title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.w600)),
                trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 14),
                onTap: () {
                  Navigator.of(context).pop();
                  context.push('/settings');
                },
              ),
              ListTile(
                key: const Key('avatar_sheet_privacy'),
                leading: const Icon(Icons.shield_outlined, color: AppTokens.primaryBlue),
                title: const Text('Privacy', style: TextStyle(fontWeight: FontWeight.w600)),
                trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 14),
                onTap: () {
                  Navigator.of(context).pop();
                  context.push('/settings/privacy');
                },
              ),
              ListTile(
                key: const Key('avatar_sheet_security'),
                leading: const Icon(Icons.lock_outline_rounded, color: AppTokens.primaryBlue),
                title: const Text('Security & PIN', style: TextStyle(fontWeight: FontWeight.w600)),
                trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 14),
                onTap: () {
                  Navigator.of(context).pop();
                  context.push('/settings/security');
                },
              ),
              ListTile(
                key: const Key('avatar_sheet_devices'),
                leading: const Icon(Icons.devices_rounded, color: AppTokens.primaryBlue),
                title: const Text('Devices & Sessions', style: TextStyle(fontWeight: FontWeight.w600)),
                trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 14),
                onTap: () {
                  Navigator.of(context).pop();
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Active Devices: Current Android/iOS Device')),
                  );
                },
              ),
              ListTile(
                key: const Key('avatar_sheet_language'),
                leading: const Icon(Icons.language_rounded, color: AppTokens.primaryBlue),
                title: const Text('Language', style: TextStyle(fontWeight: FontWeight.w600)),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'English',
                      style: TextStyle(
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        fontSize: 13,
                      ),
                    ),
                    const SizedBox(width: 4),
                    const Icon(Icons.arrow_forward_ios_rounded, size: 14),
                  ],
                ),
                onTap: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Language: English (Sinhala & Tamil supported)')),
                  );
                },
              ),
              ListTile(
                key: const Key('avatar_sheet_appearance'),
                leading: Icon(
                  isDark ? Icons.dark_mode_rounded : Icons.light_mode_rounded,
                  color: AppTokens.primaryBlue,
                ),
                title: const Text('Appearance', style: TextStyle(fontWeight: FontWeight.w600)),
                trailing: Switch(
                  value: isDark,
                  activeColor: AppTokens.primaryBlue,
                  onChanged: (val) {
                    ref.read(themeControllerProvider.notifier).setThemeMode(
                          val ? ThemeMode.dark : ThemeMode.light,
                        );
                  },
                ),
              ),
              Divider(color: isDark ? AppTokens.borderDark : AppTokens.borderLight, height: 16),
              ListTile(
                key: const Key('avatar_sheet_logout'),
                leading: const Icon(Icons.logout_rounded, color: AppTokens.error),
                title: const Text(
                  'Log Out',
                  style: TextStyle(
                    color: AppTokens.error,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                onTap: () {
                  Navigator.of(context).pop();
                  _showLogoutDialog(context, ref);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showLogoutDialog(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Log Out'),
        content: const Text('Are you sure you want to log out of your session?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppTokens.error),
            onPressed: () {
              Navigator.of(ctx).pop();
              ref.read(authServiceProvider).logout();
              context.go('/welcome');
            },
            child: const Text('Log Out', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }
}
