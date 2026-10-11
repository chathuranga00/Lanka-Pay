import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_tokens.dart';

/// Custom theme extension providing brand gradients and custom tokens.
@immutable
class AppThemeExtension extends ThemeExtension<AppThemeExtension> {
  final Gradient headerGradient;
  final Color sheetColor;
  final Color mutedSurface;
  final Color navyIconBg;
  final Color cardBackground;
  final Color positiveAmountColor;
  final Color negativeAmountColor;
  final Color simulatedBadgeBg;
  final Color simulatedBadgeText;
  final Gradient accountCardDarkGradient;
  final Gradient accountCardLavenderGradient;
  final Gradient violetTileGradient;
  final Gradient orangeTileGradient;
  final Gradient pinkTileGradient;
  final Gradient cyanTileGradient;
  final Color textSecondary;
  final Color border;

  const AppThemeExtension({
    required this.headerGradient,
    required this.sheetColor,
    required this.mutedSurface,
    required this.navyIconBg,
    required this.cardBackground,
    required this.positiveAmountColor,
    required this.negativeAmountColor,
    required this.simulatedBadgeBg,
    required this.simulatedBadgeText,
    required this.accountCardDarkGradient,
    required this.accountCardLavenderGradient,
    required this.violetTileGradient,
    required this.orangeTileGradient,
    required this.pinkTileGradient,
    required this.cyanTileGradient,
    required this.textSecondary,
    required this.border,
  });

  static const AppThemeExtension light = AppThemeExtension(
    headerGradient: AppTokens.lightHeaderGradient,
    sheetColor: AppTokens.sheetLight,
    mutedSurface: AppTokens.mutedSurfaceLight,
    navyIconBg: AppTokens.navyIconTile,
    cardBackground: AppTokens.sheetLight,
    positiveAmountColor: AppTokens.amountPositiveLight,
    negativeAmountColor: AppTokens.amountNegativeLight,
    simulatedBadgeBg: AppTokens.simulatedBadgeBgLight,
    simulatedBadgeText: AppTokens.simulatedBadgeTextLight,
    accountCardDarkGradient: AppTokens.cardCharcoalGradient,
    accountCardLavenderGradient: AppTokens.cardLavenderGradient,
    violetTileGradient: AppTokens.violetTileGradient,
    orangeTileGradient: AppTokens.orangeTileGradient,
    pinkTileGradient: AppTokens.pinkTileGradient,
    cyanTileGradient: AppTokens.cyanTileGradient,
    textSecondary: AppTokens.textSecondaryLight,
    border: AppTokens.borderLight,
  );

  static const AppThemeExtension dark = AppThemeExtension(
    headerGradient: AppTokens.darkHeaderAmbient,
    sheetColor: AppTokens.sheetLight, // Can be switched to AppTokens.mutedSurfaceDark if desired
    mutedSurface: AppTokens.mutedSurfaceDark,
    navyIconBg: AppTokens.navyIconTile,
    cardBackground: AppTokens.cardDark,
    positiveAmountColor: AppTokens.amountPositiveDark,
    negativeAmountColor: AppTokens.amountNegativeDark,
    simulatedBadgeBg: AppTokens.simulatedBadgeBgDark,
    simulatedBadgeText: AppTokens.simulatedBadgeTextDark,
    accountCardDarkGradient: AppTokens.cardCharcoalGradient,
    accountCardLavenderGradient: AppTokens.cardLavenderGradient,
    violetTileGradient: AppTokens.violetTileGradient,
    orangeTileGradient: AppTokens.orangeTileGradient,
    pinkTileGradient: AppTokens.pinkTileGradient,
    cyanTileGradient: AppTokens.cyanTileGradient,
    textSecondary: AppTokens.textSecondaryDark,
    border: AppTokens.borderDark,
  );

  @override
  AppThemeExtension copyWith({
    Gradient? headerGradient,
    Color? sheetColor,
    Color? mutedSurface,
    Color? navyIconBg,
    Color? cardBackground,
    Color? positiveAmountColor,
    Color? negativeAmountColor,
    Color? simulatedBadgeBg,
    Color? simulatedBadgeText,
    Gradient? accountCardDarkGradient,
    Gradient? accountCardLavenderGradient,
    Gradient? violetTileGradient,
    Gradient? orangeTileGradient,
    Gradient? pinkTileGradient,
    Gradient? cyanTileGradient,
    Color? textSecondary,
    Color? border,
  }) {
    return AppThemeExtension(
      headerGradient: headerGradient ?? this.headerGradient,
      sheetColor: sheetColor ?? this.sheetColor,
      mutedSurface: mutedSurface ?? this.mutedSurface,
      navyIconBg: navyIconBg ?? this.navyIconBg,
      cardBackground: cardBackground ?? this.cardBackground,
      positiveAmountColor: positiveAmountColor ?? this.positiveAmountColor,
      negativeAmountColor: negativeAmountColor ?? this.negativeAmountColor,
      simulatedBadgeBg: simulatedBadgeBg ?? this.simulatedBadgeBg,
      simulatedBadgeText: simulatedBadgeText ?? this.simulatedBadgeText,
      accountCardDarkGradient: accountCardDarkGradient ?? this.accountCardDarkGradient,
      accountCardLavenderGradient: accountCardLavenderGradient ?? this.accountCardLavenderGradient,
      violetTileGradient: violetTileGradient ?? this.violetTileGradient,
      orangeTileGradient: orangeTileGradient ?? this.orangeTileGradient,
      pinkTileGradient: pinkTileGradient ?? this.pinkTileGradient,
      cyanTileGradient: cyanTileGradient ?? this.cyanTileGradient,
      textSecondary: textSecondary ?? this.textSecondary,
      border: border ?? this.border,
    );
  }

  @override
  AppThemeExtension lerp(ThemeExtension<AppThemeExtension>? other, double t) {
    if (other is! AppThemeExtension) return this;
    return AppThemeExtension(
      headerGradient: Gradient.lerp(headerGradient, other.headerGradient, t) ?? headerGradient,
      sheetColor: Color.lerp(sheetColor, other.sheetColor, t) ?? sheetColor,
      mutedSurface: Color.lerp(mutedSurface, other.mutedSurface, t) ?? mutedSurface,
      navyIconBg: Color.lerp(navyIconBg, other.navyIconBg, t) ?? navyIconBg,
      cardBackground: Color.lerp(cardBackground, other.cardBackground, t) ?? cardBackground,
      positiveAmountColor: Color.lerp(positiveAmountColor, other.positiveAmountColor, t) ?? positiveAmountColor,
      negativeAmountColor: Color.lerp(negativeAmountColor, other.negativeAmountColor, t) ?? negativeAmountColor,
      simulatedBadgeBg: Color.lerp(simulatedBadgeBg, other.simulatedBadgeBg, t) ?? simulatedBadgeBg,
      simulatedBadgeText: Color.lerp(simulatedBadgeText, other.simulatedBadgeText, t) ?? simulatedBadgeText,
      accountCardDarkGradient: Gradient.lerp(accountCardDarkGradient, other.accountCardDarkGradient, t) ?? accountCardDarkGradient,
      accountCardLavenderGradient: Gradient.lerp(accountCardLavenderGradient, other.accountCardLavenderGradient, t) ?? accountCardLavenderGradient,
      violetTileGradient: Gradient.lerp(violetTileGradient, other.violetTileGradient, t) ?? violetTileGradient,
      orangeTileGradient: Gradient.lerp(orangeTileGradient, other.orangeTileGradient, t) ?? orangeTileGradient,
      pinkTileGradient: Gradient.lerp(pinkTileGradient, other.pinkTileGradient, t) ?? pinkTileGradient,
      cyanTileGradient: Gradient.lerp(cyanTileGradient, other.cyanTileGradient, t) ?? cyanTileGradient,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t) ?? textSecondary,
      border: Color.lerp(border, other.border, t) ?? border,
    );
  }
}

/// Convenience extension for BuildContext
extension AppThemeX on BuildContext {
  AppThemeExtension get appTheme => Theme.of(this).extension<AppThemeExtension>() ?? AppThemeExtension.light;
}

/// Backward compatibility layer for existing screens.
/// Maps legacy color tokens directly to [AppTokens] so existing code automatically
/// acquires the new violet neo-banking palette without compilation errors.
abstract final class AppColors {
  AppColors._();

  // Primary: Violet
  static const Color primaryGreen = AppTokens.primaryViolet;
  static const Color primaryGreenLight = AppTokens.lightViolet;
  static const Color primaryGreenDark = AppTokens.deepViolet;

  // Secondary: Light Violet / Lavender
  static const Color accentGold = AppTokens.lightViolet;
  static const Color accentGoldLight = AppTokens.softViolet;
  static const Color accentGoldDark = AppTokens.darkViolet;

  // Surface & Background - Light
  static const Color backgroundLight = AppTokens.mutedSurfaceLight;
  static const Color surfaceLight = AppTokens.sheetLight;
  static const Color cardLight = AppTokens.sheetLight;

  // Surface & Background - Dark
  static const Color backgroundDark = AppTokens.backgroundDark;
  static const Color surfaceDark = AppTokens.cardDark;
  static const Color cardDark = AppTokens.cardDark;

  // Text colors
  static const Color textPrimaryLight = AppTokens.textPrimaryLight;
  static const Color textSecondaryLight = AppTokens.textSecondaryLight;
  static const Color textPrimaryDark = AppTokens.textPrimaryDark;
  static const Color textSecondaryDark = AppTokens.textSecondaryDark;

  // Status Colors
  static const Color success = AppTokens.success;
  static const Color warning = AppTokens.warning;
  static const Color error = AppTokens.error;
  static const Color info = AppTokens.primaryViolet;

  // Simulated Banner Colors
  static const Color simulatedBannerBg = AppTokens.simulatedBadgeBgLight;
  static const Color simulatedBannerBorder = AppTokens.lightViolet;
  static const Color simulatedBannerText = AppTokens.deepViolet;

  static const Color simulatedBannerDarkBg = AppTokens.simulatedBadgeBgDark;
  static const Color simulatedBannerDarkBorder = AppTokens.darkViolet;
  static const Color simulatedBannerDarkText = AppTokens.lightViolet;
}

/// Main application theme definitions built strictly from [AppTokens].
abstract final class AppTheme {
  AppTheme._();

  static ThemeData get lightTheme {
    final colorScheme = const ColorScheme.light(
      primary: AppTokens.primaryViolet,
      onPrimary: Colors.white,
      primaryContainer: AppTokens.softViolet,
      onPrimaryContainer: AppTokens.deepViolet,
      secondary: AppTokens.lightViolet,
      onSecondary: Colors.white,
      secondaryContainer: AppTokens.softViolet,
      onSecondaryContainer: AppTokens.deepViolet,
      surface: AppTokens.sheetLight,
      onSurface: AppTokens.textPrimaryLight,
      error: AppTokens.error,
      onError: Colors.white,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppTokens.mutedSurfaceLight,
      fontFamily: GoogleFonts.manrope().fontFamily,
      textTheme: TextTheme(
        displayLarge: AppTokens.displayLarge.copyWith(color: AppTokens.textPrimaryLight),
        displayMedium: AppTokens.displayMedium.copyWith(color: AppTokens.textPrimaryLight),
        titleLarge: AppTokens.titleLarge.copyWith(color: AppTokens.textPrimaryLight),
        titleMedium: AppTokens.titleMedium.copyWith(color: AppTokens.textPrimaryLight),
        bodyLarge: AppTokens.bodyLarge.copyWith(color: AppTokens.textPrimaryLight),
        bodyMedium: AppTokens.bodyMedium.copyWith(color: AppTokens.textPrimaryLight),
        labelLarge: AppTokens.labelLarge.copyWith(color: AppTokens.textPrimaryLight),
        labelMedium: AppTokens.labelMedium.copyWith(color: AppTokens.textPrimaryLight),
        labelSmall: AppTokens.labelSmall.copyWith(color: AppTokens.textSecondaryLight),
      ),
      extensions: const [AppThemeExtension.light],
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        foregroundColor: AppTokens.textPrimaryLight,
        elevation: 0,
        centerTitle: true,
      ),
      cardTheme: CardThemeData(
        color: AppTokens.sheetLight,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: AppTokens.borderRadiusCard,
          side: const BorderSide(color: AppTokens.borderLight),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppTokens.primaryViolet,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppTokens.primaryViolet,
          side: const BorderSide(color: AppTokens.primaryViolet, width: 1.5),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.borderLight),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.borderLight),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.primaryViolet, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.error, width: 1.5),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: AppTokens.borderLight,
        thickness: 1,
      ),
    );
  }

  static ThemeData get darkTheme {
    final colorScheme = const ColorScheme.dark(
      primary: AppTokens.primaryViolet,
      onPrimary: Colors.white,
      primaryContainer: AppTokens.glowPurple,
      onPrimaryContainer: Colors.white,
      secondary: AppTokens.lightViolet,
      onSecondary: Colors.white,
      secondaryContainer: AppTokens.glowPurple,
      onSecondaryContainer: Colors.white,
      surface: AppTokens.cardDark,
      onSurface: AppTokens.textPrimaryDark,
      error: AppTokens.error,
      onError: Colors.white,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppTokens.backgroundDark,
      fontFamily: GoogleFonts.manrope().fontFamily,
      textTheme: TextTheme(
        displayLarge: AppTokens.displayLarge.copyWith(color: AppTokens.textPrimaryDark),
        displayMedium: AppTokens.displayMedium.copyWith(color: AppTokens.textPrimaryDark),
        titleLarge: AppTokens.titleLarge.copyWith(color: AppTokens.textPrimaryDark),
        titleMedium: AppTokens.titleMedium.copyWith(color: AppTokens.textPrimaryDark),
        bodyLarge: AppTokens.bodyLarge.copyWith(color: AppTokens.textPrimaryDark),
        bodyMedium: AppTokens.bodyMedium.copyWith(color: AppTokens.textPrimaryDark),
        labelLarge: AppTokens.labelLarge.copyWith(color: AppTokens.textPrimaryDark),
        labelMedium: AppTokens.labelMedium.copyWith(color: AppTokens.textPrimaryDark),
        labelSmall: AppTokens.labelSmall.copyWith(color: AppTokens.textSecondaryDark),
      ),
      extensions: const [AppThemeExtension.dark],
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        foregroundColor: AppTokens.textPrimaryDark,
        elevation: 0,
        centerTitle: true,
      ),
      cardTheme: CardThemeData(
        color: AppTokens.cardDark,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: AppTokens.borderRadiusCard,
          side: const BorderSide(color: AppTokens.borderDark),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppTokens.primaryViolet,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppTokens.lightViolet,
          side: const BorderSide(color: AppTokens.lightViolet, width: 1.5),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppTokens.cardDark,
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.borderDark),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.borderDark),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.primaryViolet, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: AppTokens.borderRadiusInput,
          borderSide: const BorderSide(color: AppTokens.error, width: 1.5),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: AppTokens.borderDark,
        thickness: 1,
      ),
    );
  }
}
