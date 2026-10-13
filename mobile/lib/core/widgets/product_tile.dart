import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Product category tile matching the target home layout.
/// Displays bold title, optional subtitle, and an illustration slot.
/// Radius ~28, soft card with very subtle shadow.
class ProductTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? illustration;
  final VoidCallback? onTap;
  final double height;
  final Color? backgroundColor;

  const ProductTile({
    super.key = const Key('product_tile'),
    required this.title,
    this.subtitle,
    this.illustration,
    this.onTap,
    this.height = 130.0,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = backgroundColor ?? (isDark ? AppTokens.cardDark : AppTokens.cardLight);
    final titleColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final subtitleColor = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;
    final shadows = isDark ? AppTokens.softShadowDark : AppTokens.softShadowLight;

    final tile = Container(
      height: height,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: AppTokens.borderRadiusTile,
        boxShadow: shadows,
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight.withOpacity(0.5),
          width: 0.8,
        ),
      ),
      child: Stack(
        children: [
          // Text Details
          Positioned(
            left: 0,
            top: 0,
            right: 48,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: titleColor,
                    letterSpacing: -0.2,
                  ),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    subtitle!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 12,
                      fontWeight: FontWeight.w400,
                      color: subtitleColor,
                    ),
                  ),
                ],
              ],
            ),
          ),

          // Illustration Slot (Bottom-Right aligned)
          if (illustration != null)
            Positioned(
              right: 0,
              bottom: 0,
              child: illustration!,
            ),
        ],
      ),
    );

    if (onTap != null) {
      return Semantics(
        button: true,
        label: title,
        child: InkWell(
          onTap: onTap,
          borderRadius: AppTokens.borderRadiusTile,
          child: tile,
        ),
      );
    }

    return tile;
  }
}
