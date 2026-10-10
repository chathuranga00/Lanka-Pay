package com.lankapay.transfer.domain;

import java.util.Set;

/**
 * TransferStatus enum delegating to {@link TransactionStatus} and {@link TransactionStateMachine}.
 */
public enum TransferStatus {

    QUOTED(TransactionStatus.QUOTED),
    PENDING(TransactionStatus.PENDING),
    PROCESSING(TransactionStatus.PROCESSING),
    COMPLETED(TransactionStatus.COMPLETED),
    FAILED(TransactionStatus.FAILED),
    REQUIRES_RECONCILIATION(TransactionStatus.REQUIRES_RECONCILIATION),
    REVERSAL_PENDING(TransactionStatus.REVERSAL_PENDING),
    REVERSED(TransactionStatus.REVERSED);

    private final TransactionStatus canonical;

    TransferStatus(TransactionStatus canonical) {
        this.canonical = canonical;
    }

    public TransactionStatus toCanonical() {
        return canonical;
    }

    public static TransferStatus fromCanonical(TransactionStatus status) {
        if (status == null) return null;
        return TransferStatus.valueOf(status.name());
    }

    public TransferStatus transitionTo(TransferStatus target) {
        TransactionStateMachine.validateTransition(this.canonical, target.canonical);
        return target;
    }

    public boolean isTerminal() {
        return TransactionStateMachine.isTerminal(this.canonical);
    }
}
