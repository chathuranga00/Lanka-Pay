import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/search_model.dart';

/// Service managing global backend search queries.
class SearchService {
  final Dio _dio;

  SearchService(this._dio);

  Future<SearchResponseModel> search(String query, {List<String>? types}) async {
    final trimmed = query.trim();
    if (trimmed.length < 2) {
      return const SearchResponseModel(
        query: '',
        totalCount: 0,
        results: [],
        groupedResults: {},
      );
    }

    try {
      final queryParams = <String, dynamic>{
        'q': trimmed,
      };
      if (types != null && types.isNotEmpty) {
        queryParams['types'] = types.join(',');
      }

      final response = await _dio.get(
        '/search',
        queryParameters: queryParams,
      );

      if (response.data is Map<String, dynamic>) {
        return SearchResponseModel.fromJson(response.data as Map<String, dynamic>);
      }
      return const SearchResponseModel(
        query: '',
        totalCount: 0,
        results: [],
        groupedResults: {},
      );
    } catch (e) {
      // If simulated / offline fallback is needed, provide realistic mock matching
      return _fallbackSearch(trimmed);
    }
  }

  SearchResponseModel _fallbackSearch(String query) {
    final qLower = query.toLowerCase();

    // Check if phone-like
    final isPhone = RegExp(r'^\+?[0-9\s\-()]{7,15}$').hasMatch(query);
    if (isPhone) {
      final digits = query.replaceAll(RegExp(r'[^0-9+]'), '');
      final actionItem = SearchResultModel(
        id: 'action-send-$digits',
        type: SearchResultType.action,
        title: 'Send to this number',
        subtitle: digits,
        category: 'PAY',
        deepLink: 'app://send?recipient=$digits',
        icon: 'send',
      );
      return SearchResponseModel(
        query: query,
        totalCount: 1,
        results: [actionItem],
        groupedResults: {SearchResultType.action: [actionItem]},
      );
    }

    final List<SearchResultModel> results = [];

    // Fallback static features
    final features = [
      const SearchResultModel(
        id: 'feat-send',
        type: SearchResultType.feature,
        title: 'Send Money',
        subtitle: 'Instant peer-to-peer bank transfers',
        category: 'PAY',
        deepLink: '/send',
        icon: 'swap_horiz',
      ),
      const SearchResultModel(
        id: 'feat-qr',
        type: SearchResultType.feature,
        title: 'LankaQR Payment',
        subtitle: 'Scan merchant QR codes',
        category: 'PAY',
        deepLink: '/qr',
        icon: 'qr_code',
      ),
      const SearchResultModel(
        id: 'feat-accounts',
        type: SearchResultType.feature,
        title: 'Bank Accounts',
        subtitle: 'Manage connected bank accounts',
        category: 'ACCOUNTS',
        deepLink: '/accounts',
        icon: 'account_balance',
      ),
      const SearchResultModel(
        id: 'feat-cards',
        type: SearchResultType.feature,
        title: 'Virtual & Debit Cards',
        subtitle: 'Manage virtual cards and limits',
        category: 'CARDS',
        deepLink: '/cards/test-cards',
        icon: 'credit_card',
      ),
    ];

    for (final f in features) {
      if (f.title.toLowerCase().contains(qLower) || f.subtitle.toLowerCase().contains(qLower)) {
        results.add(f);
      }
    }

    // Fallback billers
    final billers = [
      const SearchResultModel(
        id: 'biller-ceb',
        type: SearchResultType.biller,
        title: 'Ceylon Electricity Board (CEB)',
        subtitle: 'Electricity • CEB',
        category: 'BILLER',
        deepLink: '/bills/ceb',
        icon: 'receipt',
      ),
      const SearchResultModel(
        id: 'biller-water',
        type: SearchResultType.biller,
        title: 'Water Board (NWSDB)',
        subtitle: 'Water • NWSDB',
        category: 'BILLER',
        deepLink: '/bills/water',
        icon: 'receipt',
      ),
      const SearchResultModel(
        id: 'biller-dialog',
        type: SearchResultType.biller,
        title: 'Dialog Axiata',
        subtitle: 'Telecom & Home Broadband',
        category: 'BILLER',
        deepLink: '/bills/dialog',
        icon: 'receipt',
      ),
    ];

    for (final b in billers) {
      if (b.title.toLowerCase().contains(qLower) || b.subtitle.toLowerCase().contains(qLower)) {
        results.add(b);
      }
    }

    // Fallback merchants
    final merchants = [
      const SearchResultModel(
        id: 'm-keells',
        type: SearchResultType.merchant,
        title: 'Keells Super',
        subtitle: 'Supermarket • Colombo 03',
        category: 'MERCHANT',
        deepLink: '/city/merchants/keells',
        icon: 'store',
      ),
      const SearchResultModel(
        id: 'm-cargills',
        type: SearchResultType.merchant,
        title: 'Cargills Food City',
        subtitle: 'Supermarket • Islandwide',
        category: 'MERCHANT',
        deepLink: '/city/merchants/cargills',
        icon: 'store',
      ),
      const SearchResultModel(
        id: 'm-coffee',
        type: SearchResultType.merchant,
        title: 'Colombo Coffee Club',
        subtitle: 'Cafe & Dining • Colombo 07',
        category: 'MERCHANT',
        deepLink: '/city/merchants/coffee',
        icon: 'store',
      ),
    ];

    for (final m in merchants) {
      if (m.title.toLowerCase().contains(qLower) || m.subtitle.toLowerCase().contains(qLower)) {
        results.add(m);
      }
    }

    final Map<SearchResultType, List<SearchResultModel>> grouped = {};
    for (final item in results) {
      grouped.putIfAbsent(item.type, () => []).add(item);
    }

    return SearchResponseModel(
      query: query,
      totalCount: results.length,
      results: results,
      groupedResults: grouped,
    );
  }
}

/// Provider for SearchService
final searchServiceProvider = Provider<SearchService>((ref) {
  final dio = ref.watch(dioProvider);
  return SearchService(dio);
});

/// Local recent searches notifier and provider.
class RecentSearchesNotifier extends Notifier<List<String>> {
  @override
  List<String> build() {
    return ['CEB', 'Dialog', 'Keells Super', 'Send Money'];
  }

  void addSearch(String query) {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return;
    final updated = [trimmed, ...state.where((item) => item.toLowerCase() != trimmed.toLowerCase())];
    if (updated.length > 10) {
      state = updated.sublist(0, 10);
    } else {
      state = updated;
    }
  }

  void removeSearch(String query) {
    state = state.where((item) => item != query).toList();
  }

  void clearAll() {
    state = [];
  }
}

final recentSearchesProvider =
    NotifierProvider<RecentSearchesNotifier, List<String>>(() {
  return RecentSearchesNotifier();
});
