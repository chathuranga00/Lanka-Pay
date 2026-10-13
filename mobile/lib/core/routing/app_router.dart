import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/splash_screen.dart';
import '../../features/auth/presentation/welcome_screen.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/register_screen.dart';
import '../../features/auth/presentation/otp_verification_screen.dart';
import '../../features/auth/presentation/consent_screen.dart';
import '../../features/auth/presentation/recovery_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/pay/presentation/pay_hub_screen.dart';
import '../../features/city/presentation/city_screen.dart';
import '../../features/chat/presentation/chat_screen.dart';
import '../../features/explore/presentation/explore_screen.dart';
import '../../features/accounts/presentation/accounts_screen.dart';
import '../../features/accounts/presentation/add_bank_account_screen.dart';
import '../../features/send/presentation/send_screen.dart';
import '../../features/send/presentation/transfer_result_screen.dart';
import '../../features/send/domain/transfer_models.dart';
import '../../features/qr/presentation/qr_screen.dart';
import '../../features/history/presentation/history_screen.dart';
import '../../features/notifications/presentation/notifications_screen.dart';
import '../../features/settings/presentation/settings_screen.dart';
import '../../features/settings/presentation/privacy_settings_screen.dart';
import '../../features/settings/presentation/security_settings_screen.dart';
import '../../features/more/presentation/more_screen.dart';
import '../../features/cards/presentation/add_card/add_card_flow_screen.dart';
import '../../features/cards/presentation/widgets/test_cards_list_screen.dart';
import '../../features/search/presentation/search_screen.dart';
import '../../features/bills/domain/bill_models.dart';
import '../../features/bills/presentation/bills_screen.dart';
import '../../features/bills/presentation/bill_details_screen.dart';
import '../../features/bills/presentation/bill_result_screen.dart';
import '../../features/bills/presentation/bills_history_screen.dart';
import '../../features/scheduled/domain/scheduled_models.dart';
import '../../features/scheduled/presentation/scheduled_list_screen.dart';
import '../../features/scheduled/presentation/schedule_create_screen.dart';
import '../../features/scheduled/presentation/schedule_details_screen.dart';
import '../widgets/dev_design_system_screen.dart';
import 'app_shell.dart';

final rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');

final initialLocationProvider = Provider<String>((ref) => '/splash');

final routerProvider = Provider<GoRouter>((ref) {
  final initialLocation = ref.watch(initialLocationProvider);
  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: initialLocation,
    routes: [
      // Authentication and onboarding flows (Full-screen, over the shell)
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/welcome',
        builder: (context, state) => const WelcomeScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/consent',
        builder: (context, state) => const ConsentScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/register',
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/verify-otp',
        builder: (context, state) {
          final extra = state.extra as Map<String, dynamic>? ?? {};
          final userId = int.tryParse(extra['userId']?.toString() ?? '0') ?? 0;
          final phone = extra['phone']?.toString() ?? '';
          return OtpVerificationScreen(
            userId: userId,
            phone: phone,
          );
        },
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/recover',
        builder: (context, state) => const RecoveryScreen(),
      ),

      // Super App Shell with 5 tabs using StatefulShellRoute.indexedStack
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return AppShell(navigationShell: navigationShell);
        },
        branches: [
          // Tab 0: Home
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/home',
                builder: (context, state) => const HomeScreen(),
              ),
            ],
          ),
          // Tab 1: Pay
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/pay',
                builder: (context, state) => const PayHubScreen(),
              ),
            ],
          ),
          // Tab 2: City
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/city',
                builder: (context, state) => const CityScreen(),
              ),
            ],
          ),
          // Tab 3: Chat
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/chat',
                builder: (context, state) => const ChatScreen(),
              ),
            ],
          ),
          // Tab 4: Explore
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/explore',
                builder: (context, state) => const ExploreScreen(),
              ),
            ],
          ),
        ],
      ),

      // Legacy & Direct Navigation Routes (all keep working on top of the shell)
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/accounts',
        builder: (context, state) => const AccountsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/send',
        builder: (context, state) => SendScreen(resolvedQr: state.extra),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/more',
        builder: (context, state) => const MoreScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/search',
        builder: (context, state) => const SearchScreen(),
      ),

      // Full-screen flows (hide bottom tab bar, pushed on root navigator)
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/accounts/add',
        builder: (context, state) => const AddBankAccountScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/cards/add',
        builder: (context, state) => const AddCardFlowScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/cards/test-cards',
        builder: (context, state) => const TestCardsListScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/send/result',
        builder: (context, state) {
          final transfer = state.extra as TransferModel;
          return TransferResultScreen(initialTransfer: transfer);
        },
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/qr',
        builder: (context, state) => const QrScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/history',
        builder: (context, state) => const HistoryScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/notifications',
        builder: (context, state) => const NotificationsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/settings',
        builder: (context, state) => const SettingsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/settings/privacy',
        builder: (context, state) => const PrivacySettingsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/settings/security',
        builder: (context, state) => const SecuritySettingsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/rewards',
        builder: (context, state) => Scaffold(
          appBar: AppBar(title: const Text('Rewards')),
          body: const Center(child: Text('Rewards & Offers')),
        ),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/bills',
        builder: (context, state) => const BillsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/bills/details',
        builder: (context, state) {
          final extra = state.extra as Map<String, dynamic>? ?? {};
          return BillDetailsScreen(
            biller: extra['biller'] as BillerModel?,
            savedBiller: extra['savedBiller'] as SavedBillerModel?,
          );
        },
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/bills/result',
        builder: (context, state) {
          final extra = state.extra as Map<String, dynamic>? ?? {};
          final receipt = extra['receipt'] as BillPaymentReceiptModel;
          return BillResultScreen(receipt: receipt);
        },
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/bills/history',
        builder: (context, state) => const BillsHistoryScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/scheduled',
        builder: (context, state) => const ScheduledListScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/scheduled/create',
        builder: (context, state) => const ScheduleCreateScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/scheduled/details',
        builder: (context, state) {
          final schedule = state.extra as ScheduledPaymentModel;
          return ScheduleDetailsScreen(schedule: schedule);
        },
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/goals',
        builder: (context, state) => Scaffold(
          appBar: AppBar(title: const Text('Savings Goals')),
          body: const Center(child: Text('Savings Goals')),
        ),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/dev/design-system',
        builder: (context, state) => const DevDesignSystemScreen(),
      ),
    ],
  );
});
