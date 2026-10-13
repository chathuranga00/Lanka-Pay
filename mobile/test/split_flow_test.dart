import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/split/data/split_service.dart';
import 'package:lankapay_id/features/split/domain/split_models.dart';
import 'package:lankapay_id/features/split/presentation/split_create_screen.dart';
import 'package:lankapay_id/features/split/presentation/split_details_screen.dart';
import 'package:lankapay_id/features/split/presentation/split_pay_request_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// -----------------------------------------------------------------------------
// Test Data & Fake Split Service
// -----------------------------------------------------------------------------

final sampleParticipant1 = SplitParticipantModel(
  userId: 102,
  phone: '+94772222222',
  displayName: 'Nimal Silva',
  shareMinor: 250000,
  status: ParticipantStatus.paid,
  paidAt: DateTime.now(),
  canRemind: false,
);

final sampleParticipant2 = SplitParticipantModel(
  userId: 103,
  phone: '+94773333333',
  displayName: 'Sanduni Jayawardena',
  shareMinor: 250000,
  status: ParticipantStatus.invited,
  canRemind: true,
);

final sampleParticipant3 = SplitParticipantModel(
  userId: 104,
  phone: '+94774444444',
  displayName: 'Dinesh Fernando',
  shareMinor: 250000,
  status: ParticipantStatus.invited,
  canRemind: true,
);

final sampleParticipant4 = SplitParticipantModel(
  userId: 105,
  phone: '+94775555555',
  displayName: 'Amara Weerasinghe',
  shareMinor: 250000,
  status: ParticipantStatus.invited,
  canRemind: true,
);

final sampleSplit1 = SplitModel(
  id: 'SPLIT-101',
  ownerUserId: 101,
  ownerName: 'Kamal Perera',
  title: 'Dutch Hospital Lunch',
  totalMinor: 1000000,
  ownerShareMinor: 250000,
  currency: 'LKR',
  participants: [sampleParticipant1, sampleParticipant2, sampleParticipant3],
  expiresAt: DateTime.now().add(const Duration(hours: 48)),
  status: SplitStatus.active,
  totalParticipants: 3,
  paidCount: 1,
  collectedMinor: 250000,
  progressPercent: 33.33,
  createdAt: DateTime.now(),
);

class FakeSplitService extends SplitService {
  final List<SplitModel> splits;
  bool remindCalled = false;
  bool cancelCalled = false;
  bool payCalled = false;

  FakeSplitService({List<SplitModel>? initialSplits})
      : splits = initialSplits != null ? List.from(initialSplits) : [sampleSplit1],
        super(Dio());

  @override
  Future<List<SplitModel>> listSplits() async {
    return List.from(splits);
  }

  @override
  Future<SplitModel> getSplit(String id) async {
    return splits.firstWhere((s) => s.id == id);
  }

  @override
  Future<SplitModel> createSplit({
    required String title,
    required int totalMinor,
    String currency = 'LKR',
    String mode = 'EQUAL',
    required List<Map<String, dynamic>> participants,
    int validityHours = 48,
  }) async {
    final newId = 'SPLIT-${splits.length + 1}';
    final pModels = participants.map((p) {
      return SplitParticipantModel(
        userId: 200 + participants.indexOf(p),
        phone: p['phone'] as String?,
        displayName: p['name'] as String? ?? p['phone'] as String?,
        shareMinor: (p['shareMinor'] as num?)?.toInt() ?? (totalMinor ~/ (participants.length + 1)),
        status: ParticipantStatus.invited,
        canRemind: true,
      );
    }).toList();

    final created = SplitModel(
      id: newId,
      ownerUserId: 101,
      ownerName: 'Kamal Perera',
      title: title,
      totalMinor: totalMinor,
      ownerShareMinor: totalMinor ~/ (participants.length + 1),
      currency: currency,
      participants: pModels,
      expiresAt: DateTime.now().add(Duration(hours: validityHours)),
      status: SplitStatus.active,
      totalParticipants: pModels.length,
      paidCount: 0,
      collectedMinor: 0,
      progressPercent: 0.0,
      createdAt: DateTime.now(),
    );
    splits.add(created);
    return created;
  }

  @override
  Future<SplitModel> remindParticipant(String id, int participantId) async {
    remindCalled = true;
    final idx = splits.indexWhere((s) => s.id == id);
    if (idx != -1) {
      final s = splits[idx];
      final updatedP = s.participants.map((p) {
        if (p.userId == participantId) {
          return SplitParticipantModel(
            userId: p.userId,
            phone: p.phone,
            displayName: p.displayName,
            shareMinor: p.shareMinor,
            status: p.status,
            paidAt: p.paidAt,
            lastRemindedAt: DateTime.now(),
            canRemind: false, // 24-hour rate limit active
          );
        }
        return p;
      }).toList();

      final updated = SplitModel(
        id: s.id,
        ownerUserId: s.ownerUserId,
        ownerName: s.ownerName,
        title: s.title,
        totalMinor: s.totalMinor,
        ownerShareMinor: s.ownerShareMinor,
        currency: s.currency,
        participants: updatedP,
        expiresAt: s.expiresAt,
        status: s.status,
        totalParticipants: s.totalParticipants,
        paidCount: s.paidCount,
        collectedMinor: s.collectedMinor,
        progressPercent: s.progressPercent,
        createdAt: s.createdAt,
      );
      splits[idx] = updated;
      return updated;
    }
    throw Exception('Split not found');
  }

  @override
  Future<SplitModel> cancelSplit(String id) async {
    cancelCalled = true;
    final idx = splits.indexWhere((s) => s.id == id);
    if (idx != -1) {
      final s = splits[idx];
      final updatedP = s.participants.map((p) {
        return SplitParticipantModel(
          userId: p.userId,
          phone: p.phone,
          displayName: p.displayName,
          shareMinor: p.shareMinor,
          status: p.status == ParticipantStatus.paid ? ParticipantStatus.paid : ParticipantStatus.cancelled,
          paidAt: p.paidAt,
          canRemind: false,
        );
      }).toList();

      final updated = SplitModel(
        id: s.id,
        ownerUserId: s.ownerUserId,
        ownerName: s.ownerName,
        title: s.title,
        totalMinor: s.totalMinor,
        ownerShareMinor: s.ownerShareMinor,
        currency: s.currency,
        participants: updatedP,
        expiresAt: s.expiresAt,
        status: SplitStatus.cancelled,
        totalParticipants: s.totalParticipants,
        paidCount: s.paidCount,
        collectedMinor: s.collectedMinor,
        progressPercent: s.progressPercent,
        createdAt: s.createdAt,
      );
      splits[idx] = updated;
      return updated;
    }
    throw Exception('Split not found');
  }

  @override
  Future<SplitModel> payShare(
    String id, {
    required String pin,
    String? fundingSource,
    String? idempotencyKey,
  }) async {
    payCalled = true;
    final idx = splits.indexWhere((s) => s.id == id);
    if (idx != -1) {
      final s = splits[idx];
      final updatedP = s.participants.map((p) {
        return SplitParticipantModel(
          userId: p.userId,
          phone: p.phone,
          displayName: p.displayName,
          shareMinor: p.shareMinor,
          status: ParticipantStatus.paid,
          paidAt: DateTime.now(),
          canRemind: false,
        );
      }).toList();

      final updated = SplitModel(
        id: s.id,
        ownerUserId: s.ownerUserId,
        ownerName: s.ownerName,
        title: s.title,
        totalMinor: s.totalMinor,
        ownerShareMinor: s.ownerShareMinor,
        currency: s.currency,
        participants: updatedP,
        expiresAt: s.expiresAt,
        status: SplitStatus.completed,
        totalParticipants: s.totalParticipants,
        paidCount: updatedP.length,
        collectedMinor: s.totalMinor - s.ownerShareMinor,
        progressPercent: 100.0,
        createdAt: s.createdAt,
      );
      splits[idx] = updated;
      return updated;
    }
    throw Exception('Split not found');
  }
}

Widget createTestApp({
  required Widget child,
  FakeSplitService? fakeService,
}) {
  final service = fakeService ?? FakeSplitService();
  return ProviderScope(
    overrides: [
      splitServiceProvider.overrideWithValue(service),
    ],
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('en', ''),
        Locale('si', ''),
        Locale('ta', ''),
      ],
      home: child,
    ),
  );
}

void main() {
  group('Split Bills Flow Tests', () {
    testWidgets('1. Split Creator renders and creates equal split with participants', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fakeService = FakeSplitService();

      await tester.pumpWidget(createTestApp(
        child: const SplitCreateScreen(),
        fakeService: fakeService,
      ));
      await tester.pumpAndSettle();

      // Verify title and amount fields exist
      expect(find.byKey(TestKeys.splitTitleField), findsOneWidget);
      expect(find.byKey(TestKeys.splitAmountField), findsOneWidget);
      expect(find.byKey(TestKeys.splitEqualMode), findsOneWidget);
      expect(find.byKey(TestKeys.splitCustomMode), findsOneWidget);
      expect(find.byKey(TestKeys.splitAddPerson), findsOneWidget);
      expect(find.byKey(TestKeys.splitSend), findsOneWidget);

      // Enter title and amount
      await tester.enterText(find.byKey(TestKeys.splitTitleField), 'Dinner at Ministry of Crab');
      await tester.enterText(find.byKey(TestKeys.splitAmountField), '9000');
      await tester.pumpAndSettle();

      // Add a participant by phone
      await tester.enterText(find.byKey(TestKeys.splitPersonPhoneField), '+94778888888');
      await tester.tap(find.byKey(TestKeys.splitAddPerson));
      await tester.pumpAndSettle();

      // Check participant is added
      expect(find.text('+94778888888'), findsWidgets);

      // Also tap recent recipient chip
      expect(find.text('Kamal Perera'), findsOneWidget);
      await tester.tap(find.text('Kamal Perera'));
      await tester.pumpAndSettle();

      // Now we have 2 participants added
      expect(find.text('Create Split (2 Participants)'), findsOneWidget);

      // Tap Send / Create button
      await tester.tap(find.byKey(TestKeys.splitSend));
      await tester.pumpAndSettle();

      // Verify split was created in service
      expect(fakeService.splits.any((s) => s.title == 'Dinner at Ministry of Crab'), isTrue);
    });

    testWidgets('2. Split Detail displays progress "1 of 3 paid" and participant statuses', (tester) async {
      final fakeService = FakeSplitService();

      await tester.pumpWidget(createTestApp(
        child: SplitDetailsScreen(
          splitId: sampleSplit1.id,
          initialSplit: sampleSplit1,
        ),
        fakeService: fakeService,
      ));
      await tester.pumpAndSettle();

      // Check screen key
      expect(find.byKey(TestKeys.split(sampleSplit1.id)), findsOneWidget);
      expect(find.byKey(TestKeys.splitProgress), findsOneWidget);

      // Progress string: 1 of 3 paid
      expect(find.text('1 of 3 paid'), findsOneWidget);
      expect(find.text('Dutch Hospital Lunch'), findsWidgets);

      // Check participant status badges
      expect(find.text('Paid'), findsOneWidget);
      expect(find.text('Invited'), findsNWidgets(2));
      expect(find.text('Nimal Silva'), findsOneWidget);
      expect(find.text('Sanduni Jayawardena'), findsOneWidget);
    });

    testWidgets('3. Reminders are rate limited: Owner reminds participant and cooldown activates', (tester) async {
      final fakeService = FakeSplitService();

      await tester.pumpWidget(createTestApp(
        child: SplitDetailsScreen(
          splitId: sampleSplit1.id,
          initialSplit: sampleSplit1,
        ),
        fakeService: fakeService,
      ));
      await tester.pumpAndSettle();

      // Participant 103 (Sanduni) is invited with canRemind=true
      final remindBtnKey = TestKeys.splitRemind(103);
      expect(find.byKey(remindBtnKey), findsOneWidget);

      // Tap remind
      await tester.tap(find.byKey(remindBtnKey));
      await tester.pumpAndSettle();

      // Check fake service was invoked
      expect(fakeService.remindCalled, isTrue);

      // Verify reminder state updated
      expect(find.text('Reminded'), findsOneWidget);
    });

    testWidgets('4. Owner can cancel unpaid split', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fakeService = FakeSplitService();

      await tester.pumpWidget(createTestApp(
        child: SplitDetailsScreen(
          splitId: sampleSplit1.id,
          initialSplit: sampleSplit1,
        ),
        fakeService: fakeService,
      ));
      await tester.pumpAndSettle();

      // Find cancel button
      expect(find.byKey(TestKeys.splitCancel), findsOneWidget);
      await tester.tap(find.byKey(TestKeys.splitCancel));
      await tester.pumpAndSettle();

      // Confirmation dialog pops up
      expect(find.text('Cancel Split?'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Cancel Split'));
      await tester.pumpAndSettle();

      // Check fake service was cancelled
      expect(fakeService.cancelCalled, isTrue);
    });

    testWidgets('5. Participant Request Screen displays amount and pays share with PIN', (tester) async {
      final fakeService = FakeSplitService();

      await tester.pumpWidget(createTestApp(
        child: SplitPayRequestScreen(
          splitId: sampleSplit1.id,
          initialSplit: sampleSplit1,
        ),
        fakeService: fakeService,
      ));
      await tester.pumpAndSettle();

      // Verifies bill title, owner, and pay button
      expect(find.text('Kamal Perera'), findsOneWidget);
      expect(find.text('Dutch Hospital Lunch'), findsOneWidget);
      expect(find.byKey(TestKeys.splitPinField), findsOneWidget);
      expect(find.byKey(TestKeys.splitPay), findsOneWidget);

      // Enter step-up PIN
      await tester.enterText(find.byKey(TestKeys.splitPinField), '1234');
      await tester.pumpAndSettle();

      // Tap Pay
      await tester.tap(find.byKey(TestKeys.splitPay));
      await tester.pumpAndSettle();

      // Check payment was executed
      expect(fakeService.payCalled, isTrue);
      expect(find.text('Payment Successful!'), findsOneWidget);
    });
  });
}
