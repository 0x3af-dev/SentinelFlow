package com.sentinelflow.operations;

/**
 * Operational health vocabulary for dependency probes. Deliberately distinct
 * from Spring Boot's HealthStatus: these statuses describe a single dependency
 * capability and never drag the core application liveness/readiness down.
 */
public enum DependencyStatus {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
    DISABLED,
    UNKNOWN
}