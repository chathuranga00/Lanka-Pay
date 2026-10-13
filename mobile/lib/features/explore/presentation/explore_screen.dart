import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/theme/theme_controller.dart';
import '../../../core/widgets/info_tile.dart';
import '../../../core/widgets/product_tile.dart';
import '../../../core/widgets/section_header.dart';
import '../../auth/data/auth_service.dart';

/// Explore & Products Tab (Tab 4: Explore).
/// Hub for cards, bank accounts, financial products, and profile/settings options.
class ExploreScreen extends ConsumerWidget {
  const ExploreScreen({super.key = const Key('explore_screen')});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      key: const Key('explore_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Explore & Products',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        centerTitle: false,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // 1. Primary "Get a Card" / Manage Cards Banner
          Container(
            key: const Key('explore_cards_banner'),
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF4C84F0), Color(0xFF7B3FE4)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(24),
              boxShadow: isDark ? [] : AppTokens.cardShadowLight,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.2),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text(
                        'Virtual & Physical Cards',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    const Icon(Icons.credit_card_rounded, color: Colors.white, size: 28),
                  ],
                ),
                const SizedBox(height: 12),
                const Text(
                  'Manage Your Cards',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Instant freeze/unfreeze, PIN controls, and simulated tokenized payments.',
                  style: TextStyle(color: Colors.white70, fontSize: 13),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    ElevatedButton(
                      key: const Key('explore_view_cards_btn'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.white,
                        foregroundColor: AppTokens.primaryBlue,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      onPressed: () => context.push('/cards/test-cards'),
                      child: const Text('View Cards', style: TextStyle(fontWeight: FontWeight.w700)),
                    ),
                    const SizedBox(width: 8),
                    TextButton(
                      key: const Key('explore_add_card_btn'),
                      style: TextButton.styleFrom(foregroundColor: Colors.white),
                      onPressed: () => context.push('/cards/add'),
                      child: const Text('+ Add New Card'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 2. Connected Bank Accounts Section
          SectionHeader(
            title: 'CONNECTED BANK ACCOUNTS',
            actionLabel: 'Manage >',
            onActionTap: () => context.push('/accounts'),
          ),
          const SizedBox(height: 8),
          InfoTile(
            key: const Key('explore_accounts_tile'),
            title: 'Bank Accounts',
            subtitle: 'Commercial Bank, Bank of Ceylon & linked accounts',
            icon: Icons.account_balance_rounded,
            onTap: () => context.push('/accounts'),
          ),
          const SizedBox(height: 24),

          // 3. Financial Products 2x2 Grid (Borrow, Save, Invest, Insurance)
          const SectionHeader(title: 'FINANCIAL PRODUCTS'),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: ProductTile(
                  key: const Key('explore_prod_borrow'),
                  title: 'Borrow',
                  illustration: Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: AppTokens.tileTintLight,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Icon(Icons.payments_rounded, color: AppTokens.primaryBlue, size: 28),
                  ),
                  onTap: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ProductTile(
                  key: const Key('explore_prod_save'),
                  title: 'Save',
                  illustration: Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: AppTokens.nudgeBannerBgLight,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Icon(Icons.savings_rounded, color: AppTokens.nudgeBannerTextLight, size: 28),
                  ),
                  onTap: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: ProductTile(
                  key: const Key('explore_prod_invest'),
                  title: 'Invest',
                  illustration: Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: const Color(0xFFF3E8FF),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Icon(Icons.trending_up_rounded, color: Color(0xFF7B3FE4), size: 28),
                  ),
                  onTap: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ProductTile(
                  key: const Key('explore_prod_insurance'),
                  title: 'Insurance',
                  illustration: Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: const Color(0xFFDCFCE7),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Icon(Icons.shield_rounded, color: Color(0xFF16A34A), size: 28),
                  ),
                  onTap: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // 4. Settings, Profile, Privacy, Security, Language, Appearance & Logout
          const SectionHeader(title: 'PREFERENCES & SECURITY'),
          const SizedBox(height: 8),
          _buildSettingsCard(context, isDark, ref),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildSettingsCard(BuildContext context, bool isDark, WidgetRef ref) {
    return Material(
      color: isDark ? AppTokens.cardDark : Colors.white,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: BorderSide(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
          width: 0.8,
        ),
      ),
      child: Column(
        children: [
          ListTile(
            key: const Key('explore_settings_item'),
            leading: const Icon(Icons.settings_outlined, color: AppTokens.primaryBlue),
            title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.w600)),
            trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 14),
            onTap: () => context.push('/settings'),
          ),
          const Divider(height: 1),
          ListTile(
            key: const Key('explore_privacy_item'),
            leading: const Icon(Icons.shield_outlined, color: AppTokens.primaryBlue),
            title: const Text('Privacy Settings', style: TextStyle(fontWeight: FontWeight.w600)),
            trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 14),
            onTap: () => context.push('/settings/privacy'),
          ),
          const Divider(height: 1),
          ListTile(
            key: const Key('explore_security_item'),
            leading: const Icon(Icons.lock_outline_rounded, color: AppTokens.primaryBlue),
            title: const Text('Security & Step-Up PIN', style: TextStyle(fontWeight: FontWeight.w600)),
            trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 14),
            onTap: () => context.push('/settings/security'),
          ),
          const Divider(height: 1),
          ListTile(
            key: const Key('explore_appearance_item'),
            leading: Icon(
              isDark ? Icons.dark_mode_rounded : Icons.light_mode_rounded,
              color: AppTokens.primaryBlue,
            ),
            title: const Text('Dark Mode', style: TextStyle(fontWeight: FontWeight.w600)),
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
          const Divider(height: 1),
          ListTile(
            key: const Key('explore_logout_item'),
            leading: const Icon(Icons.logout_rounded, color: AppTokens.error),
            title: const Text('Log Out', style: TextStyle(color: AppTokens.error, fontWeight: FontWeight.w600)),
            onTap: () {
              ref.read(authServiceProvider).logout();
              context.go('/welcome');
            },
          ),
        ],
      ),
    );
  }
}
