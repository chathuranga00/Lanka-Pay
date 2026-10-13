import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../../accounts/data/bank_account_service.dart';
import '../../cards/data/card_service.dart';
import '../data/bill_service.dart';
import '../domain/bill_models.dart';

class BillDetailsScreen extends ConsumerStatefulWidget {
  final BillerModel? biller;
  final SavedBillerModel? savedBiller;

  const BillDetailsScreen({
    super.key,
    this.biller,
    this.savedBiller,
  });

  @override
  ConsumerState<BillDetailsScreen> createState() => _BillDetailsScreenState();
}

class _BillDetailsScreenState extends ConsumerState<BillDetailsScreen> {
  final TextEditingController _refController = TextEditingController();
  final TextEditingController _amountController = TextEditingController();
  final TextEditingController _pinController = TextEditingController();
  final TextEditingController _nicknameController = TextEditingController();

  BillerModel? _biller;
  BillLookupModel? _lookupResult;
  bool _isLookingUp = false;
  String? _lookupError;

  bool _isPaying = false;
  String? _payError;

  // Funding source: type = 'ACCOUNT' or 'CARD', id = int
  String _fundingType = 'ACCOUNT';
  int? _fundingId;

  bool _saveBiller = false;

  @override
  void initState() {
    super.initState();
    _biller = widget.biller;

    if (widget.savedBiller != null) {
      _refController.text = widget.savedBiller!.maskedReference;
      _nicknameController.text = widget.savedBiller!.nickname;
      _saveBiller = false; // Already saved
    }

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _resolveBillerAndFunding();
    });
  }

  Future<void> _resolveBillerAndFunding() async {
    if (_biller == null && widget.savedBiller != null) {
      try {
        final b = await ref.read(billServiceProvider).getBiller(widget.savedBiller!.billerId);
        if (mounted) setState(() => _biller = b);
      } catch (_) {
        // Fallback biller model from saved biller
        if (mounted) {
          setState(() {
            _biller = BillerModel(
              id: widget.savedBiller!.billerId,
              name: widget.savedBiller!.billerName,
              category: widget.savedBiller!.category,
              referenceLabel: 'Account Number',
              serviceFee: 30.0,
              partialPayAllowed: false,
              requiresLookup: true,
            );
          });
        }
      }
    }

    // Default funding source to first account if available
    final accounts = ref.read(bankAccountsProvider).value ?? [];
    if (accounts.isNotEmpty && _fundingId == null) {
      final defaultAcc = accounts.firstWhere(
        (a) => a.isDefault,
        orElse: () => accounts.first,
      );
      setState(() {
        _fundingType = 'ACCOUNT';
        _fundingId = defaultAcc.id;
      });
    }
  }

  @override
  void dispose() {
    _refController.dispose();
    _amountController.dispose();
    _pinController.dispose();
    _nicknameController.dispose();
    super.dispose();
  }

  Future<void> _performLookup() async {
    final refText = _refController.text.trim();
    if (refText.isEmpty) {
      setState(() => _lookupError = 'Please enter a customer reference.');
      return;
    }

    final billerId = _biller?.id ?? widget.savedBiller?.billerId;
    if (billerId == null) return;

    setState(() {
      _isLookingUp = true;
      _lookupError = null;
    });

    try {
      final result = await ref.read(billServiceProvider).lookupBill(
            billerId: billerId,
            customerReference: refText,
          );
      if (!mounted) return;

      setState(() {
        _isLookingUp = false;
        _lookupResult = result;
        if (result.amountDue > 0) {
          _amountController.text = result.amountDue.toStringAsFixed(2);
        }
      });
    } on DioException catch (e) {
      if (!mounted) return;
      setState(() {
        _isLookingUp = false;
        if (e.response?.data is Map) {
          final data = Map<String, dynamic>.from(e.response!.data as Map);
          _lookupError = data['message'] as String? ?? 'Invalid customer reference.';
        } else {
          _lookupError = 'Could not lookup bill. Please check reference.';
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLookingUp = false;
        _lookupError = 'Lookup failed: $e';
      });
    }
  }

  Future<void> _executePayment() async {
    final billerId = _biller?.id ?? widget.savedBiller?.billerId;
    final refText = _refController.text.trim();
    final amountText = _amountController.text.trim();
    final pin = _pinController.text.trim();

    if (billerId == null) return;

    if (refText.isEmpty) {
      setState(() => _payError = 'Customer reference is required.');
      return;
    }

    final amount = double.tryParse(amountText);
    if (amount == null || amount <= 0) {
      setState(() => _payError = 'Please enter a valid payment amount.');
      return;
    }

    if (_fundingId == null) {
      setState(() => _payError = 'Please select a payment funding source.');
      return;
    }

    if (pin.isEmpty || pin.length < 4) {
      setState(() => _payError = 'Please enter your 4 or 6-digit transaction PIN.');
      return;
    }

    setState(() {
      _isPaying = true;
      _payError = null;
    });

    try {
      final receipt = await ref.read(billServiceProvider).payBill(
            billerId: billerId,
            customerReference: refText,
            amount: amount,
            fundingType: _fundingType,
            fundingId: _fundingId!,
            pin: pin,
            saveBiller: _saveBiller,
            billerNickname: _saveBiller ? _nicknameController.text.trim() : null,
          );

      if (!mounted) return;
      setState(() => _isPaying = false);

      // Refresh saved billers if saved
      if (_saveBiller) {
        ref.read(savedBillersProvider.notifier).refresh();
      }

      // Navigate to distinct result screen
      context.pushReplacement('/bills/result', extra: {'receipt': receipt});
    } on DioException catch (e) {
      if (!mounted) return;
      setState(() {
        _isPaying = false;
        if (e.response?.data is Map) {
          final data = Map<String, dynamic>.from(e.response!.data as Map);
          _payError = data['message'] as String? ?? 'Payment failed. Please check details.';
        } else {
          _payError = 'Payment request failed: ${e.message}';
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isPaying = false;
        _payError = 'Payment failed: $e';
      });
    }
  }

  void _showFundingSourcePicker() {
    final accounts = ref.read(bankAccountsProvider).value ?? [];
    final cards = ref.read(cardsProvider).value ?? [];
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? AppTokens.cardDark : Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Select Funding Source',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 16),
            if (accounts.isNotEmpty) ...[
              Text(
                'Bank Accounts',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 8),
              ...accounts.map((acc) {
                final isSelected = _fundingType == 'ACCOUNT' && _fundingId == acc.id;
                return ListTile(
                  leading: const Icon(Icons.account_balance_rounded, color: AppTokens.primaryBlue),
                  title: Text(acc.accountName.isNotEmpty ? acc.accountName : acc.institutionCode),
                  subtitle: Text(acc.maskedNumber),
                  trailing: isSelected
                      ? const Icon(Icons.check_circle_rounded, color: AppTokens.primaryBlue)
                      : null,
                  onTap: () {
                    setState(() {
                      _fundingType = 'ACCOUNT';
                      _fundingId = acc.id;
                    });
                    Navigator.pop(ctx);
                  },
                );
              }),
              const SizedBox(height: 16),
            ],
            if (cards.isNotEmpty) ...[
              Text(
                'Payment Cards',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 8),
              ...cards.map((card) {
                final isSelected = _fundingType == 'CARD' && _fundingId == card.id;
                final cardName = (card.nickname != null && card.nickname!.isNotEmpty)
                    ? card.nickname!
                    : '${card.brandLabel} Card';
                return ListTile(
                  leading: const Icon(Icons.credit_card_rounded, color: AppTokens.primaryViolet),
                  title: Text(cardName),
                  subtitle: Text(card.maskedPan),
                  trailing: isSelected
                      ? const Icon(Icons.check_circle_rounded, color: AppTokens.primaryViolet)
                      : null,
                  onTap: () {
                    setState(() {
                      _fundingType = 'CARD';
                      _fundingId = card.id;
                    });
                    Navigator.pop(ctx);
                  },
                );
              }),
            ],
            if (accounts.isEmpty && cards.isEmpty) ...[
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: Text('No active bank accounts or cards found.')),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _getFundingLabel() {
    if (_fundingType == 'ACCOUNT') {
      final accounts = ref.read(bankAccountsProvider).value ?? [];
      final acc = accounts.where((a) => a.id == _fundingId).firstOrNull;
      if (acc != null) {
        final title = acc.accountName.isNotEmpty ? acc.accountName : acc.institutionCode;
        return '$title (${acc.maskedNumber})';
      }
      return 'Bank Account';
    } else {
      final cards = ref.read(cardsProvider).value ?? [];
      final card = cards.where((c) => c.id == _fundingId).firstOrNull;
      if (card != null) {
        final name = (card.nickname != null && card.nickname!.isNotEmpty)
            ? card.nickname!
            : card.brandLabel;
        return '$name (${card.maskedPan})';
      }
      return 'Payment Card';
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final biller = _biller;
    final fee = biller?.serviceFee ?? 30.0;
    final amount = double.tryParse(_amountController.text) ?? 0.0;
    final total = amount > 0 ? (amount + fee) : 0.0;

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: Text(
          biller?.name ?? 'Bill Details',
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Biller Header Card
            if (biller != null)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: isDark ? AppTokens.cardDark : Colors.white,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                  ),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: biller.category.color.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Icon(biller.category.icon, color: biller.category.color, size: 26),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            biller.name,
                            style: TextStyle(
                              fontWeight: FontWeight.w700,
                              fontSize: 16,
                              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${biller.category.displayName} • Service Fee LKR ${biller.serviceFee.toStringAsFixed(2)}',
                            style: TextStyle(
                              fontSize: 12,
                              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 20),

            // Customer Reference Section
            Text(
              biller?.referenceLabel ?? 'Customer Reference Number',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    key: AppTestKeys.billReference,
                    controller: _refController,
                    decoration: InputDecoration(
                      hintText: 'Enter ${biller?.referenceLabel.toLowerCase() ?? "reference number"}',
                      filled: true,
                      fillColor: isDark ? AppTokens.searchFieldBgDark : AppTokens.searchFieldBgLight,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                FilledButton.tonal(
                  onPressed: _isLookingUp ? null : _performLookup,
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: _isLookingUp
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Lookup'),
                ),
              ],
            ),
            if (_lookupError != null) ...[
              const SizedBox(height: 8),
              Text(
                _lookupError!,
                style: const TextStyle(color: AppTokens.error, fontSize: 13),
              ),
            ],
            const SizedBox(height: 16),

            // Lookup Result Card
            if (_lookupResult != null) _buildLookupBanner(context, _lookupResult!, isDark),

            const SizedBox(height: 20),

            // Amount Field
            Text(
              'Amount (LKR)',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              key: AppTestKeys.billAmountField,
              controller: _amountController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                prefixText: 'LKR ',
                prefixStyle: TextStyle(
                  fontWeight: FontWeight.w700,
                  color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                ),
                hintText: '0.00',
                filled: true,
                fillColor: isDark ? AppTokens.searchFieldBgDark : AppTokens.searchFieldBgLight,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              ),
            ),
            const SizedBox(height: 20),

            // Funding Source Section (Pay From)
            Text(
              'Pay From',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 8),
            InkWell(
              onTap: _showFundingSourcePicker,
              borderRadius: BorderRadius.circular(14),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                decoration: BoxDecoration(
                  color: isDark ? AppTokens.cardDark : Colors.white,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      _fundingType == 'ACCOUNT'
                          ? Icons.account_balance_rounded
                          : Icons.credit_card_rounded,
                      color: _fundingType == 'ACCOUNT'
                          ? AppTokens.primaryBlue
                          : AppTokens.primaryViolet,
                      size: 22,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        _getFundingLabel(),
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                        ),
                      ),
                    ),
                    Icon(
                      Icons.arrow_drop_down_rounded,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Step-up Auth PIN Field
            Text(
              'Step-up PIN',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              key: AppTestKeys.billPinField,
              controller: _pinController,
              obscureText: true,
              keyboardType: TextInputType.number,
              maxLength: 6,
              decoration: InputDecoration(
                counterText: '',
                hintText: 'Enter your 4 or 6-digit transaction PIN',
                prefixIcon: const Icon(Icons.lock_outline_rounded),
                filled: true,
                fillColor: isDark ? AppTokens.searchFieldBgDark : AppTokens.searchFieldBgLight,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              ),
            ),
            const SizedBox(height: 16),

            // Save Biller Checkbox (only if not already a saved biller)
            if (widget.savedBiller == null) ...[
              CheckboxListTile(
                key: AppTestKeys.billSaveCheckbox,
                value: _saveBiller,
                onChanged: (val) => setState(() => _saveBiller = val ?? false),
                title: const Text('Save this biller for future payments'),
                contentPadding: EdgeInsets.zero,
                controlAffinity: ListTileControlAffinity.leading,
              ),
              if (_saveBiller) ...[
                const SizedBox(height: 6),
                TextField(
                  key: AppTestKeys.billNicknameField,
                  controller: _nicknameController,
                  decoration: InputDecoration(
                    labelText: 'Biller Nickname',
                    hintText: 'e.g. My Apartment Meter',
                    filled: true,
                    fillColor: isDark ? AppTokens.searchFieldBgDark : AppTokens.searchFieldBgLight,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  ),
                ),
              ],
              const SizedBox(height: 16),
            ],

            // Bill Confirmation Breakdown Card
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: isDark ? AppTokens.cardDark : const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: isDark ? AppTokens.borderDark : const Color(0xFFE2E8F0),
                ),
              ),
              child: Column(
                children: [
                  _buildSummaryRow('Amount Due', 'LKR ${amount.toStringAsFixed(2)}', isDark),
                  const SizedBox(height: 8),
                  _buildSummaryRow('Service Fee', 'LKR ${fee.toStringAsFixed(2)}', isDark),
                  const Divider(height: 20),
                  _buildSummaryRow(
                    'Total Payable',
                    'LKR ${total.toStringAsFixed(2)}',
                    isDark,
                    isTotal: true,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            if (_payError != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppTokens.error.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppTokens.error.withOpacity(0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline_rounded, color: AppTokens.error, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _payError!,
                        style: const TextStyle(color: AppTokens.error, fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Pay Button
            SizedBox(
              width: double.infinity,
              height: 52,
              child: FilledButton(
                key: AppTestKeys.billPay,
                onPressed: _isPaying ? null : _executePayment,
                style: FilledButton.styleFrom(
                  backgroundColor: AppTokens.primaryBlue,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                ),
                child: _isPaying
                    ? const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                      )
                    : Text(
                        'Pay LKR ${total.toStringAsFixed(2)}',
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
              ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildLookupBanner(BuildContext context, BillLookupModel result, bool isDark) {
    Color bannerBg;
    Color iconColor;
    IconData icon;
    String statusTitle;

    if (result.isNoBillDue) {
      bannerBg = Colors.green.withOpacity(0.1);
      iconColor = Colors.green;
      icon = Icons.check_circle_outline_rounded;
      statusTitle = 'No Bill Due';
    } else if (result.isAmountChanged) {
      bannerBg = Colors.orange.withOpacity(0.1);
      iconColor = Colors.orange;
      icon = Icons.info_outline_rounded;
      statusTitle = 'Bill Amount Updated';
    } else if (result.isInvalidReference) {
      bannerBg = AppTokens.error.withOpacity(0.1);
      iconColor = AppTokens.error;
      icon = Icons.cancel_outlined;
      statusTitle = 'Invalid Reference';
    } else {
      bannerBg = AppTokens.primaryBlue.withOpacity(0.08);
      iconColor = AppTokens.primaryBlue;
      icon = Icons.receipt_long_rounded;
      statusTitle = 'Bill Found';
    }

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: bannerBg,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: iconColor.withOpacity(0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: iconColor, size: 20),
              const SizedBox(width: 8),
              Text(
                statusTitle,
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  fontSize: 14,
                  color: iconColor,
                ),
              ),
              const Spacer(),
              if (result.dueDate != null)
                Text(
                  'Due: ${result.dueDate}',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: isDark ? Colors.white70 : Colors.black87,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          if (result.customerName.isNotEmpty)
            Text(
              'Customer: ${result.customerName}',
              style: TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 13,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
          if (result.statusMessage.isNotEmpty)
            Text(
              result.statusMessage,
              style: TextStyle(
                fontSize: 12,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildSummaryRow(String label, String value, bool isDark, {bool isTotal = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: isTotal ? 15 : 13,
            fontWeight: isTotal ? FontWeight.w700 : FontWeight.w500,
            color: isDark ? (isTotal ? Colors.white : Colors.white70) : (isTotal ? Colors.black : Colors.black54),
          ),
        ),
        Text(
          value,
          style: TextStyle(
            fontSize: isTotal ? 16 : 13,
            fontWeight: isTotal ? FontWeight.w800 : FontWeight.w600,
            color: isTotal
                ? AppTokens.primaryBlue
                : (isDark ? Colors.white : AppTokens.textPrimaryLight),
          ),
        ),
      ],
    );
  }
}
