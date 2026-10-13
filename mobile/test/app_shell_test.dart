import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/localization/locale_controller.dart';
import 'package:lankapay_id/core/routing/app_router.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/theme/theme_controller.dart';
import 'package:lankapay_id/core/widgets/app_bottom_nav.dart';
import 'package:lankapay_id/core/widgets/simulated_badge.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/accounts/presentation/accounts_screen.dart';
import 'package:lankapay_id/features/chat/presentation/chat_screen.dart';
import 'package:lankapay_id/features/city/presentation/city_screen.dart';
import 'package:lankapay_id/features/explore/presentation/explore_screen.dart';
import 'package:lankapay_id/features/home/presentation/home_screen.dart';
import 'package:lankapay_id/features/more/presentation/more_screen.dart';
import 'package:lankapay_id/features/pay/presentation/pay_hub_screen.dart';
import 'package:lankapay_id/features/send/presentation/send_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

// Fake Bank Account Service for predictable shell tab rendering
class FakeBankAccountService implements BankAccountService {
  @override
  Future<List<BankAccountModel>> listAccounts() async {
    return [
      const BankAccountModel(
        id: 1,
        institutionCode: 'BOC',
        accountName: 'Kamal Perera',
        maskedNumber: '******7890',
        label: 'Salary Account',
        isDefault: true,
        discoverable: true,
        receivingEnabled: true,
        status: 'ACTIVE',
      ),
    ];
  }

  @override
  Future<List<BankInstitutionModel>> listInstitutions() async => [];

  @override
  Future<BankAccountModel> linkAccount({
    required String institutionCode,
    required String accountNumber,
    required String accountName,
    String? label,
    bool consentGiven = true,
  }) async => throw UnimplementedError();

  @override
  Future<BankAccountModel> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async => throw UnimplementedError();

  @override
  Future<BankAccountModel> setDefault(int id) async {
    return const BankAccountModel(
      id: 1,
      institutionCode: 'BOC',
      accountName: 'Kamal Perera',
      maskedNumber: '******7890',
      label: 'Salary Account',
      isDefault: true,
      discoverable: true,
      receivingEnabled: true,
      status: 'ACTIVE',
    );
  }

  @override
  Future<void> removeAccount(int id, {bool consentGiven = true}) async {}
}

Widget createTestShellApp({
  String initialLocation = '/home',
  List overrides = const [],
}) {
  return ProviderScope(
    overrides: [
      initialLocationProvider.overrideWithValue(initialLocation),
      bankAccountServiceProvider.overrideWithValue(FakeBankAccountService()),
      ...overrides.cast(),
    ],
    child: Consumer(
      builder: (context, ref, _) {
        final router = ref.watch(routerProvider);
        final themeMode = ref.watch(themeControllerProvider);
        final locale = ref.watch(localeProvider);

        return MaterialApp.router(
          theme: AppTheme.lightTheme,
          darkTheme: AppTheme.darkTheme,
          themeMode: themeMode,
          locale: locale,
          routerConfig: router,
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
        );
      },
    ),
  );
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('AppShell & StatefulShellRoute Tab Navigation', () {
    testWidgets('boots at /home tab and renders AppBottomNav with 5 items', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestShellApp(initialLocation: '/home'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      // Verify shell and bottom nav are rendered
      expect(find.byType(AppBottomNav), findsOneWidget);
      expect(find.byKey(const Key('nav_home')), findsOneWidget);
      expect(find.byKey(const Key('nav_pay')), findsOneWidget);
      expect(find.byKey(const Key('nav_city')), findsOneWidget);
      expect(find.byKey(const Key('nav_chat')), findsOneWidget);
      expect(find.byKey(const Key('nav_explore')), findsOneWidget);

      // Verify HomeScreen is rendered on tab 0
      expect(find.byType(HomeScreen), findsOneWidget);
    });

    testWidgets('tapping each nav item switches tabs and preserves state', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestShellApp(initialLocation: '/home'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      // 1. Switch to Tab 1: Pay
      await tester.tap(find.byKey(const Key('nav_pay')));
      await tester.pumpAndSettle();
      expect(find.byType(PayHubScreen), findsOneWidget);

      // 2. Switch to Tab 2: City
      await tester.tap(find.byKey(const Key('nav_city')));
      await tester.pumpAndSettle();
      expect(find.byType(CityScreen), findsOneWidget);

      // 3. Switch to Tab 3: Chat
      await tester.tap(find.byKey(const Key('nav_chat')));
      await tester.pumpAndSettle();
      expect(find.byType(ChatScreen), findsOneWidget);

      // 4. Switch to Tab 4: Explore
      await tester.tap(find.byKey(const Key('nav_explore')));
      await tester.pumpAndSettle();
      expect(find.byType(ExploreScreen), findsOneWidget);

      // 5. Switch back to Tab 0: Home
      await tester.tap(find.byKey(const Key('nav_home')));
      await tester.pumpAndSettle();
      expect(find.byType(HomeScreen), findsOneWidget);
    });
  });

  group('Header Slim SimulatedBadge Tests', () {
    testWidgets('Home, Accounts, and Send routes render slim SimulatedBadge in header', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestShellApp(initialLocation: '/home'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.byType(SimulatedBadge), findsWidgets);

      await tester.pumpWidget(createTestShellApp(initialLocation: '/accounts'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.byType(SimulatedBadge), findsWidgets);

      await tester.pumpWidget(createTestShellApp(initialLocation: '/send'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.byType(SimulatedBadge), findsWidgets);
    });
  });

  group('MoreScreen Feature & Theme Persistence Tests', () {
    testWidgets('renders all 10 menu tiles and appearance options', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestShellApp(initialLocation: '/more'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      // Check all 10 required keys
      expect(find.byKey(const Key('more_qr')), findsOneWidget);
      expect(find.byKey(const Key('more_requests')), findsOneWidget);
      expect(find.byKey(const Key('more_history')), findsOneWidget);
      expect(find.byKey(const Key('more_notifications')), findsOneWidget);
      expect(find.byKey(const Key('more_privacy')), findsOneWidget);
      expect(find.byKey(const Key('more_security')), findsOneWidget);
      expect(find.byKey(const Key('more_profile')), findsOneWidget);
      expect(find.byKey(const Key('more_language')), findsOneWidget);
      expect(find.byKey(const Key('more_help')), findsOneWidget);
      expect(find.byKey(const Key('more_logout')), findsOneWidget);

      // Check appearance controls
      expect(find.byKey(const Key('theme_toggle')), findsOneWidget);
      expect(find.byKey(const Key('theme_option_system')), findsOneWidget);
      expect(find.byKey(const Key('theme_option_light')), findsOneWidget);
      expect(find.byKey(const Key('theme_option_dark')), findsOneWidget);
    });

    testWidgets('theme option buttons switch ThemeMode and persist in SharedPreferences', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      SharedPreferences.setMockInitialValues({});
      await tester.pumpWidget(createTestShellApp(initialLocation: '/more'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      // Tap dark mode option
      await tester.tap(find.byKey(const Key('theme_option_dark')));
      await tester.pumpAndSettle();

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString(ThemeController.prefKey), 'dark');

      // Tap light mode option
      await tester.tap(find.byKey(const Key('theme_option_light')));
      await tester.pumpAndSettle();
      expect(prefs.getString(ThemeController.prefKey), 'light');

      // Tap system mode option
      await tester.tap(find.byKey(const Key('theme_option_system')));
      await tester.pumpAndSettle();
      expect(prefs.getString(ThemeController.prefKey), 'system');

      // Tap theme toggle pill
      await tester.tap(find.byKey(const Key('theme_toggle')));
      await tester.pumpAndSettle();
      expect(prefs.getString(ThemeController.prefKey), isNotNull);
    });

    testWidgets('Profile, Language, and Help tiles open functional modals without dead taps', (tester) async {
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestShellApp(initialLocation: '/more'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      // 1. Profile modal
      await tester.tap(find.byKey(const Key('more_profile')));
      await tester.pumpAndSettle();
      expect(find.text('Profile Details'), findsOneWidget);
      expect(find.byKey(const Key('profile_close_btn')), findsOneWidget);
      await tester.tap(find.byKey(const Key('profile_close_btn')));
      await tester.pumpAndSettle();

      // 2. Language modal
      await tester.tap(find.byKey(const Key('more_language')));
      await tester.pumpAndSettle();
      expect(find.text('Select Language / භාෂාව / மொழி'), findsOneWidget);
      expect(find.text('සිංහල (Sinhala)'), findsOneWidget);
      await tester.tap(find.text('English (Default)'));
      await tester.pumpAndSettle();

      // 3. Help modal
      await tester.ensureVisible(find.byKey(const Key('more_help')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('more_help')));
      await tester.pumpAndSettle();
      expect(find.text('Help & Support'), findsWidgets);
      expect(find.textContaining('LankaPay CEFT Support Hotline'), findsOneWidget);
      expect(find.byKey(const Key('help_close_btn')), findsOneWidget);
      await tester.tap(find.byKey(const Key('help_close_btn')));
      await tester.pumpAndSettle();
    });
  });
}
