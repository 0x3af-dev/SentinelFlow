package com.sentinelflow.operations;

/**
 * Outcome of a single dependency probe: a status plus a short human-usable
 * detail string. Read-only and informational.
 */
public record DependencyProbeResult(DependencyStatus status, String detail) {

    public static DependencyProbeResult healthy(String detail) {
        return new DependencyProbeResult(DependencyStatus.HEALTHY, detail);
    }

    public static DependencyProbeResult degraded(String detail) {
        return new DependencyProbeResult(DependencyStatus.DEGRADED, detail);
    }

    public static DependencyProbeResult unavailable(String detail) {
        return new DependencyProbeResult(DependencyStatus.UNAVAILABLE, detail);
    }

    public static DependencyProbeResult disabled(String detail) {
        return new DependencyProbeResult(DependencyStatus.DISABLED, detail);
    }
}