import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/feature_flags.dart';
import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/theme/theme_controller.dart';
import '../../../core/widgets/section_header.dart';
import '../../auth/data/auth_service.dart';

/// Explore Tab: The LankaPay Super App "Showcase".
/// Sections rendered as large cards/tiles driven by feature flags:
/// - My money: Accounts, Cards, Savings goals, Statements
/// - Pay: Bills, Scheduled, Split, QR, Requests
/// - Rewards and City
/// - Support and Insights
/// - Family (if enabled)
/// - Coming soon (need a licensed partner): Investments, Insurance, Loans
/// Search inside Explore filters modules dynamically.
class ExploreScreen extends ConsumerStatefulWidget {
  const ExploreScreen({super.key = const Key('explore_screen')});

  @override
  ConsumerState<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends ConsumerState<ExploreScreen> {
  final TextEditingController _searchController = TextEditingController();
  String _searchQuery = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final flags = ref.watch(featureFlagsProvider);
    final familyEnabled = flags[FeatureFlagKeys.family] ?? false;

    return Scaffold(
      key: const Key('explore_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Explore',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 22),
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        centerTitle: false,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // 1. Search Bar inside Explore
          Container(
            margin: const EdgeInsets.only(bottom: 18),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                width: 0.8,
              ),
              boxShadow: isDark ? [] : AppTokens.cardShadowLight,
            ),
            child: TextField(
              key: AppTestKeys.exploreSearch,
              controller: _searchController,
              onChanged: (val) {
                setState(() {
                  _searchQuery = val.trim().toLowerCase();
                });
              },
              decoration: InputDecoration(
                hintText: 'Search products, pay, services...',
                hintStyle: TextStyle(
                  color: isDark ? Colors.white38 : AppTokens.textSecondaryLight,
                  fontSize: 14,
                ),
                prefixIcon: const Icon(Icons.search_rounded, color: AppTokens.primaryBlue),
                suffixIcon: _searchQuery.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear_rounded, size: 18),
                        onPressed: () {
                          _searchController.clear();
                          setState(() {
                            _searchQuery = '';
                          });
                        },
                      )
                    : null,
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              ),
            ),
          ),

          // 2. Sections as Large Cards / Showcase Tiles
          if (_matchesAny(['accounts', 'cards', 'savings', 'goals', 'statements', 'money'])) ...[
            const SectionHeader(title: 'MY MONEY'),
            const SizedBox(height: 8),
            _buildMyMoneySection(context, isDark),
            const SizedBox(height: 20),
          ],

          if (_matchesAny(['pay', 'bills', 'scheduled', 'split', 'qr', 'requests', 'transfer'])) ...[
            const SectionHeader(title: 'PAY & TRANSFERS'),
            const SizedBox(height: 8),
            _buildPaySection(context, isDark),
            const SizedBox(height: 20),
          ],

          if (_matchesAny(['rewards', 'city', 'offers', 'points', 'lifestyle', 'merchants'])) ...[
            const SectionHeader(title: 'REWARDS & CITY'),
            const SizedBox(height: 8),
            _buildRewardsAndCitySection(context, isDark),
            const SizedBox(height: 20),
          ],

          if (_matchesAny(['support', 'insights', 'help', 'chat', 'assistant', 'spending'])) ...[
            const SectionHeader(title: 'SUPPORT & INSIGHTS'),
            const SizedBox(height: 8),
            _buildSupportAndInsightsSection(context, isDark),
            const SizedBox(height: 20),
          ],

          if (familyEnabled && _matchesAny(['family', 'kids', 'allowance', 'shared'])) ...[
            const SectionHeader(title: 'FAMILY'),
            const SizedBox(height: 8),
            _buildFamilySection(context, isDark),
            const SizedBox(height: 20),
          ],

          if (_matchesAny(['investments', 'insurance', 'loans', 'borrow', 'stocks', 'coming soon', 'partner'])) ...[
            const SectionHeader(title: 'COMING SOON (LICENSED PARTNER REQUIRED)'),
            const SizedBox(height: 8),
            _buildComingSoonSection(context, isDark),
            const SizedBox(height: 20),
          ],

          // 3. Settings & Appearance Preferences
          if (_searchQuery.isEmpty || _matchesAny(['settings', 'privacy', 'security', 'dark mode', 'logout'])) ...[
            const SectionHeader(title: 'PREFERENCES & SECURITY'),
            const SizedBox(height: 8),
            _buildSettingsCard(context, isDark),
            const SizedBox(height: 32),
          ],
        ],
      ),
    );
  }

  bool _matchesAny(List<String> keywords) {
    if (_searchQuery.isEmpty) return true;
    for (final kw in keywords) {
      if (kw.contains(_searchQuery) || _searchQuery.contains(kw)) {
        return true;
      }
    }
    return false;
  }

  // ── 1. My Money Section ─────────────────────────────────────
  Widget _buildMyMoneySection(BuildContext context, bool isDark) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreAccounts,
                title: 'Accounts',
                subtitle: 'Bank accounts & links',
                icon: Icons.account_balance_rounded,
                accentColor: const Color(0xFF2563EB),
                isDark: isDark,
                onTap: () => context.push('/accounts'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreCards,
                title: 'Cards',
                subtitle: 'Virtual & debit cards',
                icon: Icons.credit_card_rounded,
                accentColor: const Color(0xFF7C3AED),
                isDark: isDark,
                onTap: () => context.push('/cards/test-cards'),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreGoals,
                title: 'Savings Goals',
                subtitle: 'Simulated target ledgers',
                icon: Icons.savings_rounded,
                accentColor: const Color(0xFF059669),
                isDark: isDark,
                badgeText: 'SIMULATED',
                onTap: () => context.push('/goals'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreStatements,
                title: 'Statements',
                subtitle: 'PDF & history downloads',
                icon: Icons.receipt_long_rounded,
                accentColor: const Color(0xFFD97706),
                isDark: isDark,
                onTap: () => context.push('/history'),
              ),
            ),
          ],
        ),
      ],
    );
  }

  // ── 2. Pay Section ──────────────────────────────────────────
  Widget _buildPaySection(BuildContext context, bool isDark) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreBills,
                title: 'Bills',
                subtitle: 'Utilities & telecom',
                icon: Icons.receipt_rounded,
                accentColor: const Color(0xFFDC2626),
                isDark: isDark,
                onTap: () => context.push('/bills'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreScheduled,
                title: 'Scheduled',
                subtitle: 'Standing orders',
                icon: Icons.schedule_rounded,
                accentColor: const Color(0xFF4F46E5),
                isDark: isDark,
                onTap: () => context.push('/scheduled'),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreSplit,
                title: 'Split',
                subtitle: 'Group bill splits',
                icon: Icons.call_split_rounded,
                accentColor: const Color(0xFFEA580C),
                isDark: isDark,
                onTap: () => context.push('/split'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildShowcaseTile(
                context,
                key: AppTestKeys.exploreQr,
                title: 'LankaQR',
                subtitle: 'Scan & instant pay',
                icon: Icons.qr_code_scanner_rounded,
                accentColor: const Color(0xFF0284C7),
                isDark: isDark,
                onTap: () => context.push('/qr'),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        _buildShowcaseTile(
          context,
          key: AppTestKeys.exploreRequests,
          title: 'Requests & Send Money',
          subtitle: 'Peer-to-peer transfers and requests',
          icon: Icons.swap_horiz_rounded,
          accentColor: const Color(0xFF0D9488),
          isDark: isDark,
          onTap: () => context.push('/send'),
        ),
      ],
    );
  }

  // ── 3. Rewards and City Section ─────────────────────────────
  Widget _buildRewardsAndCitySection(BuildContext context, bool isDark) {
    return Row(
      children: [
        Expanded(
          child: _buildShowcaseTile(
            context,
            key: AppTestKeys.exploreRewards,
            title: 'Rewards',
            subtitle: 'Points & vouchers',
            icon: Icons.stars_rounded,
            accentColor: const Color(0xFFEAB308),
            isDark: isDark,
            onTap: () => context.push('/rewards'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _buildShowcaseTile(
            context,
            key: AppTestKeys.exploreCity,
            title: 'City',
            subtitle: 'Offers & merchant map',
            icon: Icons.location_city_rounded,
            accentColor: const Color(0xFF8B5CF6),
            isDark: isDark,
            onTap: () => context.push('/city'),
          ),
        ),
      ],
    );
  }

  // ── 4. Support and Insights Section ─────────────────────────
  Widget _buildSupportAndInsightsSection(BuildContext context, bool isDark) {
    return Row(
      children: [
        Expanded(
          child: _buildShowcaseTile(
            context,
            key: AppTestKeys.exploreSupport,
            title: 'Support',
            subtitle: 'Help chat & agents',
            icon: Icons.support_agent_rounded,
            accentColor: const Color(0xFF3B82F6),
            isDark: isDark,
            onTap: () => context.push('/chat'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _buildShowcaseTile(
            context,
            key: AppTestKeys.exploreInsights,
            title: 'Insights',
            subtitle: 'Factual spending rules',
            icon: Icons.insights_rounded,
            accentColor: const Color(0xFF10B981),
            isDark: isDark,
            onTap: () => context.push('/history'),
          ),
        ),
      ],
    );
  }

  // ── 5. Family Section (if enabled) ──────────────────────────
  Widget _buildFamilySection(BuildContext context, bool isDark) {
    return _buildShowcaseTile(
      context,
      key: AppTestKeys.exploreFamily,
      title: 'Family Finance',
      subtitle: 'Simulated family accounts and spending limits',
      icon: Icons.family_restroom_rounded,
      accentColor: const Color(0xFFEC4899),
      isDark: isDark,
      badgeText: 'SIMULATED',
      onTap: () {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Family hub is active in simulated mode.')),
        );
      },
    );
  }

  // ── 6. Coming Soon (Licensed Partner Required) ──────────────
  Widget _buildComingSoonSection(BuildContext context, bool isDark) {
    return Column(
      children: [
        _buildComingSoonTile(
          context,
          key: AppTestKeys.exploreInvestments,
          title: 'Investments',
          subtitle: 'Stocks, Treasury bills & Unit trusts',
          icon: Icons.trending_up_rounded,
          partnerDescription:
              'Requires an SEC-licensed stockbroker or regulated fund manager under Central Bank of Sri Lanka (CBSL) oversight. Real securities trading cannot be simulated.',
          isDark: isDark,
        ),
        const SizedBox(height: 10),
        _buildComingSoonTile(
          context,
          key: AppTestKeys.exploreInsurance,
          title: 'Insurance',
          subtitle: 'Life, health and motor vehicle policies',
          icon: Icons.shield_outlined,
          partnerDescription:
              'Requires integration with an Insurance Regulatory Commission of Sri Lanka (IRCSL) registered insurer. We never create unbacked insurance policies.',
          isDark: isDark,
        ),
        const SizedBox(height: 10),
        _buildComingSoonTile(
          context,
          key: AppTestKeys.exploreLoans,
          title: 'Loans & Borrowing',
          subtitle: 'Personal loans, micro-credit & lines of credit',
          icon: Icons.account_balance_wallet_outlined,
          partnerDescription:
              'Requires a licensed commercial bank or non-bank financial institution (NBFI) under CBSL lending rules. Credit facilities require formal underwriting.',
          isDark: isDark,
        ),
      ],
    );
  }

  // ── Tile Builder Helpers ────────────────────────────────────
  Widget _buildShowcaseTile(
    BuildContext context, {
    required Key key,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color accentColor,
    required bool isDark,
    String? badgeText,
    required VoidCallback onTap,
  }) {
    return Material(
      color: isDark ? AppTokens.cardDark : Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        key: key,
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
              width: 0.8,
            ),
            boxShadow: isDark ? [] : AppTokens.cardShadowLight,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: accentColor.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Icon(icon, color: accentColor, size: 24),
                  ),
                  if (badgeText != null)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: accentColor.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        badgeText,
                        style: TextStyle(
                          color: accentColor,
                          fontSize: 10,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    )
                  else
                    const Icon(Icons.arrow_forward_ios_rounded, size: 12, color: AppTokens.textSecondaryLight),
                ],
              ),
              const SizedBox(height: 14),
              Text(
                title,
                style: const TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 15,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                subtitle,
                style: TextStyle(
                  fontSize: 12,
                  color: isDark ? Colors.white60 : AppTokens.textSecondaryLight,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildComingSoonTile(
    BuildContext context, {
    required Key key,
    required String title,
    required String subtitle,
    required IconData icon,
    required String partnerDescription,
    required bool isDark,
  }) {
    return Material(
      color: isDark ? const Color(0xFF1E293B).withOpacity(0.5) : const Color(0xFFF8FAFC),
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        key: key,
        borderRadius: BorderRadius.circular(18),
        onTap: () => _showComingSoonExplanationSheet(context, title, partnerDescription),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
              width: 0.8,
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: isDark ? Colors.white10 : const Color(0xFFE2E8F0),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: isDark ? Colors.white38 : AppTokens.textSecondaryLight, size: 22),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          title,
                          style: TextStyle(
                            fontWeight: FontWeight.w700,
                            fontSize: 15,
                            color: isDark ? Colors.white70 : const Color(0xFF475569),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: isDark ? Colors.white12 : const Color(0xFFE2E8F0),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Text(
                            'PARTNER REQUIRED',
                            style: TextStyle(
                              fontSize: 9,
                              fontWeight: FontWeight.w800,
                              color: AppTokens.textSecondaryLight,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? Colors.white38 : const Color(0xFF94A3B8),
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.info_outline_rounded, size: 18, color: AppTokens.textSecondaryLight),
            ],
          ),
        ),
      ),
    );
  }

  void _showComingSoonExplanationSheet(
    BuildContext context,
    String title,
    String partnerDescription,
  ) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        final isDark = Theme.of(context).brightness == Brightness.dark;
        return Container(
          key: AppTestKeys.exploreComingSoonSheet,
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: isDark ? AppTokens.cardDark : Colors.white,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: const Color(0xFFFEE2E2),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(Icons.gavel_rounded, color: Color(0xFFDC2626), size: 24),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                        ),
                        const Text(
                          'Licensed Partner Required',
                          style: TextStyle(fontSize: 12, color: Color(0xFFDC2626), fontWeight: FontWeight.w700),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Text(
                partnerDescription,
                style: TextStyle(
                  fontSize: 14,
                  height: 1.5,
                  color: isDark ? Colors.white70 : AppTokens.textPrimaryLight,
                ),
              ),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Row(
                  children: [
                    Icon(Icons.verified_user_outlined, size: 18, color: AppTokens.primaryBlue),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'This university prototype will never launch a fake or unbacked financial flow.',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTokens.primaryBlue,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Understood', style: TextStyle(fontWeight: FontWeight.w700)),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  // ── 7. Settings Card ────────────────────────────────────────
  Widget _buildSettingsCard(BuildContext context, bool isDark) {
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
