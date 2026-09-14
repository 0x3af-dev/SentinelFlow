package com.sentinelflow.investigation.service;

import com.sentinelflow.analytics.counterfactual.CounterfactualService;
import com.sentinelflow.analytics.dto.AddInvestigationEventRequest;
import com.sentinelflow.analytics.dto.CreateInvestigationRequest;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.DisagreementInfo;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.InvestigationMetadata;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.PolicySimulationResponse;
import com.sentinelflow.analytics.dto.TimelineEntry;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import com.sentinelflow.analytics.policy.PolicyLabService;
import com.sentinelflow.analytics.replay.DecisionReplayService;
import com.sentinelflow.investigation.Investigation;
import com.sentinelflow.investigation.InvestigationEvent;
import com.sentinelflow.investigation.InvestigationEventRepository;
import com.sentinelflow.investigation.InvestigationPriority;
import com.sentinelflow.investigation.InvestigationRepository;
import com.sentinelflow.investigation.InvestigationStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application layer for the investigation API on top of the Phase 1
 * investigation model. Investigations and their events are append-only; the
 * API is read-only with respect to all production lineage data.
 */
@Service
public class InvestigationApplicationService {

    private final InvestigationRepository investigationRepository;
    private final InvestigationEventRepository eventRepository;
    private final TransactionRepository transactionRepository;
    private final DecisionReplayService replayService;
    private final PolicyLabService policyLabService;
    private final CounterfactualService counterfactualService;

    public InvestigationApplicationService(InvestigationRepository investigationRepository,
                                           InvestigationEventRepository eventRepository,
                                           TransactionRepository transactionRepository,
                                           DecisionReplayService replayService,
                                           PolicyLabService policyLabService,
                                           CounterfactualService counterfactualService) {
        this.investigationRepository = investigationRepository;
        this.eventRepository = eventRepository;
        this.transactionRepository = transactionRepository;
        this.replayService = replayService;
        this.policyLabService = policyLabService;
        this.counterfactualService = counterfactualService;
    }

    @Transactional
    public InvestigationMetadata create(CreateInvestigationRequest request) {
        String reference = requiredText(request.transactionReference(), "transactionReference");
        Transaction txn = transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> new AnalyticsNotFoundException("Transaction not found: " + reference));

        InvestigationPriority priority = resolvePriority(request.priority());
        String investigator = request.assignedTo() == null ? null : request.assignedTo().trim();

        Investigation investigation = investigationRepository.save(new Investigation(
                generateReference(), txn, InvestigationStatus.OPEN, priority,
                investigator, Instant.now()));

        Map<String, Object> payload = request.note() == null || request.note().isBlank()
                ? Map.of() : Map.of("note", request.note().trim());
        eventRepository.save(new InvestigationEvent(
                investigation, "INVESTIGATION_CREATED", "SYSTEM",
                investigator == null ? "SYSTEM" : investigator, Instant.now(), payload));

        return toMetadata(investigation, 1);
    }

    @Transactional(readOnly = true)
    public InvestigationMetadata get(UUID investigationId) {
        Investigation investigation = requireInvestigation(investigationId);
        return toMetadata(investigation, eventRepository.findByInvestigationId(investigationId).size());
    }

    @Transactional(readOnly = true)
    public List<InvestigationMetadata> list(String transactionReference) {
        List<Investigation> investigations;
        if (transactionReference == null || transactionReference.isBlank()) {
            investigations = investigationRepository.findAllByOrderByOpenedAtDesc();
        } else {
            Transaction txn = transactionRepository.findByTransactionReference(transactionReference.trim())
                    .orElseThrow(() -> new AnalyticsNotFoundException("Transaction not found: " + transactionReference));
            investigations = investigationRepository.findByTransactionIdOrderByOpenedAtDesc(txn.getId());
        }
        return investigations.stream()
                .map(i -> toMetadata(i, eventRepository.findByInvestigationId(i.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(UUID investigationId) {
        Investigation investigation = requireInvestigation(investigationId);
        return eventRepository.findByInvestigationId(investigationId).stream()
                .sorted(Comparator.comparing(InvestigationEvent::getEventTimestamp)
                        .thenComparing(InvestigationEvent::getCreatedAt)
                        .thenComparing(e -> e.getId().toString()))
                .map(e -> new TimelineEntry(e.getId(), e.getEventType(), e.getActorType(),
                        e.getActorReference(), e.getEventTimestamp(), e.getPayload()))
                .toList();
    }

    @Transactional
    public TimelineEntry addEvent(UUID investigationId, AddInvestigationEventRequest request) {
        String eventType = requiredText(request.eventType(), "eventType");
        Investigation investigation = requireInvestigation(investigationId);
        InvestigationEvent saved = eventRepository.save(new InvestigationEvent(
                investigation, eventType,
                request.actorType() == null || request.actorType().isBlank() ? "ANALYST" : request.actorType().trim(),
                request.actorReference(),
                Instant.now(), request.payload()));
        return new TimelineEntry(saved.getId(), saved.getEventType(), saved.getActorType(),
                saved.getActorReference(), saved.getEventTimestamp(), saved.getPayload());
    }

    @Transactional(readOnly = true)
    public DecisionReplayResponse decisionReplay(UUID investigationId) {
        return replayService.replay(requireInvestigation(investigationId)
                .getTransaction().getTransactionReference());
    }

    @Transactional(readOnly = true)
    public EvidenceGraphDto evidence(UUID investigationId) {
        DecisionReplayResponse replay = decisionReplay(investigationId);
        return new EvidenceGraphDto(replay.evidence().nodes(), replay.evidence().edges());
    }

    @Transactional(readOnly = true)
    public InvestigationSummary summary(UUID investigationId) {
        Investigation investigation = requireInvestigation(investigationId);
        String reference = investigation.getTransaction().getTransactionReference();
        DecisionReplayResponse replay = replayService.replay(reference);
        List<PolicySimulationResponse> simulations = policyLabService.listSimulations(reference);
        List<com.sentinelflow.analytics.dto.CounterfactualResponse> counterfactuals =
                counterfactualService.listCounterfactuals(reference);

        DisagreementInfo disagreement = DisagreementInfo.of(
                replay.riskScore().prediction(), replay.riskScore().score(), replay.triggeredRules());

        return new InvestigationSummary(
                reference, replay.transaction(), replay.riskScore(), replay.decision(),
                replay.riskFactors(), replay.triggeredRules(), disagreement,
                replay.evidence().nodeCount(), replay.evidence().edgeCount(),
                simulations, counterfactuals, toMetadata(investigation,
                        eventRepository.findByInvestigationId(investigationId).size()));
    }

    private Investigation requireInvestigation(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new AnalyticsNotFoundException("Investigation not found: " + investigationId));
    }

    private InvestigationMetadata toMetadata(Investigation investigation, int eventCount) {
        return new InvestigationMetadata(
                investigation.getId(),
                investigation.getInvestigationReference(),
                investigation.getTransaction().getTransactionReference(),
                investigation.getStatus().name(),
                investigation.getPriority().name(),
                investigation.getAssignedTo(),
                investigation.getOpenedAt(),
                investigation.getUpdatedAt(),
                investigation.getResolvedAt(),
                investigation.getResolution() == null ? null : investigation.getResolution().name(),
                investigation.getResolutionNotes(),
                eventCount);
    }

    private InvestigationPriority resolvePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return InvestigationPriority.MEDIUM;
        }
        for (InvestigationPriority p : InvestigationPriority.values()) {
            if (p.name().equalsIgnoreCase(priority.trim())) {
                return p;
            }
        }
        throw new AnalyticsValidationException("Unknown priority: " + priority);
    }

    private static String generateReference() {
        return "INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AnalyticsValidationException(field + " must not be blank");
        }
        return value.trim();
    }
}