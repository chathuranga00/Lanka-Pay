import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'all_keys_tap_test.dart';

void main() {
  setUpAll(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('Main Screens Visual Regression Golden Tests (Light & Dark)', () {
    testWidgets('Home Screen - Light Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/home',
        fixedThemeMode: ThemeMode.light,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/home_screen_light.png'),
      );
    });

    testWidgets('Home Screen - Dark Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/home',
        fixedThemeMode: ThemeMode.dark,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/home_screen_dark.png'),
      );
    });

    testWidgets('Pay Hub Screen - Light Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/pay-hub',
        fixedThemeMode: ThemeMode.light,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/pay_hub_screen_light.png'),
      );
    });

    testWidgets('Pay Hub Screen - Dark Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/pay-hub',
        fixedThemeMode: ThemeMode.dark,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/pay_hub_screen_dark.png'),
      );
    });

    testWidgets('City Screen - Light Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/city',
        fixedThemeMode: ThemeMode.light,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/city_screen_light.png'),
      );
    });

    testWidgets('City Screen - Dark Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/city',
        fixedThemeMode: ThemeMode.dark,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/city_screen_dark.png'),
      );
    });

    testWidgets('Chat Screen - Light Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/support',
        fixedThemeMode: ThemeMode.light,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/chat_screen_light.png'),
      );
    });

    testWidgets('Chat Screen - Dark Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/support',
        fixedThemeMode: ThemeMode.dark,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/chat_screen_dark.png'),
      );
    });

    testWidgets('Explore Screen - Light Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/explore',
        fixedThemeMode: ThemeMode.light,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/explore_screen_light.png'),
      );
    });

    testWidgets('Explore Screen - Dark Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/explore',
        fixedThemeMode: ThemeMode.dark,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/explore_screen_dark.png'),
      );
    });

    testWidgets('Accounts Screen - Light Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/accounts',
        fixedThemeMode: ThemeMode.light,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/accounts_screen_light.png'),
      );
    });

    testWidgets('Accounts Screen - Dark Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/accounts',
        fixedThemeMode: ThemeMode.dark,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/accounts_screen_dark.png'),
      );
    });

    testWidgets('Send Screen - Light Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/send',
        fixedThemeMode: ThemeMode.light,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/send_screen_light.png'),
      );
    });

    testWidgets('Send Screen - Dark Mode Golden', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/send',
        fixedThemeMode: ThemeMode.dark,
      ));
      await tester.pumpAndSettle();

      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/send_screen_dark.png'),
      );
    });
  });
}
