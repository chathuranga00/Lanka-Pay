package com.lankapay.city.web.dto;

import java.util.List;

public record MerchantPageResponse(
        List<CityMerchantDto> merchants,
        String nextCursor,
        boolean hasMore,
        int totalCount
) {
}
