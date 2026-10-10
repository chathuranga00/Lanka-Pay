-- ============================================================
-- Lanka Pay – V6
-- Persistent abuse counter for payment identity lookups.
-- Tracks per-user daily lookup counts; in-memory Bucket4j
-- handles per-minute rate limits without a DB round-trip.
-- ============================================================

CREATE TABLE reg_lookup_abuse_counters (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    actor_id      BIGINT      NOT NULL  COMMENT 'User performing the lookups',
    counter_date  DATE        NOT NULL  COMMENT 'Calendar date (UTC)',
    lookup_count  INT         NOT NULL DEFAULT 0,
    flagged       TINYINT(1)  NOT NULL DEFAULT 0
        COMMENT '1 when daily count exceeded abuse threshold',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reg_lookup_abuse_actor_date (actor_id, counter_date),
    INDEX         idx_reg_lookup_abuse_actor (actor_id),
    CONSTRAINT    fk_reg_lookup_abuse_actor FOREIGN KEY (actor_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
