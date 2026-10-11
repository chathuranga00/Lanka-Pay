import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Square soft icon button with a label underneath (Add / Freeze / Settings style).
class ActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final bool isDestructive;
  final bool isActive;

  const ActionButton({
    super.key,
    required this.icon,
    required this.label,
    this.onTap,
    this.isDestructive = false,
    this.isActive = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final bgColor = isActive
        ? AppTokens.primaryViolet.withOpacity(0.15)
        : (isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight);

    final iconColor = isDestructive
        ? AppTokens.error
        : (isActive
            ? AppTokens.primaryViolet
            : (isDark ? Colors.white : AppTokens.textPrimaryLight));

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Padding(
          padding: const EdgeInsets.all(4.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 58,
                height: 58,
                decoration: BoxDecoration(
                  color: bgColor,
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(
                    color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                    width: 1.0,
                  ),
                ),
                alignment: Alignment.center,
                child: Icon(
                  icon,
                  size: 22,
                  color: iconColor,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                label,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textPrimaryLight,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
