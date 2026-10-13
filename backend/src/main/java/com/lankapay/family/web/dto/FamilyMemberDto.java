package com.lankapay.family.web.dto;

import com.lankapay.family.domain.AgeBand;
import com.lankapay.family.domain.FamilyMember;
import com.lankapay.family.domain.FamilyMemberStatus;

import java.time.Instant;
import java.util.Set;

public record FamilyMemberDto(
        String id,
        Long guardianUserId,
        String firstName,
        AgeBand ageBand,
        String avatarInitials,
        long balanceMinor,
        long dailyLimitMinor,
        long monthlyLimitMinor,
        Set<String> allowedCategories,
        boolean isFrozen,
        AllowanceScheduleDto allowanceSchedule,
        long todaySpentMinor,
        long monthSpentMinor,
        Instant createdAt,
        Instant updatedAt,
        FamilyMemberStatus status
) {
    public static FamilyMemberDto fromDomain(FamilyMember member, long todaySpentMinor, long monthSpentMinor) {
        if (member == null) return null;
        AllowanceScheduleDto scheduleDto = null;
        if (member.getAllowanceSchedule() != null) {
            scheduleDto = new AllowanceScheduleDto(
                    member.getAllowanceSchedule().enabled(),
                    member.getAllowanceSchedule().amountMinor(),
                    member.getAllowanceSchedule().frequency(),
                    member.getAllowanceSchedule().nextRunDate(),
                    member.getAllowanceSchedule().sourceAccount()
            );
        }
        return new FamilyMemberDto(
                member.getId(),
                member.getGuardianUserId(),
                member.getFirstName(),
                member.getAgeBand(),
                member.getAvatarInitials(),
                member.getBalanceMinor(),
                member.getDailyLimitMinor(),
                member.getMonthlyLimitMinor(),
                member.getAllowedCategories(),
                member.isFrozen(),
                scheduleDto,
                todaySpentMinor,
                monthSpentMinor,
                member.getCreatedAt(),
                member.getUpdatedAt(),
                member.getStatus()
        );
    }
}
