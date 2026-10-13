import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/info_tile.dart';
import '../../../core/widgets/section_header.dart';

/// Consolidated Payments Hub (Tab 1: Pay).
/// Provides quick access to Send, LankaQR scan, Bills, Split, and Scheduled transfers.
class PayHubScreen extends ConsumerWidget {
  const PayHubScreen({super.key = const Key('pay_hub_screen')});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      key: const Key('pay_hub_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Payments & Transfers',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        centerTitle: false,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // 1. Payment Actions 2x2 Grid
          Row(
            children: [
              Expanded(
                child: _buildActionTile(
                  context,
                  isDark,
                  key: const Key('pay_action_send'),
                  title: 'Send Money',
                  subtitle: 'Instant P2P',
                  icon: Icons.swap_horiz_rounded,
                  color: AppTokens.primaryBlue,
                  onTap: () => context.push('/send'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildActionTile(
                  context,
                  isDark,
                  key: const Key('pay_action_qr'),
                  title: 'Scan LankaQR',
                  subtitle: 'Merchant Pay',
                  icon: Icons.qr_code_scanner_rounded,
                  color: AppTokens.primaryBlue,
                  onTap: () => context.push('/qr'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _buildActionTile(
                  context,
                  isDark,
                  key: const Key('pay_action_bills'),
                  title: 'Pay Bills',
                  subtitle: 'CEB, Water, Telco',
                  icon: Icons.receipt_long_rounded,
                  color: const Color(0xFFF59E0B),
                  onTap: () => context.push('/send'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildActionTile(
                  context,
                  isDark,
                  key: const Key('pay_action_split'),
                  title: 'Split Bill',
                  subtitle: 'Friends & Dining',
                  icon: Icons.call_split_rounded,
                  color: const Color(0xFF10B981),
                  onTap: () => context.push('/send'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // 2. Scheduled & Standing Orders
          const SectionHeader(title: 'AUTOMATION & SCHEDULES'),
          const SizedBox(height: 8),
          InfoTile(
            key: const Key('pay_info_scheduled'),
            title: 'Scheduled Transfers',
            subtitle: 'Manage standing orders & recurring rent/utilities',
            icon: Icons.schedule_rounded,
            onTap: () => context.push('/history'),
          ),
          const SizedBox(height: 24),

          // 3. Recent Transfer Receipts
          SectionHeader(
            title: 'RECENT RECEIPTS',
            actionLabel: 'See history >',
            onActionTap: () => context.push('/history'),
          ),
          const SizedBox(height: 8),
          InfoTile(
            key: const Key('pay_info_history'),
            title: 'Transaction History',
            subtitle: 'View detailed receipts & settlement proofs',
            icon: Icons.history_rounded,
            onTap: () => context.push('/history'),
          ),
        ],
      ),
    );
  }

  Widget _buildActionTile(
    BuildContext context,
    bool isDark, {
    required Key key,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return InkWell(
      key: key,
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isDark ? AppTokens.cardDark : Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: isDark ? [] : AppTokens.softShadowLight,
          border: Border.all(
            color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
            width: 0.8,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: color.withOpacity(0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: color, size: 24),
            ),
            const SizedBox(height: 12),
            Text(
              title,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              subtitle,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 12,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
