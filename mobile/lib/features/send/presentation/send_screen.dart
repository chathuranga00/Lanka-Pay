import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../accounts/data/bank_account_service.dart';
import '../../accounts/domain/bank_account_model.dart';
import '../data/transfer_service.dart';
import '../domain/transfer_models.dart';

class SendScreen extends ConsumerStatefulWidget {
  final dynamic resolvedQr;

  const SendScreen({super.key, this.resolvedQr});

  @override
  ConsumerState<SendScreen> createState() => _SendScreenState();
}

class _SendScreenState extends ConsumerState<SendScreen> {
  // Step indicator:
  // 0: Search & verify recipient
  // 1: Multi-bank account selection & amount entry
  int _currentStep = 0;

  // Controllers
  final _phoneController = TextEditingController();
  final _amountController = TextEditingController();
  final _noteController = TextEditingController();

  // State
  bool _isSearching = false;
  String? _searchError;
  LookupResultModel? _lookupResult;
  EligibleAccountModel? _selectedPayeeAccount;

  // Sender account
  BankAccountModel? _selectedSenderAccount;

  // Quote & submission
  bool _isGettingQuote = false;
  QuoteModel? _quote;
  String? _quoteError;

  // Idempotency key per intent attempt
  late String _idempotencyKey;
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    _idempotencyKey = _generateIdempotencyKey();

    if (widget.resolvedQr != null) {
      final qr = widget.resolvedQr;
      _phoneController.text = qr.maskedPhone ?? '';
      if (qr.amount != null && qr.amount > 0) {
        _amountController.text = qr.amount.toStringAsFixed(2);
      }
      if (qr.note != null) {
        _noteController.text = qr.note;
      }
      _lookupResult = LookupResultModel(
        resultType: 'FOUND',
        displayName: qr.recipientDisplayName ?? 'QR Recipient',
        verificationStatus: 'VERIFIED',
      );
      _currentStep = 1;
    }
  }

  @override
  void dispose() {
    _phoneController.dispose();
    _amountController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  String _generateIdempotencyKey() {
    final now = DateTime.now().millisecondsSinceEpoch;
    final rand = Random().nextInt(999999).toString().padLeft(6, '0');
    return 'IDEM-$now-$rand';
  }

  Future<void> _performRecipientLookup() async {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      setState(() => _searchError = 'Please enter a mobile phone number');
      return;
    }

    setState(() {
      _isSearching = true;
      _searchError = null;
      _lookupResult = null;
      _selectedPayeeAccount = null;
    });

    try {
      final result = await ref.read(transferServiceProvider).lookupRecipient(phone);
      if (mounted) {
        setState(() {
          _isSearching = false;
          _lookupResult = result;
          if (result.isFound) {
            // Select default or first eligible account
            final eligible = result.accounts ?? [];
            if (eligible.isNotEmpty) {
              _selectedPayeeAccount = eligible.firstWhere(
                (a) => a.isDefault,
                orElse: () => eligible.first,
              );
            }
            _currentStep = 1; // Proceed to Account & Amount step
          } else {
            _searchError = _getFriendlyLookupError(result.resultType);
          }
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSearching = false;
          _searchError = 'Unable to lookup recipient at this time. Please check your connection and try again.';
        });
      }
    }
  }

  String _getFriendlyLookupError(String resultType) {
    switch (resultType) {
      case 'DISCOVERY_DISABLED':
        return 'The recipient has disabled phone-number discovery for privacy under PDPA.';
      case 'NO_ELIGIBLE_ACCOUNT':
        return 'The recipient is registered but has no linked bank accounts configured to receive payments.';
      case 'PRIVACY_RESTRICTED':
        return 'Unable to route transfer to this user due to privacy preferences.';
      case 'NOT_FOUND':
      default:
        return 'No LankaPay-ID account found for this mobile number. Make sure the number is registered.';
    }
  }

  Future<void> _onReviewTransferPressed(List<BankAccountModel> senderAccounts) async {
    final amountText = _amountController.text.trim();
    final amount = double.tryParse(amountText);

    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a valid transfer amount greater than 0')),
      );
      return;
    }

    // Default sender account if not set
    if (_selectedSenderAccount == null && senderAccounts.isNotEmpty) {
      _selectedSenderAccount = senderAccounts.firstWhere(
        (a) => a.isDefault,
        orElse: () => senderAccounts.first,
      );
    }

    if (_selectedSenderAccount == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please select a linked bank account to debit from')),
      );
      return;
    }

    setState(() {
      _isGettingQuote = true;
      _quoteError = null;
    });

    try {
      final quote = await ref.read(transferServiceProvider).getQuote(
            payeePhone: _phoneController.text.trim(),
            amount: amount,
          );
      if (mounted) {
        setState(() {
          _isGettingQuote = false;
          _quote = quote;
        });
        _showConfirmationBottomSheet();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isGettingQuote = false;
          _quoteError = 'Failed to fetch transfer quote. Please check your limits or try again.';
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_quoteError!)),
        );
      }
    }
  }

  void _showConfirmationBottomSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _ConfirmationSheet(
        payeeName: _lookupResult?.displayName ?? 'Verified Recipient',
        payeeBank: _selectedPayeeAccount?.bankDisplayName ?? 'LankaPay Bank',
        payeeMaskedAccount: _selectedPayeeAccount?.maskedNumber ?? '****',
        senderBank: _selectedSenderAccount?.institutionCode ?? 'Origin Bank',
        senderMaskedAccount: _selectedSenderAccount?.maskedNumber ?? '****',
        amount: _quote?.amount ?? 0.0,
        fee: _quote?.fee ?? 0.0,
        total: _quote?.totalAmount ?? 0.0,
        note: _noteController.text.trim().isEmpty ? 'Direct Transfer' : _noteController.text.trim(),
        onConfirmed: () {
          Navigator.of(ctx).pop();
          _showPinAuthenticationDialog();
        },
      ),
    );
  }

  void _showPinAuthenticationDialog() {
    final pinController = TextEditingController();
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Row(
          children: [
            Icon(Icons.lock_outline_rounded, color: AppColors.primaryGreen),
            SizedBox(width: 8),
            Text('Step-Up Authentication'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Enter your 6-digit security PIN to authorize this transfer.',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondaryLight),
            ),
            const SizedBox(height: 16),
            TextField(
              key: const Key('transfer_pin_input'),
              controller: pinController,
              keyboardType: TextInputType.number,
              obscureText: true,
              maxLength: 6,
              autofocus: true,
              decoration: const InputDecoration(
                hintText: '••••••',
                counterText: '',
                prefixIcon: Icon(Icons.pin_outlined),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            key: const Key('transfer_confirm_pin_btn'),
            onPressed: () {
              final pin = pinController.text.trim();
              if (pin.length == 6) {
                Navigator.of(ctx).pop();
                _submitTransfer(pin);
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('PIN must be 6 digits')),
                );
              }
            },
            child: const Text('Authorize Payment'),
          ),
        ],
      ),
    );
  }

  Future<void> _submitTransfer(String pin) async {
    if (_isSubmitting) return; // Double-tap protection

    setState(() => _isSubmitting = true);

    try {
      final transfer = await ref.read(transferServiceProvider).initiateTransfer(
            idempotencyKey: _idempotencyKey,
            pin: pin,
            payeePhone: _phoneController.text.trim(),
            amount: _quote!.amount,
            quoteReference: _quote?.quoteReference,
            note: _noteController.text.trim(),
          );

      if (mounted) {
        setState(() => _isSubmitting = false);
        // Navigate to distinct result screen
        context.pushReplacement('/send/result', extra: transfer);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Transfer initiation failed: ${e.toString()}'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accountsAsync = ref.watch(bankAccountsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Send Money'),
        leading: _currentStep == 1
            ? IconButton(
                icon: const Icon(Icons.arrow_back_rounded),
                onPressed: () => setState(() => _currentStep = 0),
              )
            : null,
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24.0),
                child: _currentStep == 0
                    ? _buildStep0Search(isDark)
                    : _buildStep1AmountAndAccounts(isDark, accountsAsync),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStep0Search(bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Recipient Verification',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w700,
            color: isDark ? Colors.white : AppColors.textPrimaryLight,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          'Enter the recipient\'s registered mobile phone number to verify their LankaPay-ID and eligible receiving accounts.',
          style: TextStyle(
            fontSize: 14,
            height: 1.4,
            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 24),
        TextField(
          key: const Key('send_phone_field'),
          controller: _phoneController,
          keyboardType: TextInputType.phone,
          decoration: const InputDecoration(
            labelText: 'Recipient Phone Number',
            hintText: 'e.g. 0771234567 or +94771234567',
            prefixIcon: Icon(Icons.person_search_rounded),
          ),
        ),
        if (_searchError != null) ...[
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppColors.error.withOpacity(0.08),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.error.withOpacity(0.3)),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.error_outline_rounded, color: AppColors.error, size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _searchError!,
                    style: const TextStyle(
                      color: AppColors.error,
                      fontSize: 13,
                      height: 1.4,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 28),
        ElevatedButton(
          key: const Key('send_lookup_btn'),
          onPressed: _isSearching ? null : _performRecipientLookup,
          child: _isSearching
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                )
              : const Text('Verify Recipient'),
        ),
      ],
    );
  }

  Widget _buildStep1AmountAndAccounts(
    bool isDark,
    AsyncValue<List<BankAccountModel>> accountsAsync,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Recipient Card
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.primaryGreen.withOpacity(0.08),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.primaryGreen.withOpacity(0.3)),
          ),
          child: Row(
            children: [
              CircleAvatar(
                backgroundColor: AppColors.primaryGreen,
                child: Text(
                  (_lookupResult?.displayName?.isNotEmpty ?? false)
                      ? _lookupResult!.displayName![0]
                      : 'R',
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _lookupResult?.displayName ?? 'Verified Payee',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: isDark ? Colors.white : AppColors.textPrimaryLight,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Receiving: ${_selectedPayeeAccount?.bankDisplayName ?? "Default Bank"} (${_selectedPayeeAccount?.maskedNumber ?? "****"})',
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.verified_rounded, color: AppColors.primaryGreen, size: 20),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Payee Receiving Account Selector (if multiple)
        if ((_lookupResult?.accounts?.length ?? 0) > 1) ...[
          Text(
            'Recipient Receiving Account',
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: isDark ? Colors.white : AppColors.textPrimaryLight,
            ),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<EligibleAccountModel>(
            value: _selectedPayeeAccount,
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.account_balance_rounded),
            ),
            items: _lookupResult!.accounts!.map((acc) {
              return DropdownMenuItem(
                value: acc,
                child: Text('${acc.bankDisplayName} (${acc.maskedNumber})'),
              );
            }).toList(),
            onChanged: (val) {
              if (val != null) setState(() => _selectedPayeeAccount = val);
            },
          ),
          const SizedBox(height: 20),
        ],

        // Multi-bank debit account selection
        Text(
          'Pay From (Your Linked Account)',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: isDark ? Colors.white : AppColors.textPrimaryLight,
          ),
        ),
        const SizedBox(height: 8),
        accountsAsync.when(
          data: (accounts) {
            if (accounts.isEmpty) {
              return Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.warning.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text(
                  'No linked bank accounts found. Please link a bank account before initiating transfers.',
                  style: TextStyle(fontSize: 13, color: AppColors.warning),
                ),
              );
            }

            final currentSender = _selectedSenderAccount ??
                accounts.firstWhere((a) => a.isDefault, orElse: () => accounts.first);

            return DropdownButtonFormField<BankAccountModel>(
              key: const Key('send_sender_account_dropdown'),
              value: currentSender,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.account_balance_wallet_rounded),
              ),
              items: accounts.map((acc) {
                return DropdownMenuItem(
                  value: acc,
                  child: Text(
                    '${acc.institutionCode} (${acc.maskedNumber})${acc.isDefault ? " [Default]" : ""}',
                  ),
                );
              }).toList(),
              onChanged: (val) {
                if (val != null) setState(() => _selectedSenderAccount = val);
              },
            );
          },
          loading: () => const LinearProgressIndicator(),
          error: (e, st) => const Text('Could not load bank accounts'),
        ),
        const SizedBox(height: 20),

        // Amount Entry
        TextField(
          key: const Key('send_amount_field'),
          controller: _amountController,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(
            labelText: 'Amount (LKR)',
            hintText: '0.00',
            prefixIcon: Icon(Icons.currency_exchange_rounded),
          ),
        ),
        const SizedBox(height: 16),

        // Note/Reference
        TextField(
          key: const Key('send_note_field'),
          controller: _noteController,
          decoration: const InputDecoration(
            labelText: 'Reference / Note (Optional)',
            hintText: 'e.g. Dinner split, Rent',
            prefixIcon: Icon(Icons.notes_rounded),
          ),
        ),
        const SizedBox(height: 28),

        accountsAsync.maybeWhen(
          data: (accounts) => ElevatedButton(
            key: const Key('send_review_btn'),
            onPressed: (_isGettingQuote || _isSubmitting)
                ? null
                : () => _onReviewTransferPressed(accounts),
            child: _isGettingQuote
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                  )
                : const Text('Review Transfer'),
          ),
          orElse: () => const ElevatedButton(
            onPressed: null,
            child: Text('Loading accounts...'),
          ),
        ),
      ],
    );
  }
}

class _ConfirmationSheet extends StatelessWidget {
  final String payeeName;
  final String payeeBank;
  final String payeeMaskedAccount;
  final String senderBank;
  final String senderMaskedAccount;
  final double amount;
  final double fee;
  final double total;
  final String note;
  final VoidCallback onConfirmed;

  const _ConfirmationSheet({
    required this.payeeName,
    required this.payeeBank,
    required this.payeeMaskedAccount,
    required this.senderBank,
    required this.senderMaskedAccount,
    required this.amount,
    required this.fee,
    required this.total,
    required this.note,
    required this.onConfirmed,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: isDark ? AppColors.surfaceDark : Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: isDark ? Colors.white24 : Colors.black12,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Confirm Payment Details',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: isDark ? Colors.white : AppColors.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 20),

            _buildDetailRow('Recipient Name', payeeName, isDark),
            const Divider(height: 20),
            _buildDetailRow('Recipient Bank', payeeBank, isDark),
            const Divider(height: 20),
            _buildDetailRow('Receiving Account', payeeMaskedAccount, isDark, isMonospace: true),
            const Divider(height: 20),
            _buildDetailRow('From Account', '$senderBank ($senderMaskedAccount)', isDark),
            const Divider(height: 20),
            _buildDetailRow('Transfer Amount', 'LKR ${amount.toStringAsFixed(2)}', isDark),
            const Divider(height: 20),
            _buildDetailRow('Network Fee', 'LKR ${fee.toStringAsFixed(2)}', isDark),
            const Divider(height: 20),
            _buildDetailRow('Total Debit', 'LKR ${total.toStringAsFixed(2)}', isDark, isBold: true),
            const Divider(height: 20),
            _buildDetailRow('Reference / Note', note, isDark),
            const SizedBox(height: 28),

            ElevatedButton(
              key: const Key('confirmation_continue_btn'),
              onPressed: onConfirmed,
              child: const Text('Proceed to Authentication'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDetailRow(
    String label,
    String value,
    bool isDark, {
    bool isBold = false,
    bool isMonospace = false,
  }) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 13,
            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
          ),
        ),
        Text(
          value,
          style: TextStyle(
            fontSize: 13,
            fontWeight: isBold ? FontWeight.w800 : FontWeight.w600,
            fontFamily: isMonospace ? 'monospace' : null,
            color: isBold
                ? AppColors.primaryGreen
                : (isDark ? Colors.white : AppColors.textPrimaryLight),
          ),
        ),
      ],
    );
  }
}
