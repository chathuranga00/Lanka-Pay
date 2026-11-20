package com.lankapay.registry.domain;

import java.time.LocalDate;

/**
 * Pure domain model representing daily lookup abuse counters.
 * Contains no JPA or database-specific annotations.
 */
public record AbuseCounterRecord(
        Long id,
        Long actorId,
        LocalDate counterDate,
        int lookupCount,
        boolean flagged
) {
    public AbuseCounterRecord(Long actorId, LocalDate counterDate) {
        this(null, actorId, counterDate, 0, false);
    }

    public AbuseCounterRecord increment() {
        return new AbuseCounterRecord(id, actorId, counterDate, lookupCount + 1, flagged);
    }

    public AbuseCounterRecord flag() {
        return new AbuseCounterRecord(id, actorId, counterDate, lookupCount, true);
    }
}
