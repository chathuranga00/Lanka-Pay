package com.lankapay.search.web.dto;

import com.lankapay.search.domain.SearchResultType;
import java.util.List;
import java.util.Map;

/**
 * Aggregated search response containing both flat and grouped results.
 */
public record SearchResponse(
        String query,
        int totalCount,
        List<SearchResultItem> results,
        Map<SearchResultType, List<SearchResultItem>> groupedResults
) {
}
