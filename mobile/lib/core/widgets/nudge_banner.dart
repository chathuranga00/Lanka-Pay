import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Pale yellow actionable banner matching the target home layout.
/// Background pale yellow ~#FDEFB0 with high-contrast dark text in light mode,
/// with an accessible dark amber treatment in dark mode.
class NudgeBanner extends StatelessWidget {
  final String text;
  final Widget? icon;
  final Widget? action;
  final VoidCallback? onTap;

  const NudgeBanner({
    super.key = const Key('nudge_banner'),
    required this.text,
    this.icon,
    this.action,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = isDark ? AppTokens.nudgeBannerBgDark : AppTokens.nudgeBannerBgLight;
    final fg = isDark ? AppTokens.nudgeBannerTextDark : AppTokens.nudgeBannerTextLight;

    final banner = Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          if (icon != null) ...[
            icon!,
            const SizedBox(width: 10),
          ],
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: fg,
              ),
            ),
          ),
          if (action != null)
            action!
          else if (onTap != null)
            Icon(
              Icons.chevron_right_rounded,
              size: 20,
              color: fg.withOpacity(0.8),
            ),
        ],
      ),
    );

    if (onTap != null) {
      return Semantics(
        button: true,
        label: text,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(18),
          child: banner,
        ),
      );
    }

    return banner;
  }
}
