import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/bill_service.dart';
import '../domain/bill_models.dart';

class BillsHistoryScreen extends ConsumerStatefulWidget {
  const BillsHistoryScreen({super.key});

  @override
  ConsumerState<BillsHistoryScreen> createState() => _BillsHistoryScreenState();
}

class _BillsHistoryScreenState extends ConsumerState<BillsHistoryScreen> {
  String _selectedStatusFilter = 'ALL';

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final paymentsAsync = ref.watch(billPaymentsProvider);

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Bill Payments History',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: Column(
        children: [
          // Filter Chips Row
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: SizedBox(
              key: AppTestKeys.billHistoryFilter,
              height: 40,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  _buildFilterChip('ALL', 'All', isDark),
                  const SizedBox(width: 8),
                  _buildFilterChip('COMPLETED', 'Completed', isDark),
                  const SizedBox(width: 8),
                  _buildFilterChip('PENDING', 'Pending', isDark),
                  const SizedBox(width: 8),
                  _buildFilterChip('FAILED', 'Failed', isDark),
                  const SizedBox(width: 8),
                  _buildFilterChip('REVERSED', 'Reversed', isDark),
                ],
              ),
            ),
          ),
          const Divider(height: 1),

          // Payments List
          Expanded(
            child: paymentsAsync.when(
              data: (payments) {
                final filtered = payments.where((p) {
                  if (_selectedStatusFilter == 'ALL') return true;
                  if (_selectedStatusFilter == 'PENDING') {
                    return p.status == 'PENDING' || p.status == 'REQUIRES_RECONCILIATION';
                  }
                  return p.status == _selectedStatusFilter;
                }).toList();

                if (filtered.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.receipt_long_outlined,
                          size: 48,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          'No bill payments found',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                            color: isDark ? Colors.white70 : Colors.black54,
                          ),
                        ),
                      ],
                    ),
                  );
                }

                return ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  itemCount: filtered.length,
                  separatorBuilder: (ctx, i) => const SizedBox(height: 10),
                  itemBuilder: (ctx, index) {
                    final item = filtered[index];
                    return _buildPaymentCard(context, item, isDark);
                  },
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (err, stack) => Center(
                child: Text('Failed to load history: $err'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String filterKey, String label, bool isDark) {
    final isSelected = _selectedStatusFilter == filterKey;
    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (_) {
        setState(() => _selectedStatusFilter = filterKey);
      },
    );
  }

  Widget _buildPaymentCard(BuildContext context, BillPaymentReceiptModel item, bool isDark) {
    Color badgeColor;
    if (item.isCompleted) {
      badgeColor = const Color(0xFF10B981);
    } else if (item.isPending) {
      badgeColor = const Color(0xFFF59E0B);
    } else if (item.isReversed) {
      badgeColor = const Color(0xFF8B5CF6);
    } else {
      badgeColor = AppTokens.error;
    }

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
      ),
      child: Material(
        color: isDark ? AppTokens.cardDark : Colors.white,
        borderRadius: BorderRadius.circular(16),
        clipBehavior: Clip.antiAlias,
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          leading: Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: item.category.color.withOpacity(0.12),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(item.category.icon, color: item.category.color, size: 22),
          ),
          title: Text(
            item.billerName,
            style: TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 15,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
          ),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 2),
              Text(
                '${item.maskedReference} • ${item.receiptNumber}',
                style: TextStyle(
                  fontSize: 12,
                  fontFamily: 'RobotoMono',
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: badgeColor.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  item.status,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    color: badgeColor,
                  ),
                ),
              ),
            ],
          ),
          trailing: Text(
            'LKR ${item.totalAmount.toStringAsFixed(2)}',
            style: TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 15,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
          ),
          onTap: () {
            context.push('/bills/result', extra: {'receipt': item});
          },
        ),
      ),
    );
  }
}
