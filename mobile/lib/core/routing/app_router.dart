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

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/splash',
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/welcome',
        builder: (context, state) => const WelcomeScreen(),
      ),
      GoRoute(
        path: '/consent',
        builder: (context, state) => const ConsentScreen(),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
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
        path: '/recover',
        builder: (context, state) => const RecoveryScreen(),
      ),
      GoRoute(
        path: '/home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/accounts',
        builder: (context, state) => const AccountsScreen(),
      ),
      GoRoute(
        path: '/accounts/add',
        builder: (context, state) => const AddBankAccountScreen(),
      ),
      GoRoute(
        path: '/send',
        builder: (context, state) => const SendScreen(),
      ),
      GoRoute(
        path: '/send/result',
        builder: (context, state) {
          final transfer = state.extra as TransferModel;
          return TransferResultScreen(initialTransfer: transfer);
        },
      ),
      GoRoute(
        path: '/qr',
        builder: (context, state) => const QrScreen(),
      ),
      GoRoute(
        path: '/history',
        builder: (context, state) => const HistoryScreen(),
      ),
      GoRoute(
        path: '/notifications',
        builder: (context, state) => const NotificationsScreen(),
      ),
      GoRoute(
        path: '/settings',
        builder: (context, state) => const SettingsScreen(),
      ),
      GoRoute(
        path: '/settings/privacy',
        builder: (context, state) => const PrivacySettingsScreen(),
      ),
      GoRoute(
        path: '/settings/security',
        builder: (context, state) => const SecuritySettingsScreen(),
      ),
    ],
  );
});
