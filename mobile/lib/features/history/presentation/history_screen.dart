import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/error_retry.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../core/widgets/transaction_tile.dart';
import '../../analytics/data/insights_service.dart';
import '../../analytics/domain/insight_model.dart';
import '../../send/domain/transfer_models.dart';
import '../data/history_service.dart';
import 'receipt_screen.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  HistoryFilter _filter = const HistoryFilter();
  List<TransferModel> _transfers = [];
  bool _isLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final items = await ref.read(historyServiceProvider).listHistory(filter: _filter);
      if (mounted) {
        setState(() {
          _transfers = items;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _error = 'Failed to load transfer history.';
        });
      }
    }
  }

  void _showFilterModal() {
    HapticFeedback.lightImpact();
    String? tempDirection = _filter.direction;
    String? tempStatus = _filter.status;
    String? tempFundingType = _filter.fundingType;
    final minController = TextEditingController(
      text: _filter.minAmount != null ? _filter.minAmount.toString() : '',
    );
    final maxController = TextEditingController(
      text: _filter.maxAmount != null ? _filter.maxAmount.toString() : '',
    );

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setSheetState) {
          final isDark = Theme.of(context).brightness == Brightness.dark;

          return Container(
            padding: EdgeInsets.only(
              top: 24,
              left: 24,
              right: 24,
              bottom: MediaQuery.of(context).viewInsets.bottom + 24,
            ),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Filter History',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    TextButton(
                      key: const Key('history_reset_filter_btn'),
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        setSheetState(() {
                          tempDirection = null;
                          tempStatus = null;
                          tempFundingType = null;
                          minController.clear();
                          maxController.clear();
                        });
                      },
                      child: Text(
                        'Reset',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          color: isDark ? AppTokens.lightViolet : AppTokens.primaryViolet,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Funding Type chips
                const Text(
                  'FUNDING TYPE',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: Colors.grey,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    _buildFilterChip(
                      'All',
                      tempFundingType == null || tempFundingType == 'ALL',
                      () => setSheetState(() => tempFundingType = 'ALL'),
                      isDark,
                      key: const Key('filter_funding_all'),
                    ),
                    const SizedBox(width: 8),
                    _buildFilterChip(
                      'Bank Account',
                      tempFundingType == 'ACCOUNT',
                      () => setSheetState(() => tempFundingType = 'ACCOUNT'),
                      isDark,
                      key: const Key('filter_funding_account'),
                    ),
                    const SizedBox(width: 8),
                    _buildFilterChip(
                      'Card',
                      tempFundingType == 'CARD',
                      () => setSheetState(() => tempFundingType = 'CARD'),
                      isDark,
                      key: const Key('filter_funding_card'),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Direction chips
                const Text(
                  'DIRECTION',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: Colors.grey,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    _buildFilterChip(
                      'All',
                      tempDirection == null,
                      () => setSheetState(() => tempDirection = null),
                      isDark,
                    ),
                    const SizedBox(width: 8),
                    _buildFilterChip(
                      'Sent',
                      tempDirection == 'OUTGOING',
                      () => setSheetState(() => tempDirection = 'OUTGOING'),
                      isDark,
                      key: const Key('filter_chip_sent'),
                    ),
                    const SizedBox(width: 8),
                    _buildFilterChip(
                      'Received',
                      tempDirection == 'INCOMING',
                      () => setSheetState(() => tempDirection = 'INCOMING'),
                      isDark,
                      key: const Key('filter_chip_received'),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Status chips
                const Text(
                  'STATUS',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: Colors.grey,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    _buildFilterChip(
                      'All',
                      tempStatus == null,
                      () => setSheetState(() => tempStatus = null),
                      isDark,
                    ),
                    const SizedBox(width: 8),
                    _buildFilterChip(
                      'Completed',
                      tempStatus == 'COMPLETED',
                      () => setSheetState(() => tempStatus = 'COMPLETED'),
                      isDark,
                      key: const Key('filter_chip_completed'),
                    ),
                    const SizedBox(width: 8),
                    _buildFilterChip(
                      'Failed',
                      tempStatus == 'FAILED',
                      () => setSheetState(() => tempStatus = 'FAILED'),
                      isDark,
                      key: const Key('filter_chip_failed'),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Amount range
                const Text(
                  'AMOUNT RANGE (LKR)',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: Colors.grey,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        key: const Key('filter_min_amount_field'),
                        controller: minController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          hintText: 'Min',
                          contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextField(
                        key: const Key('filter_max_amount_field'),
                        controller: maxController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          hintText: 'Max',
                          contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),

                ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 52),
                  child: ElevatedButton(
                    key: const Key('history_apply_filters_btn'),
                    onPressed: () {
                      HapticFeedback.lightImpact();
                      final min = double.tryParse(minController.text.trim());
                      final max = double.tryParse(maxController.text.trim());
                      setState(() {
                        _filter = HistoryFilter(
                          direction: tempDirection,
                          status: tempStatus,
                          fundingType: tempFundingType,
                          minAmount: min,
                          maxAmount: max,
                        );
                      });
                      Navigator.of(ctx).pop();
                      _loadHistory();
                    },
                    child: const Text(
                      'Apply Filters',
                      style: TextStyle(fontFamily: AppTokens.fontFamily, fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildFilterChip(String label, bool isSelected, VoidCallback onTap, bool isDark, {Key? key}) {
    return ChoiceChip(
      key: key,
      label: Text(
        label,
        style: TextStyle(
          fontFamily: AppTokens.fontFamily,
          color: isSelected ? Colors.white : (isDark ? Colors.white70 : AppTokens.textPrimaryLight),
          fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
        ),
      ),
      selected: isSelected,
      onSelected: (_) {
        HapticFeedback.lightImpact();
        onTap();
      },
      selectedColor: AppTokens.primaryViolet,
      backgroundColor: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    );
  }

  void _openReceipt(TransferModel transfer) async {
    HapticFeedback.lightImpact();
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: CircularProgressIndicator(color: AppTokens.primaryViolet),
      ),
    );

    try {
      final receipt = await ref.read(historyServiceProvider).getReceipt(transfer.transferId);
      if (mounted) {
        Navigator.of(context).pop(); // dismiss loader
        Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => ReceiptScreen(receipt: receipt)),
        );
      }
    } catch (_) {
      if (mounted) {
        Navigator.of(context).pop();
        // Fallback receipt
        final fallback = ReceiptModel(
          receiptNumber: 'REC-${transfer.reference}',
          transferId: transfer.transferId,
          reference: transfer.reference,
          status: transfer.status.name.toUpperCase(),
          amount: transfer.amount,
          fee: transfer.fee,
          totalDebited: transfer.totalAmount > 0 ? transfer.totalAmount : transfer.amount,
          receiver: ReceiptPartyModel(displayName: transfer.receiverDisplayName ?? 'Payee'),
          note: transfer.note,
          createdAt: transfer.createdAt,
          simulated: transfer.simulated,
          fundingType: transfer.fundingType,
          cardId: transfer.cardId,
          cardLast4: transfer.cardLast4,
          cardKind: transfer.cardKind,
          paymentMethod: transfer.paymentMethod,
        );
        Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => ReceiptScreen(receipt: fallback)),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: Column(
        children: [
          const SimulatedBanner(),
          Expanded(
            child: GradientHeaderScaffold(
              scrollable: false,
              bodyPadding: EdgeInsets.zero,
              header: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      if (Navigator.of(context).canPop())
                        IconButton(
                          icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
                          tooltip: 'Back',
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                      Text(
                        'History',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      IconButton(
                        key: const Key('history_filter_btn'),
                        icon: const Icon(Icons.filter_list_rounded),
                        tooltip: 'Filter',
                        onPressed: _showFilterModal,
                      ),
                      IconButton(
                        key: const Key('history_refresh_btn'),
                        icon: const Icon(Icons.refresh_rounded),
                        tooltip: 'Refresh',
                        onPressed: () {
                          HapticFeedback.lightImpact();
                          _loadHistory();
                        },
                      ),
                    ],
                  ),
                ],
              ),
              body: Column(
                children: [
                  if (_hasActiveFilters()) _buildActiveFilterBar(isDark),
                  _buildInsightsCard(isDark),
                  Expanded(
                    child: _isLoading
                        ? const Center(
                            child: CircularProgressIndicator(color: AppTokens.primaryViolet),
                          )
                        : _error != null
                            ? ErrorRetry(
                                message: _error!,
                                onRetry: _loadHistory,
                              )
                            : _transfers.isEmpty
                                ? const EmptyState(
                                    icon: Icons.receipt_long_outlined,
                                    title: 'No transactions found',
                                    description: 'No transfers match the selected filter criteria.',
                                  )
                                : ListView.separated(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: AppTokens.screenHorizontalPadding,
                                      vertical: 16.0,
                                    ),
                                    itemCount: _transfers.length,
                                    separatorBuilder: (_, index) => Divider(
                                      height: 1,
                                      color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                                    ),
                                    itemBuilder: (context, index) {
                                      final item = _transfers[index];
                                      final isSuccess = item.status.isConfirmedSuccess;
                                      String subtitle = item.reference;
                                      if (item.fundingType == FundingType.card) {
                                        subtitle = '${item.reference} • Paid with card •••• ${item.cardLast4 ?? "1234"}';
                                      } else if (item.paymentMethod != null && item.paymentMethod!.isNotEmpty) {
                                        subtitle = '${item.reference} • ${item.paymentMethod}';
                                      }
                                      return TransactionTile(
                                        key: Key('history_item_${item.transferId}'),
                                        icon: isSuccess
                                            ? Icons.check_circle_outline_rounded
                                            : Icons.arrow_outward_rounded,
                                        title: item.receiverDisplayName ?? item.reference,
                                        subtitle: subtitle,
                                        amount: 'LKR ${item.amount.toStringAsFixed(2)}',
                                        status: item.status.name.toUpperCase(),
                                        isPositive: false,
                                        onTap: () => _openReceipt(item),
                                      );
                                    },
                                  ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  bool _hasActiveFilters() {
    return _filter.direction != null ||
        _filter.status != null ||
        (_filter.fundingType != null && _filter.fundingType != 'ALL') ||
        _filter.minAmount != null ||
        _filter.maxAmount != null;
  }

  Widget _buildActiveFilterBar(bool isDark) {
    final parts = [
      if (_filter.fundingType != null && _filter.fundingType != 'ALL') 'Funding: ${_filter.fundingType}',
      if (_filter.direction != null) _filter.direction!,
      if (_filter.status != null) _filter.status!,
    ];
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      color: AppTokens.softViolet,
      child: Row(
        children: [
          const Icon(Icons.filter_alt_outlined, size: 16, color: AppTokens.deepViolet),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Filtered by: ${parts.join(" ")}'.trim(),
              style: const TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppTokens.deepViolet,
              ),
            ),
          ),
          TextButton(
            onPressed: () {
              HapticFeedback.lightImpact();
              setState(() => _filter = const HistoryFilter());
              _loadHistory();
            },
            child: const Text(
              'Clear',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: AppTokens.deepViolet,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInsightsCard(bool isDark) {
    final insights = ref.watch(insightsProvider);
    if (insights.isEmpty) return const SizedBox.shrink();

    return Container(
      key: TestKeys.activityInsightsCard,
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E2230) : const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
          width: 0.8,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(5),
                decoration: BoxDecoration(
                  color: AppTokens.primaryBlue.withOpacity(0.12),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.auto_graph_rounded, size: 15, color: AppTokens.primaryBlue),
              ),
              const SizedBox(width: 8),
              Text(
                'Financial Insights',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                ),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.amber.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  'Factual',
                  style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: Colors.amber),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ...insights.take(3).map((item) => Padding(
                padding: const EdgeInsets.only(bottom: 6.0),
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF252A3D) : Colors.white,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              item.title,
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              item.text,
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 11,
                                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                              ),
                            ),
                            const SizedBox(height: 4),
                            InkWell(
                              key: TestKeys.insightAction(item.id),
                              onTap: () {},
                              child: const Text(
                                'View details →',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                  color: AppTokens.primaryBlue,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        key: TestKeys.insightDismiss(item.type),
                        icon: const Icon(Icons.close_rounded, size: 16),
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                        tooltip: 'Dismiss',
                        onPressed: () {
                          HapticFeedback.lightImpact();
                          ref.read(insightsProvider.notifier).dismiss(item.type);
                        },
                      ),
                    ],
                  ),
                ),
              )),
        ],
      ),
    );
  }
}
