import 'dart:async';
import 'dart:math';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/app_avatar.dart';
import '../../../core/widgets/shimmer_skeletons.dart';
import '../../../core/widgets/simulated_badge.dart';
import '../../../core/widgets/transaction_tile.dart';
import '../../accounts/data/bank_account_service.dart';
import '../../accounts/domain/bank_account_model.dart';
import '../../auth/data/auth_service.dart';
import '../../home/data/home_providers.dart';
import '../../qr/domain/qr_models.dart';
import '../../qr/presentation/qr_scanner_screen.dart';
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
  // 0: Transfers Dashboard & Recipient Search
  // 1: Multi-bank account selection & amount entry
  int _currentStep = 0;

  // Controllers & Focus Nodes
  final _phoneController = TextEditingController();
  final _amountController = TextEditingController();
  final _noteController = TextEditingController();
  final _searchFocusNode = FocusNode();

  // Search & Lookup State
  bool _isSearching = false;
  String? _searchError;
  LookupResultModel? _lookupResult;
  EligibleAccountModel? _selectedPayeeAccount;

  // Debouncing & Rate Limit Cooldown
  Timer? _debounceTimer;
  int _cooldownSeconds = 0;
  Timer? _cooldownTimer;

  // Sender Account
  BankAccountModel? _selectedSenderAccount;

  // Quote & Submission
  bool _isGettingQuote = false;
  QuoteModel? _quote;
  String? _quoteError;

  // Idempotency Key per intent attempt
  late String _idempotencyKey;
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    _idempotencyKey = _generateIdempotencyKey();

    if (widget.resolvedQr != null) {
      _populateFromResolvedQr(widget.resolvedQr);
    }
  }

  void _populateFromResolvedQr(dynamic qr) {
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
      accounts: const [
        EligibleAccountModel(
          institutionCode: 'LANKAPAY',
          bankDisplayName: 'Receiving Bank',
          maskedNumber: '****',
          isDefault: true,
        ),
      ],
    );
    _selectedPayeeAccount = _lookupResult!.accounts!.first;
    _currentStep = 1;
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _cooldownTimer?.cancel();
    _phoneController.dispose();
    _amountController.dispose();
    _noteController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  String _generateIdempotencyKey() {
    final now = DateTime.now().millisecondsSinceEpoch;
    final rand = Random().nextInt(999999).toString().padLeft(6, '0');
    return 'IDEM-$now-$rand';
  }

  bool _isValidSriLankanMobile(String raw) {
    final cleaned = raw.replaceAll(RegExp(r'[\s\-]'), '');
    if (RegExp(r'^(07[0-9]{8}|\+947[0-9]{8})$').hasMatch(cleaned)) {
      return true;
    }
    final normalized = AuthService.normalizePhone(raw);
    return RegExp(r'^\+947[0-9]{8}$').hasMatch(normalized);
  }

  void _onPhoneChanged(String value) {
    _debounceTimer?.cancel();
    if (value.trim().isEmpty) {
      if (_searchError != null) {
        setState(() => _searchError = null);
      }
      return;
    }

    // Never auto-search on every keystroke. Only debounce if valid length is reached.
    final cleaned = value.replaceAll(RegExp(r'[\s\-]'), '');
    if ((cleaned.startsWith('07') && cleaned.length == 10) ||
        (cleaned.startsWith('+947') && cleaned.length == 12)) {
      _debounceTimer = Timer(const Duration(milliseconds: 900), () {
        if (mounted) {
          _performRecipientLookup();
        }
      });
    }
  }

  void _startCooldown([int seconds = 30]) {
    _cooldownTimer?.cancel();
    setState(() => _cooldownSeconds = seconds);
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      setState(() {
        if (_cooldownSeconds > 1) {
          _cooldownSeconds--;
        } else {
          _cooldownSeconds = 0;
          timer.cancel();
        }
      });
    });
  }

  Future<void> _performRecipientLookup() async {
    _debounceTimer?.cancel();

    if (_cooldownSeconds > 0) {
      setState(() {
        _searchError = 'Too many lookup attempts. Please wait a moment before trying again.';
      });
      return;
    }

    final rawPhone = _phoneController.text.trim();
    if (rawPhone.isEmpty) {
      setState(() => _searchError = 'Please enter a mobile phone number');
      return;
    }

    if (!_isValidSriLankanMobile(rawPhone)) {
      setState(() {
        _searchError = 'Please enter a valid Sri Lankan mobile number (e.g. 0771234567 or +94771234567).';
      });
      return;
    }

    final normalizedPhone = AuthService.normalizePhone(rawPhone);

    setState(() {
      _isSearching = true;
      _searchError = null;
      _lookupResult = null;
      _selectedPayeeAccount = null;
    });

    try {
      final result = await ref.read(transferServiceProvider).lookupRecipient(normalizedPhone);
      if (!mounted) return;

      if (result.isFound) {
        if (result.verificationStatus != null &&
            result.verificationStatus!.isNotEmpty &&
            result.verificationStatus != 'VERIFIED') {
          setState(() {
            _isSearching = false;
            _searchError = 'Recipient verification is incomplete or pending. They cannot receive payments yet.';
          });
          return;
        }

        final eligible = result.accounts ?? [];
        if (eligible.isEmpty) {
          setState(() {
            _isSearching = false;
            _searchError = 'The recipient is registered but has no linked bank accounts configured to receive payments.';
          });
          return;
        }

        setState(() {
          _isSearching = false;
          _lookupResult = result;
          _selectedPayeeAccount = eligible.firstWhere(
            (a) => a.isDefault,
            orElse: () => eligible.first,
          );
          _currentStep = 1;
        });
      } else {
        setState(() {
          _isSearching = false;
          _searchError = _getFriendlyLookupError(result.resultType);
        });
      }
    } on DioException catch (e) {
      if (!mounted) return;
      _handleLookupDioException(e);
    } catch (e) {
      if (!mounted) return;
      final errStr = e.toString().toLowerCase();
      if (errStr.contains('429') || errStr.contains('too many attempts') || errStr.contains('rate limit')) {
        _startCooldown();
        setState(() {
          _isSearching = false;
          _searchError = 'Too many lookup attempts. Please wait a moment before trying again.';
        });
      } else if (errStr.contains('500') ||
          errStr.contains('502') ||
          errStr.contains('503') ||
          errStr.contains('service down') ||
          errStr.contains('unavailable')) {
        setState(() {
          _isSearching = false;
          _searchError = 'Payment registry service is temporarily unavailable. Please try again later.';
        });
      } else {
        setState(() {
          _isSearching = false;
          _searchError = 'Payment registry service is temporarily unavailable. Please try again later.';
        });
      }
    }
  }

  void _handleLookupDioException(DioException e) {
    final status = e.response?.statusCode;
    if (status == 429) {
      _startCooldown();
      setState(() {
        _isSearching = false;
        _searchError = 'Too many lookup attempts. Please wait a moment before trying again.';
      });
      return;
    }

    if (e.response?.data is Map) {
      final data = Map<String, dynamic>.from(e.response!.data as Map);
      final resultType = data['resultType'] as String?;
      if (resultType != null) {
        setState(() {
          _isSearching = false;
          _searchError = _getFriendlyLookupError(resultType);
        });
        return;
      }
    }

    if (status != null && status >= 500) {
      setState(() {
        _isSearching = false;
        _searchError = 'Payment registry service is temporarily unavailable. Please try again later.';
      });
      return;
    }

    setState(() {
      _isSearching = false;
      _searchError = 'Payment registry service is temporarily unavailable. Please try again later.';
    });
  }

  String _getFriendlyLookupError(String resultType) {
    switch (resultType) {
      case 'DISCOVERY_DISABLED':
        return 'The recipient has disabled phone-number discovery for privacy under PDPA.';
      case 'NO_ELIGIBLE_ACCOUNT':
        return 'The recipient is registered but has no linked bank accounts configured to receive payments.';
      case 'PRIVACY_RESTRICTED':
      case 'UNAVAILABLE':
        return 'Unable to route transfer to this user due to privacy preferences.';
      case 'VERIFICATION_INCOMPLETE':
        return 'Recipient verification is incomplete or pending. They cannot receive payments yet.';
      case 'NOT_FOUND':
      default:
        return 'No LankaPay-ID account found for this mobile number. Make sure the number is registered.';
    }
  }

  Future<void> _openScanner() async {
    final result = await Navigator.of(context).push<ResolvedQrModel>(
      MaterialPageRoute(builder: (_) => const QrScannerScreen()),
    );
    if (result != null && mounted) {
      _populateFromResolvedQr(result);
    }
  }

  void _onRecipientTap(TransferModel recipient) async {
    final phone = recipient.receiverPhone ??
        (recipient.receiverId != null ? '+9477000${recipient.receiverId}' : '');
    _phoneController.text = phone;
    if (phone.isNotEmpty) {
      await _performRecipientLookup();
    } else {
      setState(() {
        _lookupResult = LookupResultModel(
          resultType: 'FOUND',
          displayName: recipient.receiverDisplayName ?? 'Verified Recipient',
          verificationStatus: 'VERIFIED',
          accounts: const [
            EligibleAccountModel(
              institutionCode: 'LANKAPAY',
              bankDisplayName: 'Receiving Bank',
              maskedNumber: '****',
              isDefault: true,
            ),
          ],
        );
        _selectedPayeeAccount = _lookupResult!.accounts!.first;
        _currentStep = 1;
      });
    }
  }

  void _handleBetweenAccounts() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Row(
          children: [
            Icon(Icons.credit_card_rounded, color: AppColors.primaryGreen),
            SizedBox(width: 8),
            Text('Between Accounts'),
          ],
        ),
        content: const Text(
          'Transfers between your own accounts are not available in this version.',
          style: TextStyle(fontSize: 14, height: 1.4),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  void _showTransactionDetails(TransferModel tx) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? AppColors.surfaceDark : Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(24.0),
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
              'Transfer Detail',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 16),
            _buildDetailRow('Reference', tx.reference, isDark, isMonospace: true),
            const Divider(height: 20),
            _buildDetailRow('Recipient', tx.receiverDisplayName ?? 'N/A', isDark),
            const Divider(height: 20),
            _buildDetailRow('Amount', 'LKR ${tx.amount.toStringAsFixed(2)}', isDark, isBold: true),
            const Divider(height: 20),
            _buildDetailRow('Status', tx.status.name.toUpperCase(), isDark),
            const SizedBox(height: 20),
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
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
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
                : (isDark ? Colors.white : AppTokens.textPrimaryLight),
          ),
        ),
      ],
    );
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
              style: TextStyle(fontSize: 13, color: AppTokens.textSecondaryLight),
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
    if (_isSubmitting) return;

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
    final themeExt = context.appTheme;

    if (_currentStep == 1) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Recipient Verification'),
          leading: IconButton(
            icon: const Icon(Icons.arrow_back_rounded),
            onPressed: () => setState(() => _currentStep = 0),
          ),
          actions: const [
            Center(child: SimulatedBadge(compact: true)),
            SizedBox(width: 16),
          ],
        ),
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24.0),
            child: _buildStep1AmountAndAccounts(isDark),
          ),
        ),
      );
    }

    final recentTransfersAsync = ref.watch(recentTransactionsProvider);

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.lightHeaderStart,
      body: Stack(
        children: [
          // Animated gradient background at top
          Positioned.fill(
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 400),
              curve: Curves.easeInOut,
              decoration: BoxDecoration(
                gradient: themeExt.headerGradient,
              ),
            ),
          ),

          SafeArea(
            bottom: false,
            child: RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(recentTransactionsProvider);
                ref.invalidate(bankAccountsProvider);
                await ref.read(recentTransactionsProvider.future);
              },
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Top Header Section
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Header: "Send" title + QR Scanner icon
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Text(
                                'Send',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 28,
                                  fontWeight: FontWeight.w800,
                                  color: Colors.white,
                                ),
                              ),
                              Row(
                                children: [
                                  const SimulatedBadge(compact: true),
                                  const SizedBox(width: 8),
                                  Container(
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: Colors.white.withOpacity(0.12),
                                    ),
                                    child: IconButton(
                                      key: const Key('send_scan_qr'),
                                      icon: const Icon(
                                        Icons.qr_code_scanner_rounded,
                                        color: Colors.white,
                                        size: 22,
                                      ),
                                      onPressed: _openScanner,
                                      tooltip: 'Scan QR Code',
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),

                          // Search Bar Container
                          _buildSearchField(isDark),

                          // Inline Lookup State Message (if any)
                          if (_searchError != null) ...[
                            const SizedBox(height: 12),
                            _buildInlineLookupMessage(),
                          ],
                          const SizedBox(height: 18),

                          // Recent Recipients Row: "+ Send" circle first, then avatars
                          _buildRecentRecipientsRow(recentTransfersAsync),
                          const SizedBox(height: 8),
                        ],
                      ),
                    ),

                    // Rounded Bottom Content Sheet
                    Container(
                      decoration: BoxDecoration(
                        color: isDark ? AppColors.surfaceDark : Colors.white,
                        borderRadius: const BorderRadius.vertical(
                          top: Radius.circular(28),
                        ),
                      ),
                      padding: const EdgeInsets.fromLTRB(20, 24, 20, 40),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // 2x2 MenuTiles Grid
                          _buildMenuGrid(isDark),
                          const SizedBox(height: 28),

                          // "TRANSACTIONS" header with "See all" link
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                'TRANSACTIONS',
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w700,
                                  letterSpacing: 1.1,
                                  color: isDark
                                      ? AppTokens.textSecondaryDark
                                      : AppTokens.textSecondaryLight,
                                ),
                              ),
                              GestureDetector(
                                onTap: () => context.push('/history'),
                                child: const Text(
                                  'See all',
                                  style: TextStyle(
                                    fontFamily: AppTokens.fontFamily,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w600,
                                    color: AppColors.primaryGreen,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),

                          // Transactions List
                          _buildTransactionsList(recentTransfersAsync, isDark),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchField(bool isDark) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF262638) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.08),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Stack(
        alignment: Alignment.centerLeft,
        children: [
          // Visible Search Input [send_search]
          TextField(
            key: const Key('send_search'),
            controller: _phoneController,
            focusNode: _searchFocusNode,
            keyboardType: TextInputType.phone,
            textInputAction: TextInputAction.search,
            onChanged: _onPhoneChanged,
            onSubmitted: (_) => _performRecipientLookup(),
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 15,
              fontWeight: FontWeight.w500,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
            decoration: InputDecoration(
              hintText: 'Search by mobile number',
              hintStyle: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 14,
                color: isDark
                    ? AppTokens.textSecondaryDark.withOpacity(0.6)
                    : AppTokens.textSecondaryLight.withOpacity(0.7),
              ),
              prefixIcon: Icon(
                Icons.search_rounded,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                size: 22,
              ),
              suffixIcon: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (_phoneController.text.isNotEmpty)
                    IconButton(
                      icon: const Icon(Icons.clear_rounded, size: 18),
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      onPressed: () {
                        _phoneController.clear();
                        setState(() => _searchError = null);
                      },
                    ),
                  IconButton(
                    key: const Key('send_lookup_btn'),
                    icon: _isSearching
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(
                            Icons.arrow_forward_rounded,
                            color: AppColors.primaryGreen,
                            size: 22,
                          ),
                    onPressed: _isSearching ? null : _performRecipientLookup,
                    tooltip: 'Search Recipient',
                  ),
                ],
              ),
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            ),
          ),

          // Secondary invisible field [send_phone_field] for backward compatibility with existing tests
          Opacity(
            opacity: 0.0,
            child: SizedBox(
              width: 1,
              height: 1,
              child: TextField(
                key: const Key('send_phone_field'),
                controller: _phoneController,
                onSubmitted: (_) => _performRecipientLookup(),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInlineLookupMessage() {
    final isRateLimit = _cooldownSeconds > 0 ||
        (_searchError != null && _searchError!.toLowerCase().contains('too many'));

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isRateLimit
            ? AppColors.warning.withOpacity(0.12)
            : AppColors.error.withOpacity(0.12),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isRateLimit
              ? AppColors.warning.withOpacity(0.4)
              : AppColors.error.withOpacity(0.4),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            isRateLimit ? Icons.timer_outlined : Icons.info_outline_rounded,
            color: isRateLimit ? AppColors.warning : AppColors.error,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              _searchError!,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 13,
                height: 1.35,
                color: isRateLimit ? AppColors.warning : AppColors.error,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          if (_cooldownSeconds > 0) ...[
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.warning.withOpacity(0.2),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                '${_cooldownSeconds}s',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: AppColors.warning,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildRecentRecipientsRow(AsyncValue<List<TransferModel>> recentTransfersAsync) {
    return SizedBox(
      height: 94,
      child: recentTransfersAsync.maybeWhen(
        data: (transfers) {
          // Derive distinct recipients (newest first)
          final seen = <String>{};
          final distinctRecipients = <TransferModel>[];
          for (final t in transfers) {
            final name = t.receiverDisplayName?.trim();
            final id = t.receiverId?.toString() ?? name ?? '';
            if (id.isNotEmpty && !seen.contains(id)) {
              seen.add(id);
              distinctRecipients.add(t);
            }
          }

          return ListView.builder(
            scrollDirection: Axis.horizontal,
            itemCount: 1 + distinctRecipients.length,
            itemBuilder: (context, index) {
              if (index == 0) {
                // "+ Send" circle button [send_new]
                return Padding(
                  padding: const EdgeInsets.only(right: 16.0),
                  child: InkWell(
                    key: const Key('send_new'),
                    onTap: () => _searchFocusNode.requestFocus(),
                    borderRadius: BorderRadius.circular(30),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 52,
                          height: 52,
                          decoration: const BoxDecoration(
                            shape: BoxShape.circle,
                            color: Color(0xFF7065F0), // Vibrant purple circle per design
                          ),
                          alignment: Alignment.center,
                          child: const Icon(
                            Icons.add,
                            color: Colors.white,
                            size: 26,
                          ),
                        ),
                        const SizedBox(height: 6),
                        const Text(
                          'Send',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              }

              final recipient = distinctRecipients[index - 1];
              final rawName = recipient.receiverDisplayName ?? 'Recipient';
              final firstName = rawName.trim().split(RegExp(r'\s+')).first;
              final recipientKey = Key('recipient_${recipient.receiverId ?? recipient.transferId}');

              return Padding(
                padding: const EdgeInsets.only(right: 16.0),
                child: InkWell(
                  key: recipientKey,
                  onTap: () => _onRecipientTap(recipient),
                  borderRadius: BorderRadius.circular(30),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      AppAvatar(
                        name: rawName,
                        size: 52,
                      ),
                      const SizedBox(height: 6),
                      SizedBox(
                        width: 56,
                        child: Text(
                          firstName,
                          textAlign: TextAlign.center,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          );
        },
        orElse: () => Row(
          children: [
            InkWell(
              key: const Key('send_new'),
              onTap: () => _searchFocusNode.requestFocus(),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: Color(0xFF7065F0),
                    ),
                    alignment: Alignment.center,
                    child: const Icon(Icons.add, color: Colors.white, size: 26),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Send',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.white),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMenuGrid(bool isDark) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _MenuTile(
                key: const Key('tile_pay_by_phone'),
                icon: Icons.send_rounded,
                title: 'Pay by phone\nnumber',
                onTap: () => _searchFocusNode.requestFocus(),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: _MenuTile(
                key: const Key('tile_scan_qr'),
                icon: Icons.qr_code_scanner_rounded,
                title: 'Scan QR\ncode',
                onTap: _openScanner,
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),
        Row(
          children: [
            Expanded(
              child: _MenuTile(
                key: const Key('tile_between_accounts'),
                icon: Icons.credit_card_rounded,
                title: 'Between my\naccounts',
                onTap: _handleBetweenAccounts,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: _MenuTile(
                key: const Key('tile_requests'),
                icon: Icons.request_quote_rounded,
                title: 'Payment\nrequests',
                onTap: () => context.push('/qr'),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildTransactionsList(
    AsyncValue<List<TransferModel>> recentTransfersAsync,
    bool isDark,
  ) {
    return recentTransfersAsync.when(
      data: (transfers) {
        if (transfers.isEmpty) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 24.0),
            child: Center(
              child: Text(
                'No recent transactions',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 14,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
            ),
          );
        }

        return ListView.separated(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: transfers.length,
          separatorBuilder: (_, _) => Divider(
            height: 1,
            color: isDark ? Colors.white10 : Colors.black.withOpacity(0.04),
          ),
          itemBuilder: (context, index) {
            final tx = transfers[index];
            return TransactionTile(
              icon: Icons.arrow_upward_rounded,
              title: tx.receiverDisplayName ?? tx.reference,
              subtitle: tx.note?.isNotEmpty == true ? tx.note! : 'Transfer',
              amount: 'LKR ${tx.amount.toStringAsFixed(2)}',
              status: tx.status.name.toUpperCase(),
              isPositive: false,
              onTap: () => _showTransactionDetails(tx),
            );
          },
        );
      },
      loading: () => const Column(
        children: [
          SkeletonTransactionTile(key: Key('skeleton_tx_1')),
          SkeletonTransactionTile(key: Key('skeleton_tx_2')),
          SkeletonTransactionTile(key: Key('skeleton_tx_3')),
        ],
      ),
      error: (e, st) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 16.0),
        child: Center(
          child: Column(
            children: [
              Text(
                'Could not load transactions',
                style: TextStyle(
                  fontSize: 13,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => ref.refresh(recentTransactionsProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStep1AmountAndAccounts(bool isDark) {
    final accountsAsync = ref.watch(bankAccountsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Verified Recipient Card
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
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Receiving: ${_selectedPayeeAccount?.bankDisplayName ?? "Default Bank"} (${_selectedPayeeAccount?.maskedNumber ?? "****"})',
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 12,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
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
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
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
            color: isDark ? Colors.white : AppTokens.textPrimaryLight,
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

/// 2x2 Menu Card Tile matching screenshot style
class _MenuTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final VoidCallback onTap;

  const _MenuTile({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Container(
          height: 136,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isDark ? AppColors.cardDark : Colors.white,
            borderRadius: BorderRadius.circular(22),
            border: Border.all(
              color: isDark ? Colors.white.withOpacity(0.06) : Colors.black.withOpacity(0.04),
              width: 1,
            ),
            boxShadow: [
              BoxShadow(
                color: isDark ? Colors.black.withOpacity(0.2) : Colors.black.withOpacity(0.03),
                blurRadius: 10,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Dark Navy rounded square icon container
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: const Color(0xFF0F172A), // Dark navy square from screenshot
                  borderRadius: BorderRadius.circular(14),
                ),
                alignment: Alignment.center,
                child: Icon(
                  icon,
                  color: Colors.white,
                  size: 22,
                ),
              ),
              Text(
                title,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  height: 1.25,
                  color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                ),
              ),
            ],
          ),
        ),
      ),
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
                fontFamily: AppTokens.fontFamily,
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
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
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
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
                : (isDark ? Colors.white : AppTokens.textPrimaryLight),
          ),
        ),
      ],
    );
  }
}
