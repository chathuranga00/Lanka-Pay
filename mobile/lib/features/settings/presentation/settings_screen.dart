import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/localization/locale_controller.dart';
import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';
import '../../auth/data/auth_service.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final currentLocale = ref.watch(localeProvider);

    return GradientHeaderScaffold(
      header: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(AppTokens.space20, AppTokens.space12, AppTokens.space20, AppTokens.space16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  if (Navigator.of(context).canPop()) ...[
                    IconButton(
                      icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
                      tooltip: 'Back',
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        Navigator.of(context).pop();
                      },
                    ),
                    const SizedBox(width: AppTokens.space8),
                  ],
                  Expanded(
                    child: Text(
                      l10n?.navSettings ?? 'Settings',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppTokens.space4),
              Padding(
                padding: EdgeInsets.only(left: Navigator.of(context).canPop() ? 48.0 : 0.0),
                child: Text(
                  'Preferences, security, and session management',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 13,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
      bodyPadding: const EdgeInsets.all(AppTokens.space20),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SimulatedBanner(),
          const SizedBox(height: AppTokens.space16),

          // Core Settings Card
          Material(
            color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
            borderRadius: AppTokens.borderRadiusCard,
            clipBehavior: Clip.antiAlias,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: AppTokens.borderRadiusCard,
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
              ),
              child: Column(
              children: [
                _buildSettingTile(
                  key: AppTestKeys.settingsPrivacyTile,
                  icon: Icons.privacy_tip_outlined,
                  title: 'Privacy & Payment-ID',
                  subtitle: 'Directory discovery & emergency freeze',
                  onTap: () {
                    HapticFeedback.lightImpact();
                    context.push('/settings/privacy');
                  },
                  isDark: isDark,
                ),
                Divider(height: 1, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                _buildSettingTile(
                  key: AppTestKeys.settingsSecurityTile,
                  icon: Icons.security_rounded,
                  title: 'Security & Device Management',
                  subtitle: 'Biometrics, PIN, active device sessions',
                  onTap: () {
                    HapticFeedback.lightImpact();
                    context.push('/settings/security');
                  },
                  isDark: isDark,
                ),
                Divider(height: 1, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                _buildSettingTile(
                  icon: Icons.account_balance_outlined,
                  title: 'Manage Bank Accounts',
                  subtitle: 'Default payment account & receiving status',
                  onTap: () {
                    HapticFeedback.lightImpact();
                    context.push('/accounts');
                  },
                  isDark: isDark,
                ),
                Divider(height: 1, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                _buildSettingTile(
                  icon: Icons.language_rounded,
                  title: 'Language / භාෂාව / மொழி',
                  subtitle: 'English, සිංහල, தமிழ்',
                  onTap: () {
                    HapticFeedback.lightImpact();
                    _showLanguageSheet(context, ref, currentLocale, isDark);
                  },
                  isDark: isDark,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppTokens.space20),

          // Sign Out Card
          Material(
            color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
            borderRadius: AppTokens.borderRadiusCard,
            clipBehavior: Clip.antiAlias,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: AppTokens.borderRadiusCard,
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
              ),
              child: Semantics(
                label: 'Sign out of your LankaPay account',
                child: ListTile(
                key: AppTestKeys.settingsSignOutTile,
                contentPadding: const EdgeInsets.symmetric(horizontal: AppTokens.space16, vertical: 4),
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTokens.errorLight,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.logout_rounded, color: AppTokens.error, size: 20),
                ),
                title: Text(
                  'Sign Out',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    color: AppTokens.error,
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                  ),
                ),
                subtitle: Text(
                  'Revoke session and exit app',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
                onTap: () async {
                  HapticFeedback.lightImpact();
                  await ref.read(authStateProvider.notifier).logout();
                  if (context.mounted) {
                    context.go('/welcome');
                  }
                },
              ),
            ),
          ),
        ),
        const SizedBox(height: AppTokens.space32),
      ],
    ),
  );
  }

  Widget _buildSettingTile({
    Key? key,
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
    required bool isDark,
  }) {
    return Semantics(
      label: '$title: $subtitle',
      child: ListTile(
        key: key,
        contentPadding: const EdgeInsets.symmetric(horizontal: AppTokens.space16, vertical: 4),
        leading: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppTokens.primaryViolet.withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: AppTokens.primaryViolet, size: 20),
        ),
        title: Text(
          title,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontWeight: FontWeight.w700,
            fontSize: 14,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
        subtitle: Text(
          subtitle,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 12,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        trailing: Icon(
          Icons.chevron_right_rounded,
          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
        ),
        onTap: onTap,
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
                title: 'English (Default)',
                selected: currentLocale.languageCode == 'en',
                onTap: () {
                  ref.read(localeProvider.notifier).setLanguageCode('en');
                  Navigator.pop(ctx);
                },
                isDark: isDark,
              ),
              _buildLanguageOption(
                title: 'සිංහල (Sinhala)',
                selected: currentLocale.languageCode == 'si',
                onTap: () {
                  ref.read(localeProvider.notifier).setLanguageCode('si');
                  Navigator.pop(ctx);
                },
                isDark: isDark,
              ),
              _buildLanguageOption(
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
}
