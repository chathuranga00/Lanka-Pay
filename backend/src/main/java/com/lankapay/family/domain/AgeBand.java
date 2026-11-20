package com.lankapay.family.domain;

/**
 * Age brackets for family member minors.
 * Data minimisation: We do NOT collect exact date of birth, only the broad age band.
 */
public enum AgeBand {
    UNDER_10,
    AGE_10_TO_13,
    AGE_14_TO_17
}
