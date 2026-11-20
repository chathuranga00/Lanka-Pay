import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../send/domain/transfer_models.dart';
import '../data/history_service.dart';

class ReceiptScreen extends StatelessWidget {
  final ReceiptModel receipt;

  const ReceiptScreen({super.key, required this.receipt});

  void _shareReceipt(BuildContext context) {
    HapticFeedback.lightImpact();
    final text = '''
LANKAPAY PAYMENT RECEIPT [SIMULATED]
Receipt #: ${receipt.receiptNumber}
Status: ${receipt.status}
Amount: LKR ${receipt.totalDebited.toStringAsFixed(2)}
Sender: ${receipt.sender?.displayName ?? 'N/A'} (${receipt.sender?.maskedPhone ?? ''})
Receiver: ${receipt.receiver?.displayName ?? 'N/A'} (${receipt.receiver?.maskedPhone ?? ''})
Bank Ref: ${receipt.bankTxRef ?? 'N/A'}
Tx Reference: ${receipt.reference}
Date: ${receipt.completedAt ?? receipt.createdAt ?? 'N/A'}
Digital Signature: ${receipt.digitalSignature ?? 'N/A'}
''';

    try {
      Share.share(
        text,
        subject: 'LankaPay Receipt - ${receipt.receiptNumber}',
      );
    } catch (_) {
      Clipboard.setData(ClipboardData(text: text));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Receipt details copied to clipboard')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: Column(
        children: [
          const SimulatedBanner(),
          Expanded(
            child: GradientHeaderScaffold(
              scrollable: true,
              header: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      if (Navigator.of(context).canPop())
                        IconButton(
                          icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
                          tooltip: 'Back',
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                      Text(
                        'Receipt',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                    ],
                  ),
                  IconButton(
                    key: const Key('receipt_share_btn'),
                    icon: const Icon(Icons.share_rounded),
                    tooltip: 'Share Receipt',
                    onPressed: () => _shareReceipt(context),
                  ),
                ],
              ),
              body: Column(
                children: [
                  Container(
                    padding: const EdgeInsets.all(24),
                    decoration: BoxDecoration(
                      color: isDark ? AppTokens.cardDark : Colors.white,
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.04),
                          blurRadius: 16,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Column(
                      children: [
                        Container(
                          width: 60,
                          height: 60,
                          decoration: BoxDecoration(
                            color: AppTokens.primaryViolet.withOpacity(0.12),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.receipt_long_rounded,
                            size: 32,
                            color: AppTokens.primaryViolet,
                          ),
                        ),
                        const SizedBox(height: 14),
                        const Text(
                          'LankaPay Transfer Receipt',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          receipt.receiptNumber,
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: AppTokens.primaryViolet,
                          ),
                        ),
                        Divider(height: 32, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        Text(
                          'Total Paid',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 12,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'LKR ${receipt.totalDebited.toStringAsFixed(2)}',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 32,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.5,
                            color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                          ),
                        ),
                        const SizedBox(height: 24),
                        _buildReceiptRow('Status', receipt.status, isDark, isBold: true),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow('Principal Amount', 'LKR ${receipt.amount.toStringAsFixed(2)}', isDark),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow('Network Fee', 'LKR ${receipt.fee.toStringAsFixed(2)}', isDark),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow('Recipient', receipt.receiver?.displayName ?? 'N/A', isDark),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow('Recipient Phone', receipt.receiver?.maskedPhone ?? 'N/A', isDark),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow('Sender Phone', receipt.sender?.maskedPhone ?? 'N/A', isDark),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow('Bank Tx Ref', receipt.bankTxRef ?? 'N/A', isDark, isMonospace: true),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow(
                          'Payment Method',
                          receipt.paymentMethod ??
                              (receipt.fundingType == FundingType.card
                                  ? 'Paid with card •••• ${receipt.cardLast4 ?? '1234'}'
                                  : 'Paid with bank account'),
                          isDark,
                        ),
                        Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                        _buildReceiptRow('Transfer Reference', receipt.reference, isDark, isMonospace: true),
                        if (receipt.digitalSignature != null) ...[
                          Divider(height: 20, color: isDark ? AppTokens.borderDark : AppTokens.borderLight),
                          _buildReceiptRow(
                            'Tamper Hash',
                            '${receipt.digitalSignature!.substring(0, 16)}...',
                            isDark,
                            isMonospace: true,
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 28),
                  ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 52),
                    child: ElevatedButton.icon(
                      onPressed: () => _shareReceipt(context),
                      icon: const Icon(Icons.share_rounded),
                      label: const Text(
                        'Share Receipt',
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
          ),
        ],
      ),
    );
  }

  Widget _buildReceiptRow(String label, String value, bool isDark, {bool isBold = false, bool isMonospace = false}) {
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
              fontWeight: isBold ? FontWeight.w700 : FontWeight.w600,
              fontFamily: isMonospace ? 'monospace' : AppTokens.fontFamily,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
          ),
        ),
      ],
    );
  }
}
