import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Interactive info tile with title, subtitle, optional progress bar, and icon.
/// Designed for dashboard metrics, transaction overviews, and cashback/rewards.
/// Radius ~28, soft card with very subtle shadow.
class InfoTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final dynamic icon;
  final double? progress;
  final VoidCallback? onTap;
  final double? height;
  final Color? backgroundColor;

  const InfoTile({
    super.key = const Key('info_tile'),
    required this.title,
    this.subtitle,
    this.icon,
    this.progress,
    this.onTap,
    this.height = 110.0,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = backgroundColor ?? (isDark ? AppTokens.cardDark : AppTokens.cardLight);
    final titleColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final subtitleColor = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;
    final shadows = isDark ? AppTokens.softShadowDark : AppTokens.softShadowLight;

    final content = Container(
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
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Header: Title & optional top icon
          Column(
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
                    fontSize: 13,
                    fontWeight: FontWeight.w400,
                    color: subtitleColor,
                  ),
                ),
              ],
            ],
          ),

          // Bottom: Progress bar or Icon slot
          if (progress != null)
            ClipRRect(
              borderRadius: BorderRadius.circular(999),
              child: Container(
                key: const Key('info_tile_progress'),
                height: 5,
                color: isDark ? AppTokens.borderDark : AppTokens.searchFieldBgLight,
                alignment: Alignment.centerLeft,
                child: FractionallySizedBox(
                  widthFactor: progress!.clamp(0.0, 1.0),
                  child: Container(
                    decoration: BoxDecoration(
                      color: AppTokens.primaryBlue,
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                ),
              ),
            )
          else if (icon != null)
            Align(
              alignment: Alignment.bottomLeft,
              child: icon is IconData
                  ? Icon(icon as IconData, color: AppTokens.primaryBlue, size: 24)
                  : (icon as Widget),
            ),
        ],
      ),
    );

    if (onTap != null) {
      return Semantics(
        button: true,
        label: '$title ${subtitle ?? ''}',
        child: InkWell(
          onTap: onTap,
          borderRadius: AppTokens.borderRadiusTile,
          child: content,
        ),
      );
    }

    return content;
  }
}
