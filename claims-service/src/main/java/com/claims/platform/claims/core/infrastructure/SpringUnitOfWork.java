package com.claims.platform.claims.core.infrastructure;

import com.claims.platform.claims.core.application.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringUnitOfWork implements UnitOfWork {
    private final TransactionTemplate transactions;

    public SpringUnitOfWork(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
