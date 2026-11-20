package com.lankapay.common.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirestoreTransactionHelperTest {

    @Mock
    private Firestore firestore;

    @Mock
    private ApiFuture<String> futureString;

    @Mock
    private ApiFuture<Void> futureVoid;

    private FirestoreTransactionHelper transactionHelper;

    @BeforeEach
    void setUp() {
        transactionHelper = new FirestoreTransactionHelper(firestore);
    }

    @Test
    @DisplayName("runTransaction executes and returns value from future")
    void runTransaction_success() throws Exception {
        when(firestore.runTransaction(any())).thenReturn((ApiFuture) futureString);
        when(futureString.get()).thenReturn("TRANSACTION_COMMITTED");

        String result = transactionHelper.runTransaction(tx -> "TRANSACTION_COMMITTED");

        assertThat(result).isEqualTo("TRANSACTION_COMMITTED");
        verify(firestore).runTransaction(any());
    }

    @Test
    @DisplayName("runTransaction unwraps RuntimeException from ExecutionException")
    void runTransaction_unwrapsRuntimeException() throws Exception {
        when(firestore.runTransaction(any())).thenReturn((ApiFuture) futureString);
        IllegalStateException domainException = new IllegalStateException("Insufficient funds");
        when(futureString.get()).thenThrow(new ExecutionException(domainException));

        assertThatThrownBy(() -> transactionHelper.runTransaction(tx -> "FAIL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Insufficient funds");
    }

    @Test
    @DisplayName("runTransaction unwraps generic checked exception to RuntimeException")
    void runTransaction_unwrapsCheckedException() throws Exception {
        when(firestore.runTransaction(any())).thenReturn((ApiFuture) futureString);
        Exception checked = new Exception("Low-level gRPC error");
        when(futureString.get()).thenThrow(new ExecutionException(checked));

        assertThatThrownBy(() -> transactionHelper.runTransaction(tx -> "FAIL"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Firestore transaction failed: Low-level gRPC error");
    }

    @Test
    @DisplayName("runTransactionVoid executes consumer block")
    void runTransactionVoid_success() throws Exception {
        when(firestore.runTransaction(any())).thenReturn((ApiFuture) futureVoid);
        when(futureVoid.get()).thenReturn(null);

        AtomicBoolean executed = new AtomicBoolean(false);
        transactionHelper.runTransactionVoid(tx -> executed.set(true));

        verify(firestore).runTransaction(any());
    }
}
