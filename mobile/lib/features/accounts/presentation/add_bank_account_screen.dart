import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../data/bank_account_service.dart';
import '../domain/bank_account_model.dart';

class AddBankAccountScreen extends ConsumerStatefulWidget {
  const AddBankAccountScreen({super.key});

  @override
  ConsumerState<AddBankAccountScreen> createState() => _AddBankAccountScreenState();
}

class _AddBankAccountScreenState extends ConsumerState<AddBankAccountScreen> {
  final _formKey = GlobalKey<FormState>();
  final _accountNumberController = TextEditingController();
  final _accountNameController = TextEditingController();
  final _labelController = TextEditingController();

  String _selectedInstitution = 'SIMULATED_BANK_A';
  bool _isLoading = false;
  bool _consentGiven = true;
  String? _errorMessage;

  final List<BankInstitutionModel> _institutions = const [
    BankInstitutionModel(institutionCode: 'SIMULATED_BANK_A', displayName: 'Simulated Bank A (BOC Stub)'),
    BankInstitutionModel(institutionCode: 'SIMULATED_BANK_B', displayName: 'Simulated Bank B (Commercial Stub)'),
    BankInstitutionModel(institutionCode: 'SIMULATED_BANK_C', displayName: 'Simulated Bank C (Sampath Stub)'),
  ];

  @override
  void dispose() {
    _accountNumberController.dispose();
    _accountNameController.dispose();
    _labelController.dispose();
    super.dispose();
  }

  void _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (!_consentGiven) {
      setState(() {
        _errorMessage = 'You must grant consent to verify account eligibility.';
      });
      return;
    }
    HapticFeedback.lightImpact();

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final success = await ref.read(bankAccountsProvider.notifier).linkAccount(
      institutionCode: _selectedInstitution,
      accountNumber: _accountNumberController.text.trim(),
      accountName: _accountNameController.text.trim(),
      label: _labelController.text.trim().isEmpty ? null : _labelController.text.trim(),
    );

    setState(() {
      _isLoading = false;
    });

    if (success && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Simulated bank account linked successfully!')),
      );
      context.pop();
    } else if (mounted) {
      setState(() {
        _errorMessage = 'Failed to link account. Please verify simulated account details.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
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
                        'Link Bank Account',
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
                    'Add a simulated Sri Lankan bank account for instant transfers.',
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
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppTokens.softViolet,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: AppTokens.lightViolet.withOpacity(0.5)),
                      ),
                      child: const Row(
                        children: [
                          Icon(Icons.info_outline_rounded, color: AppTokens.deepViolet, size: 22),
                          SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              'SIMULATED ENVIRONMENT: Enter simulated account test numbers (e.g. 101001, 202002). No real bank credentials required.',
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: AppTokens.deepViolet,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 20),

                    if (_errorMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppTokens.errorLight,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: AppTokens.error.withOpacity(0.4)),
                        ),
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
                      const SizedBox(height: 18),
                    ],

                    DropdownButtonFormField<String>(
                      key: const Key('add_bank_institution_dropdown'),
                      isExpanded: true,
                      value: _selectedInstitution,
                      decoration: const InputDecoration(
                        labelText: 'Select Bank Institution',
                        prefixIcon: Icon(Icons.account_balance_rounded),
                      ),
                      items: _institutions.map((i) {
                        return DropdownMenuItem(
                          value: i.institutionCode,
                          child: Text(
                            i.displayName,
                            style: const TextStyle(fontSize: 13),
                            overflow: TextOverflow.ellipsis,
                          ),
                        );
                      }).toList(),
                      onChanged: (val) {
                        if (val != null) {
                          setState(() {
                            _selectedInstitution = val;
                          });
                        }
                      },
                    ),
                    const SizedBox(height: 16),

                    TextFormField(
                      key: const Key('add_bank_number_field'),
                      controller: _accountNumberController,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Account Number',
                        hintText: 'e.g. 101001 or 202002',
                        prefixIcon: Icon(Icons.numbers_rounded),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().length < 4) {
                          return 'Enter a valid account number';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    TextFormField(
                      key: const Key('add_bank_name_field'),
                      controller: _accountNameController,
                      textCapitalization: TextCapitalization.words,
                      decoration: const InputDecoration(
                        labelText: 'Account Holder Name',
                        hintText: 'e.g. Kamal Perera',
                        prefixIcon: Icon(Icons.person_outline_rounded),
                      ),
                      validator: (value) {
                        if (value == null || value.trim().length < 2) {
                          return 'Account holder name is required';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    TextFormField(
                      key: const Key('add_bank_label_field'),
                      controller: _labelController,
                      decoration: const InputDecoration(
                        labelText: 'Account Label (Optional)',
                        hintText: 'e.g. Salary, Savings',
                        prefixIcon: Icon(Icons.label_outline_rounded),
                      ),
                    ),
                    const SizedBox(height: 16),

                    CheckboxListTile(
                      key: const Key('add_bank_consent_checkbox'),
                      value: _consentGiven,
                      activeColor: AppTokens.primaryViolet,
                      onChanged: (val) {
                        HapticFeedback.lightImpact();
                        setState(() {
                          _consentGiven = val ?? true;
                        });
                      },
                      title: const Text(
                        'I authorize LankaPay to verify account details with the issuing bank stub and store encrypted references.',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      controlAffinity: ListTileControlAffinity.leading,
                      contentPadding: EdgeInsets.zero,
                    ),
                    const SizedBox(height: 24),

                    ConstrainedBox(
                      constraints: const BoxConstraints(minHeight: 52),
                      child: ElevatedButton(
                        key: const Key('add_bank_submit_btn'),
                        onPressed: _isLoading ? null : _submit,
                        child: _isLoading
                            ? const SizedBox(
                                height: 22,
                                width: 22,
                                child: CircularProgressIndicator(strokeWidth: 2.2, color: Colors.white),
                              )
                            : const Text(
                                'Link Account',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],
                ),
              ),
    );
  }
}
