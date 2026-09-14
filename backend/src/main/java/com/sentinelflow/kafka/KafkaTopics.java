package com.sentinelflow.kafka;

public final class KafkaTopics {
    public static final String TRANSACTION_PROCESS = "sentinelflow.transactions.process";
    public static final String TRANSACTION_PROCESS_DLQ = "sentinelflow.transactions.process.dlq";
    public static final String TRANSACTION_PROCESS_RETRY = "sentinelflow.transactions.process.retry";

    private KafkaTopics() {}
}
