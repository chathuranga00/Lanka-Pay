import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';
import '../data/auth_service.dart';

class RecoveryScreen extends ConsumerStatefulWidget {
  const RecoveryScreen({super.key});

  @override
  ConsumerState<RecoveryScreen> createState() => _RecoveryScreenState();
}

class _RecoveryScreenState extends ConsumerState<RecoveryScreen> {
  final _formKey = GlobalKey<FormState>();
  final _phoneController = TextEditingController();
  final _nicController = TextEditingController();
  bool _isLoading = false;
  String? _successMessage;
  String? _errorMessage;

  @override
  void dispose() {
    _phoneController.dispose();
    _nicController.dispose();
    super.dispose();
  }

  void _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _successMessage = null;
    });

    try {
      final authService = ref.read(authServiceProvider);
      await authService.initiateRecovery(
        phone: _phoneController.text.trim(),
        nic: _nicController.text.trim(),
      );

      setState(() {
        _isLoading = false;
        _successMessage =
            'Recovery instructions and security verification code have been dispatched to your registered phone.';
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = 'Could not verify account identity. Please check your phone number and NIC.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.recoverPinTitle ?? 'Security Recovery'),
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
                      Icon(
                        Icons.lock_reset_rounded,
                        size: 64,
                        color: isDark ? AppColors.accentGoldLight : AppColors.primaryGreen,
                      ),
                      const SizedBox(height: 20),
                      Text(
                        l10n?.recoverPinTitle ?? 'Recover Your Account',
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w700,
                          color: isDark ? Colors.white : AppColors.textPrimaryLight,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        l10n?.recoverPinSubtitle ??
                            'Recover access by verifying your registered phone number and National Identity Card (NIC).',
                        style: TextStyle(
                          fontSize: 14,
                          color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 32),

                      if (_errorMessage != null) ...[
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
                                  _errorMessage!,
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
                        const SizedBox(height: 20),
                      ],

                      if (_successMessage != null) ...[
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.success.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: AppColors.success.withOpacity(0.4)),
                          ),
                          child: Row(
                            children: [
                              const Icon(Icons.check_circle_outline_rounded, color: AppColors.success, size: 20),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  _successMessage!,
                                  style: const TextStyle(
                                    color: AppColors.success,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 20),
                      ],

                      TextFormField(
                        key: const Key('recover_phone_field'),
                        controller: _phoneController,
                        keyboardType: TextInputType.phone,
                        decoration: InputDecoration(
                          labelText: l10n?.phoneLabel ?? 'Registered Mobile Number',
                          hintText: l10n?.phoneHint ?? 'e.g. 0771234567',
                          prefixIcon: const Icon(Icons.phone_iphone_rounded),
                        ),
                        validator: (value) {
                          if (value == null || value.trim().isEmpty) {
                            return 'Mobile number is required';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      TextFormField(
                        key: const Key('recover_nic_field'),
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
                          return null;
                        },
                      ),
                      const SizedBox(height: 28),

                      ElevatedButton(
                        key: const Key('recover_submit_btn'),
                        onPressed: _isLoading ? null : _submit,
                        child: _isLoading
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : Text(l10n?.recoverSubmit ?? 'Send Recovery Code'),
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
