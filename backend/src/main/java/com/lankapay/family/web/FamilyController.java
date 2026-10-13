package com.lankapay.family.web;

import com.lankapay.family.service.FamilyService;
import com.lankapay.family.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for SIMULATED guardian-managed family accounts.
 *
 * NOTE FOR COMPLIANCE:
 * Real-world production deployment requires independent legal counsel regarding
 * minors' data privacy (e.g. COPPA/GDPR-K) and verifiable parental consent (VPC).
 */
@Tag(name = "Family", description = "Simulated guardian-managed family member accounts for minors")
@RestController
@RequestMapping("/api/v1/family")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @Operation(summary = "Create family member profile",
            description = "Guardian creates a child profile with first name, age band, and spending controls.")
    @PostMapping("/members")
    public ResponseEntity<FamilyMemberDto> createMember(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateFamilyMemberRequest request,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        FamilyMemberDto dto = familyService.createMember(userId, request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "List guardian's family members",
            description = "Returns active family member profiles managed by the authenticated guardian.")
    @GetMapping("/members")
    public ResponseEntity<List<FamilyMemberDto>> listMembers(
            @AuthenticationPrincipal Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(familyService.listMembers(userId));
    }

    @Operation(summary = "Get family member profile",
            description = "Returns controls, balance, and limits. Accessible only by the managing guardian.")
    @GetMapping("/members/{id}")
    public ResponseEntity<FamilyMemberDto> getMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String memberId
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(familyService.getMember(userId, memberId));
    }

    @Operation(summary = "Update family member controls",
            description = "Guardian updates spending limits, allowed categories, freeze status, and allowance schedule.")
    @PutMapping("/members/{id}/controls")
    public ResponseEntity<FamilyMemberDto> updateControls(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String memberId,
            @RequestBody @Valid UpdateFamilyControlsRequest request,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(familyService.updateControls(userId, memberId, request, clientIp));
    }

    @Operation(summary = "Freeze or unfreeze family member",
            description = "Quick toggle to freeze/unfreeze child card/spending.")
    @PostMapping("/members/{id}/freeze")
    public ResponseEntity<FamilyMemberDto> freezeMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String memberId,
            @RequestParam(name = "frozen", defaultValue = "true") boolean frozen,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(familyService.setFreeze(userId, memberId, frozen, clientIp));
    }

    @Operation(summary = "Fund family member balance",
            description = "Only the verified guardian can fund the child's simulated balance.")
    @PostMapping("/members/{id}/fund")
    public ResponseEntity<FamilyMemberDto> fundMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String memberId,
            @RequestBody @Valid FundFamilyMemberRequest request,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(familyService.fundMember(userId, memberId, request, clientIp));
    }

    @Operation(summary = "Delete family member profile",
            description = "Permanently removes minor profile data according to data minimisation & retention rules.")
    @DeleteMapping("/members/{id}")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String memberId,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        familyService.deleteMember(userId, memberId, clientIp);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "View family member transaction activity",
            description = "Lists append-only ledger transaction activity for guardian oversight.")
    @GetMapping("/members/{id}/activity")
    public ResponseEntity<List<FamilyTransactionDto>> getActivity(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String memberId
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(familyService.getActivity(userId, memberId));
    }

    @Operation(summary = "Child-safe minimal dashboard view",
            description = "Simplified view showing balance, remaining limit, and allowed categories.")
    @GetMapping("/members/{id}/child-view")
    public ResponseEntity<ChildViewDto> getChildView(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String memberId
    ) {
        return ResponseEntity.ok(familyService.getChildView(userId, memberId));
    }

    @Operation(summary = "Child simulated transaction spend",
            description = "Executes purchase subject to guardian-enforced rules (limits, allowed categories, freeze).")
    @PostMapping("/members/{id}/spend")
    public ResponseEntity<FamilyTransactionDto> spend(
            @PathVariable("id") String memberId,
            @RequestBody @Valid SpendFamilyMemberRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        FamilyTransactionDto tx = familyService.spend(memberId, request, clientIp);
        return ResponseEntity.ok(tx);
    }
}
