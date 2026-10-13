package com.lankapay.bill.web;

import com.lankapay.bill.service.SavedBillerService;
import com.lankapay.bill.web.dto.SavedBillerRequest;
import com.lankapay.bill.web.dto.SavedBillerResponse;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/saved-billers")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Saved Billers", description = "CRUD operations for user saved billers")
public class SavedBillerController {

    private final SavedBillerService savedBillerService;
    private final UserStore userStore;

    public SavedBillerController(SavedBillerService savedBillerService, UserStore userStore) {
        this.savedBillerService = savedBillerService;
        this.userStore = userStore;
    }

    @GetMapping
    @Operation(summary = "List all saved billers for the authenticated user")
    public ResponseEntity<List<SavedBillerResponse>> listSavedBillers(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(savedBillerService.getSavedBillers(user.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a saved biller by ID")
    public ResponseEntity<SavedBillerResponse> getSavedBiller(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(savedBillerService.getSavedBillerById(id, user.getId()));
    }

    @PostMapping
    @Operation(summary = "Save a biller with reference and nickname")
    public ResponseEntity<SavedBillerResponse> createSavedBiller(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @Valid @RequestBody SavedBillerRequest request,
            HttpServletRequest httpRequest
    ) {
        User user = resolveUser(principal);
        String clientIp = httpRequest.getRemoteAddr();
        SavedBillerResponse saved = savedBillerService.saveBiller(user.getId(), request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a saved biller's nickname or reference")
    public ResponseEntity<SavedBillerResponse> updateSavedBiller(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id,
            @Valid @RequestBody SavedBillerRequest request,
            HttpServletRequest httpRequest
    ) {
        User user = resolveUser(principal);
        String clientIp = httpRequest.getRemoteAddr();
        SavedBillerResponse updated = savedBillerService.updateBiller(id, user.getId(), request, clientIp);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved biller")
    public ResponseEntity<Void> deleteSavedBiller(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        User user = resolveUser(principal);
        String clientIp = httpRequest.getRemoteAddr();
        savedBillerService.deleteBiller(id, user.getId(), clientIp);
        return ResponseEntity.noContent().build();
    }

    private User resolveUser(org.springframework.security.core.userdetails.User principal) {
        return userStore.findUserByPhone(principal.getUsername())
                .map(acc -> {
                    User u = new User(acc.phone(), acc.fullName(), null, null);
                    u.setId(acc.id());
                    return u;
                })
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getUsername()));
    }
}
