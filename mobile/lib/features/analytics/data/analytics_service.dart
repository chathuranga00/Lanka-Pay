import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../domain/analytics_models.dart';

final analyticsServiceProvider = Provider<AnalyticsService>((ref) {
  final dio = ref.watch(dioProvider);
  return AnalyticsService(dio);
});

class SelectedMonthNotifier extends Notifier<String> {
  @override
  String build() {
    final now = DateTime.now();
    final monthStr = now.month < 10 ? '0${now.month}' : '${now.month}';
    return '${now.year}-$monthStr';
  }

  void setMonth(String month) => state = month;
}

final selectedMonthProvider = NotifierProvider<SelectedMonthNotifier, String>(SelectedMonthNotifier.new);

final analyticsSummaryProvider = FutureProvider.family<AnalyticsSummaryModel, String>((ref, month) async {
  final service = ref.watch(analyticsServiceProvider);
  return service.getSummary(month);
});

final activityListProvider = FutureProvider.family<List<ActivityItemModel>, ActivityFilter>((ref, filter) async {
  final service = ref.watch(analyticsServiceProvider);
  return service.listActivity(month: filter.month, direction: filter.direction, category: filter.category);
});

final budgetProvider = FutureProvider.family<BudgetModel?, String>((ref, month) async {
  final service = ref.watch(analyticsServiceProvider);
  try {
    return await service.getBudget(month);
  } catch (_) {
    return null;
  }
});

class AnalyticsService {
  final Dio _dio;

  AnalyticsService(this._dio);

  Future<AnalyticsSummaryModel> getSummary(String month) async {
    try {
      final response = await _dio.get(
        '/api/v1/analytics/summary',
        queryParameters: {'month': month},
      );
      return AnalyticsSummaryModel.fromJson(Map<String, dynamic>.from(response.data as Map));
    } catch (e) {
      // Provide demo/fallback summary if backend is unreachable during testing
      return _getFallbackSummary(month);
    }
  }

  Future<List<ActivityItemModel>> listActivity({
    String? month,
    String? direction,
    String? category,
  }) async {
    try {
      final query = <String, dynamic>{};
      if (month != null && month.isNotEmpty) query['month'] = month;
      if (direction != null && direction.isNotEmpty) query['direction'] = direction;
      if (category != null && category.isNotEmpty) query['category'] = category;

      final response = await _dio.get(
        '/api/v1/analytics/activity',
        queryParameters: query,
      );
      final data = response.data as List;
      return data
          .map((e) => ActivityItemModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (_) {
      return _getFallbackActivities(month);
    }
  }

  Future<BudgetModel> getBudget(String month) async {
    final response = await _dio.get(
      '/api/v1/budgets',
      queryParameters: {'month': month},
    );
    return BudgetModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<BudgetModel> setBudget(String month, int monthlyTotalMinor, [Map<String, int>? categoryBudgetsMinor]) async {
    final response = await _dio.post(
      '/api/v1/budgets',
      data: {
        'month': month,
        'monthlyTotalMinor': monthlyTotalMinor,
        if (categoryBudgetsMinor != null) 'categoryBudgetsMinor': categoryBudgetsMinor,
      },
    );
    return BudgetModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<BudgetModel> patchBudget(String month, int? monthlyTotalMinor, [Map<String, int>? categoryBudgetsMinor]) async {
    final response = await _dio.patch(
      '/api/v1/budgets',
      data: {
        'month': month,
        if (monthlyTotalMinor != null) 'monthlyTotalMinor': monthlyTotalMinor,
        if (categoryBudgetsMinor != null) 'categoryBudgetsMinor': categoryBudgetsMinor,
      },
    );
    return BudgetModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<void> updateCategory(int transactionId, String transactionType, String category, [String? colorHex]) async {
    await _dio.put(
      '/api/v1/analytics/transactions/$transactionId/category',
      data: {
        'transactionType': transactionType,
        'category': category,
        if (colorHex != null) 'colorHex': colorHex,
      },
    );
  }

  Future<Map<String, dynamic>> exportStatement(String month) async {
    final response = await _dio.post(
      '/api/v1/statements',
      data: {'month': month},
    );
    return Map<String, dynamic>.from(response.data as Map);
  }

  AnalyticsSummaryModel _getFallbackSummary(String month) {
    return AnalyticsSummaryModel(
      month: month,
      spentMinor: 2450000,
      spentAmount: 24500.00,
      receivedMinor: 6500000,
      receivedAmount: 65000.00,
      pendingSpentMinor: 150000,
      pendingSpentAmount: 1500.00,
      pendingCount: 1,
      byCategoryTotals: [
        const CategoryTotalModel(
          category: 'FOOD',
          displayName: 'Food & Dining',
          colorHex: '#F97316',
          totalMinor: 950000,
          totalAmount: 9500.00,
          count: 5,
          percentage: 38.78,
        ),
        const CategoryTotalModel(
          category: 'BILLS',
          displayName: 'Bills & Utilities',
          colorHex: '#06B6D4',
          totalMinor: 820000,
          totalAmount: 8200.00,
          count: 2,
          percentage: 33.47,
        ),
        const CategoryTotalModel(
          category: 'TRANSPORT',
          displayName: 'Transport',
          colorHex: '#10B981',
          totalMinor: 430000,
          totalAmount: 4300.00,
          count: 4,
          percentage: 17.55,
        ),
        const CategoryTotalModel(
          category: 'SHOPPING',
          displayName: 'Shopping',
          colorHex: '#EC4899',
          totalMinor: 250000,
          totalAmount: 2500.00,
          count: 1,
          percentage: 10.20,
        ),
      ],
      topPayees: const [
        TopPayeeModel(name: 'Ceylon Electricity Board', totalMinor: 520000, totalAmount: 5200.00, count: 1),
        TopPayeeModel(name: 'Uber Eats', totalMinor: 480000, totalAmount: 4800.00, count: 3),
        TopPayeeModel(name: 'Dialog Broadband', totalMinor: 300000, totalAmount: 3000.00, count: 1),
      ],
      monthOverMonth: const MonthOverMonthModel(
        currentMonthSpentMinor: 2450000,
        currentMonthSpent: 24500.00,
        previousMonthSpentMinor: 2100000,
        previousMonthSpent: 21000.00,
        differenceMinor: 350000,
        differenceAmount: 3500.00,
        percentageChange: 16.67,
        increased: true,
      ),
      budgetTotalMinor: 3000000,
      budgetTotalAmount: 30000.00,
      budgetUsedPercentage: 81.67,
      budgetRemainingMinor: 550000,
      budgetRemainingAmount: 5500.00,
    );
  }

  List<ActivityItemModel> _getFallbackActivities(String? month) {
    return [
      ActivityItemModel(
        id: 1,
        type: 'TRANSFER',
        reference: 'TXN-882194',
        title: 'Uber Eats Colombo',
        subtitle: 'Lunch order',
        amountMinor: 245000,
        amount: 2450.00,
        currency: 'LKR',
        direction: 'DEBIT',
        status: 'COMPLETED',
        category: 'FOOD',
        colorHex: '#F97316',
        fundingType: 'ACCOUNT',
        createdAt: DateTime.now().subtract(const Duration(hours: 3)),
      ),
      ActivityItemModel(
        id: 2,
        type: 'BILL',
        reference: 'BIL-992104',
        title: 'Ceylon Electricity Board',
        subtitle: 'Bill ref: •••• 5678',
        amountMinor: 520000,
        amount: 5200.00,
        currency: 'LKR',
        direction: 'DEBIT',
        status: 'COMPLETED',
        category: 'BILLS',
        colorHex: '#06B6D4',
        fundingType: 'ACCOUNT',
        createdAt: DateTime.now().subtract(const Duration(days: 2)),
      ),
      ActivityItemModel(
        id: 3,
        type: 'TRANSFER',
        reference: 'TXN-773120',
        title: 'Salary Credit - WSO2',
        subtitle: 'Monthly payroll',
        amountMinor: 6500000,
        amount: 65000.00,
        currency: 'LKR',
        direction: 'CREDIT',
        status: 'COMPLETED',
        category: 'TRANSFERS',
        colorHex: '#3B82F6',
        fundingType: 'ACCOUNT',
        createdAt: DateTime.now().subtract(const Duration(days: 4)),
      ),
    ];
  }
}
