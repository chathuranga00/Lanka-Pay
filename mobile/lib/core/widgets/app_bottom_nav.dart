import 'dart:ui';
import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Modern frosted bottom navigation dock with 4 items.
class AppBottomNav extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onIndexChanged;

  const AppBottomNav({
    super.key,
    required this.currentIndex,
    required this.onIndexChanged,
  });

  static const List<AppNavItem> items = [
    AppNavItem(
      label: 'Home',
      icon: Icons.home_rounded,
      key: Key('app_nav_home'),
    ),
    AppNavItem(
      label: 'Cards',
      icon: Icons.credit_card_rounded,
      key: Key('app_nav_cards'),
    ),
    AppNavItem(
      label: 'Transfers',
      icon: Icons.swap_horiz_rounded,
      key: Key('app_nav_transfers'),
    ),
    AppNavItem(
      label: 'More',
      icon: Icons.grid_view_rounded,
      key: Key('app_nav_more'),
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: isDark
                ? AppTokens.cardDark.withOpacity(0.85)
                : Colors.white.withOpacity(0.90),
            border: Border(
              top: BorderSide(
                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                width: 0.8,
              ),
            ),
          ),
          child: SafeArea(
            top: false,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: List.generate(items.length, (index) {
                final item = items[index];
                final isSelected = index == currentIndex;

                return InkWell(
                  key: item.key,
                  onTap: () => onIndexChanged(index),
                  borderRadius: BorderRadius.circular(16),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          item.icon,
                          size: 24,
                          color: isSelected
                              ? AppTokens.primaryViolet
                              : (isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          item.label,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 11,
                            fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                            color: isSelected
                                ? AppTokens.primaryViolet
                                : (isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              }),
            ),
          ),
        ),
      ),
    );
  }
}

class AppNavItem {
  final String label;
  final IconData icon;
  final Key key;
  const AppNavItem({required this.label, required this.icon, required this.key});
}
