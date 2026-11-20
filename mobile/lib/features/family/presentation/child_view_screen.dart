import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/test_keys.dart';
import '../data/family_service.dart';
import '../domain/family_member_model.dart';

class ChildViewScreen extends ConsumerStatefulWidget {
  final String memberId;

  const ChildViewScreen({
    super.key,
    required this.memberId,
  });

  @override
  ConsumerState<ChildViewScreen> createState() => _ChildViewScreenState();
}

class _ChildViewScreenState extends ConsumerState<ChildViewScreen> {
  @override
  Widget build(BuildContext context) {
    final childView = ref.watch(familyChildViewProvider(widget.memberId));
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    if (childView == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Child View')),
        body: const Center(child: Text('Profile not found')),
      );
    }

    final balanceText =
        'Rs. ${(childView.balanceMinor / 100).toStringAsFixed(2)}';
    final remainingText =
        'Rs. ${(childView.remainingDailyMinor / 100).toStringAsFixed(2)}';
    final dailyLimitText =
        'Rs. ${(childView.dailyLimitMinor / 100).toStringAsFixed(2)}';

    return Scaffold(
      backgroundColor:
          isDark ? const Color(0xFF0F172A) : const Color(0xFFF0FDF4),
      appBar: AppBar(
        title: Text(
          '${childView.firstName}\'s Space',
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 16),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: const Color(0xFF10B981).withOpacity(0.15),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: const Color(0xFF10B981).withOpacity(0.4),
              ),
            ),
            child: const Text(
              'CHILD SAFE',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w800,
                color: Color(0xFF10B981),
              ),
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          children: [
            // Avatar & Name Card
            Center(
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 36,
                    backgroundColor: childView.isFrozen
                        ? Colors.red.shade100
                        : const Color(0xFF10B981),
                    child: Text(
                      childView.avatarInitials,
                      style: TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.w800,
                        color: childView.isFrozen
                            ? Colors.red.shade800
                            : Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Hello, ${childView.firstName}!',
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: isDark ? Colors.white : const Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: childView.isFrozen
                          ? Colors.red.withOpacity(0.15)
                          : Colors.green.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      childView.isFrozen
                          ? 'Account Frozen by Guardian'
                          : 'Ready to Spend',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: childView.isFrozen ? Colors.red : Colors.green,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Balance Highlight Tile
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF0EA5E9), Color(0xFF2563EB)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF0EA5E9).withOpacity(0.3),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Available Balance',
                    style: TextStyle(
                      color: Colors.white70,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    balanceText,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 32,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Left for Today',
                            style: TextStyle(color: Colors.white70, fontSize: 11),
                          ),
                          Text(
                            remainingText,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          const Text(
                            'Daily Allowance',
                            style: TextStyle(color: Colors.white70, fontSize: 11),
                          ),
                          Text(
                            dailyLimitText,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Allowed Categories Card
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF1E293B) : Colors.white,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.check_circle_outline,
                          color: Color(0xFF10B981), size: 18),
                      const SizedBox(width: 8),
                      Text(
                        'Where You Can Spend',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: isDark ? Colors.white : const Color(0xFF0F172A),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: childView.allowedCategories.map((cat) {
                      return Chip(
                        label: Text(cat),
                        backgroundColor: const Color(0xFF10B981).withOpacity(0.12),
                        labelStyle: const TextStyle(
                          color: Color(0xFF059669),
                          fontWeight: FontWeight.w700,
                          fontSize: 12,
                        ),
                        side: BorderSide.none,
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Simulate Purchase Button (for interactive demo / testing)
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                key: AppTestKeys.familySpendSimulateBtn,
                onPressed: childView.isFrozen
                    ? null
                    : () => _simulatePurchase(context, childView),
                icon: const Icon(Icons.shopping_bag_outlined),
                label: const Text('Simulate Purchase (Test Limits)'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF6366F1),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Recent Activity Section
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'Recent Activity',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: isDark ? Colors.white : const Color(0xFF0F172A),
                ),
              ),
            ),
            const SizedBox(height: 12),
            if (childView.recentTransactions.isEmpty)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF1E293B) : Colors.white,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Center(
                  child: Text(
                    'No purchases yet',
                    style: TextStyle(
                      color: isDark ? Colors.grey[400] : Colors.grey[500],
                    ),
                  ),
                ),
              )
            else
              ...childView.recentTransactions.map((tx) {
                final isApproved = tx.status == 'APPROVED';
                return Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E293B) : Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: isDark
                          ? const Color(0xFF334155)
                          : const Color(0xFFE2E8F0),
                    ),
                  ),
                  child: Row(
                    children: [
                      CircleAvatar(
                        radius: 18,
                        backgroundColor: isApproved
                            ? (tx.type == 'FUNDING'
                                ? Colors.green.shade100
                                : Colors.blue.shade100)
                            : Colors.red.shade100,
                        child: Icon(
                          isApproved
                              ? (tx.type == 'FUNDING'
                                  ? Icons.arrow_downward
                                  : Icons.shopping_bag)
                              : Icons.block,
                          size: 18,
                          color: isApproved
                              ? (tx.type == 'FUNDING'
                                  ? Colors.green.shade800
                                  : Colors.blue.shade800)
                              : Colors.red.shade800,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              tx.merchantOrSource,
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: isDark ? Colors.white : const Color(0xFF0F172A),
                              ),
                            ),
                            Text(
                              isApproved
                                  ? tx.category
                                  : (tx.declineReason ?? 'Declined by Rules'),
                              style: TextStyle(
                                fontSize: 11,
                                color: isApproved
                                    ? (isDark ? Colors.grey[400] : Colors.grey[600])
                                    : Colors.red,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Text(
                        '${tx.type == 'FUNDING' ? '+' : '-'}Rs. ${(tx.amountMinor / 100).toStringAsFixed(2)}',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: isApproved
                              ? (tx.type == 'FUNDING'
                                  ? Colors.green
                                  : (isDark ? Colors.white : const Color(0xFF0F172A)))
                              : Colors.red,
                        ),
                      ),
                    ],
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  void _simulatePurchase(BuildContext context, ChildViewModel childView) {
    final amountCtrl = TextEditingController(text: '150');
    String selectedCategory = childView.allowedCategories.isNotEmpty
        ? childView.allowedCategories.first
        : 'FOOD';

    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Padding(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 20,
                bottom: MediaQuery.of(context).viewInsets.bottom + 20,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Simulate Child Purchase',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: amountCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Amount (LKR)',
                      border: OutlineInputBorder(),
                      prefixText: 'Rs. ',
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: selectedCategory,
                    decoration: const InputDecoration(
                      labelText: 'Category',
                      border: OutlineInputBorder(),
                    ),
                    items: childView.allowedCategories.map((c) {
                      return DropdownMenuItem(value: c, child: Text(c));
                    }).toList(),
                    onChanged: (val) {
                      if (val != null) {
                        setModalState(() => selectedCategory = val);
                      }
                    },
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: () {
                        final amount = int.tryParse(amountCtrl.text) ?? 0;
                        if (amount > 0) {
                          final success = ref
                              .read(familyNotifierProvider.notifier)
                              .spend(
                                widget.memberId,
                                amount * 100,
                                selectedCategory,
                                'Simulated Store',
                                note: 'Simulated child checkout',
                              );
                          Navigator.pop(ctx);
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                success
                                    ? 'Purchase approved within limits!'
                                    : 'Purchase blocked by guardian controls!',
                              ),
                              backgroundColor:
                                  success ? Colors.green : Colors.red,
                            ),
                          );
                        }
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF6366F1),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      child: const Text('Execute Purchase'),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}
