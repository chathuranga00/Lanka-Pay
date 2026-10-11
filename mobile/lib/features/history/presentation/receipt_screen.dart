import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../data/history_service.dart';

class ReceiptScreen extends StatelessWidget {
  final ReceiptModel receipt;

  const ReceiptScreen({super.key, required this.receipt});

  void _shareReceipt(BuildContext context) {
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
      appBar: AppBar(
        title: const Text('Transaction Receipt'),
        actions: [
          IconButton(
            key: const Key('receipt_share_btn'),
            icon: const Icon(Icons.share_rounded),
            tooltip: 'Share Receipt',
            onPressed: () => _shareReceipt(context),
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
                  children: [
                    Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: isDark ? AppColors.surfaceDark : Colors.white,
                        borderRadius: BorderRadius.circular(24),
                        border: Border.all(
                          color: isDark ? Colors.white12 : Colors.black12,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.05),
                            blurRadius: 16,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: Column(
                        children: [
                          const Icon(Icons.receipt_long_rounded, size: 54, color: AppColors.primaryGreen),
                          const SizedBox(height: 12),
                          const Text(
                            'LankaPay Transfer Receipt',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            receipt.receiptNumber,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              fontFamily: 'monospace',
                              color: AppColors.accentGoldDark,
                            ),
                          ),
                          const Divider(height: 32),
                          Text(
                            'Total Paid',
                            style: TextStyle(
                              fontSize: 12,
                              color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'LKR ${receipt.totalDebited.toStringAsFixed(2)}',
                            style: const TextStyle(
                              fontSize: 30,
                              fontWeight: FontWeight.w800,
                              letterSpacing: -0.5,
                            ),
                          ),
                          const SizedBox(height: 24),
                          _buildReceiptRow('Status', receipt.status, isDark, isBold: true),
                          const Divider(height: 20),
                          _buildReceiptRow('Principal Amount', 'LKR ${receipt.amount.toStringAsFixed(2)}', isDark),
                          const Divider(height: 20),
                          _buildReceiptRow('Network Fee', 'LKR ${receipt.fee.toStringAsFixed(2)}', isDark),
                          const Divider(height: 20),
                          _buildReceiptRow('Recipient', receipt.receiver?.displayName ?? 'N/A', isDark),
                          const Divider(height: 20),
                          _buildReceiptRow('Recipient Phone', receipt.receiver?.maskedPhone ?? 'N/A', isDark),
                          const Divider(height: 20),
                          _buildReceiptRow('Sender Phone', receipt.sender?.maskedPhone ?? 'N/A', isDark),
                          const Divider(height: 20),
                          _buildReceiptRow('Bank Tx Ref', receipt.bankTxRef ?? 'N/A', isDark, isMonospace: true),
                          const Divider(height: 20),
                          _buildReceiptRow('Transfer Reference', receipt.reference, isDark, isMonospace: true),
                          if (receipt.digitalSignature != null) ...[
                            const Divider(height: 20),
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
                    ElevatedButton.icon(
                      onPressed: () => _shareReceipt(context),
                      icon: const Icon(Icons.share_rounded),
                      label: const Text('Share Receipt'),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
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
              fontWeight: isBold ? FontWeight.w700 : FontWeight.w600,
              fontFamily: isMonospace ? 'monospace' : null,
              color: isDark ? Colors.white : AppColors.textPrimaryLight,
            ),
          ),
        ),
      ],
    );
  }
}
