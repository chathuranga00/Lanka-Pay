import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:dio/dio.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../accounts/data/bank_account_service.dart';
import '../../bills/data/bill_service.dart';
import '../../bills/domain/bill_models.dart';
import '../../cards/data/card_service.dart';
import '../data/scheduled_service.dart';
import '../domain/scheduled_models.dart';

class ScheduleCreateScreen extends ConsumerStatefulWidget {
  const ScheduleCreateScreen({super.key});

  @override
  ConsumerState<ScheduleCreateScreen> createState() => _ScheduleCreateScreenState();
}

class _ScheduleCreateScreenState extends ConsumerState<ScheduleCreateScreen> {
  ScheduleType _selectedType = ScheduleType.transfer;
  ScheduleFrequency _selectedFrequency = ScheduleFrequency.monthly;

  final TextEditingController _payeeController = TextEditingController();
  final TextEditingController _amountController = TextEditingController();
  final TextEditingController _pinController = TextEditingController();
  final TextEditingController _descriptionController = TextEditingController();

  BillerModel? _selectedBiller;
  bool _isBillAmountVariable = false;
  bool _consentGiven = false;

  late DateTime _startDate;
  DateTime? _endDate;

  String? _selectedFundingSource;
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    // Default start date = tomorrow
    final now = DateTime.now();
    _startDate = DateTime(now.year, now.month, now.day).add(const Duration(days: 1));
  }

  @override
  void dispose() {
    _payeeController.dispose();
    _amountController.dispose();
    _pinController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  String _formatDate(DateTime dt) {
    final y = dt.year;
    final m = dt.month.toString().padLeft(2, '0');
    final d = dt.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }

  Future<void> _pickStartDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _startDate,
      firstDate: now,
      lastDate: now.add(const Duration(days: 365 * 2)),
    );
    if (picked != null) {
      setState(() {
        _startDate = picked;
        if (_endDate != null && _endDate!.isBefore(_startDate)) {
          _endDate = null;
        }
      });
    }
  }

  Future<void> _pickEndDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _endDate ?? _startDate.add(const Duration(days: 30)),
      firstDate: _startDate,
      lastDate: _startDate.add(const Duration(days: 365 * 5)),
    );
    if (picked != null) {
      setState(() {
        _endDate = picked;
      });
    }
  }

  Future<void> _submit() async {
    setState(() {
      _errorMessage = null;
    });

    final payeeText = _payeeController.text.trim();
    if (payeeText.isEmpty) {
      setState(() {
        _errorMessage = _selectedType == ScheduleType.transfer
            ? 'Please enter recipient mobile or account number.'
            : 'Please enter customer reference.';
      });
      return;
    }

    int? amountMinor;
    if (_selectedType == ScheduleType.transfer || !_isBillAmountVariable) {
      final amt = double.tryParse(_amountController.text.trim());
      if (amt == null || amt <= 0) {
        setState(() {
          _errorMessage = 'Please enter a valid amount.';
        });
        return;
      }
      amountMinor = (amt * 100).round();
    }

    if (!_consentGiven) {
      setState(() {
        _errorMessage = 'You must provide consent for automatic scheduled debits.';
      });
      return;
    }

    final pin = _pinController.text.trim();
    if (pin.length < 4) {
      setState(() {
        _errorMessage = 'Please enter your 4-digit security PIN.';
      });
      return;
    }

    setState(() {
      _isLoading = true;
    });

    try {
      final Map<String, dynamic> payeeMap;
      if (_selectedType == ScheduleType.transfer) {
        payeeMap = {
          'type': 'TRANSFER',
          'recipientIdentity': payeeText,
        };
      } else {
        final billerId = _selectedBiller?.id ?? 'CEB-01';
        payeeMap = {
          'type': 'BILL',
          'billerId': billerId,
          'customerReference': payeeText,
          if (_selectedBiller != null) 'billerName': _selectedBiller!.name,
        };
      }

      final service = ref.read(scheduledServiceProvider);
      await service.createSchedule(
        type: _selectedType,
        payee: payeeMap,
        amountMinor: amountMinor,
        fundingSource: _selectedFundingSource,
        frequency: _selectedFrequency,
        startDate: _formatDate(_startDate),
        endDate: _endDate != null ? _formatDate(_endDate!) : null,
        pin: pin,
        consentGiven: _consentGiven,
        description: _descriptionController.text.trim().isNotEmpty
            ? _descriptionController.text.trim()
            : null,
      );

      if (!mounted) return;

      await ref.read(scheduledListProvider.notifier).refresh();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Scheduled payment created successfully!'),
            backgroundColor: AppTokens.success,
          ),
        );
        context.pop();
      }
    } on DioException catch (e) {
      if (!mounted) return;
      final serverMsg = e.response?.data is Map
          ? (e.response?.data['message'] ?? e.response?.data['error'])?.toString()
          : null;
      setState(() {
        _isLoading = false;
        _errorMessage = serverMsg ?? 'Failed to create schedule. Please check inputs.';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accounts = ref.watch(bankAccountsProvider).value ?? [];
    final cards = ref.watch(cardsProvider).value ?? [];
    final billers = ref.watch(billersProvider).value ?? [];

    if (_selectedBiller == null && billers.isNotEmpty) {
      _selectedBiller = billers.first;
    }

    if (_selectedFundingSource == null) {
      if (accounts.isNotEmpty) {
        final defaultAcc = accounts.firstWhere((a) => a.isDefault, orElse: () => accounts.first);
        _selectedFundingSource = 'ACCOUNT:${defaultAcc.id}';
      } else if (cards.isNotEmpty) {
        _selectedFundingSource = 'CARD:${cards.first.id}';
      }
    }

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Create Schedule',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Payment Type Segmented Switch
              Container(
                decoration: BoxDecoration(
                  color: isDark ? AppTokens.searchFieldBgDark : AppTokens.searchFieldBgLight,
                  borderRadius: BorderRadius.circular(12),
                ),
                padding: const EdgeInsets.all(4),
                child: Row(
                  children: [
                    Expanded(
                      child: InkWell(
                        key: AppTestKeys.schedTypeTransfer,
                        onTap: () {
                          setState(() {
                            _selectedType = ScheduleType.transfer;
                            _isBillAmountVariable = false;
                          });
                        },
                        borderRadius: BorderRadius.circular(10),
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 10),
                          decoration: BoxDecoration(
                            color: _selectedType == ScheduleType.transfer
                                ? (isDark ? AppTokens.cardDark : Colors.white)
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(10),
                            boxShadow: _selectedType == ScheduleType.transfer
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(0.06),
                                      blurRadius: 4,
                                      offset: const Offset(0, 2),
                                    )
                                  ]
                                : null,
                          ),
                          child: Center(
                            child: Text(
                              'Transfer',
                              style: TextStyle(
                                fontWeight: _selectedType == ScheduleType.transfer
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                                color: _selectedType == ScheduleType.transfer
                                    ? AppTokens.primaryBlue
                                    : (isDark
                                        ? AppTokens.textSecondaryDark
                                        : AppTokens.textSecondaryLight),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: InkWell(
                        key: AppTestKeys.schedTypeBill,
                        onTap: () {
                          setState(() {
                            _selectedType = ScheduleType.bill;
                          });
                        },
                        borderRadius: BorderRadius.circular(10),
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 10),
                          decoration: BoxDecoration(
                            color: _selectedType == ScheduleType.bill
                                ? (isDark ? AppTokens.cardDark : Colors.white)
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(10),
                            boxShadow: _selectedType == ScheduleType.bill
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(0.06),
                                      blurRadius: 4,
                                      offset: const Offset(0, 2),
                                    )
                                  ]
                                : null,
                          ),
                          child: Center(
                            child: Text(
                              'Bill Payment',
                              style: TextStyle(
                                fontWeight: _selectedType == ScheduleType.bill
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                                color: _selectedType == ScheduleType.bill
                                    ? AppTokens.primaryBlue
                                    : (isDark
                                        ? AppTokens.textSecondaryDark
                                        : AppTokens.textSecondaryLight),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 20),

              // Biller dropdown if BILL type
              if (_selectedType == ScheduleType.bill && billers.isNotEmpty) ...[
                Text(
                  'Select Biller',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
                const SizedBox(height: 6),
                DropdownButtonFormField<BillerModel>(
                  value: _selectedBiller,
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                      ),
                    ),
                  ),
                  items: billers.map((b) {
                    return DropdownMenuItem<BillerModel>(
                      value: b,
                      child: Text(b.name),
                    );
                  }).toList(),
                  onChanged: (val) {
                    setState(() {
                      _selectedBiller = val;
                    });
                  },
                ),
                const SizedBox(height: 16),
              ],

              // 2. Payee Input Field
              Text(
                _selectedType == ScheduleType.transfer
                    ? 'Recipient (Mobile or LankaPay Account)'
                    : 'Customer Reference Number',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 6),
              TextField(
                key: AppTestKeys.schedPayeeField,
                controller: _payeeController,
                decoration: InputDecoration(
                  hintText: _selectedType == ScheduleType.transfer
                      ? 'e.g. +94771234567 or 1002345678'
                      : 'e.g. 1029384756',
                  prefixIcon: Icon(
                    _selectedType == ScheduleType.transfer
                        ? Icons.person_outline_rounded
                        : Icons.receipt_outlined,
                  ),
                  filled: true,
                  fillColor: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(
                      color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 16),

              // Variable Bill toggle for bills
              if (_selectedType == ScheduleType.bill) ...[
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Pay exact bill amount due automatically',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    Switch(
                      value: _isBillAmountVariable,
                      onChanged: (val) {
                        setState(() {
                          _isBillAmountVariable = val;
                          if (val) _amountController.clear();
                        });
                      },
                      activeColor: AppTokens.primaryBlue,
                    ),
                  ],
                ),
                const SizedBox(height: 8),
              ],

              // 3. Amount Field (only if not variable bill amount)
              if (!_isBillAmountVariable) ...[
                Text(
                  'Amount (LKR)',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
                const SizedBox(height: 6),
                TextField(
                  key: AppTestKeys.schedAmountField,
                  controller: _amountController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [
                    FilteringTextInputFormatter.allow(RegExp(r'^\d+\.?\d{0,2}')),
                  ],
                  decoration: InputDecoration(
                    hintText: '0.00',
                    prefixText: 'LKR ',
                    prefixStyle: const TextStyle(fontWeight: FontWeight.w700),
                    filled: true,
                    fillColor: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // 4. Frequency Selector
              Text(
                'Frequency',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  _buildFrequencyChip('Monthly', ScheduleFrequency.monthly, isDark),
                  const SizedBox(width: 8),
                  _buildFrequencyChip('Weekly', ScheduleFrequency.weekly, isDark),
                  const SizedBox(width: 8),
                  _buildFrequencyChip('One-time', ScheduleFrequency.once, isDark),
                ],
              ),

              const SizedBox(height: 16),

              // 5. Start and End Dates
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Start Date',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: isDark
                                ? AppTokens.textSecondaryDark
                                : AppTokens.textSecondaryLight,
                          ),
                        ),
                        const SizedBox(height: 6),
                        InkWell(
                          onTap: _pickStartDate,
                          borderRadius: BorderRadius.circular(12),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
                            decoration: BoxDecoration(
                              color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(
                                color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                              ),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.calendar_today_rounded, size: 16),
                                const SizedBox(width: 8),
                                Text(
                                  _formatDate(_startDate),
                                  style: const TextStyle(fontWeight: FontWeight.w600),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (_selectedFrequency != ScheduleFrequency.once) ...[
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'End Date (Optional)',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: isDark
                                  ? AppTokens.textSecondaryDark
                                  : AppTokens.textSecondaryLight,
                            ),
                          ),
                          const SizedBox(height: 6),
                          InkWell(
                            onTap: _pickEndDate,
                            borderRadius: BorderRadius.circular(12),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
                              decoration: BoxDecoration(
                                color: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(
                                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                                ),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.event_busy_rounded, size: 16),
                                  const SizedBox(width: 8),
                                  Text(
                                    _endDate != null ? _formatDate(_endDate!) : 'Until cancelled',
                                    style: TextStyle(
                                      fontWeight: FontWeight.w600,
                                      color: _endDate != null
                                          ? (isDark
                                              ? AppTokens.textPrimaryDark
                                              : AppTokens.textPrimaryLight)
                                          : (isDark
                                              ? AppTokens.textTertiaryDark
                                              : AppTokens.textTertiaryLight),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),

              const SizedBox(height: 16),

              // 6. Funding Source Selector
              if (accounts.isNotEmpty || cards.isNotEmpty) ...[
                Text(
                  'Funding Source',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                ),
                const SizedBox(height: 6),
                DropdownButtonFormField<String>(
                  value: _selectedFundingSource,
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                      ),
                    ),
                  ),
                  items: [
                    ...accounts.map(
                      (a) => DropdownMenuItem(
                        value: 'ACCOUNT:${a.id}',
                        child: Text('${a.accountName} (${a.maskedNumber})'),
                      ),
                    ),
                    ...cards.map(
                      (c) => DropdownMenuItem(
                        value: 'CARD:${c.id}',
                        child: Text('${c.brandLabel} (${c.maskedPan})'),
                      ),
                    ),
                  ],
                  onChanged: (val) {
                    setState(() {
                      _selectedFundingSource = val;
                    });
                  },
                ),
                const SizedBox(height: 16),
              ],

              // Description
              Text(
                'Note / Memo (Optional)',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 6),
              TextField(
                controller: _descriptionController,
                decoration: InputDecoration(
                  hintText: 'e.g. House Rent, Monthly Allowance',
                  filled: true,
                  fillColor: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(
                      color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 16),

              // 7. Consent Checkbox
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppTokens.primaryBlue.withOpacity(0.06),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppTokens.primaryBlue.withOpacity(0.18)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Checkbox(
                      key: AppTestKeys.schedConsentCheckbox,
                      value: _consentGiven,
                      onChanged: (val) {
                        setState(() {
                          _consentGiven = val ?? false;
                        });
                      },
                      activeColor: AppTokens.primaryBlue,
                    ),
                    Expanded(
                      child: GestureDetector(
                        onTap: () {
                          setState(() {
                            _consentGiven = !_consentGiven;
                          });
                        },
                        child: Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Text(
                            'I authorize LankaPay to automatically debit my funding source according to this schedule. I understand risk limits apply at each execution.',
                            style: TextStyle(
                              fontSize: 12,
                              color: isDark
                                  ? AppTokens.textSecondaryDark
                                  : AppTokens.textSecondaryLight,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 16),

              // 8. Step-up Auth PIN
              Text(
                'Enter Security PIN for Authorization',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 6),
              TextField(
                key: AppTestKeys.schedPinField,
                controller: _pinController,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 6,
                decoration: InputDecoration(
                  counterText: '',
                  hintText: 'Enter 4-digit PIN',
                  prefixIcon: const Icon(Icons.lock_outline_rounded),
                  filled: true,
                  fillColor: isDark ? AppTokens.cardDark : AppTokens.cardLight,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(
                      color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                    ),
                  ),
                ),
              ),

              // Error banner if any
              if (_errorMessage != null) ...[
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTokens.error.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppTokens.error.withOpacity(0.3)),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.error_outline_rounded, color: AppTokens.error, size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          _errorMessage!,
                          style: TextStyle(
                            color: AppTokens.error,
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 24),

              // 9. Submit Button [sched_create]
              FilledButton(
                key: AppTestKeys.schedCreate,
                onPressed: _isLoading ? null : _submit,
                style: FilledButton.styleFrom(
                  backgroundColor: AppTokens.primaryBlue,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                child: _isLoading
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text(
                        'Set Up Scheduled Payment',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
              ),

              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFrequencyChip(String label, ScheduleFrequency freq, bool isDark) {
    final isSelected = _selectedFrequency == freq;
    return Expanded(
      child: ChoiceChip(
        label: Center(child: Text(label)),
        selected: isSelected,
        onSelected: (_) {
          setState(() {
            _selectedFrequency = freq;
          });
        },
        selectedColor: AppTokens.primaryBlue.withOpacity(0.18),
        labelStyle: TextStyle(
          fontSize: 12,
          fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
          color: isSelected
              ? AppTokens.primaryBlue
              : (isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight),
        ),
      ),
    );
  }
}
