package com.lankapay.search.web;

import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.web.ErrorResponse;
import com.lankapay.search.domain.SearchResultType;
import com.lankapay.search.service.SearchRateLimiter;
import com.lankapay.search.service.SearchService;
import com.lankapay.search.web.dto.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Super App Global Search Controller.
 *
 * Enforces:
 * - Mandatory user authentication (401 if unauthenticated)
 * - Minimum 2-character query length (400 if < 2)
 * - Rate limiting per user and per IP (429 if exceeded)
 * - Anti-enumeration: phone queries return single send action, no user leak
 * - Scoped strictly to authenticated user's data + public catalogs
 */
@Tag(name = "Global Search", description = "Anti-enumeration search across features, transactions, billers, and merchants")
@RestController
@RequestMapping("/api/v1/search")
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private final SearchService searchService;
    private final SearchRateLimiter searchRateLimiter;

    public SearchController(SearchService searchService, SearchRateLimiter searchRateLimiter) {
        this.searchService = searchService;
        this.searchRateLimiter = searchRateLimiter;
    }

    @Operation(summary = "Search across features, user transactions, billers, and merchants",
               description = "Requires authentication and rate limiting. Minimum 2 characters. "
                           + "Phone numbers return a single 'Send to this number' action without user enumeration.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results returned successfully"),
        @ApiResponse(responseCode = "400", description = "Query too short (minimum 2 characters required)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized (unauthenticated request rejected)"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "types", required = false) String typesParam,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {

        // 1. Authentication check
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Minimum 2 characters validation
        if (query == null || query.trim().length() < 2) {
            throw new ValidationException("SEARCH_QUERY_TOO_SHORT", "Search query must be at least 2 characters");
        }

        // 3. Rate limiting check (per user and per IP)
        String clientIp = extractIp(request);
        searchRateLimiter.consume(userId, clientIp);

        // 4. Parse optional types parameter
        Set<SearchResultType> requestedTypes = parseTypes(typesParam);

        // 5. Execute scoped search
        SearchResponse response = searchService.search(userId, query, requestedTypes, clientIp);
        return ResponseEntity.ok(response);
    }

    private Set<SearchResultType> parseTypes(String typesParam) {
        if (typesParam == null || typesParam.isBlank()) {
            return EnumSet.allOf(SearchResultType.class);
        }
        return Arrays.stream(typesParam.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .map(typeStr -> {
                    try {
                        return SearchResultType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
