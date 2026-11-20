import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/goals_service.dart';
import '../domain/goal_model.dart';
import 'goal_details_screen.dart';

/// Screen listing simulated savings goals with circular progress rings.
class GoalsScreen extends ConsumerStatefulWidget {
  const GoalsScreen({super.key = const Key('goals_screen')});

  @override
  ConsumerState<GoalsScreen> createState() => _GoalsScreenState();
}

class _GoalsScreenState extends ConsumerState<GoalsScreen> {
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final goals = ref.watch(goalsProvider);

    return Scaffold(
      key: const Key('goals_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Savings Goals',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        actions: [
          IconButton(
            key: AppTestKeys.goalCreateBtn,
            icon: const Icon(Icons.add_circle_outline_rounded, color: AppTokens.primaryBlue),
            tooltip: 'Create Goal',
            onPressed: () => _showCreateGoalDialog(context),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // Simulated Ledger Notice Banner
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1E293B) : const Color(0xFFEFF6FF),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isDark ? const Color(0xFF334155) : const Color(0xFFBFDBFE),
                width: 1,
              ),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.info_outline_rounded, color: AppTokens.primaryBlue, size: 22),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: AppTokens.primaryBlue,
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: const Text(
                              'SIMULATED',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 10,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          const Text(
                            'Internal Savings Ledger',
                            style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Set targets and track simulated savings without locking real bank funds. Contributions and withdrawals are tracked purely in this internal ledger.',
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

          if (goals.isEmpty)
            _buildEmptyState(context, isDark)
          else
            ...goals.map((goal) => _buildGoalCard(context, goal, isDark)),
          const SizedBox(height: 24),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('goals_fab_create'),
        backgroundColor: AppTokens.primaryBlue,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add_rounded),
        label: const Text('New Goal', style: TextStyle(fontWeight: FontWeight.w700)),
        onPressed: () => _showCreateGoalDialog(context),
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 48, horizontal: 24),
      alignment: Alignment.center,
      child: Column(
        children: [
          Icon(Icons.savings_outlined, size: 64, color: isDark ? Colors.white38 : AppTokens.textSecondaryLight),
          const SizedBox(height: 12),
          const Text(
            'No Goals Created Yet',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          Text(
            'Create your first simulated savings goal to start tracking progress toward your financial targets.',
            textAlign: TextAlign.center,
            style: TextStyle(color: isDark ? Colors.white60 : AppTokens.textSecondaryLight, fontSize: 13),
          ),
          const SizedBox(height: 20),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTokens.primaryBlue,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            icon: const Icon(Icons.add_rounded),
            label: const Text('Create Goal', style: TextStyle(fontWeight: FontWeight.w700)),
            onPressed: () => _showCreateGoalDialog(context),
          ),
        ],
      ),
    );
  }

  Widget _buildGoalCard(BuildContext context, GoalModel goal, bool isDark) {
    final progress = goal.progressRatio;
    final pct = (goal.progressPercentage).clamp(0.0, 100.0).toStringAsFixed(0);

    return Container(
      key: AppTestKeys.goal(goal.id),
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
          width: 0.8,
        ),
        boxShadow: isDark ? [] : AppTokens.cardShadowLight,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => GoalDetailsScreen(goalId: goal.id),
              ),
            );
          },
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                // Progress Ring with percentage
                SizedBox(
                  width: 58,
                  height: 58,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      CircularProgressIndicator(
                        value: progress,
                        strokeWidth: 5,
                        backgroundColor: isDark ? Colors.white12 : const Color(0xFFE2E8F0),
                        valueColor: AlwaysStoppedAnimation<Color>(
                          goal.isCompleted ? const Color(0xFF10B981) : AppTokens.primaryBlue,
                        ),
                      ),
                      Text(
                        '$pct%',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w800,
                          color: goal.isCompleted ? const Color(0xFF10B981) : AppTokens.primaryBlue,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                // Goal Info
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              goal.name,
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                                fontSize: 16,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          if (goal.isCompleted)
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: const Color(0xFFDCFCE7),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: const Text(
                                'REACHED',
                                style: TextStyle(
                                  color: Color(0xFF16A34A),
                                  fontSize: 10,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          Text(
                            goal.formattedSaved,
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              fontSize: 14,
                              color: AppTokens.primaryBlue,
                            ),
                          ),
                          Text(
                            ' / ${goal.formattedTarget}',
                            style: TextStyle(
                              fontSize: 13,
                              color: isDark ? Colors.white60 : AppTokens.textSecondaryLight,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'From ${goal.sourceAccount}',
                        style: TextStyle(
                          fontSize: 11,
                          color: isDark ? Colors.white54 : AppTokens.textSecondaryLight,
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.arrow_forward_ios_rounded, size: 14, color: AppTokens.textSecondaryLight),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showCreateGoalDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    final targetCtrl = TextEditingController();
    final depositCtrl = TextEditingController();
    final pinCtrl = TextEditingController();
    String sourceAccount = 'Commercial Bank •••• 4012';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) {
          final isDark = Theme.of(context).brightness == Brightness.dark;

          return Container(
            padding: EdgeInsets.only(
              left: 20,
              right: 20,
              top: 24,
              bottom: MediaQuery.of(context).viewInsets.bottom + 24,
            ),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            ),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Create Savings Goal',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: AppTokens.primaryBlue.withOpacity(0.12),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Text(
                          'SIMULATED',
                          style: TextStyle(
                            color: AppTokens.primaryBlue,
                            fontSize: 11,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    key: AppTestKeys.goalNameInput,
                    controller: nameCtrl,
                    decoration: const InputDecoration(
                      labelText: 'Goal Name',
                      hintText: 'e.g. Vacation in Galle, Laptop',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    key: AppTestKeys.goalTargetInput,
                    controller: targetCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Target Amount (LKR)',
                      hintText: 'e.g. 50000',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    key: AppTestKeys.goalAmountInput,
                    controller: depositCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Initial Deposit (Optional)',
                      hintText: 'e.g. 10000',
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
                      labelText: 'Step-Up PIN (if deposit > 0)',
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
                      key: AppTestKeys.goalCreateSubmit,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTokens.primaryBlue,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      onPressed: () {
                        final name = nameCtrl.text.trim();
                        final targetLkr = double.tryParse(targetCtrl.text.trim()) ?? 0.0;
                        final depositLkr = double.tryParse(depositCtrl.text.trim()) ?? 0.0;
                        final pin = pinCtrl.text.trim();

                        if (name.isEmpty || targetLkr <= 0) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Please enter valid name and target amount')),
                          );
                          return;
                        }

                        try {
                          ref.read(goalsProvider.notifier).createGoal(
                                name: name,
                                targetMinor: (targetLkr * 100).toInt(),
                                initialDepositMinor: (depositLkr * 100).toInt(),
                                sourceAccount: sourceAccount,
                                pin: depositLkr > 0 ? pin : null,
                                deadline: DateTime.now().plusMonths(6),
                              );
                          Navigator.of(context).pop();
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Created simulated goal "$name"')),
                          );
                        } catch (e) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Error: ${e.toString()}')),
                          );
                        }
                      },
                      child: const Text('Create Goal', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
