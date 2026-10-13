import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/config/app_config.dart';
import '../../../../core/test_keys.dart';
import '../../../../core/theme/app_tokens.dart';
import '../../../../core/widgets/account_card.dart';
import '../../../../core/widgets/simulated_badge.dart';
import '../../../accounts/data/bank_account_service.dart';
import '../../data/card_service.dart';
import '../../domain/card_model.dart';
import '../../domain/card_validator.dart';
import '../widgets/step_up_pin_dialog.dart';
import '../widgets/test_cards_list_screen.dart';

/// 3-Screen Add Card Flow:
/// 1. Choose card type (Test card or Virtual card)
/// 2. Card entry form (Luhn-checked live, obscured CVV, dedicated un-logged Dio for vault)
/// 3. Bank OTP verification challenge with cooldown, attempts, and success state
class AddCardFlowScreen extends ConsumerStatefulWidget {
  const AddCardFlowScreen({super.key});

  @override
  ConsumerState<AddCardFlowScreen> createState() => _AddCardFlowScreenState();
}

class _AddCardFlowScreenState extends ConsumerState<AddCardFlowScreen> {
  int _currentStep = 0; // 0: Choose, 1: Form, 2: OTP, 3: Success

  // Form Controllers
  late TextEditingController _panController;
  late TextEditingController _expController;
  late TextEditingController _cvvController;
  late TextEditingController _holderController;
  late TextEditingController _nicknameController;
  late TextEditingController _otpController;

  bool _consentGiven = false;
  bool _isLuhnValid = false;
  bool _isLoading = false;
  String? _formError;
  String? _otpError;

  CardModel? _createdCard;

  // OTP Cooldown & Attempts
  Timer? _cooldownTimer;
  int _cooldownSeconds = 30;
  int _attemptsRemaining = 3;

  // Virtual Card Selection
  int? _selectedBankAccountId;

  @override
  void initState() {
    super.initState();
    _panController = TextEditingController();
    _expController = TextEditingController();
    _cvvController = TextEditingController();
    _holderController = TextEditingController();
    _nicknameController = TextEditingController();
    _otpController = TextEditingController();

    _panController.addListener(_validatePan);
  }

  @override
  void dispose() {
    _cooldownTimer?.cancel();
    _panController.removeListener(_validatePan);
    _panController.dispose();
    _expController.dispose();
    _cvvController.dispose();
    _holderController.dispose();
    _nicknameController.dispose();
    _otpController.dispose();
    super.dispose();
  }

  void _validatePan() {
    final digits = _panController.text.replaceAll(RegExp(r'\D'), '');
    final valid = digits.length == 16 && CardValidator.isValidLuhn(digits);
    if (_isLuhnValid != valid) {
      setState(() {
        _isLuhnValid = valid;
        _formError = null;
      });
    }
  }

  void _startCooldown() {
    _cooldownTimer?.cancel();
    setState(() => _cooldownSeconds = 30);
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      if (_cooldownSeconds <= 1) {
        timer.cancel();
        setState(() => _cooldownSeconds = 0);
      } else {
        setState(() => _cooldownSeconds--);
      }
    });
  }

  // Clear all sensitive data and controllers from memory immediately
  void _clearSensitiveFormMemory() {
    _panController.clear();
    _expController.clear();
    _cvvController.clear();
    _holderController.clear();
  }

  // ---------------------------------------------------------------------------
  // Step 1: Virtual Card Creation
  // ---------------------------------------------------------------------------
  Future<void> _handleVirtualCardCreation() async {
    final accounts = ref.read(bankAccountsProvider).value ?? [];
    if (accounts.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please link a bank account first to back virtual cards')),
      );
      return;
    }

    final selectedId = _selectedBankAccountId ?? accounts.first.id;

    final pin = await showStepUpPinDialog(
      context,
      title: 'Authorize Virtual Card',
      subtitle: 'Enter your 6-digit PIN to issue a new virtual card.',
    );
    if (pin == null) return;

    setState(() => _isLoading = true);
    try {
      final card = await ref.read(cardsProvider.notifier).issueVirtualCard(
        linkedBankAccountId: selectedId,
        nickname: _nicknameController.text.trim().isNotEmpty
            ? _nicknameController.text.trim()
            : 'Virtual Card',
        pin: pin,
      );
      if (!mounted) return;
      setState(() {
        _createdCard = card;
        _currentStep = 3; // Success state
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to issue virtual card: $e')),
      );
    }
  }

  // ---------------------------------------------------------------------------
  // Step 2: Test Card Submission (SimVault Tokenize -> Link Card)
  // ---------------------------------------------------------------------------
  Future<void> _submitCardForm() async {
    final panDigits = _panController.text.replaceAll(RegExp(r'\D'), '');
    final expText = _expController.text.replaceAll(RegExp(r'\D'), '');
    final cvv = _cvvController.text.trim();
    final holderName = _holderController.text.trim();
    final nickname = _nicknameController.text.trim();

    if (!CardValidator.isValidLuhn(panDigits)) {
      setState(() => _formError = 'Invalid card number (fails Luhn check)');
      return;
    }
    if (!CardValidator.isValidExpiry(_expController.text)) {
      setState(() => _formError = 'Invalid expiry date (MM/YY)');
      return;
    }
    if (!CardValidator.isValidCvv(cvv)) {
      setState(() => _formError = 'CVV must be 3 or 4 digits');
      return;
    }
    if (holderName.length < 2) {
      setState(() => _formError = 'Card holder name is required');
      return;
    }
    if (!_consentGiven) {
      setState(() => _formError = 'Please give consent to link this card');
      return;
    }

    final expMonth = expText.substring(0, 2);
    final expYear = expText.substring(2);

    setState(() {
      _isLoading = true;
      _formError = null;
    });

    try {
      final service = ref.read(cardServiceProvider);

      // 1. Post DIRECTLY to /sim-vault/v1/tokenize via dedicated unlogged Dio
      final tokenResult = await service.tokenize(
        pan: panDigits,
        expMonth: expMonth,
        expYear: expYear,
        cvv: cvv,
        holderName: holderName,
      );

      // 2. Clear all sensitive controllers & memory immediately
      _clearSensitiveFormMemory();

      // 3. Post returned token to POST /api/v1/cards
      final card = await ref.read(cardsProvider.notifier).linkCard(
        cardToken: tokenResult.cardToken,
        nickname: nickname.isNotEmpty ? nickname : holderName,
        consentGiven: true,
      );

      if (!mounted) return;
      setState(() {
        _createdCard = card;
        _isLoading = false;
        _currentStep = 2; // Advance to OTP challenge
      });
      _startCooldown();
    } catch (e) {
      if (!mounted) return;
      // Also clear controllers on failure if sensitive
      _clearSensitiveFormMemory();
      setState(() {
        _formError = e.toString().replaceFirst('Exception: ', '');
        _isLoading = false;
      });
    }
  }

  // ---------------------------------------------------------------------------
  // Step 3: Bank OTP Verification Challenge
  // ---------------------------------------------------------------------------
  Future<void> _verifyOtp() async {
    final otp = _otpController.text.trim();
    if (otp.length != 6) {
      setState(() => _otpError = 'OTP must be 6 digits');
      return;
    }

    setState(() {
      _isLoading = true;
      _otpError = null;
    });

    try {
      final verified = await ref.read(cardsProvider.notifier).verifyCard(
        id: _createdCard!.id,
        otp: otp,
      );
      if (!mounted) return;
      setState(() {
        _createdCard = verified;
        _currentStep = 3; // Success state
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _attemptsRemaining = (_attemptsRemaining > 1) ? _attemptsRemaining - 1 : 0;
        _otpError = e.toString().replaceFirst('Exception: ', '');
        _isLoading = false;
      });
    }
  }

  void _resendOtp() {
    if (_cooldownSeconds > 0) return;
    _startCooldown();
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Simulated OTP resent to phone (Use 123456 in test mode)')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      key: AppTestKeys.flowAddCard,
      appBar: AppBar(
        title: Text(_stepTitle),
        actions: const [
          Center(child: SimulatedBadge(compact: true)),
          SizedBox(width: 16),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppTokens.screenHorizontalPadding),
          child: _buildCurrentStep(context, isDark),
        ),
      ),
    );
  }

  String get _stepTitle {
    switch (_currentStep) {
      case 0:
        return 'Add Card';
      case 1:
        return 'Card Details';
      case 2:
        return 'Verify Card';
      case 3:
      default:
        return 'Card Added';
    }
  }

  Widget _buildCurrentStep(BuildContext context, bool isDark) {
    switch (_currentStep) {
      case 0:
        return _buildChooseStep(context, isDark);
      case 1:
        return _buildFormStep(context, isDark);
      case 2:
        return _buildOtpStep(context, isDark);
      case 3:
      default:
        return _buildSuccessStep(context, isDark);
    }
  }

  // ---------------------------------------------------------------------------
  // UI: Choose Card Type
  // ---------------------------------------------------------------------------
  Widget _buildChooseStep(BuildContext context, bool isDark) {
    final accounts = ref.watch(bankAccountsProvider).value ?? [];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Choose Card Type',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 22,
            fontWeight: FontWeight.w800,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Link an existing test payment card or generate an instant virtual card backed by your bank account.',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 14,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 28),

        // Option 1: Add my card (test card)
        Card(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: BorderSide(
              color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
            ),
          ),
          child: ListTile(
            key: AppTestKeys.btnChooseTestCard,
            contentPadding: const EdgeInsets.all(16),
            leading: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: AppTokens.primaryViolet.withOpacity(0.12),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Icon(Icons.credit_card_rounded, color: AppTokens.primaryViolet),
            ),
            title: const Text(
              'Add my card (test card)',
              style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
            ),
            subtitle: const Padding(
              padding: EdgeInsets.only(top: 4.0),
              child: Text(
                'Link a Luhn-valid 16-digit test debit/credit card for simulations.',
                style: TextStyle(fontSize: 13),
              ),
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => setState(() => _currentStep = 1),
          ),
        ),
        const SizedBox(height: 16),

        // Option 2: Create a virtual card
        Card(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
            side: BorderSide(
              color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ListTile(
                  key: AppTestKeys.btnChooseVirtualCard,
                  contentPadding: EdgeInsets.zero,
                  leading: Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: AppTokens.primaryViolet.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(Icons.lock_clock_rounded, color: AppTokens.primaryViolet),
                  ),
                  title: const Text(
                    'Create a virtual card',
                    style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                  ),
                  subtitle: const Padding(
                    padding: EdgeInsets.only(top: 4.0),
                    child: Text(
                      'Instant digital card backed by one of your linked accounts.',
                      style: TextStyle(fontSize: 13),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                if (accounts.isNotEmpty) ...[
                  const Text('Select Backing Account:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 6),
                  DropdownButtonFormField<int>(
                    value: _selectedBankAccountId ?? accounts.first.id,
                    decoration: InputDecoration(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    items: accounts.map((a) {
                      return DropdownMenuItem<int>(
                        value: a.id,
                        child: Text('${a.institutionCode} (${a.maskedNumber})'),
                      );
                    }).toList(),
                    onChanged: (val) => setState(() => _selectedBankAccountId = val),
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: _isLoading ? null : _handleVirtualCardCreation,
                    child: _isLoading
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('Issue Virtual Card'),
                  ),
                ] else
                  const Text(
                    'No bank account linked. Please link an account first.',
                    style: TextStyle(color: AppTokens.error, fontSize: 13),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // UI: Card Form Step
  // ---------------------------------------------------------------------------
  Widget _buildFormStep(BuildContext context, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Visible Prototype Banner
        Container(
          key: AppTestKeys.bannerTestCardNotice,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: AppTokens.error.withOpacity(0.12),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppTokens.error.withOpacity(0.3)),
          ),
          child: const Row(
            children: [
              Icon(Icons.warning_amber_rounded, color: AppTokens.error, size: 22),
              SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Prototype: use TEST cards only. Never enter a real card.',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: AppTokens.error,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // Debug link to seeded test cards (debug builds / simulated environment)
        if (kDebugMode || AppConfig.environment == 'dev')
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: AppTestKeys.linkTestCardsScreen,
              onPressed: () async {
                final selected = await Navigator.of(context).push<TestCardItem>(
                  MaterialPageRoute(builder: (_) => const TestCardsListScreen()),
                );
                if (selected != null) {
                  setState(() {
                    _panController.text = selected.number;
                    _expController.text = selected.expiry;
                    _cvvController.text = selected.cvv;
                    _holderController.text = selected.holderName;
                  });
                }
              },
              icon: const Icon(Icons.list_alt_rounded, size: 18),
              label: const Text('View Seeded Test Cards Table (Debug)'),
            ),
          ),
        const SizedBox(height: 12),

        // Card Number Input
        Text(
          'Card Number',
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 6),
        TextField(
          key: AppTestKeys.inputCardNumber,
          controller: _panController,
          keyboardType: TextInputType.number,
          autocorrect: false,
          enableSuggestions: false,
          inputFormatters: [CardNumberInputFormatter()],
          decoration: InputDecoration(
            hintText: '9900 0000 0000 0010',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            suffixIcon: _panController.text.length >= 19
                ? Icon(
                    _isLuhnValid ? Icons.check_circle_rounded : Icons.cancel_rounded,
                    color: _isLuhnValid ? Colors.green : AppTokens.error,
                  )
                : null,
          ),
        ),
        if (_panController.text.length >= 19 && !_isLuhnValid)
          const Padding(
            padding: EdgeInsets.only(top: 4.0),
            child: Text(
              'Invalid card number (fails Luhn check)',
              style: TextStyle(color: AppTokens.error, fontSize: 12),
            ),
          ),
        const SizedBox(height: 16),

        // Expiry & CVV Row
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Expiry (MM/YY)',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                  const SizedBox(height: 6),
                  TextField(
                    key: AppTestKeys.inputCardExpiry,
                    controller: _expController,
                    keyboardType: TextInputType.number,
                    autocorrect: false,
                    enableSuggestions: false,
                    inputFormatters: [CardExpiryInputFormatter()],
                    decoration: InputDecoration(
                      hintText: '12/27',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'CVV',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                  const SizedBox(height: 6),
                  TextField(
                    key: AppTestKeys.inputCardCvv,
                    controller: _cvvController,
                    keyboardType: TextInputType.number,
                    obscureText: true,
                    autocorrect: false,
                    enableSuggestions: false,
                    maxLength: 4,
                    decoration: InputDecoration(
                      counterText: '',
                      hintText: '•••',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Holder Name Input
        Text(
          'Cardholder Name',
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 6),
        TextField(
          key: AppTestKeys.inputCardHolderName,
          controller: _holderController,
          autocorrect: false,
          enableSuggestions: false,
          decoration: InputDecoration(
            hintText: 'e.g. Test Approved',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 16),

        // Nickname Input
        Text(
          'Card Nickname (Optional)',
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 6),
        TextField(
          key: AppTestKeys.inputCardNickname,
          controller: _nicknameController,
          decoration: InputDecoration(
            hintText: 'e.g. Shopping Card',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 16),

        // Consent Checkbox
        CheckboxListTile(
          key: AppTestKeys.checkboxCardConsent,
          contentPadding: EdgeInsets.zero,
          title: const Text(
            'I authorize linking this test card to my LankaPay ID account.',
            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
          ),
          value: _consentGiven,
          onChanged: (val) => setState(() => _consentGiven = val ?? false),
          controlAffinity: ListTileControlAffinity.leading,
        ),
        const SizedBox(height: 8),

        if (_formError != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 12.0),
            child: Text(
              _formError!,
              style: const TextStyle(color: AppTokens.error, fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),

        ElevatedButton(
          key: AppTestKeys.btnCardFormSubmit,
          onPressed: (_isLoading || !_isLuhnValid || !_consentGiven) ? null : _submitCardForm,
          child: _isLoading
              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Tokenize & Link Card'),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // UI: Bank OTP Challenge Step
  // ---------------------------------------------------------------------------
  Widget _buildOtpStep(BuildContext context, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Bank OTP Verification',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 22,
            fontWeight: FontWeight.w800,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Enter the simulated 6-digit ownership verification code sent to your registered mobile.',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 14,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 24),

        TextField(
          key: AppTestKeys.inputCardOtp,
          controller: _otpController,
          keyboardType: TextInputType.number,
          maxLength: 6,
          autofocus: true,
          decoration: InputDecoration(
            counterText: '',
            hintText: '123456',
            prefixIcon: const Icon(Icons.shield_outlined),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 12),

        // Attempts Remaining
        Text(
          '$_attemptsRemaining attempts remaining',
          key: AppTestKeys.otpAttemptsText,
          style: TextStyle(
            fontSize: 13,
            color: _attemptsRemaining <= 1 ? AppTokens.error : Colors.grey,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 12),

        if (_otpError != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 12.0),
            child: Text(
              _otpError!,
              style: const TextStyle(color: AppTokens.error, fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),

        ElevatedButton(
          key: AppTestKeys.btnCardOtpVerify,
          onPressed: _isLoading ? null : _verifyOtp,
          child: _isLoading
              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Verify Card'),
        ),
        const SizedBox(height: 16),

        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            TextButton(
              key: AppTestKeys.btnCardOtpResend,
              onPressed: _cooldownSeconds == 0 ? _resendOtp : null,
              child: const Text('Resend Code'),
            ),
            if (_cooldownSeconds > 0)
              Text(
                ' (${_cooldownSeconds}s)',
                key: AppTestKeys.otpCooldownText,
                style: const TextStyle(fontSize: 13, color: Colors.grey),
              ),
          ],
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // UI: Success Step
  // ---------------------------------------------------------------------------
  Widget _buildSuccessStep(BuildContext context, bool isDark) {
    final card = _createdCard;

    return Column(
      key: AppTestKeys.cardOtpSuccess,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 20),
        Center(
          child: Container(
            width: 72,
            height: 72,
            decoration: BoxDecoration(
              color: Colors.green.withOpacity(0.12),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.check_circle_rounded, color: Colors.green, size: 48),
          ),
        ),
        const SizedBox(height: 16),
        Text(
          'Card Verified Successfully!',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 22,
            fontWeight: FontWeight.w800,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Your card is now active and ready for transfers and payments.',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 14,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 28),

        if (card != null)
          AccountCard(
            bankName: 'TEST CARD',
            nickname: card.nickname ?? (card.isVirtual ? 'Virtual Card' : 'Linked Card'),
            maskedNumber: '•••• •••• •••• ${card.last4}',
            expiryText: card.formattedExpiry,
            style: AccountCardStyle.charcoal,
          ),
        const SizedBox(height: 32),

        ElevatedButton(
          key: AppTestKeys.btnCardOtpDone,
          onPressed: () {
            ref.invalidate(cardsProvider);
            Navigator.of(context).pop();
          },
          child: const Text('Done'),
        ),
      ],
    );
  }
}
