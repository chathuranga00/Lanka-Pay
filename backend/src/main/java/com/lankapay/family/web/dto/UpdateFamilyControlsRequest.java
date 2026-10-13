package com.lankapay.family.web.dto;

import java.util.Set;

public class UpdateFamilyControlsRequest {

    private Long dailyLimitMinor;
    private Long monthlyLimitMinor;
    private Set<String> allowedCategories;
    private Boolean isFrozen;
    private AllowanceScheduleDto allowanceSchedule;

    public UpdateFamilyControlsRequest() {}

    public UpdateFamilyControlsRequest(Long dailyLimitMinor, Long monthlyLimitMinor,
                                       Set<String> allowedCategories, Boolean isFrozen,
                                       AllowanceScheduleDto allowanceSchedule) {
        this.dailyLimitMinor = dailyLimitMinor;
        this.monthlyLimitMinor = monthlyLimitMinor;
        this.allowedCategories = allowedCategories;
        this.isFrozen = isFrozen;
        this.allowanceSchedule = allowanceSchedule;
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

    public Boolean getIsFrozen() {
        return isFrozen;
    }

    public void setIsFrozen(Boolean isFrozen) {
        this.isFrozen = isFrozen;
    }

    public AllowanceScheduleDto getAllowanceSchedule() {
        return allowanceSchedule;
    }

    public void setAllowanceSchedule(AllowanceScheduleDto allowanceSchedule) {
        this.allowanceSchedule = allowanceSchedule;
    }
}
