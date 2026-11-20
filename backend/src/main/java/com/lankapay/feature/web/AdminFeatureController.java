package com.lankapay.feature.web;

import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.feature.web.dto.FeaturesResponse;
import com.lankapay.feature.web.dto.UpdateFeatureRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Administrative endpoints to view and toggle Super App feature flags.
 * Restricted to ADMIN role only.
 */
@Tag(name = "Admin Feature Flags", description = "Admin controls for enabling or disabling Super App modules")
@RestController
@RequestMapping("/api/v1/admin/features")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeatureController {

    private final FeatureFlagService featureFlagService;

    public AdminFeatureController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Operation(summary = "Get all features (admin)", description = "Returns feature status map for administrators.")
    @ApiResponse(responseCode = "200", description = "Features returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Access denied (non-admin)")
    @GetMapping
    public ResponseEntity<FeaturesResponse> getAdminFeatures(@AuthenticationPrincipal Long adminUserId) {
        if (adminUserId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new FeaturesResponse(
                featureFlagService.getAllFeatures(),
                featureFlagService.getEnvironment(),
                featureFlagService.getDisclaimer()
        ));
    }

    @Operation(summary = "Bulk update feature flags", description = "Updates multiple feature flags simultaneously.")
    @ApiResponse(responseCode = "200", description = "Features updated")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Access denied (non-admin)")
    @PutMapping
    public ResponseEntity<FeaturesResponse> updateFeatures(
            @RequestBody Map<String, Boolean> updates,
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest request) {

        if (adminUserId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = request != null ? request.getRemoteAddr() : "127.0.0.1";
        featureFlagService.setFeatures(updates, adminUserId, clientIp);
        return ResponseEntity.ok(new FeaturesResponse(
                featureFlagService.getAllFeatures(),
                featureFlagService.getEnvironment(),
                featureFlagService.getDisclaimer()
        ));
    }

    @Operation(summary = "Toggle a single feature flag", description = "Enables or disables a specific feature flag.")
    @ApiResponse(responseCode = "200", description = "Feature flag updated")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Access denied (non-admin)")
    @PatchMapping("/{featureKey}")
    public ResponseEntity<FeaturesResponse> toggleFeature(
            @PathVariable String featureKey,
            @Valid @RequestBody UpdateFeatureRequest request,
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest httpRequest) {

        if (adminUserId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        featureFlagService.setFeature(featureKey, request.enabled(), adminUserId, clientIp);
        return ResponseEntity.ok(new FeaturesResponse(
                featureFlagService.getAllFeatures(),
                featureFlagService.getEnvironment(),
                featureFlagService.getDisclaimer()
        ));
    }
}
