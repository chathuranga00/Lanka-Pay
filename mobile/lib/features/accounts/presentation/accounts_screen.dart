import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/account_card.dart';
import '../../../core/widgets/action_button.dart';
import '../../../core/widgets/gradient_header_scaffold.dart';
import '../../../core/widgets/shimmer_skeletons.dart';
import '../../../core/widgets/simulated_badge.dart';
import '../../../core/widgets/transaction_tile.dart';
import '../../../l10n/app_localizations.dart';
import '../../history/data/history_service.dart';
import '../../history/presentation/receipt_screen.dart';
import '../../home/data/home_providers.dart';
import '../../send/domain/transfer_models.dart';
import 'package:intl/intl.dart';
import '../../../core/test_keys.dart';
import '../../../core/widgets/empty_state.dart';
import '../../cards/data/card_service.dart';
import '../../cards/domain/card_model.dart';
import '../../cards/presentation/widgets/card_details_sheet.dart';
import '../../cards/presentation/widgets/card_settings_sheet.dart';
import '../../cards/presentation/widgets/step_up_pin_dialog.dart';
import '../data/bank_account_service.dart';
import '../domain/bank_account_model.dart';

class AccountsScreen extends ConsumerStatefulWidget {
  const AccountsScreen({super.key});

  @override
  ConsumerState<AccountsScreen> createState() => _AccountsScreenState();
}

class _AccountsScreenState extends ConsumerState<AccountsScreen> with WidgetsBindingObserver {
  int _selectedSegment = 0; // 0: Accounts, 1: Cards
  late PageController _pageController;
  late PageController _cardPageController;
  int _selectedIndex = 0;
  int _selectedCardIndex = 0;
  bool _digitsRevealed = false;
  bool _cardDigitsRevealed = false;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(viewportFraction: 0.85);
    _cardPageController = PageController(viewportFraction: 0.85);
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pageController.dispose();
    _cardPageController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.hidden) {
      if (_digitsRevealed && mounted) {
        setState(() {
          _digitsRevealed = false;
        });
      }
      if (_cardDigitsRevealed && mounted) {
        setState(() {
          _cardDigitsRevealed = false;
        });
      }
    }
  }

  Future<void> _refreshAll() async {
    ref.invalidate(bankAccountsProvider);
    ref.invalidate(cardsProvider);
    ref.invalidate(recentTransactionsProvider);
  }

  void _showRenameDialog(BankAccountModel account) {
    final controller = TextEditingController(text: account.label ?? '');
    final messenger = ScaffoldMessenger.of(context);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Rename Account'),
        content: TextField(
          key: const Key('rename_account_field'),
          controller: controller,
          decoration: const InputDecoration(
            labelText: 'Account Label (Private)',
            hintText: 'e.g. My Primary Savings',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            key: const Key('rename_account_save_btn'),
            onPressed: () async {
              Navigator.pop(ctx);
              try {
                await ref.read(bankAccountsProvider.notifier).patchAccount(
                  id: account.id,
                  label: controller.text.trim(),
                );
                if (mounted) {
                  messenger.clearSnackBars();
                  messenger.showSnackBar(
                    const SnackBar(content: Text('Account renamed successfully')),
                  );
                }
              } catch (e) {
                if (mounted) {
                  messenger.clearSnackBars();
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text('Failed to rename account: $e'),
                      backgroundColor: AppColors.error,
                    ),
                  );
                }
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  void _showRemoveConfirmation(BankAccountModel account) {
    final messenger = ScaffoldMessenger.of(context);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Remove Bank Account?'),
        content: Text(
          'Are you sure you want to remove ${account.accountName} (${account.maskedNumber})? It will no longer send or receive transfers.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            key: const Key('confirm_remove_account_btn'),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.error),
            onPressed: () async {
              Navigator.pop(ctx);
              try {
                await ref.read(bankAccountsProvider.notifier).removeAccount(account.id);
                if (mounted) {
                  messenger.clearSnackBars();
                  messenger.showSnackBar(
                    const SnackBar(content: Text('Account removed successfully')),
                  );
                }
              } catch (e) {
                if (mounted) {
                  messenger.clearSnackBars();
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text('Failed to remove account: $e'),
                      backgroundColor: AppColors.error,
                    ),
                  );
                }
              }
            },
            child: const Text('Remove'),
          ),
        ],
      ),
    );
  }

  void _showPauseResumeDialog(BankAccountModel account) {
    final willPause = account.receivingEnabled;
    final messenger = ScaffoldMessenger.of(context);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(willPause ? 'Pause Receiving?' : 'Resume Receiving?'),
        content: Text(
          willPause
              ? 'Pause receiving transfers for ${account.accountName} (${account.maskedNumber})? Senders will not be able to transfer funds to this account until resumed.'
              : 'Resume receiving transfers for ${account.accountName} (${account.maskedNumber})? Senders will be able to transfer funds to this account.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            key: willPause
                ? const Key('confirm_pause_btn')
                : const Key('confirm_resume_btn'),
            style: willPause
                ? ElevatedButton.styleFrom(backgroundColor: AppColors.accentGoldDark)
                : null,
            onPressed: () async {
              Navigator.pop(ctx);
              try {
                await ref.read(bankAccountsProvider.notifier).toggleReceiving(
                  account.id,
                  !willPause,
                );
                if (mounted) {
                  messenger.clearSnackBars();
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(
                        willPause
                            ? 'Receiving paused for this account'
                            : 'Receiving resumed for this account',
                      ),
                    ),
                  );
                }
              } catch (e) {
                if (mounted) {
                  messenger.clearSnackBars();
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text('Failed to update receiving: $e'),
                      backgroundColor: AppColors.error,
                    ),
                  );
                }
              }
            },
            child: Text(willPause ? 'Pause' : 'Resume'),
          ),
        ],
      ),
    );
  }

  void _showAccountSettingsSheet(BankAccountModel account) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final messenger = ScaffoldMessenger.of(context);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Drag Handle
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Title Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          account.label?.isNotEmpty == true
                              ? account.label!
                              : account.accountName,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${account.institutionCode} • ${account.maskedNumber}',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 13,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SimulatedBadge(compact: true),
                ],
              ),
              const SizedBox(height: 16),
              const Divider(height: 1),
              const SizedBox(height: 8),

              // 1. Verification status
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: AppTokens.primaryViolet.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.verified_outlined,
                    color: AppTokens.primaryViolet,
                    size: 20,
                  ),
                ),
                title: const Text(
                  'Verification Status',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                subtitle: Text(
                  'Status: ${account.status}',
                  style: const TextStyle(fontSize: 12),
                ),
                trailing: TextButton(
                  key: const Key('reverify_account_btn'),
                  onPressed: () {
                    messenger.clearSnackBars();
                    messenger.showSnackBar(
                      SnackBar(content: Text('Verification status refreshed: ${account.status}')),
                    );
                  },
                  child: const Text('Re-verify'),
                ),
              ),

              // 2. Rename
              ListTile(
                key: const Key('settings_rename_btn'),
                contentPadding: EdgeInsets.zero,
                leading: Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    Icons.edit_outlined,
                    color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                    size: 20,
                  ),
                ),
                title: const Text(
                  'Rename (Private label)',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                subtitle: Text(
                  account.label?.isNotEmpty == true ? account.label! : 'Tap to add nickname',
                  style: const TextStyle(fontSize: 12),
                ),
                trailing: const Icon(Icons.chevron_right_rounded, size: 20),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _showRenameDialog(account);
                },
              ),

              // 3. Set as default
              if (!account.isDefault)
                ListTile(
                  key: const Key('settings_set_default_btn'),
                  contentPadding: EdgeInsets.zero,
                  leading: Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(
                      color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.star_outline_rounded,
                      color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                      size: 20,
                    ),
                  ),
                  title: const Text(
                    'Set as Default Account',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  subtitle: const Text(
                    'Primary account used for incoming payments',
                    style: TextStyle(fontSize: 12),
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded, size: 20),
                  onTap: () async {
                    Navigator.pop(sheetContext);
                    try {
                      await ref.read(bankAccountsProvider.notifier).setDefault(account.id);
                      if (mounted) {
                        messenger.clearSnackBars();
                        messenger.showSnackBar(
                          const SnackBar(content: Text('Account set as default')),
                        );
                      }
                    } catch (e) {
                      if (mounted) {
                        messenger.clearSnackBars();
                        messenger.showSnackBar(
                          SnackBar(
                            content: Text('Failed to set default: $e'),
                            backgroundColor: AppColors.error,
                          ),
                        );
                      }
                    }
                  },
                ),

              // 4. Discoverable toggle
              SwitchListTile(
                key: const Key('settings_toggle_discoverable'),
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text(
                  'Discoverable in Registry',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                subtitle: const Text(
                  'Allow payers to discover this account via phone lookup',
                  style: TextStyle(fontSize: 12),
                ),
                value: account.discoverable,
                onChanged: (val) async {
                  try {
                    await ref.read(bankAccountsProvider.notifier).patchAccount(
                      id: account.id,
                      discoverable: val,
                    );
                    if (mounted) {
                      messenger.clearSnackBars();
                      messenger.showSnackBar(
                        const SnackBar(content: Text('Discoverability updated')),
                      );
                    }
                  } catch (e) {
                    if (mounted) {
                      messenger.clearSnackBars();
                      messenger.showSnackBar(
                        SnackBar(
                          content: Text('Failed to update discoverability: $e'),
                          backgroundColor: AppColors.error,
                        ),
                      );
                    }
                  }
                },
              ),

              // 5. Receiving toggle
              SwitchListTile(
                key: const Key('settings_toggle_receiving'),
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text(
                  'Enable Receiving',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                subtitle: const Text(
                  'Accept incoming transfers to this account',
                  style: TextStyle(fontSize: 12),
                ),
                value: account.receivingEnabled,
                onChanged: (val) async {
                  try {
                    await ref.read(bankAccountsProvider.notifier).toggleReceiving(
                      account.id,
                      val,
                    );
                    if (mounted) {
                      messenger.clearSnackBars();
                      messenger.showSnackBar(
                        const SnackBar(content: Text('Receiving status updated')),
                      );
                    }
                  } catch (e) {
                    if (mounted) {
                      messenger.clearSnackBars();
                      messenger.showSnackBar(
                        SnackBar(
                          content: Text('Failed to update receiving: $e'),
                          backgroundColor: AppColors.error,
                        ),
                      );
                    }
                  }
                },
              ),

              const SizedBox(height: 8),
              const Divider(height: 1),
              const SizedBox(height: 8),

              // 6. Remove account
              ListTile(
                key: const Key('settings_remove_btn'),
                contentPadding: EdgeInsets.zero,
                leading: Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: AppColors.error.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.delete_outline_rounded,
                    color: AppColors.error,
                    size: 20,
                  ),
                ),
                title: const Text(
                  'Remove Account',
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                    color: AppColors.error,
                  ),
                ),
                subtitle: const Text(
                  'Disconnect this simulated account from LankaPay ID',
                  style: TextStyle(fontSize: 12),
                ),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _showRemoveConfirmation(account);
                },
              ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      ),
    );
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

  bool _matchesAccount(TransferModel tx, BankAccountModel account) {
    if (tx.senderAccountId != null || tx.receiverAccountId != null) {
      return tx.senderAccountId == account.id || tx.receiverAccountId == account.id;
    }
    if (tx.bankTxRef != null && (tx.bankTxRef == account.maskedNumber || tx.bankTxRef == account.institutionCode)) return true;
    if (tx.note != null && tx.note!.contains(account.maskedNumber)) return true;
    return false;
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(cardsProvider);
    if (_selectedSegment == 1) {
      return _buildCardsScreen(context);
    }
    return _buildAccountsScreen(context);
  }

  Widget _buildAccountsScreen(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accountsAsync = ref.watch(bankAccountsProvider);
    final simulatedBalance = ref.watch(simulatedBalanceProvider);

    if (accountsAsync.hasError && !accountsAsync.hasValue) {
      return Scaffold(
        appBar: AppBar(
          title: Text(l10n?.navAccounts ?? 'Accounts'),
          actions: const [
            Center(child: SimulatedBadge(compact: true)),
            SizedBox(width: 16),
          ],
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 54, color: AppColors.error),
                const SizedBox(height: 16),
                const Text(
                  'Failed to load linked accounts',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                Text(
                  accountsAsync.error.toString(),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.grey, fontSize: 13),
                  maxLines: 2,
                ),
                const SizedBox(height: 20),
                ElevatedButton.icon(
                  onPressed: () => ref.refresh(bankAccountsProvider),
                  icon: const Icon(Icons.refresh_rounded),
                  label: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return accountsAsync.when(
      loading: () => GradientHeaderScaffold(
        scrollable: false,
        header: _buildLoadingHeader(context, isDark, l10n),
        body: const Padding(
          padding: EdgeInsets.symmetric(horizontal: 20, vertical: 24),
          child: Column(
            children: [
              SkeletonAccountCard(key: Key('skeleton_account_card_1')),
              SizedBox(height: 24),
              SkeletonAccountCard(key: Key('skeleton_account_card_2')),
            ],
          ),
        ),
      ),
      error: (err, stack) => Scaffold(
        appBar: AppBar(
          title: Text(l10n?.navAccounts ?? 'Accounts'),
          actions: const [
            Center(child: SimulatedBadge(compact: true)),
            SizedBox(width: 16),
          ],
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 54, color: AppColors.error),
                const SizedBox(height: 16),
                const Text(
                  'Failed to load linked accounts',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                Text(
                  err.toString(),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.grey, fontSize: 13),
                  maxLines: 2,
                ),
                const SizedBox(height: 20),
                ElevatedButton.icon(
                  onPressed: () => ref.read(bankAccountsProvider.notifier).refresh(),
                  icon: const Icon(Icons.refresh_rounded),
                  label: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
      ),
      data: (accounts) {
        if (accounts.isEmpty) {
          return GradientHeaderScaffold(
            scrollable: true,
            header: _buildTopHeader(
              context,
              isDark,
              l10n,
              activeReceivingCount: 0,
              simulatedBalance: null,
            ),
            body: RefreshIndicator(
              onRefresh: _refreshAll,
              color: AppTokens.primaryViolet,
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 40.0),
                  child: Center(
                    child: Card(
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(20),
                        side: BorderSide(
                          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                        ),
                      ),
                      color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
                      child: Padding(
                        padding: const EdgeInsets.all(28.0),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 64,
                              height: 64,
                              decoration: BoxDecoration(
                                color: AppTokens.primaryViolet.withOpacity(0.12),
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(
                                Icons.account_balance_outlined,
                                size: 32,
                                color: AppTokens.primaryViolet,
                              ),
                            ),
                            const SizedBox(height: 18),
                            Text(
                              'No Bank Accounts Linked',
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 18,
                                fontWeight: FontWeight.w700,
                                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Link your simulated Sri Lankan bank accounts to start sending and receiving funds via LankaPay ID.',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 13,
                                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                                height: 1.4,
                              ),
                            ),
                            const SizedBox(height: 24),
                            ElevatedButton.icon(
                              key: const Key('empty_add_account_btn'),
                              onPressed: () => context.push('/accounts/add'),
                              icon: const Icon(Icons.add_rounded),
                              label: const Text('Link your first account'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          );
        }

        final safeIndex = _selectedIndex.clamp(0, accounts.length - 1);
        final selectedAccount = accounts[safeIndex];
        final activeReceivingCount = accounts.where((a) => a.receivingEnabled && a.status == 'ACTIVE').length;

        final recentTransfersAsync = ref.watch(recentTransactionsProvider);
        final allTransfers = recentTransfersAsync.value ?? [];
        final filteredTransfers = allTransfers.where((t) => _matchesAccount(t, selectedAccount)).toList();

        return GradientHeaderScaffold(
          scrollable: false,
          bodyPadding: EdgeInsets.zero,
          header: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top Title & Add Button
              _buildTopHeader(
                context,
                isDark,
                l10n,
                activeReceivingCount: activeReceivingCount,
                simulatedBalance: simulatedBalance,
              ),
              const SizedBox(height: 16),

              // PageView Carousel of AccountCards
              SizedBox(
                height: 200,
                child: PageView.builder(
                  controller: _pageController,
                  itemCount: accounts.length,
                  onPageChanged: (index) {
                    HapticFeedback.lightImpact();
                    setState(() {
                      _selectedIndex = index;
                    });
                  },
                  itemBuilder: (context, index) {
                    final account = accounts[index];
                    final isCharcoal = index.isEven;
                    final isCurrent = index == safeIndex;
                    final reduceMotion = MediaQuery.of(context).disableAnimations;

                    return AnimatedScale(
                      scale: (!reduceMotion && !isCurrent) ? 0.94 : 1.0,
                      duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 250),
                      curve: Curves.easeOutCubic,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 8.0),
                        child: AccountCard(
                          bankName: '${account.institutionCode} (SIMULATED)',
                          nickname: account.label?.isNotEmpty == true
                              ? account.label!
                              : account.accountName,
                          maskedNumber: account.maskedNumber,
                          isDefault: account.isDefault,
                          isDiscoverable: account.discoverable,
                          isPaused: !account.receivingEnabled,
                          style: isCharcoal ? AccountCardStyle.charcoal : AccountCardStyle.lavender,
                          showDigitToggle: true,
                          isDigitsRevealed: _digitsRevealed,
                          onToggleVisibility: () {
                            setState(() {
                              _digitsRevealed = !_digitsRevealed;
                            });
                          },
                        ),
                      ),
                    );
                  },
                ),
              ),
              const SizedBox(height: 12),

              // Page Indicator Dots
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(accounts.length, (i) {
                  final isSelected = i == safeIndex;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    width: isSelected ? 22 : 7,
                    height: 7,
                    decoration: BoxDecoration(
                      color: isSelected
                          ? AppTokens.primaryViolet
                          : (isDark ? Colors.white.withOpacity(0.3) : Colors.black.withOpacity(0.2)),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  );
                }),
              ),
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
                  // Three ActionButtons for SELECTED account
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      // 1. Link account
                      ActionButton(
                        key: const Key('btn_add_account'),
                        icon: Icons.add_rounded,
                        label: 'Link account',
                        onTap: () => context.push('/accounts/add'),
                      ),

                      // 2. Pause receiving / Resume
                      ActionButton(
                        key: const Key('btn_freeze'),
                        icon: Icons.ac_unit_rounded,
                        label: selectedAccount.receivingEnabled ? 'Pause receiving' : 'Resume',
                        isActive: !selectedAccount.receivingEnabled,
                        onTap: () => _showPauseResumeDialog(selectedAccount),
                      ),

                      // 3. Settings
                      ActionButton(
                        key: const Key('btn_account_settings'),
                        icon: Icons.settings_outlined,
                        label: 'Settings',
                        onTap: () => _showAccountSettingsSheet(selectedAccount),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),

                  // Recent Activity Header
                  Text(
                    'Recent activity',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Filtered Transactions or EmptyState
                  if (filteredTransfers.isEmpty)
                    Container(
                      key: const Key('accounts_no_transactions'),
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      decoration: BoxDecoration(
                        color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                          width: 1.0,
                        ),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              color: isDark
                                  ? Colors.white.withOpacity(0.08)
                                  : Colors.black.withOpacity(0.06),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            alignment: Alignment.center,
                            child: Icon(
                              Icons.credit_card_rounded,
                              size: 22,
                              color: isDark ? Colors.white60 : Colors.black45,
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Text(
                              'No transaction yet',
                              style: TextStyle(
                                fontFamily: AppTokens.fontFamily,
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                              ),
                            ),
                          ),
                        ],
                      ),
                    )
                  else
                    ...filteredTransfers.map((tx) {
                      final isIncoming = tx.receiverDisplayName != null &&
                          tx.receiverDisplayName!.toLowerCase() == selectedAccount.accountName.toLowerCase();
                      return TransactionTile(
                        icon: isIncoming ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded,
                        title: tx.receiverDisplayName ?? tx.reference,
                        subtitle: tx.createdAt ?? 'Recent',
                        amount: '${isIncoming ? '+' : '-'}LKR ${tx.amount.toStringAsFixed(2)}',
                        status: tx.status.name.toUpperCase(),
                        isPositive: isIncoming,
                        onTap: () => _openTransactionDetail(tx),
                      );
                    }),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  // ---------------------------------------------------------------------------
  // Top Header Area
  // ---------------------------------------------------------------------------
  Widget _buildTopHeader(
    BuildContext context,
    bool isDark,
    AppLocalizations? l10n, {
    required int activeReceivingCount,
    required double? simulatedBalance,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Title Row with + Button
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              l10n?.navAccounts ?? 'Accounts',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 24,
                fontWeight: FontWeight.w800,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                letterSpacing: -0.5,
              ),
            ),
            Row(
              children: [
                const SimulatedBadge(compact: true),
                const SizedBox(width: 4),
                IconButton(
                  key: const Key('accounts_add'),
                  icon: const Icon(Icons.add_rounded),
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                  onPressed: () => context.push('/accounts/add'),
                  tooltip: 'Link new account',
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 10),

        // Segmented Control (Accounts | Cards)
        _buildSegmentedControl(isDark),
        const SizedBox(height: 12),

        // Receiving / Linked Label
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Text(
              'Receiving',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ),
            const SizedBox(width: 6),
            const SimulatedBadge(compact: true),
          ],
        ),
        const SizedBox(height: 2),

        // Big Number (count of active receiving accounts or simulated balance)
        Text(
          simulatedBalance != null
              ? 'LKR ${simulatedBalance.toStringAsFixed(2)}'
              : '$activeReceivingCount',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 34,
            fontWeight: FontWeight.w900,
            letterSpacing: -1.0,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
      ],
    );
  }

  Widget _buildLoadingHeader(BuildContext context, bool isDark, AppLocalizations? l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              l10n?.navAccounts ?? 'Accounts',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 24,
                fontWeight: FontWeight.w800,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
            const SimulatedBadge(compact: true),
          ],
        ),
        const SizedBox(height: 10),
        _buildSegmentedControl(isDark),
        const SizedBox(height: 12),
        const Text(
          'Receiving',
          style: TextStyle(fontSize: 13, color: Colors.grey),
        ),
        const SizedBox(height: 4),
        const Text(
          '--',
          style: TextStyle(fontSize: 34, fontWeight: FontWeight.w900),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // Segmented Control (Accounts | Cards)
  // ---------------------------------------------------------------------------
  Widget _buildSegmentedControl(bool isDark) {
    return Container(
      height: 38,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: isDark ? Colors.white.withOpacity(0.08) : Colors.black.withOpacity(0.06),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildSegmentItem(
            index: 0,
            label: 'Accounts',
            key: AppTestKeys.segAccounts,
            isDark: isDark,
          ),
          _buildSegmentItem(
            index: 1,
            label: 'Cards',
            key: AppTestKeys.segCards,
            isDark: isDark,
          ),
        ],
      ),
    );
  }

  Widget _buildSegmentItem({
    required int index,
    required String label,
    required Key key,
    required bool isDark,
  }) {
    final isSelected = _selectedSegment == index;
    return InkWell(
      key: key,
      onTap: () {
        HapticFeedback.lightImpact();
        setState(() => _selectedSegment = index);
      },
      borderRadius: BorderRadius.circular(8),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected
              ? (isDark ? AppTokens.mutedSurfaceDark : Colors.white)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.08),
                    blurRadius: 4,
                    offset: const Offset(0, 1),
                  ),
                ]
              : null,
        ),
        child: Text(
          label,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 13,
            fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
            color: isSelected
                ? (isDark ? Colors.white : AppTokens.textPrimaryLight)
                : (isDark ? Colors.white60 : Colors.black54),
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Cards Experience
  // ---------------------------------------------------------------------------
  Widget _buildCardsScreen(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardsAsync = ref.watch(cardsProvider);

    if (cardsAsync.hasError && !cardsAsync.hasValue) {
      return GradientHeaderScaffold(
        scrollable: true,
        header: _buildCardsHeader(context, isDark, [], null),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 54, color: AppTokens.error),
                const SizedBox(height: 16),
                const Text(
                  'Failed to load cards',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                Text(
                  cardsAsync.error.toString().replaceFirst('Exception: ', ''),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.grey, fontSize: 13),
                  maxLines: 2,
                ),
                const SizedBox(height: 20),
                ElevatedButton.icon(
                  key: AppTestKeys.btnRetry,
                  onPressed: () => ref.read(cardsProvider.notifier).refresh(),
                  icon: const Icon(Icons.refresh_rounded),
                  label: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return cardsAsync.when(
      loading: () => GradientHeaderScaffold(
        scrollable: false,
        header: _buildCardsLoadingHeader(context, isDark),
        body: const Padding(
          padding: EdgeInsets.symmetric(horizontal: 20, vertical: 24),
          child: Column(
            children: [
              SkeletonAccountCard(key: Key('skeleton_card_1')),
              SizedBox(height: 24),
              SkeletonAccountCard(key: Key('skeleton_card_2')),
            ],
          ),
        ),
      ),
      error: (err, stack) => GradientHeaderScaffold(
        scrollable: true,
        header: _buildCardsHeader(context, isDark, [], null),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 54, color: AppTokens.error),
                const SizedBox(height: 16),
                const Text(
                  'Failed to load cards',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                Text(
                  err.toString().replaceFirst('Exception: ', ''),
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.grey, fontSize: 13),
                  maxLines: 2,
                ),
                const SizedBox(height: 20),
                ElevatedButton.icon(
                  key: AppTestKeys.btnRetry,
                  onPressed: () => ref.read(cardsProvider.notifier).refresh(),
                  icon: const Icon(Icons.refresh_rounded),
                  label: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
      ),
      data: (cards) {
        if (cards.isEmpty) {
          return GradientHeaderScaffold(
            scrollable: true,
            header: _buildCardsHeader(context, isDark, [], null),
            body: RefreshIndicator(
              onRefresh: _refreshAll,
              color: AppTokens.primaryViolet,
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                child: _buildEmptyCardsView(context, isDark),
              ),
            ),
          );
        }

        final safeIndex = _selectedCardIndex.clamp(0, cards.length - 1);
        final selectedCard = cards[safeIndex];

        return GradientHeaderScaffold(
          scrollable: false,
          bodyPadding: EdgeInsets.zero,
          header: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildCardsHeader(context, isDark, cards, selectedCard),
              const SizedBox(height: 16),
              _buildCardsCarousel(context, isDark, cards),
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
                  _buildCardActionsRow(selectedCard),
                  const SizedBox(height: 24),
                  Text(
                    'Recent activity',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                  const SizedBox(height: 12),
                  _buildNoTransactionsWidget(isDark),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildCardsHeader(
    BuildContext context,
    bool isDark,
    List<CardModel> cards,
    CardModel? selectedCard,
  ) {
    final availableLimit = selectedCard != null
        ? 'LKR ${NumberFormat("#,##0.00").format(selectedCard.dailyLimit)}'
        : '--';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Title Row with + Button
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Cards',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 24,
                fontWeight: FontWeight.w800,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                letterSpacing: -0.5,
              ),
            ),
            Row(
              children: [
                const SimulatedBadge(compact: true),
                const SizedBox(width: 4),
                IconButton(
                  key: AppTestKeys.cardsAdd,
                  icon: const Icon(Icons.add_rounded),
                  color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                  onPressed: () => context.push('/cards/add'),
                  tooltip: 'Add new card',
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 10),

        // Segmented Control
        _buildSegmentedControl(isDark),
        const SizedBox(height: 12),

        // Available Label
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Text(
              'Available',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ),
            const SizedBox(width: 6),
            const SimulatedBadge(compact: true),
          ],
        ),
        const SizedBox(height: 2),

        // Big Number (remaining daily limit of selected card)
        Text(
          availableLimit,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 34,
            fontWeight: FontWeight.w900,
            letterSpacing: -1.0,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
      ],
    );
  }

  Widget _buildCardsLoadingHeader(BuildContext context, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Cards',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 24,
                fontWeight: FontWeight.w800,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
            const SimulatedBadge(compact: true),
          ],
        ),
        const SizedBox(height: 10),
        _buildSegmentedControl(isDark),
        const SizedBox(height: 12),
        const Text(
          'Available',
          style: TextStyle(fontSize: 13, color: Colors.grey),
        ),
        const SizedBox(height: 4),
        const Text(
          '--',
          style: TextStyle(fontSize: 34, fontWeight: FontWeight.w900),
        ),
      ],
    );
  }

  Widget _buildCardsCarousel(BuildContext context, bool isDark, List<CardModel> cards) {
    final safeIndex = _selectedCardIndex.clamp(0, cards.length - 1);

    return Column(
      children: [
        SizedBox(
          height: 200,
          child: PageView.builder(
            controller: _cardPageController,
            itemCount: cards.length,
            onPageChanged: (index) {
              HapticFeedback.lightImpact();
              setState(() {
                _selectedCardIndex = index;
              });
            },
            itemBuilder: (context, index) {
              final card = cards[index];
              final isCharcoal = index.isEven;
              final isCurrent = index == safeIndex;
              final reduceMotion = MediaQuery.of(context).disableAnimations;

              String? statusBadge;
              if (card.isFrozen) {
                statusBadge = 'FROZEN';
              } else if (card.isExpired) {
                statusBadge = 'EXPIRED';
              } else if (card.isPendingVerification) {
                statusBadge = 'PENDING VERIFICATION';
              }

              return AnimatedScale(
                scale: (!reduceMotion && !isCurrent) ? 0.94 : 1.0,
                duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 250),
                curve: Curves.easeOutCubic,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8.0),
                  child: AccountCard(
                    key: Key('card_${card.id}'),
                    bankName: 'TEST CARD',
                    nickname: card.nickname?.isNotEmpty == true
                        ? card.nickname!
                        : (card.isVirtual ? 'Virtual Card' : 'Linked Card'),
                    maskedNumber: '•••• •••• •••• ${card.last4}',
                    expiryText: card.formattedExpiry,
                    style: isCharcoal ? AccountCardStyle.charcoal : AccountCardStyle.lavender,
                    isPaused: card.isFrozen,
                    pausedText: 'Frozen',
                    statusBadgeText: statusBadge,
                    showDigitToggle: true,
                    hideDigitsByDefault: true,
                    isDigitsRevealed: _cardDigitsRevealed,
                    onToggleVisibility: () {
                      setState(() {
                        _cardDigitsRevealed = !_cardDigitsRevealed;
                      });
                    },
                    toggleVisibilityKey: AppTestKeys.cardToggleVisibility,
                  ),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 12),

        // Page Indicator Dots
        if (cards.length > 1)
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: List.generate(cards.length, (i) {
              final isSelected = i == safeIndex;
              return AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                margin: const EdgeInsets.symmetric(horizontal: 4),
                width: isSelected ? 22 : 7,
                height: 7,
                decoration: BoxDecoration(
                  color: isSelected
                      ? AppTokens.primaryViolet
                      : (isDark ? Colors.white.withOpacity(0.3) : Colors.black.withOpacity(0.2)),
                  borderRadius: BorderRadius.circular(4),
                ),
              );
            }),
          ),
      ],
    );
  }

  Widget _buildCardActionsRow(CardModel selectedCard) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        // 1. Add card
        ActionButton(
          key: AppTestKeys.btnAddCard,
          icon: Icons.add_rounded,
          label: 'Add card',
          onTap: () => context.push('/cards/add'),
        ),

        // 2. Freeze / Unfreeze
        ActionButton(
          key: AppTestKeys.btnFreezeCard,
          icon: Icons.ac_unit_rounded,
          label: selectedCard.isFrozen ? 'Unfreeze' : 'Freeze',
          isActive: selectedCard.isFrozen,
          onTap: () => _handleCardFreeze(selectedCard),
        ),

        // 3. Details
        ActionButton(
          key: AppTestKeys.btnCardDetails,
          icon: Icons.credit_card_outlined,
          label: 'Details',
          onTap: () => _handleCardDetails(selectedCard),
        ),

        // 4. Settings
        ActionButton(
          key: AppTestKeys.btnCardSettings,
          icon: Icons.settings_outlined,
          label: 'Settings',
          onTap: () => _handleCardSettings(selectedCard),
        ),
      ],
    );
  }

  void _handleCardFreeze(CardModel card) {
    if (!card.isFrozen) {
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Freeze Card?'),
          content: const Text('Transactions on this card will be temporarily blocked.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              key: AppTestKeys.confirmFreezeCardBtn,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTokens.primaryViolet,
                foregroundColor: Colors.white,
              ),
              onPressed: () async {
                Navigator.of(ctx).pop();
                try {
                  await ref.read(cardsProvider.notifier).freezeCard(card.id);
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Card frozen successfully')),
                    );
                  }
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Failed to freeze card: $e')),
                    );
                  }
                }
              },
              child: const Text('Freeze'),
            ),
          ],
        ),
      );
    } else {
      _handleCardUnfreeze(card);
    }
  }

  Future<void> _handleCardUnfreeze(CardModel card) async {
    final pin = await showStepUpPinDialog(
      context,
      title: 'Unfreeze Card',
      subtitle: 'Enter your 6-digit PIN to unfreeze this card.',
    );
    if (pin == null) return;

    try {
      await ref.read(cardsProvider.notifier).unfreezeCard(card.id, pin: pin);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Card unfrozen successfully')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to unfreeze card: $e')),
        );
      }
    }
  }

  Future<void> _handleCardDetails(CardModel card) async {
    if (card.isLinked) {
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Linked Card Details'),
          content: const Text(
            'Card details are never shown for linked physical cards for security purposes.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Got it'),
            ),
          ],
        ),
      );
      return;
    }

    // Virtual card: requires step-up PIN auth
    final pin = await showStepUpPinDialog(
      context,
      title: 'Reveal Card Details',
      subtitle: 'Enter your 6-digit PIN to view sensitive card details.',
    );
    if (pin == null) return;

    if (mounted) {
      CardDetailsSheet.show(context, cardId: card.id, pin: pin);
    }
  }

  void _handleCardSettings(CardModel card) {
    CardSettingsSheet.show(context, card: card);
  }

  Widget _buildEmptyCardsView(BuildContext context, bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 40.0),
      child: EmptyState(
        icon: Icons.credit_card_rounded,
        title: 'Add your first card',
        description: 'Link an existing test card or issue an instant virtual card to get started.',
        actionLabel: 'Add your first card',
        actionKey: AppTestKeys.emptyAddCardBtn,
        onActionTap: () => context.push('/cards/add'),
      ),
    );
  }

  Widget _buildNoTransactionsWidget(bool isDark) {
    return Container(
      key: const Key('accounts_no_transactions'),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.mutedSurfaceDark : AppTokens.mutedSurfaceLight,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
          width: 1.0,
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: isDark
                  ? Colors.white.withOpacity(0.08)
                  : Colors.black.withOpacity(0.06),
              borderRadius: BorderRadius.circular(12),
            ),
            alignment: Alignment.center,
            child: Icon(
              Icons.credit_card_rounded,
              size: 22,
              color: isDark ? Colors.white60 : Colors.black45,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Text(
              'No transaction yet',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
