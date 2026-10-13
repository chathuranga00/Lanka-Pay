package com.lankapay.rewards.web;

import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.rewards.service.RewardsService;
import com.lankapay.rewards.web.dto.PointsLedgerEntryDto;
import com.lankapay.rewards.web.dto.RedeemPointsRequest;
import com.lankapay.rewards.web.dto.RedeemPointsResponse;
import com.lankapay.rewards.web.dto.RewardOfferDto;
import com.lankapay.rewards.web.dto.RewardsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Rewards", description = "Simulated rewards points, append-only ledger, offers and redemptions")
public class RewardsController {

    private final RewardsService rewardsService;
    private final UserStore userStore;

    @Autowired
    public RewardsController(RewardsService rewardsService, UserStore userStore) {
        this.rewardsService = rewardsService;
        this.userStore = userStore;
    }

    private User resolveUser(org.springframework.security.core.userdetails.User principal) {
        String phone = principal.getUsername();
        return userStore.findUserByPhone(phone)
                .map(UserAccount::toUser)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found for phone: " + phone));
    }

    @GetMapping({"/api/v1/rewards", "/rewards"})
    @Operation(summary = "Get rewards points balance, tier, and expiring soon points")
    public ResponseEntity<RewardsSummaryResponse> getRewards(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(rewardsService.getSummary(user.getId()));
    }

    @GetMapping({"/api/v1/rewards/ledger", "/rewards/ledger"})
    @Operation(summary = "Get append-only points ledger entries with running balance")
    public ResponseEntity<List<PointsLedgerEntryDto>> getLedger(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @RequestParam(required = false, defaultValue = "ALL") String type
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(rewardsService.getLedger(user.getId(), type));
    }

    @GetMapping({"/api/v1/rewards/offers", "/rewards/offers"})
    @Operation(summary = "Get active reward offers with multipliers and caps")
    public ResponseEntity<List<RewardOfferDto>> getOffers() {
        return ResponseEntity.ok(rewardsService.getOffers());
    }

    @PostMapping({"/api/v1/rewards/redeem", "/rewards/redeem"})
    @Operation(summary = "Redeem simulated points with idempotency and step-up authentication")
    public ResponseEntity<RedeemPointsResponse> redeem(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @Valid @RequestBody RedeemPointsRequest request
    ) {
        User user = resolveUser(principal);
        RedeemPointsResponse response = rewardsService.redeemPoints(user, request, idempotencyKey, stepUpPinHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
