import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../data/transfer_service.dart';
import '../domain/transfer_models.dart';

class TransferResultScreen extends ConsumerStatefulWidget {
  final TransferModel initialTransfer;
  final bool enableAutoPolling;

  const TransferResultScreen({
    super.key,
    required this.initialTransfer,
    this.enableAutoPolling = true,
  });

  @override
  ConsumerState<TransferResultScreen> createState() => _TransferResultScreenState();
}

class _TransferResultScreenState extends ConsumerState<TransferResultScreen> {
  late TransferModel _currentTransfer;
  bool _isPolling = false;
  Timer? _pollTimer;
  int _pollCount = 0;
  static const int _maxPollAttempts = 10; // 10 attempts * 3s = 30s max polling

  @override
  void initState() {
    super.initState();
    _currentTransfer = widget.initialTransfer;
    if (widget.enableAutoPolling && _currentTransfer.status.isPendingOrReconciling) {
      _startPolling();
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  void _startPolling() {
    _pollTimer?.cancel();
    _pollCount = 0;
    setState(() => _isPolling = true);

    _pollTimer = Timer.periodic(const Duration(seconds: 3), (timer) async {
      _pollCount++;
      await _checkStatus();
      if (!_currentTransfer.status.isPendingOrReconciling || _pollCount >= _maxPollAttempts) {
        timer.cancel();
        if (mounted) {
          setState(() => _isPolling = false);
        }
      }
    });
  }

  Future<void> _checkStatus() async {
    try {
      final updated = await ref
          .read(transferServiceProvider)
          .getTransferById(_currentTransfer.transferId);
      if (mounted) {
        setState(() {
          _currentTransfer = updated;
        });
      }
    } catch (_) {
      // Continue polling or leave current state intact
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final status = _currentTransfer.status;

    return PopScope(
      canPop: false,
      onPopInvoked: (didPop) {
        if (!didPop) context.go('/home');
      },
      child: Scaffold(
        body: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: GradientHeaderScaffold(
                scrollable: true,
                header: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      _buildAppBarTitle(status),
                      style: TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close_rounded),
                      tooltip: 'Close',
                      onPressed: () {
                        HapticFeedback.lightImpact();
                        context.go('/home');
                      },
                    ),
                  ],
                ),
                body: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildStatusIconCard(status, isDark),
                    const SizedBox(height: 24),
                    _buildAmountSummary(isDark),
                    const SizedBox(height: 24),
                    _buildReceiptDetailsCard(isDark),
                    const SizedBox(height: 32),
                    _buildActionButtons(status),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _buildAppBarTitle(TransferStatusType status) {
    if (status.isConfirmedSuccess) return 'Payment Receipt';
    if (status.isPendingOrReconciling) return 'Pending Settlement';
    if (status.isReversed) return 'Reversal Notice';
    return 'Transfer Unsuccessful';
  }

  Widget _buildStatusIconCard(TransferStatusType status, bool isDark) {
    Color iconColor;
    Color bgColor;
    IconData iconData;
    String titleText;
    String subtitleText;
    String? suggestedNextStep;

    if (status.isConfirmedSuccess) {
      // Green ONLY when bank confirmed
      iconColor = AppTokens.success;
      bgColor = AppTokens.success.withOpacity(0.12);
      iconData = Icons.check_circle_rounded;
      titleText = 'Payment Confirmed';
      subtitleText = 'Bank adapter confirmed debit and credit transfer.';
    } else if (status.isPendingOrReconciling) {
      // Amber/Gold for pending/reconciliation - NEVER green
      iconColor = AppTokens.warning;
      bgColor = AppTokens.warningLight;
      iconData = Icons.hourglass_top_rounded;
      titleText = status == TransferStatusType.requiresReconciliation
          ? 'Requires Bank Reconciliation'
          : 'Processing with Bank...';
      subtitleText = status == TransferStatusType.requiresReconciliation
          ? 'Transaction timeout occurred. Our background worker is reconciling the settlement with the bank. Do not retry.'
          : 'Your transfer is being processed across LankaPay CEFTS network. Status will update automatically.';
    } else if (status.isReversed) {
      // Neutral slate reversal notice
      iconColor = const Color(0xFF6B7280);
      bgColor = const Color(0xFF6B7280).withOpacity(0.12);
      iconData = Icons.replay_rounded;
      titleText = 'Payment Reversed';
      subtitleText = 'The transfer was refunded or reversed back to your origin account.';
    } else {
      // Red failed screen
      iconColor = AppTokens.error;
      bgColor = AppTokens.errorLight;
      iconData = Icons.cancel_rounded;
      final isCard = _currentTransfer.fundingType == FundingType.card ||
          (_currentTransfer.cardLast4 != null && _currentTransfer.cardLast4!.isNotEmpty);
      titleText = isCard ? 'Payment Declined' : 'Payment Failed';
      final decline = _getDeclineInfo(_currentTransfer.failureReason, isCard);
      subtitleText = decline.friendlyReason;
      suggestedNextStep = decline.suggestedNextStep;
    }

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: iconColor.withOpacity(0.35), width: 1.5),
      ),
      child: Column(
        children: [
          Icon(iconData, size: 64, color: iconColor),
          const SizedBox(height: 16),
          Text(
            titleText,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 20,
              fontWeight: FontWeight.w800,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitleText,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: AppTokens.fontFamily,
              fontSize: 13,
              height: 1.4,
              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
            ),
          ),
          if (suggestedNextStep != null) ...[
            const SizedBox(height: 14),
            Container(
              key: const Key('result_suggested_next_step'),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: AppTokens.error.withOpacity(0.12),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppTokens.error.withOpacity(0.2)),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.info_outline_rounded, size: 16, color: AppTokens.error),
                  const SizedBox(width: 8),
                  Text(
                    'Suggested: $suggestedNextStep',
                    style: const TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: AppTokens.error,
                    ),
                  ),
                ],
              ),
            ),
          ],
          if (_isPolling && status.isPendingOrReconciling) ...[
            const SizedBox(height: 16),
            const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2, color: AppTokens.warning),
                ),
                SizedBox(width: 10),
                Text(
                  'Auto-checking status...',
                  style: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: AppTokens.warning,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  ({String friendlyReason, String? suggestedNextStep}) _getDeclineInfo(String? rawReason, bool isCard) {
    final reason = (rawReason ?? '').toUpperCase();
    if (reason.contains('CARD_FROZEN') || reason.contains('FROZEN')) {
      return (
        friendlyReason: 'This card is currently frozen.',
        suggestedNextStep: 'Unfreeze card',
      );
    }
    if (reason.contains('CARD_EXPIRED') || reason.contains('EXPIRED')) {
      return (
        friendlyReason: 'This card has expired.',
        suggestedNextStep: 'Choose another card',
      );
    }
    if (reason.contains('DAILY_LIMIT_EXCEEDED')) {
      return (
        friendlyReason: 'Daily spending limit for this card has been exceeded.',
        suggestedNextStep: 'Lower the amount',
      );
    }
    if (reason.contains('PER_PAYMENT_LIMIT_EXCEEDED')) {
      return (
        friendlyReason: 'Transaction exceeds the per-payment limit for this card.',
        suggestedNextStep: 'Lower the amount',
      );
    }
    if (reason.contains('LIMIT_EXCEEDED') || reason.contains('DAILY_LIMIT')) {
      return (
        friendlyReason: 'Card limit exceeded.',
        suggestedNextStep: 'Lower the amount',
      );
    }
    if (reason.contains('ONLINE_PAYMENTS_DISABLED')) {
      return (
        friendlyReason: 'Online payments are disabled for this card.',
        suggestedNextStep: 'Choose another card',
      );
    }
    if (reason.contains('CHALLENGE_FAILED')) {
      return (
        friendlyReason: 'Bank OTP challenge verification failed.',
        suggestedNextStep: 'Choose another card',
      );
    }
    if (reason.contains('CARD_BLOCKED') || reason.contains('BLOCKED')) {
      return (
        friendlyReason: 'This card is blocked.',
        suggestedNextStep: 'Choose another card',
      );
    }
    return (
      friendlyReason: rawReason != null && rawReason.isNotEmpty
          ? rawReason
          : (isCard ? 'Card payment was declined by the bank.' : 'Transfer was declined by the bank.'),
      suggestedNextStep: isCard ? 'Choose another card' : null,
    );
  }

  Widget _buildAmountSummary(bool isDark) {
    final displayAmt = _currentTransfer.totalAmount > 0
        ? _currentTransfer.totalAmount
        : _currentTransfer.amount;

    return Column(
      children: [
        Text(
          'Total Amount',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 13,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'LKR ${displayAmt.toStringAsFixed(2)}',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 32,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.5,
            color: isDark ? Colors.white : AppTokens.textPrimaryLight,
          ),
        ),
      ],
    );
  }

  Widget _buildReceiptDetailsCard(bool isDark) {
    final paymentMethodText = _currentTransfer.paymentMethod ??
        (_currentTransfer.fundingType == FundingType.card
            ? 'Paid with card •••• ${_currentTransfer.cardLast4 ?? "1234"}'
            : 'Paid with bank account');

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : AppTokens.mutedSurfaceLight,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildRow('Transfer Reference', _currentTransfer.reference, isDark, isMonospace: true),
          Divider(height: 24, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
          if (_currentTransfer.receiverDisplayName != null) ...[
            _buildRow('Recipient', _currentTransfer.receiverDisplayName!, isDark),
            Divider(height: 24, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
          ],
          _buildRow('Payment Method', paymentMethodText, isDark),
          Divider(height: 24, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
          if (_currentTransfer.fee > 0) ...[
            _buildRow('Fee (simulated)', 'LKR ${_currentTransfer.fee.toStringAsFixed(2)}', isDark),
            Divider(height: 24, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
          ],
          if (_currentTransfer.bankTxRef != null) ...[
            _buildRow('Bank Tx Ref', _currentTransfer.bankTxRef!, isDark, isMonospace: true),
            Divider(height: 24, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
          ],
          _buildRow('Status', _currentTransfer.status.name.toUpperCase(), isDark),
          if (_currentTransfer.note != null && _currentTransfer.note!.isNotEmpty) ...[
            Divider(height: 24, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
            _buildRow('Reference / Note', _currentTransfer.note!, isDark),
          ],
        ],
      ),
    );
  }

  Widget _buildRow(String label, String value, bool isDark, {bool isMonospace = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 13,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(width: 16),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              fontFamily: isMonospace ? 'monospace' : AppTokens.fontFamily,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildActionButtons(TransferStatusType status) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (status.isPendingOrReconciling) ...[
          ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 52),
            child: OutlinedButton.icon(
              key: const Key('transfer_refresh_btn'),
              onPressed: _isPolling
                  ? null
                  : () {
                      HapticFeedback.lightImpact();
                      _checkStatus();
                    },
              icon: const Icon(Icons.refresh_rounded),
              label: const Text(
                'Refresh Status Now',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
        ],
        ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 52),
          child: ElevatedButton(
            key: const Key('transfer_done_btn'),
            onPressed: () {
              HapticFeedback.lightImpact();
              context.go('/home');
            },
            child: const Text(
              'Return to Home',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 16,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      ],
    );
  }
}
