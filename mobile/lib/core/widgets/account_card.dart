import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';
import 'simulated_badge.dart';

enum AccountCardStyle { charcoal, lavender }

/// Bank account card representation supporting charcoal & lavender styles
/// with a subtle geometric hexagon pattern and simulated indicator.
class AccountCard extends StatefulWidget {
  final String bankName;
  final String nickname;
  final String maskedNumber;
  final String balance;
  final bool isDefault;
  final AccountCardStyle style;
  final VoidCallback? onTap;
  final String? expiryText;

  const AccountCard({
    super.key,
    required this.bankName,
    required this.nickname,
    required this.maskedNumber,
    required this.balance,
    this.isDefault = false,
    this.style = AccountCardStyle.charcoal,
    this.onTap,
    this.expiryText,
  });

  @override
  State<AccountCard> createState() => _AccountCardState();
}

class _AccountCardState extends State<AccountCard> {
  bool _isMasked = false;

  @override
  Widget build(BuildContext context) {
    final isCharcoal = widget.style == AccountCardStyle.charcoal;
    final gradient = isCharcoal
        ? AppTokens.cardCharcoalGradient
        : AppTokens.cardLavenderGradient;

    return GestureDetector(
      onTap: widget.onTap,
      child: Container(
        height: 200,
        decoration: BoxDecoration(
          borderRadius: AppTokens.borderRadiusCard,
          gradient: gradient,
          boxShadow: isCharcoal ? AppTokens.softShadowDark : AppTokens.cardShadowLight,
          border: Border.all(
            color: Colors.white.withOpacity(isCharcoal ? 0.08 : 0.20),
            width: 1.2,
          ),
        ),
        child: ClipRRect(
          borderRadius: AppTokens.borderRadiusCard,
          child: Stack(
            children: [
              // Subtle translucent geometric hexagon pattern
              Positioned.fill(
                child: CustomPaint(
                  painter: _HexagonPatternPainter(
                    color: Colors.white.withOpacity(isCharcoal ? 0.04 : 0.06),
                  ),
                ),
              ),

              // Content Padding
              Padding(
                padding: const EdgeInsets.all(AppTokens.space20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    // Top Row: Bank Name, Badges
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                widget.bankName,
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 14,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 0.8,
                                  color: Colors.white.withOpacity(0.92),
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 2),
                              Text(
                                widget.nickname,
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                  color: Colors.white.withOpacity(0.72),
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                        Row(
                          children: [
                            if (widget.isDefault) ...[
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(
                                  color: Colors.white.withOpacity(0.20),
                                  borderRadius: AppTokens.borderRadiusSmall,
                                ),
                                child: const Text(
                                  'DEFAULT',
                                  style: TextStyle(
                                    fontFamily: AppTokens.fontFamily,
                                    fontSize: 9,
                                    fontWeight: FontWeight.w800,
                                    letterSpacing: 0.6,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 6),
                            ],
                            const SimulatedBadge(compact: true),
                          ],
                        ),
                      ],
                    ),

                    // Middle: Balance with Eye toggle
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Text(
                          _isMasked ? '••••••••' : widget.balance,
                          style: const TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 24,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.2,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          icon: Icon(
                            _isMasked ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                            color: Colors.white.withOpacity(0.8),
                            size: 20,
                          ),
                          onPressed: () => setState(() => _isMasked = !_isMasked),
                          tooltip: 'Toggle balance',
                        ),
                      ],
                    ),

                    // Bottom Row: Masked Number & Expiry
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          widget.maskedNumber,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1.5,
                            color: Colors.white.withOpacity(0.85),
                          ),
                        ),
                        if (widget.expiryText != null)
                          Text(
                            widget.expiryText!,
                            style: TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 12,
                              fontWeight: FontWeight.w500,
                              color: Colors.white.withOpacity(0.65),
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Custom painter rendering subtle geometric hexagons on card backgrounds
class _HexagonPatternPainter extends CustomPainter {
  final Color color;
  _HexagonPatternPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    final strokePaint = Paint()
      ..color = color.withOpacity(color.opacity * 1.5)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;

    const radius = 38.0;
    final centers = [
      Offset(size.width * 0.75, size.height * 0.35),
      Offset(size.width * 0.88, size.height * 0.65),
      Offset(size.width * 0.62, size.height * 0.72),
      Offset(size.width * 0.48, size.height * 0.38),
    ];

    for (final c in centers) {
      final path = _buildHexagonPath(c, radius);
      canvas.drawPath(path, paint);
      canvas.drawPath(path, strokePaint);
    }
  }

  Path _buildHexagonPath(Offset center, double r) {
    final path = Path();
    for (int i = 0; i < 6; i++) {
      final angle = (i * 60) * math.pi / 180;
      final x = center.dx + r * math.cos(angle);
      final y = center.dy + r * math.sin(angle);
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.close();
    return path;
  }

  @override
  bool shouldRepaint(covariant _HexagonPatternPainter oldDelegate) =>
      oldDelegate.color != color;
}
