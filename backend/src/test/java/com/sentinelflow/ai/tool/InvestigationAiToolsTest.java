package com.sentinelflow.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.exception.ToolBudgetExceededException;
import com.sentinelflow.ai.tool.InvestigationAiTools.Investigating;
import com.sentinelflow.ai.tool.ToolViews.BehavioralResult;
import com.sentinelflow.ai.tool.ToolViews.DecisionResult;
import com.sentinelflow.ai.tool.ToolViews.EvidenceResult;
import com.sentinelflow.ai.tool.ToolViews.InvestigationContextResult;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.DecisionInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.PolicyInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.RiskScoreInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.TransactionInfo;
import com.sentinelflow.analytics.dto.EvidenceInfo;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.InvestigationMetadata;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.TimelineEntry;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.replay.DecisionReplayService;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
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
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvestigationAiToolsTest {

    private AiProperties properties;
    private ToolCallBudget budget;
    private TransactionRepository transactions;
    private UserRepository users;
    private DeviceRepository devices;
    private LocationRepository locations;
    private DecisionReplayService replayService;
    private InvestigationApplicationService investigations;
    private InvestigationRepository investigationRepository;
    private InvestigationEventRepository eventRepository;
    private DecisionRecordRepository decisionRepository;
    private EvidenceNodeRepository evidenceNodeRepository;
    private Investigating tools;

    private final UUID evidenceNodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setMaxResultRows(2);
        properties.setMaxHistoryEvents(5);
        budget = new ToolCallBudget(properties);
        budget.begin();

        transactions = mock(TransactionRepository.class);
        users = mock(UserRepository.class);
        devices = mock(DeviceRepository.class);
        locations = mock(LocationRepository.class);
        replayService = mock(DecisionReplayService.class);
        investigations = mock(InvestigationApplicationService.class);
        investigationRepository = mock(InvestigationRepository.class);
        eventRepository = mock(InvestigationEventRepository.class);
        decisionRepository = mock(DecisionRecordRepository.class);
        evidenceNodeRepository = mock(EvidenceNodeRepository.class);

        tools = new Investigating(transactions, users, devices, locations, replayService,
                investigations, investigationRepository, eventRepository, decisionRepository,
                evidenceNodeRepository, budget, properties);
    }

    @AfterEach
    void tearDown() {
        budget.end();
    }

    @Test
    void getRiskDecisionReturnsReplayWithoutError() {
        when(replayService.replay("txn-1")).thenReturn(replay());
        DecisionResult result = tools.getRiskDecision("txn-1");
        assertThat(result.error()).isNull();
        assertThat(result.replay()).isNotNull();
        assertThat(result.replay().decision().finalDecision()).isEqualTo("REVIEW");
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void getRiskDecisionMissingTransactionReturnsErrorNotThrow() {
        when(replayService.replay(anyString())).thenThrow(new AnalyticsNotFoundException("not found"));
        DecisionResult result = tools.getRiskDecision("txn-missing");
        assertThat(result.replay()).isNull();
        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void getRiskDecisionBlankArgumentReturnsInvalidArgument() {
        DecisionResult result = tools.getRiskDecision("   ");
        assertThat(result.replay()).isNull();
        assertThat(result.error().code()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void getEvidenceBuildsGraphFromReplay() {
        when(replayService.replay("txn-1")).thenReturn(replay());
        EvidenceResult result = tools.getEvidence("txn-1");
        assertThat(result.error()).isNull();
        assertThat(result.graph().nodes()).hasSize(1);
        assertThat(result.graph().nodes().get(0).id()).isEqualTo(evidenceNodeId);
    }

    @Test
    void getBehavioralContextBoundsRowsAndMinimizesPii() {
        User user = user("USR-1");
        UUID userId = user.getId();
        UUID deviceId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        when(users.findByExternalReference("USR-1")).thenReturn(Optional.of(user));

        Transaction t1 = txn("txn-1", userId, 100L);
        Transaction t2 = txn("txn-2", userId, 200L);
        Transaction t3 = txn("txn-3", userId, 300L);
        when(transactions.findByUser_IdOrderByTransactionTimestampDesc(userId))
                .thenReturn(List.of(t1, t2, t3));

        Device device = mock(Device.class);
        when(device.getId()).thenReturn(deviceId);
        when(device.getDeviceReference()).thenReturn("DEV-1");
        when(device.getDeviceType()).thenReturn("MOBILE");
        when(device.getPlatform()).thenReturn("ANDROID");
        when(device.getFirstSeenAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(device.getLastSeenAt()).thenReturn(Instant.now());
        when(devices.findByUser_Id(userId)).thenReturn(List.of(device));
        when(transactions.findDistinctUserIdsByDeviceId(deviceId)).thenReturn(List.of(userId));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(transactions.countByDevice_Id(deviceId)).thenReturn(3L);

        Location location = mock(Location.class);
        when(location.getId()).thenReturn(locationId);
        when(location.getCountry()).thenReturn("IN");
        when(location.getRegion()).thenReturn("Karnataka");
        when(location.getCity()).thenReturn("Bengaluru");
        when(location.getFirstSeenAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(location.getLastSeenAt()).thenReturn(Instant.now());
        when(locations.findByUser_Id(userId)).thenReturn(List.of(location));
        when(transactions.countByLocation_Id(locationId)).thenReturn(3L);

        when(evidenceNodeRepository.findByEntityTypeAndEntityId(anyString(), any())).thenReturn(List.of());

        BehavioralResult result = tools.getBehavioralContext("USR-1");
        assertThat(result.error()).isNull();
        assertThat(result.context().userExternalReference()).isEqualTo("USR-1");
        assertThat(result.context().recentTransactions()).hasSize(2);
        assertThat(result.context().devices()).hasSize(1);
        assertThat(result.context().locations()).hasSize(1);
        assertThat(result.context().devices().get(0).transactionCount()).isEqualTo(3L);
        assertThat(result.context().locations().get(0).country()).isEqualTo("IN");
        verify(users, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void unknownUserReturnsNotFoundPair() {
        when(users.findByExternalReference("USR-X")).thenReturn(Optional.empty());
        BehavioralResult result = tools.getBehavioralContext("USR-X");
        assertThat(result.context()).isNull();
        assertThat(result.error().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void nullInvestigationIdReturnsInvalidArgument() {
        InvestigationContextResult result = tools.getInvestigationContext(null);
        assertThat(result.context()).isNull();
        assertThat(result.error().code()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void investigationContextBuildsKernelFromSummaryAndBoundedTimeline() {
        UUID investigationId = UUID.randomUUID();
        Investigation investigation = mock(Investigation.class);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(investigations.summary(investigationId)).thenReturn(summary());
        Instant base = Instant.now();
        var event = new com.sentinelflow.investigation.InvestigationEvent(
                investigation, "NOTE", "ANALYST", "a1", base.plusSeconds(1), Map.of("text", "added note"));
        when(eventRepository.findByInvestigationId(investigationId)).thenReturn(List.of(event));

        InvestigationContextResult result = tools.getInvestigationContext(investigationId);
        assertThat(result.error()).isNull();
        assertThat(result.context().summary().transactionReference()).isEqualTo("txn-demo-001");
        assertThat(result.context().timeline()).hasSize(1);
        assertThat(result.context().timeline().get(0).eventType()).isEqualTo("NOTE");
    }

    @Test
    void exhaustedBudgetThrowsToolBudgetExceeded() {
        properties.setMaxToolCalls(1);
        when(replayService.replay(anyString())).thenReturn(replay());
        tools.getEvidence("txn-1");
        assertThatThrownBy(() -> tools.getEvidence("txn-1"))
                .isInstanceOf(ToolBudgetExceededException.class);
    }

    private DecisionReplayResponse replay() {
        return new DecisionReplayResponse(
                "txn-1",
                new TransactionInfo("APPROVED", new BigDecimal("100.00"), "USD", "ONLINE", "PURCHASE",
                        Instant.parse("2026-09-14T10:00:00Z")),
                null,
                null,
                new RiskScoreInfo(0.6, "MEDIUM", Instant.now(), 12),
                List.of(),
                List.of(),
                new PolicyInfo("fraud-policy", "v1", 0.75, 0.9, Map.of()),
                new DecisionInfo("REVIEW", "score above review threshold", Instant.now()),
                null,
                new EvidenceInfo(1, 0,
                        List.of(new EvidenceGraphDto.EvidenceNodeDto(evidenceNodeId, "DECISION", "INTERNAL",
                                "DECISION", evidenceNodeId, Instant.now(), Map.of("decision", "REVIEW"))),
                        List.of()));
    }

    private InvestigationSummary summary() {
        return new InvestigationSummary("txn-demo-001", null, null, null, List.of(), List.of(), null,
                1, 0, List.of(), List.of(),
                new InvestigationMetadata(UUID.randomUUID(), "INV-1", "txn-demo-001", "OPEN",
                        "MEDIUM", "analyst-1", Instant.now(), Instant.now(), null, null, null, 3));
    }

    private User user(String externalRef) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getExternalReference()).thenReturn(externalRef);
        when(user.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private Transaction txn(String ref, UUID userId, long amount) {
        Transaction txn = mock(Transaction.class);
        when(txn.getId()).thenReturn(UUID.randomUUID());
        when(txn.getTransactionReference()).thenReturn(ref);
        when(txn.getTransactionTimestamp()).thenReturn(Instant.now());
        when(txn.getAmount()).thenReturn(BigDecimal.valueOf(amount));
        when(txn.getCurrency()).thenReturn("INR");
        when(txn.getChannel()).thenReturn("ONLINE");
        Merchant merchant = mock(Merchant.class);
        when(merchant.getName()).thenReturn("Demo Mart");
        when(merchant.getCategory()).thenReturn("GROCERY");
        when(txn.getMerchant()).thenReturn(merchant);
        when(decisionRepository.findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId()))
                .thenReturn(Optional.empty());
        return txn;
    }
}