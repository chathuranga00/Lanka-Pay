import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/scheduled_service.dart';
import '../domain/scheduled_models.dart';

class ScheduleDetailsScreen extends ConsumerStatefulWidget {
  final ScheduledPaymentModel schedule;

  const ScheduleDetailsScreen({
    super.key,
    required this.schedule,
  });

  @override
  ConsumerState<ScheduleDetailsScreen> createState() => _ScheduleDetailsScreenState();
}

class _ScheduleDetailsScreenState extends ConsumerState<ScheduleDetailsScreen> {
  late ScheduledPaymentModel _currentSchedule;
  bool _isActionInProgress = false;

  @override
  void initState() {
    super.initState();
    _currentSchedule = widget.schedule;
  }

  Future<void> _refreshDetails() async {
    try {
      final updated = await ref.read(scheduledServiceProvider).getSchedule(_currentSchedule.id);
      if (mounted) {
        setState(() {
          _currentSchedule = updated;
        });
      }
    } catch (_) {}
  }

  Future<void> _confirmPause() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Pause Scheduled Payment?'),
        content: const Text(
          'Future automated runs will be paused. You can resume this schedule at any time before its end date.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            key: AppTestKeys.schedConfirmDialogBtn,
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: AppTokens.warning),
            child: const Text('Pause Schedule'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      setState(() => _isActionInProgress = true);
      try {
        await ref.read(scheduledListProvider.notifier).pause(_currentSchedule.id);
        await _refreshDetails();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Scheduled payment paused.'),
              backgroundColor: AppTokens.warning,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to pause: $e'), backgroundColor: AppTokens.error),
          );
        }
      } finally {
        if (mounted) setState(() => _isActionInProgress = false);
      }
    }
  }

  Future<void> _confirmResume() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Resume Scheduled Payment?'),
        content: const Text(
          'Automated payments will resume on the next scheduled run date.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            key: AppTestKeys.schedConfirmDialogBtn,
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: AppTokens.primaryBlue),
            child: const Text('Resume Schedule'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      setState(() => _isActionInProgress = true);
      try {
        await ref.read(scheduledListProvider.notifier).resume(_currentSchedule.id);
        await _refreshDetails();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Scheduled payment resumed.'),
              backgroundColor: AppTokens.success,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to resume: $e'), backgroundColor: AppTokens.error),
          );
        }
      } finally {
        if (mounted) setState(() => _isActionInProgress = false);
      }
    }
  }

  Future<void> _confirmCancel() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cancel Scheduled Payment?'),
        content: const Text(
          'Are you sure you want to cancel this scheduled payment? This cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Keep Schedule'),
          ),
          FilledButton(
            key: AppTestKeys.schedConfirmDialogBtn,
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: AppTokens.error),
            child: const Text('Cancel Schedule'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      setState(() => _isActionInProgress = true);
      try {
        await ref.read(scheduledListProvider.notifier).cancel(_currentSchedule.id);
        await _refreshDetails();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Scheduled payment cancelled.'),
              backgroundColor: AppTokens.error,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to cancel: $e'), backgroundColor: AppTokens.error),
          );
        }
      } finally {
        if (mounted) setState(() => _isActionInProgress = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final runsAsync = ref.watch(scheduledRunsProvider(_currentSchedule.id));

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Schedule Details',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            await _refreshDetails();
            ref.invalidate(scheduledRunsProvider(_currentSchedule.id));
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Top Header Card
                Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                    ),
                  ),
                  child: Column(
                    children: [
                      // Type Icon + Status
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: (_currentSchedule.type == ScheduleType.transfer
                                      ? AppTokens.primaryBlue
                                      : const Color(0xFFF59E0B))
                                  .withOpacity(0.12),
                              borderRadius: BorderRadius.circular(14),
                            ),
                            child: Icon(
                              _currentSchedule.type.icon,
                              color: _currentSchedule.type == ScheduleType.transfer
                                  ? AppTokens.primaryBlue
                                  : const Color(0xFFF59E0B),
                              size: 28,
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                            decoration: BoxDecoration(
                              color: _currentSchedule.status.badgeBgColor,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              _currentSchedule.status.displayName,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: _currentSchedule.status.badgeColor,
                              ),
                            ),
                          ),
                        ],
                      ),

                      const SizedBox(height: 16),

                      // Payee Title
                      Text(
                        _currentSchedule.payee.displayTitle,
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      if (_currentSchedule.payee.displaySubtitle.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          _currentSchedule.payee.displaySubtitle,
                          style: TextStyle(
                            fontSize: 14,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],

                      const SizedBox(height: 16),

                      // Amount
                      Text(
                        _currentSchedule.formattedAmount,
                        style: TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.w900,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${_currentSchedule.frequency.displayName} payment',
                        style: TextStyle(
                          fontSize: 13,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                      ),
                    ],
                  ),
                ),

                // Failure warning if consecutive failures > 0
                if (_currentSchedule.consecutiveFailures > 0) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: AppTokens.error.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppTokens.error.withOpacity(0.25)),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(Icons.warning_amber_rounded, color: AppTokens.error, size: 22),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                _currentSchedule.consecutiveFailures >= 2
                                    ? 'Schedule Auto-Paused'
                                    : 'Execution Warning',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w700,
                                  color: AppTokens.error,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                _currentSchedule.consecutiveFailures >= 2
                                    ? 'This schedule was paused after 2 consecutive failures to prevent unexpected charges. Last reason: ${_currentSchedule.lastFailureReason ?? "Failed"}'
                                    : 'Last scheduled run failed: ${_currentSchedule.lastFailureReason ?? "Failed"}. The system will attempt on the next scheduled run.',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: AppTokens.error,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],

                const SizedBox(height: 20),

                // Schedule Details Table Card
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Schedule Information',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                      const SizedBox(height: 12),
                      _buildDetailRow('Schedule ID', _currentSchedule.id, isDark),
                      const Divider(height: 16),
                      _buildDetailRow('Payment Type', _currentSchedule.type.displayName, isDark),
                      const Divider(height: 16),
                      _buildDetailRow('Frequency', _currentSchedule.frequency.displayName, isDark),
                      const Divider(height: 16),
                      _buildDetailRow('Next Run Date', _currentSchedule.formattedNextRun, isDark),
                      const Divider(height: 16),
                      _buildDetailRow('Start Date', _currentSchedule.startDate, isDark),
                      const Divider(height: 16),
                      _buildDetailRow('End Date', _currentSchedule.endDate ?? 'Until cancelled', isDark),
                      if (_currentSchedule.fundingSource != null) ...[
                        const Divider(height: 16),
                        _buildDetailRow('Funding Source', _currentSchedule.fundingSource!, isDark),
                      ],
                    ],
                  ),
                ),

                const SizedBox(height: 20),

                // Action Buttons: Pause / Resume / Cancel
                if (_currentSchedule.canPause || _currentSchedule.canResume || _currentSchedule.canCancel) ...[
                  Row(
                    children: [
                      if (_currentSchedule.canPause)
                        Expanded(
                          child: OutlinedButton.icon(
                            key: AppTestKeys.schedPause,
                            onPressed: _isActionInProgress ? null : _confirmPause,
                            icon: const Icon(Icons.pause_circle_outline_rounded),
                            label: const Text('Pause'),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTokens.warning,
                              side: BorderSide(color: AppTokens.warning.withOpacity(0.5)),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ),
                      if (_currentSchedule.canResume)
                        Expanded(
                          child: FilledButton.icon(
                            key: AppTestKeys.schedResume,
                            onPressed: _isActionInProgress ? null : _confirmResume,
                            icon: const Icon(Icons.play_circle_outline_rounded),
                            label: const Text('Resume'),
                            style: FilledButton.styleFrom(
                              backgroundColor: AppTokens.primaryBlue,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ),
                      if (_currentSchedule.canCancel) ...[
                        const SizedBox(width: 12),
                        Expanded(
                          child: OutlinedButton.icon(
                            key: AppTestKeys.schedCancel,
                            onPressed: _isActionInProgress ? null : _confirmCancel,
                            icon: const Icon(Icons.cancel_outlined),
                            label: const Text('Cancel'),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTokens.error,
                              side: BorderSide(color: AppTokens.error.withOpacity(0.5)),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 24),
                ],

                // Run History Section
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Run History',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.refresh_rounded, size: 20),
                      onPressed: () => ref.invalidate(scheduledRunsProvider(_currentSchedule.id)),
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                runsAsync.when(
                  loading: () => const Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(child: CircularProgressIndicator.adaptive()),
                  ),
                  error: (err, _) => Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                      ),
                    ),
                    child: Center(
                      child: Text(
                        'Failed to load run history',
                        style: TextStyle(
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                      ),
                    ),
                  ),
                  data: (runs) {
                    if (runs.isEmpty) {
                      return Container(
                        padding: const EdgeInsets.all(24),
                        decoration: BoxDecoration(
                          color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(
                            color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                          ),
                        ),
                        child: Center(
                          child: Column(
                            children: [
                              Icon(
                                Icons.history_rounded,
                                size: 36,
                                color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'No runs yet',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'Automated executions will appear here once processed.',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
                                ),
                                textAlign: TextAlign.center,
                              ),
                            ],
                          ),
                        ),
                      );
                    }

                    return Column(
                      children: runs.map((run) => _buildRunCard(run, isDark)).toList(),
                    );
                  },
                ),

                const SizedBox(height: 32),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDetailRow(String label, String value, bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 13,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        Text(
          value,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
      ],
    );
  }

  Widget _buildRunCard(ScheduledRunModel run, bool isDark) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: run.isSuccess
                  ? AppTokens.success.withOpacity(0.12)
                  : AppTokens.error.withOpacity(0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(
              run.isSuccess ? Icons.check_circle_outline_rounded : Icons.cancel_outlined,
              color: run.isSuccess ? AppTokens.success : AppTokens.error,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      run.runDate,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: run.isSuccess
                            ? AppTokens.success.withOpacity(0.12)
                            : AppTokens.error.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        run.status,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: run.isSuccess ? AppTokens.success : AppTokens.error,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                if (run.amount != null) ...[
                  Text(
                    run.formattedAmount,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                ],
                if (run.transactionId != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    'Transaction ID: #${run.transactionId}',
                    style: TextStyle(
                      fontSize: 11,
                      color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
                    ),
                  ),
                ],
                if (run.failureReason != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    run.failureReason!,
                    style: TextStyle(
                      fontSize: 12,
                      color: AppTokens.error,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
