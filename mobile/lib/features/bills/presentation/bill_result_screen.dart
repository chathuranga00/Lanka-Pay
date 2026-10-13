import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../domain/bill_models.dart';

class BillResultScreen extends StatelessWidget {
  final BillPaymentReceiptModel receipt;

  const BillResultScreen({
    super.key,
    required this.receipt,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final Color statusColor;
    final IconData statusIcon;
    final String title;
    final String subtitle;
    final String badgeText;

    if (receipt.isCompleted) {
      statusColor = const Color(0xFF10B981); // Emerald green
      statusIcon = Icons.check_circle_rounded;
      title = 'Payment Successful';
      subtitle = 'Your payment has been acknowledged by ${receipt.billerName}.';
      badgeText = 'COMPLETED';
    } else if (receipt.isPending) {
      statusColor = const Color(0xFFF59E0B); // Amber
      statusIcon = Icons.schedule_rounded;
      title = 'Payment Pending';
      subtitle = 'Biller acknowledgment is in progress. You will receive a safe notification upon confirmation.';
      badgeText = 'PENDING';
    } else if (receipt.isReversed) {
      statusColor = const Color(0xFF8B5CF6); // Purple
      statusIcon = Icons.replay_rounded;
      title = 'Payment Reversed';
      subtitle = 'This transaction was reversed and funds have returned to your funding source.';
      badgeText = 'REVERSED';
    } else {
      statusColor = AppTokens.error; // Red
      statusIcon = Icons.cancel_rounded;
      title = 'Payment Failed';
      subtitle = receipt.failureReason ?? 'Payment was rejected or could not be completed.';
      badgeText = 'FAILED';
    }

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        elevation: 0,
        backgroundColor: Colors.transparent,
        actions: [
          IconButton(
            key: AppTestKeys.billResultDone,
            icon: const Icon(Icons.close_rounded),
            onPressed: () => context.go('/bills'),
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
          child: Column(
            children: [
              // Status Circle & Icon
              Container(
                width: 76,
                height: 76,
                decoration: BoxDecoration(
                  color: statusColor.withOpacity(0.12),
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Icon(statusIcon, color: statusColor, size: 48),
                ),
              ),
              const SizedBox(height: 16),

              // Title
              Text(
                title,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                ),
              ),
              const SizedBox(height: 8),

              // Subtitle
              Text(
                subtitle,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 14,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              const SizedBox(height: 12),

              // Status Badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                decoration: BoxDecoration(
                  color: statusColor.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: statusColor.withOpacity(0.4)),
                ),
                child: Text(
                  badgeText,
                  style: TextStyle(
                    color: statusColor,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
              const SizedBox(height: 24),

              // Amount Card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 20),
                decoration: BoxDecoration(
                  color: isDark ? AppTokens.cardDark : const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: isDark ? AppTokens.borderDark : const Color(0xFFE2E8F0),
                  ),
                ),
                child: Column(
                  children: [
                    Text(
                      'Total Paid',
                      style: TextStyle(
                        fontSize: 13,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'LKR ${receipt.totalAmount.toStringAsFixed(2)}',
                      style: TextStyle(
                        fontSize: 30,
                        fontWeight: FontWeight.w800,
                        color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Detailed Receipt Breakdown
              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: isDark ? AppTokens.cardDark : Colors.white,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                  ),
                ),
                child: Column(
                  children: [
                    _buildReceiptRow('Biller', receipt.billerName, isDark),
                    const SizedBox(height: 10),
                    _buildReceiptRow('Account / Ref', receipt.maskedReference, isDark, isMono: true),
                    const SizedBox(height: 10),
                    _buildReceiptRow('Receipt Number', receipt.receiptNumber, isDark, isMono: true),
                    if (receipt.billerConfirmationRef != null) ...[
                      const SizedBox(height: 10),
                      _buildReceiptRow('Biller Ref', receipt.billerConfirmationRef!, isDark, isMono: true),
                    ],
                    const Divider(height: 24),
                    _buildReceiptRow('Bill Amount', 'LKR ${receipt.amount.toStringAsFixed(2)}', isDark),
                    const SizedBox(height: 10),
                    _buildReceiptRow('Convenience Fee', 'LKR ${receipt.fee.toStringAsFixed(2)}', isDark),
                    const SizedBox(height: 10),
                    _buildReceiptRow('Paid From', receipt.fundingLabel ?? receipt.fundingType, isDark),
                    if (receipt.timestamp != null) ...[
                      const SizedBox(height: 10),
                      _buildReceiptRow('Date & Time', receipt.timestamp!, isDark),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 28),

              // Action Buttons
              if (receipt.isFailed) ...[
                SizedBox(
                  width: double.infinity,
                  height: 50,
                  child: FilledButton(
                    key: AppTestKeys.billResultRetry,
                    onPressed: () => context.pop(),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppTokens.primaryBlue,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    ),
                    child: const Text(
                      'Try Again',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
              ],

              SizedBox(
                width: double.infinity,
                height: 50,
                child: FilledButton.tonal(
                  key: AppTestKeys.billResultDone,
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    context.go('/bills');
                  },
                  style: FilledButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                  child: const Text(
                    'Done',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                ),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildReceiptRow(String label, String value, bool isDark, {bool isMono = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(
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
              fontFamily: isMono ? 'RobotoMono' : null,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
          ),
        ),
      ],
    );
  }
}
