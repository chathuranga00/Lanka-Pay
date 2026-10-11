import 'package:flutter/material.dart';
import '../theme/app_theme.dart';
import '../theme/app_tokens.dart';

/// Small chip badge indicating that a card, balance, or transaction is simulated.
class SimulatedBadge extends StatelessWidget {
  final String text;
  final bool compact;

  const SimulatedBadge({
    super.key,
    this.text = 'SIMULATED',
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final themeExt = context.appTheme;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: compact ? AppTokens.space4 * 1.5 : AppTokens.space8,
        vertical: compact ? 2.0 : AppTokens.space4,
      ),
      decoration: BoxDecoration(
        color: themeExt.simulatedBadgeBg,
        borderRadius: AppTokens.borderRadiusSmall,
        border: Border.all(
          color: themeExt.simulatedBadgeText.withOpacity(0.2),
          width: 0.8,
        ),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontFamily: AppTokens.fontFamily,
          fontSize: compact ? 9.0 : 10.0,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.8,
          color: themeExt.simulatedBadgeText,
        ),
      ),
    );
  }
}
