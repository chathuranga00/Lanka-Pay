import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';
import '../data/auth_service.dart';
import '../domain/auth_state.dart';

class OtpVerificationScreen extends ConsumerStatefulWidget {
  final int userId;
  final String phone;

  const OtpVerificationScreen({
    super.key,
    required this.userId,
    required this.phone,
  });

  @override
  ConsumerState<OtpVerificationScreen> createState() => _OtpVerificationScreenState();
}

class _OtpVerificationScreenState extends ConsumerState<OtpVerificationScreen> {
  final _otpController = TextEditingController();

  @override
  void dispose() {
    _otpController.dispose();
    super.dispose();
  }

  void _verify() async {
    final otp = _otpController.text.trim();
    if (otp.length != 6) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter all 6 digits of the OTP code.')),
      );
      return;
    }
    HapticFeedback.lightImpact();

    final success = await ref.read(authStateProvider.notifier).verifyOtp(
      userId: widget.userId,
      otp: otp,
      phone: widget.phone,
    );

    if (success && mounted) {
      context.go('/home');
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final authState = ref.watch(authStateProvider);

    return GradientHeaderScaffold(
      scrollable: true,
      header: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SimulatedBanner(),
          const SizedBox(height: AppTokens.space12),
          Row(
            children: [
              if (Navigator.of(context).canPop())
                IconButton(
                  icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
                  tooltip: 'Back',
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    Navigator.of(context).pop();
                  },
                ),
                      Text(
                        l10n?.otpTitle ?? 'OTP Verification',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Enter the 6-digit code sent to ${widget.phone}',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 13,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                ],
              ),
              body: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: 12),
                  Center(
                    child: Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        color: AppTokens.primaryViolet.withOpacity(0.12),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.mark_email_read_outlined,
                        size: 38,
                        color: AppTokens.primaryViolet,
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),

                  if (authState is AuthError) ...[
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppTokens.errorLight,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: AppTokens.error.withOpacity(0.4)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.error_outline_rounded, color: AppTokens.error, size: 22),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              authState.message,
                              style: const TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                color: AppTokens.error,
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],

                  TextField(
                    key: const Key('otp_input_field'),
                    controller: _otpController,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 28,
                      letterSpacing: 16,
                      fontWeight: FontWeight.w800,
                      color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                    ),
                    decoration: InputDecoration(
                      hintText: '000000',
                      counterText: '',
                      hintStyle: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        letterSpacing: 16,
                        color: isDark ? Colors.white24 : Colors.black26,
                      ),
                    ),
                  ),
                  const SizedBox(height: 28),

                  ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 52),
                    child: ElevatedButton(
                      key: const Key('otp_verify_btn'),
                      onPressed: authState is AuthLoading ? null : _verify,
                      child: authState is AuthLoading
                          ? const SizedBox(
                              height: 22,
                              width: 22,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.2,
                                color: Colors.white,
                              ),
                            )
                          : Text(
                              l10n?.verifyButton ?? 'Verify & Continue',
                              style: const TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  TextButton(
                    key: const Key('otp_resend_btn'),
                    onPressed: () {
                      HapticFeedback.lightImpact();
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Simulated OTP re-sent to your mobile.')),
                      );
                    },
                    child: Text(
                      l10n?.resendOtp ?? 'Resend Code',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: isDark ? AppTokens.lightViolet : AppTokens.primaryViolet,
                      ),
                    ),
                  ),
                ],
              ),
    );
  }
}
