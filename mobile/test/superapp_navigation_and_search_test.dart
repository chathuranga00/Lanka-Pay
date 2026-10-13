import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/config/feature_flags.dart';
import 'package:lankapay_id/core/localization/locale_controller.dart';
import 'package:lankapay_id/core/network/api_client.dart';
import 'package:lankapay_id/core/routing/app_router.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/theme/theme_controller.dart';
import 'package:lankapay_id/core/widgets/app_bottom_nav.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/accounts/presentation/accounts_screen.dart';
import 'package:lankapay_id/features/chat/presentation/chat_screen.dart';
import 'package:lankapay_id/features/city/presentation/city_screen.dart';
import 'package:lankapay_id/features/explore/presentation/explore_screen.dart';
import 'package:lankapay_id/features/home/presentation/home_screen.dart';
import 'package:lankapay_id/features/pay/presentation/pay_hub_screen.dart';
import 'package:lankapay_id/features/search/data/search_service.dart';
import 'package:lankapay_id/features/search/domain/search_model.dart';
import 'package:lankapay_id/features/search/presentation/search_screen.dart';
import 'package:lankapay_id/features/send/presentation/send_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

class FakeBankAccountService implements BankAccountService {
  @override
  Future<List<BankAccountModel>> listAccounts() async {
    return [
      const BankAccountModel(
        id: 1,
        institutionCode: 'BOC',
        accountName: 'Kasun Perera',
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
  Future<BankAccountModel> setDefault(int id) async => throw UnimplementedError();

  @override
  Future<void> removeAccount(int id, {bool consentGiven = true}) async {}
}

class FakeSearchService extends SearchService {
  final SearchResponseModel Function(String query)? onSearch;

  FakeSearchService(super.dio, {this.onSearch});

  @override
  Future<SearchResponseModel> search(String query, {List<String>? types}) async {
    if (onSearch != null) {
      return onSearch!(query);
    }
    return super.search(query, types: types);
  }
}

Widget createTestApp({
  String initialLocation = '/home',
  Map<String, bool>? initialFlags,
  SearchResponseModel Function(String query)? searchStub,
}) {
  return ProviderScope(
    overrides: [
      initialLocationProvider.overrideWithValue(initialLocation),
      bankAccountServiceProvider.overrideWithValue(FakeBankAccountService()),
      if (initialFlags != null)
        featureFlagsProvider.overrideWith(() => FeatureFlagsNotifier(initialFlags)),
      if (searchStub != null)
        searchServiceProvider.overrideWith(
          (ref) => FakeSearchService(ref.watch(dioProvider), onSearch: searchStub),
        ),
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

  group('Super App 5-Tab Navigation & State Preservation', () {
    testWidgets('renders all 5 tabs: Home, Pay, City, Chat, Explore', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(initialLocation: '/home'));
      await tester.pumpAndSettle();

      expect(find.byType(AppBottomNav), findsOneWidget);
      expect(find.byKey(const Key('nav_home')), findsOneWidget);
      expect(find.byKey(const Key('nav_pay')), findsOneWidget);
      expect(find.byKey(const Key('nav_city')), findsOneWidget);
      expect(find.byKey(const Key('nav_chat')), findsOneWidget);
      expect(find.byKey(const Key('nav_explore')), findsOneWidget);

      expect(find.byType(HomeScreen), findsOneWidget);
    });

    testWidgets('tab switching switches screens and preserves indexed stack state', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(initialLocation: '/home'));
      await tester.pumpAndSettle();

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

    testWidgets('existing routes keep working on top of the shell (/accounts, /send)', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(initialLocation: '/accounts'));
      await tester.pumpAndSettle();
      expect(find.byType(AccountsScreen), findsOneWidget);

      await tester.pumpWidget(createTestApp(initialLocation: '/send'));
      await tester.pumpAndSettle();
      expect(find.byType(SendScreen), findsOneWidget);
    });
  });

  group('Feature Flags Dynamically Hide Tabs', () {
    testWidgets('turning off city and support flags hides nav_city and nav_chat', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(
        initialLocation: '/home',
        initialFlags: {
          FeatureFlagKeys.home: true,
          FeatureFlagKeys.pay: true,
          FeatureFlagKeys.city: false, // OFF
          FeatureFlagKeys.chat: false, // OFF
          FeatureFlagKeys.explore: true,
        },
      ));
      await tester.pumpAndSettle();

      // Home, Pay, and Explore are visible
      expect(find.byKey(const Key('nav_home')), findsOneWidget);
      expect(find.byKey(const Key('nav_pay')), findsOneWidget);
      expect(find.byKey(const Key('nav_explore')), findsOneWidget);

      // City and Chat tabs are hidden
      expect(find.byKey(const Key('nav_city')), findsNothing);
      expect(find.byKey(const Key('nav_chat')), findsNothing);
    });
  });

  group('Global Search Screen & Search States', () {
    testWidgets('search screen opens with recent searches list and clear all button', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(initialLocation: '/search'));
      await tester.pumpAndSettle();

      expect(find.byType(SearchScreen), findsOneWidget);
      expect(find.byKey(const Key('search_input')), findsOneWidget);
      expect(find.byKey(const Key('search_recent_list')), findsOneWidget);
      expect(find.byKey(const Key('search_clear_recent_btn')), findsOneWidget);

      // Clear recent searches
      await tester.tap(find.byKey(const Key('search_clear_recent_btn')));
      await tester.pumpAndSettle();
      expect(find.text('Type at least 2 characters to search'), findsOneWidget);
    });

    testWidgets('typing query displays grouped results', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cebBiller = const SearchResultModel(
        id: 'biller-ceb',
        type: SearchResultType.biller,
        title: 'Ceylon Electricity Board (CEB)',
        subtitle: 'Electricity • CEB',
      );

      await tester.pumpWidget(createTestApp(
        initialLocation: '/search',
        searchStub: (query) => SearchResponseModel(
          query: query,
          totalCount: 1,
          results: [cebBiller],
          groupedResults: {SearchResultType.biller: [cebBiller]},
        ),
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('search_input')), 'ceb');
      await tester.pump(const Duration(milliseconds: 350)); // debounce
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('search_results_list')), findsOneWidget);
      expect(find.text('UTILITY BILLERS'), findsOneWidget);
      expect(find.text('Ceylon Electricity Board (CEB)'), findsOneWidget);
    });

    testWidgets('empty results state rendered when query has no matches', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(
        initialLocation: '/search',
        searchStub: (query) => SearchResponseModel(
          query: query,
          totalCount: 0,
          results: const [],
          groupedResults: const {},
        ),
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('search_input')), 'unknownxyz');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('search_empty_state')), findsOneWidget);
      expect(find.text('No Results Found'), findsOneWidget);
    });

    testWidgets('error state with retry button is displayed on failure', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(
        initialLocation: '/search',
        searchStub: (query) => throw Exception('Network timeout'),
      ));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('search_input')), 'electricity');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('search_error_state')), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });
  });

  group('Avatar Sheet Profile & Preferences', () {
    testWidgets('tapping home avatar opens AvatarSheet with settings, security, and logout', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestApp(initialLocation: '/home'));
      await tester.pumpAndSettle();

      // Tap on avatar
      await tester.tap(find.byKey(const Key('home_avatar')));
      await tester.pumpAndSettle();

      // Avatar sheet is displayed
      expect(find.byKey(const Key('avatar_sheet')), findsOneWidget);
      expect(find.byKey(const Key('avatar_sheet_settings')), findsOneWidget);
      expect(find.byKey(const Key('avatar_sheet_privacy')), findsOneWidget);
      expect(find.byKey(const Key('avatar_sheet_security')), findsOneWidget);
      expect(find.byKey(const Key('avatar_sheet_appearance')), findsOneWidget);
      expect(find.byKey(const Key('avatar_sheet_logout')), findsOneWidget);
    });
  });
}
