package com.lankapay.common.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Helper component for executing atomic Google Cloud Firestore transactions.
 *
 * Encapsulates the asynchronous {@link ApiFuture} mechanics of {@link Firestore#runTransaction(Transaction.Function)},
 * unwrapping checked {@link ExecutionException} instances and propagating clean runtime exceptions.
 */
@Component
public class FirestoreTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(FirestoreTransactionHelper.class);

    private final Firestore firestore;

    public FirestoreTransactionHelper(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Executes the given transaction callback synchronously within an atomic Firestore transaction.
     *
     * @param updateFunction callback receiving {@link Transaction}
     * @param <T>            the return type of the transaction
     * @return the result returned by {@code updateFunction}
     */
    public <T> T runTransaction(Transaction.Function<T> updateFunction) {
        try {
            ApiFuture<T> future = firestore.runTransaction(updateFunction);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore transaction interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Firestore transaction failed: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        }
    }

    /**
     * Overload for running a void transaction callback.
     *
     * @param consumer consumer callback performing writes within {@link Transaction}
     */
    public void runTransactionVoid(VoidTransactionConsumer consumer) {
        runTransaction(transaction -> {
            consumer.accept(transaction);
            return null;
        });
    }

    @FunctionalInterface
    public interface VoidTransactionConsumer {
        void accept(Transaction transaction) throws Exception;
    }
}
