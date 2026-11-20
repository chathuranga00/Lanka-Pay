import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/config/feature_flags.dart';
import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/features/explore/presentation/explore_screen.dart';
import 'package:lankapay_id/features/goals/data/goals_service.dart';
import 'package:lankapay_id/features/goals/presentation/goal_details_screen.dart';
import 'package:lankapay_id/features/goals/presentation/goals_screen.dart';

void main() {
  Widget buildExploreApp({Map<String, bool>? flags}) {
    return ProviderScope(
      overrides: [
        if (flags != null)
          featureFlagsProvider.overrideWith(
            () => FeatureFlagsNotifier(flags),
          ),
      ],
      child: const MaterialApp(
        home: ExploreScreen(),
      ),
    );
  }

  Widget buildGoalsApp() {
    return const ProviderScope(
      child: MaterialApp(
        home: GoalsScreen(),
      ),
    );
  }

  Widget buildGoalDetailsApp(String goalId) {
    return ProviderScope(
      child: MaterialApp(
        home: GoalDetailsScreen(goalId: goalId),
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

  group('Explore Showcase Tab Tests', () {
    testWidgets('1. Renders all showcase sections and module tiles with explore_<module> keys', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildExploreApp(flags: {
        FeatureFlagKeys.family: false,
      }));
      await tester.pumpAndSettle();

      // Search input
      expect(find.byKey(AppTestKeys.exploreSearch), findsOneWidget);

      // Section 1: My money
      expect(find.byKey(AppTestKeys.exploreAccounts), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreCards), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreGoals), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreStatements), findsOneWidget);

      // Section 2: Pay
      expect(find.byKey(AppTestKeys.exploreBills), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreScheduled), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreSplit), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreQr), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreRequests), findsOneWidget);

      // Section 3: Rewards & City
      expect(find.byKey(AppTestKeys.exploreRewards), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreCity), findsOneWidget);

      // Section 4: Support & Insights
      expect(find.byKey(AppTestKeys.exploreSupport), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreInsights), findsOneWidget);

      // Section 5: Family disabled by default
      expect(find.byKey(AppTestKeys.exploreFamily), findsNothing);

      // Section 6: Coming soon (Licensed Partner Required)
      expect(find.byKey(AppTestKeys.exploreInvestments), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreInsurance), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreLoans), findsOneWidget);
    });

    testWidgets('2. Family section appears only when family feature flag is enabled', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildExploreApp(flags: {
        FeatureFlagKeys.family: true,
      }));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.exploreFamily), findsOneWidget);
    });

    testWidgets('3. Search filters tiles dynamically and clears filter', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildExploreApp());
      await tester.pumpAndSettle();

      // Type "Bills" into search
      await tester.enterText(find.byKey(AppTestKeys.exploreSearch), 'Bills');
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.exploreBills), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreCity), findsNothing);
      expect(find.byKey(AppTestKeys.exploreRewards), findsNothing);

      // Clear search
      await tester.tap(find.byIcon(Icons.clear_rounded));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.exploreAccounts), findsOneWidget);
      expect(find.byKey(AppTestKeys.exploreRewards), findsOneWidget);
    });

    testWidgets('4. Coming soon tile taps open explanation sheet, never a fake working flow', (tester) async {
      configureLargeViewport(tester);
      await tester.pumpWidget(buildExploreApp());
      await tester.pumpAndSettle();

      // Tap Investments
      await tester.tap(find.byKey(AppTestKeys.exploreInvestments));
      await tester.pumpAndSettle();

      // Explanation sheet appears
      expect(find.byKey(AppTestKeys.exploreComingSoonSheet), findsOneWidget);
      expect(find.text('Licensed Partner Required'), findsOneWidget);
      expect(find.textContaining('This university prototype will never launch a fake'), findsOneWidget);

      // Dismiss sheet
      await tester.tap(find.text('Understood'));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.exploreComingSoonSheet), findsNothing);
    });
  });

  group('Savings Goals Screens & Flow Tests', () {
    testWidgets('5. Goals Screen renders SIMULATED banner, progress rings, and list items', (tester) async {
      await tester.pumpWidget(buildGoalsApp());
      await tester.pumpAndSettle();

      expect(find.text('Savings Goals'), findsOneWidget);
      expect(find.text('SIMULATED'), findsWidgets);
      expect(find.text('Internal Savings Ledger'), findsOneWidget);

      // Check default goals rendered with keys
      expect(find.byKey(AppTestKeys.goal('goal_1')), findsOneWidget);
      expect(find.byKey(AppTestKeys.goal('goal_2')), findsOneWidget);
      expect(find.byKey(AppTestKeys.goal('goal_3')), findsOneWidget);

      expect(find.text('Emergency Reserve'), findsOneWidget);
      expect(find.text('60%'), findsOneWidget);
      expect(find.text('80%'), findsOneWidget);
    });

    testWidgets('6. Create new savings goal via dialog', (tester) async {
      await tester.pumpWidget(buildGoalsApp());
      await tester.pumpAndSettle();

      // Tap create goal button
      await tester.tap(find.byKey(AppTestKeys.goalCreateBtn));
      await tester.pumpAndSettle();

      // Fill in goal name & target
      await tester.enterText(find.byKey(AppTestKeys.goalNameInput), 'New Drone');
      await tester.enterText(find.byKey(AppTestKeys.goalTargetInput), '80000');
      await tester.pumpAndSettle();

      // Tap create submit
      await tester.tap(find.byKey(AppTestKeys.goalCreateSubmit));
      await tester.pumpAndSettle();

      // New goal should now be in the list
      expect(find.text('New Drone'), findsOneWidget);
    });

    testWidgets('7. Goal Details: Contribute and Withdraw with step-up PIN', (tester) async {
      await tester.pumpWidget(buildGoalDetailsApp('goal_1'));
      await tester.pumpAndSettle();

      expect(find.text('Emergency Reserve'), findsOneWidget);
      expect(find.text('60%'), findsOneWidget);
      expect(find.byKey(AppTestKeys.goalContribute), findsOneWidget);
      expect(find.byKey(AppTestKeys.goalWithdraw), findsOneWidget);

      // --- Contribute flow ---
      await tester.tap(find.byKey(AppTestKeys.goalContribute));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(AppTestKeys.goalAmountInput), '10000');
      await tester.enterText(find.byKey(AppTestKeys.goalPinInput), '123456');
      await tester.tap(find.byKey(AppTestKeys.goalContributeConfirm));
      await tester.pumpAndSettle();

      // Balance increased (was 120,000 -> now 130,000)
      expect(find.text('LKR 130000.00'), findsOneWidget);

      // --- Withdraw flow ---
      await tester.tap(find.byKey(AppTestKeys.goalWithdraw));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(AppTestKeys.goalAmountInput), '5000');
      await tester.enterText(find.byKey(AppTestKeys.goalPinInput), '123456');
      await tester.tap(find.byKey(AppTestKeys.goalWithdrawConfirm));
      await tester.pumpAndSettle();

      // Balance decreased (130,000 - 5,000 -> 125,000)
      expect(find.text('LKR 125000.00'), findsOneWidget);
    });
  });
}
