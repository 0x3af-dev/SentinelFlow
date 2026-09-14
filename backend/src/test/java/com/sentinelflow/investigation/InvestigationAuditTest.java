package com.sentinelflow.investigation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantRepository;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class InvestigationAuditTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    UserRepository users;

    @Autowired
    MerchantRepository merchants;

    @Autowired
    TransactionRepository transactions;

    @Autowired
    InvestigationRepository investigations;

    @Autowired
    InvestigationEventRepository events;

    @Autowired
    AuditLogRepository auditLogs;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-INV-" + System.nanoTime(), "Investigation User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-INV-" + System.nanoTime(), "Inv Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        transaction = transactions.save(new Transaction("TXN-INV-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("1200.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-01T10:00:00Z"), TransactionStatus.DECIDED));
    }

    @Test
    void investigationReferencesTransaction() {
        Investigation inv = investigations.saveAndFlush(new Investigation(
                "INV-" + System.nanoTime(), transaction, InvestigationStatus.OPEN,
                InvestigationPriority.HIGH, "analyst-1", Instant.parse("2026-09-01T11:00:00Z")));

        assertThat(inv.getId()).isNotNull();
        assertThat(investigations.findByTransactionId(transaction.getId())).hasSize(1);
    }

    @Test
    void statusChangesRepresented() {
        Investigation inv = investigations.saveAndFlush(new Investigation(
                "INV-STAT-" + System.nanoTime(), transaction, InvestigationStatus.OPEN,
                InvestigationPriority.MEDIUM, "analyst-1", Instant.now()));

        inv.setStatus(InvestigationStatus.INVESTIGATING);
        investigations.saveAndFlush(inv);

        inv.setStatus(InvestigationStatus.RESOLVED);
        inv.setResolution(InvestigationResolution.FALSE_POSITIVE);
        inv.setResolutionNotes("Legitimate user behavior confirmed");
        investigations.saveAndFlush(inv);

        Investigation reloaded = investigations.findById(inv.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvestigationStatus.RESOLVED);
        assertThat(reloaded.getResolution()).isEqualTo(InvestigationResolution.FALSE_POSITIVE);
        assertThat(reloaded.getResolutionNotes()).isEqualTo("Legitimate user behavior confirmed");
    }

    @Test
    void resolutionSeparateFromStatus() {
        // OPEN with no resolution (valid)
        Investigation inv1 = investigations.save(new Investigation(
                "INV-RES1-" + System.nanoTime(), transaction, InvestigationStatus.OPEN,
                InvestigationPriority.LOW, "analyst-2", Instant.now()));

        // RESOLVED with FALSE_POSITIVE
        Investigation inv2 = investigations.saveAndFlush(new Investigation(
                "INV-RES2-" + System.nanoTime(), transaction, InvestigationStatus.RESOLVED,
                InvestigationPriority.MEDIUM, "analyst-2", Instant.now()));
        inv2.setResolution(InvestigationResolution.FALSE_POSITIVE);
        investigations.saveAndFlush(inv2);

        // RESOLVED with CONFIRMED_FRAUD
        Investigation inv3 = investigations.saveAndFlush(new Investigation(
                "INV-RES3-" + System.nanoTime(), transaction, InvestigationStatus.RESOLVED,
                InvestigationPriority.HIGH, "analyst-3", Instant.now()));
        inv3.setResolution(InvestigationResolution.CONFIRMED_FRAUD);
        investigations.saveAndFlush(inv3);

        assertThat(investigations.findByStatus(InvestigationStatus.OPEN)).hasSize(1);
        assertThat(investigations.findByStatus(InvestigationStatus.RESOLVED)).hasSize(2);
        assertThat(investigations.findByResolution(InvestigationResolution.FALSE_POSITIVE)).hasSize(1);
        assertThat(investigations.findByResolution(InvestigationResolution.CONFIRMED_FRAUD)).hasSize(1);
    }

    @Test
    void investigationEventsPersistAndHistoryRemains() {
        Investigation inv = investigations.saveAndFlush(new Investigation(
                "INV-EVT-" + System.nanoTime(), transaction, InvestigationStatus.OPEN,
                InvestigationPriority.HIGH, "analyst-1", Instant.now()));

        events.saveAndFlush(new InvestigationEvent(inv, "INVESTIGATION_CREATED", "SYSTEM", "analyst-1",
                Instant.parse("2026-09-01T11:00:00Z"), Map.of("note", "Auto-opened from BLOCK decision")));

        events.saveAndFlush(new InvestigationEvent(inv, "EVIDENCE_ADDED", "ANALYST", "analyst-1",
                Instant.parse("2026-09-01T11:05:00Z"), Map.of("evidence_id", "ev-123")));

        events.saveAndFlush(new InvestigationEvent(inv, "STATUS_CHANGED", "ANALYST", "analyst-1",
                Instant.parse("2026-09-01T11:10:00Z"), Map.of("from", "OPEN", "to", "INVESTIGATING")));

        events.saveAndFlush(new InvestigationEvent(inv, "RESOLUTION_RECORDED", "ANALYST", "analyst-1",
                Instant.parse("2026-09-01T11:30:00Z"), Map.of("resolution", "FALSE_POSITIVE")));

        List<InvestigationEvent> history = events.findByInvestigationId(inv.getId());
        assertThat(history).hasSize(4);
        assertThat(history).extracting(InvestigationEvent::getEventType)
                .containsExactly("INVESTIGATION_CREATED", "EVIDENCE_ADDED", "STATUS_CHANGED", "RESOLUTION_RECORDED");

        // Old events remain intact - history is append-only
        InvestigationEvent first = history.get(0);
        assertThat(first.getPayload()).containsEntry("note", "Auto-opened from BLOCK decision");
    }

    @Test
    void auditRecordsPersist() {
        AuditLog log = auditLogs.saveAndFlush(new AuditLog(
                "ANALYST", "analyst-1", "STATUS_CHANGED",
                "Investigation", transaction.getId(),
                Map.of("status", "OPEN"),
                Map.of("status", "INVESTIGATING"),
                Instant.parse("2026-09-01T11:10:00Z"),
                Map.of("reason", "Started review"),
                "corr-123"));

        assertThat(log.getId()).isNotNull();
        assertThat(auditLogs.findByEntityTypeAndEntityId("Investigation", transaction.getId())).hasSize(1);
    }

    @Test
    void auditEntityReferenceWorks() {
        auditLogs.saveAndFlush(new AuditLog(
                "SYSTEM", "auto", "DECISION_CREATED",
                "DecisionRecord", transaction.getId(),
                null, Map.of("decision", "BLOCK"),
                Instant.now(), null, "corr-dec-1"));

        auditLogs.saveAndFlush(new AuditLog(
                "ANALYST", "analyst-1", "INVESTIGATION_OPENED",
                "Investigation", transaction.getId(),
                null, Map.of("reference", "INV-456"),
                Instant.now(), null, "corr-dec-1"));

        List<AuditLog> logs = auditLogs.findByEntityTypeAndEntityId("DecisionRecord", transaction.getId());
        assertThat(logs).hasSize(1);
        logs = auditLogs.findByEntityTypeAndEntityId("Investigation", transaction.getId());
        assertThat(logs).hasSize(1);

        // Same correlation_reference groups related audit entries
        List<AuditLog> correlated = auditLogs.findByCorrelationReference("corr-dec-1");
        assertThat(correlated).hasSize(2);
    }
}