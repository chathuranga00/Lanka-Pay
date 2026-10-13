import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_tokens.dart';
import '../../../core/widgets/empty_state.dart';
import '../../../core/widgets/error_retry.dart';
import '../../../core/widgets/search_bar_field.dart';
import '../data/search_service.dart';
import '../domain/search_model.dart';

/// Global Search Screen for LankaPay ID Super App.
/// Features debounced search input, grouped results, recent search history,
/// loading, empty, and error retry states.
class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key = const Key('search_screen')});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final TextEditingController _controller = TextEditingController();
  Timer? _debounceTimer;

  bool _isLoading = false;
  String? _errorMessage;
  SearchResponseModel? _response;

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onQueryChanged(String query) {
    _debounceTimer?.cancel();
    if (query.trim().length < 2) {
      setState(() {
        _isLoading = false;
        _errorMessage = null;
        _response = null;
      });
      return;
    }

    _debounceTimer = Timer(const Duration(milliseconds: 300), () {
      _executeSearch(query);
    });
  }

  Future<void> _executeSearch(String query) async {
    final trimmed = query.trim();
    if (trimmed.length < 2) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(searchServiceProvider);
      final result = await service.search(trimmed);
      if (!mounted) return;

      setState(() {
        _isLoading = false;
        _response = result;
      });

      // Save to recent searches
      ref.read(recentSearchesProvider.notifier).addSearch(trimmed);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = 'Could not load search results. Check network connection.';
      });
    }
  }

  void _selectRecent(String query) {
    _controller.text = query;
    _controller.selection = TextSelection.fromPosition(
      TextPosition(offset: query.length),
    );
    _executeSearch(query);
  }

  void _handleResultTap(SearchResultModel item) {
    if (item.deepLink != null && item.deepLink!.isNotEmpty) {
      final link = item.deepLink!;
      if (link.startsWith('app://send')) {
        context.push('/send');
      } else if (link.startsWith('/')) {
        context.push(link);
      } else {
        context.push('/$link');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final recentSearches = ref.watch(recentSearchesProvider);

    return Scaffold(
      key: const Key('search_screen_scaffold'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        leading: IconButton(
          key: const Key('search_back_btn'),
          icon: Icon(
            Icons.arrow_back_ios_new_rounded,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
            size: 20,
          ),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        titleSpacing: 0,
        title: Padding(
          padding: const EdgeInsets.only(right: 16.0),
          child: SearchBarField(
            key: const Key('search_input'),
            controller: _controller,
            hintText: 'Search features, bills, merchants, pay...',
            onChanged: _onQueryChanged,
            onSubmitted: _executeSearch,
          ),
        ),
      ),
      body: SafeArea(
        child: _buildBody(context, isDark, recentSearches),
      ),
    );
  }

  Widget _buildBody(BuildContext context, bool isDark, List<String> recentSearches) {
    // 1. Loading State
    if (_isLoading) {
      return const Center(
        key: Key('search_loading'),
        child: CircularProgressIndicator(color: AppTokens.primaryBlue),
      );
    }

    // 2. Error State
    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: ErrorRetry(
            key: const Key('search_error_state'),
            message: _errorMessage!,
            retryLabel: 'Retry',
            onRetry: () => _executeSearch(_controller.text),
          ),
        ),
      );
    }

    // 3. Search Results State
    if (_response != null) {
      if (_response!.results.isEmpty) {
        return Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: EmptyState(
              key: const Key('search_empty_state'),
              icon: Icons.search_off_rounded,
              title: 'No Results Found',
              description: 'We could not find anything matching "${_controller.text}". Try another query.',
            ),
          ),
        );
      }

      return _buildGroupedResultsList(context, isDark, _response!);
    }

    // 4. Default / Recent Searches State
    return _buildRecentSearches(context, isDark, recentSearches);
  }

  Widget _buildRecentSearches(BuildContext context, bool isDark, List<String> recentSearches) {
    if (recentSearches.isEmpty) {
      return Center(
        child: Text(
          'Type at least 2 characters to search',
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
            fontSize: 14,
          ),
        ),
      );
    }

    return ListView(
      key: const Key('search_recent_list'),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'RECENT SEARCHES',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                letterSpacing: 0.8,
              ),
            ),
            TextButton(
              key: const Key('search_clear_recent_btn'),
              onPressed: () {
                ref.read(recentSearchesProvider.notifier).clearAll();
              },
              child: const Text(
                'Clear all',
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  color: AppTokens.primaryBlue,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        ...recentSearches.map((item) {
          return ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(
              Icons.history_rounded,
              color: AppTokens.primaryBlue,
              size: 20,
            ),
            title: Text(
              item,
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 15,
                fontWeight: FontWeight.w500,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
            trailing: IconButton(
              icon: Icon(
                Icons.close_rounded,
                size: 18,
                color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
              ),
              onPressed: () {
                ref.read(recentSearchesProvider.notifier).removeSearch(item);
              },
            ),
            onTap: () => _selectRecent(item),
          );
        }),
      ],
    );
  }

  Widget _buildGroupedResultsList(
      BuildContext context, bool isDark, SearchResponseModel response) {
    return ListView(
      key: const Key('search_results_list'),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        for (final entry in response.groupedResults.entries) ...[
          Padding(
            padding: const EdgeInsets.only(top: 14.0, bottom: 8.0),
            child: Text(
              entry.key.displayName.toUpperCase(),
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                letterSpacing: 0.8,
              ),
            ),
          ),
          ...entry.value.map((item) => _buildResultTile(context, isDark, item)),
        ],
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _buildResultTile(BuildContext context, bool isDark, SearchResultModel item) {
    IconData iconData;
    switch (item.type) {
      case SearchResultType.action:
        iconData = Icons.send_rounded;
        break;
      case SearchResultType.biller:
        iconData = Icons.receipt_long_rounded;
        break;
      case SearchResultType.merchant:
        iconData = Icons.storefront_rounded;
        break;
      case SearchResultType.recipient:
        iconData = Icons.person_rounded;
        break;
      case SearchResultType.transaction:
        iconData = Icons.receipt_rounded;
        break;
      case SearchResultType.feature:
      default:
        iconData = Icons.star_rounded;
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: isDark ? AppTokens.cardDark : Colors.white,
        clipBehavior: Clip.antiAlias,
        elevation: isDark ? 0 : 1,
        shadowColor: Colors.black.withOpacity(0.04),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(
            color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
            width: 0.8,
          ),
        ),
        child: ListTile(
        key: Key('result_${item.id}'),
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: isDark ? AppTokens.tileTintDark : AppTokens.tileTintLight,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(iconData, color: AppTokens.primaryBlue, size: 22),
        ),
        title: Text(
          item.title,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 15,
            fontWeight: FontWeight.w700,
            color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
          ),
        ),
        subtitle: Text(
          item.subtitle,
          style: TextStyle(
            fontFamily: AppTokens.fontFamily,
            fontSize: 13,
            color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
          ),
        ),
        trailing: Icon(
          Icons.arrow_forward_ios_rounded,
          size: 14,
          color: isDark ? AppTokens.textTertiaryDark : AppTokens.textTertiaryLight,
        ),
        onTap: () => _handleResultTap(item),
      ),
    ),
  );
}
}
