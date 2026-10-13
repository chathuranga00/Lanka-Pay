import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/scheduled_service.dart';
import '../domain/scheduled_models.dart';

class ScheduledListScreen extends ConsumerStatefulWidget {
  const ScheduledListScreen({super.key});

  @override
  ConsumerState<ScheduledListScreen> createState() => _ScheduledListScreenState();
}

class _ScheduledListScreenState extends ConsumerState<ScheduledListScreen> {
  ScheduleStatus? _selectedStatusFilter;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final schedulesAsync = ref.watch(scheduledListProvider);

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Scheduled Payments',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        elevation: 0,
        backgroundColor: Colors.transparent,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: 'Refresh',
            onPressed: () => ref.read(scheduledListProvider.notifier).refresh(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        key: AppTestKeys.schedCreate,
        onPressed: () => context.push('/scheduled/create'),
        backgroundColor: AppTokens.primaryBlue,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add_rounded),
        label: const Text(
          'New Schedule',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(scheduledListProvider.notifier).refresh(),
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            // Status filter chips
            SliverToBoxAdapter(
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(
                  children: [
                    _buildFilterChip('All', null, isDark),
                    const SizedBox(width: 8),
                    _buildFilterChip('Active', ScheduleStatus.active, isDark),
                    const SizedBox(width: 8),
                    _buildFilterChip('Paused', ScheduleStatus.paused, isDark),
                    const SizedBox(width: 8),
                    _buildFilterChip('Completed', ScheduleStatus.completed, isDark),
                  ],
                ),
              ),
            ),

            // Schedule items
            schedulesAsync.when(
              loading: () => const SliverFillRemaining(
                child: Center(child: CircularProgressIndicator.adaptive()),
              ),
              error: (err, _) => SliverFillRemaining(
                child: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.error_outline_rounded, size: 48, color: AppTokens.error),
                      const SizedBox(height: 12),
                      Text(
                        'Failed to load scheduled payments',
                        style: TextStyle(
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 12),
                      FilledButton.tonal(
                        onPressed: () => ref.read(scheduledListProvider.notifier).refresh(),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              ),
              data: (schedules) {
                final filtered = _selectedStatusFilter == null
                    ? schedules
                    : schedules.where((s) => s.status == _selectedStatusFilter).toList();

                if (filtered.isEmpty) {
                  return SliverFillRemaining(
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Container(
                              width: 72,
                              height: 72,
                              decoration: BoxDecoration(
                                color: AppTokens.primaryBlue.withOpacity(0.1),
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(
                                Icons.calendar_month_rounded,
                                size: 36,
                                color: AppTokens.primaryBlue,
                              ),
                            ),
                            const SizedBox(height: 20),
                            Text(
                              'No scheduled payments',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w700,
                                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Set up automated recurring transfers and bill payments without manual effort.',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 14,
                                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                              ),
                            ),
                            const SizedBox(height: 24),
                            FilledButton.icon(
                              key: const Key('empty_sched_create_btn'),
                              onPressed: () => context.push('/scheduled/create'),
                              icon: const Icon(Icons.add_rounded),
                              label: const Text('Create Schedule'),
                              style: FilledButton.styleFrom(
                                backgroundColor: AppTokens.primaryBlue,
                                foregroundColor: Colors.white,
                                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                }

                return SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final item = filtered[index];
                        return _buildScheduleCard(context, item, isDark);
                      },
                      childCount: filtered.length,
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFilterChip(String label, ScheduleStatus? status, bool isDark) {
    final isSelected = _selectedStatusFilter == status;
    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (_) {
        setState(() {
          _selectedStatusFilter = status;
        });
      },
      selectedColor: AppTokens.primaryBlue.withOpacity(0.18),
      labelStyle: TextStyle(
        fontSize: 13,
        fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
        color: isSelected
            ? AppTokens.primaryBlue
            : (isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight),
      ),
    );
  }

  Widget _buildScheduleCard(BuildContext context, ScheduledPaymentModel item, bool isDark) {
    return Container(
      key: AppTestKeys.sched(item.id),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => context.push('/scheduled/details', extra: item),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Icon
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: (item.type == ScheduleType.transfer
                                ? AppTokens.primaryBlue
                                : const Color(0xFFF59E0B))
                            .withOpacity(0.12),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Icon(
                        item.type.icon,
                        color: item.type == ScheduleType.transfer
                            ? AppTokens.primaryBlue
                            : const Color(0xFFF59E0B),
                        size: 24,
                      ),
                    ),
                    const SizedBox(width: 12),

                    // Payee Info
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.payee.displayTitle,
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                              color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            item.payee.displaySubtitle.isNotEmpty
                                ? item.payee.displaySubtitle
                                : item.type.displayName,
                            style: TextStyle(
                              fontSize: 13,
                              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),

                    // Status Badge
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: item.status.badgeBgColor,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        item.status.displayName,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: item.status.badgeColor,
                        ),
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 14),
                const Divider(height: 1),
                const SizedBox(height: 12),

                // Bottom row: Amount & Next Run Date
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Amount',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                            color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          item.formattedAmount,
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                          ),
                        ),
                      ],
                    ),

                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          'Next Run (${item.frequency.displayName})',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                            color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.event_outlined,
                              size: 14,
                              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                            ),
                            const SizedBox(width: 4),
                            Text(
                              item.formattedNextRun,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ],
                ),

                // Failure warning banner if consecutive failures > 0
                if (item.consecutiveFailures > 0) ...[
                  const SizedBox(height: 10),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppTokens.error.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppTokens.error.withOpacity(0.2)),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.warning_amber_rounded, size: 16, color: AppTokens.error),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            item.consecutiveFailures >= 2
                                ? 'Auto-paused after 2 failed runs. Check details.'
                                : 'Last run failed: ${item.lastFailureReason ?? "Payment declined"}',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: AppTokens.error,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
