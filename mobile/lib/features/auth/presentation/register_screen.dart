import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';
import '../data/auth_service.dart';
import '../domain/auth_state.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _phoneController = TextEditingController();
  final _nameController = TextEditingController();
  final _nicController = TextEditingController();
  final _pinController = TextEditingController();
  final _confirmPinController = TextEditingController();

  String _selectedRole = 'CUSTOMER';
  bool _obscurePin = true;

  @override
  void dispose() {
    _phoneController.dispose();
    _nameController.dispose();
    _nicController.dispose();
    _pinController.dispose();
    _confirmPinController.dispose();
    super.dispose();
  }

  void _submit() async {
    if (!_formKey.currentState!.validate()) return;

    final success = await ref.read(authStateProvider.notifier).register(
      phone: _phoneController.text.trim(),
      fullName: _nameController.text.trim(),
      nic: _nicController.text.trim(),
      pin: _pinController.text.trim(),
      role: _selectedRole,
    );

    if (success && mounted) {
      final authState = ref.read(authStateProvider);
      if (authState is AuthOtpPending) {
        context.push('/verify-otp', extra: {
          'userId': authState.userId,
          'phone': authState.phone,
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final authState = ref.watch(authStateProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.register ?? 'Register'),
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
                      Text(
                        'Create Your LankaPay Account',
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w700,
                          color: isDark ? Colors.white : AppColors.textPrimaryLight,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Link your mobile number and identity for instant transfers.',
                        style: TextStyle(
                          fontSize: 14,
                          color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                        ),
                      ),
                      const SizedBox(height: 24),

                      // Error message container if present
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
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],

                      // Full Name Field
                      TextFormField(
                        key: const Key('register_name_field'),
                        controller: _nameController,
                        textCapitalization: TextCapitalization.words,
                        decoration: InputDecoration(
                          labelText: l10n?.fullNameLabel ?? 'Full Name',
                          hintText: l10n?.fullNameHint ?? 'e.g. Kamal Perera',
                          prefixIcon: const Icon(Icons.person_outline_rounded),
                        ),
                        validator: (value) {
                          if (value == null || value.trim().length < 2) {
                            return 'Full name must be at least 2 characters';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Mobile Number Field
                      TextFormField(
                        key: const Key('register_phone_field'),
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
                            return 'Enter a valid Sri Lankan mobile number';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // NIC Field
                      TextFormField(
                        key: const Key('register_nic_field'),
                        controller: _nicController,
                        textCapitalization: TextCapitalization.characters,
                        decoration: InputDecoration(
                          labelText: l10n?.nicLabel ?? 'National Identity Card (NIC)',
                          hintText: l10n?.nicHint ?? 'e.g. 199012345678 or 901234567V',
                          prefixIcon: const Icon(Icons.badge_outlined),
                        ),
                        validator: (value) {
                          if (value == null || value.trim().isEmpty) {
                            return 'NIC is required';
                          }
                          final nic = value.trim().toUpperCase();
                          if (!RegExp(r'^(\d{9}[VX]|\d{12})$').hasMatch(nic)) {
                            return 'Enter a valid NIC (9 digits + V/X, or 12 digits)';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // 6-digit PIN Field
                      TextFormField(
                        key: const Key('register_pin_field'),
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
                      const SizedBox(height: 16),

                      // Confirm PIN Field
                      TextFormField(
                        key: const Key('register_confirm_pin_field'),
                        controller: _confirmPinController,
                        keyboardType: TextInputType.number,
                        obscureText: true,
                        maxLength: 6,
                        decoration: const InputDecoration(
                          labelText: 'Confirm 6-Digit PIN',
                          prefixIcon: Icon(Icons.lock_reset_rounded),
                          counterText: '',
                        ),
                        validator: (value) {
                          if (value != _pinController.text) {
                            return 'PINs do not match';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Account Role Selector
                      DropdownButtonFormField<String>(
                        value: _selectedRole,
                        decoration: InputDecoration(
                          labelText: l10n?.roleLabel ?? 'Account Role',
                          prefixIcon: const Icon(Icons.account_tree_outlined),
                        ),
                        items: [
                          DropdownMenuItem(
                            value: 'CUSTOMER',
                            child: Text(l10n?.customerRole ?? 'Customer / Personal'),
                          ),
                          DropdownMenuItem(
                            value: 'MERCHANT',
                            child: Text(l10n?.merchantRole ?? 'Merchant / Business'),
                          ),
                        ],
                        onChanged: (val) {
                          if (val != null) {
                            setState(() {
                              _selectedRole = val;
                            });
                          }
                        },
                      ),
                      const SizedBox(height: 28),

                      // Submit button
                      ElevatedButton(
                        key: const Key('register_submit_btn'),
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
                            : Text(l10n?.register ?? 'Register & Send OTP'),
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
