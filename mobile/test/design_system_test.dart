import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/theme/theme_controller.dart';
import 'package:lankapay_id/core/widgets/widgets.dart';
import 'package:lankapay_id/core/widgets/dev_design_system_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

Widget createTestableWidget({
  required Widget child,
  ThemeData? theme,
  List overrides = const [],
}) {
  return ProviderScope(
    overrides: overrides.cast(),
    child: MaterialApp(
      theme: theme ?? AppTheme.lightTheme,
      home: Scaffold(body: child),
    ),
  );
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('ThemeController Unit & Persistence Tests', () {
    test('defaults to ThemeMode.system', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);

      final mode = container.read(themeControllerProvider);
      expect(mode, ThemeMode.system);
    });

    test('persists and updates ThemeMode to dark and light in SharedPreferences', () async {
      SharedPreferences.setMockInitialValues({});
      final container = ProviderContainer();
      addTearDown(container.dispose);

      final notifier = container.read(themeControllerProvider.notifier);
      await notifier.setThemeMode(ThemeMode.dark);
      expect(container.read(themeControllerProvider), ThemeMode.dark);

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString(ThemeController.prefKey), 'dark');

      await notifier.setThemeMode(ThemeMode.light);
      expect(container.read(themeControllerProvider), ThemeMode.light);
      expect(prefs.getString(ThemeController.prefKey), 'light');
    });

    test('toggleTheme alternates between light and dark', () async {
      SharedPreferences.setMockInitialValues({});
      final container = ProviderContainer();
      addTearDown(container.dispose);

      final notifier = container.read(themeControllerProvider.notifier);
      // From system (non-dark), toggle goes to dark
      await notifier.toggleTheme();
      expect(container.read(themeControllerProvider), ThemeMode.dark);

      // From dark, toggle goes to light
      await notifier.toggleTheme();
      expect(container.read(themeControllerProvider), ThemeMode.light);
    });
  });

  group('Design System Widgets in Light Theme', () {
    testWidgets('SimulatedBadge renders text chip', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const SimulatedBadge(key: Key('simulated_badge'), text: 'SIMULATED'),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(SimulatedBadge), findsOneWidget);
      expect(find.text('SIMULATED'), findsOneWidget);
    });

    testWidgets('ThemeTogglePill renders and switches theme on tap', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const Center(child: ThemeTogglePill()),
        ),
      );
      await tester.pumpAndSettle();

      final toggleFinder = find.byKey(const Key('theme_toggle'));
      expect(toggleFinder, findsOneWidget);
      expect(find.byIcon(Icons.wb_sunny_rounded), findsOneWidget);
      expect(find.byIcon(Icons.nightlight_round), findsOneWidget);

      await tester.tap(toggleFinder);
      await tester.pumpAndSettle();
    });

    testWidgets('AppAvatar renders deterministic initials and pastel background', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const AppAvatar(key: Key('app_avatar'), name: 'James Taylor'),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('JT'), findsOneWidget);
      expect(find.byType(AppAvatar), findsOneWidget);
    });

    testWidgets('AccountCard renders details and toggles eye balance visibility', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const AccountCard(
            bankName: 'Bank of Ceylon',
            nickname: 'Salary Account',
            maskedNumber: '** 6917',
            balance: 'LKR 84,230.00',
            isDefault: true,
            style: AccountCardStyle.charcoal,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Bank of Ceylon'), findsOneWidget);
      expect(find.text('Salary Account'), findsOneWidget);
      expect(find.text('** 6917'), findsOneWidget);
      expect(find.text('LKR 84,230.00'), findsOneWidget);
      expect(find.text('DEFAULT'), findsOneWidget);
      expect(find.byType(SimulatedBadge), findsOneWidget);

      // Tap eye icon to mask balance
      await tester.tap(find.byIcon(Icons.visibility_outlined));
      await tester.pumpAndSettle();

      expect(find.text('••••••••'), findsOneWidget);
      expect(find.byIcon(Icons.visibility_off_outlined), findsOneWidget);
    });

    testWidgets('AccountCard renders in lavender style', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const AccountCard(
            bankName: 'Commercial Bank',
            nickname: 'Primary Savings',
            maskedNumber: '** 4433',
            balance: 'LKR 41,220.00',
            isDefault: false,
            style: AccountCardStyle.lavender,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Commercial Bank'), findsOneWidget);
      expect(find.text('Primary Savings'), findsOneWidget);
      expect(find.text('LKR 41,220.00'), findsOneWidget);
      expect(find.text('DEFAULT'), findsNothing);
    });

    testWidgets('QuickActionTile renders icon and label', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const QuickActionTile(
            icon: Icons.star_rounded,
            label: 'My bonuses',
            gradient: QuickActionGradient.violet,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('My bonuses'), findsOneWidget);
      expect(find.byIcon(Icons.star_rounded), findsOneWidget);
    });

    testWidgets('ActionButton renders square button and label', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const ActionButton(
            icon: Icons.add_rounded,
            label: 'Add money',
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Add money'), findsOneWidget);
      expect(find.byIcon(Icons.add_rounded), findsOneWidget);
    });

    testWidgets('MenuTile renders navy square icon and label', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const MenuTile(
            icon: Icons.near_me_rounded,
            label: 'Popular payments',
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Popular payments'), findsOneWidget);
      expect(find.byIcon(Icons.near_me_rounded), findsOneWidget);
    });

    testWidgets('TransactionTile renders positive and negative amounts correctly', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: Column(
            children: [
              TransactionTile(
                icon: Icons.shopping_cart_rounded,
                title: 'Supermarket',
                subtitle: 'Today, 16:40',
                amount: '- LKR 40.00',
                status: 'payment',
                isPositive: false,
              ),
              TransactionTile(
                icon: Icons.account_balance_rounded,
                title: 'Kapital Bank',
                subtitle: 'Yesterday',
                amount: '+ LKR 20.00',
                status: 'transfer',
                isPositive: true,
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Supermarket'), findsOneWidget);
      expect(find.text('- LKR 40.00'), findsOneWidget);
      expect(find.text('Kapital Bank'), findsOneWidget);
      expect(find.text('+ LKR 20.00'), findsOneWidget);
    });

    testWidgets('SectionHeader renders title and triggers optional action', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: SectionHeader(
            title: 'CARDS',
            actionLabel: 'See all >',
            onActionTap: () => tapped = true,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('CARDS'), findsOneWidget);
      expect(find.text('See all >'), findsOneWidget);

      await tester.tap(find.text('See all >'));
      expect(tapped, isTrue);
    });

    testWidgets('AppBottomNav renders 4 items and fires onIndexChanged', (tester) async {
      int selected = 0;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: AppBottomNav(
            currentIndex: selected,
            onIndexChanged: (idx) => selected = idx,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Home'), findsOneWidget);
      expect(find.text('Accounts'), findsOneWidget);
      expect(find.text('Send'), findsOneWidget);
      expect(find.text('More'), findsOneWidget);

      await tester.tap(find.byKey(const Key('nav_send')));
      expect(selected, 2);
    });

    testWidgets('EmptyState and ErrorRetry widgets render cleanly', (tester) async {
      bool retried = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: Column(
            children: [
              const EmptyState(
                icon: Icons.inbox_rounded,
                title: 'No Data',
                description: 'Nothing here yet.',
              ),
              ErrorRetry(
                message: 'Failed to connect.',
                onRetry: () => retried = true,
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No Data'), findsOneWidget);
      expect(find.text('Nothing here yet.'), findsOneWidget);
      expect(find.text('Failed to connect.'), findsOneWidget);

      await tester.tap(find.byKey(const Key('error_retry_btn')));
      expect(retried, isTrue);
    });

    testWidgets('Shimmer skeletons render without exceptions', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: const Column(
            children: [
              SkeletonAccountCard(),
              SkeletonTransactionTile(),
              SkeletonQuickActionTile(),
            ],
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.byKey(const Key('skeleton_account_card')), findsOneWidget);
      expect(find.byKey(const Key('skeleton_transaction_tile')), findsOneWidget);
      expect(find.byKey(const Key('skeleton_quick_action_tile')), findsOneWidget);
    });
  });

  group('Design System Widgets in Dark Theme', () {
    testWidgets('GradientHeaderScaffold and cards render in dark mode', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.darkTheme,
          child: const GradientHeaderScaffold(
            key: Key('gradient_header_scaffold'),
            header: Text('Header Area', style: TextStyle(color: Colors.white)),
            body: Text('Sheet Area'),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Header Area'), findsOneWidget);
      expect(find.text('Sheet Area'), findsOneWidget);
      expect(find.byType(GradientHeaderScaffold), findsOneWidget);
    });

    testWidgets('DevDesignSystemScreen renders all gallery items in dark theme', (tester) async {
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.darkTheme,
          child: const DevDesignSystemScreen(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('dev_design_system_screen')), findsOneWidget);
      expect(find.text('Design System Gallery'), findsOneWidget);
      expect(find.text('LKR 125,450.00'), findsOneWidget);
      expect(find.text('Bank of Ceylon'), findsOneWidget);
      expect(find.text('Commercial Bank'), findsOneWidget);
      expect(find.text('Supermarket'), findsOneWidget);
      expect(find.text('Kapital Bank'), findsOneWidget);
    });
  });
}
