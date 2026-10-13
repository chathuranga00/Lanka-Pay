package com.lankapay.family.web.dto;

import com.lankapay.family.domain.AgeBand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class CreateFamilyMemberRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 30, message = "First name must be between 1 and 30 characters")
    @Pattern(regexp = "^[A-Za-z\\s'-]+$", message = "First name must contain only letters, spaces, hyphens or apostrophes")
    private String firstName;

    @NotNull(message = "Age band is required")
    private AgeBand ageBand;

    @Size(max = 3, message = "Avatar initials must be at most 3 characters")
    private String avatarInitials;

    private Long dailyLimitMinor;

    private Long monthlyLimitMinor;

    private Set<String> allowedCategories;

    private AllowanceScheduleDto allowanceSchedule;

    public CreateFamilyMemberRequest() {}

    public CreateFamilyMemberRequest(String firstName, AgeBand ageBand, String avatarInitials,
                                     Long dailyLimitMinor, Long monthlyLimitMinor,
                                     Set<String> allowedCategories,
                                     AllowanceScheduleDto allowanceSchedule) {
        this.firstName = firstName;
        this.ageBand = ageBand;
        this.avatarInitials = avatarInitials;
        this.dailyLimitMinor = dailyLimitMinor;
        this.monthlyLimitMinor = monthlyLimitMinor;
        this.allowedCategories = allowedCategories;
        this.allowanceSchedule = allowanceSchedule;
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

    public Long getDailyLimitMinor() {
        return dailyLimitMinor;
    }

    public void setDailyLimitMinor(Long dailyLimitMinor) {
        this.dailyLimitMinor = dailyLimitMinor;
    }

    public Long getMonthlyLimitMinor() {
        return monthlyLimitMinor;
    }

    public void setMonthlyLimitMinor(Long monthlyLimitMinor) {
        this.monthlyLimitMinor = monthlyLimitMinor;
    }

    public Set<String> getAllowedCategories() {
        return allowedCategories;
    }

    public void setAllowedCategories(Set<String> allowedCategories) {
        this.allowedCategories = allowedCategories;
    }

    public AllowanceScheduleDto getAllowanceSchedule() {
        return allowanceSchedule;
    }

    public void setAllowanceSchedule(AllowanceScheduleDto allowanceSchedule) {
        this.allowanceSchedule = allowanceSchedule;
    }
}
