package com.fantacalcio.fantaschedina.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * MDC key and generator for the correlation id attached to every log line.
 * */
public final class CorrelationId {

    public static final String MDC_KEY = "reqId";

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static String generateJobId() {
        return "job-" + generate();
    }

    /**
     * Runs {@code action} with a fresh job correlation id in MDC, removing it afterwards.
     * The action may declare a checked exception E, which propagates to the caller.
     */
    public static <E extends Exception> void runAsJob(CheckedRunnable<E> action) throws E {
        MDC.put(MDC_KEY, generateJobId());
        try {
            action.run();
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @FunctionalInterface
    public interface CheckedRunnable<E extends Exception> {
        void run() throws E;
    }
}