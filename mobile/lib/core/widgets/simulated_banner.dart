import 'package:flutter/material.dart';
import '../theme/app_theme.dart';
import '../../l10n/app_localizations.dart';

/// Persistent top/bottom banner ensuring users are always clearly aware
/// that this is a simulated sandbox prototype with test money only.
class SimulatedBanner extends StatelessWidget {
  final Widget? child;

  const SimulatedBanner({super.key, this.child});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final l10n = AppLocalizations.of(context);
    final label = l10n?.simulatedBannerText ?? 'SIMULATED - test money only';

    final banner = Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
      decoration: BoxDecoration(
        color: isDark ? AppColors.simulatedBannerDarkBg : AppColors.simulatedBannerBg,
        border: Border(
          bottom: BorderSide(
            color: isDark ? AppColors.simulatedBannerDarkBorder : AppColors.simulatedBannerBorder,
            width: 1,
          ),
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.warning_amber_rounded,
            size: 16,
            color: isDark ? AppColors.simulatedBannerDarkText : AppColors.simulatedBannerText,
          ),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              label.toUpperCase(),
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.8,
                color: isDark ? AppColors.simulatedBannerDarkText : AppColors.simulatedBannerText,
              ),
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );

    if (child == null) {
      return banner;
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        banner,
        Expanded(child: child!),
      ],
    );
  }
}
