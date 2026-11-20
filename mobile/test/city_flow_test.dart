import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/city/presentation/city_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

Widget createCityTestApp({required Widget child}) {
  return ProviderScope(
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('en')],
      home: child,
    ),
  );
}

void main() {
  group('City Flow & Merchants Tests', () {
    testWidgets('1. City screen renders search, category chips, offers row, and seeded merchants', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(createCityTestApp(child: const CityScreen()));
      await tester.pumpAndSettle();

      // Verify title and search bar
      expect(find.text('City & Merchants'), findsOneWidget);
      expect(find.byKey(TestKeys.citySearchInput), findsOneWidget);

      // Verify category chips
      expect(find.byKey(TestKeys.cityCat('all')), findsOneWidget);
      expect(find.byKey(TestKeys.cityCat('food')), findsOneWidget);
      expect(find.byKey(TestKeys.cityCat('groceries')), findsOneWidget);
      expect(find.byKey(TestKeys.cityCat('fuel')), findsOneWidget);

      // Verify Offers for you row
      expect(find.byKey(TestKeys.cityOffersRow), findsOneWidget);

      // Verify merchant cards exist (merch_1: Cinnamon Leaf Bistro)
      expect(find.byKey(TestKeys.merchant('merch_1')), findsOneWidget);
      expect(find.text('Cinnamon Leaf Bistro'), findsOneWidget);
      expect(find.byKey(TestKeys.merchant('merch_2')), findsOneWidget);
      expect(find.text('Metro Island Grocers'), findsOneWidget);
    });

    testWidgets('2. Category chip filtering works correctly', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(createCityTestApp(child: const CityScreen()));
      await tester.pumpAndSettle();

      // Tap on Fuel category chip
      await tester.tap(find.byKey(TestKeys.cityCat('fuel')));
      await tester.pumpAndSettle();

      // Should show Ceylon Spark Fuel and Central Rapid Fuel, but not Cinnamon Leaf Bistro
      expect(find.text('Ceylon Spark Fuel'), findsOneWidget);
      expect(find.text('Cinnamon Leaf Bistro'), findsNothing);

      // Tap on All chip to reset
      await tester.tap(find.byKey(TestKeys.cityCat('all')));
      await tester.pumpAndSettle();
      expect(find.text('Cinnamon Leaf Bistro'), findsOneWidget);
    });

    testWidgets('3. Search input filters merchants by query', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(createCityTestApp(child: const CityScreen()));
      await tester.pumpAndSettle();

      // Enter search query "Kandy"
      await tester.enterText(find.byKey(TestKeys.citySearchInput), 'Kandy');
      await tester.pumpAndSettle();

      // Kandy merchants should be displayed
      expect(find.text('Kumbuk Shade Tea Room'), findsOneWidget);
      expect(find.text('Hill Capital Mart'), findsOneWidget);
      expect(find.text('Cinnamon Leaf Bistro'), findsNothing);
    });

    testWidgets('4. Merchant detail opens, displays info & offers, and favourite heart toggles', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(createCityTestApp(child: const CityScreen()));
      await tester.pumpAndSettle();

      // Tap merchant card
      await tester.tap(find.byKey(TestKeys.merchant('merch_1')));
      await tester.pumpAndSettle();

      // Verify on MerchantDetailScreen
      expect(find.byKey(const Key('merchant_detail_merch_1')), findsOneWidget);
      expect(find.textContaining('Opening Hours: 07:30 - 23:00'), findsOneWidget);
      expect(find.textContaining('QR-CITY-MERCH-1'), findsOneWidget);
      expect(find.text('2.5x Points on Weekend Dining'), findsOneWidget);

      // Verify favourite heart button exists
      expect(find.byKey(TestKeys.merchantFav), findsOneWidget);
      // Toggle favourite
      await tester.tap(find.byKey(TestKeys.merchantFav));
      await tester.pumpAndSettle();
    });

    testWidgets('5. Pay-at-merchant flow with merchant_pay button ends in confirmed payment with category', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(createCityTestApp(child: const CityScreen()));
      await tester.pumpAndSettle();

      // Open merch_1 detail
      await tester.tap(find.byKey(TestKeys.merchant('merch_1')));
      await tester.pumpAndSettle();

      // Find "Pay Here with LankaQR" button
      expect(find.byKey(TestKeys.merchantPay), findsOneWidget);
      await tester.tap(find.byKey(TestKeys.merchantPay));
      await tester.pumpAndSettle();

      // Verify payment sheet opened
      expect(find.text('Pay at Cinnamon Leaf Bistro'), findsOneWidget);
      expect(find.byKey(const Key('merchant_pay_amount_input')), findsOneWidget);
      expect(find.byKey(const Key('merchant_pay_confirm_btn')), findsOneWidget);

      // Confirm payment
      await tester.tap(find.byKey(const Key('merchant_pay_confirm_btn')));
      await tester.pumpAndSettle();

      // Verify payment confirmed dialog with merchant category
      expect(find.text('Payment Confirmed!'), findsOneWidget);
      expect(find.text('Food'), findsOneWidget);
      expect(find.textContaining('TXN-CITY-'), findsOneWidget);

      // Close dialog
      await tester.tap(find.byKey(const Key('merchant_pay_done_btn')));
      await tester.pumpAndSettle();
      expect(find.text('Payment Confirmed!'), findsNothing);
    });
  });
}
