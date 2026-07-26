package com.branz.mmorpg.api.runtime;

/**
 * Transaction seam, so core game rules can demand atomicity without importing
 * JDBC.
 *
 * <p>Contract (DEVELOPMENT_OWNERSHIP_AND_CONTRACTS §8): the service performing
 * the mutation owns the transaction, and no transaction stays open while
 * waiting for player input or for the scheduler. Work handed here is expected
 * to be short and to touch storage only.
 */
public interface TransactionRunner {

    /**
     * Runs {@code work} in one transaction, committing on normal return and
     * rolling back on any throwable.
     */
    <T> T inTransaction(TransactionalWork<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(context -> {
            work.run();
            return null;
        });
    }

    @FunctionalInterface
    interface TransactionalWork<T> {
        T apply(TransactionContext context) throws Exception;
    }

    /**
     * Opaque handle to the active transaction. Storage-layer code unwraps it to
     * the concrete type it provided; core code passes it along untouched.
     */
    interface TransactionContext {

        /**
         * @throws com.branz.mmorpg.api.error.MMOException if this context is not
         *         backed by {@code type}
         */
        <T> T unwrap(Class<T> type);
    }
}
