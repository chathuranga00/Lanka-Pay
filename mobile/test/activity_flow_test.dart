import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/analytics/data/analytics_service.dart';
import 'package:lankapay_id/features/analytics/domain/analytics_models.dart';
import 'package:lankapay_id/features/analytics/presentation/activity_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// -----------------------------------------------------------------------------
// Fake Analytics Service for Widget Tests
// -----------------------------------------------------------------------------
class FakeAnalyticsService extends AnalyticsService {
  String currentMonth = '2026-10';
  AnalyticsSummaryModel? mockSummary;
  List<ActivityItemModel>? mockActivities;
  BudgetModel? mockBudget;
  bool setBudgetCalled = false;
  int? lastSetBudgetMinor;

  FakeAnalyticsService() : super(Dio());

  @override
  Future<AnalyticsSummaryModel> getSummary(String month) async {
    if (mockSummary != null) return mockSummary!;
    return AnalyticsSummaryModel(
      month: month,
      spentMinor: 1540000,
      spentAmount: 15400.00,
      receivedMinor: 5000000,
      receivedAmount: 50000.00,
      pendingSpentMinor: 200000,
      pendingSpentAmount: 2000.00,
      pendingCount: 1,
      byCategoryTotals: [
        const CategoryTotalModel(
          category: 'FOOD',
          displayName: 'Food & Dining',
          colorHex: '#F97316',
          totalMinor: 750000,
          totalAmount: 7500.00,
          count: 3,
          percentage: 48.7,
        ),
        const CategoryTotalModel(
          category: 'BILLS',
          displayName: 'Bills & Utilities',
          colorHex: '#06B6D4',
          totalMinor: 550000,
          totalAmount: 5500.00,
          count: 2,
          percentage: 35.7,
        ),
        const CategoryTotalModel(
          category: 'TRANSPORT',
          displayName: 'Transport',
          colorHex: '#10B981',
          totalMinor: 240000,
          totalAmount: 2400.00,
          count: 2,
          percentage: 15.6,
        ),
      ],
      topPayees: const [
        TopPayeeModel(name: 'Ceylon Electricity Board', totalMinor: 550000, totalAmount: 5500.00, count: 2),
        TopPayeeModel(name: 'Cafe Kumbuk', totalMinor: 450000, totalAmount: 4500.00, count: 2),
      ],
      budgetTotalMinor: 2000000,
      budgetTotalAmount: 20000.00,
      budgetUsedPercentage: 77.0,
      budgetRemainingMinor: 460000,
      budgetRemainingAmount: 4600.00,
    );
  }

  @override
  Future<List<ActivityItemModel>> listActivity({
    String? month,
    String? direction,
    String? category,
  }) async {
    if (mockActivities != null) return mockActivities!;
    return [
      ActivityItemModel(
        id: 101,
        type: 'TRANSFER',
        reference: 'TXN-001',
        title: 'Cafe Kumbuk',
        subtitle: 'Lunch',
        amountMinor: 250000,
        amount: 2500.00,
        currency: 'LKR',
        direction: 'DEBIT',
        status: 'COMPLETED',
        category: 'FOOD',
        colorHex: '#F97316',
        fundingType: 'ACCOUNT',
        cardLast4: '4321',
        bankCode: '7010',
        createdAt: DateTime.now(),
      ),
      ActivityItemModel(
        id: 102,
        type: 'BILL',
        reference: 'BIL-002',
        title: 'Ceylon Electricity Board',
        subtitle: 'Bill ref: •••• 5678',
        amountMinor: 550000,
        amount: 5500.00,
        currency: 'LKR',
        direction: 'DEBIT',
        status: 'COMPLETED',
        category: 'BILLS',
        colorHex: '#06B6D4',
        fundingType: 'ACCOUNT',
        createdAt: DateTime.now().subtract(const Duration(days: 1)),
      ),
    ];
  }

  @override
  Future<BudgetModel> setBudget(String month, int monthlyTotalMinor, [Map<String, int>? categoryBudgetsMinor]) async {
    setBudgetCalled = true;
    lastSetBudgetMinor = monthlyTotalMinor;
    return BudgetModel(
      id: 'BGT-1',
      userId: 101,
      month: month,
      monthlyTotalMinor: monthlyTotalMinor,
      monthlyTotalAmount: monthlyTotalMinor / 100.0,
      spentMinor: 1540000,
      spentAmount: 15400.00,
      usedPercentage: (1540000 * 100.0) / monthlyTotalMinor,
      remainingMinor: monthlyTotalMinor - 1540000,
      remainingAmount: (monthlyTotalMinor - 1540000) / 100.0,
      categoryBudgetsMinor: categoryBudgetsMinor ?? {},
      notified80: false,
      notified100: false,
    );
  }
}

class FakeSelectedMonthNotifier extends SelectedMonthNotifier {
  final String initial;
  FakeSelectedMonthNotifier(this.initial);

  @override
  String build() => initial;
}

Widget createTestApp({
  required Widget child,
  FakeAnalyticsService? fakeService,
  String initialMonth = '2026-10',
}) {
  final service = fakeService ?? FakeAnalyticsService();
  return ProviderScope(
    overrides: [
      analyticsServiceProvider.overrideWithValue(service),
      selectedMonthProvider.overrideWith(() => FakeSelectedMonthNotifier(initialMonth)),
    ],
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('en')],
      home: child,
    ),
  );
}

void main() {
  group('Activity Screen Flow Tests', () {
    testWidgets('Renders month switcher and responds to activity_month_prev/next', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeAnalyticsService();
      await tester.pumpWidget(createTestApp(
        child: const ActivityScreen(),
        fakeService: fake,
        initialMonth: '2026-10',
      ));
      await tester.pumpAndSettle();

      // Check header title and month switcher
      expect(find.text('All Activity'), findsOneWidget);
      expect(find.text('October 2026'), findsOneWidget);

      // Verify presence of prev and next buttons
      expect(find.byKey(AppTestKeys.activityMonthPrev), findsOneWidget);
      expect(find.byKey(AppTestKeys.activityMonthNext), findsOneWidget);

      // Tap next month
      await tester.tap(find.byKey(AppTestKeys.activityMonthNext));
      await tester.pumpAndSettle();

      // Month changes to November 2026
      expect(find.text('November 2026'), findsOneWidget);

      // Tap previous month twice
      await tester.tap(find.byKey(AppTestKeys.activityMonthPrev));
      await tester.pumpAndSettle();
      expect(find.text('October 2026'), findsOneWidget);

      await tester.tap(find.byKey(AppTestKeys.activityMonthPrev));
      await tester.pumpAndSettle();
      expect(find.text('September 2026'), findsOneWidget);
    });

    testWidgets('Displays categories and tests category chip cat_<name>', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeAnalyticsService();
      await tester.pumpWidget(createTestApp(
        child: const ActivityScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Verify category items with keys cat_<name>
      expect(find.byKey(AppTestKeys.cat('food')), findsOneWidget);
      expect(find.byKey(AppTestKeys.cat('bills')), findsOneWidget);
      expect(find.byKey(AppTestKeys.cat('transport')), findsOneWidget);

      expect(find.text('Food & Dining'), findsOneWidget);
      expect(find.text('Bills & Utilities'), findsOneWidget);
      expect(find.text('Transport'), findsOneWidget);

      // Tap on a category item to filter
      await tester.tap(find.byKey(AppTestKeys.cat('food')));
      await tester.pumpAndSettle();

      // Filter chip for Category: FOOD appears
      expect(find.text('Category: FOOD'), findsOneWidget);
    });

    testWidgets('Budget edit sheet opens and saves with key budget_edit', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeAnalyticsService();
      await tester.pumpWidget(createTestApp(
        child: const ActivityScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Budget edit button is present
      expect(find.byKey(AppTestKeys.budgetEdit), findsOneWidget);
      expect(find.text('Monthly Budget'), findsOneWidget);

      // Tap budget_edit
      await tester.tap(find.byKey(AppTestKeys.budgetEdit));
      await tester.pumpAndSettle();

      // Modal bottom sheet opens
      expect(find.text('Set Monthly Budget'), findsOneWidget);
      expect(find.text('Save Budget'), findsOneWidget);

      // Enter new budget
      final field = find.byType(TextField);
      expect(field, findsOneWidget);
      await tester.enterText(field, '35000');
      await tester.pumpAndSettle();

      // Tap save
      await tester.tap(find.text('Save Budget'));
      await tester.pumpAndSettle();

      expect(fake.setBudgetCalled, isTrue);
      expect(fake.lastSetBudgetMinor, 3500000);
    });

    testWidgets('Item detail opens receipt sheet with receipt_share key', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeAnalyticsService();
      await tester.pumpWidget(createTestApp(
        child: const ActivityScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Tap on transaction item
      final itemFinder = find.text('Cafe Kumbuk');
      expect(itemFinder, findsOneWidget);
      await tester.tap(itemFinder);
      await tester.pumpAndSettle();

      // Receipt modal opens
      expect(find.text('Transaction Receipt'), findsOneWidget);
      expect(find.text('TXN-001'), findsOneWidget);
      expect(find.text('•••• 4321'), findsOneWidget);

      // Action buttons
      expect(find.byKey(AppTestKeys.receiptShare), findsOneWidget);
      expect(find.byKey(AppTestKeys.receiptDownload), findsOneWidget);

      // Tap share
      await tester.tap(find.byKey(AppTestKeys.receiptShare));
      await tester.pumpAndSettle();

      expect(find.text('Receipt shared successfully'), findsOneWidget);
    });

    testWidgets('Empty state renders "No spending in <month>" when list is empty', (tester) async {
      final fake = FakeAnalyticsService();
      fake.mockActivities = [];
      fake.mockSummary = const AnalyticsSummaryModel(
        month: '2026-10',
        spentMinor: 0,
        spentAmount: 0.0,
        receivedMinor: 0,
        receivedAmount: 0.0,
        pendingSpentMinor: 0,
        pendingSpentAmount: 0.0,
        pendingCount: 0,
        byCategoryTotals: [],
        topPayees: [],
      );

      await tester.pumpWidget(createTestApp(
        child: const ActivityScreen(),
        fakeService: fake,
        initialMonth: '2026-10',
      ));
      await tester.pumpAndSettle();

      // Verify empty state text
      expect(find.text('No spending in October 2026'), findsOneWidget);
    });
  });
}
