import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';
import '../data/bank_account_service.dart';
import '../domain/bank_account_model.dart';

class AccountsScreen extends ConsumerWidget {
  const AccountsScreen({super.key});

  void _showRenameDialog(BuildContext context, WidgetRef ref, BankAccountModel account) {
    final controller = TextEditingController(text: account.label ?? '');
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Rename Account'),
        content: TextField(
          key: const Key('rename_account_field'),
          controller: controller,
          decoration: const InputDecoration(
            labelText: 'Account Label',
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
              await ref.read(bankAccountsProvider.notifier).patchAccount(
                id: account.id,
                label: controller.text.trim(),
              );
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  void _showRemoveConfirmation(BuildContext context, WidgetRef ref, BankAccountModel account) {
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
              await ref.read(bankAccountsProvider.notifier).removeAccount(account.id);
            },
            child: const Text('Remove'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accountsAsync = ref.watch(bankAccountsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.navAccounts ?? 'Linked Bank Accounts'),
        actions: [
          IconButton(
            key: const Key('add_bank_account_icon_btn'),
            icon: const Icon(Icons.add_rounded),
            onPressed: () => context.push('/accounts/add'),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: accountsAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (err, stack) => Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline, size: 48, color: AppColors.error),
                      const SizedBox(height: 12),
                      const Text('Failed to load linked accounts'),
                      TextButton(
                        onPressed: () => ref.read(bankAccountsProvider.notifier).refresh(),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
                data: (accounts) {
                  if (accounts.isEmpty) {
                    return Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24.0),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.account_balance_rounded,
                              size: 64,
                              color: isDark ? AppColors.accentGoldLight : AppColors.primaryGreen,
                            ),
                            const SizedBox(height: 16),
                            const Text(
                              'No Bank Accounts Linked',
                              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'Link your simulated Sri Lankan bank accounts to start sending and receiving funds.',
                              textAlign: TextAlign.center,
                              style: TextStyle(color: Colors.grey),
                            ),
                            const SizedBox(height: 24),
                            ElevatedButton.icon(
                              key: const Key('empty_add_account_btn'),
                              onPressed: () => context.push('/accounts/add'),
                              icon: const Icon(Icons.add_rounded),
                              label: const Text('Add Bank Account'),
                            ),
                          ],
                        ),
                      ),
                    );
                  }

                  return ListView.builder(
                    padding: const EdgeInsets.all(16.0),
                    itemCount: accounts.length,
                    itemBuilder: (context, index) {
                      final account = accounts[index];
                      return Card(
                        margin: const EdgeInsets.only(bottom: 16),
                        child: Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  CircleAvatar(
                                    backgroundColor: account.isDefault
                                        ? AppColors.primaryGreen.withOpacity(0.12)
                                        : AppColors.accentGold.withOpacity(0.15),
                                    child: Icon(
                                      Icons.account_balance,
                                      color: account.isDefault ? AppColors.primaryGreen : AppColors.accentGoldDark,
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          account.label?.isNotEmpty == true
                                              ? '${account.label} (${account.institutionCode})'
                                              : account.institutionCode,
                                          style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                                        ),
                                        Text(
                                          '${account.accountName} • ${account.maskedNumber}',
                                          style: const TextStyle(fontSize: 13, color: Colors.grey),
                                        ),
                                      ],
                                    ),
                                  ),
                                  if (account.isDefault)
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                      decoration: BoxDecoration(
                                        color: AppColors.primaryGreen,
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: const Text(
                                        'DEFAULT',
                                        style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w700),
                                      ),
                                    ),
                                ],
                              ),
                              const SizedBox(height: 16),
                              const Divider(height: 1),
                              const SizedBox(height: 8),

                              // Toggles: Discoverability & Receiving
                              SwitchListTile(
                                contentPadding: EdgeInsets.zero,
                                dense: true,
                                title: const Text('Discoverable in Registry', style: TextStyle(fontSize: 13)),
                                subtitle: const Text('Allow payers to discover this account via phone lookup', style: TextStyle(fontSize: 11)),
                                value: account.discoverable,
                                onChanged: (val) {
                                  ref.read(bankAccountsProvider.notifier).patchAccount(
                                    id: account.id,
                                    discoverable: val,
                                  );
                                },
                              ),
                              SwitchListTile(
                                contentPadding: EdgeInsets.zero,
                                dense: true,
                                title: const Text('Enable Receiving', style: TextStyle(fontSize: 13)),
                                subtitle: const Text('Accept incoming transfers to this account', style: TextStyle(fontSize: 11)),
                                value: account.receivingEnabled,
                                onChanged: (val) {
                                  ref.read(bankAccountsProvider.notifier).patchAccount(
                                    id: account.id,
                                    receivingEnabled: val,
                                  );
                                },
                              ),
                              const SizedBox(height: 8),
                              const Divider(height: 1),

                              // Action Bar: Set Default, Rename, Remove
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  if (!account.isDefault)
                                    TextButton(
                                      key: Key('set_default_btn_${account.id}'),
                                      onPressed: () {
                                        ref.read(bankAccountsProvider.notifier).setDefault(account.id);
                                      },
                                      child: const Text('Set as Default'),
                                    )
                                  else
                                    const SizedBox.shrink(),
                                  Row(
                                    children: [
                                      IconButton(
                                        key: Key('rename_account_btn_${account.id}'),
                                        icon: const Icon(Icons.edit_outlined, size: 20),
                                        onPressed: () => _showRenameDialog(context, ref, account),
                                        tooltip: 'Rename',
                                      ),
                                      IconButton(
                                        key: Key('remove_account_btn_${account.id}'),
                                        icon: const Icon(Icons.delete_outline, size: 20, color: AppColors.error),
                                        onPressed: () => _showRemoveConfirmation(context, ref, account),
                                        tooltip: 'Remove',
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
