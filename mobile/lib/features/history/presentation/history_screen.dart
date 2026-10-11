import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/simulated_banner.dart';
import '../../send/domain/transfer_models.dart';
import '../data/history_service.dart';
import 'receipt_screen.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  HistoryFilter _filter = const HistoryFilter();
  List<TransferModel> _transfers = [];
  bool _isLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final items = await ref.read(historyServiceProvider).listHistory(filter: _filter);
      if (mounted) {
        setState(() {
          _transfers = items;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _error = 'Failed to load transfer history.';
        });
      }
    }
  }

  void _showFilterModal() {
    String? tempDirection = _filter.direction;
    String? tempStatus = _filter.status;
    final minController = TextEditingController(
      text: _filter.minAmount != null ? _filter.minAmount.toString() : '',
    );
    final maxController = TextEditingController(
      text: _filter.maxAmount != null ? _filter.maxAmount.toString() : '',
    );

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setSheetState) {
          final isDark = Theme.of(context).brightness == Brightness.dark;

          return Container(
            padding: EdgeInsets.only(
              top: 24,
              left: 24,
              right: 24,
              bottom: MediaQuery.of(context).viewInsets.bottom + 24,
            ),
            decoration: BoxDecoration(
              color: isDark ? AppColors.surfaceDark : Colors.white,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Filter History',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                    ),
                    TextButton(
                      key: const Key('history_reset_filter_btn'),
                      onPressed: () {
                        setSheetState(() {
                          tempDirection = null;
                          tempStatus = null;
                          minController.clear();
                          maxController.clear();
                        });
                      },
                      child: const Text('Reset All'),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                const Text('Direction', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                const SizedBox(height: 8),
                Row(
                  children: [
                    _buildFilterChip('All', tempDirection == null, () {
                      setSheetState(() => tempDirection = null);
                    }),
                    const SizedBox(width: 8),
                    _buildFilterChip('Sent', tempDirection == 'SENT', () {
                      setSheetState(() => tempDirection = 'SENT');
                    }, key: const Key('filter_chip_sent')),
                    const SizedBox(width: 8),
                    _buildFilterChip('Received', tempDirection == 'RECEIVED', () {
                      setSheetState(() => tempDirection = 'RECEIVED');
                    }, key: const Key('filter_chip_received')),
                  ],
                ),
                const SizedBox(height: 16),
                const Text('Status', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _buildFilterChip('All', tempStatus == null, () {
                      setSheetState(() => tempStatus = null);
                    }),
                    _buildFilterChip('Completed', tempStatus == 'COMPLETED', () {
                      setSheetState(() => tempStatus = 'COMPLETED');
                    }, key: const Key('filter_chip_completed')),
                    _buildFilterChip('Pending', tempStatus == 'PENDING', () {
                      setSheetState(() => tempStatus = 'PENDING');
                    }),
                    _buildFilterChip('Failed', tempStatus == 'FAILED', () {
                      setSheetState(() => tempStatus = 'FAILED');
                    }, key: const Key('filter_chip_failed')),
                  ],
                ),
                const SizedBox(height: 16),
                const Text('Amount Range (LKR)', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        key: const Key('filter_min_amount_field'),
                        controller: minController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          hintText: 'Min Amount',
                          isDense: true,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextField(
                        key: const Key('filter_max_amount_field'),
                        controller: maxController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          hintText: 'Max Amount',
                          isDense: true,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  key: const Key('history_apply_filters_btn'),
                  onPressed: () {
                    final min = double.tryParse(minController.text.trim());
                    final max = double.tryParse(maxController.text.trim());
                    setState(() {
                      _filter = HistoryFilter(
                        direction: tempDirection,
                        status: tempStatus,
                        minAmount: min,
                        maxAmount: max,
                      );
                    });
                    Navigator.of(ctx).pop();
                    _loadHistory();
                  },
                  child: const Text('Apply Filters'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildFilterChip(String label, bool isSelected, VoidCallback onTap, {Key? key}) {
    return ChoiceChip(
      key: key,
      label: Text(label),
      selected: isSelected,
      onSelected: (_) => onTap(),
      selectedColor: AppColors.primaryGreen,
      labelStyle: TextStyle(
        color: isSelected ? Colors.white : AppColors.textPrimaryLight,
        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
      ),
    );
  }

  void _openReceipt(TransferModel transfer) async {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(child: CircularProgressIndicator()),
    );

    try {
      final receipt = await ref.read(historyServiceProvider).getReceipt(transfer.transferId);
      if (mounted) {
        Navigator.of(context).pop(); // dismiss loader
        Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => ReceiptScreen(receipt: receipt)),
        );
      }
    } catch (_) {
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not fetch receipt details')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Transaction History'),
        actions: [
          IconButton(
            key: const Key('history_filter_btn'),
            icon: const Icon(Icons.filter_list_rounded),
            tooltip: 'Filter',
            onPressed: _showFilterModal,
          ),
          IconButton(
            key: const Key('history_refresh_btn'),
            icon: const Icon(Icons.refresh_rounded),
            tooltip: 'Refresh',
            onPressed: _loadHistory,
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SimulatedBanner(),
            if (_hasActiveFilters()) _buildActiveFilterBar(isDark),
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                      ? Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(_error!),
                              const SizedBox(height: 12),
                              ElevatedButton(
                                onPressed: _loadHistory,
                                child: const Text('Retry'),
                              ),
                            ],
                          ),
                        )
                      : _transfers.isEmpty
                          ? Center(
                              child: Text(
                                'No transactions found.',
                                style: TextStyle(
                                  color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
                                ),
                              ),
                            )
                          : ListView.builder(
                              padding: const EdgeInsets.all(16.0),
                              itemCount: _transfers.length,
                              itemBuilder: (context, index) {
                                final item = _transfers[index];
                                return _buildTransferCard(item, isDark);
                              },
                            ),
            ),
          ],
        ),
      ),
    );
  }

  bool _hasActiveFilters() {
    return _filter.direction != null ||
        _filter.status != null ||
        _filter.minAmount != null ||
        _filter.maxAmount != null;
  }

  Widget _buildActiveFilterBar(bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: AppColors.accentGold.withOpacity(0.1),
      child: Row(
        children: [
          const Icon(Icons.filter_alt_outlined, size: 16, color: AppColors.accentGoldDark),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Filtered by: ${_filter.direction ?? ""} ${_filter.status ?? ""}'.trim(),
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.accentGoldDark),
            ),
          ),
          TextButton(
            onPressed: () {
              setState(() => _filter = const HistoryFilter());
              _loadHistory();
            },
            child: const Text('Clear', style: TextStyle(fontSize: 12)),
          ),
        ],
      ),
    );
  }

  Widget _buildTransferCard(TransferModel item, bool isDark) {
    final isSuccess = item.status.isConfirmedSuccess;
    final isPending = item.status.isPendingOrReconciling;

    Color badgeColor;
    if (isSuccess) {
      badgeColor = AppColors.success;
    } else if (isPending) {
      badgeColor = AppColors.warning;
    } else {
      badgeColor = AppColors.error;
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        onTap: () => _openReceipt(item),
        leading: CircleAvatar(
          backgroundColor: badgeColor.withOpacity(0.12),
          child: Icon(
            isSuccess
                ? Icons.check_circle_outline_rounded
                : isPending
                    ? Icons.hourglass_top_rounded
                    : Icons.cancel_outlined,
            color: badgeColor,
          ),
        ),
        title: Text(
          item.receiverDisplayName ?? item.reference,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        ),
        subtitle: Text(
          '${item.status.name.toUpperCase()} • ${item.createdAt?.substring(0, 10) ?? "SIMULATED"}',
          style: TextStyle(
            fontSize: 12,
            color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
          ),
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              'LKR ${item.amount.toStringAsFixed(2)}',
              style: const TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 2),
            const Text(
              'View Receipt',
              style: TextStyle(fontSize: 11, color: AppColors.primaryGreen, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}
