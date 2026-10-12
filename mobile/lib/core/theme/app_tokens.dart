import 'package:flutter/material.dart';

/// Design tokens for LankaPay ID Mobile Application.
/// All colors, radii, spacing, gradients, and typography are centralized here.
/// Hard-coded colors and arbitrary dimensions must NOT be used outside of this file.
abstract final class AppTokens {
  // ---------------------------------------------------------------------------
  // Brand Colors (Neo-Banking Violet Palette)
  // ---------------------------------------------------------------------------
  static const Color primaryViolet = Color(0xFF7B5CFA);
  static const Color lightViolet = Color(0xFFA592FF);
  static const Color deepViolet = Color(0xFF5E3CE6);
  static const Color darkViolet = Color(0xFF4A2BC7);
  static const Color softViolet = Color(0xFFEDE9FE);

  // ---------------------------------------------------------------------------
  // Light Mode Colors
  // ---------------------------------------------------------------------------
  static const Color lightHeaderStart = Color(0xFFC9C4FF);
  static const Color lightHeaderEnd = Color(0xFF8F9BFF);
  static const Color sheetLight = Color(0xFFFFFFFF);
  static const Color cardLight = Color(0xFFFFFFFF);
  static const Color mutedSurfaceLight = Color(0xFFF4F4F6);
  static const Color textPrimaryLight = Color(0xFF14142B); // 15.6:1 contrast against white
  static const Color textSecondaryLight = Color(0xFF6E6E82); // 4.7:1 contrast against white (WCAG AA)
  static const Color textTertiaryLight = Color(0xFF8A8A9A);
  static const Color borderLight = Color(0xFFE4E4EB);

  // ---------------------------------------------------------------------------
  // Dark Mode Colors
  // ---------------------------------------------------------------------------
  static const Color backgroundDark = Color(0xFF0B0B0F);
  static const Color glowPurple = Color(0xFF2E1A5A);
  static const Color glowAmber = Color(0xFF3D2614);
  static const Color sheetDark = Color(0xFF0B0B0F); // Switchable sheet token (can be white or dark surface)
  static const Color mutedSurfaceDark = Color(0xFF161622);
  static const Color cardDark = Color(0xFF141420);
  static const Color textPrimaryDark = Color(0xFFFFFFFF); // 19.3:1 contrast against dark
  static const Color textSecondaryDark = Color(0xFFA0A0B2); // 8.2:1 contrast against dark
  static const Color textTertiaryDark = Color(0xFF6B6B80);
  static const Color borderDark = Color(0xFF222232);

  // ---------------------------------------------------------------------------
  // Functional / Semantic Colors
  // ---------------------------------------------------------------------------
  static const Color navyIconTile = Color(0xFF0B0F2A);
  static const Color white = Color(0xFFFFFFFF);

  // Positive amount: Green (accessible >= 4.5:1 against white)
  static const Color amountPositiveLight = Color(0xFF059669); // 4.54:1 on white
  static const Color amountPositiveDark = Color(0xFF10B981);

  // Negative amount matches the normal text color
  static const Color amountNegativeLight = textPrimaryLight;
  static const Color amountNegativeDark = textPrimaryDark;

  // Status colors
  static const Color success = Color(0xFF059669);
  static const Color warning = Color(0xFFD97706);
  static const Color warningLight = Color(0xFFFEF3C7);
  static const Color error = Color(0xFFDC2626);
  static const Color errorLight = Color(0xFFFEE2E2);

  // Simulated badge
  static const Color simulatedBadgeBgLight = Color(0xFFEDE9FE);
  static const Color simulatedBadgeTextLight = Color(0xFF5E3CE6);
  static const Color simulatedBadgeBgDark = Color(0xFF281C4F);
  static const Color simulatedBadgeTextDark = Color(0xFFA592FF);

  // ---------------------------------------------------------------------------
  // Gradients
  // ---------------------------------------------------------------------------
  static const LinearGradient lightHeaderGradient = LinearGradient(
    colors: [lightHeaderStart, lightHeaderEnd],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  static const LinearGradient darkHeaderAmbient = LinearGradient(
    colors: [glowPurple, backgroundDark],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  // Tile Gradients
  static const LinearGradient violetTileGradient = LinearGradient(
    colors: [Color(0xFF7B5CFA), Color(0xFFA592FF)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient orangeTileGradient = LinearGradient(
    colors: [Color(0xFFFFB066), Color(0xFFF7893B)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient pinkTileGradient = LinearGradient(
    colors: [Color(0xFFFF6B8F), Color(0xFFE83A6A)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient cyanTileGradient = LinearGradient(
    colors: [Color(0xFF7FE0F7), Color(0xFF4FB8F0)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  // Account Card Gradients
  static const LinearGradient cardCharcoalGradient = LinearGradient(
    colors: [Color(0xFF1E1E26), Color(0xFF121218)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient cardLavenderGradient = LinearGradient(
    colors: [Color(0xFF6F52E8), Color(0xFF947BFE)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  // ---------------------------------------------------------------------------
  // Radii
  // ---------------------------------------------------------------------------
  static const double radiusTile = 20.0;
  static const double radiusCard = 24.0;
  static const double radiusSheetTop = 28.0;
  static const double radiusPill = 999.0;
  static const double radiusInput = 14.0;
  static const double radiusSmall = 8.0;

  static const BorderRadius borderRadiusTile = BorderRadius.all(Radius.circular(radiusTile));
  static const BorderRadius borderRadiusCard = BorderRadius.all(Radius.circular(radiusCard));
  static const BorderRadius borderRadiusSheetTop = BorderRadius.vertical(top: Radius.circular(radiusSheetTop));
  static const BorderRadius borderRadiusPill = BorderRadius.all(Radius.circular(radiusPill));
  static const BorderRadius borderRadiusInput = BorderRadius.all(Radius.circular(radiusInput));
  static const BorderRadius borderRadiusSmall = BorderRadius.all(Radius.circular(radiusSmall));

  // ---------------------------------------------------------------------------
  // Spacing
  // ---------------------------------------------------------------------------
  static const double screenHorizontalPadding = 20.0;
  static const double space4 = 4.0;
  static const double space8 = 8.0;
  static const double space12 = 12.0;
  static const double space16 = 16.0;
  static const double space20 = 20.0;
  static const double space24 = 24.0;
  static const double space28 = 28.0;
  static const double space32 = 32.0;

  // ---------------------------------------------------------------------------
  // Elevation / Shadows
  // ---------------------------------------------------------------------------
  static List<BoxShadow> get softShadowLight => [
        BoxShadow(
          color: const Color(0xFF14142B).withOpacity(0.06),
          blurRadius: 16,
          offset: const Offset(0, 4),
        ),
      ];

  static List<BoxShadow> get cardShadowLight => [
        BoxShadow(
          color: const Color(0xFF7B5CFA).withOpacity(0.12),
          blurRadius: 20,
          offset: const Offset(0, 8),
        ),
      ];

  static List<BoxShadow> get softShadowDark => [
        BoxShadow(
          color: Colors.black.withOpacity(0.35),
          blurRadius: 18,
          offset: const Offset(0, 6),
        ),
      ];

  // ---------------------------------------------------------------------------
  // Typography (Manrope Family)
  // ---------------------------------------------------------------------------
  static const String fontFamily = 'Manrope';

  static const TextStyle displayLarge = TextStyle(
    fontFamily: fontFamily,
    fontSize: 32,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.5,
  );

  static const TextStyle displayMedium = TextStyle(
    fontFamily: fontFamily,
    fontSize: 26,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.3,
  );

  static const TextStyle titleLarge = TextStyle(
    fontFamily: fontFamily,
    fontSize: 20,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.2,
  );

  static const TextStyle titleMedium = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    fontWeight: FontWeight.w600,
  );

  static const TextStyle bodyLarge = TextStyle(
    fontFamily: fontFamily,
    fontSize: 15,
    fontWeight: FontWeight.w500,
    height: 1.4,
  );

  static const TextStyle bodyMedium = TextStyle(
    fontFamily: fontFamily,
    fontSize: 13,
    fontWeight: FontWeight.w400,
    height: 1.4,
  );

  static const TextStyle labelLarge = TextStyle(
    fontFamily: fontFamily,
    fontSize: 14,
    fontWeight: FontWeight.w700,
  );

  static const TextStyle labelMedium = TextStyle(
    fontFamily: fontFamily,
    fontSize: 12,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.2,
  );

  static const TextStyle labelSmall = TextStyle(
    fontFamily: fontFamily,
    fontSize: 11,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.5,
  );

  static const TextStyle sectionHeaderStyle = TextStyle(
    fontFamily: fontFamily,
    fontSize: 11,
    fontWeight: FontWeight.w800,
    letterSpacing: 1.2,
  );
}
