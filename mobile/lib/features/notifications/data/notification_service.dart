import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/notification_models.dart';

class NotificationService {
  final Dio _dio;

  NotificationService(this._dio);

  Future<List<NotificationItemModel>> getNotifications({
    bool? unreadOnly,
    int page = 0,
    int size = 20,
  }) async {
    final queryParams = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (unreadOnly != null) queryParams['unreadOnly'] = unreadOnly;

    final response = await _dio.get(
      '/notifications',
      queryParameters: queryParams,
    );

    final data = response.data;
    List items = [];
    if (data is Map && data['content'] is List) {
      items = data['content'] as List;
    } else if (data is List) {
      items = data;
    }

    return items
        .map((e) => NotificationItemModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<void> markAsRead(int notificationId) async {
    await _dio.patch('/notifications/$notificationId/read');
  }

  Future<void> markAllAsRead() async {
    await _dio.post('/notifications/mark-all-read');
  }

  Future<NotificationPreferencesModel> getPreferences() async {
    final response = await _dio.get('/notifications/preferences');
    return NotificationPreferencesModel.fromJson(
      Map<String, dynamic>.from(response.data as Map),
    );
  }

  Future<NotificationPreferencesModel> updatePreferences(
    NotificationPreferencesModel prefs,
  ) async {
    final response = await _dio.patch(
      '/notifications/preferences',
      data: {
        'inAppEnabled': prefs.inAppEnabled,
        'pushEnabled': prefs.pushEnabled,
        'emailEnabled': prefs.emailEnabled,
        'smsEnabled': prefs.smsEnabled,
        'transactionAlerts': prefs.transactionAlerts,
        'securityAlerts': prefs.securityAlerts,
        'marketingAlerts': prefs.marketingAlerts,
      },
    );
    return NotificationPreferencesModel.fromJson(
      Map<String, dynamic>.from(response.data as Map),
    );
  }
}

final notificationServiceProvider = Provider<NotificationService>((ref) {
  final dio = ref.watch(dioProvider);
  return NotificationService(dio);
});
