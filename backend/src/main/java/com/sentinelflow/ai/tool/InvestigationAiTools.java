package com.sentinelflow.ai.tool;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.tool.ToolViews.BehavioralContextView;
import com.sentinelflow.ai.tool.ToolViews.BehavioralResult;
import com.sentinelflow.ai.tool.ToolViews.DecisionResult;
import com.sentinelflow.ai.tool.ToolViews.EvidenceResult;
import com.sentinelflow.ai.tool.ToolViews.DeviceHistoryView;
import com.sentinelflow.ai.tool.ToolViews.InvestigationContextResult;
import com.sentinelflow.ai.tool.ToolViews.InvestigationToolContext;
import com.sentinelflow.ai.tool.ToolViews.LocationHistoryView;
import com.sentinelflow.ai.tool.ToolViews.RecentTransactionView;
import com.sentinelflow.ai.tool.ToolViews.ToolError;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.TimelineEntry;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.replay.DecisionReplayService;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.evidence.EvidenceNode;
import com.sentinelflow.evidence.EvidenceNodeRepository;
import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.DeviceRepository;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.LocationRepository;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.investigation.Investigation;
import com.sentinelflow.investigation.InvestigationEventRepository;
import com.sentinelflow.investigation.InvestigationRepository;
import com.sentinelflow.investigation.service.InvestigationApplicationService;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The controlled, read-only tool surface exposed to the AI investigator. Every
 * method validates its arguments, enforces the active tool budget, bounds its
 * result set, and never mutates a row. Tools return structured
 * {@code (result, error)} pairs so the model can reason about gaps and abstain;
 * they throw neither for missing data nor for bad input.
 */
@Component
public class InvestigationAiTools {

    private static final Logger log = LoggerFactory.getLogger(InvestigationAiTools.class);

    private final Investigating investigating;

    public InvestigationAiTools(TransactionRepository transactions,
                                UserRepository users,
                                DeviceRepository devices,
                                LocationRepository locations,
                                DecisionReplayService replayService,
                                InvestigationApplicationService investigations,
                                InvestigationRepository investigationRepository,
                                InvestigationEventRepository eventRepository,
                                DecisionRecordRepository decisionRepository,
                                EvidenceNodeRepository evidenceNodeRepository,
                                ToolCallBudget budget,
                                AiProperties properties) {
        this.investigating = new Investigating(transactions, users, devices, locations,
                replayService, investigations, investigationRepository, eventRepository,
                decisionRepository, evidenceNodeRepository, budget, properties);
    }

    @Tool(description = "Return the bounded structured context for an investigation: persisted production decision, risk score, "
            + "model, rule findings, policy thresholds, disagreement, policy-lab simulations, counterfactual analyses and the "
            + "investigation event timeline. Use this first for questions about a specific investigation.")
    public InvestigationContextResult getInvestigationContext(
            @ToolParam(description = "Internal investigation id (UUID).") UUID investigationId) {
        return investigating.getInvestigationContext(investigationId);
    }

    @Tool(description = "Return the complete read-only decision lineage of a transaction: persisted risk score, model and feature "
            + "snapshot metadata, risk factors, triggered rules, policy thresholds, the recorded decision and the bounded evidence graph.")
    public DecisionResult getRiskDecision(
            @ToolParam(description = "Transaction reference, e.g. txn-demo-001.") String transactionReference) {
        return investigating.getRiskDecision(transactionReference);
    }

    @Tool(description = "Return the bounded evidence graph (nodes and edges) backing a transaction's recorded decision.")
    public EvidenceResult getEvidence(
            @ToolParam(description = "Transaction reference, e.g. txn-demo-001.") String transactionReference) {
        return investigating.getEvidence(transactionReference);
    }

    @Tool(description = "Return bounded behavioral context for a user: recent transactions with recorded outcomes, device history and "
            + "location history. Use for questions about behavioral context, new devices or new locations.")
    public BehavioralResult getBehavioralContext(
            @ToolParam(description = "Internal user id (UUID) or external user reference.") String userId) {
        return investigating.getBehavioralContext(userId);
    }

    /**
     * Read-only implementation. Kept as a separate class so the @Tool facade
     * stays focused on schema/description; the context byte-for-byte is what the
     * model sees.
     */
    static final class Investigating {

        private final TransactionRepository transactions;
        private final UserRepository users;
        private final DeviceRepository devices;
        private final LocationRepository locations;
        private final DecisionReplayService replayService;
        private final InvestigationApplicationService investigations;
        private final InvestigationRepository investigationRepository;
        private final InvestigationEventRepository eventRepository;
        private final DecisionRecordRepository decisionRepository;
        private final EvidenceNodeRepository evidenceNodeRepository;
        private final ToolCallBudget budget;
        private final AiProperties properties;

        Investigating(TransactionRepository transactions, UserRepository users,
                      DeviceRepository devices, LocationRepository locations,
                      DecisionReplayService replayService,
                      InvestigationApplicationService investigations,
                      InvestigationRepository investigationRepository,
                      InvestigationEventRepository eventRepository,
                      DecisionRecordRepository decisionRepository,
                      EvidenceNodeRepository evidenceNodeRepository,
                      ToolCallBudget budget, AiProperties properties) {
            this.transactions = transactions;
            this.users = users;
            this.devices = devices;
            this.locations = locations;
            this.replayService = replayService;
            this.investigations = investigations;
            this.investigationRepository = investigationRepository;
            this.eventRepository = eventRepository;
            this.decisionRepository = decisionRepository;
            this.evidenceNodeRepository = evidenceNodeRepository;
            this.budget = budget;
            this.properties = properties;
        }

        InvestigationContextResult getInvestigationContext(UUID investigationId) {
            if (investigationId == null) {
                return new InvestigationContextResult(null, new ToolError("INVALID_ARGUMENT", "investigationId is required"));
            }
            budget.consume("getInvestigationContext");
            try {
                Investigation investigation = investigationRepository
                        .findById(investigationId)
                        .orElseThrow(() -> new AnalyticsNotFoundException("Investigation not found: " + investigationId));
                InvestigationSummary summary = investigations.summary(investigationId);
                int limit = properties.getMaxHistoryEvents();
                List<TimelineEntry> timeline = investigationId == null
                        ? List.of()
                        : eventRepository.findByInvestigationId(investigationId).stream()
                                .sorted(java.util.Comparator
                                        .comparing(com.sentinelflow.investigation.InvestigationEvent::getEventTimestamp)
                                        .thenComparing(com.sentinelflow.investigation.InvestigationEvent::getCreatedAt))
                                .limit(limit)
                                .map(e -> new TimelineEntry(e.getId(), e.getEventType(), e.getActorType(),
                                        e.getActorReference(), e.getEventTimestamp(), e.getPayload()))
                                .toList();
                return new InvestigationContextResult(
                        new InvestigationToolContext(summary, timeline), null);
            } catch (AnalyticsNotFoundException e) {
                return new InvestigationContextResult(null, new ToolError("NOT_FOUND", e.getMessage()));
            }
        }

        DecisionResult getRiskDecision(String transactionReference) {
            String ref = required(transactionReference);
            if (ref == null) {
                return new DecisionResult(null, new ToolError("INVALID_ARGUMENT", "transactionReference is required"));
            }
            budget.consume("getRiskDecision");
            try {
                return new DecisionResult(replayService.replay(ref), null);
            } catch (AnalyticsNotFoundException e) {
                return new DecisionResult(null, new ToolError("NOT_FOUND", e.getMessage()));
            }
        }

        EvidenceResult getEvidence(String transactionReference) {
            String ref = required(transactionReference);
            if (ref == null) {
                return new EvidenceResult(null, new ToolError("INVALID_ARGUMENT", "transactionReference is required"));
            }
            budget.consume("getEvidence");
            try {
                var replay = replayService.replay(ref);
                return new EvidenceResult(
                        new EvidenceGraphDto(replay.evidence().nodes(), replay.evidence().edges()), null);
            } catch (AnalyticsNotFoundException e) {
                return new EvidenceResult(null, new ToolError("NOT_FOUND", e.getMessage()));
            }
        }

        BehavioralResult getBehavioralContext(String userId) {
            if (userId == null || userId.isBlank()) {
                return new BehavioralResult(null, new ToolError("INVALID_ARGUMENT", "userId is required"));
            }
            budget.consume("getBehavioralContext");
            Optional<User> user = resolveUser(userId);
            if (user.isEmpty()) {
                return new BehavioralResult(null, new ToolError("NOT_FOUND", "User not found: " + userId));
            }
            User u = user.get();
            int limit = properties.getMaxResultRows();

            List<RecentTransactionView> recent = transactions
                    .findByUser_IdOrderByTransactionTimestampDesc(u.getId()).stream()
                    .limit(limit)
                    .map(this::toRecentTransaction)
                    .toList();
            List<DeviceHistoryView> deviceHistory = devices.findByUser_Id(u.getId()).stream()
                    .limit(limit)
                    .map(d -> toDeviceHistory(d, limit))
                    .toList();
            List<LocationHistoryView> locationHistory = locations.findByUser_Id(u.getId()).stream()
                    .limit(limit)
                    .map(this::toLocationHistory)
                    .toList();

            return new BehavioralResult(new BehavioralContextView(
                    u.getExternalReference(),
                    accountAgeDays(u),
                    recent, deviceHistory, locationHistory), null);
        }

        private RecentTransactionView toRecentTransaction(Transaction txn) {
            DecisionRecord decision = decisionRepository.findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId())
                    .orElse(null);
            return new RecentTransactionView(
                    txn.getTransactionReference(),
                    txn.getTransactionTimestamp(),
                    txn.getAmount(),
                    txn.getCurrency(),
                    txn.getMerchant().getName(),
                    txn.getMerchant().getCategory(),
                    txn.getChannel(),
                    decision == null ? null : decision.getFinalDecision().name(),
                    decision == null ? null : decision.getRiskScore().getRiskScore(),
                    evidenceNodeId("TRANSACTION", txn.getId()));
        }

        private DeviceHistoryView toDeviceHistory(Device device, int limit) {
            List<String> knownUsers = transactions.findDistinctUserIdsByDeviceId(device.getId()).stream()
                    .limit(limit)
                    .map(id -> users.findById(id).map(User::getExternalReference).orElse(id.toString()))
                    .toList();
            return new DeviceHistoryView(
                    device.getDeviceReference(),
                    device.getDeviceType(),
                    device.getPlatform(),
                    device.getFirstSeenAt(),
                    device.getLastSeenAt(),
                    transactions.countByDevice_Id(device.getId()),
                    knownUsers,
                    evidenceNodeId("DEVICE", device.getId()));
        }

        private LocationHistoryView toLocationHistory(Location location) {
            return new LocationHistoryView(
                    location.getCountry(),
                    location.getRegion(),
                    location.getCity(),
                    location.getFirstSeenAt(),
                    location.getLastSeenAt(),
                    transactions.countByLocation_Id(location.getId()),
                    evidenceNodeId("LOCATION", location.getId()));
        }

        private Optional<User> resolveUser(String userId) {
            try {
                UUID id = UUID.fromString(userId);
                Optional<User> byId = users.findById(id);
                if (byId.isPresent()) {
                    return byId;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to external reference lookup.
            }
            return users.findByExternalReference(userId);
        }

        private String evidenceNodeId(String entityType, UUID entityId) {
            List<EvidenceNode> nodes = evidenceNodeRepository.findByEntityTypeAndEntityId(entityType, entityId);
            return nodes.isEmpty() ? null : nodes.get(0).getId().toString();
        }

        private static Long accountAgeDays(User user) {
            return user.getCreatedAt() == null ? null : Duration.between(user.getCreatedAt(), Instant.now()).toDays();
        }

        private static String required(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}