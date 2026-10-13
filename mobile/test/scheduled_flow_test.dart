import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/scheduled/data/scheduled_service.dart';
import 'package:lankapay_id/features/scheduled/domain/scheduled_models.dart';
import 'package:lankapay_id/features/scheduled/presentation/scheduled_list_screen.dart';
import 'package:lankapay_id/features/scheduled/presentation/schedule_create_screen.dart';
import 'package:lankapay_id/features/scheduled/presentation/schedule_details_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// -----------------------------------------------------------------------------
// Test Data & Fake Scheduled Service
// -----------------------------------------------------------------------------

final sampleSchedule1 = ScheduledPaymentModel(
  id: 'SCHED-001',
  userId: 1,
  type: ScheduleType.transfer,
  payee: const ScheduledPayeeModel(
    type: ScheduleType.transfer,
    recipientIdentity: '+94771234567',
    recipientName: 'Kasun Bandara',
  ),
  amountMinor: 500000,
  amount: 5000.00,
  currency: 'LKR',
  frequency: ScheduleFrequency.monthly,
  startDate: '2026-10-15',
  nextRunAt: DateTime(2026, 10, 15, 9, 0),
  status: ScheduleStatus.active,
  consecutiveFailures: 0,
);

final sampleSchedule2 = ScheduledPaymentModel(
  id: 'SCHED-002',
  userId: 1,
  type: ScheduleType.bill,
  payee: const ScheduledPayeeModel(
    type: ScheduleType.bill,
    billerId: 'CEB-01',
    billerName: 'Simulated Electricity Board',
    customerReference: '1029384756',
  ),
  isBillAmount: true,
  currency: 'LKR',
  frequency: ScheduleFrequency.monthly,
  startDate: '2026-10-20',
  nextRunAt: DateTime(2026, 10, 20, 9, 0),
  status: ScheduleStatus.paused,
  consecutiveFailures: 2,
  lastFailureReason: 'Insufficient funds in funding account',
);

final sampleRuns = [
  ScheduledRunModel(
    id: 'RUN-101',
    scheduleId: 'SCHED-001',
    runDate: '2026-09-15',
    executedAt: DateTime(2026, 9, 15, 9, 0),
    status: 'SUCCESS',
    amount: 5000.00,
    amountMinor: 500000,
    transactionId: 987654,
  ),
  ScheduledRunModel(
    id: 'RUN-102',
    scheduleId: 'SCHED-001',
    runDate: '2026-08-15',
    executedAt: DateTime(2026, 8, 15, 9, 0),
    status: 'FAILED',
    amount: 5000.00,
    amountMinor: 500000,
    failureReason: 'Daily transfer limit exceeded',
  ),
];

class FakeScheduledService extends ScheduledService {
  final List<ScheduledPaymentModel> items;
  final List<ScheduledRunModel> runs;

  FakeScheduledService({
    List<ScheduledPaymentModel>? initialItems,
    List<ScheduledRunModel>? initialRuns,
  })  : items = initialItems != null ? List.from(initialItems) : [sampleSchedule1, sampleSchedule2],
        runs = initialRuns != null ? List.from(initialRuns) : List.from(sampleRuns),
        super(Dio());

  @override
  Future<List<ScheduledPaymentModel>> listSchedules({ScheduleStatus? status}) async {
    if (status != null) {
      return items.where((s) => s.status == status).toList();
    }
    return List.from(items);
  }

  @override
  Future<ScheduledPaymentModel> getSchedule(String id) async {
    return items.firstWhere((s) => s.id == id);
  }

  @override
  Future<ScheduledPaymentModel> createSchedule({
    required ScheduleType type,
    required Map<String, dynamic> payee,
    int? amountMinor,
    String currency = 'LKR',
    String? fundingSource,
    required ScheduleFrequency frequency,
    required String startDate,
    String? endDate,
    required String pin,
    required bool consentGiven,
    String? description,
    String? idempotencyKey,
  }) async {
    final newId = 'SCHED-${items.length + 1}';
    final created = ScheduledPaymentModel(
      id: newId,
      userId: 1,
      type: type,
      payee: ScheduledPayeeModel.fromJson(payee),
      amountMinor: amountMinor,
      amount: amountMinor != null ? amountMinor / 100.0 : null,
      currency: currency,
      isBillAmount: amountMinor == null,
      fundingSource: fundingSource,
      frequency: frequency,
      startDate: startDate,
      endDate: endDate,
      nextRunAt: DateTime.tryParse(startDate),
      status: ScheduleStatus.active,
      consecutiveFailures: 0,
    );
    items.add(created);
    return created;
  }

  @override
  Future<void> pauseSchedule(String id) async {
    final idx = items.indexWhere((s) => s.id == id);
    if (idx != -1) {
      final s = items[idx];
      items[idx] = ScheduledPaymentModel(
        id: s.id,
        userId: s.userId,
        type: s.type,
        payee: s.payee,
        amountMinor: s.amountMinor,
        amount: s.amount,
        currency: s.currency,
        isBillAmount: s.isBillAmount,
        fundingSource: s.fundingSource,
        frequency: s.frequency,
        startDate: s.startDate,
        endDate: s.endDate,
        nextRunAt: s.nextRunAt,
        status: ScheduleStatus.paused,
        consecutiveFailures: s.consecutiveFailures,
        lastFailureReason: s.lastFailureReason,
      );
    }
  }

  @override
  Future<void> resumeSchedule(String id) async {
    final idx = items.indexWhere((s) => s.id == id);
    if (idx != -1) {
      final s = items[idx];
      items[idx] = ScheduledPaymentModel(
        id: s.id,
        userId: s.userId,
        type: s.type,
        payee: s.payee,
        amountMinor: s.amountMinor,
        amount: s.amount,
        currency: s.currency,
        isBillAmount: s.isBillAmount,
        fundingSource: s.fundingSource,
        frequency: s.frequency,
        startDate: s.startDate,
        endDate: s.endDate,
        nextRunAt: s.nextRunAt,
        status: ScheduleStatus.active,
        consecutiveFailures: 0,
      );
    }
  }

  @override
  Future<void> cancelSchedule(String id) async {
    final idx = items.indexWhere((s) => s.id == id);
    if (idx != -1) {
      final s = items[idx];
      items[idx] = ScheduledPaymentModel(
        id: s.id,
        userId: s.userId,
        type: s.type,
        payee: s.payee,
        amountMinor: s.amountMinor,
        amount: s.amount,
        currency: s.currency,
        isBillAmount: s.isBillAmount,
        fundingSource: s.fundingSource,
        frequency: s.frequency,
        startDate: s.startDate,
        endDate: s.endDate,
        nextRunAt: null,
        status: ScheduleStatus.cancelled,
      );
    }
  }

  @override
  Future<List<ScheduledRunModel>> getRuns(String id) async {
    return runs.where((r) => r.scheduleId == id).toList();
  }
}

Widget createTestApp({
  required Widget child,
  FakeScheduledService? fakeService,
}) {
  final service = fakeService ?? FakeScheduledService();
  return ProviderScope(
    overrides: [
      scheduledServiceProvider.overrideWithValue(service),
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
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Scheduled Payments Flow Tests', () {
    testWidgets('1. Scheduled List Screen renders list items with sched_<id> keys, next run, and status badge', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final fake = FakeScheduledService();
      await tester.pumpWidget(createTestApp(
        child: const ScheduledListScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Check header
      expect(find.text('Scheduled Payments'), findsOneWidget);

      // Verify FAB with schedCreate key
      expect(find.byKey(AppTestKeys.schedCreate), findsOneWidget);

      // Verify items by their sched_<id> keys
      expect(find.byKey(AppTestKeys.sched('SCHED-001')), findsOneWidget);
      expect(find.byKey(AppTestKeys.sched('SCHED-002')), findsOneWidget);

      // Verify content for item 1
      expect(find.text('Kasun Bandara'), findsOneWidget);
      expect(find.text('LKR 5000.00'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(AppTestKeys.sched('SCHED-001')),
          matching: find.text('Active'),
        ),
        findsOneWidget,
      );
      expect(find.text('2026-10-15'), findsOneWidget);

      // Verify content for item 2
      expect(find.text('Simulated Electricity Board'), findsOneWidget);
      expect(find.text('Bill Amount'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(AppTestKeys.sched('SCHED-002')),
          matching: find.text('Paused'),
        ),
        findsOneWidget,
      );
      expect(find.text('Auto-paused after 2 failed runs. Check details.'), findsOneWidget);
    });

    testWidgets('2. Schedule Create Screen supports switching type, entering payee, amount, consent, PIN, and submit', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final fake = FakeScheduledService();
      await tester.pumpWidget(createTestApp(
        child: const ScheduleCreateScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Verify screen title
      expect(find.text('Create Schedule'), findsOneWidget);

      // Check test keys exist
      expect(find.byKey(AppTestKeys.schedTypeTransfer), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedTypeBill), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedPayeeField), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedAmountField), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedConsentCheckbox), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedPinField), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedCreate), findsOneWidget);

      // Fill in transfer details
      await tester.enterText(find.byKey(AppTestKeys.schedPayeeField), '+94779876543');
      await tester.enterText(find.byKey(AppTestKeys.schedAmountField), '2500.00');

      // Check consent checkbox
      await tester.tap(find.byKey(AppTestKeys.schedConsentCheckbox));
      await tester.pumpAndSettle();

      // Enter PIN
      await tester.enterText(find.byKey(AppTestKeys.schedPinField), '1234');
      await tester.pumpAndSettle();

      // Submit
      await tester.tap(find.byKey(AppTestKeys.schedCreate));
      await tester.pumpAndSettle();

      // Check that a new schedule was added to fake service
      expect(fake.items.length, 3);
      final created = fake.items.last;
      expect(created.payee.recipientIdentity, '+94779876543');
      expect(created.amountMinor, 250000);
      expect(created.status, ScheduleStatus.active);
    });

    testWidgets('3. Schedule Details Screen displays schedule info and run history', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final fake = FakeScheduledService();
      await tester.pumpWidget(createTestApp(
        child: ScheduleDetailsScreen(schedule: sampleSchedule1),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Title & Payee
      expect(find.text('Schedule Details'), findsOneWidget);
      expect(find.text('Kasun Bandara'), findsOneWidget);
      expect(find.text('LKR 5000.00'), findsWidgets);

      // Run history
      expect(find.text('Run History'), findsOneWidget);
      expect(find.text('2026-09-15'), findsOneWidget);
      expect(find.text('SUCCESS'), findsOneWidget);
      expect(find.text('2026-08-15'), findsOneWidget);
      expect(find.text('FAILED'), findsOneWidget);
      expect(find.text('Daily transfer limit exceeded'), findsOneWidget);
    });

    testWidgets('4. Pause confirmation dialog triggers on sched_pause and confirms via sched_confirm_dialog_btn', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final fake = FakeScheduledService();
      await tester.pumpWidget(createTestApp(
        child: ScheduleDetailsScreen(schedule: sampleSchedule1),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Tap Pause button
      expect(find.byKey(AppTestKeys.schedPause), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.schedPause));
      await tester.pumpAndSettle();

      // Check dialog
      expect(find.text('Pause Scheduled Payment?'), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedConfirmDialogBtn), findsOneWidget);

      // Confirm pause
      await tester.tap(find.byKey(AppTestKeys.schedConfirmDialogBtn));
      await tester.pumpAndSettle();

      // Verify schedule status in fake service is now PAUSED
      final updated = fake.items.firstWhere((s) => s.id == 'SCHED-001');
      expect(updated.status, ScheduleStatus.paused);
    });

    testWidgets('5. Resume confirmation dialog triggers on sched_resume and confirms via sched_confirm_dialog_btn', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final fake = FakeScheduledService();
      await tester.pumpWidget(createTestApp(
        child: ScheduleDetailsScreen(schedule: sampleSchedule2),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Tap Resume button
      expect(find.byKey(AppTestKeys.schedResume), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.schedResume));
      await tester.pumpAndSettle();

      // Check dialog
      expect(find.text('Resume Scheduled Payment?'), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedConfirmDialogBtn), findsOneWidget);

      // Confirm resume
      await tester.tap(find.byKey(AppTestKeys.schedConfirmDialogBtn));
      await tester.pumpAndSettle();

      // Verify schedule status in fake service is now ACTIVE
      final updated = fake.items.firstWhere((s) => s.id == 'SCHED-002');
      expect(updated.status, ScheduleStatus.active);
    });

    testWidgets('6. Cancel confirmation dialog triggers on sched_cancel and confirms via sched_confirm_dialog_btn', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      final fake = FakeScheduledService();
      await tester.pumpWidget(createTestApp(
        child: ScheduleDetailsScreen(schedule: sampleSchedule1),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Tap Cancel button
      expect(find.byKey(AppTestKeys.schedCancel), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.schedCancel));
      await tester.pumpAndSettle();

      // Check dialog
      expect(find.text('Cancel Scheduled Payment?'), findsOneWidget);
      expect(find.byKey(AppTestKeys.schedConfirmDialogBtn), findsOneWidget);

      // Confirm cancel
      await tester.tap(find.byKey(AppTestKeys.schedConfirmDialogBtn));
      await tester.pumpAndSettle();

      // Verify schedule status in fake service is now CANCELLED
      final updated = fake.items.firstWhere((s) => s.id == 'SCHED-001');
      expect(updated.status, ScheduleStatus.cancelled);
    });
  });
}
