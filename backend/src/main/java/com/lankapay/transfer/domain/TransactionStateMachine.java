package com.lankapay.transfer.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Explicit state machine for transfers per section 9.
 *
 * Legal Transitions:
 *   QUOTED                  ──→ PENDING
 *   PENDING                 ──→ PROCESSING
 *   PROCESSING              ──→ COMPLETED, FAILED, REQUIRES_RECONCILIATION
 *   REQUIRES_RECONCILIATION ──→ COMPLETED, FAILED
 *   COMPLETED               ──→ REVERSAL_PENDING
 *   REVERSAL_PENDING        ──→ REVERSED
 *   FAILED                  ──→ (terminal, no outgoing transitions)
 *   REVERSED                ──→ (terminal, no outgoing transitions)
 *
 * Invariants:
 * - A timeout moves the transaction to REQUIRES_RECONCILIATION, NEVER to FAILED.
 * - Receipts and responses must NEVER say SUCCESS until the BankAdapter confirms.
 * - Every transition is append-only audited via TransactionEvent.
 */
public final class TransactionStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> TRANSITIONS;

    static {
        Map<TransactionStatus, Set<TransactionStatus>> map = new EnumMap<>(TransactionStatus.class);

        map.put(TransactionStatus.QUOTED,
                EnumSet.of(TransactionStatus.PENDING));

        map.put(TransactionStatus.PENDING,
                EnumSet.of(TransactionStatus.PROCESSING,
                           TransactionStatus.FAILED));

        map.put(TransactionStatus.PROCESSING,
                EnumSet.of(TransactionStatus.COMPLETED,
                           TransactionStatus.FAILED,
                           TransactionStatus.REQUIRES_RECONCILIATION));

        map.put(TransactionStatus.REQUIRES_RECONCILIATION,
                EnumSet.of(TransactionStatus.COMPLETED,
                           TransactionStatus.FAILED));

        map.put(TransactionStatus.COMPLETED,
                EnumSet.of(TransactionStatus.REVERSAL_PENDING));

        map.put(TransactionStatus.REVERSAL_PENDING,
                EnumSet.of(TransactionStatus.REVERSED));

        map.put(TransactionStatus.FAILED,
                EnumSet.noneOf(TransactionStatus.class));

        map.put(TransactionStatus.REVERSED,
                EnumSet.noneOf(TransactionStatus.class));

        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private TransactionStateMachine() {}

    /**
     * Checks whether moving from {@code from} to {@code to} is an allowed transition.
     */
    public static boolean isValidTransition(TransactionStatus from, TransactionStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    /**
     * Validates transition from {@code from} to {@code to}.
     *
     * @throws IllegalStateException if the transition is illegal
     */
    public static void validateTransition(TransactionStatus from, TransactionStatus to) {
        if (!isValidTransition(from, to)) {
            Set<TransactionStatus> allowed = legalNextStates(from);
            throw new IllegalStateException(
                    "Illegal transfer state transition: %s -> %s. Allowed transitions from %s: %s"
                            .formatted(from, to, from, allowed.isEmpty() ? "NONE (terminal state)" : allowed)
            );
        }
    }

    /**
     * Returns the set of legal next states from the given status.
     */
    public static Set<TransactionStatus> legalNextStates(TransactionStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return TRANSITIONS.getOrDefault(from, Collections.emptySet());
    }

    /**
     * Returns true if the status has no outgoing transitions (terminal state).
     */
    public static boolean isTerminal(TransactionStatus status) {
        return legalNextStates(status).isEmpty();
    }
}
