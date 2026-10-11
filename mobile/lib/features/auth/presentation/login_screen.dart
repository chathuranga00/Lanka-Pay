import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
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

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.signIn ?? 'Sign In'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24.0),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const SizedBox(height: 16),
                      Text(
                        'Welcome Back',
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w700,
                          color: isDark ? Colors.white : AppColors.textPrimaryLight,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Sign in using your registered mobile number and 6-digit security PIN.',
                        style: TextStyle(
                          fontSize: 14,
                          color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                        ),
                      ),
                      const SizedBox(height: 32),

                      if (authState is AuthError) ...[
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.error.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: AppColors.error.withOpacity(0.4)),
                          ),
                          child: Row(
                            children: [
                              const Icon(Icons.error_outline_rounded, color: AppColors.error, size: 20),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  authState.message,
                                  style: const TextStyle(
                                    color: AppColors.error,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ),
                              if (authState.isRetryable)
                                TextButton(
                                  onPressed: _submit,
                                  child: Text(
                                    l10n?.retryButton ?? 'Retry',
                                    style: const TextStyle(
                                      color: AppColors.error,
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
                      const SizedBox(height: 16),

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
                          suffixIcon: IconButton(
                            icon: Icon(
                              _obscurePin ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                            ),
                            onPressed: () {
                              setState(() {
                                _obscurePin = !_obscurePin;
                              });
                            },
                          ),
                        ),
                        validator: (value) {
                          if (value == null || !RegExp(r'^\d{6}$').hasMatch(value)) {
                            return 'PIN must be exactly 6 numeric digits';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 12),

                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          key: const Key('login_forgot_pin_btn'),
                          onPressed: () {
                            context.push('/recover');
                          },
                          child: Text(
                            l10n?.forgotPin ?? 'Forgot PIN?',
                            style: TextStyle(
                              color: isDark ? AppColors.accentGoldLight : AppColors.primaryGreen,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 20),

                      ElevatedButton(
                        key: const Key('login_submit_btn'),
                        onPressed: authState is AuthLoading ? null : _submit,
                        child: authState is AuthLoading
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : Text(l10n?.signIn ?? 'Sign In'),
                      ),
                      const SizedBox(height: 20),

                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Text("Don't have an account?"),
                          TextButton(
                            key: const Key('login_goto_register_btn'),
                            onPressed: () {
                              context.push('/consent');
                            },
                            child: Text(
                              l10n?.register ?? 'Register',
                              style: const TextStyle(fontWeight: FontWeight.w700),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
