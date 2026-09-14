package com.sentinelflow.transaction;

public enum TransactionStatus {
    RECEIVED,
    ENRICHING,
    SCORING,
    DECIDED,
    COMPLETED,
    FAILED
}
