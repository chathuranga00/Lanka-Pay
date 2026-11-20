import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:lankapay_id/core/config/feature_flags.dart';
import 'package:lankapay_id/core/widgets/shimmer_skeletons.dart';
import 'package:lankapay_id/core/widgets/simulated_badge.dart';
import 'package:lankapay_id/features/home/data/home_api_model.dart';
import 'package:lankapay_id/features/home/data/home_providers.dart';
import 'package:lankapay_id/features/home/presentation/home_screen.dart';
import 'package:lankapay_id/features/home/presentation/widgets/avatar_sheet.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

HomeAggregateResponseModel createTestHomeResponse({
  String greetingStatus = 'OK',
  String fullName = 'Mikhail',
  String activityStatus = 'OK',
  bool hasActivity = true,
  String totalSpentLkr = '1,500.00',
  double budgetFraction = 0.3,
  String month = 'October',
  String rewardsStatus = 'OK',
  String points = '1,250 pts',
  bool hasNewOffers = true,
  String walletStatus = 'OK',
  bool hasWallet = true,
  String balanceLkr = '125,450.00',
  String nudgeStatus = 'OK',
  String? nudgeId = 'security_tip',
  String nudgeMessage = 'Never share your PIN, OTP, or password with anyone',
}) {
  return HomeAggregateResponseModel(
    greeting: HomeSectionModel(
      status: greetingStatus,
      data: greetingStatus == 'OK'
          ? GreetingDataModel(fullName: fullName, initial: fullName[0], salutation: 'Hello, $fullName')
          : null,
      error: greetingStatus == 'FAILED' ? 'FAILED' : null,
    ),
    unreadNotifications: const HomeSectionModel(
      status: 'OK',
      data: NotificationDataModel(unreadCount: 2),
    ),
    wallet: HomeSectionModel(
      status: walletStatus,
      data: (walletStatus == 'OK' && hasWallet)
          ? WalletDataModel(
              primaryBalanceMinor: 12545000,
              primaryBalanceLkr: balanceLkr,
              currency: 'LKR',
              isSimulated: true,
              defaultAccount: const LinkedAccountSummaryModel(
                id: 101,
                bankCode: 'BANK_A',
                bankName: 'Bank of Ceylon',
                maskedAccountNumber: '•••• 5678',
                accountName: 'Primary Account',
                isDefault: true,
              ),
              activeCard: const CardSummaryModel(
                cardId: 201,
                maskedNumber: '•••• 0010',
                brandLabel: 'TEST CARD',
                frozen: false,
                expiry: '12/28',
              ),
              totalAccounts: 1,
              totalCards: 1,
            )
          : null,
      error: walletStatus == 'FAILED' ? 'FAILED' : null,
    ),
    recentActivity: const HomeSectionModel(
      status: 'OK',
      data: [],
    ),
    rewardsPoints: HomeSectionModel(
      status: rewardsStatus,
      data: rewardsStatus == 'OK'
          ? RewardsPointsDataModel(
              pointsBalance: 1250,
              formattedPoints: points,
              hasNewOffers: hasNewOffers,
            )
          : null,
      error: rewardsStatus == 'FAILED' ? 'FAILED' : null,
    ),
    stories: const HomeSectionModel(
      status: 'UNAVAILABLE',
      data: null,
      error: 'MODULE_NOT_YET_IMPLEMENTED',
    ),
    spendSummary: HomeSectionModel(
      status: activityStatus,
      data: activityStatus == 'OK'
          ? SpendSummaryDataModel(
              totalSpentMinor: 150000,
              totalSpentLkr: totalSpentLkr,
              budgetMinor: 5000000,
              budgetLkr: '50,000.00',
              budgetUsedFraction: budgetFraction,
              month: month,
              hasTransactions: hasActivity,
            )
          : null,
      error: activityStatus == 'FAILED' ? 'FAILED' : null,
    ),
    nudge: HomeSectionModel(
      status: nudgeStatus,
      data: (nudgeStatus == 'OK' && nudgeId != null)
          ? NudgeDataModel(
              nudgeId: nudgeId,
              type: 'SECURITY_TIP',
              message: nudgeMessage,
              actionLabel: 'Learn more',
              actionRoute: '/profile/security',
            )
          : null,
      error: nudgeStatus == 'FAILED' ? 'FAILED' : null,
    ),
    shortcuts: const HomeSectionModel(
      status: 'OK',
      data: [],
    ),
  );
}

Widget createTestHomeApp({
  dynamic overrides = const [],
  String initialLocation = '/',
  List<RouteBase> extraRoutes = const [],
}) {
  final router = GoRouter(
    initialLocation: initialLocation,
    routes: [
      GoRoute(path: '/', builder: (_, __) => const HomeScreen()),
      GoRoute(path: '/search', builder: (_, __) => const Scaffold(body: Text('Search Screen'))),
      GoRoute(path: '/rewards', builder: (_, __) => const Scaffold(body: Text('Rewards Screen'))),
      GoRoute(path: '/history', builder: (_, __) => const Scaffold(body: Text('History Screen'))),
      GoRoute(path: '/activity', builder: (_, __) => const Scaffold(body: Text('History Screen'))),
      GoRoute(path: '/accounts', builder: (_, __) => const Scaffold(body: Text('Accounts Screen'))),
      GoRoute(path: '/accounts/add', builder: (_, __) => const Scaffold(body: Text('Add Account Screen'))),
      GoRoute(path: '/bills', builder: (_, __) => const Scaffold(body: Text('Bills Screen'))),
      GoRoute(path: '/explore', builder: (_, __) => const Scaffold(body: Text('Explore Screen'))),
      GoRoute(path: '/settings', builder: (_, __) => const Scaffold(body: Text('Settings Screen'))),
      GoRoute(path: '/qr', builder: (_, __) => const Scaffold(body: Text('QR Screen'))),
      ...extraRoutes,
    ],
  );

  return ProviderScope(
    overrides: overrides,
    child: MaterialApp.router(
      routerConfig: router,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('en')],
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('HomeScreen Data State & Visual Elements', () {
    testWidgets('renders all sections and tokens accurately', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse();

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('home_profile')), findsOneWidget);
      expect(find.text('Mikhail'), findsOneWidget);
      expect(find.byKey(const Key('home_rewards')), findsOneWidget);
      expect(find.byKey(const Key('home_rewards_dot')), findsOneWidget);
      expect(find.byKey(const Key('home_search')), findsOneWidget);
      expect(find.byKey(const Key('home_stories')), findsOneWidget);
      expect(find.byKey(const Key('tile_activity')), findsOneWidget);
      expect(find.byKey(const Key('tile_rewards')), findsOneWidget);
      expect(find.byKey(const Key('home_wallet')), findsOneWidget);
      expect(find.byKey(const Key('home_nudge')), findsOneWidget);
      expect(find.byKey(const Key('tile_bills')), findsOneWidget);
      expect(find.byKey(const Key('tile_goals')), findsOneWidget);
    });
  });

  group('HomeScreen Loading States', () {
    testWidgets('renders skeletons while fetching data', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final completer = Completer<HomeAggregateResponseModel>();

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => completer.future),
          ],
        ),
      );
      await tester.pump();

      expect(find.byKey(const Key('home_stories_skeleton')), findsOneWidget);
      expect(find.byKey(const Key('tile_activity_skeleton')), findsOneWidget);
      expect(find.byKey(const Key('tile_rewards_skeleton')), findsOneWidget);
      expect(find.byKey(const Key('home_wallet_loading')), findsOneWidget);
    });
  });

  group('HomeScreen Empty States', () {
    testWidgets('renders EmptyState and empty texts when sections lack data', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse(
        hasActivity: false,
        month: 'April',
        hasWallet: false,
      );

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No spending in April'), findsOneWidget);
      expect(find.byKey(const Key('home_wallet_empty')), findsOneWidget);
    });
  });

  group('HomeScreen Error States and Retry', () {
    testWidgets('renders error views with retry buttons', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse(
        activityStatus: 'FAILED',
        rewardsStatus: 'FAILED',
        walletStatus: 'FAILED',
      );

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('tile_activity_error')), findsOneWidget);
      expect(find.byKey(const Key('tile_rewards_error')), findsOneWidget);
      expect(find.byKey(const Key('home_wallet_error')), findsOneWidget);
    });
  });

  group('HomeScreen Interactive Controls & Taps', () {
    testWidgets('tapping search [home_search] navigates to /search', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse();

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_search')));
      await tester.pumpAndSettle();

      expect(find.text('Search Screen'), findsOneWidget);
    });

    testWidgets('tapping rewards button [home_rewards] navigates to /rewards', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse();

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_rewards')));
      await tester.pumpAndSettle();

      expect(find.text('Rewards Screen'), findsOneWidget);
    });

    testWidgets('tapping activity tile [tile_activity] navigates to /history', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse();

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('tile_activity')));
      await tester.pumpAndSettle();

      expect(find.text('History Screen'), findsOneWidget);
    });

    testWidgets('tapping wallet card [home_wallet] navigates to /accounts', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse();

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_wallet')));
      await tester.pumpAndSettle();

      expect(find.text('Accounts Screen'), findsOneWidget);
    });

    testWidgets('tapping nudge dismiss button dismisses banner', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockResponse = createTestHomeResponse(nudgeId: 'nudge_dismiss_test');

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            homeAggregateProvider.overrideWith((ref) => Future.value(mockResponse)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('home_nudge')), findsOneWidget);

      await tester.tap(find.byKey(const Key('nudge_dismiss')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('home_nudge')), findsNothing);
    });
  });
}
