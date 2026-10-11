import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';
import '../theme/app_tokens.dart';

/// Base shimmer effect wrapper that adapts smoothly to light and dark themes.
class ShimmerWrapper extends StatelessWidget {
  final Widget child;
  final bool? enabled;

  const ShimmerWrapper({super.key, required this.child, this.enabled});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isTest = WidgetsBinding.instance.runtimeType.toString().contains('TestWidgets');
    final isEnabled = enabled ?? !isTest;

    if (!isEnabled) {
      return child;
    }

    return Shimmer.fromColors(
      baseColor: isDark ? const Color(0xFF1C1C28) : const Color(0xFFE8E8EE),
      highlightColor: isDark ? const Color(0xFF28283A) : const Color(0xFFF6F6FA),
      child: child,
    );
  }
}

/// Generic skeleton box with customizable dimensions and radius.
class SkeletonBox extends StatelessWidget {
  final double width;
  final double height;
  final BorderRadius? borderRadius;

  const SkeletonBox({
    super.key,
    required this.width,
    required this.height,
    this.borderRadius,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: borderRadius ?? BorderRadius.circular(8),
      ),
    );
  }
}

/// Shimmer skeleton simulating an AccountCard while loading.
class SkeletonAccountCard extends StatelessWidget {
  const SkeletonAccountCard({super.key = const Key('skeleton_account_card')});

  @override
  Widget build(BuildContext context) {
    return ShimmerWrapper(
      child: Container(
        height: 200,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: AppTokens.borderRadiusCard,
        ),
      ),
    );
  }
}

/// Shimmer skeleton simulating a TransactionTile while loading.
class SkeletonTransactionTile extends StatelessWidget {
  const SkeletonTransactionTile({super.key = const Key('skeleton_transaction_tile')});

  @override
  Widget build(BuildContext context) {
    return ShimmerWrapper(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12.0, horizontal: 4.0),
        child: Row(
          children: [
            const SkeletonBox(
              width: 46,
              height: 46,
              borderRadius: BorderRadius.all(Radius.circular(15)),
            ),
            const SizedBox(width: 14),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SkeletonBox(width: 130, height: 14),
                  SizedBox(height: 6),
                  SkeletonBox(width: 80, height: 11),
                ],
              ),
            ),
            const Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                SkeletonBox(width: 70, height: 14),
                SizedBox(height: 6),
                SkeletonBox(width: 45, height: 11),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Shimmer skeleton simulating a QuickActionTile while loading.
class SkeletonQuickActionTile extends StatelessWidget {
  const SkeletonQuickActionTile({super.key = const Key('skeleton_quick_action_tile')});

  @override
  Widget build(BuildContext context) {
    return ShimmerWrapper(
      child: Container(
        width: 80,
        height: 96,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: AppTokens.borderRadiusTile,
        ),
      ),
    );
  }
}
