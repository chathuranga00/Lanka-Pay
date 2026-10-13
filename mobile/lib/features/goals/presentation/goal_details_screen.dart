import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/goals_service.dart';
import '../domain/goal_model.dart';

/// Details screen for a single simulated savings goal.
/// Displays progress ring, contribute/withdraw actions with step-up auth, and append-only ledger entries.
class GoalDetailsScreen extends ConsumerStatefulWidget {
  final String goalId;

  const GoalDetailsScreen({
    super.key = const Key('goal_details_screen'),
    required this.goalId,
  });

  @override
  ConsumerState<GoalDetailsScreen> createState() => _GoalDetailsScreenState();
}

class _GoalDetailsScreenState extends ConsumerState<GoalDetailsScreen> {
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final goals = ref.watch(goalsProvider);
    final goal = goals.cast<GoalModel?>().firstWhere(
          (g) => g?.id == widget.goalId,
          orElse: () => null,
        );

    if (goal == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Goal Not Found')),
        body: const Center(child: Text('This goal no longer exists.')),
      );
    }

    final entries = ref.read(goalsProvider.notifier).getEntriesForGoal(widget.goalId);
    final progress = goal.progressRatio;
    final pct = (goal.progressPercentage).clamp(0.0, 100.0).toStringAsFixed(0);

    return Scaffold(
      key: const Key('goal_details_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: Text(
          goal.name,
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 16, top: 12, bottom: 12),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: AppTokens.primaryBlue.withOpacity(0.12),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Center(
              child: Text(
                'SIMULATED',
                style: TextStyle(
                  color: AppTokens.primaryBlue,
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // 1. Hero Progress Ring Card
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                width: 0.8,
              ),
              boxShadow: isDark ? [] : AppTokens.cardShadowLight,
            ),
            child: Column(
              children: [
                SizedBox(
                  width: 120,
                  height: 120,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      SizedBox(
                        width: 120,
                        height: 120,
                        child: CircularProgressIndicator(
                          value: progress,
                          strokeWidth: 10,
                          backgroundColor: isDark ? Colors.white12 : const Color(0xFFE2E8F0),
                          valueColor: AlwaysStoppedAnimation<Color>(
                            goal.isCompleted ? const Color(0xFF10B981) : AppTokens.primaryBlue,
                          ),
                        ),
                      ),
                      Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            '$pct%',
                            style: TextStyle(
                              fontSize: 26,
                              fontWeight: FontWeight.w900,
                              color: goal.isCompleted ? const Color(0xFF10B981) : AppTokens.primaryBlue,
                            ),
                          ),
                          Text(
                            goal.isCompleted ? 'Target Met' : 'Saved',
                            style: TextStyle(
                              fontSize: 11,
                              color: isDark ? Colors.white60 : AppTokens.textSecondaryLight,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  goal.formattedSaved,
                  style: const TextStyle(
                    fontSize: 26,
                    fontWeight: FontWeight.w900,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'of ${goal.formattedTarget} target',
                  style: TextStyle(
                    fontSize: 14,
                    color: isDark ? Colors.white60 : AppTokens.textSecondaryLight,
                  ),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.account_balance_rounded, size: 16, color: AppTokens.textSecondaryLight),
                      const SizedBox(width: 6),
                      Text(
                        'Funded via ${goal.sourceAccount}',
                        style: TextStyle(
                          fontSize: 12,
                          color: isDark ? Colors.white70 : AppTokens.textSecondaryLight,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 2. Action Buttons (Contribute & Withdraw)
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 50,
                  child: ElevatedButton.icon(
                    key: AppTestKeys.goalContribute,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTokens.primaryBlue,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      elevation: 0,
                    ),
                    icon: const Icon(Icons.add_rounded),
                    label: const Text(
                      'Contribute',
                      style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                    ),
                    onPressed: () => _showContributeDialog(context, goal),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: SizedBox(
                  height: 50,
                  child: OutlinedButton.icon(
                    key: AppTestKeys.goalWithdraw,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: isDark ? Colors.white : AppTokens.textPrimaryLight,
                      side: BorderSide(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                        width: 1.2,
                      ),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    ),
                    icon: const Icon(Icons.arrow_downward_rounded),
                    label: const Text(
                      'Withdraw',
                      style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                    ),
                    onPressed: () => _showWithdrawDialog(context, goal),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // 3. Simulated Ledger Entries Section
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'SIMULATED LEDGER ENTRIES',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                  color: AppTokens.textSecondaryLight,
                  letterSpacing: 0.8,
                ),
              ),
              Text(
                '${entries.length} entries',
                style: const TextStyle(fontSize: 12, color: AppTokens.textSecondaryLight),
              ),
            ],
          ),
          const SizedBox(height: 10),

          if (entries.isEmpty)
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: isDark ? AppTokens.cardDark : Colors.white,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Center(
                child: Text(
                  'No transactions recorded yet in this goal ledger.',
                  style: TextStyle(color: isDark ? Colors.white60 : AppTokens.textSecondaryLight, fontSize: 13),
                ),
              ),
            )
          else
            ...entries.map((entry) => _buildLedgerTile(entry, isDark)),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildLedgerTile(GoalLedgerEntryModel entry, bool isDark) {
    final isWithdrawal = entry.type == 'WITHDRAWAL';

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
          width: 0.8,
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: isWithdrawal
                  ? const Color(0xFFFEE2E2)
                  : const Color(0xFFDCFCE7),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(
              isWithdrawal ? Icons.arrow_outward_rounded : Icons.arrow_downward_rounded,
              color: isWithdrawal ? const Color(0xFFDC2626) : const Color(0xFF16A34A),
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry.note ?? (isWithdrawal ? 'Withdrawal' : 'Contribution'),
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  'Balance after: ${entry.formattedBalanceAfter}',
                  style: TextStyle(
                    fontSize: 12,
                    color: isDark ? Colors.white60 : AppTokens.textSecondaryLight,
                  ),
                ),
              ],
            ),
          ),
          Text(
            entry.formattedAmount,
            style: TextStyle(
              fontWeight: FontWeight.w800,
              fontSize: 15,
              color: isWithdrawal ? const Color(0xFFDC2626) : const Color(0xFF16A34A),
            ),
          ),
        ],
      ),
    );
  }

  void _showContributeDialog(BuildContext context, GoalModel goal) {
    final amountCtrl = TextEditingController();
    final pinCtrl = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 24,
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Theme.of(context).brightness == Brightness.dark ? AppTokens.cardDark : Colors.white,
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Contribute to Goal',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 6),
              const Text(
                'Simulated transfer. Enter amount and your step-up PIN.',
                style: TextStyle(fontSize: 13, color: AppTokens.textSecondaryLight),
              ),
              const SizedBox(height: 16),
              TextField(
                key: AppTestKeys.goalAmountInput,
                controller: amountCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Amount (LKR)',
                  hintText: 'e.g. 5000',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 14),
              TextField(
                key: AppTestKeys.goalPinInput,
                controller: pinCtrl,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 6,
                decoration: const InputDecoration(
                  labelText: 'Step-Up PIN',
                  hintText: '6-digit PIN (e.g. 123456)',
                  border: OutlineInputBorder(),
                  counterText: '',
                ),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  key: AppTestKeys.goalContributeConfirm,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTokens.primaryBlue,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  onPressed: () {
                    final lkr = double.tryParse(amountCtrl.text.trim()) ?? 0.0;
                    final pin = pinCtrl.text.trim();
                    if (lkr <= 0) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Please enter a valid amount')),
                      );
                      return;
                    }
                    try {
                      ref.read(goalsProvider.notifier).contribute(
                            goalId: goal.id,
                            amountMinor: (lkr * 100).toInt(),
                            pin: pin,
                            note: 'Manual contribution',
                          );
                      Navigator.of(context).pop();
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Contributed LKR ${lkr.toStringAsFixed(2)} successfully')),
                      );
                    } catch (e) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Error: ${e.toString()}')),
                      );
                    }
                  },
                  child: const Text('Confirm Contribution', style: TextStyle(fontWeight: FontWeight.w700)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showWithdrawDialog(BuildContext context, GoalModel goal) {
    final amountCtrl = TextEditingController();
    final pinCtrl = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 24,
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Theme.of(context).brightness == Brightness.dark ? AppTokens.cardDark : Colors.white,
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Withdraw from Goal',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 6),
              Text(
                'Available simulated balance: ${goal.formattedSaved}',
                style: const TextStyle(fontSize: 13, color: AppTokens.textSecondaryLight),
              ),
              const SizedBox(height: 16),
              TextField(
                key: AppTestKeys.goalAmountInput,
                controller: amountCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Amount to Withdraw (LKR)',
                  hintText: 'e.g. 2000',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 14),
              TextField(
                key: AppTestKeys.goalPinInput,
                controller: pinCtrl,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 6,
                decoration: const InputDecoration(
                  labelText: 'Step-Up PIN',
                  hintText: '6-digit PIN (e.g. 123456)',
                  border: OutlineInputBorder(),
                  counterText: '',
                ),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  key: AppTestKeys.goalWithdrawConfirm,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFFDC2626),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  onPressed: () {
                    final lkr = double.tryParse(amountCtrl.text.trim()) ?? 0.0;
                    final pin = pinCtrl.text.trim();
                    if (lkr <= 0) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Please enter a valid amount')),
                      );
                      return;
                    }
                    try {
                      ref.read(goalsProvider.notifier).withdraw(
                            goalId: goal.id,
                            amountMinor: (lkr * 100).toInt(),
                            pin: pin,
                            note: 'Manual withdrawal',
                          );
                      Navigator.of(context).pop();
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Withdrew LKR ${lkr.toStringAsFixed(2)} successfully')),
                      );
                    } catch (e) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Error: ${e.toString()}')),
                      );
                    }
                  },
                  child: const Text('Confirm Withdrawal', style: TextStyle(fontWeight: FontWeight.w700)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
