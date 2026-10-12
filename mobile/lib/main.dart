import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/localization/locale_controller.dart';
import 'core/routing/app_router.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/theme_controller.dart';
import 'l10n/app_localizations.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    const ProviderScope(
      child: LankaPayApp(),
    ),
  );
}

class LankaPayApp extends ConsumerWidget {
  const LankaPayApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    final themeMode = ref.watch(themeControllerProvider);
    final locale = ref.watch(localeProvider);

    return MaterialApp.router(
      title: 'Lanka Pay',
      debugShowCheckedModeBanner: false,
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
      builder: (context, child) {
        final platformBrightness = MediaQuery.of(context).platformBrightness;
        final isDark = themeMode == ThemeMode.dark ||
            (themeMode == ThemeMode.system && platformBrightness == Brightness.dark);
        final theme = isDark ? AppTheme.darkTheme : AppTheme.lightTheme;
        final reduceMotion = MediaQuery.of(context).disableAnimations;

        return AnimatedTheme(
          data: theme,
          duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 400),
          curve: Curves.easeInOut,
          child: child ?? const SizedBox.shrink(),
        );
      },
    );
  }
}
