import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../theme/app_tokens.dart';
import '../theme/theme_controller.dart';

/// Pill toggle with Moon and Sun icons, featuring a smooth sliding active indicator.
class ThemeTogglePill extends ConsumerWidget {
  final VoidCallback? onToggle;

  const ThemeTogglePill({
    super.key = const Key('theme_toggle'),
    this.onToggle,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentMode = ref.watch(themeControllerProvider);
    final isDark = switch (currentMode) {
      ThemeMode.dark => true,
      ThemeMode.light => false,
      ThemeMode.system => Theme.of(context).brightness == Brightness.dark,
    };

    return GestureDetector(
      onTap: () {
        if (onToggle != null) {
          onToggle!();
        } else {
          ref.read(themeControllerProvider.notifier).toggleTheme();
        }
      },
      child: Container(
        width: 78,
        height: 38,
        padding: const EdgeInsets.all(3.0),
        decoration: BoxDecoration(
          color: isDark ? Colors.white.withOpacity(0.14) : Colors.black.withOpacity(0.08),
          borderRadius: AppTokens.borderRadiusPill,
        ),
        child: Stack(
          children: [
            // Sliding Active Circle Indicator
            AnimatedAlign(
              duration: const Duration(milliseconds: 280),
              curve: Curves.easeInOutCubic,
              alignment: isDark ? Alignment.centerLeft : Alignment.centerRight,
              child: Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isDark ? AppTokens.primaryViolet : const Color(0xFFF59E0B),
                  boxShadow: [
                    BoxShadow(
                      color: (isDark ? AppTokens.primaryViolet : const Color(0xFFF59E0B)).withOpacity(0.35),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
              ),
            ),
            // Icons Layer
            Row(
              children: [
                Expanded(
                  child: Center(
                    child: Icon(
                      Icons.nightlight_round,
                      size: 17,
                      color: isDark ? Colors.white : (isDark ? Colors.white54 : AppTokens.textSecondaryLight),
                    ),
                  ),
                ),
                Expanded(
                  child: Center(
                    child: Icon(
                      Icons.wb_sunny_rounded,
                      size: 18,
                      color: !isDark ? Colors.white : Colors.white60,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
