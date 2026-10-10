package com.lankapay.registry.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * JPA entity for reg_lookup_abuse_counters.
 * Tracks per-user daily lookup volume; used alongside Bucket4j
 * per-minute buckets to detect sustained enumeration attempts.
 */
@Entity
@Table(name = "reg_lookup_abuse_counters",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_reg_lookup_abuse_actor_date",
               columnNames = {"actor_id", "counter_date"}))
public class LookupAbuseCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "counter_date", nullable = false)
    private LocalDate counterDate;

    @Column(name = "lookup_count", nullable = false)
    private int lookupCount = 0;

    /** Set to true once lookupCount exceeds the configured daily abuse threshold. */
    @Column(nullable = false)
    private boolean flagged = false;

    protected LookupAbuseCounter() {}

    public LookupAbuseCounter(Long actorId, LocalDate counterDate) {
        this.actorId     = actorId;
        this.counterDate = counterDate;
    }

    public Long      getId()           { return id; }
    public Long      getActorId()      { return actorId; }
    public LocalDate getCounterDate()  { return counterDate; }
    public int       getLookupCount()  { return lookupCount; }
    public boolean   isFlagged()       { return flagged; }

    public int increment() { return ++this.lookupCount; }
    public void flag()     { this.flagged = true; }
}
