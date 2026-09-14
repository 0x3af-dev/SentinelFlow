package com.sentinelflow.investigation.service;

import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import com.sentinelflow.investigation.Investigation;
import com.sentinelflow.investigation.InvestigationEvent;
import com.sentinelflow.investigation.InvestigationEventRepository;
import com.sentinelflow.investigation.InvestigationRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records append-only investigation events produced by analytical actions
 * (policy simulations, counterfactuals). Validates that the investigation
 * actually belongs to the transaction being analysed so analytical artifacts
 * are never attached to the wrong investigation.
 */
@Component
public class InvestigationEventPublisher {

    private final InvestigationRepository investigationRepository;
    private final InvestigationEventRepository eventRepository;

    public InvestigationEventPublisher(InvestigationRepository investigationRepository,
                                       InvestigationEventRepository eventRepository) {
        this.investigationRepository = investigationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void record(UUID investigationId, String transactionReference, String eventType,
                       String actorType, String actorReference, Map<String, Object> payload) {
        Investigation investigation = investigationRepository.findById(investigationId)
                .orElseThrow(() -> new AnalyticsNotFoundException("Investigation not found: " + investigationId));

        if (!investigation.getTransaction().getTransactionReference().equals(transactionReference)) {
            throw new AnalyticsValidationException(
                    "Investigation " + investigationId + " does not reference transaction " + transactionReference);
        }

        eventRepository.save(new InvestigationEvent(
                investigation, eventType, actorType, actorReference,
                java.time.Instant.now(), payload));
    }
}