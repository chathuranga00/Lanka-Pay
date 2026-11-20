class InsightModel {
  final String id;
  final String type; // SPENDING_CHANGE, CATEGORY_SURGE, BUDGET_LIMIT, UPCOMING_PAYMENT, ACCOUNT_ATTENTION, EXPIRING_REWARDS
  final String title;
  final String text;
  final String actionDeepLink;

  const InsightModel({
    required this.id,
    required this.type,
    required this.title,
    required this.text,
    required this.actionDeepLink,
  });

  factory InsightModel.fromJson(Map<String, dynamic> json) {
    return InsightModel(
      id: json['id'] as String? ?? '',
      type: json['type'] as String? ?? 'GENERAL',
      title: json['title'] as String? ?? '',
      text: json['text'] as String? ?? '',
      actionDeepLink: json['actionDeepLink'] as String? ?? '',
    );
  }
}
