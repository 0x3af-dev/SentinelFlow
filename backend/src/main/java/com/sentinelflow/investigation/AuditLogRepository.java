package com.sentinelflow.investigation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    List<AuditLog> findByActorTypeAndActorReference(String actorType, String actorReference);
    List<AuditLog> findByCorrelationReference(String correlationReference);
}