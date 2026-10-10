package com.lankapay.transfer.domain;

/**
 * Transfer lifecycle states per section 9.
 *
 * All state transitions must go through {@link TransactionStateMachine}.
 */
public enum TransactionStatus {

    /** Quote created, not yet submitted. */
    QUOTED,

    /** Transfer accepted, pre-flight checks passed. */
    PENDING,

    /** Sent to BankAdapter, awaiting response. */
    PROCESSING,

    /** BankAdapter confirmed debit AND credit. */
    COMPLETED,

    /** BankAdapter confirmed permanent failure. */
    FAILED,

    /** BankAdapter timed out — MUST be reconciled, NEVER assume failed. */
    REQUIRES_RECONCILIATION,

    /** Reversal has been requested. */
    REVERSAL_PENDING,

    /** Reversal confirmed by BankAdapter. */
    REVERSED;

    /**
     * Check if a transition to the target status is allowed.
     */
    public boolean canTransitionTo(TransactionStatus target) {
        return TransactionStateMachine.isValidTransition(this, target);
    }

    /**
     * Transition to the target status, enforcing the state machine.
     *
     * @throws IllegalStateException if the transition is illegal
     */
    public TransactionStatus transitionTo(TransactionStatus target) {
        TransactionStateMachine.validateTransition(this, target);
        return target;
    }

    /** True if this status has no outgoing transitions. */
    public boolean isTerminal() {
        return TransactionStateMachine.isTerminal(this);
    }
}
