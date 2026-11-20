import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/localization/locale_controller.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/theme/theme_controller.dart';
import '../../../core/widgets/widgets.dart';
import '../../auth/data/auth_service.dart';
import '../../auth/domain/auth_state.dart';

/// More Hub screen styled with neo-banking aesthetics, grey MenuTiles,
/// Appearance controls, and service management.
class MoreScreen extends ConsumerWidget {
  const MoreScreen({super.key = const Key('more_screen')});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeControllerProvider);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final currentLocale = ref.watch(localeProvider);

    // Read real user data from auth state
    final authState = ref.watch(authStateProvider);
    final fullName = authState is AuthAuthenticated
        ? (authState.fullName ?? 'User')
        : 'User';
    final rawPhone = authState is AuthAuthenticated ? authState.phone : null;
    final maskedPhone = rawPhone != null ? _maskPhone(rawPhone) : '—';
    final isActive = authState is AuthAuthenticated;

    return GradientHeaderScaffold(
      header: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            AppTokens.space20,
            AppTokens.space12,
            AppTokens.space20,
            AppTokens.space16,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'More',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontWeight: FontWeight.w800,
                  fontSize: 24,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
              ),
              const SimulatedBadge(compact: true),
            ],
          ),
        ),
      ),
      body: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(
            horizontal: AppTokens.space20,
            vertical: AppTokens.space12,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // User summary header card
              _buildUserSummaryCard(context, isDark, fullName, maskedPhone, isActive),
              const SizedBox(height: AppTokens.space20),

              // Appearance section
              _buildAppearanceSection(context, ref, themeMode, isDark),
              const SizedBox(height: AppTokens.space24),

              // Section Header: Services & Management
              const SectionHeader(
                title: 'SERVICES & SETTINGS',
              ),
              const SizedBox(height: AppTokens.space12),

              // 2x2 Grid of MenuTiles
              GridView.count(
                crossAxisCount: 2,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: 1.25,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  MenuTile(
                    key: const Key('more_qr'),
                    icon: Icons.qr_code_2_rounded,
                    label: 'Payment QR',
                    onTap: () => context.push('/qr'),
                  ),
                  MenuTile(
                    key: const Key('more_requests'),
                    icon: Icons.request_page_rounded,
                    label: 'Payment requests',
                    onTap: () => context.push('/qr'),
                  ),
                  MenuTile(
                    key: const Key('more_history'),
                    icon: Icons.receipt_long_rounded,
                    label: 'Transaction history',
                    onTap: () => context.push('/history'),
                  ),
                  MenuTile(
                    key: const Key('more_notifications'),
                    icon: Icons.notifications_outlined,
                    label: 'Notifications',
                    onTap: () => context.push('/notifications'),
                  ),
                  MenuTile(
                    key: const Key('more_privacy'),
                    icon: Icons.privacy_tip_outlined,
                    label: 'Privacy & payment ID',
                    onTap: () => context.push('/settings/privacy'),
                  ),
                  MenuTile(
                    key: const Key('more_security'),
                    icon: Icons.security_rounded,
                    label: 'Security & devices',
                    onTap: () => context.push('/settings/security'),
                  ),
                  MenuTile(
                    key: const Key('more_profile'),
                    icon: Icons.person_outline_rounded,
                    label: 'Profile',
                    onTap: () => _showProfileSheet(context, isDark, fullName, rawPhone ?? '—'),
                  ),
                  MenuTile(
                    key: const Key('more_language'),
                    icon: Icons.language_rounded,
                    label: 'Language\n(${currentLocale.languageCode.toUpperCase()})',
                    onTap: () => _showLanguageSheet(context, ref, currentLocale, isDark),
                  ),
                  MenuTile(
                    key: const Key('more_help'),
                    icon: Icons.help_outline_rounded,
                    label: 'Help & Support',
                    onTap: () => _showHelpSheet(context, isDark),
                  ),
                  MenuTile(
                    key: const Key('more_logout'),
                    icon: Icons.logout_rounded,
                    label: 'Log out',
                    onTap: () => _handleLogout(context, ref),
                  ),
                ],
              ),
              const SizedBox(height: AppTokens.space32),
            ],
          ),
        ),
    );
  }

  Widget _buildUserSummaryCard(BuildContext context, bool isDark,
      String fullName, String maskedPhone, bool isActive) {
    final statusLabel = isActive ? 'Active' : 'Inactive';
    return Container(
      padding: const EdgeInsets.all(AppTokens.space16),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
        borderRadius: AppTokens.borderRadiusCard,
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
      ),
      child: Row(
        children: [
          AppAvatar(name: fullName, size: 48),
          const SizedBox(width: AppTokens.space16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  fullName,
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  'LankaPay-ID: $maskedPhone • $statusLabel',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppearanceSection(
    BuildContext context,
    WidgetRef ref,
    ThemeMode themeMode,
    bool isDark,
  ) {
    return Container(
      padding: const EdgeInsets.all(AppTokens.space16),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
        borderRadius: AppTokens.borderRadiusCard,
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'APPEARANCE',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const ThemeTogglePill(),
            ],
          ),
          const SizedBox(height: AppTokens.space12),

          // Segmented Theme Mode Options
          Container(
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
              ),
            ),
            child: Row(
              children: [
                _buildThemeOption(
                  label: 'System',
                  icon: Icons.brightness_auto_rounded,
                  mode: ThemeMode.system,
                  currentMode: themeMode,
                  key: const Key('theme_option_system'),
                  onTap: () => ref.read(themeControllerProvider.notifier).setThemeMode(ThemeMode.system),
                  isDark: isDark,
                ),
                _buildThemeOption(
                  label: 'Light',
                  icon: Icons.wb_sunny_rounded,
                  mode: ThemeMode.light,
                  currentMode: themeMode,
                  key: const Key('theme_option_light'),
                  onTap: () => ref.read(themeControllerProvider.notifier).setThemeMode(ThemeMode.light),
                  isDark: isDark,
                ),
                _buildThemeOption(
                  label: 'Dark',
                  icon: Icons.nightlight_round,
                  mode: ThemeMode.dark,
                  currentMode: themeMode,
                  key: const Key('theme_option_dark'),
                  onTap: () => ref.read(themeControllerProvider.notifier).setThemeMode(ThemeMode.dark),
                  isDark: isDark,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildThemeOption({
    required String label,
    required IconData icon,
    required ThemeMode mode,
    required ThemeMode currentMode,
    required Key key,
    required VoidCallback onTap,
    required bool isDark,
  }) {
    final isSelected = currentMode == mode;

    return Expanded(
      child: GestureDetector(
        key: key,
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: isSelected ? AppTokens.primaryViolet : Colors.transparent,
            borderRadius: BorderRadius.circular(13),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                icon,
                size: 15,
                color: isSelected
                    ? Colors.white
                    : (isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight),
              ),
              const SizedBox(width: 5),
              Text(
                label,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 12,
                  fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                  color: isSelected
                      ? Colors.white
                      : (isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showProfileSheet(BuildContext context, bool isDark,
      String fullName, String phone) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          padding: const EdgeInsets.all(AppTokens.space24),
          decoration: BoxDecoration(
            color: isDark ? AppTokens.cardDark : Colors.white,
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(AppTokens.radiusSheetTop),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Profile Details',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                  const SimulatedBadge(compact: true),
                ],
              ),
              const SizedBox(height: AppTokens.space16),
              _buildProfileRow('Full Name', fullName, isDark),
              _buildProfileRow('LankaPay-ID Phone', _maskPhone(phone), isDark),
              _buildProfileRow('PDPA Consent', 'Verified & Active', isDark),
              const SizedBox(height: AppTokens.space24),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  key: const Key('profile_close_btn'),
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Close'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildProfileRow(String label, String value, bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 13,
              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
            ),
          ),
          Text(
            value,
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
            ),
          ),
        ],
      ),
    );
  }

  void _showLanguageSheet(
    BuildContext context,
    WidgetRef ref,
    Locale currentLocale,
    bool isDark,
  ) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          padding: const EdgeInsets.all(AppTokens.space24),
          decoration: BoxDecoration(
            color: isDark ? AppTokens.cardDark : Colors.white,
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(AppTokens.radiusSheetTop),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Select Language / භාෂාව / மொழி',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 17,
                  fontWeight: FontWeight.w800,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
              ),
              const SizedBox(height: AppTokens.space16),
              _buildLanguageOption(
                code: 'en',
                title: 'English (Default)',
                selected: currentLocale.languageCode == 'en',
                onTap: () {
                  ref.read(localeProvider.notifier).setLanguageCode('en');
                  Navigator.pop(ctx);
                },
                isDark: isDark,
              ),
              _buildLanguageOption(
                code: 'si',
                title: 'සිංහල (Sinhala)',
                selected: currentLocale.languageCode == 'si',
                onTap: () {
                  ref.read(localeProvider.notifier).setLanguageCode('si');
                  Navigator.pop(ctx);
                },
                isDark: isDark,
              ),
              _buildLanguageOption(
                code: 'ta',
                title: 'தமிழ் (Tamil)',
                selected: currentLocale.languageCode == 'ta',
                onTap: () {
                  ref.read(localeProvider.notifier).setLanguageCode('ta');
                  Navigator.pop(ctx);
                },
                isDark: isDark,
              ),
              const SizedBox(height: AppTokens.space12),
            ],
          ),
        );
      },
    );
  }

  Widget _buildLanguageOption({
    required String code,
    required String title,
    required bool selected,
    required VoidCallback onTap,
    required bool isDark,
  }) {
    return Material(
      color: Colors.transparent,
      child: ListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(
          title,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 14,
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected
                ? AppTokens.primaryViolet
                : (isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight),
          ),
        ),
        trailing: selected
            ? const Icon(Icons.check_circle_rounded, color: AppTokens.primaryViolet)
            : null,
        onTap: onTap,
      ),
    );
  }

  void _showHelpSheet(BuildContext context, bool isDark) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          padding: const EdgeInsets.all(AppTokens.space24),
          decoration: BoxDecoration(
            color: isDark ? AppTokens.cardDark : Colors.white,
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(AppTokens.radiusSheetTop),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Help & Support',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                  const SimulatedBadge(compact: true),
                ],
              ),
              const SizedBox(height: AppTokens.space16),
              Text(
                'LankaPay CEFT Support Hotline: 1937\nEmail: support@lankapay.lk\nHours: 24/7 National Financial Switch Helpdesk',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  height: 1.5,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: AppTokens.space16),
              Container(
                padding: const EdgeInsets.all(AppTokens.space12),
                decoration: BoxDecoration(
                  color: AppTokens.warningLight,
                  borderRadius: AppTokens.borderRadiusSmall,
                ),
                child: Row(
                  children: [
                    const Icon(Icons.info_outline_rounded, color: AppTokens.warning, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Simulated Sandbox Environment: All balances and transfers are for testing purposes only.',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: AppTokens.warning,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppTokens.space24),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  key: const Key('help_close_btn'),
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Dismiss'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  /// Masks a phone number: +94771234567 → 077****567
  String _maskPhone(String phone) {
    if (phone.isEmpty) return '—';
    // Normalise to local format for display
    String local = phone.startsWith('+94')
        ? '0${phone.substring(3)}'
        : phone;
    if (local.length < 7) return local;
    final start = local.substring(0, 3);          // 077
    final end   = local.substring(local.length - 3); // 567
    return '$start****$end';
  }

  Future<void> _handleLogout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Sign Out'),
        content: const Text('Are you sure you want to sign out from LankaPay?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            key: const Key('more_logout_confirm_btn'),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Sign Out'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await ref.read(authStateProvider.notifier).logout();
      if (context.mounted) {
        context.go('/welcome');
      }
    }
  }
}
