package com.portfolio.insurance.common.plsql;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs PL/SQL next to JPA. Pending changes are flushed first so the procedure sees them (JDBC calls do not trigger
 * Hibernate's auto-flush). Procedures that change rows behind Hibernate's back ({@link #run}, {@link #apply}) also
 * clear the persistence context afterwards so no stale entity is returned. Must be called inside a transaction.
 */
@Component
@RequiredArgsConstructor
public class DatabaseSideChanges {

    private final EntityManager entityManager;

    /** For PL/SQL that only reads: flushes pending changes, keeps the persistence context. */
    public <T> T read(Supplier<T> operation) {
        entityManager.flush();
        return operation.get();
    }

    public void run(Runnable operation) {
        apply(() -> {
            operation.run();
            return null;
        });
    }

    public <T> T apply(Supplier<T> operation) {
        entityManager.flush();
        T result = operation.get();
        entityManager.clear();
        return result;
    }
}
