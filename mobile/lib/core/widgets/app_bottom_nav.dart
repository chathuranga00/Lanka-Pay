import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_tokens.dart';

/// Modern frosted bottom navigation dock matching the target design language.
/// Displays 5 items (Home, Payments, City, Chat, Products), active item in primary blue (~#4C84F0).
/// Uses original vector icons and supports notification badges.
class AppBottomNav extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onIndexChanged;
  final List<AppNavItem>? items;
  final int? chatBadgeCount;

  const AppBottomNav({
    super.key = const Key('app_bottom_nav'),
    required this.currentIndex,
    required this.onIndexChanged,
    this.items,
    this.chatBadgeCount,
  });

  /// Default 5 items for Super App layout (Home, Pay, City, Chat, Explore)
  static List<AppNavItem> get default5Items => [
        const AppNavItem(
          label: 'Home',
          icon: Icons.account_balance_wallet_rounded,
          key: Key('nav_home'),
        ),
        const AppNavItem(
          label: 'Pay',
          icon: Icons.sync_alt_rounded,
          key: Key('nav_pay'),
        ),
        const AppNavItem(
          label: 'City',
          icon: Icons.storefront_rounded,
          key: Key('nav_city'),
        ),
        const AppNavItem(
          label: 'Chat',
          icon: Icons.chat_bubble_rounded,
          key: Key('nav_chat'),
          badgeCount: 1,
        ),
        const AppNavItem(
          label: 'Explore',
          icon: Icons.grid_view_rounded,
          key: Key('nav_explore'),
        ),
      ];

  /// Backward-compatible 4 items for AppShell router branches
  static const List<AppNavItem> default4Items = [
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

  static const List<AppNavItem> defaultItems = default4Items;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final navItems = items ?? default5Items;

    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 6),
          decoration: BoxDecoration(
            color: isDark
                ? AppTokens.cardDark.withOpacity(0.85)
                : Colors.white.withOpacity(0.92),
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
                final badge = item.badgeCount ?? (item.label == 'Chat' ? chatBadgeCount : null);

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
                          padding: const EdgeInsets.symmetric(horizontal: 2.0, vertical: 4.0),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Stack(
                                clipBehavior: Clip.none,
                                children: [
                                  Icon(
                                    item.icon,
                                    size: 24,
                                    color: isSelected
                                        ? AppTokens.primaryBlue
                                        : (isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight),
                                  ),
                                  if (badge != null && badge > 0)
                                    Positioned(
                                      right: -6,
                                      top: -3,
                                      child: Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                                        decoration: BoxDecoration(
                                          color: AppTokens.error,
                                          borderRadius: BorderRadius.circular(10),
                                        ),
                                        constraints: const BoxConstraints(
                                          minWidth: 14,
                                          minHeight: 14,
                                        ),
                                        child: Center(
                                          child: Text(
                                            '$badge',
                                            style: const TextStyle(
                                              fontFamily: AppTokens.fontFamily,
                                              fontSize: 9,
                                              fontWeight: FontWeight.w700,
                                              color: Colors.white,
                                            ),
                                          ),
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                              const SizedBox(height: 3),
                              FittedBox(
                                fit: BoxFit.scaleDown,
                                child: Text(
                                  item.label,
                                  style: TextStyle(
                                    fontFamily: AppTokens.fontFamily,
                                    fontSize: 11,
                                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                                    color: isSelected
                                        ? AppTokens.primaryBlue
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
  final int? badgeCount;

  const AppNavItem({
    required this.label,
    required this.icon,
    required this.key,
    this.badgeCount,
  });
}
