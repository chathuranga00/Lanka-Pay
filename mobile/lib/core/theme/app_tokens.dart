import 'package:flutter/material.dart';

/// Design tokens for LankaPay ID Mobile Application (Super App design language).
/// All colors, radii, spacing, gradients, and typography are centralized here.
/// Hard-coded colors and arbitrary dimensions must NOT be used outside of this file.
abstract final class AppTokens {
  // ---------------------------------------------------------------------------
  // Super App Palette Tokens
  // ---------------------------------------------------------------------------
  /// Primary blue ~#4C84F0 (active tab, icon tiles, links)
  static const Color primaryBlue = Color(0xFF4C84F0);
  static const Color primaryBlueLight = Color(0xFF6F9EFA);
  static const Color primaryBlueDark = Color(0xFF386DD6);

  /// Light blue tint for tiles (~#EBF2FD)
  static const Color tileTintLight = Color(0xFFEBF2FD);
  static const Color tileTintDark = Color(0xFF1E2B45);

  /// Nudge banner pale yellow ~#FDEFB0 with dark text (~#2C240E)
  static const Color nudgeBannerBgLight = Color(0xFFFDEFB0);
  static const Color nudgeBannerTextLight = Color(0xFF2C240E);
  static const Color nudgeBannerBgDark = Color(0xFF3B3214);
  static const Color nudgeBannerTextDark = Color(0xFFFDEFB0);

  /// Grey search field ~#EFEFF1
  static const Color searchFieldBgLight = Color(0xFFEFEFF1);
  static const Color searchFieldBgDark = Color(0xFF1C1C1E);

  /// Avatar grey (~#D1D1D6 / dark ~#3A3A3C)
  static const Color avatarGreyLight = Color(0xFFD1D1D6);
  static const Color avatarGreyDark = Color(0xFF3A3A3C);

  /// Dark small pill ~#3A3A3C for the points pill
  static const Color pointsPillBgLight = Color(0xFF3A3A3C);
  static const Color pointsPillTextLight = Color(0xFFFFFFFF);
  static const Color pointsPillBgDark = Color(0xFF2C2C2E);
  static const Color pointsPillTextDark = Color(0xFFFFFFFF);

  /// Rewards button violet ~#7B3FE4
  static const Color rewardsButtonViolet = Color(0xFF7B3FE4);
  static const Color rewardsButtonVioletDark = Color(0xFF8B52F5);

  // ---------------------------------------------------------------------------
  // Backward-compatible Brand Colors (retained for existing flows)
  // ---------------------------------------------------------------------------
  static const Color primaryViolet = Color(0xFF7B5CFA);
  static const Color lightViolet = Color(0xFFA592FF);
  static const Color deepViolet = Color(0xFF5E3CE6);
  static const Color darkViolet = Color(0xFF4A2BC7);
  static const Color softViolet = Color(0xFFEDE9FE);

  // ---------------------------------------------------------------------------
  // Light Mode Colors
  // ---------------------------------------------------------------------------
  static const Color backgroundLight = Color(0xFFFFFFFF); // Background white
  static const Color lightHeaderStart = Color(0xFFEBF2FD);
  static const Color lightHeaderEnd = Color(0xFFFFFFFF);
  static const Color sheetLight = Color(0xFFFFFFFF);
  static const Color cardLight = Color(0xFFFFFFFF);
  static const Color mutedSurfaceLight = Color(0xFFF7F7F9);

  /// Text near-black #1D1D1F (WCAG AAA contrast against white > 16:1)
  static const Color textPrimaryLight = Color(0xFF1D1D1F);

  /// Secondary text mid grey (WCAG AA contrast against white > 4.5:1)
  static const Color textSecondaryLight = Color(0xFF6E6E73);
  static const Color textTertiaryLight = Color(0xFF8E8E93);
  static const Color borderLight = Color(0xFFE5E5EA);

  // ---------------------------------------------------------------------------
  // Dark Mode Colors
  // ---------------------------------------------------------------------------
  static const Color backgroundDark = Color(0xFF000000); // Sleek pure dark background
  static const Color glowPurple = Color(0xFF1C1C28);
  static const Color glowAmber = Color(0xFF2A2416);
  static const Color sheetDark = Color(0xFF1C1C1E);
  static const Color mutedSurfaceDark = Color(0xFF121214);
  static const Color cardDark = Color(0xFF1C1C1E);

  /// Dark mode text colors (WCAG compliant)
  static const Color textPrimaryDark = Color(0xFFFFFFFF);
  static const Color textSecondaryDark = Color(0xFFA1A1A6);
  static const Color textTertiaryDark = Color(0xFF636366);
  static const Color borderDark = Color(0xFF2C2C2E);

  // ---------------------------------------------------------------------------
  // Functional / Semantic Colors
  // ---------------------------------------------------------------------------
  static const Color navyIconTile = Color(0xFF0B0F2A);
  static const Color white = Color(0xFFFFFFFF);

  /// Positive amount: Green (accessible >= 4.5:1 against white)
  static const Color amountPositiveLight = Color(0xFF059669);
  static const Color amountPositiveDark = Color(0xFF34C759);

  /// Negative amount matches normal primary text
  static const Color amountNegativeLight = textPrimaryLight;
  static const Color amountNegativeDark = textPrimaryDark;

  /// Semantic Status colors
  static const Color success = Color(0xFF34C759);
  static const Color warning = Color(0xFFFF9500);
  static const Color warningLight = Color(0xFFFEF3C7);
  static const Color error = Color(0xFFFF3B30);
  static const Color errorLight = Color(0xFFFEE2E2);

  /// Simulated badge
  static const Color simulatedBadgeBgLight = Color(0xFFEBF2FD);
  static const Color simulatedBadgeTextLight = Color(0xFF386DD6);
  static const Color simulatedBadgeBgDark = Color(0xFF1E2B45);
  static const Color simulatedBadgeTextDark = Color(0xFF6F9EFA);

  // ---------------------------------------------------------------------------
  // Gradients
  // ---------------------------------------------------------------------------
  static const LinearGradient lightHeaderGradient = LinearGradient(
    colors: [Color(0xFFF2F6FE), Color(0xFFFFFFFF)],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  static const LinearGradient darkHeaderAmbient = LinearGradient(
    colors: [Color(0xFF1C1C28), Color(0xFF000000)],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  // Tile Gradients
  static const LinearGradient blueTileGradient = LinearGradient(
    colors: [Color(0xFF4C84F0), Color(0xFF6F9EFA)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient violetTileGradient = LinearGradient(
    colors: [Color(0xFF7B3FE4), Color(0xFF9F68F7)],
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
    colors: [Color(0xFF4C84F0), Color(0xFF7B5CFA)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  // ---------------------------------------------------------------------------
  // Radii (Target screenshot specifications)
  // ---------------------------------------------------------------------------
  /// Tile radius ~28
  static const double radiusTile = 28.0;

  /// Card radius ~32
  static const double radiusCard = 32.0;

  /// Search field radius ~18
  static const double radiusSearch = 18.0;

  /// Story cards ~24
  static const double radiusStory = 24.0;

  static const double radiusSheetTop = 28.0;
  static const double radiusPill = 999.0;
  static const double radiusInput = 18.0;
  static const double radiusSmall = 12.0;

  static const BorderRadius borderRadiusTile = BorderRadius.all(Radius.circular(radiusTile));
  static const BorderRadius borderRadiusCard = BorderRadius.all(Radius.circular(radiusCard));
  static const BorderRadius borderRadiusSearch = BorderRadius.all(Radius.circular(radiusSearch));
  static const BorderRadius borderRadiusStory = BorderRadius.all(Radius.circular(radiusStory));
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

  static const double spacing4 = space4;
  static const double spacing8 = space8;
  static const double spacing12 = space12;
  static const double spacing14 = 14.0;
  static const double spacing16 = space16;
  static const double spacing20 = space20;
  static const double spacing24 = space24;
  static const double spacing32 = space32;

  static const double radiusSm = 8.0;
  static const double radiusMd = 12.0;
  static const double radiusLg = 16.0;
  static const double radiusFull = 999.0;

  // ---------------------------------------------------------------------------
  // Elevation / Shadows (Large soft cards with very subtle shadow)
  // ---------------------------------------------------------------------------
  static List<BoxShadow> get softShadowLight => [
        BoxShadow(
          color: const Color(0xFF000000).withOpacity(0.04),
          blurRadius: 16,
          offset: const Offset(0, 4),
        ),
      ];

  static List<BoxShadow> get cardShadowLight => [
        BoxShadow(
          color: const Color(0xFF000000).withOpacity(0.05),
          blurRadius: 20,
          offset: const Offset(0, 6),
        ),
      ];

  static List<BoxShadow> get softShadowDark => [
        BoxShadow(
          color: Colors.black.withOpacity(0.40),
          blurRadius: 18,
          offset: const Offset(0, 4),
        ),
      ];

  // ---------------------------------------------------------------------------
  // Typography (Geometric Sans: Manrope bundled with bold 700 headings)
  // ---------------------------------------------------------------------------
  static const String fontFamily = 'Manrope';

  static const TextStyle displayLarge = TextStyle(
    fontFamily: fontFamily,
    fontSize: 32,
    fontWeight: FontWeight.w700,
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
    fontWeight: FontWeight.w700,
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
    fontSize: 12,
    fontWeight: FontWeight.w700,
    letterSpacing: 1.0,
  );
}
