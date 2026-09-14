package com.sentinelflow.security;

import com.sentinelflow.investigation.AuditLog;
import com.sentinelflow.investigation.AuditLogRepository;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);

    private final AuditLogRepository repository;

    public AuditEventService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Record a security-relevant event. Actor reference should be the
     *  authenticated principal (never trust client-supplied identity). */
    public void record(String action, String entityType, java.util.UUID entityId,
                       AuthPrincipal actor, Map<String, Object> metadata) {
        record(action, entityType, entityId, actor, null, null, metadata, null);
    }

    public void record(String action, String entityType, java.util.UUID entityId,
                       AuthPrincipal actor, Map<String, Object> previousState,
                       Map<String, Object> newState, Map<String, Object> metadata,
                       String correlationReference) {
        String actorType = actor == null ? "SYSTEM" : "USER";
        String actorReference = actor == null ? "SYSTEM" : actor.username();
        AuditLog auditLog = new AuditLog(
                actorType, actorReference, action, entityType, entityId,
                previousState, newState, Instant.now(), metadata, correlationReference);
        repository.save(auditLog);
        log.debug("Audit action={} entity={}/{} actor={}", action, entityType, entityId, actorReference);
    }
}
