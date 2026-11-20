import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/insight_model.dart';

class InsightsNotifier extends Notifier<List<InsightModel>> {
  @override
  List<InsightModel> build() {
    return [
      const InsightModel(
        id: 'ins_spend_change',
        type: 'SPENDING_CHANGE',
        title: 'Spending Decreased vs Last Month',
        text: 'Your spending this month is 14.2% lower than the same period last month.',
        actionDeepLink: '/analytics',
      ),
      const InsightModel(
        id: 'ins_budget_limit',
        type: 'BUDGET_LIMIT',
        title: 'Monthly Budget Nearing Limit',
        text: 'You have used 82.5% of your monthly budget limit of LKR 50,000.00.',
        actionDeepLink: '/analytics',
      ),
      const InsightModel(
        id: 'ins_upcoming_pay',
        type: 'UPCOMING_PAYMENT',
        title: 'Upcoming Scheduled Payment',
        text: 'You have a scheduled electricity bill payment of LKR 3,500.00 due in 4 days.',
        actionDeepLink: '/scheduled',
      ),
    ];
  }

  void dismiss(String type) {
    state = state.where((item) => item.type.toUpperCase() != type.toUpperCase()).toList();
  }

  void setInsights(List<InsightModel> insights) {
    state = insights;
  }
}

final insightsProvider = NotifierProvider<InsightsNotifier, List<InsightModel>>(() {
  return InsightsNotifier();
});
