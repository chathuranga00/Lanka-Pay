import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
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
    HapticFeedback.lightImpact();

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
                        l10n?.recoverPinTitle ?? 'Security Recovery',
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
                    l10n?.recoverPinSubtitle ??
                        'Recover access by verifying your registered phone number and National Identity Card (NIC).',
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
                          Icons.lock_reset_rounded,
                          size: 38,
                          color: AppTokens.primaryViolet,
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),

                    if (_errorMessage != null) ...[
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
                                _errorMessage!,
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

                    if (_successMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppTokens.softViolet,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: AppTokens.primaryViolet.withOpacity(0.4)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.check_circle_outline_rounded, color: AppTokens.primaryViolet, size: 22),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                _successMessage!,
                                style: const TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  color: AppTokens.deepViolet,
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
                    const SizedBox(height: 18),

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

                    ConstrainedBox(
                      constraints: const BoxConstraints(minHeight: 52),
                      child: ElevatedButton(
                        key: const Key('recover_submit_btn'),
                        onPressed: _isLoading ? null : _submit,
                        child: _isLoading
                            ? const SizedBox(
                                height: 22,
                                width: 22,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2.2,
                                  color: Colors.white,
                                ),
                              )
                            : Text(
                                l10n?.recoverSubmit ?? 'Send Recovery Code',
                                style: const TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
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
