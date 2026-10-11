import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
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

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.privacyNoticeTitle ?? 'Privacy Notice & Terms'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppColors.primaryGreen.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppColors.primaryGreen.withOpacity(0.2)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.shield_rounded, color: AppColors.primaryGreen, size: 36),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text(
                                  'Sri Lanka PDPA Compliance',
                                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  l10n?.privacyNoticeSubtitle ??
                                      'How we protect your financial data under Sri Lanka Personal Data Protection Act.',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
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
                      style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'LankaPay collects your verified mobile phone number, National Identity Card (NIC), and linked bank account tokens solely to facilitate instant inter-bank clearing, payment identity discovery, and mandatory fraud prevention audits.',
                      style: TextStyle(fontSize: 13, height: 1.5),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '2. Masking & Protection',
                      style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'All bank account numbers and payment credentials are encrypted using AES-256-GCM authenticated encryption. Personal identifiers are never written in plain text to application logs or exposed on lock-screen push notifications.',
                      style: TextStyle(fontSize: 13, height: 1.5),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '3. Simulated Environment Notice',
                      style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'This installation operates in SIMULATED test mode. No actual fiat funds or legal tender will be debited or credited from real banking institutions.',
                      style: TextStyle(fontSize: 13, height: 1.5),
                    ),
                    const SizedBox(height: 24),
                    const Divider(),
                    CheckboxListTile(
                      key: const Key('consent_terms_checkbox'),
                      value: _agreedToTerms,
                      onChanged: (val) {
                        setState(() {
                          _agreedToTerms = val ?? false;
                        });
                      },
                      title: const Text(
                        'I accept the LankaPay User Agreement and Terms of Service',
                        style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                      ),
                      controlAffinity: ListTileControlAffinity.leading,
                      contentPadding: EdgeInsets.zero,
                    ),
                    CheckboxListTile(
                      key: const Key('consent_privacy_checkbox'),
                      value: _agreedToPrivacy,
                      onChanged: (val) {
                        setState(() {
                          _agreedToPrivacy = val ?? false;
                        });
                      },
                      title: const Text(
                        'I consent to the collection and processing of my KYC data under the PDPA',
                        style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                      ),
                      controlAffinity: ListTileControlAffinity.leading,
                      contentPadding: EdgeInsets.zero,
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton(
                      key: const Key('consent_continue_btn'),
                      onPressed: (_agreedToTerms && _agreedToPrivacy)
                          ? () {
                              context.go('/register');
                            }
                          : null,
                      child: Text(l10n?.consentContinue ?? 'Agree & Continue'),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton(
                      onPressed: () {
                        context.pop();
                      },
                      child: Text(l10n?.consentDecline ?? 'Decline'),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
