package com.lankapay.transfer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStateMachineTest {

    @Test
    @DisplayName("Legal transitions flow: QUOTED -> PENDING -> PROCESSING -> COMPLETED")
    void testNormalHappyPath() {
        TransactionStatus status = TransactionStatus.QUOTED;
        status = status.transitionTo(TransactionStatus.PENDING);
        assertThat(status).isEqualTo(TransactionStatus.PENDING);

        status = status.transitionTo(TransactionStatus.PROCESSING);
        assertThat(status).isEqualTo(TransactionStatus.PROCESSING);

        status = status.transitionTo(TransactionStatus.COMPLETED);
        assertThat(status).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Timeout moves to REQUIRES_RECONCILIATION, never directly to FAILED")
    void testTimeoutTransition() {
        TransactionStatus status = TransactionStatus.PROCESSING;
        status = status.transitionTo(TransactionStatus.REQUIRES_RECONCILIATION);
        assertThat(status).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);

        // Can then resolve to COMPLETED or FAILED via ReconciliationService
        assertThat(status.canTransitionTo(TransactionStatus.COMPLETED)).isTrue();
        assertThat(status.canTransitionTo(TransactionStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("Reconciliation resolves to COMPLETED")
    void testReconciliationToCompleted() {
        TransactionStatus status = TransactionStatus.REQUIRES_RECONCILIATION;
        status = status.transitionTo(TransactionStatus.COMPLETED);
        assertThat(status).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Reconciliation resolves to FAILED")
    void testReconciliationToFailed() {
        TransactionStatus status = TransactionStatus.REQUIRES_RECONCILIATION;
        status = status.transitionTo(TransactionStatus.FAILED);
        assertThat(status).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("Reversal flow: COMPLETED -> REVERSAL_PENDING -> REVERSED")
    void testReversalFlow() {
        TransactionStatus status = TransactionStatus.COMPLETED;
        status = status.transitionTo(TransactionStatus.REVERSAL_PENDING);
        assertThat(status).isEqualTo(TransactionStatus.REVERSAL_PENDING);

        status = status.transitionTo(TransactionStatus.REVERSED);
        assertThat(status).isEqualTo(TransactionStatus.REVERSED);
        assertThat(status.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "FAILED, PENDING",
            "FAILED, COMPLETED",
            "REVERSED, COMPLETED",
            "REVERSED, PENDING",
            "PENDING, COMPLETED",
            "PENDING, REVERSED",
            "PROCESSING, REVERSED",
            "COMPLETED, FAILED",
            "COMPLETED, COMPLETED",
            "QUOTED, COMPLETED"
    })
    @DisplayName("Illegal transitions are strictly rejected")
    void testIllegalTransitions(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> from.transitionTo(to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal transfer state transition");
    }

    @Test
    @DisplayName("Terminal states have no outgoing transitions")
    void testTerminalStates() {
        assertThat(TransactionStateMachine.isTerminal(TransactionStatus.FAILED)).isTrue();
        assertThat(TransactionStateMachine.isTerminal(TransactionStatus.REVERSED)).isTrue();
        assertThat(TransactionStateMachine.isTerminal(TransactionStatus.COMPLETED)).isFalse();
        assertThat(TransactionStateMachine.isTerminal(TransactionStatus.PROCESSING)).isFalse();
    }
}
