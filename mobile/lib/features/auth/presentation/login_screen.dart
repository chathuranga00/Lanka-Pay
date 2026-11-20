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

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _phoneController = TextEditingController();
  final _pinController = TextEditingController();
  bool _obscurePin = true;

  @override
  void dispose() {
    _phoneController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  void _submit() async {
    if (!_formKey.currentState!.validate()) return;
    HapticFeedback.lightImpact();

    final success = await ref.read(authStateProvider.notifier).login(
      phone: _phoneController.text.trim(),
      pin: _pinController.text.trim(),
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
                        l10n?.signIn ?? 'Sign In',
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
                    'Sign in with your verified mobile number & security PIN.',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 13,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                ],
              ),
              body: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
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
                            if (authState.isRetryable)
                              TextButton(
                                onPressed: _submit,
                                child: Text(
                                  l10n?.retryButton ?? 'Retry',
                                  style: const TextStyle(
                                    fontFamily: AppTokens.fontFamily,
                                    color: AppTokens.error,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 20),
                    ],

                    TextFormField(
                      key: const Key('login_phone_field'),
                      controller: _phoneController,
                      keyboardType: TextInputType.phone,
                      decoration: InputDecoration(
                        labelText: l10n?.phoneLabel ?? 'Mobile Number',
                        hintText: l10n?.phoneHint ?? 'e.g. 0771234567 or +94771234567',
                        prefixIcon: const Icon(Icons.phone_iphone_rounded),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Mobile number is required';
                        }
                        final normalized = AuthService.normalizePhone(value.trim());
                        if (!RegExp(r'^\+[1-9]\d{7,14}$').hasMatch(normalized)) {
                          return 'Enter a valid mobile number';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 18),

                    TextFormField(
                      key: const Key('login_pin_field'),
                      controller: _pinController,
                      keyboardType: TextInputType.number,
                      obscureText: _obscurePin,
                      maxLength: 6,
                      decoration: InputDecoration(
                        labelText: l10n?.pinLabel ?? '6-Digit Security PIN',
                        prefixIcon: const Icon(Icons.lock_outline_rounded),
                        counterText: '',
                        suffixIcon: Semantics(
                          label: _obscurePin ? 'Show PIN' : 'Hide PIN',
                          button: true,
                          child: IconButton(
                            icon: Icon(
                              _obscurePin ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              size: 22,
                            ),
                            onPressed: () {
                              setState(() {
                                _obscurePin = !_obscurePin;
                              });
                            },
                          ),
                        ),
                      ),
                      validator: (value) {
                        if (value == null || !RegExp(r'^\d{6}$').hasMatch(value)) {
                          return 'PIN must be exactly 6 numeric digits';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 8),

                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        key: const Key('login_forgot_pin_btn'),
                        onPressed: () {
                          HapticFeedback.lightImpact();
                          context.push('/recover');
                        },
                        child: Text(
                          l10n?.forgotPin ?? 'Forgot PIN?',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            color: isDark ? AppTokens.lightViolet : AppTokens.primaryViolet,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),

                    ConstrainedBox(
                      constraints: const BoxConstraints(minHeight: 52),
                      child: ElevatedButton(
                        key: const Key('login_submit_btn'),
                        onPressed: authState is AuthLoading ? null : _submit,
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
                                l10n?.signIn ?? 'Sign In',
                                style: const TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                      ),
                    ),
                    const SizedBox(height: 20),

                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          "Don't have an account?",
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          ),
                        ),
                        TextButton(
                          key: const Key('login_goto_register_btn'),
                          onPressed: () {
                            HapticFeedback.lightImpact();
                            context.push('/consent');
                          },
                          child: Text(
                            l10n?.register ?? 'Register',
                            style: TextStyle(
                              fontFamily: AppTokens.fontFamily,
                              fontWeight: FontWeight.w700,
                              color: isDark ? AppTokens.lightViolet : AppTokens.primaryViolet,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
    );
  }
}
