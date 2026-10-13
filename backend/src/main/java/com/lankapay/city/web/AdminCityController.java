package com.lankapay.city.web;

import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.domain.CityOffer;
import com.lankapay.city.service.CityService;
import com.lankapay.city.web.dto.CreateMerchantRequest;
import com.lankapay.city.web.dto.CreateOfferRequest;
import com.lankapay.city.web.dto.UpdateMerchantRequest;
import com.lankapay.city.web.dto.UpdateOfferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Admin City", description = "Admin controls for managing simulated merchants and offers (audited)")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCityController {

    private final CityService cityService;

    @Autowired
    public AdminCityController(CityService cityService) {
        this.cityService = cityService;
    }

    private Long resolveAdminId(Object principal, Long queryUserId) {
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            return 9999L;
        } else if (principal instanceof Long l) {
            return l;
        }
        return queryUserId != null ? queryUserId : 9999L;
    }

    // ── Merchants ──────────────────────────────────────────────────────────

    @PostMapping({"/api/v1/admin/city/merchants", "/city/admin/merchants"})
    @Operation(summary = "Admin create merchant (audited)")
    public ResponseEntity<CityMerchant> createMerchant(
            @Valid @RequestBody CreateMerchantRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(cityService.adminCreateMerchant(adminId, request, ip));
    }

    @PutMapping({"/api/v1/admin/city/merchants/{id}", "/city/admin/merchants/{id}"})
    @Operation(summary = "Admin update merchant (audited)")
    public ResponseEntity<CityMerchant> updateMerchant(
            @PathVariable String id,
            @Valid @RequestBody UpdateMerchantRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(cityService.adminUpdateMerchant(adminId, id, request, ip));
    }

    @DeleteMapping({"/api/v1/admin/city/merchants/{id}", "/city/admin/merchants/{id}"})
    @Operation(summary = "Admin delete merchant (audited)")
    public ResponseEntity<Void> deleteMerchant(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        cityService.adminDeleteMerchant(adminId, id, ip);
        return ResponseEntity.noContent().build();
    }

    // ── Offers ──────────────────────────────────────────────────────────────

    @PostMapping({"/api/v1/admin/city/offers", "/city/admin/offers"})
    @Operation(summary = "Admin create offer (audited)")
    public ResponseEntity<CityOffer> createOffer(
            @Valid @RequestBody CreateOfferRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(cityService.adminCreateOffer(adminId, request, ip));
    }

    @PutMapping({"/api/v1/admin/city/offers/{id}", "/city/admin/offers/{id}"})
    @Operation(summary = "Admin update offer (audited)")
    public ResponseEntity<CityOffer> updateOffer(
            @PathVariable String id,
            @Valid @RequestBody UpdateOfferRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(cityService.adminUpdateOffer(adminId, id, request, ip));
    }

    @DeleteMapping({"/api/v1/admin/city/offers/{id}", "/city/admin/offers/{id}"})
    @Operation(summary = "Admin delete offer (audited)")
    public ResponseEntity<Void> deleteOffer(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        cityService.adminDeleteOffer(adminId, id, ip);
        return ResponseEntity.noContent().build();
    }
}
