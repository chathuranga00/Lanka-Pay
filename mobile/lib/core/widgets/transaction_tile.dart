import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_theme.dart';
import '../theme/app_tokens.dart';

/// Transaction row item featuring navy icon square, title, timestamp, and signed amount.
class TransactionTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String amount;
  final String status;
  final bool isPositive;
  final VoidCallback? onTap;

  const TransactionTile({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.amount,
    required this.status,
    this.isPositive = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final themeExt = context.appTheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final amountColor = isPositive
        ? themeExt.positiveAmountColor
        : (isDark ? AppTokens.amountNegativeDark : AppTokens.amountNegativeLight);

    return Semantics(
      button: true,
      label: '$title, $amount, $status',
      child: InkWell(
        onTap: onTap != null
            ? () {
                HapticFeedback.lightImpact();
                onTap!();
              }
            : null,
        borderRadius: BorderRadius.circular(16),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 52),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 10.0, horizontal: 4.0),
            child: Row(
              children: [
                // Navy Icon Square
                Container(
                  width: 46,
                  height: 46,
                  decoration: BoxDecoration(
                    color: AppTokens.navyIconTile,
                    borderRadius: BorderRadius.circular(15),
                  ),
                  alignment: Alignment.center,
                  child: Icon(
                    icon,
                    color: Colors.white,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 14),

                // Title & Subtitle
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 3),
                      Text(
                        subtitle,
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),

                // Amount & Status
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      amount,
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                        color: amountColor,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      status,
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
