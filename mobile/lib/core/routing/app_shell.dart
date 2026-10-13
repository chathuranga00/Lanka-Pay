import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../config/feature_flags.dart';
import '../widgets/app_bottom_nav.dart';

/// Navigation tab item descriptor with its target branch index and feature flag key.
class _TabDescriptor {
  final int branchIndex;
  final String flagKey;
  final AppNavItem navItem;

  const _TabDescriptor({
    required this.branchIndex,
    required this.flagKey,
    required this.navItem,
  });
}

/// App shell wrapping the 5 Super App tabs with an indexed stack StatefulNavigationShell
/// and dynamically filtered AppBottomNav based on feature flags.
class AppShell extends ConsumerWidget {
  final StatefulNavigationShell navigationShell;

  const AppShell({
    super.key = const Key('app_shell'),
    required this.navigationShell,
  });

  static final List<_TabDescriptor> _allTabs = [
    const _TabDescriptor(
      branchIndex: 0,
      flagKey: FeatureFlagKeys.home,
      navItem: AppNavItem(
        label: 'Home',
        icon: Icons.account_balance_wallet_rounded,
        key: Key('nav_home'),
      ),
    ),
    const _TabDescriptor(
      branchIndex: 1,
      flagKey: FeatureFlagKeys.pay,
      navItem: AppNavItem(
        label: 'Pay',
        icon: Icons.sync_alt_rounded,
        key: Key('nav_pay'),
      ),
    ),
    const _TabDescriptor(
      branchIndex: 2,
      flagKey: FeatureFlagKeys.city,
      navItem: AppNavItem(
        label: 'City',
        icon: Icons.storefront_rounded,
        key: Key('nav_city'),
      ),
    ),
    const _TabDescriptor(
      branchIndex: 3,
      flagKey: FeatureFlagKeys.chat,
      navItem: AppNavItem(
        label: 'Chat',
        icon: Icons.chat_bubble_rounded,
        key: Key('nav_chat'),
        badgeCount: 1,
      ),
    ),
    const _TabDescriptor(
      branchIndex: 4,
      flagKey: FeatureFlagKeys.explore,
      navItem: AppNavItem(
        label: 'Explore',
        icon: Icons.grid_view_rounded,
        key: Key('nav_explore'),
      ),
    ),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final flags = ref.watch(featureFlagsProvider);

    // Filter tabs based on feature flags
    final visibleDescriptors = _allTabs.where((t) {
      if (t.flagKey == FeatureFlagKeys.home) return true; // Home is always visible
      return flags[t.flagKey] ?? true;
    }).toList();

    final visibleItems = visibleDescriptors.map((t) => t.navItem).toList();
    final visibleBranchIndices = visibleDescriptors.map((t) => t.branchIndex).toList();

    int selectedVisibleIndex = visibleBranchIndices.indexOf(navigationShell.currentIndex);
    if (selectedVisibleIndex < 0) {
      // If the current branch is disabled, redirect to Home branch (index 0)
      selectedVisibleIndex = 0;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        navigationShell.goBranch(0);
      });
    }

    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: AppBottomNav(
        currentIndex: selectedVisibleIndex,
        onIndexChanged: (visibleIndex) {
          if (visibleIndex >= 0 && visibleIndex < visibleBranchIndices.length) {
            final targetBranch = visibleBranchIndices[visibleIndex];
            navigationShell.goBranch(
              targetBranch,
              initialLocation: targetBranch == navigationShell.currentIndex,
            );
          }
        },
        items: visibleItems,
      ),
    );
  }
}
