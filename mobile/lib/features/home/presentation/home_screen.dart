import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/account_card.dart';
import '../../../core/widgets/app_avatar.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/error_retry.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/quick_action_tile.dart';
import '../../../core/widgets/section_header.dart';
import '../../../core/widgets/shimmer_skeletons.dart';
import '../../../core/widgets/simulated_badge.dart';
import '../../../core/widgets/transaction_tile.dart';
import '../../accounts/data/bank_account_service.dart';
import '../../accounts/domain/bank_account_model.dart';
import '../../auth/data/auth_service.dart';
import '../../auth/domain/auth_state.dart';
import '../../history/data/history_service.dart';
import '../../history/presentation/receipt_screen.dart';
import '../../send/domain/transfer_models.dart';
import '../../settings/presentation/privacy_settings_screen.dart';
import 'widgets/avatar_sheet.dart';
import '../data/home_providers.dart';

/// Redesigned Neo-Banking Home Dashboard tab matching the target design language.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  bool _isSummaryHidden = false;

  Future<void> _refreshAll() async {
    ref.invalidate(bankAccountsProvider);
    ref.invalidate(unreadNotificationCountProvider);
    ref.invalidate(recentTransactionsProvider);
    ref.invalidate(monthlyReceivedProvider);
  }

  Future<void> _openTransactionDetail(TransferModel item) async {
    try {
      final receipt = await ref.read(historyServiceProvider).getReceipt(item.transferId);
      if (!mounted) return;
      Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => ReceiptScreen(receipt: receipt)),
      );
    } catch (_) {
      if (!mounted) return;
      final fallbackReceipt = ReceiptModel(
        receiptNumber: 'REC-${item.reference}',
        transferId: item.transferId,
        reference: item.reference,
        status: item.status.name.toUpperCase(),
        amount: item.amount,
        totalDebited: item.amount,
        receiver: ReceiptPartyModel(displayName: item.receiverDisplayName ?? 'Payee'),
        note: item.note,
        createdAt: item.createdAt,
        simulated: item.simulated,
      );
      Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => ReceiptScreen(receipt: fallbackReceipt)),
      );
    }
  }

  bool _isIncomingTransfer(TransferModel item, String? currentUserId, String? currentUserPhone) {
    if (currentUserId != null && currentUserId.isNotEmpty && item.receiverId != null) {
      if (item.receiverId.toString() == currentUserId) return true;
    }
    if (currentUserPhone != null && currentUserPhone.isNotEmpty && item.senderPhone != null) {
      if (item.senderPhone != currentUserPhone) return true;
    }
    final receiver = item.receiverDisplayName?.toLowerCase() ?? '';
    final note = item.note?.toLowerCase() ?? '';
    if (receiver.contains('received') || note.contains('received') || receiver.contains('from')) {
      return true;
    }
    return false;
  }

  String _formatStatusLabel(TransferStatusType status) {
    switch (status) {
      case TransferStatusType.completed:
        return 'Completed';
      case TransferStatusType.pending:
      case TransferStatusType.processing:
      case TransferStatusType.requiresReconciliation:
        return 'Pending';
      case TransferStatusType.failed:
        return 'Failed';
      case TransferStatusType.reversed:
      case TransferStatusType.reversalPending:
        return 'Reversed';
      default:
        return 'Completed';
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GradientHeaderScaffold(
      scrollable: false,
      bodyPadding: EdgeInsets.zero,
      header: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildHeaderRow(context, isDark),
          const SizedBox(height: 16),
          _buildSummary(context, isDark),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refreshAll,
        color: AppTokens.primaryViolet,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.symmetric(
            horizontal: AppTokens.screenHorizontalPadding,
            vertical: AppTokens.space16,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 3. ACCOUNTS Section
              _buildAccountsSection(context, isDark),
              const SizedBox(height: 20),

              // 4. QUICK ACTIONS Section
              _buildQuickActionsSection(context, isDark),
              const SizedBox(height: 20),

              // 5. LAST TRANSACTIONS Section
              _buildLastTransactionsSection(context, isDark),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 1. Header Row
  // ---------------------------------------------------------------------------
  Widget _buildHeaderRow(BuildContext context, bool isDark) {
    final profile = ref.watch(paymentProfileProvider);
    final safeName = profile.safeName;
    final firstName = (safeName != null && safeName.trim().isNotEmpty)
        ? safeName.trim().split(' ').first
        : 'User';

    final unreadAsync = ref.watch(unreadNotificationCountProvider);
    final unreadCount = unreadAsync.value ?? 0;
    final hasUnread = unreadCount > 0;

    final iconColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        GestureDetector(
          key: const Key('home_avatar'),
          onTap: () => AvatarSheet.show(context),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AppAvatar(
                name: safeName ?? firstName,
                size: 42,
              ),
              const SizedBox(width: 12),
              Text(
                'Hello, $firstName',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                  color: iconColor,
                  letterSpacing: -0.2,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
        const Spacer(),
        IconButton(
          key: const Key('home_search'),
          icon: Icon(Icons.search_rounded, color: iconColor, size: 24),
          tooltip: 'Search',
          onPressed: () => context.push('/search'),
        ),
        KeyedSubtree(
          key: const Key('home_notifications_icon_btn'),
          child: IconButton(
            key: const Key('home_bell'),
            tooltip: 'Notifications',
            icon: Stack(
              clipBehavior: Clip.none,
              children: [
                Icon(
                  Icons.notifications_none_rounded,
                  color: iconColor,
                  size: 24,
                ),
                if (hasUnread)
                  Positioned(
                    right: 0,
                    top: 0,
                    child: Container(
                      key: const Key('home_bell_unread_dot'),
                      width: 9,
                      height: 9,
                      decoration: BoxDecoration(
                        color: AppTokens.error,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: isDark ? AppTokens.backgroundDark : AppTokens.lightHeaderStart,
                          width: 1.5,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            onPressed: () => context.push('/notifications'),
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // 2. Summary: Simulated balance / Received this month
  // ---------------------------------------------------------------------------
  Widget _buildSummary(BuildContext context, bool isDark) {
    final simulatedBalance = ref.watch(simulatedBalanceProvider);
    final monthlyReceivedAsync = ref.watch(monthlyReceivedProvider);

    final isSimulated = simulatedBalance != null;
    final amount = isSimulated ? simulatedBalance : (monthlyReceivedAsync.value ?? 0.0);
    final formattedAmount = 'LKR ${NumberFormat('#,##0.00').format(amount)}';

    final labelColor = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;
    final valueColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              isSimulated ? 'Simulated balance' : 'Received this month',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: labelColor,
              ),
            ),
            if (isSimulated) ...[
              const SizedBox(width: 8),
              const SimulatedBadge(compact: true),
            ],
          ],
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            Text(
              _isSummaryHidden ? '••••••••' : formattedAmount,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 28,
                fontWeight: FontWeight.w800,
                color: valueColor,
                letterSpacing: -0.4,
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              key: const Key('home_toggle_balance'),
              icon: Icon(
                _isSummaryHidden ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                color: labelColor,
                size: 20,
              ),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              tooltip: 'Toggle balance',
              onPressed: () => setState(() => _isSummaryHidden = !_isSummaryHidden),
            ),
          ],
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // 3. ACCOUNTS Section
  // ---------------------------------------------------------------------------
  Widget _buildAccountsSection(BuildContext context, bool isDark) {
    final accountsAsync = ref.watch(bankAccountsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionHeader(
          title: 'ACCOUNTS',
          actionLabel: 'Add +',
          actionKey: const Key('home_add_account'),
          onActionTap: () => context.push('/accounts/add'),
        ),
        const SizedBox(height: 8),
        if (accountsAsync.hasError && !accountsAsync.hasValue)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: ErrorRetry(
              key: const Key('home_accounts_error'),
              message: 'Failed to load bank accounts',
              onRetry: () => ref.refresh(bankAccountsProvider),
            ),
          )
        else
          accountsAsync.when(
            loading: () => const SizedBox(
              height: 200,
              child: SkeletonAccountCard(),
            ),
            error: (error, _) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: ErrorRetry(
                key: const Key('home_accounts_error'),
                message: 'Failed to load bank accounts',
                onRetry: () => ref.refresh(bankAccountsProvider),
              ),
            ),
            data: (accounts) {
            if (accounts.isEmpty) {
              return EmptyState(
                key: const Key('home_accounts_empty'),
                icon: Icons.account_balance_outlined,
                title: 'No linked accounts',
                description: 'Link your bank account to start transacting.',
                actionLabel: 'Add Account',
                actionKey: const Key('home_add_account_empty_btn'),
                onActionTap: () => context.push('/accounts/add'),
              );
            }

            final sorted = List<BankAccountModel>.from(accounts)
              ..sort((a, b) => (b.isDefault ? 1 : 0).compareTo(a.isDefault ? 1 : 0));

            return SizedBox(
              height: 200,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                physics: const BouncingScrollPhysics(),
                itemCount: sorted.length,
                separatorBuilder: (_, _) => const SizedBox(width: AppTokens.space12),
                itemBuilder: (context, index) {
                  final acc = sorted[index];
                  final isSingle = sorted.length == 1;
                  final width = isSingle
                      ? MediaQuery.of(context).size.width - (AppTokens.screenHorizontalPadding * 2)
                      : 310.0;

                  return SizedBox(
                    width: width,
                    child: AccountCard(
                      key: Key('home_account_${acc.id}'),
                      bankName: acc.institutionCode,
                      nickname: acc.label ?? acc.accountName,
                      maskedNumber: acc.maskedNumber,
                      balance: 'LKR 125,450.00',
                      isDefault: acc.isDefault,
                      style: index.isEven ? AccountCardStyle.charcoal : AccountCardStyle.lavender,
                      onTap: () => context.push('/accounts'),
                    ),
                  );
                },
              ),
            );
          },
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // 4. QUICK ACTIONS Section
  // ---------------------------------------------------------------------------
  Widget _buildQuickActionsSection(BuildContext context, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SectionHeader(title: 'QUICK ACTIONS'),
        const SizedBox(height: 8),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          physics: const BouncingScrollPhysics(),
          child: Row(
            children: [
              KeyedSubtree(
                key: const Key('home_quick_send_btn'),
                child: QuickActionTile(
                  key: const Key('qa_send'),
                  icon: Icons.send_rounded,
                  label: 'Send money',
                  gradient: QuickActionGradient.violet,
                  width: 86,
                  height: 96,
                  onTap: () => context.push('/send'),
                ),
              ),
              const SizedBox(width: AppTokens.space12),
              KeyedSubtree(
                key: const Key('home_quick_scan_btn'),
                child: QuickActionTile(
                  key: const Key('qa_scan'),
                  icon: Icons.qr_code_scanner_rounded,
                  label: 'Scan QR',
                  gradient: QuickActionGradient.orange,
                  width: 86,
                  height: 96,
                  onTap: () => context.push('/qr'),
                ),
              ),
              const SizedBox(width: AppTokens.space12),
              KeyedSubtree(
                key: const Key('home_quick_request_btn'),
                child: QuickActionTile(
                  key: const Key('qa_request'),
                  icon: Icons.call_received_rounded,
                  label: 'Request money',
                  gradient: QuickActionGradient.pink,
                  width: 86,
                  height: 96,
                  onTap: () => context.push('/qr'),
                ),
              ),
              const SizedBox(width: AppTokens.space12),
              QuickActionTile(
                key: const Key('qa_my_qr'),
                icon: Icons.qr_code_2_rounded,
                label: 'My payment ID/QR',
                gradient: QuickActionGradient.cyan,
                width: 86,
                height: 96,
                onTap: () => context.push('/qr'),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // 5. LAST TRANSACTIONS Section
  // ---------------------------------------------------------------------------
  Widget _buildLastTransactionsSection(BuildContext context, bool isDark) {
    final recentAsync = ref.watch(recentTransactionsProvider);
    final authState = ref.watch(authStateProvider);
    final String? currentUserId = authState is AuthAuthenticated ? authState.userId : null;
    final String? currentUserPhone = authState is AuthAuthenticated ? authState.phone : null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KeyedSubtree(
          key: const Key('home_view_all_history_btn'),
          child: SectionHeader(
            title: 'LAST TRANSACTIONS',
            actionLabel: 'See all >',
            actionKey: const Key('home_see_all'),
            onActionTap: () => context.push('/history'),
          ),
        ),
        Text(
          'Recent Activity',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        const SizedBox(height: 6),
        if (recentAsync.hasError && !recentAsync.hasValue)
          ErrorRetry(
            key: const Key('home_transactions_error'),
            message: 'Could not load recent transactions',
            onRetry: () => ref.refresh(recentTransactionsProvider),
          )
        else
          recentAsync.when(
            loading: () => const Column(
              key: Key('home_transactions_loading'),
              children: [
                SkeletonTransactionTile(key: Key('skeleton_tx_0')),
                SkeletonTransactionTile(key: Key('skeleton_tx_1')),
                SkeletonTransactionTile(key: Key('skeleton_tx_2')),
              ],
            ),
            error: (error, _) => ErrorRetry(
              key: const Key('home_transactions_error'),
              message: 'Could not load recent transactions',
              onRetry: () => ref.refresh(recentTransactionsProvider),
            ),
            data: (transfers) {
            if (transfers.isEmpty) {
              return const EmptyState(
                key: Key('home_transactions_empty'),
                icon: Icons.receipt_long_outlined,
                title: 'No transactions yet',
                description: 'Your recent payments and transfers will appear here.',
              );
            }

            final recent = transfers.take(5).toList();

            return Column(
              children: recent.map((item) {
                final isIncoming = _isIncomingTransfer(item, currentUserId, currentUserPhone);
                final isConfirmedSuccess = item.status.isConfirmedSuccess;
                final isPositive = isIncoming && isConfirmedSuccess;
                final prefix = isPositive ? '+' : '-';
                final amountText = '$prefix LKR ${NumberFormat('#,##0.00').format(item.amount)}';
                final statusLabel = _formatStatusLabel(item.status);
                final subtitle = (item.createdAt != null && item.createdAt!.length >= 10)
                    ? item.createdAt!.substring(0, 10)
                    : 'Recent transfer';

                return TransactionTile(
                  key: Key('tx_${item.transferId}'),
                  icon: isIncoming ? Icons.south_west_rounded : Icons.north_east_rounded,
                  title: item.receiverDisplayName ?? (item.senderPhone != null ? 'From ${item.senderPhone}' : item.reference),
                  subtitle: subtitle,
                  amount: amountText,
                  status: statusLabel,
                  isPositive: isPositive,
                  onTap: () => _openTransactionDetail(item),
                );
              }).toList(),
            );
          },
        ),
      ],
    );
  }
}
