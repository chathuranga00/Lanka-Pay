import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../history/data/history_service.dart';
import '../../notifications/data/notification_service.dart';
import '../../send/domain/transfer_models.dart';
import 'home_api_model.dart';
import 'home_api_service.dart';

/// Aggregator provider fetching complete dashboard from GET /api/v1/home.
final homeAggregateProvider = FutureProvider<HomeAggregateResponseModel>((ref) async {
  final service = ref.watch(homeApiServiceProvider);
  return await service.getHome();
});

/// Client-side set of dismissed nudge IDs for the active session.
class DismissedNudgesNotifier extends Notifier<Set<String>> {
  @override
  Set<String> build() => <String>{};

  void dismiss(String id) {
    state = {...state, id};
  }
}

final dismissedNudgeIdsProvider =
    NotifierProvider<DismissedNudgesNotifier, Set<String>>(() {
  return DismissedNudgesNotifier();
});

/// Provider for unread notifications count, derived from GET /notifications.
final unreadNotificationCountProvider = FutureProvider<int>((ref) async {
  try {
    final notifs = await ref.watch(notificationServiceProvider).getNotifications();
    return notifs.where((n) => !n.read).length;
  } catch (_) {
    return 0;
  }
});

/// Provider for the 5 most recent transfers.
final recentTransactionsProvider = FutureProvider<List<TransferModel>>((ref) async {
  final historyService = ref.watch(historyServiceProvider);
  return await historyService.listHistory(page: 0, size: 5);
});

/// Provider for simulated balance (if an endpoint returns it). Defaults to null.
final simulatedBalanceProvider = Provider<double?>((ref) => null);

/// Provider calculating "Received this month" (sum of confirmed SUCCESS incoming transfers this month).
final monthlyReceivedProvider = FutureProvider<double>((ref) async {
  try {
    final history = await ref.watch(historyServiceProvider).listHistory(size: 50);
    final now = DateTime.now();
    double total = 0.0;
    for (final tx in history) {
      if (tx.status.isConfirmedSuccess) {
        final note = tx.note?.toLowerCase() ?? '';
        final receiver = tx.receiverDisplayName?.toLowerCase() ?? '';
        final isIncoming = receiver.contains('received') ||
            note.contains('received') ||
            receiver.contains('from') ||
            tx.receiverId != null;

        if (isIncoming || tx.receiverDisplayName != null) {
          if (tx.createdAt != null) {
            final dt = DateTime.tryParse(tx.createdAt!);
            if (dt != null) {
              if (dt.year == now.year && dt.month == now.month) {
                total += tx.amount;
              }
            } else {
              total += tx.amount;
            }
          } else {
            total += tx.amount;
          }
        }
      }
    }
    return total;
  } catch (_) {
    return 0.0;
  }
});
