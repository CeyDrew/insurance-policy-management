package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.BatchProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fault injection for the restart demonstration: when set, the result import throws as it reaches the given
 * detail line, as if the process had been killed. Off unless {@code app.batch.collection.fail-at-line} is set;
 * tests switch it on and off.
 */
@Component
public class CollectionFaultInjector {

    private final AtomicLong failAtLine;

    CollectionFaultInjector(BatchProperties properties) {
        this.failAtLine = new AtomicLong(properties.collection().failAtLine());
    }

    public void failAtLine(long lineNo) {
        failAtLine.set(lineNo);
    }

    public void off() {
        failAtLine.set(0);
    }

    void check(long lineNo) {
        if (lineNo == failAtLine.get()) {
            throw new SimulatedCrashException("Simulated crash at detail line " + lineNo);
        }
    }

    /** Not retried, not skipped: fails the step like a crash would. */
    static class SimulatedCrashException extends RuntimeException {
        SimulatedCrashException(String message) {
            super(message);
        }
    }
}
