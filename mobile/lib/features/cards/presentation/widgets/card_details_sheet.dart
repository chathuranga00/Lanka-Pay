import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/services/screen_security_service.dart';
import '../../../../core/test_keys.dart';
import '../../../../core/theme/app_tokens.dart';
import '../../../../core/widgets/simulated_badge.dart';
import '../../data/card_service.dart';
import '../../domain/card_model.dart';

/// Ephemeral card details sheet for virtual cards.
/// - Requires step-up PIN
/// - Displays PAN, CVV, expiry for 30s countdown
/// - Screen protection active (FLAG_SECURE on Android, screen protection on iOS)
/// - NO copy button for full number
/// - Auto-dismisses and clears memory on app background or timer expiration
class CardDetailsSheet extends ConsumerStatefulWidget {
  final int cardId;
  final String? pin;

  const CardDetailsSheet({
    super.key,
    required this.cardId,
    this.pin,
  });

  static Future<void> show(BuildContext context, {required int cardId, String? pin}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      enableDrag: true,
      builder: (ctx) => CardDetailsSheet(cardId: cardId, pin: pin),
    );
  }

  @override
  ConsumerState<CardDetailsSheet> createState() => _CardDetailsSheetState();
}

class _CardDetailsSheetState extends ConsumerState<CardDetailsSheet>
    with WidgetsBindingObserver {
  CardRevealModel? _details;
  bool _loading = true;
  String? _error;
  Timer? _countdownTimer;
  int _remainingSeconds = 30;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Block screenshots and screen recording on this sensitive screen
    ScreenSecurityService.enableSecure();
    _fetchDetails();
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    // Unblock screenshot protection upon exit
    ScreenSecurityService.disableSecure();
    _clearSensitiveData();
    super.dispose();
  }

  void _clearSensitiveData() {
    _details = null;
  }

  void _dismissAndClear() {
    _countdownTimer?.cancel();
    _clearSensitiveData();
    if (mounted) {
      Navigator.of(context).maybePop();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Dismiss and purge memory immediately when backgrounded
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.hidden) {
      _dismissAndClear();
    }
  }

  Future<void> _fetchDetails() async {
    try {
      final service = ref.read(cardServiceProvider);
      final res = await service.revealCard(widget.cardId, pin: widget.pin);
      if (!mounted) return;
      setState(() {
        _details = res;
        _remainingSeconds = res.expiresInSeconds > 0 ? res.expiresInSeconds : 30;
        _loading = false;
      });
      _startCountdown();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString().replaceFirst('Exception: ', '');
        _loading = false;
      });
    }
  }

  void _startCountdown() {
    _countdownTimer?.cancel();
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      if (_remainingSeconds <= 1) {
        timer.cancel();
        _dismissAndClear();
      } else {
        setState(() {
          _remainingSeconds--;
        });
      }
    });
  }

  String _formatPan(String rawPan) {
    final clean = rawPan.replaceAll(RegExp(r'\D'), '');
    final buffer = StringBuffer();
    for (int i = 0; i < clean.length; i++) {
      if (i > 0 && i % 4 == 0) buffer.write(' ');
      buffer.write(clean[i]);
    }
    return buffer.toString();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      key: AppTestKeys.cardRevealSheet,
      padding: const EdgeInsets.only(top: 12),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Material(
        color: Colors.transparent,
        child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Handle bar
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Title Row with Timer
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Text(
                        'Card Details',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 18,
                          fontWeight: FontWeight.w800,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const SimulatedBadge(compact: true),
                    ],
                  ),
                  if (!_loading && _error == null)
                    Container(
                      key: AppTestKeys.revealCountdownTimer,
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppTokens.error.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.timer_outlined, size: 14, color: AppTokens.error),
                          const SizedBox(width: 4),
                          Text(
                            '${_remainingSeconds}s',
                            style: const TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 12,
                              fontWeight: FontWeight.w800,
                              color: AppTokens.error,
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 16),

              if (_loading)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 40.0),
                  child: Center(
                    child: CircularProgressIndicator(),
                  ),
                )
              else if (_error != null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24.0),
                  child: Column(
                    children: [
                      const Icon(Icons.error_outline, size: 40, color: AppTokens.error),
                      const SizedBox(height: 12),
                      Text(
                        _error!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        child: const Text('Close'),
                      ),
                    ],
                  ),
                )
              else ...[
                // Card View Container
                Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    gradient: AppTokens.cardCharcoalGradient,
                    borderRadius: AppTokens.borderRadiusCard,
                    border: Border.all(
                      color: Colors.white.withOpacity(0.12),
                    ),
                    boxShadow: AppTokens.softShadowDark,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            _details?.brandLabel ?? 'TEST CARD',
                            style: const TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontSize: 14,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 1.0,
                              color: Colors.white,
                            ),
                          ),
                          const SimulatedBadge(compact: true),
                        ],
                      ),
                      const SizedBox(height: 24),

                      // Card Number (NO copy button)
                      Text(
                        _formatPan(_details?.pan ?? ''),
                        style: const TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 2.0,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Expiry & CVV Row
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'EXPIRES',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                  color: Colors.white.withOpacity(0.6),
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${_details?.expMonth}/${_details?.expYear}',
                                style: const TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'CVV',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                  color: Colors.white.withOpacity(0.6),
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                _details?.cvv ?? '',
                                style: const TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Security Notice Banner
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.security_rounded, size: 18, color: AppTokens.primaryViolet),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'Protected view. Automatically hides in ${_remainingSeconds}s. Screenshots blocked.',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 12,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                ElevatedButton(
                  onPressed: _dismissAndClear,
                  child: const Text('Hide Details'),
                ),
              ],
            ],
          ),
        ),
      ),
    ),
  );
}
}
