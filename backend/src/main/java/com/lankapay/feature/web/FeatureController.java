package com.lankapay.feature.web;

import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.feature.web.dto.FeaturesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint for clients to check which Super App modules are active.
 * Requires authenticated access (protected by SecurityConfig).
 */
@Tag(name = "Feature Flags", description = "Query enabled Super App features and modules")
@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {

    private final FeatureFlagService featureFlagService;

    public FeatureController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Operation(summary = "Get enabled features", description = "Returns active feature flags for the current environment.")
    @ApiResponse(responseCode = "200", description = "Features map returned successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @GetMapping
    public ResponseEntity<FeaturesResponse> getFeatures(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new FeaturesResponse(
                featureFlagService.getAllFeatures(),
                featureFlagService.getEnvironment(),
                featureFlagService.getDisclaimer()
        ));
    }
}
