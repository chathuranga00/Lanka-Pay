import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import 'home_api_model.dart';

final homeApiServiceProvider = Provider<HomeApiService>((ref) {
  return HomeApiService(ref.watch(dioProvider));
});

class HomeApiService {
  final Dio _dio;

  HomeApiService(this._dio);

  Future<HomeAggregateResponseModel> getHome() async {
    final response = await _dio.get('/api/v1/home');
    return HomeAggregateResponseModel.fromJson(response.data as Map<String, dynamic>);
  }
}
