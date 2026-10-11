import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../theme/app_tokens.dart';
import 'widgets.dart';

/// Dev-only showcase screen for visually inspecting every component of the design system
/// across light and dark themes. Accessible in debug builds.
class DevDesignSystemScreen extends ConsumerStatefulWidget {
  const DevDesignSystemScreen({super.key = const Key('dev_design_system_screen')});

  @override
  ConsumerState<DevDesignSystemScreen> createState() => _DevDesignSystemScreenState();
}

class _DevDesignSystemScreenState extends ConsumerState<DevDesignSystemScreen> {
  int _selectedNavIndex = 0;
  bool _showShimmer = false;

  @override
  Widget build(BuildContext context) {
    if (!kDebugMode) {
      return const Scaffold(
        body: Center(child: Text('Design system gallery only available in debug mode.')),
      );
    }

    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GradientHeaderScaffold(
      header: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const AppAvatar(name: 'James Taylor', size: 42),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Design System Gallery',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                      Text(
                        'Debug Mode Preview',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 12,
                          color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const ThemeTogglePill(),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Total balance',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                  Text(
                    'LKR 125,450.00',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.5,
                      color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                    ),
                  ),
                ],
              ),
              ElevatedButton.icon(
                onPressed: () => setState(() => _showShimmer = !_showShimmer),
                icon: Icon(_showShimmer ? Icons.check_circle_outline : Icons.auto_awesome, size: 16),
                label: Text(_showShimmer ? 'Live UI' : 'Shimmer'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
                ),
              ),
            ],
          ),
        ],
      ),
      bottomNavigationBar: AppBottomNav(
        currentIndex: _selectedNavIndex,
        onIndexChanged: (idx) => setState(() => _selectedNavIndex = idx),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Section: Theme Toggle & Badges
          const SectionHeader(title: 'THEME & BADGES'),
          const SizedBox(height: 8),
          const Wrap(
            spacing: 12,
            runSpacing: 10,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              ThemeTogglePill(),
              SimulatedBadge(),
              SimulatedBadge(compact: true, text: 'SANDBOX'),
            ],
          ),
          const SizedBox(height: 24),

          // Section: Deterministic Avatars
          const SectionHeader(title: 'DETERMINISTIC PASTEL AVATARS'),
          const SizedBox(height: 8),
          const Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              AppAvatar(name: 'Anna Lopez'),
              AppAvatar(name: 'Penny Klark'),
              AppAvatar(name: 'Mark Rivero'),
              AppAvatar(name: 'Chathuranga'),
              AppAvatar(name: 'Kamal Perera'),
            ],
          ),
          const SizedBox(height: 24),

          // Section: Account Cards
          const SectionHeader(title: 'ACCOUNT CARDS (CHARCOAL & LAVENDER)'),
          const SizedBox(height: 10),
          if (_showShimmer) ...[
            const SkeletonAccountCard(),
          ] else ...[
            const AccountCard(
              bankName: 'Bank of Ceylon',
              nickname: 'Salary Account',
              maskedNumber: '** 6917',
              balance: 'LKR 84,230.00',
              isDefault: true,
              style: AccountCardStyle.charcoal,
              expiryText: '01/29',
            ),
            const SizedBox(height: 14),
            const AccountCard(
              bankName: 'Commercial Bank',
              nickname: 'Primary Savings',
              maskedNumber: '** 4433',
              balance: 'LKR 41,220.00',
              isDefault: false,
              style: AccountCardStyle.lavender,
              expiryText: '11/28',
            ),
          ],
          const SizedBox(height: 24),

          // Section: Quick Action Tiles (Finance 4-Tiles)
          const SectionHeader(title: 'QUICK ACTION TILES (FINANCE GRID)'),
          const SizedBox(height: 10),
          if (_showShimmer) ...[
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                SkeletonQuickActionTile(),
                SkeletonQuickActionTile(),
                SkeletonQuickActionTile(),
                SkeletonQuickActionTile(),
              ],
            ),
          ] else ...[
            const SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  QuickActionTile(
                    icon: Icons.star_rounded,
                    label: 'My bonuses',
                    gradient: QuickActionGradient.violet,
                  ),
                  SizedBox(width: 10),
                  QuickActionTile(
                    icon: Icons.bar_chart_rounded,
                    label: 'Finance\nanalysis',
                    gradient: QuickActionGradient.orange,
                  ),
                  SizedBox(width: 10),
                  QuickActionTile(
                    icon: Icons.account_balance_wallet_rounded,
                    label: 'Payment',
                    gradient: QuickActionGradient.pink,
                  ),
                  SizedBox(width: 10),
                  QuickActionTile(
                    icon: Icons.trending_up_rounded,
                    label: 'Invest',
                    gradient: QuickActionGradient.cyan,
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 24),

          // Section: Action Buttons
          const SectionHeader(title: 'ACTION BUTTONS (CARDS SCREEN DOCK)'),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              ActionButton(
                icon: Icons.add_rounded,
                label: 'Add money',
                onTap: () {},
              ),
              ActionButton(
                icon: Icons.ac_unit_rounded,
                label: 'Freeze',
                onTap: () {},
              ),
              ActionButton(
                icon: Icons.settings_outlined,
                label: 'Settings',
                onTap: () {},
              ),
            ],
          ),
          const SizedBox(height: 24),

          // Section: Menu Tiles 2x2
          const SectionHeader(title: 'MENU TILES (TRANSFERS 2X2 GRID)'),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: MenuTile(
                  icon: Icons.near_me_rounded,
                  label: 'Popular\npayments',
                  onTap: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: MenuTile(
                  icon: Icons.account_balance_rounded,
                  label: 'Bank of\nrecipient',
                  onTap: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: MenuTile(
                  icon: Icons.credit_card_rounded,
                  label: 'Between\nmy cards',
                  onTap: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: MenuTile(
                  icon: Icons.contact_page_rounded,
                  label: "Recipient's\ncard",
                  onTap: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // Section: Transactions Feed
          SectionHeader(
            title: 'LAST TRANSACTIONS',
            actionLabel: 'See all >',
            onActionTap: () {},
          ),
          const SizedBox(height: 6),
          if (_showShimmer) ...[
            const SkeletonTransactionTile(),
            const SkeletonTransactionTile(),
          ] else ...[
            TransactionTile(
              icon: Icons.shopping_cart_rounded,
              title: 'Supermarket',
              subtitle: 'Today, 16:40',
              amount: '- LKR 4,200.00',
              status: 'payment',
              isPositive: false,
              onTap: () {},
            ),
            const Divider(height: 1),
            TransactionTile(
              icon: Icons.account_balance_rounded,
              title: 'Kapital Bank',
              subtitle: 'Yesterday, 11:20',
              amount: '+ LKR 12,000.00',
              status: 'transfer',
              isPositive: true,
              onTap: () {},
            ),
          ],
          const SizedBox(height: 24),

          // Section: Empty & Error states
          const SectionHeader(title: 'EMPTY STATE & ERROR RETRY'),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              border: Border.all(color: AppTokens.borderLight),
              borderRadius: AppTokens.borderRadiusCard,
            ),
            child: EmptyState(
              icon: Icons.receipt_long_rounded,
              title: 'No Transactions Yet',
              description: 'When you send or receive simulated transfers, your receipts will appear here.',
              actionLabel: 'Send Money',
              onActionTap: () {},
            ),
          ),
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              border: Border.all(color: AppTokens.borderLight),
              borderRadius: AppTokens.borderRadiusCard,
            ),
            child: ErrorRetry(
              message: 'Could not connect to the simulated bank adapter. Check network.',
              onRetry: () {},
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}
