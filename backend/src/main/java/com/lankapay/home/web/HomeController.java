package com.lankapay.home.web;

import com.lankapay.home.service.HomeAggregatorService;
import com.lankapay.home.web.dto.HomeAggregateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super App Home Aggregator controller.
 * Delivers the complete Home screen state in a single call.
 * Requires authenticated user.
 */
@Tag(name = "Home Aggregator", description = "One-call resilient aggregator for Super App Home tab")
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeAggregatorService homeAggregatorService;

    public HomeController(HomeAggregatorService homeAggregatorService) {
        this.homeAggregatorService = homeAggregatorService;
    }

    @Operation(summary = "Get aggregated home dashboard",
               description = "Returns user greeting, unread notifications, wallet, recent activity, and shortcuts.")
    @ApiResponse(responseCode = "200", description = "Aggregated dashboard returned successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized (unauthenticated request rejected)")
    @GetMapping
    public ResponseEntity<HomeAggregateResponse> getHome(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(homeAggregatorService.aggregateHome(userId));
    }
}
