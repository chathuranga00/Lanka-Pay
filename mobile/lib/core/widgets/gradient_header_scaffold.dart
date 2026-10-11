import 'package:flutter/material.dart';
import '../theme/app_theme.dart';
import '../theme/app_tokens.dart';

/// Scaffold with an animated gradient header area and rounded-top content sheet.
class GradientHeaderScaffold extends StatelessWidget {
  final Widget? header;
  final Widget body;
  final Widget? bottomNavigationBar;
  final bool scrollable;
  final EdgeInsetsGeometry? headerPadding;
  final EdgeInsetsGeometry? bodyPadding;

  const GradientHeaderScaffold({
    super.key,
    this.header,
    required this.body,
    this.bottomNavigationBar,
    this.scrollable = true,
    this.headerPadding,
    this.bodyPadding,
  });

  @override
  Widget build(BuildContext context) {
    final themeExt = context.appTheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.lightHeaderStart,
      bottomNavigationBar: bottomNavigationBar,
      body: Stack(
        children: [
          // Background Gradient Animation
          Positioned.fill(
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 400),
              curve: Curves.easeInOut,
              decoration: BoxDecoration(
                gradient: themeExt.headerGradient,
              ),
            ),
          ),

          // Foreground Content
          SafeArea(
            bottom: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header Area
                if (header != null)
                  Padding(
                    padding: headerPadding ??
                        const EdgeInsets.symmetric(
                          horizontal: AppTokens.screenHorizontalPadding,
                          vertical: AppTokens.space12,
                        ),
                    child: header!,
                  ),

                // Rounded Content Sheet
                Expanded(
                  child: Container(
                    decoration: BoxDecoration(
                      color: themeExt.sheetColor,
                      borderRadius: AppTokens.borderRadiusSheetTop,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.04),
                          blurRadius: 10,
                          offset: const Offset(0, -2),
                        ),
                      ],
                    ),
                    child: ClipRRect(
                      borderRadius: AppTokens.borderRadiusSheetTop,
                      child: scrollable
                          ? SingleChildScrollView(
                              padding: bodyPadding ??
                                  const EdgeInsets.symmetric(
                                    horizontal: AppTokens.screenHorizontalPadding,
                                    vertical: AppTokens.space20,
                                  ),
                              child: body,
                            )
                          : Padding(
                              padding: bodyPadding ??
                                  const EdgeInsets.symmetric(
                                    horizontal: AppTokens.screenHorizontalPadding,
                                    vertical: AppTokens.space20,
                                  ),
                              child: body,
                            ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
