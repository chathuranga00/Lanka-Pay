package com.lankapay.registry.domain;

/**
 * All possible outcomes of a payment identity lookup.
 *
 * Using typed results (not just 404 vs 200) lets the caller distinguish
 * between "phone not in system" and "phone in system but private", which
 * matters for UX — but both must return the SAME HTTP status (404) to
 * prevent phone enumeration via HTTP status codes.
 *
 * Anti-enumeration rule: FOUND returns 200; all other states return 404.
 * Never reveal WHETHER a phone is registered to an unauthenticated observer.
 */
public enum LookupResultType {

    /** Phone is registered, discoverable, and has at least one eligible account. */
    FOUND,

    /** Phone is not found in the payment registry (or never verified). */
    NOT_FOUND,

    /** Phone is registered but the user has disabled discovery. */
    DISCOVERY_DISABLED,

    /** Phone is registered and discoverable, but has no ACTIVE+receivingEnabled accounts. */
    NO_ELIGIBLE_ACCOUNT,

    /**
     * Phone is registered but privacy restrictions prevent returning account details.
     * Used when the registry entry exists but all accounts have discoverable=false.
     */
    PRIVACY_RESTRICTED
}
