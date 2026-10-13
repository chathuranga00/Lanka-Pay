import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/shimmer_skeletons.dart';
import '../data/analytics_service.dart';
import '../domain/analytics_models.dart';

class ActivityScreen extends ConsumerStatefulWidget {
  const ActivityScreen({super.key});

  @override
  ConsumerState<ActivityScreen> createState() => _ActivityScreenState();
}

class _ActivityScreenState extends ConsumerState<ActivityScreen> {
  String _selectedDirection = 'ALL';
  String _selectedCategory = 'ALL';

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final currentMonth = ref.watch(selectedMonthProvider);
    final summaryAsync = ref.watch(analyticsSummaryProvider(currentMonth));
    final filter = ActivityFilter(
      month: currentMonth,
      direction: _selectedDirection == 'ALL' ? null : _selectedDirection,
      category: _selectedCategory == 'ALL' ? null : _selectedCategory,
    );
    final activitiesAsync = ref.watch(activityListProvider(filter));

    final formattedMonthTitle = _formatMonthTitle(currentMonth);

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'All Activity',
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18),
        ),
        centerTitle: true,
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(analyticsSummaryProvider(currentMonth));
            ref.invalidate(activityListProvider);
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(
              horizontal: AppTokens.screenHorizontalPadding,
              vertical: AppTokens.space12,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // 1. Month Switcher
                _buildMonthSwitcher(context, isDark, currentMonth, formattedMonthTitle),
                const SizedBox(height: 16),

                // 2. Summary & Budget section
                summaryAsync.when(
                  loading: () => const SkeletonAccountCard(),
                  error: (err, stack) => _buildErrorBanner(isDark, err.toString()),
                  data: (summary) => Column(
                    children: [
                      _buildSpendingSummaryCard(context, isDark, summary),
                      const SizedBox(height: 16),
                      _buildBudgetCard(context, isDark, summary, currentMonth),
                      const SizedBox(height: 20),
                      _buildCategorySection(context, isDark, summary),
                    ],
                  ),
                ),

                const SizedBox(height: 20),

                // 3. Filter Chips
                _buildFilterChips(isDark),
                const SizedBox(height: 14),

                // 4. Transactions List
                activitiesAsync.when(
                  loading: () => const Column(
                    children: [
                      SkeletonTransactionTile(key: Key('sk_txn_1')),
                      SkeletonTransactionTile(key: Key('sk_txn_2')),
                      SkeletonTransactionTile(key: Key('sk_txn_3')),
                    ],
                  ),
                  error: (err, stack) => Center(
                    child: Text('Error loading transactions: $err'),
                  ),
                  data: (items) => _buildTransactionsList(context, isDark, items, formattedMonthTitle),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 1. Month Switcher
  // ---------------------------------------------------------------------------
  Widget _buildMonthSwitcher(
    BuildContext context,
    bool isDark,
    String currentMonth,
    String formattedTitle,
  ) {
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(16),
        boxShadow: isDark
            ? []
            : [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            key: AppTestKeys.activityMonthPrev,
            icon: const Icon(Icons.chevron_left_rounded, size: 28),
            onPressed: () => _shiftMonth(-1),
            tooltip: 'Previous month',
          ),
          Row(
            children: [
              const Icon(Icons.calendar_month_outlined, size: 18, color: AppTokens.primaryViolet),
              const SizedBox(width: 8),
              Text(
                formattedTitle,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: textPrimary,
                ),
              ),
            ],
          ),
          IconButton(
            key: AppTestKeys.activityMonthNext,
            icon: const Icon(Icons.chevron_right_rounded, size: 28),
            onPressed: () => _shiftMonth(1),
            tooltip: 'Next month',
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 2. Spending Summary Card
  // ---------------------------------------------------------------------------
  Widget _buildSpendingSummaryCard(
    BuildContext context,
    bool isDark,
    AnalyticsSummaryModel summary,
  ) {
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(20),
        boxShadow: isDark
            ? []
            : [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'TOTAL SPENT',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.0,
              color: textSecondary,
            ),
          ),
          const SizedBox(height: 6),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                'Rs ',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: textPrimary,
                ),
              ),
              Text(
                NumberFormat('#,##0.00').format(summary.spentAmount),
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w800,
                  color: textPrimary,
                ),
              ),
              const Spacer(),
              if (summary.monthOverMonth != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: summary.monthOverMonth!.increased
                        ? AppTokens.error.withOpacity(0.12)
                        : AppTokens.success.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        summary.monthOverMonth!.increased
                            ? Icons.arrow_upward_rounded
                            : Icons.arrow_downward_rounded,
                        size: 14,
                        color: summary.monthOverMonth!.increased
                            ? AppTokens.error
                            : AppTokens.success,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '${summary.monthOverMonth!.percentageChange.abs().toStringAsFixed(1)}%',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: summary.monthOverMonth!.increased
                              ? AppTokens.error
                              : AppTokens.success,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _buildSmallMetric(
                'Received',
                'Rs ${NumberFormat('#,##0.00').format(summary.receivedAmount)}',
                AppTokens.success,
                textSecondary,
              ),
              if (summary.pendingCount > 0)
                _buildSmallMetric(
                  'Pending (${summary.pendingCount})',
                  'Rs ${NumberFormat('#,##0.00').format(summary.pendingSpentAmount)}',
                  AppTokens.warning,
                  textSecondary,
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSmallMetric(String label, String value, Color valueColor, Color labelColor) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: TextStyle(fontSize: 12, color: labelColor)),
        const SizedBox(height: 2),
        Text(
          value,
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: valueColor),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // 3. Budget Progress Bar Card
  // ---------------------------------------------------------------------------
  Widget _buildBudgetCard(
    BuildContext context,
    bool isDark,
    AnalyticsSummaryModel summary,
    String currentMonth,
  ) {
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    final hasBudget = summary.budgetTotalMinor != null && summary.budgetTotalMinor! > 0;
    final usedPct = summary.budgetUsedPercentage ?? 0.0;
    final progress = (usedPct / 100.0).clamp(0.0, 1.0);

    Color progressColor = AppTokens.success;
    if (usedPct >= 100.0) {
      progressColor = AppTokens.error;
    } else if (usedPct >= 80.0) {
      progressColor = AppTokens.warning;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(20),
        boxShadow: isDark
            ? []
            : [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(Icons.pie_chart_outline_rounded, size: 20, color: progressColor),
                  const SizedBox(width: 8),
                  Text(
                    'Monthly Budget',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: textPrimary,
                    ),
                  ),
                ],
              ),
              InkWell(
                key: AppTestKeys.budgetEdit,
                onTap: () => _showBudgetEditSheet(context, summary, currentMonth),
                borderRadius: BorderRadius.circular(12),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  child: Row(
                    children: [
                      Icon(Icons.edit_outlined, size: 16, color: AppTokens.primaryViolet),
                      const SizedBox(width: 4),
                      Text(
                        hasBudget ? 'Edit' : 'Set Budget',
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: AppTokens.primaryViolet,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (hasBudget) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 8,
                backgroundColor: isDark ? Colors.white10 : Colors.black.withOpacity(0.06),
                valueColor: AlwaysStoppedAnimation<Color>(progressColor),
              ),
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${usedPct.toStringAsFixed(1)}% used',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: progressColor,
                  ),
                ),
                Text(
                  'Rs ${NumberFormat('#,##0').format(summary.spentAmount)} of Rs ${NumberFormat('#,##0').format(summary.budgetTotalAmount ?? 0)}',
                  style: TextStyle(fontSize: 12, color: textSecondary),
                ),
              ],
            ),
          ] else ...[
            Text(
              'No budget set for this month. Set a budget to track spending limits and get timely alerts.',
              style: TextStyle(fontSize: 12, color: textSecondary),
            ),
          ],
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 4. Categories Donut / Bar & List
  // ---------------------------------------------------------------------------
  Widget _buildCategorySection(
    BuildContext context,
    bool isDark,
    AnalyticsSummaryModel summary,
  ) {
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    final categories = summary.byCategoryTotals;
    if (categories.isEmpty) {
      return const SizedBox.shrink();
    }

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(20),
        boxShadow: isDark
            ? []
            : [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'SPENDING BY CATEGORY',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.0,
              color: textSecondary,
            ),
          ),
          const SizedBox(height: 16),

          // Accessible Multi-Segment Category Bar
          _buildCategorySegmentBar(categories),
          const SizedBox(height: 16),

          // Accessible Text List underneath
          ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: categories.length,
            separatorBuilder: (_, __) => const Divider(height: 16),
            itemBuilder: (context, index) {
              final cat = categories[index];
              return InkWell(
                key: AppTestKeys.cat(cat.category.toLowerCase()),
                onTap: () {
                  setState(() {
                    _selectedCategory = (_selectedCategory == cat.category) ? 'ALL' : cat.category;
                  });
                },
                child: Row(
                  children: [
                    Container(
                      width: 12,
                      height: 12,
                      decoration: BoxDecoration(
                        color: cat.color,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            cat.displayName,
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: textPrimary,
                            ),
                          ),
                          Text(
                            '${cat.count} txns • ${cat.percentage.toStringAsFixed(1)}%',
                            style: TextStyle(fontSize: 12, color: textSecondary),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      'Rs ${NumberFormat('#,##0.00').format(cat.totalAmount)}',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: textPrimary,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildCategorySegmentBar(List<CategoryTotalModel> categories) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(6),
      child: SizedBox(
        height: 12,
        child: Row(
          children: categories.map((cat) {
            final flex = (cat.percentage * 10).toInt().clamp(1, 1000);
            return Expanded(
              flex: flex,
              child: Container(color: cat.color),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 5. Filter Chips
  // ---------------------------------------------------------------------------
  Widget _buildFilterChips(bool isDark) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          _buildDirectionChip('ALL', 'All Transactions'),
          const SizedBox(width: 8),
          _buildDirectionChip('DEBIT', 'Spent'),
          const SizedBox(width: 8),
          _buildDirectionChip('CREDIT', 'Received'),
          if (_selectedCategory != 'ALL') ...[
            const SizedBox(width: 12),
            Chip(
              label: Text('Category: $_selectedCategory'),
              onDeleted: () => setState(() => _selectedCategory = 'ALL'),
              backgroundColor: AppTokens.primaryViolet.withOpacity(0.15),
              labelStyle: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: AppTokens.primaryViolet,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildDirectionChip(String code, String label) {
    final isSelected = _selectedDirection == code;
    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (selected) {
        if (selected) {
          setState(() => _selectedDirection = code);
        }
      },
      selectedColor: AppTokens.primaryViolet,
      labelStyle: TextStyle(
        fontSize: 13,
        fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
        color: isSelected ? Colors.white : null,
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 6. Transactions List
  // ---------------------------------------------------------------------------
  Widget _buildTransactionsList(
    BuildContext context,
    bool isDark,
    List<ActivityItemModel> items,
    String formattedMonth,
  ) {
    final cardBg = isDark ? AppTokens.cardDark : Colors.white;
    final textPrimary = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final textSecondary = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;

    if (items.isEmpty) {
      return Container(
        padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 20),
        decoration: BoxDecoration(
          color: cardBg,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Center(
          child: Column(
            children: [
              Icon(Icons.receipt_long_outlined, size: 48, color: textSecondary.withOpacity(0.5)),
              const SizedBox(height: 12),
              Text(
                'No spending in $formattedMonth',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: textPrimary,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                'Transactions performed in this month will appear here.',
                style: TextStyle(fontSize: 13, color: textSecondary),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      );
    }

    return Material(
      color: cardBg,
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: ListView.separated(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: items.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final item = items[index];
          final isCredit = item.direction == 'CREDIT';
          final amountColor = isCredit ? AppTokens.success : textPrimary;
          final prefix = isCredit ? '+' : '-';

          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            leading: CircleAvatar(
              backgroundColor: item.color.withOpacity(0.15),
              child: Icon(
                item.type == 'BILL' ? Icons.receipt_rounded : Icons.swap_horiz_rounded,
                color: item.color,
                size: 20,
              ),
            ),
            title: Text(
              item.title,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: textPrimary,
              ),
            ),
            subtitle: Text(
              '${DateFormat('MMM d, h:mm a').format(item.createdAt)} • ${item.category}',
              style: TextStyle(fontSize: 12, color: textSecondary),
            ),
            trailing: Text(
              '$prefix Rs ${NumberFormat('#,##0.00').format(item.amount)}',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w800,
                color: amountColor,
              ),
            ),
            onTap: () => _showReceiptSheet(context, item),
          );
        },
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 7. Modals: Budget Edit & Receipt Detail
  // ---------------------------------------------------------------------------
  void _showBudgetEditSheet(
    BuildContext context,
    AnalyticsSummaryModel summary,
    String currentMonth,
  ) {
    final controller = TextEditingController(
      text: summary.budgetTotalAmount != null && summary.budgetTotalAmount! > 0
          ? summary.budgetTotalAmount!.toInt().toString()
          : '',
    );

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetCtx) {
        return Padding(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 24,
            bottom: MediaQuery.of(sheetCtx).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'Set Monthly Budget',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              Text(
                'Set a spending limit for ${_formatMonthTitle(currentMonth)}. You will be notified when reaching 80% and 100%.',
                style: const TextStyle(fontSize: 13, color: Colors.grey),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: controller,
                keyboardType: TextInputType.number,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Budget Amount (LKR)',
                  prefixText: 'Rs ',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTokens.primaryViolet,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                onPressed: () async {
                  final text = controller.text.trim();
                  final val = int.tryParse(text);
                  if (val != null && val > 0) {
                    final minor = val * 100;
                    Navigator.of(sheetCtx).pop();
                    await ref.read(analyticsServiceProvider).setBudget(currentMonth, minor);
                    ref.invalidate(analyticsSummaryProvider(currentMonth));
                    ref.invalidate(budgetProvider(currentMonth));
                  }
                },
                child: const Text('Save Budget', style: TextStyle(fontWeight: FontWeight.w700, color: Colors.white)),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showReceiptSheet(BuildContext context, ActivityItemModel item) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetCtx) {
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Transaction Receipt', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppTokens.success.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      item.status,
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: AppTokens.success,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              Center(
                child: Column(
                  children: [
                    Text(
                      'Rs ${NumberFormat('#,##0.00').format(item.amount)}',
                      style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w900),
                    ),
                    const SizedBox(height: 4),
                    Text(item.title, style: const TextStyle(fontSize: 14, color: Colors.grey)),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              const Divider(),
              _buildReceiptRow('Reference', item.reference),
              _buildReceiptRow('Date', DateFormat('yyyy-MM-dd HH:mm').format(item.createdAt)),
              _buildReceiptRow('Category', item.category),
              _buildReceiptRow('Funding', item.fundingType),
              if (item.cardLast4 != null)
                _buildReceiptRow('Card', '•••• ${item.cardLast4}'),
              if (item.bankCode != null)
                _buildReceiptRow('Bank', item.bankCode!),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      key: AppTestKeys.receiptDownload,
                      icon: const Icon(Icons.download_rounded, size: 18),
                      label: const Text('Download PDF'),
                      onPressed: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Downloading statement PDF...')),
                        );
                        Navigator.of(sheetCtx).pop();
                      },
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton.icon(
                      key: AppTestKeys.receiptShare,
                      style: ElevatedButton.styleFrom(backgroundColor: AppTokens.primaryViolet),
                      icon: const Icon(Icons.share_rounded, size: 18, color: Colors.white),
                      label: const Text('Share Receipt', style: TextStyle(color: Colors.white)),
                      onPressed: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Receipt shared successfully')),
                        );
                        Navigator.of(sheetCtx).pop();
                      },
                    ),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildReceiptRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 13, color: Colors.grey)),
          Text(value, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }

  Widget _buildErrorBanner(bool isDark, String message) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTokens.error.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text('Error: $message', style: const TextStyle(color: AppTokens.error)),
    );
  }

  void _shiftMonth(int delta) {
    final current = ref.read(selectedMonthProvider);
    try {
      final parts = current.split('-');
      var year = int.parse(parts[0]);
      var month = int.parse(parts[1]) + delta;
      if (month < 1) {
        month = 12;
        year -= 1;
      } else if (month > 12) {
        month = 1;
        year += 1;
      }
      final nextMonthStr = month < 10 ? '0$month' : '$month';
      ref.read(selectedMonthProvider.notifier).setMonth('$year-$nextMonthStr');
    } catch (_) {}
  }

  String _formatMonthTitle(String ym) {
    try {
      final parts = ym.split('-');
      final year = int.parse(parts[0]);
      final month = int.parse(parts[1]);
      final date = DateTime(year, month);
      return DateFormat('MMMM yyyy').format(date);
    } catch (_) {
      return ym;
    }
  }
}
