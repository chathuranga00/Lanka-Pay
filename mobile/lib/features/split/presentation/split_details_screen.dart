import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/split_service.dart';
import '../domain/split_models.dart';

class SplitDetailsScreen extends ConsumerStatefulWidget {
  final String splitId;
  final SplitModel? initialSplit;

  const SplitDetailsScreen({
    super.key,
    required this.splitId,
    this.initialSplit,
  });

  @override
  ConsumerState<SplitDetailsScreen> createState() => _SplitDetailsScreenState();
}

class _SplitDetailsScreenState extends ConsumerState<SplitDetailsScreen> {
  SplitModel? _split;
  bool _isLoading = true;
  String? _errorMessage;
  String? _actionMessage;
  bool _actionInProgress = false;

  @override
  void initState() {
    super.initState();
    _split = widget.initialSplit;
    _fetchDetails();
  }

  Future<void> _fetchDetails() async {
    setState(() {
      _isLoading = _split == null;
      _errorMessage = null;
    });

    try {
      final service = ref.read(splitServiceProvider);
      final data = await service.getSplit(widget.splitId);
      if (mounted) {
        setState(() {
          _split = data;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceAll('Exception: ', '');
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _sendReminder(SplitParticipantModel participant) async {
    final pId = participant.userId;
    if (pId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Cannot remind unregistered participant')),
      );
      return;
    }

    setState(() {
      _actionInProgress = true;
      _actionMessage = null;
    });

    try {
      final service = ref.read(splitServiceProvider);
      final updated = await service.remindParticipant(widget.splitId, pId);
      if (mounted) {
        setState(() {
          _split = updated;
          _actionMessage = 'Reminder sent to ${participant.displayName ?? participant.phone}';
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(_actionMessage!),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        final err = e.toString().replaceAll('Exception: ', '');
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(err.contains('24 hours') ? 'Rate limit: Only 1 reminder every 24 hours' : err),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _actionInProgress = false;
        });
      }
    }
  }

  Future<void> _cancelSplit() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cancel Split?'),
        content: const Text(
          'This will cancel all unpaid shares and invalidate their payment requests.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Keep Active'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Cancel Split'),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    setState(() {
      _actionInProgress = true;
    });

    try {
      final service = ref.read(splitServiceProvider);
      final updated = await service.cancelSplit(widget.splitId);
      if (mounted) {
        setState(() {
          _split = updated;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Split has been cancelled')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString()), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _actionInProgress = false;
        });
      }
    }
  }

  Color _getStatusColor(ParticipantStatus status) {
    switch (status) {
      case ParticipantStatus.paid:
        return Colors.green;
      case ParticipantStatus.declined:
        return Colors.red;
      case ParticipantStatus.expired:
        return Colors.orange;
      case ParticipantStatus.cancelled:
        return Colors.grey;
      case ParticipantStatus.invited:
        return AppTokens.primaryBlue;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      key: TestKeys.split(widget.splitId),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: Text(_split?.title ?? 'Split Details'),
        elevation: 0,
        backgroundColor: Colors.transparent,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _fetchDetails,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(AppTokens.spacing24),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline, size: 48, color: Colors.red),
                        const SizedBox(height: AppTokens.spacing16),
                        Text(_errorMessage!, textAlign: TextAlign.center),
                        const SizedBox(height: AppTokens.spacing16),
                        FilledButton(onPressed: _fetchDetails, child: const Text('Retry')),
                      ],
                    ),
                  ),
                )
              : _buildContent(context, isDark),
    );
  }

  Widget _buildContent(BuildContext context, bool isDark) {
    final split = _split!;
    final totalParticipants = split.participants.length;
    final paidCount = split.paidCount;
    final progressText = '$paidCount of $totalParticipants paid';
    final progress = totalParticipants > 0 ? (paidCount / totalParticipants) : 0.0;

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(AppTokens.spacing16),
        children: [
          // Header Card with Progress
          Card(
            key: TestKeys.splitProgress,
            elevation: 0,
            color: isDark ? AppTokens.cardDark : AppTokens.tileTintLight,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(AppTokens.radiusLg),
            ),
            child: Padding(
              padding: const EdgeInsets.all(AppTokens.spacing20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Text(
                          split.title,
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppTokens.spacing12,
                          vertical: AppTokens.spacing4,
                        ),
                        decoration: BoxDecoration(
                          color: split.status == SplitStatus.completed
                              ? Colors.green.withOpacity(0.15)
                              : split.status == SplitStatus.cancelled
                                  ? Colors.red.withOpacity(0.15)
                                  : AppTokens.primaryBlue.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(AppTokens.radiusFull),
                        ),
                        child: Text(
                          split.status.label,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: split.status == SplitStatus.completed
                                ? Colors.green
                                : split.status == SplitStatus.cancelled
                                    ? Colors.red
                                    : AppTokens.primaryBlueDark,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppTokens.spacing12),
                  Text(
                    'Total Bill: LKR ${split.totalAmount.toStringAsFixed(2)}',
                    style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                      color: AppTokens.primaryBlueDark,
                    ),
                  ),
                  const SizedBox(height: AppTokens.spacing4),
                  Text(
                    'Your share (including remainder): LKR ${split.ownerShareAmount.toStringAsFixed(2)}',
                    style: TextStyle(
                      fontSize: 13,
                      color: isDark ? Colors.grey[300] : Colors.grey[700],
                    ),
                  ),
                  const SizedBox(height: AppTokens.spacing16),

                  // Progress Bar
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        progressText,
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                      ),
                      Text(
                        '${(progress * 100).toInt()}%',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppTokens.spacing8),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(AppTokens.radiusFull),
                    child: LinearProgressIndicator(
                      value: progress,
                      minHeight: 8,
                      backgroundColor: Colors.grey.withOpacity(0.2),
                      valueColor: AlwaysStoppedAnimation<Color>(
                        progress == 1.0 ? Colors.green : AppTokens.primaryBlue,
                      ),
                    ),
                  ),
                  const SizedBox(height: AppTokens.spacing8),
                  Text(
                    'Collected: LKR ${split.collectedAmount.toStringAsFixed(2)} of ${(split.totalAmount - split.ownerShareAmount).toStringAsFixed(2)} requested',
                    style: TextStyle(
                      fontSize: 12,
                      color: isDark ? Colors.grey[400] : Colors.grey[600],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppTokens.spacing20),

          // Participants Section
          Text(
            'Participants (${split.participants.length})',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
          const SizedBox(height: AppTokens.spacing12),

          ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: split.participants.length,
            separatorBuilder: (_, _) => const SizedBox(height: AppTokens.spacing8),
            itemBuilder: (context, index) {
              final p = split.participants[index];
              final statusColor = _getStatusColor(p.status);
              final idKey = p.userId ?? p.phone ?? index;

              return Card(
                elevation: 0,
                color: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(AppTokens.spacing12),
                  child: Row(
                    children: [
                      CircleAvatar(
                        backgroundColor: statusColor.withOpacity(0.15),
                        child: Icon(
                          p.status == ParticipantStatus.paid
                              ? Icons.check
                              : p.status == ParticipantStatus.declined
                                  ? Icons.close
                                  : Icons.person_outline,
                          color: statusColor,
                          size: 20,
                        ),
                      ),
                      const SizedBox(width: AppTokens.spacing12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              p.displayName ?? p.phone ?? 'Participant',
                              style: const TextStyle(fontWeight: FontWeight.bold),
                            ),
                            Text(
                              p.phone ?? '',
                              style: TextStyle(
                                fontSize: 12,
                                color: isDark ? Colors.grey[400] : Colors.grey[600],
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Share: LKR ${p.shareAmount.toStringAsFixed(2)}',
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: AppTokens.primaryBlue,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: AppTokens.spacing8,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: statusColor.withOpacity(0.12),
                              borderRadius: BorderRadius.circular(AppTokens.radiusSm),
                            ),
                            child: Text(
                              p.status.label,
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: statusColor,
                              ),
                            ),
                          ),
                          if (split.status == SplitStatus.active &&
                              p.status == ParticipantStatus.invited) ...[
                            const SizedBox(height: 6),
                            SizedBox(
                              height: 32,
                              child: OutlinedButton.icon(
                                key: TestKeys.splitRemind(idKey),
                                style: OutlinedButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(horizontal: 8),
                                  side: BorderSide(
                                    color: p.canRemind ? AppTokens.primaryBlue : Colors.grey,
                                  ),
                                ),
                                icon: const Icon(Icons.notifications_active_outlined, size: 14),
                                label: Text(
                                  p.canRemind ? 'Remind' : 'Reminded',
                                  style: const TextStyle(fontSize: 11),
                                ),
                                onPressed: (_actionInProgress || !p.canRemind)
                                    ? null
                                    : () => _sendReminder(p),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
              );
            },
          ),

          const SizedBox(height: AppTokens.spacing24),

          // Owner Cancel Action
          if (split.status == SplitStatus.active)
            OutlinedButton.icon(
              key: TestKeys.splitCancel,
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.red,
                side: const BorderSide(color: Colors.red),
                padding: const EdgeInsets.symmetric(vertical: AppTokens.spacing14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(AppTokens.radiusMd),
                ),
              ),
              icon: const Icon(Icons.cancel_outlined, size: 20),
              label: const Text('Cancel Split'),
              onPressed: _actionInProgress ? null : _cancelSplit,
            ),
        ],
      ),
    );
  }
}
