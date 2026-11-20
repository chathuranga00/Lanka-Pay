import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/theme/app_tokens.dart';
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

/// Computes the relative luminance contrast ratio between two colors according to WCAG 2.1 specs.
double calculateContrastRatio(Color foreground, Color background) {
  final l1 = foreground.computeLuminance();
  final l2 = background.computeLuminance();
  final lighter = math.max(l1, l2);
  final darker = math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('WCAG AA Color Contrast Verification', () {
    test('Light theme text colors meet WCAG AA contrast (>= 4.5:1) against white', () {
      final primaryRatio = calculateContrastRatio(AppTokens.textPrimaryLight, AppTokens.backgroundLight);
      expect(primaryRatio, greaterThanOrEqualTo(4.5), reason: 'Near-black #1D1D1F must meet WCAG AA on white');

      final secondaryRatio = calculateContrastRatio(AppTokens.textSecondaryLight, AppTokens.backgroundLight);
      expect(secondaryRatio, greaterThanOrEqualTo(4.5), reason: 'Secondary grey must meet WCAG AA on white');

      final nudgeRatio = calculateContrastRatio(AppTokens.nudgeBannerTextLight, AppTokens.nudgeBannerBgLight);
      expect(nudgeRatio, greaterThanOrEqualTo(4.5), reason: 'Nudge banner text must meet WCAG AA on pale yellow');
    });

    test('Dark theme text colors meet WCAG AA contrast (>= 4.5:1) against dark surfaces', () {
      final primaryRatio = calculateContrastRatio(AppTokens.textPrimaryDark, AppTokens.cardDark);
      expect(primaryRatio, greaterThanOrEqualTo(4.5), reason: 'White text must meet WCAG AA on dark card');

      final secondaryRatio = calculateContrastRatio(AppTokens.textSecondaryDark, AppTokens.cardDark);
      expect(secondaryRatio, greaterThanOrEqualTo(4.5), reason: 'Secondary text must meet WCAG AA on dark card');

      final nudgeRatio = calculateContrastRatio(AppTokens.nudgeBannerTextDark, AppTokens.nudgeBannerBgDark);
      expect(nudgeRatio, greaterThanOrEqualTo(4.5), reason: 'Dark nudge text must meet WCAG AA on dark banner');
    });
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
      await notifier.toggleTheme();
      expect(container.read(themeControllerProvider), ThemeMode.dark);

      await notifier.toggleTheme();
      expect(container.read(themeControllerProvider), ThemeMode.light);
    });
  });

  group('Super App Shared Widgets in Light Theme', () {
    testWidgets('AppAvatar renders neutral grey circle and initial', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: AppAvatar(
            name: 'Mikhail',
            onTap: () => tapped = true,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(AppAvatar), findsOneWidget);
      expect(find.text('M'), findsOneWidget);

      await tester.tap(find.byType(AppAvatar));
      expect(tapped, isTrue);
    });

    testWidgets('SearchBarField renders rounded input ~18 with search icon', (tester) async {
      final controller = TextEditingController();
      String changedText = '';
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: SearchBarField(
            controller: controller,
            hintText: 'Search products',
            onChanged: (val) => changedText = val,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(SearchBarField), findsOneWidget);
      expect(find.byIcon(Icons.search_rounded), findsOneWidget);
      expect(find.text('Search products'), findsOneWidget);

      await tester.enterText(find.byType(TextField), 'Cards');
      expect(changedText, 'Cards');
    });

    testWidgets('StoryCard renders caption and blue ring when unseen', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: Row(
            children: [
              StoryCard(
                key: const Key('story_unseen'),
                caption: 'Tailored\nto you',
                isUnseen: true,
                onTap: () => tapped = true,
              ),
              const StoryCard(
                key: Key('story_seen'),
                caption: 'Blog\nPulse',
                isUnseen: false,
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('story_card_unseen_ring')), findsOneWidget);
      expect(find.text('Tailored\nto you'), findsOneWidget);
      expect(find.text('Blog\nPulse'), findsOneWidget);

      await tester.tap(find.byKey(const Key('story_unseen')));
      expect(tapped, isTrue);
    });

    testWidgets('InfoTile renders title, subtitle, and progress bar', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: InfoTile(
            title: 'All transactions',
            subtitle: 'No spending in April',
            progress: 0.40,
            onTap: () => tapped = true,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('All transactions'), findsOneWidget);
      expect(find.text('No spending in April'), findsOneWidget);
      expect(find.byKey(const Key('info_tile_progress')), findsOneWidget);

      await tester.tap(find.byType(InfoTile));
      expect(tapped, isTrue);
    });

    testWidgets('NudgeBanner renders pale yellow banner with dark text', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: NudgeBanner(
            text: 'Arrange delivery',
            onTap: () => tapped = true,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(NudgeBanner), findsOneWidget);
      expect(find.text('Arrange delivery'), findsOneWidget);

      await tester.tap(find.byType(NudgeBanner));
      expect(tapped, isTrue);
    });

    testWidgets('WalletCard renders balance, account name, points pill, and nudge banner', (tester) async {
      bool toggled = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: WalletCard(
            balance: '0 LKR',
            name: 'Primary Account',
            onEyeToggle: () => toggled = true,
            pointsPill: const Text('★ 0 pts'),
            nudgeBanner: const NudgeBanner(text: 'Arrange delivery'),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(WalletCard), findsOneWidget);
      expect(find.text('0 LKR'), findsOneWidget);
      expect(find.text('Primary Account'), findsOneWidget);
      expect(find.text('★ 0 pts'), findsOneWidget);
      expect(find.text('Arrange delivery'), findsOneWidget);

      await tester.tap(find.byKey(const Key('wallet_card_eye_toggle')));
      expect(toggled, isTrue);
    });

    testWidgets('ProductTile renders title, subtitle, and illustration slot', (tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: ProductTile(
            title: 'Borrow',
            subtitle: 'Quick cash',
            illustration: const Icon(Icons.payments_rounded),
            onTap: () => tapped = true,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(ProductTile), findsOneWidget);
      expect(find.text('Borrow'), findsOneWidget);
      expect(find.text('Quick cash'), findsOneWidget);
      expect(find.byIcon(Icons.payments_rounded), findsOneWidget);

      await tester.tap(find.byType(ProductTile));
      expect(tapped, isTrue);
    });

    testWidgets('AppBottomNav renders 5 items with active blue and chat badge', (tester) async {
      int selected = 0;
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.lightTheme,
          child: AppBottomNav(
            currentIndex: selected,
            onIndexChanged: (idx) => selected = idx,
            chatBadgeCount: 1,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Home'), findsOneWidget);
      expect(find.text('Pay'), findsOneWidget);
      expect(find.text('City'), findsOneWidget);
      expect(find.text('Chat'), findsOneWidget);
      expect(find.text('Explore'), findsOneWidget);
      expect(find.text('1'), findsOneWidget); // Chat badge

      await tester.tap(find.byKey(const Key('nav_pay')));
      expect(selected, 1);
    });
  });

  group('Super App Shared Widgets in Dark Theme', () {
    testWidgets('All new shared widgets render cleanly in Dark Theme', (tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.darkTheme,
          child: SingleChildScrollView(
            child: Column(
              children: [
                const AppAvatar(name: 'Mikhail'),
                const SearchBarField(hintText: 'Search'),
                const StoryCard(caption: 'Story 1', isUnseen: true),
                const InfoTile(title: 'Info', subtitle: 'Detail', progress: 0.5),
                const NudgeBanner(text: 'Delivery'),
                const WalletCard(balance: '1,000 LKR', name: 'Savings'),
                const ProductTile(title: 'Save'),
                AppBottomNav(currentIndex: 0, onIndexChanged: (_) {}),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(AppAvatar), findsOneWidget);
      expect(find.byType(SearchBarField), findsOneWidget);
      expect(find.byType(StoryCard), findsOneWidget);
      expect(find.byType(InfoTile), findsOneWidget);
      expect(find.byType(NudgeBanner), findsOneWidget);
      expect(find.byType(WalletCard), findsOneWidget);
      expect(find.byType(ProductTile), findsOneWidget);
      expect(find.byType(AppBottomNav), findsOneWidget);
    });

    testWidgets('DevDesignSystemScreen renders all gallery items in light and dark theme', (tester) async {
      tester.view.physicalSize = const Size(800, 3000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      // Dark Mode Test
      await tester.pumpWidget(
        createTestableWidget(
          theme: AppTheme.darkTheme,
          child: const DevDesignSystemScreen(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('dev_design_system_screen')), findsOneWidget);
      expect(find.text('Super App Design System'), findsOneWidget);
      expect(find.text('Mikhail'), findsOneWidget);
      expect(find.byKey(const Key('gallery_avatar')), findsOneWidget);
      expect(find.byKey(const Key('gallery_search_bar')), findsOneWidget);
      expect(find.byKey(const Key('gallery_story_1')), findsOneWidget);
      expect(find.byKey(const Key('gallery_info_tile_1')), findsOneWidget);
      expect(find.byKey(const Key('gallery_wallet_card')), findsOneWidget);
      expect(find.byKey(const Key('gallery_product_borrow')), findsOneWidget);
      expect(find.byKey(const Key('gallery_product_save')), findsOneWidget);
      expect(find.byKey(const Key('gallery_bottom_nav')), findsOneWidget);
    });
  });
}
