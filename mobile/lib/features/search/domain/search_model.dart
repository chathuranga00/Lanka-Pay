/// Types of search results returned from backend search API.
enum SearchResultType {
  feature,
  transaction,
  biller,
  merchant,
  recipient,
  action;

  static SearchResultType fromString(String val) {
    switch (val.toUpperCase()) {
      case 'FEATURE':
        return SearchResultType.feature;
      case 'TRANSACTION':
        return SearchResultType.transaction;
      case 'BILLER':
        return SearchResultType.biller;
      case 'MERCHANT':
        return SearchResultType.merchant;
      case 'RECIPIENT':
        return SearchResultType.recipient;
      case 'ACTION':
        return SearchResultType.action;
      default:
        return SearchResultType.feature;
    }
  }

  String get displayName {
    switch (this) {
      case SearchResultType.feature:
        return 'Features & Services';
      case SearchResultType.transaction:
        return 'Past Transactions';
      case SearchResultType.biller:
        return 'Utility Billers';
      case SearchResultType.merchant:
        return 'City Merchants';
      case SearchResultType.recipient:
        return 'Recent Recipients';
      case SearchResultType.action:
        return 'Quick Actions';
    }
  }
}

/// An individual search hit model.
class SearchResultModel {
  final String id;
  final SearchResultType type;
  final String title;
  final String subtitle;
  final String? category;
  final String? deepLink;
  final String? icon;
  final Map<String, dynamic> metadata;

  const SearchResultModel({
    required this.id,
    required this.type,
    required this.title,
    required this.subtitle,
    this.category,
    this.deepLink,
    this.icon,
    this.metadata = const {},
  });

  factory SearchResultModel.fromJson(Map<String, dynamic> json) {
    return SearchResultModel(
      id: json['id']?.toString() ?? '',
      type: SearchResultType.fromString(json['type']?.toString() ?? ''),
      title: json['title']?.toString() ?? '',
      subtitle: json['subtitle']?.toString() ?? '',
      category: json['category']?.toString(),
      deepLink: json['deepLink']?.toString(),
      icon: json['icon']?.toString(),
      metadata: json['metadata'] as Map<String, dynamic>? ?? {},
    );
  }
}

/// Aggregated search response model.
class SearchResponseModel {
  final String query;
  final int totalCount;
  final List<SearchResultModel> results;
  final Map<SearchResultType, List<SearchResultModel>> groupedResults;

  const SearchResponseModel({
    required this.query,
    required this.totalCount,
    required this.results,
    required this.groupedResults,
  });

  factory SearchResponseModel.fromJson(Map<String, dynamic> json) {
    final rawResults = (json['results'] as List<dynamic>? ?? [])
        .map((e) => SearchResultModel.fromJson(e as Map<String, dynamic>))
        .toList();

    final Map<SearchResultType, List<SearchResultModel>> grouped = {};
    for (final item in rawResults) {
      grouped.putIfAbsent(item.type, () => []).add(item);
    }

    return SearchResponseModel(
      query: json['query']?.toString() ?? '',
      totalCount: (json['totalCount'] as num?)?.toInt() ?? rawResults.length,
      results: rawResults,
      groupedResults: grouped,
    );
  }
}
