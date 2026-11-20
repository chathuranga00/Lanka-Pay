import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/shimmer_skeletons.dart';
import '../data/rewards_service.dart';
import '../domain/rewards_models.dart';

class RewardsScreen extends ConsumerStatefulWidget {
  const RewardsScreen({super.key});

  @override
  ConsumerState<RewardsScreen> createState() => _RewardsScreenState();
}

class _RewardsScreenState extends ConsumerState<RewardsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final summaryAsync = ref.watch(rewardsSummaryProvider);
    final offersAsync = ref.watch(rewardsOffersProvider);
    final filterType = ref.watch(rewardsLedgerTypeFilterProvider);
    final ledgerAsync = ref.watch(rewardsLedgerProvider(filterType));

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Rewards & Points',
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18),
        ),
        centerTitle: true,
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(rewardsSummaryProvider);
            ref.invalidate(rewardsOffersProvider);
            ref.invalidate(rewardsLedgerProvider);
          },
          child: NestedScrollView(
            headerSliverBuilder: (context, innerBoxIsScrolled) {
              return [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppTokens.screenHorizontalPadding,
                      vertical: AppTokens.space8,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // Disclaimer Banner
                        _buildDisclaimerBanner(isDark),
                        const SizedBox(height: 12),

                        // Balance, Tier & Expiring Pill Card
                        summaryAsync.when(
                          loading: () => const SkeletonAccountCard(key: Key('sk_rew_card')),
                          error: (err, _) => _buildErrorCard(isDark, err.toString()),
                          data: (summary) => _buildHeroRewardsCard(context, isDark, summary),
                        ),
                        const SizedBox(height: 16),

                        // Tab Bar: Offers & Ledger
                        Container(
                          decoration: BoxDecoration(
                            color: isDark ? AppTokens.cardDark : Colors.white,
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: TabBar(
                            controller: _tabController,
                            labelColor: AppTokens.primaryViolet,
                            unselectedLabelColor: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                            indicatorColor: AppTokens.primaryViolet,
                            indicatorWeight: 3,
                            tabs: const [
                              Tab(text: 'Special Offers'),
                              Tab(key: AppTestKeys.rewardsLedger, text: 'Points History'),
                            ],
                          ),
                        ),
                        const SizedBox(height: 12),
                      ],
                    ),
                  ),
                ),
              ];
            },
            body: TabBarView(
              controller: _tabController,
              children: [
                // Tab 1: Offers List
                _buildOffersTab(context, isDark, offersAsync),

                // Tab 2: Ledger with Filters
                _buildLedgerTab(context, isDark, ledgerAsync, filterType),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 1. Prominent Simulated Disclaimer Banner
  // ---------------------------------------------------------------------------
  Widget _buildDisclaimerBanner(bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppTokens.primaryViolet.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTokens.primaryViolet.withOpacity(0.3)),
      ),
      child: const Row(
        children: [
          Icon(Icons.info_outline_rounded, size: 20, color: AppTokens.primaryViolet),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Simulated points only • No real cashback payout exists. Points hold no cash value.',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppTokens.primaryViolet,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 2. Hero Rewards Card (Balance Pill, Tier, Expiring Soon, Redeem CTA)
  // ---------------------------------------------------------------------------
  Widget _buildHeroRewardsCard(BuildContext context, bool isDark, RewardsSummaryModel summary) {
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(24),
        boxShadow: isDark ? [] : [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Tier Pill
              Container(
                key: AppTestKeys.rewardsTierBadge,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF8B5CF6), Color(0xFF6D28D9)],
                  ),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.workspace_premium_rounded, size: 16, color: Colors.white),
                    const SizedBox(width: 6),
                    Text(
                      '${summary.tierDisplayName} Tier',
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),

              // Expiring Soon Indicator
              if (summary.expiringSoonPoints > 0)
                Container(
                  key: AppTestKeys.rewardsExpiring,
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: AppTokens.warning.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.access_time_rounded, size: 14, color: AppTokens.warning),
                      const SizedBox(width: 4),
                      Text(
                        '${summary.expiringSoonPoints} pts expiring soon',
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: AppTokens.warning,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          const SizedBox(height: 18),

          // Points Balance Pill
          Container(
            key: AppTestKeys.rewardsPointsPill,
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
            decoration: BoxDecoration(
              color: isDark ? Colors.white.withOpacity(0.05) : const Color(0xFFF8F7FF),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppTokens.primaryViolet.withOpacity(0.2)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'SIMULATED POINTS BALANCE',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.8,
                        color: textSecondary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${NumberFormat('#,##0').format(summary.pointsBalance)} pts',
                      style: TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.w900,
                        color: textPrimary,
                      ),
                    ),
                  ],
                ),

                // Redeem Action Button
                ElevatedButton.icon(
                  key: AppTestKeys.rewardsRedeem,
                  onPressed: () => _openRedeemSheet(context, summary),
                  icon: const Icon(Icons.redeem_rounded, size: 18, color: Colors.white),
                  label: const Text(
                    'Redeem',
                    style: TextStyle(fontWeight: FontWeight.w700, color: Colors.white),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTokens.primaryViolet,
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Daily & Monthly Cap Trackers
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Today: +${summary.dailyEarnedPoints} pts (${summary.dailyCapRemaining} cap left)',
                style: TextStyle(fontSize: 11, color: textSecondary),
              ),
              Text(
                'Month: +${summary.monthlyEarnedPoints} pts',
                style: TextStyle(fontSize: 11, color: textSecondary),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 3. Tab 1: Offers List
  // ---------------------------------------------------------------------------
  Widget _buildOffersTab(BuildContext context, bool isDark, AsyncValue<List<RewardOfferModel>> offersAsync) {
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;

    return offersAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(16),
        child: Column(
          children: [
            SkeletonTransactionTile(key: Key('sk_off_1')),
            SkeletonTransactionTile(key: Key('sk_off_2')),
          ],
        ),
      ),
      error: (err, _) => Center(child: Text('Error loading offers: $err')),
      data: (offers) {
        if (offers.isEmpty) {
          return Center(
            child: Text('No active offers at this time', style: TextStyle(color: textSecondary)),
          );
        }

        return ListView.separated(
          padding: const EdgeInsets.symmetric(
            horizontal: AppTokens.screenHorizontalPadding,
            vertical: AppTokens.space12,
          ),
          itemCount: offers.length,
          separatorBuilder: (_, __) => const SizedBox(height: 12),
          itemBuilder: (context, index) {
            final offer = offers[index];
            return Material(
              key: AppTestKeys.rewardsOffer(offer.id),
              color: cardBg,
              borderRadius: BorderRadius.circular(18),
              elevation: isDark ? 0 : 2,
              shadowColor: Colors.black.withOpacity(0.04),
              child: InkWell(
                onTap: () => _showOfferDetailsModal(context, isDark, offer),
                borderRadius: BorderRadius.circular(18),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: AppTokens.primaryViolet.withOpacity(0.12),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: const Icon(Icons.local_offer_rounded, color: AppTokens.primaryViolet, size: 24),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    offer.title,
                                    style: TextStyle(
                                      fontSize: 15,
                                      fontWeight: FontWeight.w700,
                                      color: textPrimary,
                                    ),
                                  ),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: AppTokens.success.withOpacity(0.15),
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Text(
                                    offer.rewardBadgeText,
                                    style: const TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w800,
                                      color: AppTokens.success,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              offer.description,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(fontSize: 12, color: textSecondary),
                            ),
                            const SizedBox(height: 6),
                            const Text(
                              'Simulated points offer',
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.w600,
                                color: AppTokens.primaryViolet,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }

  // ---------------------------------------------------------------------------
  // 4. Tab 2: Ledger with Filters
  // ---------------------------------------------------------------------------
  Widget _buildLedgerTab(
    BuildContext context,
    bool isDark,
    AsyncValue<List<PointsLedgerEntryModel>> ledgerAsync,
    String currentFilter,
  ) {
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;

    return Column(
      children: [
        // Filter Chips (All, Earn, Redeem, Reversals)
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: AppTokens.screenHorizontalPadding,
            vertical: 8,
          ),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _buildFilterChip('ALL', 'All Entries', currentFilter),
                const SizedBox(width: 8),
                _buildFilterChip('EARN', 'Earned', currentFilter),
                const SizedBox(width: 8),
                _buildFilterChip('REDEEM', 'Redeemed', currentFilter),
                const SizedBox(width: 8),
                _buildFilterChip('REVERSE', 'Reversed', currentFilter),
              ],
            ),
          ),
        ),

        // Ledger List
        Expanded(
          child: ledgerAsync.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(16),
              child: Column(
                children: [
                  SkeletonTransactionTile(key: Key('sk_led_1')),
                  SkeletonTransactionTile(key: Key('sk_led_2')),
                  SkeletonTransactionTile(key: Key('sk_led_3')),
                ],
              ),
            ),
            error: (err, _) => Center(child: Text('Error loading ledger: $err')),
            data: (entries) {
              if (entries.isEmpty) {
                return Center(
                  child: Text('No points activity yet', style: TextStyle(color: textSecondary)),
                );
              }

              return ListView.separated(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppTokens.screenHorizontalPadding,
                  vertical: 8,
                ),
                itemCount: entries.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (context, index) {
                  final item = entries[index];
                  final isEarn = item.type == 'EARN';
                  final pointColor = isEarn ? AppTokens.success : AppTokens.error;
                  final prefix = isEarn ? '+' : '';

                  return Material(
                    color: cardBg,
                    borderRadius: BorderRadius.circular(16),
                    elevation: isDark ? 0 : 1,
                    shadowColor: Colors.black.withOpacity(0.04),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      child: Row(
                        children: [
                          CircleAvatar(
                            backgroundColor: pointColor.withOpacity(0.12),
                            child: Icon(
                              isEarn ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded,
                              color: pointColor,
                              size: 18,
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.reason,
                                  style: TextStyle(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w700,
                                    color: textPrimary,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  '${DateFormat('MMM d, h:mm a').format(item.createdAt)} • Bal: ${item.runningBalance} pts',
                                  style: TextStyle(fontSize: 12, color: textSecondary),
                                ),
                                const SizedBox(height: 2),
                                const Text(
                                  'Simulated points',
                                  style: TextStyle(fontSize: 10, color: AppTokens.primaryViolet),
                                ),
                              ],
                            ),
                          ),
                          Text(
                            '$prefix${item.points} pts',
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w800,
                              color: pointColor,
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildFilterChip(String type, String label, String current) {
    final isSelected = current == type;
    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (selected) {
        if (selected) {
          ref.read(rewardsLedgerTypeFilterProvider.notifier).setFilter(type);
        }
      },
      selectedColor: AppTokens.primaryViolet,
      labelStyle: TextStyle(
        fontSize: 12,
        fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
        color: isSelected ? Colors.white : null,
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 5. Offer Details Bottom Sheet
  // ---------------------------------------------------------------------------
  void _showOfferDetailsModal(BuildContext context, bool isDark, RewardOfferModel offer) {
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      offer.title,
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: textPrimary),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppTokens.success.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      offer.rewardBadgeText,
                      style: const TextStyle(fontWeight: FontWeight.w800, color: AppTokens.success),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(offer.description, style: TextStyle(fontSize: 14, color: textSecondary)),
              const SizedBox(height: 16),
              const Divider(),
              const SizedBox(height: 8),
              Text(
                'Terms & Conditions',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: textPrimary),
              ),
              const SizedBox(height: 4),
              Text(offer.terms, style: TextStyle(fontSize: 12, color: textSecondary)),
              const SizedBox(height: 8),
              const Text(
                'Simulated points only. No real money value.',
                style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppTokens.primaryViolet),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: () => Navigator.pop(ctx),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTokens.primaryViolet,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: const Text('Got it', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
              ),
            ],
          ),
        );
      },
    );
  }

  // ---------------------------------------------------------------------------
  // 6. Redeem Flow with Step-Up PIN Confirmation
  // ---------------------------------------------------------------------------
  void _openRedeemSheet(BuildContext context, RewardsSummaryModel summary) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetCtx) {
        return _RedeemBottomSheetContent(summary: summary);
      },
    );
  }

  Widget _buildErrorCard(bool isDark, String message) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTokens.error.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text('Error: $message', style: const TextStyle(color: AppTokens.error)),
    );
  }
}

class _RedeemBottomSheetContent extends ConsumerStatefulWidget {
  final RewardsSummaryModel summary;

  const _RedeemBottomSheetContent({required this.summary});

  @override
  ConsumerState<_RedeemBottomSheetContent> createState() => _RedeemBottomSheetContentState();
}

class _RedeemBottomSheetContentState extends ConsumerState<_RedeemBottomSheetContent> {
  String _selectedRewardType = 'VOUCHER';
  String _selectedPartner = 'Keells Super';
  int _selectedPoints = 100;
  final TextEditingController _pinController = TextEditingController();
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submitRedeem() async {
    final pin = _pinController.text.trim();
    if (pin.length < 4) {
      setState(() => _errorMessage = 'Please enter your 4-digit PIN for confirmation');
      return;
    }

    if (widget.summary.pointsBalance < _selectedPoints) {
      setState(() => _errorMessage = 'Insufficient simulated points balance');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(rewardsServiceProvider);
      final idempKey = 'RED-${DateTime.now().millisecondsSinceEpoch}';
      final response = await service.redeemPoints(
        rewardType: _selectedRewardType,
        points: _selectedPoints,
        voucherPartner: _selectedRewardType == 'VOUCHER' ? _selectedPartner : null,
        pin: pin,
        idempotencyKey: idempKey,
      );

      if (mounted) {
        Navigator.pop(context);
        ref.invalidate(rewardsSummaryProvider);
        ref.invalidate(rewardsLedgerProvider);
        _showSuccessDialog(response);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorMessage = e.toString().contains('401') || e.toString().contains('INVALID_PIN')
              ? 'Invalid PIN. Please try again.'
              : 'Redemption failed: $e';
        });
      }
    }
  }

  void _showSuccessDialog(RedeemPointsResponseModel response) {
    showDialog(
      context: context,
      builder: (dlgCtx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Redemption Successful', style: TextStyle(fontWeight: FontWeight.w800)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Redeemed ${response.pointsRedeemed} simulated points.'),
            const SizedBox(height: 8),
            if (response.voucherCode != null) ...[
              Text('Voucher Code: ${response.voucherCode}', style: const TextStyle(fontWeight: FontWeight.w700)),
              Text('Partner: ${response.partner}'),
            ],
            if (response.statementCreditLabel != null)
              Text(response.statementCreditLabel!, style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 12),
            const Text(
              'Simulated points only. Points and vouchers hold no cash value.',
              style: TextStyle(fontSize: 11, color: AppTokens.primaryViolet, fontWeight: FontWeight.w600),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dlgCtx),
            child: const Text('Done', style: TextStyle(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 24,
        right: 24,
        top: 24,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            'Redeem Simulated Points',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 6),
          const Text(
            'Simulated points only • Exchange for simulated partner vouchers or statement credits.',
            style: TextStyle(fontSize: 12, color: AppTokens.primaryViolet, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 18),

          // Reward Type Selection
          Row(
            children: [
              Expanded(
                child: ChoiceChip(
                  label: const Text('Voucher'),
                  selected: _selectedRewardType == 'VOUCHER',
                  onSelected: (val) => setState(() => _selectedRewardType = 'VOUCHER'),
                  selectedColor: AppTokens.primaryViolet,
                  labelStyle: TextStyle(
                    color: _selectedRewardType == 'VOUCHER' ? Colors.white : null,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ChoiceChip(
                  label: const Text('Statement Credit'),
                  selected: _selectedRewardType == 'STATEMENT_CREDIT',
                  onSelected: (val) => setState(() => _selectedRewardType = 'STATEMENT_CREDIT'),
                  selectedColor: AppTokens.primaryViolet,
                  labelStyle: TextStyle(
                    color: _selectedRewardType == 'STATEMENT_CREDIT' ? Colors.white : null,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Partner Selection (if Voucher)
          if (_selectedRewardType == 'VOUCHER') ...[
            DropdownButtonFormField<String>(
              value: _selectedPartner,
              decoration: InputDecoration(
                labelText: 'Partner',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
              items: const [
                DropdownMenuItem(value: 'Keells Super', child: Text('Keells Super')),
                DropdownMenuItem(value: 'Cargills Food City', child: Text('Cargills Food City')),
                DropdownMenuItem(value: 'Daraz', child: Text('Daraz')),
              ],
              onChanged: (val) => setState(() => _selectedPartner = val ?? 'Keells Super'),
            ),
            const SizedBox(height: 14),
          ],

          // Points Selector
          DropdownButtonFormField<int>(
            value: _selectedPoints,
            decoration: InputDecoration(
              labelText: 'Points to Redeem',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            ),
            items: const [
              DropdownMenuItem(value: 50, child: Text('50 pts')),
              DropdownMenuItem(value: 100, child: Text('100 pts')),
              DropdownMenuItem(value: 200, child: Text('200 pts')),
              DropdownMenuItem(value: 500, child: Text('500 pts')),
            ],
            onChanged: (val) => setState(() => _selectedPoints = val ?? 100),
          ),
          const SizedBox(height: 14),

          // Step-Up PIN field
          TextField(
            key: AppTestKeys.rewardsPinField,
            controller: _pinController,
            obscureText: true,
            keyboardType: TextInputType.number,
            maxLength: 4,
            decoration: InputDecoration(
              labelText: 'Step-up Authentication PIN',
              hintText: 'Enter 4-digit PIN',
              counterText: '',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
          const SizedBox(height: 8),

          if (_errorMessage != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(_errorMessage!, style: const TextStyle(color: AppTokens.error, fontSize: 13)),
            ),

          ElevatedButton(
            key: AppTestKeys.rewardsRedeemConfirm,
            onPressed: _isLoading ? null : _submitRedeem,
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTokens.primaryViolet,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            child: _isLoading
                ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Text('Confirm Redemption', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }
}
