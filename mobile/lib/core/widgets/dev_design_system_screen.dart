import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../theme/app_tokens.dart';
import 'widgets.dart';

/// Dev-only showcase screen for visually inspecting every component of the Super App design system
/// across light and dark themes. Accessible in debug builds.
class DevDesignSystemScreen extends ConsumerStatefulWidget {
  const DevDesignSystemScreen({super.key = const Key('dev_design_system_screen')});

  @override
  ConsumerState<DevDesignSystemScreen> createState() => _DevDesignSystemScreenState();
}

class _DevDesignSystemScreenState extends ConsumerState<DevDesignSystemScreen> {
  int _selectedNavIndex = 0;
  bool _showShimmer = false;
  bool _isBalanceHidden = false;

  @override
  Widget build(BuildContext context) {
    if (!kDebugMode) {
      return const Scaffold(
        body: Center(child: Text('Design system gallery only available in debug mode.')),
      );
    }

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primaryTextColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final secondaryTextColor = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: Text(
          'Super App Design System',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 17,
            fontWeight: FontWeight.w700,
            color: primaryTextColor,
          ),
        ),
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 16),
            child: ThemeTogglePill(),
          ),
        ],
      ),
      bottomNavigationBar: AppBottomNav(
        key: const Key('gallery_bottom_nav'),
        currentIndex: _selectedNavIndex,
        onIndexChanged: (idx) => setState(() => _selectedNavIndex = idx),
        chatBadgeCount: 1,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // ── 1. Target Home Header Showcase ────────────────────────────────
            const SectionHeader(title: 'TARGET HOME HEADER & SEARCH'),
            const SizedBox(height: 12),
            Row(
              children: [
                const AppAvatar(
                  key: Key('gallery_avatar'),
                  name: 'Mikhail',
                  size: 42,
                ),
                const SizedBox(width: 12),
                Row(
                  children: [
                    Text(
                      'Mikhail',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        color: primaryTextColor,
                      ),
                    ),
                    const SizedBox(width: 4),
                    Icon(
                      Icons.chevron_right_rounded,
                      size: 20,
                      color: primaryTextColor,
                    ),
                  ],
                ),
                const Spacer(),
                Container(
                  key: const Key('gallery_rewards_btn'),
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: isDark ? AppTokens.rewardsButtonVioletDark : AppTokens.rewardsButtonViolet,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.card_giftcard_rounded,
                    color: Colors.white,
                    size: 20,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const SearchBarField(
              key: Key('gallery_search_bar'),
              hintText: 'Search',
            ),
            const SizedBox(height: 24),

            // ── 2. Stories Row Showcase ───────────────────────────────────────
            const SectionHeader(title: 'STORIES (ROUNDED CARDS WITH BLUE RING)'),
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              clipBehavior: Clip.none,
              child: Row(
                children: [
                  StoryCard(
                    key: const Key('gallery_story_1'),
                    caption: 'Tailored\nto you',
                    isUnseen: true,
                    gradient: const LinearGradient(
                      colors: [Color(0xFF4C84F0), Color(0xFF6F9EFA)],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                    ),
                    graphic: Container(
                      width: 36,
                      height: 36,
                      decoration: const BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.favorite_rounded, color: Color(0xFFFFD600), size: 20),
                    ),
                  ),
                  const SizedBox(width: 12),
                  const StoryCard(
                    key: Key('gallery_story_2'),
                    caption: 'Financial\nInsights',
                    isUnseen: false,
                    gradient: LinearGradient(
                      colors: [Color(0xFF2C2411), Color(0xFF14120A)],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                    ),
                    graphic: Icon(Icons.bookmark_rounded, color: Color(0xFFFFD600), size: 28),
                  ),
                  const SizedBox(width: 12),
                  const StoryCard(
                    key: Key('gallery_story_3'),
                    caption: 'Merchant\nOffers',
                    isUnseen: false,
                    gradient: LinearGradient(
                      colors: [Color(0xFF384358), Color(0xFF1E2430)],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                    ),
                    graphic: Icon(Icons.apartment_rounded, color: Colors.white, size: 28),
                  ),
                  const SizedBox(width: 12),
                  const StoryCard(
                    key: Key('gallery_story_4'),
                    caption: 'Smart coin\nanalytics',
                    isUnseen: false,
                    gradient: LinearGradient(
                      colors: [Color(0xFF2E4C8E), Color(0xFF121B33)],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                    ),
                    graphic: Icon(Icons.monetization_on_rounded, color: Colors.white, size: 28),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // ── 3. Info Tiles Row Showcase ────────────────────────────────────
            const SectionHeader(title: 'INFO TILES (TILE RADIUS ~28)'),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: InfoTile(
                    key: const Key('gallery_info_tile_1'),
                    title: 'All transactions',
                    subtitle: 'No spending in April',
                    progress: 0.10,
                    onTap: () {},
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: InfoTile(
                    key: const Key('gallery_info_tile_2'),
                    title: 'Cashback\nand rewards',
                    icon: Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: AppTokens.primaryBlue,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(
                        Icons.shield_outlined,
                        color: Colors.white,
                        size: 18,
                      ),
                    ),
                    onTap: () {},
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // ── 4. Wallet Card Showcase ───────────────────────────────────────
            const SectionHeader(title: 'WALLET CARD (HERO CARD RADIUS ~32)'),
            const SizedBox(height: 12),
            WalletCard(
              key: const Key('gallery_wallet_card'),
              balance: '0 LKR',
              name: 'Primary Account',
              isBalanceHidden: _isBalanceHidden,
              onEyeToggle: () => setState(() => _isBalanceHidden = !_isBalanceHidden),
              pointsPill: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: isDark ? AppTokens.pointsPillBgDark : AppTokens.pointsPillBgLight,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.star_rounded, size: 12, color: Colors.white),
                    const SizedBox(width: 4),
                    Text(
                      '0 pts',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: isDark ? AppTokens.pointsPillTextDark : AppTokens.pointsPillTextLight,
                      ),
                    ),
                  ],
                ),
              ),
              cardThumbnails: [
                Container(
                  width: 56,
                  height: 34,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(6),
                    gradient: const LinearGradient(
                      colors: [Color(0xFF253350), Color(0xFF131A29)],
                    ),
                  ),
                  padding: const EdgeInsets.all(4),
                  alignment: Alignment.bottomLeft,
                  child: const Text(
                    '•••• 7572',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 8,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                    ),
                  ),
                ),
              ],
              nudgeBanner: NudgeBanner(
                key: const Key('gallery_nudge_banner'),
                text: 'Arrange delivery',
                onTap: () {},
              ),
            ),
            const SizedBox(height: 24),

            // ── 5. Product Tiles Showcase ─────────────────────────────────────
            const SectionHeader(title: 'PRODUCT TILES (BORROW & SAVE)'),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: ProductTile(
                    key: const Key('gallery_product_borrow'),
                    title: 'Borrow',
                    illustration: Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(
                        color: AppTokens.tileTintLight,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: const Icon(
                        Icons.payments_rounded,
                        color: AppTokens.primaryBlue,
                        size: 28,
                      ),
                    ),
                    onTap: () {},
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ProductTile(
                    key: const Key('gallery_product_save'),
                    title: 'Save',
                    illustration: Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(
                        color: AppTokens.nudgeBannerBgLight,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: const Icon(
                        Icons.savings_rounded,
                        color: AppTokens.nudgeBannerTextLight,
                        size: 28,
                      ),
                    ),
                    onTap: () {},
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // ── 6. Account Cards (Legacy & Multi-cards) ────────────────────────
            const SectionHeader(title: 'EXISTING ACCOUNT CARDS'),
            const SizedBox(height: 12),
            if (_showShimmer) ...[
              const SkeletonAccountCard(),
            ] else ...[
              const AccountCard(
                bankName: 'Bank of Ceylon',
                nickname: 'Salary Account',
                maskedNumber: '** 6917',
                balance: 'LKR 84,230.00',
                isDefault: true,
                style: AccountCardStyle.charcoal,
                expiryText: '01/29',
              ),
              const SizedBox(height: 12),
              const AccountCard(
                bankName: 'Commercial Bank',
                nickname: 'Primary Savings',
                maskedNumber: '** 4433',
                balance: 'LKR 41,220.00',
                isDefault: false,
                style: AccountCardStyle.lavender,
                expiryText: '11/28',
              ),
            ],
            const SizedBox(height: 24),

            // ── 7. Transactions Feed ──────────────────────────────────────────
            SectionHeader(
              title: 'RECENT TRANSACTIONS',
              actionLabel: 'See all >',
              onActionTap: () {},
            ),
            const SizedBox(height: 8),
            TransactionTile(
              icon: Icons.shopping_bag_rounded,
              title: 'Supermarket',
              subtitle: 'Today, 16:40',
              amount: '- LKR 4,200.00',
              status: 'payment',
              isPositive: false,
              onTap: () {},
            ),
            const Divider(height: 1),
            TransactionTile(
              icon: Icons.account_balance_rounded,
              title: 'Salary Deposit',
              subtitle: 'Yesterday, 11:20',
              amount: '+ LKR 125,000.00',
              status: 'transfer',
              isPositive: true,
              onTap: () {},
            ),
            const SizedBox(height: 24),

            // ── 8. Empty & Error States ───────────────────────────────────────
            const SectionHeader(title: 'EMPTY STATE & ERROR RETRY'),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
                borderRadius: AppTokens.borderRadiusCard,
              ),
              child: EmptyState(
                icon: Icons.receipt_long_rounded,
                title: 'No Transactions Yet',
                description: 'When you send or receive simulated transfers, your receipts will appear here.',
                actionLabel: 'Send Money',
                onActionTap: () {},
              ),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                ),
                borderRadius: AppTokens.borderRadiusCard,
              ),
              child: ErrorRetry(
                message: 'Could not connect to the simulated bank adapter. Check network.',
                onRetry: () {},
              ),
            ),
            const SizedBox(height: 36),
          ],
        ),
      ),
    );
  }
}
