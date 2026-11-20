import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';

/// Privacy notice and user terms consent screen required by Section 13/PDPA
class ConsentScreen extends StatefulWidget {
  const ConsentScreen({super.key});

  @override
  State<ConsentScreen> createState() => _ConsentScreenState();
}

class _ConsentScreenState extends State<ConsentScreen> {
  bool _agreedToTerms = false;
  bool _agreedToPrivacy = false;

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
                        l10n?.privacyNoticeTitle ?? 'Privacy Notice & Terms',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    l10n?.privacyNoticeSubtitle ??
                        'How we protect your financial data under Sri Lanka Personal Data Protection Act.',
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
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppTokens.softViolet,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppTokens.lightViolet.withOpacity(0.5)),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.shield_rounded, color: AppTokens.deepViolet, size: 36),
                        SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Sri Lanka PDPA Compliance',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontWeight: FontWeight.w700,
                                  fontSize: 15,
                                  color: AppTokens.deepViolet,
                                ),
                              ),
                              SizedBox(height: 4),
                              Text(
                                'Financial data processing strictly adheres to Sri Lanka Personal Data Protection Act No. 9 of 2022.',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 12,
                                  color: Color(0xFF4A2BC7),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    '1. Data Collection & Purpose',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'LankaPay collects your verified mobile phone number, National Identity Card (NIC), and linked bank account tokens solely to facilitate instant inter-bank clearing, payment identity discovery, and mandatory fraud prevention audits.',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 13,
                      height: 1.5,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    '2. Masking & Protection',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'All bank account numbers and payment credentials are encrypted using AES-256-GCM authenticated encryption. Personal identifiers are never written in plain text to application logs or exposed on lock-screen push notifications.',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 13,
                      height: 1.5,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    '3. Simulated Environment Notice',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'This installation operates in SIMULATED test mode. No actual fiat funds or legal tender will be debited or credited from real banking institutions.',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 13,
                      height: 1.5,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                  const SizedBox(height: 24),
                  Divider(color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                  const SizedBox(height: 8),

                  CheckboxListTile(
                    key: const Key('consent_terms_checkbox'),
                    value: _agreedToTerms,
                    activeColor: AppTokens.primaryViolet,
                    onChanged: (val) {
                      HapticFeedback.lightImpact();
                      setState(() {
                        _agreedToTerms = val ?? false;
                      });
                    },
                    title: const Text(
                      'I accept the LankaPay User Agreement and Terms of Service',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    controlAffinity: ListTileControlAffinity.leading,
                    contentPadding: EdgeInsets.zero,
                  ),
                  CheckboxListTile(
                    key: const Key('consent_privacy_checkbox'),
                    value: _agreedToPrivacy,
                    activeColor: AppTokens.primaryViolet,
                    onChanged: (val) {
                      HapticFeedback.lightImpact();
                      setState(() {
                        _agreedToPrivacy = val ?? false;
                      });
                    },
                    title: const Text(
                      'I consent to the collection and processing of my KYC data under the PDPA',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    controlAffinity: ListTileControlAffinity.leading,
                    contentPadding: EdgeInsets.zero,
                  ),
                  const SizedBox(height: 24),

                  ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 52),
                    child: ElevatedButton(
                      key: const Key('consent_continue_btn'),
                      onPressed: (_agreedToTerms && _agreedToPrivacy)
                          ? () {
                              HapticFeedback.lightImpact();
                              context.go('/register');
                            }
                          : null,
                      child: Text(
                        l10n?.consentContinue ?? 'Agree & Continue',
                        style: const TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 52),
                    child: OutlinedButton(
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        if (Navigator.of(context).canPop()) {
                          Navigator.of(context).pop();
                        }
                      },
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(
                          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                          width: 1.2,
                        ),
                      ),
                      child: Text(
                        l10n?.consentDecline ?? 'Decline',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),
                ],
              ),
    );
  }
}
