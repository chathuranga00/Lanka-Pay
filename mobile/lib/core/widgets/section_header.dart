import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Small-caps header section label with an optional trailing action button.
class SectionHeader extends StatelessWidget {
  final String title;
  final String? actionLabel;
  final VoidCallback? onActionTap;
  final Key? actionKey;

  const SectionHeader({
    super.key,
    required this.title,
    this.actionLabel,
    this.onActionTap,
    this.actionKey,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            title.toUpperCase(),
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 12,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.2,
              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
            ),
          ),
          if (actionLabel != null)
            GestureDetector(
              key: actionKey,
              onTap: onActionTap,
              child: Text(
                actionLabel!,
                style: const TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: AppTokens.primaryViolet,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
