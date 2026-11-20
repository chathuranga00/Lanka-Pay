package com.lankapay.family.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.family.domain.*;
import com.lankapay.family.web.dto.*;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating simulated guardian-managed family accounts for minors.
 *
 * Safety & Data Minimisation:
 * - Real use requires independent legal advice on minors' privacy regulations (COPPA, GDPR-K)
 *   and verifiable parental consent.
 * - Profile contains ONLY first name, age band, and avatar initials.
 * - NO independent phone number, NO public registry discoverability, NO photos, NO geolocation.
 * - Only the verified guardian can fund the simulated balance.
 * - Every transaction and control modification is strictly audited and notifies the guardian.
 */
@Service
public class FamilyService {

    private static final Logger log = LoggerFactory.getLogger(FamilyService.class);

    public static final Set<String> DEFAULT_CATEGORIES = Set.of(
            "FOOD", "EDUCATION", "TRANSPORT", "ENTERTAINMENT", "BOOKSTORE"
    );

    private final FamilyStore familyStore;
    private final UserStore userStore;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public FamilyService(FamilyStore familyStore,
                         UserStore userStore,
                         AuditService auditService,
                         NotificationService notificationService) {
        this.familyStore = familyStore;
        this.userStore = userStore;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public FamilyMemberDto createMember(Long guardianUserId, CreateFamilyMemberRequest request, String clientIp) {
        if (guardianUserId == null) {
            throw new AuthorizationException("Guardian must be authenticated");
        }
        userStore.findUserById(guardianUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", guardianUserId));

        // Data minimisation: Clean first name, reject multi-token legal names
        String cleanFirstName = request.getFirstName().trim();
        if (cleanFirstName.contains(" ")) {
            cleanFirstName = cleanFirstName.split("\\s+")[0];
        }

        String initials = request.getAvatarInitials();
        if (initials == null || initials.isBlank()) {
            initials = cleanFirstName.substring(0, Math.min(2, cleanFirstName.length())).toUpperCase();
        } else {
            initials = initials.trim().toUpperCase();
        }

        long dailyLimit = (request.getDailyLimitMinor() != null && request.getDailyLimitMinor() > 0)
                ? request.getDailyLimitMinor()
                : 500_000L; // Default 5,000 LKR in minor units

        long monthlyLimit = (request.getMonthlyLimitMinor() != null && request.getMonthlyLimitMinor() > 0)
                ? request.getMonthlyLimitMinor()
                : 2_500_000L; // Default 25,000 LKR in minor units

        Set<String> categories = (request.getAllowedCategories() != null && !request.getAllowedCategories().isEmpty())
                ? request.getAllowedCategories().stream().map(String::toUpperCase).collect(Collectors.toSet())
                : new HashSet<>(DEFAULT_CATEGORIES);

        AllowanceSchedule schedule = AllowanceSchedule.disabled();
        if (request.getAllowanceSchedule() != null && request.getAllowanceSchedule().enabled()) {
            schedule = new AllowanceSchedule(
                    true,
                    request.getAllowanceSchedule().amountMinor() != null ? request.getAllowanceSchedule().amountMinor() : 0L,
                    request.getAllowanceSchedule().frequency(),
                    request.getAllowanceSchedule().nextRunDate(),
                    request.getAllowanceSchedule().sourceAccount()
            );
        }

        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();

        FamilyMember member = new FamilyMember(
                id,
                guardianUserId,
                cleanFirstName,
                request.getAgeBand(),
                initials,
                0L,
                dailyLimit,
                monthlyLimit,
                categories,
                false,
                schedule,
                now,
                now,
                FamilyMemberStatus.ACTIVE
        );

        familyStore.save(member);

        // Audit & Guardian Notification
        auditService.log(guardianUserId, "FAMILY_MEMBER_CREATED", "FAMILY_MEMBER", null,
                "{\"memberId\":\"" + id + "\",\"ageBand\":\"" + member.getAgeBand() + "\"}", clientIp);

        notifyGuardian(guardianUserId, "Family Member Created",
                "A family member profile was created. Set spending limits anytime.");

        return FamilyMemberDto.fromDomain(member, 0L, 0L);
    }

    public List<FamilyMemberDto> listMembers(Long guardianUserId) {
        if (guardianUserId == null) return Collections.emptyList();
        List<FamilyMember> members = familyStore.findByGuardianUserId(guardianUserId);
        return members.stream()
                .map(m -> {
                    long todaySpent = calculateSpentToday(m.getId());
                    long monthSpent = calculateSpentMonth(m.getId());
                    return FamilyMemberDto.fromDomain(m, todaySpent, monthSpent);
                })
                .collect(Collectors.toList());
    }

    public FamilyMemberDto getMember(Long guardianUserId, String memberId) {
        FamilyMember member = findMemberOrThrow(memberId);
        assertGuardianOwnership(guardianUserId, member);

        long todaySpent = calculateSpentToday(member.getId());
        long monthSpent = calculateSpentMonth(member.getId());
        return FamilyMemberDto.fromDomain(member, todaySpent, monthSpent);
    }

    public FamilyMemberDto updateControls(Long guardianUserId, String memberId,
                                          UpdateFamilyControlsRequest request, String clientIp) {
        FamilyMember member = findMemberOrThrow(memberId);
        assertGuardianOwnership(guardianUserId, member);

        if (request.getDailyLimitMinor() != null && request.getDailyLimitMinor() > 0) {
            member.setDailyLimitMinor(request.getDailyLimitMinor());
        }
        if (request.getMonthlyLimitMinor() != null && request.getMonthlyLimitMinor() > 0) {
            member.setMonthlyLimitMinor(request.getMonthlyLimitMinor());
        }
        if (request.getAllowedCategories() != null && !request.getAllowedCategories().isEmpty()) {
            Set<String> cleanCats = request.getAllowedCategories().stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            member.setAllowedCategories(cleanCats);
        }
        if (request.getIsFrozen() != null) {
            member.setFrozen(request.getIsFrozen());
        }
        if (request.getAllowanceSchedule() != null) {
            member.setAllowanceSchedule(new AllowanceSchedule(
                    request.getAllowanceSchedule().enabled(),
                    request.getAllowanceSchedule().amountMinor() != null ? request.getAllowanceSchedule().amountMinor() : 0L,
                    request.getAllowanceSchedule().frequency(),
                    request.getAllowanceSchedule().nextRunDate(),
                    request.getAllowanceSchedule().sourceAccount()
            ));
        }

        member.setUpdatedAt(Instant.now());
        familyStore.save(member);

        auditService.log(guardianUserId, "FAMILY_CONTROLS_UPDATED", "FAMILY_MEMBER", null,
                "{\"memberId\":\"" + memberId + "\"}", clientIp);

        notifyGuardian(guardianUserId, "Family Controls Updated",
                "Family member spending controls updated by guardian.");

        long todaySpent = calculateSpentToday(member.getId());
        long monthSpent = calculateSpentMonth(member.getId());
        return FamilyMemberDto.fromDomain(member, todaySpent, monthSpent);
    }

    public FamilyMemberDto setFreeze(Long guardianUserId, String memberId, boolean frozen, String clientIp) {
        FamilyMember member = findMemberOrThrow(memberId);
        assertGuardianOwnership(guardianUserId, member);

        member.setFrozen(frozen);
        member.setUpdatedAt(Instant.now());
        familyStore.save(member);

        auditService.log(guardianUserId, frozen ? "FAMILY_MEMBER_FROZEN" : "FAMILY_MEMBER_UNFROZEN",
                "FAMILY_MEMBER", null, "{\"memberId\":\"" + memberId + "\"}", clientIp);

        notifyGuardian(guardianUserId, frozen ? "Family Card Frozen" : "Family Card Unfrozen",
                frozen ? "Family member simulated account has been frozen." : "Family member simulated account has been unfrozen.");

        long todaySpent = calculateSpentToday(member.getId());
        long monthSpent = calculateSpentMonth(member.getId());
        return FamilyMemberDto.fromDomain(member, todaySpent, monthSpent);
    }

    public FamilyMemberDto fundMember(Long guardianUserId, String memberId,
                                      FundFamilyMemberRequest request, String clientIp) {
        FamilyMember member = findMemberOrThrow(memberId);
        assertGuardianOwnership(guardianUserId, member);

        if (request.getAmountMinor() == null || request.getAmountMinor() <= 0) {
            throw new ValidationException("Funding amount must be strictly positive");
        }

        long newBalance = member.getBalanceMinor() + request.getAmountMinor();
        member.setBalanceMinor(newBalance);
        Instant now = Instant.now();
        member.setUpdatedAt(now);
        familyStore.save(member);

        String source = request.getSourceAccount() != null ? request.getSourceAccount() : "Guardian Account";
        FamilyTransaction tx = new FamilyTransaction(
                UUID.randomUUID().toString(),
                member.getId(),
                guardianUserId,
                FamilyTransactionType.FUNDING,
                request.getAmountMinor(),
                newBalance,
                "ALLOWANCE",
                source,
                request.getNote(),
                FamilyTransactionStatus.APPROVED,
                null,
                now
        );
        familyStore.saveTransaction(tx);

        auditService.log(guardianUserId, "FAMILY_MEMBER_FUNDED", "FAMILY_MEMBER", null,
                "{\"memberId\":\"" + memberId + "\",\"amount\":" + request.getAmountMinor() + "}", clientIp);

        notifyGuardian(guardianUserId, "Family Balance Funded",
                "Simulated balance funded for family member.");

        long todaySpent = calculateSpentToday(member.getId());
        long monthSpent = calculateSpentMonth(member.getId());
        return FamilyMemberDto.fromDomain(member, todaySpent, monthSpent);
    }

    public FamilyTransactionDto spend(String memberId, SpendFamilyMemberRequest request, String clientIp) {
        FamilyMember member = findMemberOrThrow(memberId);

        if (request.getAmountMinor() == null || request.getAmountMinor() <= 0) {
            throw new ValidationException("Spend amount must be strictly positive");
        }

        Instant now = Instant.now();
        String category = request.getCategory() != null ? request.getCategory().trim().toUpperCase() : "GENERAL";

        // 1. Freeze check
        if (member.isFrozen()) {
            recordDeclinedSpend(member, request, category, "Account is frozen by guardian", clientIp);
            throw new ValidationException("Account is frozen by guardian");
        }

        // 2. Category check
        if (!member.getAllowedCategories().contains(category)) {
            recordDeclinedSpend(member, request, category, "Category not allowed by guardian", clientIp);
            throw new ValidationException("Category not allowed by guardian: " + category);
        }

        // 3. Daily limit check
        long todaySpent = calculateSpentToday(member.getId());
        if (todaySpent + request.getAmountMinor() > member.getDailyLimitMinor()) {
            recordDeclinedSpend(member, request, category, "Daily spending limit exceeded", clientIp);
            throw new ValidationException("Daily spending limit exceeded");
        }

        // 4. Monthly limit check
        long monthSpent = calculateSpentMonth(member.getId());
        if (monthSpent + request.getAmountMinor() > member.getMonthlyLimitMinor()) {
            recordDeclinedSpend(member, request, category, "Monthly spending limit exceeded", clientIp);
            throw new ValidationException("Monthly spending limit exceeded");
        }

        // 5. Balance check
        if (member.getBalanceMinor() < request.getAmountMinor()) {
            recordDeclinedSpend(member, request, category, "Insufficient simulated balance", clientIp);
            throw new ValidationException("Insufficient simulated balance");
        }

        // All checks passed -> Approve transaction
        long newBalance = member.getBalanceMinor() - request.getAmountMinor();
        member.setBalanceMinor(newBalance);
        member.setUpdatedAt(now);
        familyStore.save(member);

        FamilyTransaction tx = new FamilyTransaction(
                UUID.randomUUID().toString(),
                member.getId(),
                member.getGuardianUserId(),
                FamilyTransactionType.SPEND,
                request.getAmountMinor(),
                newBalance,
                category,
                request.getMerchantName(),
                request.getNote(),
                FamilyTransactionStatus.APPROVED,
                null,
                now
        );
        familyStore.saveTransaction(tx);

        auditService.log(member.getGuardianUserId(), "FAMILY_SPEND_APPROVED", "FAMILY_MEMBER", null,
                "{\"memberId\":\"" + member.getId() + "\",\"amount\":" + request.getAmountMinor() + ",\"category\":\"" + category + "\"}", clientIp);

        notifyGuardian(member.getGuardianUserId(), "Family Purchase Approved",
                "Family member transaction approved. Tap to view activity.");

        return FamilyTransactionDto.fromDomain(tx);
    }

    public void deleteMember(Long guardianUserId, String memberId, String clientIp) {
        FamilyMember member = findMemberOrThrow(memberId);
        assertGuardianOwnership(guardianUserId, member);

        // Data minimisation & retention enforcement:
        // Purging member profile and transactions
        familyStore.delete(memberId);

        auditService.log(guardianUserId, "FAMILY_MEMBER_DELETED", "FAMILY_MEMBER", null,
                "{\"memberId\":\"" + memberId + "\"}", clientIp);

        notifyGuardian(guardianUserId, "Family Member Deleted",
                "Family member profile removed according to data retention rules.");
    }

    public List<FamilyTransactionDto> getActivity(Long guardianUserId, String memberId) {
        FamilyMember member = findMemberOrThrow(memberId);
        assertGuardianOwnership(guardianUserId, member);

        return familyStore.findTransactionsByMemberId(memberId).stream()
                .map(FamilyTransactionDto::fromDomain)
                .collect(Collectors.toList());
    }

    public ChildViewDto getChildView(Long guardianUserId, String memberId) {
        FamilyMember member = findMemberOrThrow(memberId);
        if (guardianUserId != null) {
            assertGuardianOwnership(guardianUserId, member);
        }

        long todaySpent = calculateSpentToday(member.getId());
        long remainingDaily = Math.max(0L, member.getDailyLimitMinor() - todaySpent);

        List<FamilyTransactionDto> recent = familyStore.findTransactionsByMemberId(memberId).stream()
                .limit(5)
                .map(FamilyTransactionDto::fromDomain)
                .collect(Collectors.toList());

        return new ChildViewDto(
                member.getId(),
                member.getFirstName(),
                member.getAvatarInitials(),
                member.getBalanceMinor(),
                member.getDailyLimitMinor(),
                remainingDaily,
                member.getAllowedCategories(),
                member.isFrozen(),
                recent
        );
    }

    // ── Helper methods ─────────────────────────────────────────

    private FamilyMember findMemberOrThrow(String memberId) {
        return familyStore.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("FAMILY_MEMBER", memberId));
    }

    private void assertGuardianOwnership(Long guardianUserId, FamilyMember member) {
        if (guardianUserId == null || !guardianUserId.equals(member.getGuardianUserId())) {
            throw new AuthorizationException("Not authorized to access or modify this family member profile");
        }
    }

    private void recordDeclinedSpend(FamilyMember member, SpendFamilyMemberRequest request,
                                     String category, String reason, String clientIp) {
        FamilyTransaction declinedTx = new FamilyTransaction(
                UUID.randomUUID().toString(),
                member.getId(),
                member.getGuardianUserId(),
                FamilyTransactionType.SPEND,
                request.getAmountMinor(),
                member.getBalanceMinor(),
                category,
                request.getMerchantName(),
                request.getNote(),
                FamilyTransactionStatus.DECLINED,
                reason,
                Instant.now()
        );
        familyStore.saveTransaction(declinedTx);

        auditService.log(member.getGuardianUserId(), "FAMILY_SPEND_DECLINED", "FAMILY_MEMBER", null,
                "{\"memberId\":\"" + member.getId() + "\",\"amount\":" + request.getAmountMinor() + ",\"reason\":\"" + reason + "\"}", clientIp);
    }

    private long calculateSpentToday(String memberId) {
        LocalDate today = LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);
        Instant startOfDay = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        return calculateSpentSince(memberId, startOfDay);
    }

    private long calculateSpentMonth(String memberId) {
        LocalDate today = LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return calculateSpentSince(memberId, startOfMonth);
    }

    private long calculateSpentSince(String memberId, Instant since) {
        return familyStore.findTransactionsByMemberId(memberId).stream()
                .filter(t -> t.getType() == FamilyTransactionType.SPEND)
                .filter(t -> t.getStatus() == FamilyTransactionStatus.APPROVED)
                .filter(t -> t.getTimestamp().isAfter(since))
                .mapToLong(FamilyTransaction::getAmountMinor)
                .sum();
    }

    private void notifyGuardian(Long guardianUserId, String title, String message) {
        try {
            Optional<UserAccount> userOpt = userStore.findUserById(guardianUserId);
            if (userOpt.isPresent()) {
                notificationService.dispatch(userOpt.get().toUser(), NotificationType.GENERAL, title, message);
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch guardian notification for userId={}: {}", guardianUserId, e.getMessage());
        }
    }
}
