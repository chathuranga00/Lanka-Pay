package com.lankapay.rewards.domain;

/**
 * Types of immutable ledger entries in the rewards points system.
 */
public enum PointsEntryType {
    EARN,
    REDEEM,
    EXPIRE,
    REVERSE,
    ADJUST
}
