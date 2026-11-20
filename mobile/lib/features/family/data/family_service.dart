import 'dart:math';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/family_member_model.dart';

/// Riverpod StateNotifier/Notifier managing simulated family members and guardian controls.
class FamilyNotifier extends Notifier<List<FamilyMemberModel>> {
  final Map<String, List<FamilyTransactionModel>> _activityStore = {};

  @override
  List<FamilyMemberModel> build() {
    // Seed sample family members for simulation & testing
    final child1 = FamilyMemberModel(
      id: 'family_1',
      guardianUserId: 1,
      firstName: 'Senula',
      ageBand: 'AGE_10_TO_13',
      avatarInitials: 'SE',
      balanceMinor: 250000, // LKR 2,500.00
      dailyLimitMinor: 100000, // LKR 1,000.00
      monthlyLimitMinor: 1000000, // LKR 10,000.00
      allowedCategories: const ['FOOD', 'EDUCATION', 'BOOKSTORE'],
      isFrozen: false,
      allowanceSchedule: const AllowanceScheduleModel(
        enabled: true,
        amountMinor: 50000, // LKR 500.00
        frequency: 'WEEKLY',
        nextRunDate: '2026-10-20',
        sourceAccount: 'Commercial Bank •••• 4012',
      ),
      todaySpentMinor: 25000, // LKR 250.00
      monthSpentMinor: 180000, // LKR 1,800.00
      createdAt: DateTime.now().subtract(const Duration(days: 60)),
      updatedAt: DateTime.now().subtract(const Duration(days: 2)),
    );

    final child2 = FamilyMemberModel(
      id: 'family_2',
      guardianUserId: 1,
      firstName: 'Kaveesha',
      ageBand: 'AGE_14_TO_17',
      avatarInitials: 'KV',
      balanceMinor: 500000, // LKR 5,000.00
      dailyLimitMinor: 200000, // LKR 2,000.00
      monthlyLimitMinor: 2000000, // LKR 20,000.00
      allowedCategories: const ['FOOD', 'EDUCATION', 'TRANSPORT', 'BOOKSTORE'],
      isFrozen: false,
      allowanceSchedule: const AllowanceScheduleModel(
        enabled: false,
        amountMinor: 0,
        frequency: 'MONTHLY',
      ),
      todaySpentMinor: 0,
      monthSpentMinor: 450000,
      createdAt: DateTime.now().subtract(const Duration(days: 30)),
      updatedAt: DateTime.now().subtract(const Duration(days: 5)),
    );

    _activityStore['family_1'] = [
      FamilyTransactionModel(
        id: 'tx_f1_1',
        memberId: 'family_1',
        type: 'SPEND',
        amountMinor: 25000,
        balanceAfterMinor: 250000,
        category: 'FOOD',
        merchantOrSource: 'School Canteen',
        note: 'Lunch snack',
        status: 'APPROVED',
        timestamp: DateTime.now().subtract(const Duration(hours: 3)),
      ),
      FamilyTransactionModel(
        id: 'tx_f1_2',
        memberId: 'family_1',
        type: 'FUNDING',
        amountMinor: 100000,
        balanceAfterMinor: 275000,
        category: 'ALLOWANCE',
        merchantOrSource: 'Guardian Account',
        note: 'Weekly allowance',
        status: 'APPROVED',
        timestamp: DateTime.now().subtract(const Duration(days: 2)),
      ),
    ];

    _activityStore['family_2'] = [
      FamilyTransactionModel(
        id: 'tx_f2_1',
        memberId: 'family_2',
        type: 'FUNDING',
        amountMinor: 500000,
        balanceAfterMinor: 500000,
        category: 'ALLOWANCE',
        merchantOrSource: 'Guardian Account',
        note: 'Monthly allowance',
        status: 'APPROVED',
        timestamp: DateTime.now().subtract(const Duration(days: 5)),
      ),
    ];

    return [child1, child2];
  }

  FamilyMemberModel? getMemberById(String id) {
    try {
      return state.firstWhere((m) => m.id == id);
    } catch (_) {
      return null;
    }
  }

  List<FamilyTransactionModel> getActivity(String memberId) {
    return _activityStore[memberId] ?? [];
  }

  ChildViewModel? getChildView(String memberId) {
    final member = getMemberById(memberId);
    if (member == null) return null;
    final remaining = max(0, member.dailyLimitMinor - member.todaySpentMinor);
    final activity = getActivity(memberId);
    return ChildViewModel(
      id: member.id,
      firstName: member.firstName,
      avatarInitials: member.avatarInitials,
      balanceMinor: member.balanceMinor,
      dailyLimitMinor: member.dailyLimitMinor,
      remainingDailyMinor: remaining,
      allowedCategories: member.allowedCategories,
      isFrozen: member.isFrozen,
      recentTransactions: activity.take(5).toList(),
    );
  }

  FamilyMemberModel createMember({
    required String firstName,
    required String ageBand,
    String? avatarInitials,
    int? dailyLimitMinor,
    int? monthlyLimitMinor,
    List<String>? allowedCategories,
    AllowanceScheduleModel? allowanceSchedule,
  }) {
    final cleanName = firstName.trim().split(' ').first;
    final initials = (avatarInitials != null && avatarInitials.isNotEmpty)
        ? avatarInitials.trim().toUpperCase()
        : cleanName.substring(0, min(2, cleanName.length)).toUpperCase();

    final id = 'family_${DateTime.now().millisecondsSinceEpoch}';
    final newMember = FamilyMemberModel(
      id: id,
      guardianUserId: 1,
      firstName: cleanName,
      ageBand: ageBand,
      avatarInitials: initials,
      balanceMinor: 0,
      dailyLimitMinor: dailyLimitMinor ?? 100000,
      monthlyLimitMinor: monthlyLimitMinor ?? 1000000,
      allowedCategories: allowedCategories ?? const ['FOOD', 'EDUCATION', 'TRANSPORT'],
      isFrozen: false,
      allowanceSchedule: allowanceSchedule,
      todaySpentMinor: 0,
      monthSpentMinor: 0,
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );

    _activityStore[id] = [];
    state = [...state, newMember];
    return newMember;
  }

  void updateControls(
    String id, {
    int? dailyLimitMinor,
    int? monthlyLimitMinor,
    List<String>? allowedCategories,
    bool? isFrozen,
    AllowanceScheduleModel? allowanceSchedule,
  }) {
    state = state.map((m) {
      if (m.id == id) {
        return m.copyWith(
          dailyLimitMinor: dailyLimitMinor ?? m.dailyLimitMinor,
          monthlyLimitMinor: monthlyLimitMinor ?? m.monthlyLimitMinor,
          allowedCategories: allowedCategories ?? m.allowedCategories,
          isFrozen: isFrozen ?? m.isFrozen,
          allowanceSchedule: allowanceSchedule ?? m.allowanceSchedule,
          updatedAt: DateTime.now(),
        );
      }
      return m;
    }).toList();
  }

  void toggleFreeze(String id, bool frozen) {
    updateControls(id, isFrozen: frozen);
  }

  void fundMember(String id, int amountMinor, {String? sourceAccount, String? note}) {
    if (amountMinor <= 0) return;
    state = state.map((m) {
      if (m.id == id) {
        final newBalance = m.balanceMinor + amountMinor;
        final tx = FamilyTransactionModel(
          id: 'tx_${DateTime.now().millisecondsSinceEpoch}',
          memberId: id,
          type: 'FUNDING',
          amountMinor: amountMinor,
          balanceAfterMinor: newBalance,
          category: 'ALLOWANCE',
          merchantOrSource: sourceAccount ?? 'Guardian Account',
          note: note ?? 'Simulated funding',
          status: 'APPROVED',
          timestamp: DateTime.now(),
        );
        _activityStore.putIfAbsent(id, () => []).insert(0, tx);

        return m.copyWith(
          balanceMinor: newBalance,
          updatedAt: DateTime.now(),
        );
      }
      return m;
    }).toList();
  }

  bool spend(String id, int amountMinor, String category, String merchantName, {String? note}) {
    final member = getMemberById(id);
    if (member == null) return false;

    final normCat = category.toUpperCase().trim();

    // Check freeze
    if (member.isFrozen) {
      _recordDeclined(id, amountMinor, normCat, merchantName, 'Account is frozen by guardian', note);
      return false;
    }

    // Check category
    if (!member.allowedCategories.contains(normCat)) {
      _recordDeclined(id, amountMinor, normCat, merchantName, 'Category not allowed: $normCat', note);
      return false;
    }

    // Check daily limit
    if (member.todaySpentMinor + amountMinor > member.dailyLimitMinor) {
      _recordDeclined(id, amountMinor, normCat, merchantName, 'Daily spending limit exceeded', note);
      return false;
    }

    // Check monthly limit
    if (member.monthSpentMinor + amountMinor > member.monthlyLimitMinor) {
      _recordDeclined(id, amountMinor, normCat, merchantName, 'Monthly spending limit exceeded', note);
      return false;
    }

    // Check balance
    if (member.balanceMinor < amountMinor) {
      _recordDeclined(id, amountMinor, normCat, merchantName, 'Insufficient simulated balance', note);
      return false;
    }

    // Approve
    final newBalance = member.balanceMinor - amountMinor;
    final tx = FamilyTransactionModel(
      id: 'tx_${DateTime.now().millisecondsSinceEpoch}',
      memberId: id,
      type: 'SPEND',
      amountMinor: amountMinor,
      balanceAfterMinor: newBalance,
      category: normCat,
      merchantOrSource: merchantName,
      note: note,
      status: 'APPROVED',
      timestamp: DateTime.now(),
    );
    _activityStore.putIfAbsent(id, () => []).insert(0, tx);

    state = state.map((m) {
      if (m.id == id) {
        return m.copyWith(
          balanceMinor: newBalance,
          todaySpentMinor: m.todaySpentMinor + amountMinor,
          monthSpentMinor: m.monthSpentMinor + amountMinor,
          updatedAt: DateTime.now(),
        );
      }
      return m;
    }).toList();

    return true;
  }

  void _recordDeclined(String memberId, int amountMinor, String category, String merchantName, String reason, String? note) {
    final member = getMemberById(memberId);
    final tx = FamilyTransactionModel(
      id: 'tx_${DateTime.now().millisecondsSinceEpoch}',
      memberId: memberId,
      type: 'SPEND',
      amountMinor: amountMinor,
      balanceAfterMinor: member?.balanceMinor ?? 0,
      category: category,
      merchantOrSource: merchantName,
      note: note,
      status: 'DECLINED',
      declineReason: reason,
      timestamp: DateTime.now(),
    );
    _activityStore.putIfAbsent(memberId, () => []).insert(0, tx);
  }

  void deleteMember(String id) {
    _activityStore.remove(id);
    state = state.where((m) => m.id != id).toList();
  }
}

final familyNotifierProvider =
    NotifierProvider<FamilyNotifier, List<FamilyMemberModel>>(
  FamilyNotifier.new,
);

final familyMemberActivityProvider =
    Provider.family<List<FamilyTransactionModel>, String>((ref, memberId) {
  final notifier = ref.watch(familyNotifierProvider.notifier);
  // Re-read when state changes
  ref.watch(familyNotifierProvider);
  return notifier.getActivity(memberId);
});

final familyChildViewProvider =
    Provider.family<ChildViewModel?, String>((ref, memberId) {
  final notifier = ref.watch(familyNotifierProvider.notifier);
  ref.watch(familyNotifierProvider);
  return notifier.getChildView(memberId);
});
