import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../../l10n/app_localizations.dart';
import '../../accounts/data/bank_account_service.dart';
import '../../accounts/domain/bank_account_model.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accountsAsync = ref.watch(bankAccountsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n?.appTitle ?? 'Lanka Pay'),
        actions: [
          IconButton(
            key: const Key('home_notifications_icon_btn'),
            icon: const Icon(Icons.notifications_none_rounded),
            onPressed: () => context.push('/notifications'),
          ),
          IconButton(
            key: const Key('home_settings_icon_btn'),
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => context.push('/settings'),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Clean Primary Card
                    accountsAsync.when(
                      loading: () => Card(
                        child: Container(
                          height: 140,
                          alignment: Alignment.center,
                          child: const CircularProgressIndicator(),
                        ),
                      ),
                      error: (_, _) => _buildDefaultAccountCard(context, isDark, null),
                      data: (accounts) {
                        final BankAccountModel? defaultAccount = accounts.isEmpty
                            ? null
                            : accounts.firstWhere(
                                (a) => a.isDefault,
                                orElse: () => accounts.first,
                              );
                        return _buildDefaultAccountCard(context, isDark, defaultAccount);
                      },
                    ),
                    const SizedBox(height: 24),

                    // Quick Action Buttons (Send, Scan, Request)
                    Row(
                      children: [
                        _buildQuickAction(
                          context: context,
                          key: const Key('home_quick_send_btn'),
                          icon: Icons.send_rounded,
                          label: 'Send',
                          onTap: () => context.push('/send'),
                        ),
                        const SizedBox(width: 14),
                        _buildQuickAction(
                          context: context,
                          key: const Key('home_quick_scan_btn'),
                          icon: Icons.qr_code_scanner_rounded,
                          label: 'Scan QR',
                          onTap: () => context.push('/qr'),
                        ),
                        const SizedBox(width: 14),
                        _buildQuickAction(
                          context: context,
                          key: const Key('home_quick_request_btn'),
                          icon: Icons.call_received_rounded,
                          label: 'Request',
                          onTap: () => context.push('/qr'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 28),

                    // Recent Activity Section
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Recent Activity',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: isDark ? Colors.white : AppColors.textPrimaryLight,
                          ),
                        ),
                        TextButton(
                          key: const Key('home_view_all_history_btn'),
                          onPressed: () => context.push('/history'),
                          child: const Text('View All'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),

                    Card(
                      child: Column(
                        children: [
                          ListTile(
                            leading: CircleAvatar(
                              backgroundColor: AppColors.error.withOpacity(0.12),
                              child: const Icon(Icons.arrow_upward_rounded, color: AppColors.error),
                            ),
                            title: const Text('Transfer to Kamal P.', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                            subtitle: const Text('Instant Pay-ID • SIMULATED', style: TextStyle(fontSize: 12)),
                            trailing: const Text(
                              '- LKR 1,500.00',
                              style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.error, fontSize: 14),
                            ),
                          ),
                          const Divider(height: 1),
                          ListTile(
                            leading: CircleAvatar(
                              backgroundColor: AppColors.success.withOpacity(0.12),
                              child: const Icon(Icons.arrow_downward_rounded, color: AppColors.success),
                            ),
                            title: const Text('Received from Nimal S.', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                            subtitle: const Text('LANKAQR Merchant • SIMULATED', style: TextStyle(fontSize: 12)),
                            trailing: const Text(
                              '+ LKR 4,200.00',
                              style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.success, fontSize: 14),
                            ),
                          ),
                        ],
                      ),
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

  Widget _buildDefaultAccountCard(BuildContext context, bool isDark, BankAccountModel? account) {
    return InkWell(
      onTap: () => context.push('/accounts'),
      borderRadius: BorderRadius.circular(16),
      child: Card(
        child: Container(
          padding: const EdgeInsets.all(20.0),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: isDark
                  ? [AppColors.primaryGreenLight, AppColors.primaryGreenDark]
                  : [AppColors.primaryGreen, AppColors.primaryGreenDark],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    account != null ? 'Primary Account' : 'No Account Linked',
                    style: const TextStyle(color: Colors.white70, fontSize: 13, fontWeight: FontWeight.w500),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.accentGold.withOpacity(0.3),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: const Text(
                      'SIMULATED',
                      style: TextStyle(color: AppColors.accentGoldLight, fontSize: 10, fontWeight: FontWeight.w800),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                account != null ? 'LKR 125,450.00' : 'LKR 0.00',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 28,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.5,
                ),
              ),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    account != null
                        ? '${account.institutionCode} • ${account.maskedNumber}'
                        : 'Tap to link bank account',
                    style: const TextStyle(color: Colors.white70, fontSize: 13),
                  ),
                  if (account == null)
                    const Icon(Icons.add_circle_outline_rounded, color: Colors.white70, size: 18),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildQuickAction({
    required BuildContext context,
    required Key key,
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Expanded(
      child: InkWell(
        key: key,
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 18),
          decoration: BoxDecoration(
            color: isDark ? AppColors.surfaceDark : Colors.white,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: isDark ? Colors.white12 : Colors.black.withOpacity(0.06)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.03),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.primaryGreen.withOpacity(0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  icon,
                  color: isDark ? AppColors.accentGoldLight : AppColors.primaryGreen,
                  size: 24,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                label,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: isDark ? Colors.white : AppColors.textPrimaryLight,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
