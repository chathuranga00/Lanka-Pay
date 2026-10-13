import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Large hero wallet card matching the target home layout.
/// Features circular blue icon, balance, account name, card thumbnails row,
/// dark points pill (~#3A3A3C), and an optional bottom nudge banner slot.
/// Radius ~32, soft card with very subtle shadow.
class WalletCard extends StatelessWidget {
  final String balance;
  final String name;
  final Widget? icon;
  final List<Widget>? cardThumbnails;
  final Widget? pointsPill;
  final Widget? nudgeBanner;
  final VoidCallback? onTap;
  final VoidCallback? onEyeToggle;
  final bool isBalanceHidden;

  const WalletCard({
    super.key = const Key('wallet_card'),
    required this.balance,
    required this.name,
    this.icon,
    this.cardThumbnails,
    this.pointsPill,
    this.nudgeBanner,
    this.onTap,
    this.onEyeToggle,
    this.isBalanceHidden = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = isDark ? AppTokens.cardDark : AppTokens.cardLight;
    final titleColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;
    final subtitleColor = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;
    final shadows = isDark ? AppTokens.softShadowDark : AppTokens.cardShadowLight;

    final defaultIcon = Container(
      width: 44,
      height: 44,
      decoration: const BoxDecoration(
        color: AppTokens.primaryBlue,
        shape: BoxShape.circle,
      ),
      child: const Icon(
        Icons.account_balance_wallet_rounded,
        color: Colors.white,
        size: 22,
      ),
    );

    final card = Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: AppTokens.borderRadiusCard,
        boxShadow: shadows,
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight.withOpacity(0.6),
          width: 0.8,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header Row: Icon, Balance & Name, Points Pill
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              icon ?? defaultIcon,
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            isBalanceHidden ? '••••••' : balance,
                            style: TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 22,
                              fontWeight: FontWeight.w700,
                              color: titleColor,
                              letterSpacing: -0.3,
                            ),
                          ),
                        ),
                        if (onEyeToggle != null) ...[
                          const SizedBox(width: 8),
                          Semantics(
                            button: true,
                            label: isBalanceHidden ? 'Show balance' : 'Hide balance',
                            child: GestureDetector(
                              key: const Key('wallet_card_eye_toggle'),
                              onTap: onEyeToggle,
                              child: Icon(
                                isBalanceHidden
                                    ? Icons.visibility_off_outlined
                                    : Icons.visibility_outlined,
                                size: 18,
                                color: subtitleColor,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      name,
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 14,
                        fontWeight: FontWeight.w400,
                        color: subtitleColor,
                      ),
                    ),
                  ],
                ),
              ),
              if (pointsPill != null) pointsPill!,
            ],
          ),

          // Card Thumbnails Row
          if (cardThumbnails != null && cardThumbnails!.isNotEmpty) ...[
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: cardThumbnails!,
            ),
          ],

          // Nudge Banner Slot at bottom
          if (nudgeBanner != null) ...[
            const SizedBox(height: 18),
            nudgeBanner!,
          ],
        ],
      ),
    );

    if (onTap != null) {
      return Semantics(
        button: true,
        label: 'Wallet: $name $balance',
        child: InkWell(
          onTap: onTap,
          borderRadius: AppTokens.borderRadiusCard,
          child: card,
        ),
      );
    }

    return card;
  }
}
