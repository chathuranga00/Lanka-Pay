import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/features/family/data/family_service.dart';
import 'package:lankapay_id/features/family/presentation/guardian_dashboard_screen.dart';
import 'package:lankapay_id/features/family/presentation/child_view_screen.dart';

void main() {
  Widget buildDashboardApp() {
    return const ProviderScope(
      child: MaterialApp(
        home: GuardianDashboardScreen(),
      ),
    );
  }

  Widget buildChildApp(String memberId) {
    return ProviderScope(
      child: MaterialApp(
        home: ChildViewScreen(memberId: memberId),
      ),
    );
  }

  void configureLargeViewport(WidgetTester tester) {
    tester.view.physicalSize = const Size(1080, 2800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
  }

  group('Guardian-Managed Family Module Tests', () {
    testWidgets('1. Guardian dashboard renders with legal notice and family_<id> cards', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildDashboardApp());
      await tester.pumpAndSettle();

      // Check header and simulated badge
      expect(find.text('Family Finance'), findsOneWidget);
      expect(find.text('SIMULATED'), findsOneWidget);

      // Check compliance notice
      expect(find.byKey(AppTestKeys.familySimulatedNotice), findsOneWidget);
      expect(find.textContaining('minors\' data privacy'), findsOneWidget);

      // Check member cards with family_<id>
      expect(find.byKey(AppTestKeys.family('family_1')), findsOneWidget);
      expect(find.byKey(AppTestKeys.family('family_2')), findsOneWidget);

      // Check names and initial balances
      expect(find.text('Senula'), findsOneWidget);
      expect(find.text('Kaveesha'), findsOneWidget);
      expect(find.text('Rs. 2500.00'), findsOneWidget);
      expect(find.text('Rs. 5000.00'), findsOneWidget);
    });

    testWidgets('2. Guardian controls: update spending limits using family_limit key', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildDashboardApp());
      await tester.pumpAndSettle();

      // Tap Limits & Rules for family_1
      final limitsBtn = find.byKey(AppTestKeys.familyLimit).first;
      expect(limitsBtn, findsOneWidget);
      await tester.tap(limitsBtn);
      await tester.pumpAndSettle();

      // Modal appears
      expect(find.text('Spending Controls - Senula'), findsOneWidget);
      expect(find.byKey(AppTestKeys.familyDailyLimitInput), findsOneWidget);
      expect(find.byKey(AppTestKeys.familyMonthlyLimitInput), findsOneWidget);

      // Update daily limit to 1500 LKR
      await tester.enterText(find.byKey(AppTestKeys.familyDailyLimitInput), '1500');
      await tester.pumpAndSettle();

      // Save
      await tester.tap(find.byKey(AppTestKeys.familySaveLimitsBtn));
      await tester.pumpAndSettle();

      // Modal closed, card reflects new limit
      expect(find.textContaining('1500.00'), findsOneWidget);
    });

    testWidgets('3. Guardian controls: freeze toggle freezes account', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildDashboardApp());
      await tester.pumpAndSettle();

      // Toggle freeze switch on first card
      final freezeSwitch = find.byKey(AppTestKeys.familyFreezeToggle).first;
      expect(freezeSwitch, findsOneWidget);
      await tester.tap(freezeSwitch);
      await tester.pumpAndSettle();

      // Card now indicates frozen
      expect(find.text('ACCOUNT FROZEN'), findsOneWidget);
    });

    testWidgets('4. Guardian controls: fund simulated balance', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildDashboardApp());
      await tester.pumpAndSettle();

      // Tap Fund on first card
      final fundBtn = find.byKey(AppTestKeys.familyFundBtn).first;
      await tester.tap(fundBtn);
      await tester.pumpAndSettle();

      // Funding modal appears
      expect(find.text('Fund Allowance - Senula'), findsOneWidget);
      expect(find.byKey(AppTestKeys.familyFundAmountInput), findsOneWidget);

      // Enter 500 LKR
      await tester.enterText(find.byKey(AppTestKeys.familyFundAmountInput), '500');
      await tester.pumpAndSettle();

      // Confirm
      await tester.tap(find.byKey(AppTestKeys.familyFundSubmitBtn));
      await tester.pumpAndSettle();

      // Balance updated from 2500 to 3000
      expect(find.text('Rs. 3000.00'), findsOneWidget);
    });

    testWidgets('5. Create new family profile with data minimisation (first name only)', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildDashboardApp());
      await tester.pumpAndSettle();

      // Tap Add Child
      await tester.tap(find.byKey(AppTestKeys.familyAddMemberBtn));
      await tester.pumpAndSettle();

      expect(find.text('Add Child Profile'), findsOneWidget);
      expect(find.byKey(AppTestKeys.familyFirstNameInput), findsOneWidget);

      // Enter name
      await tester.enterText(find.byKey(AppTestKeys.familyFirstNameInput), 'Amaya');
      await tester.pumpAndSettle();

      // Submit
      await tester.tap(find.byKey(AppTestKeys.familyCreateSubmitBtn));
      await tester.pumpAndSettle();

      // New profile rendered
      expect(find.text('Amaya'), findsOneWidget);
    });

    testWidgets('6. Child view screen displays safe minor view without adult/banking details', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildChildApp('family_1'));
      await tester.pumpAndSettle();

      // Child space rendered
      expect(find.text('Senula\'s Space'), findsOneWidget);
      expect(find.text('CHILD SAFE'), findsOneWidget);
      expect(find.text('Hello, Senula!'), findsOneWidget);
      expect(find.text('Ready to Spend'), findsOneWidget);

      // Available balance
      expect(find.text('Available Balance'), findsOneWidget);
      expect(find.text('Rs. 2500.00'), findsOneWidget);

      // Allowed categories shown
      expect(find.text('Where You Can Spend'), findsOneWidget);
      expect(find.text('FOOD'), findsWidgets);
      expect(find.text('EDUCATION'), findsOneWidget);
      expect(find.text('BOOKSTORE'), findsOneWidget);

      // Recent Activity
      expect(find.text('Recent Activity'), findsOneWidget);
      expect(find.text('School Canteen'), findsOneWidget);
    });

    testWidgets('7. Simulated spend enforcement: success within limits and blocked when exceeded', (tester) async {
      final container = ProviderContainer();
      addTearDown(container.dispose);

      final notifier = container.read(familyNotifierProvider.notifier);

      // Senula has daily limit 1000.00 LKR (100000 minor) and already spent 250.00 LKR (25000 minor)
      // Balance is 2500.00 LKR (250000 minor)

      // 1. Spend 200.00 LKR (20000 minor) on FOOD -> Succeeds
      final success1 = notifier.spend('family_1', 20000, 'FOOD', 'School Canteen');
      expect(success1, isTrue);

      // 2. Spend on unallowed category (e.g. GAMBLING) -> Blocked
      final successCat = notifier.spend('family_1', 5000, 'GAMBLING', 'Online Games');
      expect(successCat, isFalse);

      // 3. Spend exceeding daily limit (e.g. 900.00 LKR, which exceeds remaining 550.00) -> Blocked
      final successLimit = notifier.spend('family_1', 90000, 'FOOD', 'Dinner');
      expect(successLimit, isFalse);

      // 4. Freeze account and try to spend -> Blocked
      notifier.toggleFreeze('family_1', true);
      final successFrozen = notifier.spend('family_1', 1000, 'FOOD', 'Snack');
      expect(successFrozen, isFalse);
    });

    testWidgets('8. Delete profile purges member data per retention rules', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildDashboardApp());
      await tester.pumpAndSettle();

      expect(find.text('Kaveesha'), findsOneWidget);

      // Tap delete on second member
      final deleteBtn = find.byKey(AppTestKeys.familyDeleteBtn).last;
      await tester.tap(deleteBtn);
      await tester.pumpAndSettle();

      // Dialog shown
      expect(find.text('Delete Kaveesha\'s Profile?'), findsOneWidget);
      expect(find.textContaining('permanently removes all child records'), findsOneWidget);

      // Confirm delete
      await tester.tap(find.text('Delete Profile'));
      await tester.pumpAndSettle();

      // Profile is removed
      expect(find.text('Kaveesha'), findsNothing);
    });
  });
}
