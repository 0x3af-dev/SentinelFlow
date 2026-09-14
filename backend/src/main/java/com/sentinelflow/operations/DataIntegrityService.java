package com.sentinelflow.operations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only referential integrity checks over the write-side aggregates.
 * Purely diagnostic: every check is a SELECT of orphaned rows, never a repair.
 * Immutable FK constraints make several checks structurally impossible to
 * violate, but they are kept as guards against application-level writes that
 * bypass the aggregates (e.g. background rebuilders) or bugs in cascade repair
 * logic.
 */
@Component
public class DataIntegrityService {

    private record CheckSpec(String id, String description, String sql) {
    }

    private static final List<CheckSpec> CHECKS = List.of(
            new CheckSpec(
                    "orphaned_evidence_edges",
                    "evidence edges referencing missing nodes",
                    """
                    SELECT count(*) FROM evidence_edges e
                    LEFT JOIN evidence_nodes s ON e.source_node_id = s.id
                    LEFT JOIN evidence_nodes t ON e.target_node_id = t.id
                    WHERE s.id IS NULL OR t.id IS NULL
                    """),
            new CheckSpec(
                    "decision_without_risk_score",
                    "decision records referencing missing risk scores",
                    """
                    SELECT count(*) FROM decision_records d
                    LEFT JOIN risk_scores r ON d.risk_score_id = r.id
                    WHERE d.risk_score_id IS NOT NULL AND r.id IS NULL
                    """),
            new CheckSpec(
                    "risk_score_without_feature_snapshot",
                    "risk scores with no feature snapshot for the transaction",
                    """
                    SELECT count(*) FROM risk_scores rs
                    WHERE NOT EXISTS (
                        SELECT 1 FROM feature_snapshots fs
                        WHERE fs.transaction_id = rs.transaction_id
                    )
                    """),
            new CheckSpec(
                    "orphaned_feature_snapshots",
                    "feature snapshots referencing missing transactions",
                    """
                    SELECT count(*) FROM feature_snapshots fs
                    LEFT JOIN transactions t ON fs.transaction_id = t.id
                    WHERE t.id IS NULL
                    """),
            new CheckSpec(
                    "orphaned_risk_factors",
                    "risk factors referencing missing transactions",
                    """
                    SELECT count(*) FROM risk_factors rf
                    LEFT JOIN transactions t ON rf.transaction_id = t.id
                    WHERE t.id IS NULL
                    """),
            new CheckSpec(
                    "investigation_without_transaction",
                    "investigations referencing missing transactions",
                    """
                    SELECT count(*) FROM investigations i
                    LEFT JOIN transactions t ON i.transaction_id = t.id
                    WHERE t.id IS NULL
                    """));

    private final JdbcTemplate jdbcTemplate;

    public DataIntegrityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IntegrityCheck> checks() {
        List<IntegrityCheck> results = new ArrayList<>(CHECKS.size());
        for (CheckSpec spec : CHECKS) {
            long issues = Long.parseLong(
                    String.valueOf(jdbcTemplate.queryForObject(spec.sql(), Object.class)));
            results.add(new IntegrityCheck(spec.id(), spec.description(), issues, issues == 0));
        }
        return results;
    }

    public record IntegrityCheck(String id, String description, long issueCount, boolean healthy) {
    }

    public record IntegrityResponse(Instant generatedAt, List<IntegrityCheck> checks, boolean healthyAll) {
    }

    public IntegrityResponse report() {
        List<IntegrityCheck> checks = checks();
        boolean healthyAll = checks.stream().allMatch(IntegrityCheck::healthy);
        return new IntegrityResponse(Instant.now(), checks, healthyAll);
    }
}