import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Add Bank Account (Simulated)'),
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
                      Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppColors.accentGold.withOpacity(0.12),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppColors.accentGold.withOpacity(0.3)),
                        ),
                        child: const Row(
                          children: [
                            Icon(Icons.info_outline_rounded, color: AppColors.accentGoldDark, size: 22),
                            SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                'SIMULATED ENVIRONMENT: Enter simulated account test numbers (e.g. 101001, 202002). No real bank credentials required.',
                                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),

                      if (_errorMessage != null) ...[
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.error.withOpacity(0.12),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: AppColors.error.withOpacity(0.4)),
                          ),
                          child: Text(
                            _errorMessage!,
                            style: const TextStyle(color: AppColors.error, fontSize: 13, fontWeight: FontWeight.w500),
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],

                      DropdownButtonFormField<String>(
                        key: const Key('add_bank_institution_dropdown'),
                        value: _selectedInstitution,
                        decoration: const InputDecoration(
                          labelText: 'Select Bank Institution',
                          prefixIcon: Icon(Icons.account_balance_rounded),
                        ),
                        items: _institutions.map((i) {
                          return DropdownMenuItem(
                            value: i.institutionCode,
                            child: Text(i.displayName, style: const TextStyle(fontSize: 13)),
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
                      const SizedBox(height: 20),

                      CheckboxListTile(
                        key: const Key('add_bank_consent_checkbox'),
                        value: _consentGiven,
                        onChanged: (val) {
                          setState(() {
                            _consentGiven = val ?? true;
                          });
                        },
                        title: const Text(
                          'I authorize LankaPay to verify account details with the issuing bank stub and store encrypted references.',
                          style: TextStyle(fontSize: 12),
                        ),
                        controlAffinity: ListTileControlAffinity.leading,
                        contentPadding: EdgeInsets.zero,
                      ),
                      const SizedBox(height: 28),

                      ElevatedButton(
                        key: const Key('add_bank_submit_btn'),
                        onPressed: _isLoading ? null : _submit,
                        child: _isLoading
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Text('Link Account'),
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
