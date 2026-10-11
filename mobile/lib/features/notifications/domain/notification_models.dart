class NotificationItemModel {
  final int id;
  final String title;
  final String message; // Privacy-safe text (no amounts or account digits)
  final String type;
  final bool read;
  final String? createdAt;

  const NotificationItemModel({
    required this.id,
    required this.title,
    required this.message,
    required this.type,
    required this.read,
    this.createdAt,
  });

  factory NotificationItemModel.fromJson(Map<String, dynamic> json) {
    return NotificationItemModel(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id']?.toString() ?? '0') ?? 0,
      title: json['title'] as String? ?? 'Notification',
      message: json['message'] as String? ?? '',
      type: json['type'] as String? ?? 'TRANSACTION',
      read: json['read'] as bool? ?? false,
      createdAt: json['createdAt'] as String?,
    );
  }
}

class NotificationPreferencesModel {
  final bool inAppEnabled;
  final bool pushEnabled;
  final bool emailEnabled;
  final bool smsEnabled;
  final bool transactionAlerts;
  final bool securityAlerts;
  final bool marketingAlerts;

  const NotificationPreferencesModel({
    this.inAppEnabled = true,
    this.pushEnabled = true,
    this.emailEnabled = false,
    this.smsEnabled = true,
    this.transactionAlerts = true,
    this.securityAlerts = true,
    this.marketingAlerts = false,
  });

  factory NotificationPreferencesModel.fromJson(Map<String, dynamic> json) {
    return NotificationPreferencesModel(
      inAppEnabled: json['inAppEnabled'] as bool? ?? true,
      pushEnabled: json['pushEnabled'] as bool? ?? true,
      emailEnabled: json['emailEnabled'] as bool? ?? false,
      smsEnabled: json['smsEnabled'] as bool? ?? true,
      transactionAlerts: json['transactionAlerts'] as bool? ?? true,
      securityAlerts: json['securityAlerts'] as bool? ?? true,
      marketingAlerts: json['marketingAlerts'] as bool? ?? false,
    );
  }

  NotificationPreferencesModel copyWith({
    bool? inAppEnabled,
    bool? pushEnabled,
    bool? emailEnabled,
    bool? smsEnabled,
    bool? transactionAlerts,
    bool? securityAlerts,
    bool? marketingAlerts,
  }) {
    return NotificationPreferencesModel(
      inAppEnabled: inAppEnabled ?? this.inAppEnabled,
      pushEnabled: pushEnabled ?? this.pushEnabled,
      emailEnabled: emailEnabled ?? this.emailEnabled,
      smsEnabled: smsEnabled ?? this.smsEnabled,
      transactionAlerts: transactionAlerts ?? this.transactionAlerts,
      securityAlerts: securityAlerts ?? this.securityAlerts,
      marketingAlerts: marketingAlerts ?? this.marketingAlerts,
    );
  }
}
