import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
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
        appBar: AppBar(
          automaticallyImplyLeading: false,
          title: Text(_buildAppBarTitle(status)),
          actions: [
            IconButton(
              icon: const Icon(Icons.close_rounded),
              onPressed: () => context.go('/home'),
              tooltip: 'Close',
            ),
          ],
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

    if (status.isConfirmedSuccess) {
      // Green ONLY when bank confirmed
      iconColor = AppColors.success;
      bgColor = AppColors.success.withOpacity(0.12);
      iconData = Icons.check_circle_rounded;
      titleText = 'Payment Confirmed';
      subtitleText = 'Bank adapter confirmed debit and credit transfer.';
    } else if (status.isPendingOrReconciling) {
      // Amber/Gold for pending/reconciliation - NEVER green
      iconColor = AppColors.warning;
      bgColor = AppColors.warning.withOpacity(0.15);
      iconData = Icons.hourglass_top_rounded;
      titleText = status == TransferStatusType.requiresReconciliation
          ? 'Requires Bank Reconciliation'
          : 'Processing with Bank...';
      subtitleText = status == TransferStatusType.requiresReconciliation
          ? 'Transaction timeout occurred. Our background worker is reconciling the settlement with the bank. Do not retry.'
          : 'Your transfer is being processed across LankaPay CEFTS network. Status will update automatically.';
    } else if (status.isReversed) {
      // Gray/amber reversal notice
      iconColor = const Color(0xFF6B7280);
      bgColor = const Color(0xFF6B7280).withOpacity(0.12);
      iconData = Icons.replay_rounded;
      titleText = 'Payment Reversed';
      subtitleText = 'The transfer was refunded or reversed back to your origin account.';
    } else {
      // Red failed screen
      iconColor = AppColors.error;
      bgColor = AppColors.error.withOpacity(0.12);
      iconData = Icons.cancel_rounded;
      titleText = 'Payment Failed';
      subtitleText = _currentTransfer.failureReason ??
          'Transfer could not be completed. Your bank account was not debited.';
    }

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: iconColor.withOpacity(0.3), width: 1.5),
      ),
      child: Column(
        children: [
          Icon(iconData, size: 64, color: iconColor),
          const SizedBox(height: 16),
          Text(
            titleText,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: isDark ? Colors.white : AppColors.textPrimaryLight,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitleText,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 13,
              height: 1.4,
              color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
            ),
          ),
          if (_isPolling && status.isPendingOrReconciling) ...[
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(width: 10),
                Text(
                  'Auto-checking status...',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: AppColors.warning,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildAmountSummary(bool isDark) {
    return Column(
      children: [
        Text(
          'Total Amount',
          style: TextStyle(
            fontSize: 13,
            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'LKR ${_currentTransfer.amount.toStringAsFixed(2)}',
          style: TextStyle(
            fontSize: 32,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.5,
            color: isDark ? Colors.white : AppColors.textPrimaryLight,
          ),
        ),
      ],
    );
  }

  Widget _buildReceiptDetailsCard(bool isDark) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? AppColors.cardDark : AppColors.surfaceLight,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? Colors.white12 : Colors.black12,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildRow('Transfer Reference', _currentTransfer.reference, isDark, isMonospace: true),
          const Divider(height: 24),
          if (_currentTransfer.receiverDisplayName != null) ...[
            _buildRow('Recipient', _currentTransfer.receiverDisplayName!, isDark),
            const Divider(height: 24),
          ],
          if (_currentTransfer.bankTxRef != null) ...[
            _buildRow('Bank Tx Ref', _currentTransfer.bankTxRef!, isDark, isMonospace: true),
            const Divider(height: 24),
          ],
          _buildRow('Status', _currentTransfer.status.name.toUpperCase(), isDark),
          if (_currentTransfer.note != null && _currentTransfer.note!.isNotEmpty) ...[
            const Divider(height: 24),
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
            fontSize: 13,
            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
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
              fontFamily: isMonospace ? 'monospace' : null,
              color: isDark ? Colors.white : AppColors.textPrimaryLight,
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
          OutlinedButton.icon(
            key: const Key('transfer_refresh_btn'),
            onPressed: _isPolling ? null : () => _checkStatus(),
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('Refresh Status Now'),
          ),
          const SizedBox(height: 12),
        ],
        ElevatedButton(
          key: const Key('transfer_done_btn'),
          onPressed: () => context.go('/home'),
          child: const Text('Return to Home'),
        ),
      ],
    );
  }
}
