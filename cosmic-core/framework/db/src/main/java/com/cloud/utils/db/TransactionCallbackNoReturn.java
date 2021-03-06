package com.cloud.utils.db;

public abstract class TransactionCallbackNoReturn implements TransactionCallback<Object> {

    @Override
    public final Object doInTransaction(final TransactionStatus status) {
        doInTransactionWithoutResult(status);
        return null;
    }

    public abstract void doInTransactionWithoutResult(TransactionStatus status);
}
