package com.lankapay.search.web.dto;

import com.lankapay.search.domain.SearchResultType;
import java.util.Map;

/**
 * Individual search hit item.
 */
public record SearchResultItem(
        String id,
        SearchResultType type,
        String title,
        String subtitle,
        String category,
        String deepLink,
        String icon,
        Map<String, Object> metadata
) {
    public SearchResultItem(
            String id,
            SearchResultType type,
            String title,
            String subtitle,
            String category,
            String deepLink,
            String icon
    ) {
        this(id, type, title, subtitle, category, deepLink, icon, Map.of());
    }
}
