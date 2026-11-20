import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_tokens.dart';

enum QuickActionGradient { violet, orange, pink, cyan }

/// Gradient rounded tile with an icon and label, used in the "FINANCE" section.
class QuickActionTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final QuickActionGradient gradient;
  final VoidCallback? onTap;
  final double? width;
  final double? height;

  const QuickActionTile({
    super.key,
    required this.icon,
    required this.label,
    this.gradient = QuickActionGradient.violet,
    this.onTap,
    this.width,
    this.height,
  });

  LinearGradient get _resolvedGradient {
    return switch (gradient) {
      QuickActionGradient.violet => AppTokens.violetTileGradient,
      QuickActionGradient.orange => AppTokens.orangeTileGradient,
      QuickActionGradient.pink => AppTokens.pinkTileGradient,
      QuickActionGradient.cyan => AppTokens.cyanTileGradient,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap != null
              ? () {
                  HapticFeedback.lightImpact();
                  onTap!();
                }
              : null,
          borderRadius: AppTokens.borderRadiusTile,
          child: Container(
            width: width ?? 86,
            height: height ?? 96,
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
            decoration: BoxDecoration(
              gradient: _resolvedGradient,
              borderRadius: AppTokens.borderRadiusTile,
              boxShadow: [
                BoxShadow(
                  color: _resolvedGradient.colors.first.withOpacity(0.28),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Icon(
                  icon,
                  color: Colors.white,
                  size: 24,
                ),
                FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(
                    label,
                    style: const TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      height: 1.2,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
