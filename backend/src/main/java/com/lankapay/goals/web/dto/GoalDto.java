package com.lankapay.goals.web.dto;

import com.lankapay.goals.domain.GoalModel;
import com.lankapay.goals.domain.GoalStatus;

import java.time.Instant;
import java.time.LocalDate;

public record GoalDto(
        String id,
        String name,
        long targetMinor,
        long savedMinor,
        LocalDate deadline,
        String sourceAccount,
        GoalStatus status,
        String ledgerType,
        double progressPercentage,
        Instant createdAt,
        Instant updatedAt
) {
    public static GoalDto fromDomain(GoalModel model) {
        double progress = 0.0;
        if (model.targetMinor() > 0) {
            progress = Math.min(100.0, ((double) model.savedMinor() / model.targetMinor()) * 100.0);
        }
        return new GoalDto(
                model.id(),
                model.name(),
                model.targetMinor(),
                model.savedMinor(),
                model.deadline(),
                model.sourceAccount(),
                model.status(),
                model.ledgerType(),
                progress,
                model.createdAt(),
                model.updatedAt()
        );
    }
}
