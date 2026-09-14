package com.sentinelflow.observability;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * SentinelFlow structured-logging vocabulary. Every operational event carries a
 * consistent set of MDC fields (eventId, correlationId, transactionReference,
 * investigationId, operation, status, durationMs, errorCode) so logs from any
 * subsystem can be correlated and machine-read. The Logback pattern renders
 * these keys in {@code resources/logback-spring.xml}.
 *
 * <p>Nevers: do not put secrets, tokens, password-like values, raw payloads, or
 * high-volume business data into the MDC — these fields are intentionally
 * low-cardinality identifiers and short status strings only.
 */
public final class Md {

    public static final String EVENT_ID = "eventId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRANSACTION_REFERENCE = "transactionReference";
    public static final String INVESTIGATION_ID = "investigationId";
    public static final String OPERATION = "operation";
    public static final String STATUS = "status";
    public static final String DURATION_MS = "durationMs";
    public static final String ERROR_CODE = "errorCode";

    public static final String OP_PIPELINE = "transaction.pipeline";
    public static final String OP_ML = "ml.inference";
    public static final String OP_AI = "ai.investigation";
    public static final String OP_KAFKA_PROCESS = "kafka.process";
    public static final String OP_OUTBOX_PUBLISH = "outbox.publish";
    public static final String OP_ENQUEUE = "transaction.enqueue";
    public static final String OP_OPERATIONS = "operations.read";

    private Md() {}

    /** Runs {@code runnable} with the given MDC fields set, always clearing them afterwards. */
    public static void run(Map<String, String> fields, Runnable runnable) {
        fields.forEach(MDC::put);
        try {
            runnable.run();
        } finally {
            fields.keySet().forEach(MDC::remove);
        }
    }

    /** Runs {@code supplier} with the given MDC fields set, returning its result and always clearing the fields. */
    public static <T> T run(Map<String, String> fields, java.util.function.Supplier<T> supplier) {
        fields.forEach(MDC::put);
        try {
            return supplier.get();
        } finally {
            fields.keySet().forEach(MDC::remove);
        }
    }

    public static Map<String, String> of(String operation, String correlationId, String transactionReference) {
        return of(operation, correlationId, transactionReference, null, null);
    }

    public static Map<String, String> of(String operation, String correlationId, String transactionReference,
                                         String eventId, String investigationId) {
        Map<String, String> fields = new HashMap<>();
        fields.put(OPERATION, operation);
        put(correlationId, CORRELATION_ID, fields);
        put(transactionReference, TRANSACTION_REFERENCE, fields);
        put(eventId, EVENT_ID, fields);
        put(investigationId, INVESTIGATION_ID, fields);
        return fields;
    }

    private static void put(String value, String key, Map<String, String> fields) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }
}