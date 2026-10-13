import 'dart:math';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/goal_model.dart';

/// Service and notifier managing simulated savings goals in the mobile app.
class GoalsNotifier extends Notifier<List<GoalModel>> {
  final Map<String, List<GoalLedgerEntryModel>> _ledgerStore = {};

  @override
  List<GoalModel> build() {
    _ledgerStore['goal_1'] = [
      GoalLedgerEntryModel(
        id: 'e_1_1',
        goalId: 'goal_1',
        type: 'CONTRIBUTION',
        amountMinor: 5000000,
        balanceAfterMinor: 12000000,
        note: 'Monthly salary allocation',
        timestamp: DateTime.now().subtract(const Duration(days: 3)),
      ),
      GoalLedgerEntryModel(
        id: 'e_1_2',
        goalId: 'goal_1',
        type: 'CONTRIBUTION',
        amountMinor: 7000000,
        balanceAfterMinor: 7000000,
        note: 'Initial deposit',
        timestamp: DateTime.now().subtract(const Duration(days: 45)),
      ),
    ];

    return [
      GoalModel(
        id: 'goal_1',
        name: 'Emergency Reserve',
        targetMinor: 20000000, // LKR 200,000.00
        savedMinor: 12000000, // LKR 120,000.00 (60%)
        deadline: DateTime.now().plusMonths(6),
        sourceAccount: 'Commercial Bank •••• 4012',
        status: 'ACTIVE',
        ledgerType: 'SIMULATED',
        progressPercentage: 60.0,
        createdAt: DateTime.now().subtract(const Duration(days: 45)),
      ),
      GoalModel(
        id: 'goal_2',
        name: 'MacBook Upgrade',
        targetMinor: 40000000, // LKR 400,000.00
        savedMinor: 32000000, // LKR 320,000.00 (80%)
        deadline: DateTime.now().plusMonths(3),
        sourceAccount: 'Bank of Ceylon •••• 9921',
        status: 'ACTIVE',
        ledgerType: 'SIMULATED',
        progressPercentage: 80.0,
        createdAt: DateTime.now().subtract(const Duration(days: 30)),
      ),
      GoalModel(
        id: 'goal_3',
        name: 'Kandy Holiday Trip',
        targetMinor: 10000000, // LKR 100,000.00
        savedMinor: 3500000, // LKR 35,000.00 (35%)
        deadline: DateTime.now().plusMonths(2),
        sourceAccount: 'Commercial Bank •••• 4012',
        status: 'ACTIVE',
        ledgerType: 'SIMULATED',
        progressPercentage: 35.0,
        createdAt: DateTime.now().subtract(const Duration(days: 14)),
      ),
    ];
  }

  List<GoalLedgerEntryModel> getEntriesForGoal(String goalId) {
    return _ledgerStore[goalId] ?? [];
  }

  GoalModel? getGoalById(String goalId) {
    for (final g in state) {
      if (g.id == goalId) return g;
    }
    return null;
  }

  GoalModel createGoal({
    required String name,
    required int targetMinor,
    DateTime? deadline,
    required String sourceAccount,
    int initialDepositMinor = 0,
    String? pin,
  }) {
    if (name.trim().isEmpty) {
      throw ArgumentError('Goal name cannot be empty');
    }
    if (targetMinor <= 0) {
      throw ArgumentError('Target amount must be strictly positive');
    }
    if (initialDepositMinor > 0) {
      if (pin == null || pin.isEmpty) {
        throw StateError('STEP_UP_AUTH_REQUIRED: Step-up PIN is required for deposits');
      }
      if (pin != '123456' && pin.length != 6) {
        throw StateError('INVALID_PIN: Invalid PIN provided for step-up authentication');
      }
    }

    final id = 'goal_${DateTime.now().millisecondsSinceEpoch}_${Random().nextInt(999)}';
    final progress = targetMinor > 0
        ? min(100.0, (initialDepositMinor / targetMinor) * 100.0)
        : 0.0;
    final status = (initialDepositMinor >= targetMinor) ? 'COMPLETED' : 'ACTIVE';

    final goal = GoalModel(
      id: id,
      name: name.trim(),
      targetMinor: targetMinor,
      savedMinor: initialDepositMinor,
      deadline: deadline,
      sourceAccount: sourceAccount,
      status: status,
      ledgerType: 'SIMULATED',
      progressPercentage: progress,
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );

    state = [goal, ...state];

    if (initialDepositMinor > 0) {
      final entry = GoalLedgerEntryModel(
        id: 'entry_${DateTime.now().millisecondsSinceEpoch}',
        goalId: id,
        type: 'CONTRIBUTION',
        amountMinor: initialDepositMinor,
        balanceAfterMinor: initialDepositMinor,
        note: 'Initial simulated deposit',
        timestamp: DateTime.now(),
      );
      _ledgerStore[id] = [entry];
    } else {
      _ledgerStore[id] = [];
    }

    return goal;
  }

  void contribute({
    required String goalId,
    required int amountMinor,
    String? note,
    required String pin,
  }) {
    if (amountMinor <= 0) {
      throw ArgumentError('Contribution amount must be positive');
    }
    if (pin.isEmpty) {
      throw StateError('STEP_UP_AUTH_REQUIRED: Step-up PIN is required for contributions');
    }
    if (pin != '123456' && pin.length != 6) {
      throw StateError('INVALID_PIN: Invalid PIN provided for step-up authentication');
    }

    final index = state.indexWhere((g) => g.id == goalId);
    if (index == -1) {
      throw ArgumentError('Goal not found: $goalId');
    }

    final current = state[index];
    final newSaved = current.savedMinor + amountMinor;
    final progress = current.targetMinor > 0
        ? min(100.0, (newSaved / current.targetMinor) * 100.0)
        : 100.0;
    final newStatus = (newSaved >= current.targetMinor) ? 'COMPLETED' : current.status;

    final updated = current.copyWith(
      savedMinor: newSaved,
      progressPercentage: progress,
      status: newStatus,
      updatedAt: DateTime.now(),
    );

    final entry = GoalLedgerEntryModel(
      id: 'entry_${DateTime.now().millisecondsSinceEpoch}',
      goalId: goalId,
      type: 'CONTRIBUTION',
      amountMinor: amountMinor,
      balanceAfterMinor: newSaved,
      note: note ?? 'Simulated contribution',
      timestamp: DateTime.now(),
    );

    final entries = _ledgerStore[goalId] ?? [];
    _ledgerStore[goalId] = [entry, ...entries];

    final updatedList = [...state];
    updatedList[index] = updated;
    state = updatedList;
  }

  void withdraw({
    required String goalId,
    required int amountMinor,
    String? note,
    required String pin,
  }) {
    if (amountMinor <= 0) {
      throw ArgumentError('Withdrawal amount must be positive');
    }
    if (pin.isEmpty) {
      throw StateError('STEP_UP_AUTH_REQUIRED: Step-up PIN is required for withdrawals');
    }
    if (pin != '123456' && pin.length != 6) {
      throw StateError('INVALID_PIN: Invalid PIN provided for step-up authentication');
    }

    final index = state.indexWhere((g) => g.id == goalId);
    if (index == -1) {
      throw ArgumentError('Goal not found: $goalId');
    }

    final current = state[index];
    if (amountMinor > current.savedMinor) {
      throw ArgumentError(
        'Cannot withdraw LKR ${(amountMinor / 100).toStringAsFixed(2)}: '
        'available balance is LKR ${(current.savedMinor / 100).toStringAsFixed(2)}',
      );
    }

    final newSaved = current.savedMinor - amountMinor;
    final progress = current.targetMinor > 0
        ? min(100.0, (newSaved / current.targetMinor) * 100.0)
        : 0.0;
    final newStatus = (newSaved < current.targetMinor && current.status == 'COMPLETED')
        ? 'ACTIVE'
        : current.status;

    final updated = current.copyWith(
      savedMinor: newSaved,
      progressPercentage: progress,
      status: newStatus,
      updatedAt: DateTime.now(),
    );

    final entry = GoalLedgerEntryModel(
      id: 'entry_${DateTime.now().millisecondsSinceEpoch}',
      goalId: goalId,
      type: 'WITHDRAWAL',
      amountMinor: amountMinor,
      balanceAfterMinor: newSaved,
      note: note ?? 'Simulated withdrawal',
      timestamp: DateTime.now(),
    );

    final entries = _ledgerStore[goalId] ?? [];
    _ledgerStore[goalId] = [entry, ...entries];

    final updatedList = [...state];
    updatedList[index] = updated;
    state = updatedList;
  }

  void deleteGoal(String goalId) {
    state = state.where((g) => g.id != goalId).toList();
    _ledgerStore.remove(goalId);
  }
}

/// Global provider for savings goals.
final goalsProvider =
    NotifierProvider<GoalsNotifier, List<GoalModel>>(GoalsNotifier.new);

extension DateTimeAddMonths on DateTime {
  DateTime plusMonths(int months) {
    return DateTime(year, month + months, day);
  }
}
