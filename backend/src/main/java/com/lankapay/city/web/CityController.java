package com.lankapay.city.web;

import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.service.CityService;
import com.lankapay.city.web.dto.*;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "City", description = "Simulated merchant discovery, categories, offers, favourites, and in-store pay flow")
public class CityController {

    private final CityService cityService;
    private final UserStore userStore;

    @Autowired
    public CityController(CityService cityService, @Autowired(required = false) UserStore userStore) {
        this.cityService = cityService;
        this.userStore = userStore;
    }

    private Long resolveUserId(Object principal, Long queryUserId) {
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            if (userStore != null) {
                return userStore.findUserByPhone(u.getUsername())
                        .map(UserAccount::id)
                        .orElse(null);
            }
        } else if (principal instanceof Long l) {
            return l;
        } else if (principal instanceof User user) {
            return user.getId();
        }
        return queryUserId;
    }

    private User resolveUser(Object principal, Long queryUserId) {
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            if (userStore != null) {
                return userStore.findUserByPhone(u.getUsername())
                        .map(UserAccount::toUser)
                        .orElse(null);
            }
        } else if (principal instanceof User user) {
            return user;
        } else if (principal instanceof Long l) {
            if (userStore != null) {
                return userStore.findUserById(l).map(UserAccount::toUser).orElse(null);
            }
        }
        if (queryUserId != null && userStore != null) {
            return userStore.findUserById(queryUserId).map(UserAccount::toUser).orElse(null);
        }
        return null;
    }

    // ── Categories ──────────────────────────────────────────────────────────

    @GetMapping({"/api/v1/city/categories", "/city/categories"})
    @Operation(summary = "List merchant categories with counts")
    public ResponseEntity<List<CityCategoryDto>> getCategories() {
        return ResponseEntity.ok(cityService.getCategories());
    }

    // ── Merchants with Filters & Cursor Pagination ─────────────────────────

    @GetMapping({"/api/v1/city/merchants", "/city/merchants"})
    @Operation(summary = "Get merchants with category, search, area filters and cursor pagination")
    public ResponseEntity<MerchantPageResponse> getMerchants(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long effectiveUserId = resolveUserId(principal, userId);

        CityCategory cityCategory = null;
        if (category != null && !category.isBlank()) {
            try {
                cityCategory = CityCategory.valueOf(category.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return ResponseEntity.ok(cityService.getMerchants(effectiveUserId, cityCategory, search, area, cursor, limit));
    }

    @GetMapping({"/api/v1/city/merchants/{id}", "/city/merchants/{id}"})
    @Operation(summary = "Get merchant details, hours, and linked offers")
    public ResponseEntity<CityMerchantDto> getMerchant(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long effectiveUserId = resolveUserId(principal, userId);
        return ResponseEntity.ok(cityService.getMerchantById(effectiveUserId, id));
    }

    @GetMapping({"/api/v1/city/offers", "/city/offers"})
    @Operation(summary = "List all active promotional offers across merchants")
    public ResponseEntity<List<CityOfferDto>> getOffers() {
        return ResponseEntity.ok(cityService.getActiveOffers());
    }

    // ── Favourites ──────────────────────────────────────────────────────────

    @GetMapping({"/api/v1/city/favourites", "/city/favourites"})
    @Operation(summary = "Get user's favourite merchants")
    public ResponseEntity<List<CityMerchantDto>> getFavourites(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long effectiveUserId = resolveUserId(principal, userId);
        if (effectiveUserId == null) {
            throw new ValidationException("UNAUTHORIZED", "Authentication required to view favourites");
        }
        return ResponseEntity.ok(cityService.getFavourites(effectiveUserId));
    }

    @PostMapping({"/api/v1/city/favourites", "/city/favourites"})
    @Operation(summary = "Add merchant to user favourites")
    public ResponseEntity<Void> addFavourite(
            @RequestBody(required = false) AddFavouriteRequest body,
            @RequestParam(required = false) String merchantId,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long effectiveUserId = resolveUserId(principal, userId);
        if (effectiveUserId == null) {
            throw new ValidationException("UNAUTHORIZED", "Authentication required to add favourite");
        }
        String targetMerchantId = body != null && body.merchantId() != null ? body.merchantId() : merchantId;
        cityService.addFavourite(effectiveUserId, targetMerchantId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping({"/api/v1/city/favourites/{merchantId}", "/city/favourites/{merchantId}"})
    @Operation(summary = "Remove merchant from favourites")
    public ResponseEntity<Void> removeFavourite(
            @PathVariable String merchantId,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long effectiveUserId = resolveUserId(principal, userId);
        if (effectiveUserId == null) {
            throw new ValidationException("UNAUTHORIZED", "Authentication required to remove favourite");
        }
        cityService.removeFavourite(effectiveUserId, merchantId);
        return ResponseEntity.noContent().build();
    }

    // ── Pay At Merchant ─────────────────────────────────────────────────────

    @PostMapping({"/api/v1/city/merchants/{id}/pay", "/city/merchants/{id}/pay"})
    @Operation(summary = "Pay at merchant reusing QR flow, authorizing and triggering rewards event")
    public ResponseEntity<PayAtMerchantResponse> payAtMerchant(
            @PathVariable String id,
            @Valid @RequestBody PayAtMerchantRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId,
            HttpServletRequest servletRequest
    ) {
        User user = resolveUser(principal, userId);
        if (user == null) {
            throw new ValidationException("UNAUTHORIZED", "Authentication required to pay at merchant");
        }
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(cityService.payAtMerchant(user, id, request, ip));
    }
}
