import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_theme.dart';
import '../theme/app_tokens.dart';

/// Rounded tile with a navy icon square and two-line label for 2x2 grid services.
class MenuTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  const MenuTile({
    super.key,
    required this.icon,
    required this.label,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final themeExt = context.appTheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;

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
            padding: const EdgeInsets.all(AppTokens.space16),
            decoration: BoxDecoration(
              color: themeExt.mutedSurface,
              borderRadius: AppTokens.borderRadiusTile,
              border: Border.all(
                color: themeExt.border,
                width: 1.0,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: AppTokens.navyIconTile,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  alignment: Alignment.center,
                  child: Icon(
                    icon,
                    color: Colors.white,
                    size: 22,
                  ),
                ),
                const SizedBox(height: 12),
                FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(
                    label,
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      height: 1.25,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
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
