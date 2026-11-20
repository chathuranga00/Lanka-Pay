package com.lankapay.family.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Domain entity representing a simulated family member (child/minor profile).
 *
 * Data Minimisation Principles:
 * - NO independent phone number registration.
 * - NO discoverability in directory/registry lookups.
 * - NO photos or face biometrics; only simple initials for avatar representation.
 * - NO geolocation or location tracking.
 * - NO surname or full legal name; only a first name.
 * - Broad age band instead of exact date of birth.
 * - Only the verified guardian can fund the simulated balance.
 */
public class FamilyMember {

    private String id;
    private Long guardianUserId;
    private String firstName;
    private AgeBand ageBand;
    private String avatarInitials;
    private long balanceMinor;
    private long dailyLimitMinor;
    private long monthlyLimitMinor;
    private Set<String> allowedCategories;
    private boolean isFrozen;
    private AllowanceSchedule allowanceSchedule;
    private Instant createdAt;
    private Instant updatedAt;
    private FamilyMemberStatus status;

    public FamilyMember() {
        this.allowedCategories = new HashSet<>();
        this.status = FamilyMemberStatus.ACTIVE;
        this.allowanceSchedule = AllowanceSchedule.disabled();
    }

    public FamilyMember(String id, Long guardianUserId, String firstName, AgeBand ageBand,
                        String avatarInitials, long balanceMinor, long dailyLimitMinor,
                        long monthlyLimitMinor, Set<String> allowedCategories, boolean isFrozen,
                        AllowanceSchedule allowanceSchedule, Instant createdAt, Instant updatedAt,
                        FamilyMemberStatus status) {
        this.id = id;
        this.guardianUserId = guardianUserId;
        this.firstName = firstName;
        this.ageBand = ageBand;
        this.avatarInitials = avatarInitials;
        this.balanceMinor = balanceMinor;
        this.dailyLimitMinor = dailyLimitMinor;
        this.monthlyLimitMinor = monthlyLimitMinor;
        this.allowedCategories = allowedCategories != null ? new HashSet<>(allowedCategories) : new HashSet<>();
        this.isFrozen = isFrozen;
        this.allowanceSchedule = allowanceSchedule != null ? allowanceSchedule : AllowanceSchedule.disabled();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status != null ? status : FamilyMemberStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getGuardianUserId() {
        return guardianUserId;
    }

    public void setGuardianUserId(Long guardianUserId) {
        this.guardianUserId = guardianUserId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public AgeBand getAgeBand() {
        return ageBand;
    }

    public void setAgeBand(AgeBand ageBand) {
        this.ageBand = ageBand;
    }

    public String getAvatarInitials() {
        return avatarInitials;
    }

    public void setAvatarInitials(String avatarInitials) {
        this.avatarInitials = avatarInitials;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public void setBalanceMinor(long balanceMinor) {
        this.balanceMinor = balanceMinor;
    }

    public long getDailyLimitMinor() {
        return dailyLimitMinor;
    }

    public void setDailyLimitMinor(long dailyLimitMinor) {
        this.dailyLimitMinor = dailyLimitMinor;
    }

    public long getMonthlyLimitMinor() {
        return monthlyLimitMinor;
    }

    public void setMonthlyLimitMinor(long monthlyLimitMinor) {
        this.monthlyLimitMinor = monthlyLimitMinor;
    }

    public Set<String> getAllowedCategories() {
        return Collections.unmodifiableSet(allowedCategories);
    }

    public void setAllowedCategories(Set<String> allowedCategories) {
        this.allowedCategories = allowedCategories != null ? new HashSet<>(allowedCategories) : new HashSet<>();
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    public void setFrozen(boolean frozen) {
        isFrozen = frozen;
    }

    public AllowanceSchedule getAllowanceSchedule() {
        return allowanceSchedule;
    }

    public void setAllowanceSchedule(AllowanceSchedule allowanceSchedule) {
        this.allowanceSchedule = allowanceSchedule != null ? allowanceSchedule : AllowanceSchedule.disabled();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public FamilyMemberStatus getStatus() {
        return status;
    }

    public void setStatus(FamilyMemberStatus status) {
        this.status = status;
    }
}
