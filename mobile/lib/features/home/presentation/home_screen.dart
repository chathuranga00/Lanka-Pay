import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/feature_flags.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/app_avatar.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/error_retry.dart';
import '../../../core/widgets/search_bar_field.dart';
import '../../../core/widgets/shimmer_skeletons.dart';
import '../../../core/widgets/simulated_badge.dart';
import '../data/home_api_model.dart';
import '../data/home_providers.dart';
import 'widgets/avatar_sheet.dart';
import '../../stories/domain/story.dart';
import '../../stories/presentation/stories_provider.dart';
import '../../stories/presentation/widgets/story_card.dart';

/// Pixel-perfect Neo-Banking Home Dashboard tab matching the target design language.
/// Driven by GET /api/v1/home with resilient per-section shimmers, errors, and empty states.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  bool _isBalanceHidden = false;

  Future<void> _refreshHome() async {
    ref.invalidate(homeAggregateProvider);
    try {
      await ref.read(homeAggregateProvider.future);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final homeAsync = ref.watch(homeAggregateProvider);

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refreshHome,
          color: AppTokens.primaryViolet,
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(
              horizontal: AppTokens.screenHorizontalPadding,
              vertical: AppTokens.space12,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // 1. Header: AppAvatar + first name + chevron [home_profile] & Rewards [home_rewards]
                _buildHeader(context, isDark, homeAsync),
                const SizedBox(height: 14),

                // 2. Search bar [home_search]
                _buildSearchBar(context, isDark),
                const SizedBox(height: 18),

                // 3. Stories row [home_stories]
                _buildStoriesRow(context, isDark, homeAsync),
                const SizedBox(height: 16),

                // 4. Two InfoTiles: "All activity" [tile_activity] & "Rewards" [tile_rewards]
                _buildInfoTiles(context, isDark, homeAsync),
                const SizedBox(height: 16),

                // 5. WalletCard [home_wallet] with NudgeBanner & [nudge_dismiss]
                _buildWalletCard(context, isDark, homeAsync),
                const SizedBox(height: 16),

                // 6. Two ProductTiles: "Pay bills" [tile_bills] & "Savings goals" [tile_goals]
                _buildProductTiles(context, isDark),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 1. Header Row
  // ---------------------------------------------------------------------------
  Widget _buildHeader(BuildContext context, bool isDark, AsyncValue<HomeAggregateResponseModel> homeAsync) {
    final textColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;

    final greeting = homeAsync.value?.greeting.data;
    final fullName = greeting?.fullName ?? 'Mikhail';
    final firstName = greeting != null && greeting.fullName.trim().isNotEmpty
        ? greeting.fullName.trim().split(' ').first
        : 'Mikhail';

    final hasNewOffers = homeAsync.value?.rewardsPoints.data?.hasNewOffers ?? false;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // AppAvatar + first name + chevron [home_profile]
        InkWell(
          key: const Key('home_profile'),
          onTap: () => AvatarSheet.show(context),
          borderRadius: BorderRadius.circular(24),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AppAvatar(
                key: const Key('home_avatar'),
                name: fullName,
                size: 40,
              ),
              const SizedBox(width: 10),
              Text(
                firstName,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: textColor,
                  letterSpacing: -0.4,
                ),
              ),
              const SizedBox(width: 4),
              Icon(
                Icons.chevron_right_rounded,
                size: 22,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ],
          ),
        ),
        const Spacer(),

        // Rewards button [home_rewards] with new offers dot [home_rewards_dot]
        InkWell(
          key: const Key('home_rewards'),
          onTap: () => context.push('/rewards'),
          borderRadius: BorderRadius.circular(22),
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(
                  color: AppTokens.primaryViolet,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.card_giftcard_rounded,
                  color: Colors.white,
                  size: 22,
                ),
              ),
              if (hasNewOffers)
                Positioned(
                  top: 0,
                  right: 0,
                  child: Container(
                    key: const Key('home_rewards_dot'),
                    width: 11,
                    height: 11,
                    decoration: BoxDecoration(
                      color: AppTokens.warning,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
                        width: 2,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // 2. Search Bar
  // ---------------------------------------------------------------------------
  Widget _buildSearchBar(BuildContext context, bool isDark) {
    return GestureDetector(
      key: const Key('home_search'),
      onTap: () => context.push('/search'),
      behavior: HitTestBehavior.opaque,
      child: IgnorePointer(
        child: SearchBarField(
          key: const Key('home_search_input'),
          hintText: 'Search',
          readOnly: true,
          onTap: () => context.push('/search'),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 3. Stories Row
  // ---------------------------------------------------------------------------
  Widget _buildStoriesRow(BuildContext context, bool isDark, AsyncValue<HomeAggregateResponseModel> homeAsync) {
    if (homeAsync.isLoading) {
      return const SizedBox(
        key: Key('home_stories'),
        height: 104,
        child: SkeletonStoriesRow(key: Key('home_stories_skeleton')),
      );
    }

    final storiesAsync = ref.watch(storiesNotifierProvider);

    return SizedBox(
      key: const Key('home_stories'),
      height: 104,
      child: storiesAsync.when(
        data: (stories) {
          if (stories.isEmpty) {
            return const SizedBox.shrink();
          }
          return ListView.separated(
            scrollDirection: Axis.horizontal,
            physics: const BouncingScrollPhysics(),
            itemCount: stories.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (ctx, index) {
              return StoryCard(
                story: stories[index],
                allStories: stories,
                index: index,
              );
            },
          );
        },
        loading: () => const SkeletonStoriesRow(key: Key('home_stories_skeleton')),
        error: (_, __) => const SkeletonStoriesRow(key: Key('home_stories_skeleton')),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 4. Two InfoTiles: "All activity" & "Rewards"
  // ---------------------------------------------------------------------------
  Widget _buildInfoTiles(BuildContext context, bool isDark, AsyncValue<HomeAggregateResponseModel> homeAsync) {
    final response = homeAsync.value;

    return Row(
      children: [
        // Left InfoTile: "All activity" [tile_activity]
        Expanded(
          child: _buildActivityTile(context, isDark, homeAsync, response?.spendSummary),
        ),
        const SizedBox(width: 12),

        // Right InfoTile: "Rewards" [tile_rewards]
        Expanded(
          child: _buildRewardsTile(context, isDark, homeAsync, response?.rewardsPoints),
        ),
      ],
    );
  }

  Widget _buildActivityTile(
    BuildContext context,
    bool isDark,
    AsyncValue<HomeAggregateResponseModel> homeAsync,
    HomeSectionModel<SpendSummaryDataModel>? spendSummary,
  ) {
    final tileBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    if (homeAsync.isLoading) {
      return const SkeletonInfoTile(key: Key('tile_activity_skeleton'));
    }

    if (spendSummary != null && spendSummary.isFailed) {
      return Container(
        height: 110,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: tileBg,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Center(
          child: ErrorRetry(
            key: const Key('tile_activity_error'),
            message: 'Activity unavailable',
            compact: true,
            onRetry: _refreshHome,
          ),
        ),
      );
    }

    final data = spendSummary?.data;
    final subtitleText = (data != null && data.hasTransactions)
        ? 'Spent Rs ${data.totalSpentLkr} this month'
        : 'No spending in ${data?.month ?? 'this month'}';
    final progress = data?.budgetUsedFraction ?? 0.0;

    return InkWell(
      key: const Key('tile_activity'),
      onTap: () => context.push('/history'),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        constraints: const BoxConstraints(minHeight: 110),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: tileBg,
          borderRadius: BorderRadius.circular(20),
          boxShadow: isDark ? [] : [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'All activity',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: textPrimary,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  subtitleText,
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: textSecondary,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
            ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: progress.clamp(0.0, 1.0),
                minHeight: 4,
                backgroundColor: isDark ? Colors.white12 : const Color(0xFFEBEBF0),
                valueColor: const AlwaysStoppedAnimation<Color>(AppTokens.primaryViolet),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRewardsTile(
    BuildContext context,
    bool isDark,
    AsyncValue<HomeAggregateResponseModel> homeAsync,
    HomeSectionModel<RewardsPointsDataModel>? rewardsPoints,
  ) {
    final tileBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    if (homeAsync.isLoading) {
      return const SkeletonInfoTile(key: Key('tile_rewards_skeleton'));
    }

    if (rewardsPoints != null && rewardsPoints.isFailed) {
      return Container(
        height: 110,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: tileBg,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Center(
          child: ErrorRetry(
            key: const Key('tile_rewards_error'),
            message: 'Rewards unavailable',
            compact: true,
            onRetry: _refreshHome,
          ),
        ),
      );
    }

    final pointsStr = rewardsPoints?.data?.formattedPoints ?? '1,250 pts';

    return InkWell(
      key: const Key('tile_rewards'),
      onTap: () => context.push('/rewards'),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        constraints: const BoxConstraints(minHeight: 110),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: tileBg,
          borderRadius: BorderRadius.circular(20),
          boxShadow: isDark ? [] : [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Rewards',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: textPrimary,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  pointsStr,
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: textSecondary,
                  ),
                ),
              ],
            ),
            Row(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF3B82F6),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.workspace_premium_rounded,
                    color: Colors.white,
                    size: 18,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 5. WalletCard with NudgeBanner & Dismissal
  // ---------------------------------------------------------------------------
  Widget _buildWalletCard(BuildContext context, bool isDark, AsyncValue<HomeAggregateResponseModel> homeAsync) {
    if (homeAsync.isLoading) {
      return const SkeletonWalletCard(key: Key('home_wallet_loading'));
    }

    final response = homeAsync.value;
    final walletSection = response?.wallet;

    if (walletSection != null && walletSection.isFailed) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isDark ? AppTokens.cardDark : Colors.white,
          borderRadius: BorderRadius.circular(24),
        ),
        child: Center(
          child: ErrorRetry(
            key: const Key('home_wallet_error'),
            message: 'Wallet unavailable',
            compact: true,
            onRetry: _refreshHome,
          ),
        ),
      );
    }

    final wallet = walletSection?.data;
    if (wallet == null) {
      return EmptyState(
        key: const Key('home_wallet_empty'),
        icon: Icons.account_balance_wallet_outlined,
        title: 'No wallet found',
        description: 'Link an account or card to view your wallet.',
        actionLabel: 'Link Account',
        actionKey: const Key('home_wallet_link_btn'),
        onActionTap: () => context.push('/accounts/add'),
      );
    }

    final cardBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    final balanceDisplay = _isBalanceHidden ? '••••••••' : 'LKR ${wallet.primaryBalanceLkr}';
    final accountName = wallet.defaultAccount?.bankName ??
        wallet.defaultAccount?.accountName ??
        'Bank of Ceylon';

    final dismissedNudges = ref.watch(dismissedNudgeIdsProvider);
    final nudgeData = response?.nudge.data;
    final showNudge = nudgeData != null && !dismissedNudges.contains(nudgeData.nudgeId);

    return InkWell(
      key: const Key('home_wallet'),
      onTap: () => context.push('/accounts'),
      borderRadius: BorderRadius.circular(24),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: cardBg,
          borderRadius: BorderRadius.circular(24),
          boxShadow: isDark ? [] : [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 14,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Top Row: Round icon + Balance + Points Pill
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Round icon
                Container(
                  width: 44,
                  height: 44,
                  decoration: const BoxDecoration(
                    color: Color(0xFF3B82F6),
                    shape: BoxShape.circle,
                  ),
                  child: const Center(
                    child: Text(
                      'LP',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                        fontSize: 15,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),

                // Balance & Account Name
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              balanceDisplay,
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 20,
                                fontWeight: FontWeight.w800,
                                color: textPrimary,
                                letterSpacing: -0.3,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          const SizedBox(width: 6),
                          const SimulatedBadge(compact: true),
                          IconButton(
                            key: const Key('home_toggle_balance'),
                            icon: Icon(
                              _isBalanceHidden ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                              size: 18,
                              color: textSecondary,
                            ),
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(),
                            tooltip: 'Toggle balance',
                            onPressed: () => setState(() => _isBalanceHidden = !_isBalanceHidden),
                          ),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        accountName,
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),

                // Dark Points Pill
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E1E28) : const Color(0xFF2C2C35),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.workspace_premium_rounded, size: 13, color: Color(0xFFFFD700)),
                      const SizedBox(width: 4),
                      Text(
                        response?.rewardsPoints.data?.formattedPoints ?? '1,250 pts',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Card Thumbnail (TEST CARD)
            Row(
              children: [
                const SizedBox(width: 56),
                Container(
                  width: 58,
                  height: 42,
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF1E3A8A), Color(0xFF3B82F6)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'TEST CARD',
                        style: TextStyle(color: Colors.white70, fontSize: 6, fontWeight: FontWeight.w700),
                      ),
                      Text(
                        wallet.activeCard != null ? wallet.activeCard!.maskedNumber.replaceAll('•••• ', '') : '0010',
                        style: const TextStyle(color: Colors.white, fontSize: 8, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
              ],
            ),

            // NudgeBanner at bottom
            if (showNudge) ...[
              const SizedBox(height: 12),
              _buildNudgeBanner(context, isDark, nudgeData),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildNudgeBanner(BuildContext context, bool isDark, NudgeDataModel nudge) {
    return Container(
      key: const Key('home_nudge'),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF332915) : const Color(0xFFFEF3C7),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          Expanded(
            child: GestureDetector(
              onTap: () {
                if (nudge.actionRoute.isNotEmpty) {
                  context.push(nudge.actionRoute);
                }
              },
              child: Text(
                nudge.message,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? const Color(0xFFFDE68A) : const Color(0xFF92400E),
                ),
              ),
            ),
          ),
          IconButton(
            key: const Key('nudge_dismiss'),
            icon: Icon(
              Icons.close_rounded,
              size: 16,
              color: isDark ? const Color(0xFFFDE68A) : const Color(0xFF92400E),
            ),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
            tooltip: 'Dismiss nudge',
            onPressed: () {
              ref.read(dismissedNudgeIdsProvider.notifier).dismiss(nudge.nudgeId);
            },
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 6. Two ProductTiles: "Pay bills" & "Savings goals"
  // ---------------------------------------------------------------------------
  Widget _buildProductTiles(BuildContext context, bool isDark) {
    final flags = ref.watch(featureFlagsProvider);
    final billsEnabled = flags[FeatureFlagKeys.bills] ?? true;
    final goalsEnabled = flags[FeatureFlagKeys.goals] ?? true;

    return Row(
      children: [
        // "Pay bills" [tile_bills]
        Expanded(
          child: _buildSingleProductTile(
            context: context,
            tileKey: const Key('tile_bills'),
            icon: Icons.receipt_long_rounded,
            iconBg: const Color(0xFFF59E0B),
            title: 'Pay bills',
            subtitle: 'Utilities & more',
            isEnabled: billsEnabled,
            onTap: () {
              if (billsEnabled) {
                context.push('/bills');
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Pay bills is coming soon!')),
                );
              }
            },
            isDark: isDark,
          ),
        ),
        const SizedBox(width: 12),

        // "Savings goals" [tile_goals]
        Expanded(
          child: _buildSingleProductTile(
            context: context,
            tileKey: const Key('tile_goals'),
            icon: Icons.savings_rounded,
            iconBg: const Color(0xFF10B981),
            title: 'Savings goals',
            subtitle: 'Target & save',
            isEnabled: goalsEnabled,
            onTap: () {
              if (goalsEnabled) {
                context.push('/explore');
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Savings goals is coming soon!')),
                );
              }
            },
            isDark: isDark,
          ),
        ),
      ],
    );
  }

  Widget _buildSingleProductTile({
    required BuildContext context,
    required Key tileKey,
    required IconData icon,
    required Color iconBg,
    required String title,
    required String subtitle,
    required bool isEnabled,
    required VoidCallback onTap,
    required bool isDark,
  }) {
    final tileBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    return InkWell(
      key: tileKey,
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        height: 94,
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: tileBg,
          borderRadius: BorderRadius.circular(20),
          boxShadow: isDark ? [] : [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: iconBg.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: iconBg, size: 22),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: textPrimary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  if (!isEnabled)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                      decoration: BoxDecoration(
                        color: Colors.grey.withValues(alpha: 0.2),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text(
                        'Coming soon',
                        style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: Colors.grey),
                      ),
                    )
                  else
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 11,
                        fontWeight: FontWeight.w500,
                        color: textSecondary,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
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
