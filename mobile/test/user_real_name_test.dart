import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/features/auth/domain/auth_state.dart';
import 'package:lankapay_id/features/auth/data/auth_service.dart';
import 'package:lankapay_id/features/home/data/home_api_model.dart';
import 'package:lankapay_id/features/home/data/home_providers.dart';
import 'package:lankapay_id/features/home/presentation/home_screen.dart';

class MockRealUserAuthNotifier extends AuthNotifier {
  final String _name;
  MockRealUserAuthNotifier(this._name);

  @override
  AuthState build() {
    return AuthAuthenticated(
      accessToken: 'dummy-token',
      userId: '1',
      phone: '+94771234567',
      fullName: _name,
    );
  }
}

void main() {
  testWidgets('HomeScreen displays real user name from auth state when loading', (tester) async {
    final completer = Completer<HomeAggregateResponseModel>();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith(() => MockRealUserAuthNotifier('Chathuranga Fernando')),
          homeAggregateProvider.overrideWith((ref) => completer.future),
        ],
        child: const MaterialApp(
          home: HomeScreen(),
        ),
      ),
    );

    // Initial pump while homeAggregateProvider is still loading
    await tester.pump();

    // Verify real user first name 'Chathuranga' is displayed, NOT 'Mikhail'
    expect(find.text('Chathuranga'), findsOneWidget);
    expect(find.text('Mikhail'), findsNothing);
    // Avatar initials for 'Chathuranga Fernando' is 'CF'
    expect(find.text('CF'), findsOneWidget);

    completer.complete(HomeAggregateResponseModel(
      greeting: const HomeSectionModel(status: 'UNAVAILABLE'),
      unreadNotifications: const HomeSectionModel(status: 'UNAVAILABLE'),
      wallet: const HomeSectionModel(status: 'UNAVAILABLE'),
      recentActivity: const HomeSectionModel(status: 'UNAVAILABLE'),
      rewardsPoints: const HomeSectionModel(status: 'UNAVAILABLE'),
      stories: const HomeSectionModel(status: 'UNAVAILABLE'),
      spendSummary: const HomeSectionModel(status: 'UNAVAILABLE'),
      nudge: const HomeSectionModel(status: 'UNAVAILABLE'),
      shortcuts: const HomeSectionModel(status: 'UNAVAILABLE'),
    ));
    await tester.pump();
  });

  testWidgets('HomeScreen displays single name and initial properly', (tester) async {
    final completer = Completer<HomeAggregateResponseModel>();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith(() => MockRealUserAuthNotifier('Kasun')),
          homeAggregateProvider.overrideWith((ref) => completer.future),
        ],
        child: const MaterialApp(
          home: HomeScreen(),
        ),
      ),
    );

    await tester.pump();

    expect(find.text('Kasun'), findsOneWidget);
    expect(find.text('Mikhail'), findsNothing);
    expect(find.text('K'), findsOneWidget);

    completer.complete(HomeAggregateResponseModel(
      greeting: const HomeSectionModel(status: 'UNAVAILABLE'),
      unreadNotifications: const HomeSectionModel(status: 'UNAVAILABLE'),
      wallet: const HomeSectionModel(status: 'UNAVAILABLE'),
      recentActivity: const HomeSectionModel(status: 'UNAVAILABLE'),
      rewardsPoints: const HomeSectionModel(status: 'UNAVAILABLE'),
      stories: const HomeSectionModel(status: 'UNAVAILABLE'),
      spendSummary: const HomeSectionModel(status: 'UNAVAILABLE'),
      nudge: const HomeSectionModel(status: 'UNAVAILABLE'),
      shortcuts: const HomeSectionModel(status: 'UNAVAILABLE'),
    ));
    await tester.pump();
  });
}
