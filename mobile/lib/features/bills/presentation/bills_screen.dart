import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/bill_service.dart';
import '../domain/bill_models.dart';

class BillsScreen extends ConsumerStatefulWidget {
  const BillsScreen({super.key});

  @override
  ConsumerState<BillsScreen> createState() => _BillsScreenState();
}

class _BillsScreenState extends ConsumerState<BillsScreen> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _showEditNicknameDialog(SavedBillerModel saved) {
    final controller = TextEditingController(text: saved.nickname);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Biller Nickname'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Nickname',
            hintText: 'e.g. Home Electricity',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              final newName = controller.text.trim();
              if (newName.isNotEmpty) {
                Navigator.pop(ctx);
                await ref
                    .read(savedBillersProvider.notifier)
                    .updateNickname(saved.id, newName);
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  void _showDeleteDialog(SavedBillerModel saved) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Remove Saved Biller?'),
        content: Text(
          'Are you sure you want to remove "${saved.nickname}" (${saved.maskedReference})?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              await ref
                  .read(savedBillersProvider.notifier)
                  .deleteSavedBiller(saved.id);
            },
            style: TextButton.styleFrom(foregroundColor: AppTokens.error),
            child: const Text('Remove'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final selectedCategory = ref.watch(selectedBillerCategoryProvider);
    final billersAsync = ref.watch(billersProvider);
    final savedBillersAsync = ref.watch(savedBillersProvider);

    return Scaffold(
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: const Text(
          'Pay Bills',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        elevation: 0,
        backgroundColor: Colors.transparent,
        actions: [
          IconButton(
            key: AppTestKeys.billHistoryFilter,
            icon: const Icon(Icons.history_rounded),
            tooltip: 'Payment History',
            onPressed: () => context.push('/bills/history'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(billersProvider);
          await ref.read(savedBillersProvider.notifier).refresh();
        },
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            // Search Input
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: Container(
                  decoration: BoxDecoration(
                    color: isDark ? AppTokens.searchFieldBgDark : AppTokens.searchFieldBgLight,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: TextField(
                    key: AppTestKeys.billsSearch,
                    controller: _searchController,
                    onChanged: (val) {
                      ref.read(billerSearchQueryProvider.notifier).setQuery(val);
                    },
                    decoration: InputDecoration(
                      hintText: 'Search billers, utilities, telco...',
                      hintStyle: TextStyle(
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                        fontSize: 14,
                      ),
                      prefixIcon: Icon(
                        Icons.search_rounded,
                        color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                      ),
                      suffixIcon: _searchController.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear_rounded, size: 18),
                              onPressed: () {
                                _searchController.clear();
                                ref.read(billerSearchQueryProvider.notifier).setQuery('');
                              },
                            )
                          : null,
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    ),
                  ),
                ),
              ),
            ),

            // Saved Billers Section
            savedBillersAsync.when(
              data: (savedList) {
                if (savedList.isEmpty) return const SliverToBoxAdapter(child: SizedBox.shrink());
                return SliverToBoxAdapter(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Row(
                          children: [
                            Text(
                              'Saved Billers',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                              ),
                            ),
                            const Spacer(),
                            Text(
                              '${savedList.length} saved',
                              style: TextStyle(
                                fontSize: 12,
                                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        key: AppTestKeys.savedBillersList,
                        height: 130,
                        child: ListView.separated(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          scrollDirection: Axis.horizontal,
                          itemCount: savedList.length,
                          separatorBuilder: (ctx, i) => const SizedBox(width: 12),
                          itemBuilder: (ctx, index) {
                            final saved = savedList[index];
                            return _buildSavedBillerCard(context, saved, isDark);
                          },
                        ),
                      ),
                      const SizedBox(height: 24),
                    ],
                  ),
                );
              },
              loading: () => const SliverToBoxAdapter(child: SizedBox.shrink()),
              error: (err, stack) => const SliverToBoxAdapter(child: SizedBox.shrink()),
            ),

            // Category Filter Grid / Chips
            SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(
                      'Categories',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: SizedBox(
                      key: AppTestKeys.billsCategoryGrid,
                      child: Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          ChoiceChip(
                            label: const Text('All'),
                            selected: selectedCategory == null,
                            onSelected: (_) {
                              ref.read(selectedBillerCategoryProvider.notifier).select(null);
                            },
                          ),
                          ...BillerCategory.values.map((cat) {
                            final isSelected = selectedCategory == cat;
                            return ChoiceChip(
                              avatar: Icon(cat.icon, size: 16, color: isSelected ? Colors.white : cat.color),
                              label: Text(cat.displayName),
                              selected: isSelected,
                              selectedColor: cat.color,
                              onSelected: (_) {
                                ref.read(selectedBillerCategoryProvider.notifier).select(isSelected ? null : cat);
                              },
                            );
                          }),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                ],
              ),
            ),

            // Billers Header
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Text(
                  selectedCategory != null
                      ? '${selectedCategory.displayName} Providers'
                      : 'All Billers',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: isDark ? Colors.white : AppTokens.textPrimaryLight,
                  ),
                ),
              ),
            ),

            // Billers List
            billersAsync.when(
              data: (billers) {
                if (billers.isEmpty) {
                  return SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.search_off_rounded,
                            size: 48,
                            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'No billers found',
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                              color: isDark ? Colors.white70 : Colors.black54,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Try clearing filters or search term',
                            style: TextStyle(
                              fontSize: 13,
                              color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                }

                return SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final biller = billers[index];
                        return _buildBillerTile(context, biller, isDark);
                      },
                      childCount: billers.length,
                    ),
                  ),
                );
              },
              loading: () => const SliverFillRemaining(
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (err, stack) => SliverFillRemaining(
                child: Center(
                  child: Text(
                    'Failed to load billers: $err',
                    style: TextStyle(color: isDark ? Colors.white70 : Colors.black87),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSavedBillerCard(BuildContext context, SavedBillerModel saved, bool isDark) {
    return Container(
      key: AppTestKeys.savedBiller(saved.id),
      width: 170,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark ? AppTokens.cardDark : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(isDark ? 0.2 : 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () {
          HapticFeedback.lightImpact();
          // Navigate to details screen with pre-filled saved biller
          context.push('/bills/details', extra: {'savedBiller': saved});
        },
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: saved.category.color.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(saved.category.icon, size: 18, color: saved.category.color),
                ),
                const Spacer(),
                PopupMenuButton<String>(
                  padding: EdgeInsets.zero,
                  iconSize: 18,
                  icon: Icon(
                    Icons.more_vert_rounded,
                    color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                  ),
                  onSelected: (val) {
                    if (val == 'edit') {
                      _showEditNicknameDialog(saved);
                    } else if (val == 'delete') {
                      _showDeleteDialog(saved);
                    }
                  },
                  itemBuilder: (ctx) => [
                    const PopupMenuItem(
                      value: 'edit',
                      child: Row(
                        children: [
                          Icon(Icons.edit_outlined, size: 16),
                          SizedBox(width: 8),
                          Text('Edit Nickname'),
                        ],
                      ),
                    ),
                    PopupMenuItem(
                      value: 'delete',
                      child: Row(
                        children: [
                          const Icon(Icons.delete_outline, size: 16, color: AppTokens.error),
                          const SizedBox(width: 8),
                          Text('Remove', style: TextStyle(color: AppTokens.error)),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const Spacer(),
            Text(
              saved.nickname,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 14,
                color: isDark ? Colors.white : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              saved.maskedReference,
              style: TextStyle(
                fontSize: 11,
                fontFamily: 'RobotoMono',
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              saved.billerName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 10,
                color: isDark ? Colors.white54 : Colors.black45,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBillerTile(BuildContext context, BillerModel biller, bool isDark) {
    return Container(
      key: AppTestKeys.biller(biller.id),
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
        ),
      ),
      child: Material(
        color: isDark ? AppTokens.cardDark : Colors.white,
        borderRadius: BorderRadius.circular(16),
        clipBehavior: Clip.antiAlias,
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          leading: Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: biller.category.color.withOpacity(0.12),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(biller.category.icon, color: biller.category.color, size: 24),
          ),
          title: Text(
            biller.name,
            style: TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 15,
              color: isDark ? Colors.white : AppTokens.textPrimaryLight,
            ),
          ),
          subtitle: Row(
            children: [
              Text(
                biller.category.displayName,
                style: TextStyle(
                  fontSize: 12,
                  color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                ),
              ),
              if (biller.serviceFee > 0) ...[
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF2C2C2E) : const Color(0xFFF2F2F7),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    'Fee: LKR ${biller.serviceFee.toStringAsFixed(2)}',
                    style: TextStyle(
                      fontSize: 10,
                      color: isDark ? Colors.white70 : Colors.black54,
                    ),
                  ),
                ),
              ],
            ],
          ),
          trailing: Icon(
            Icons.chevron_right_rounded,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
          onTap: () {
            HapticFeedback.lightImpact();
            context.push('/bills/details', extra: {'biller': biller});
          },
        ),
      ),
    );
  }
}
