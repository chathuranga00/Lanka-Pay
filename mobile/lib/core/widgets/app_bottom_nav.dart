import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../l10n/app_localizations.dart';
import '../theme/app_tokens.dart';

/// Modern frosted bottom navigation dock with 4 items.
class AppBottomNav extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onIndexChanged;
  final List<AppNavItem>? items;

  const AppBottomNav({
    super.key,
    required this.currentIndex,
    required this.onIndexChanged,
    this.items,
  });

  static const List<AppNavItem> defaultItems = [
    AppNavItem(
      label: 'Home',
      icon: Icons.home_rounded,
      key: Key('nav_home'),
    ),
    AppNavItem(
      label: 'Accounts',
      icon: Icons.credit_card_rounded,
      key: Key('nav_accounts'),
    ),
    AppNavItem(
      label: 'Send',
      icon: Icons.swap_horiz_rounded,
      key: Key('nav_send'),
    ),
    AppNavItem(
      label: 'More',
      icon: Icons.grid_view_rounded,
      key: Key('nav_more'),
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final l10n = AppLocalizations.of(context);

    // Resolve localized labels if using default items
    final List<AppNavItem> navItems = items ?? [
      AppNavItem(
        label: l10n?.navHome ?? 'Home',
        icon: Icons.home_rounded,
        key: const Key('nav_home'),
      ),
      AppNavItem(
        label: l10n?.navAccounts ?? 'Accounts',
        icon: Icons.credit_card_rounded,
        key: const Key('nav_accounts'),
      ),
      AppNavItem(
        label: l10n?.navSend ?? 'Send',
        icon: Icons.swap_horiz_rounded,
        key: const Key('nav_send'),
      ),
      AppNavItem(
        label: l10n?.navMore ?? 'More',
        icon: Icons.grid_view_rounded,
        key: const Key('nav_more'),
      ),
    ];

    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 6),
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
              children: List.generate(navItems.length, (index) {
                final item = navItems[index];
                final isSelected = index == currentIndex;

                return Expanded(
                  child: Semantics(
                    button: true,
                    label: item.label,
                    selected: isSelected,
                    child: InkWell(
                      key: item.key,
                      onTap: () {
                        HapticFeedback.lightImpact();
                        onIndexChanged(index);
                      },
                      borderRadius: BorderRadius.circular(16),
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4.0, vertical: 4.0),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                item.icon,
                                size: 24,
                                color: isSelected
                                    ? AppTokens.primaryViolet
                                    : (isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight),
                              ),
                              const SizedBox(height: 3),
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                child: Text(
                                  item.label,
                                  style: TextStyle(
                                    fontFamily: AppTokens.fontFamily,
                                    fontSize: 11,
                                    fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                                    color: isSelected
                                        ? AppTokens.primaryViolet
                                        : (isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight),
                                  ),
                                  maxLines: 1,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
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
