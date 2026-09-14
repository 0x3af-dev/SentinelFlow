package com.sentinelflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.dto.InvestigationExplanation;
import com.sentinelflow.ai.dto.InvestigationExplanationRequest;
import com.sentinelflow.ai.exception.AiProviderResponseException;
import com.sentinelflow.ai.exception.AiProviderUnavailableException;
import com.sentinelflow.ai.exception.AiResponseInvalidException;
import com.sentinelflow.ai.exception.AiUnavailableException;
import com.sentinelflow.ai.gateway.AiGateway;
import com.sentinelflow.ai.model.AiInvestigationRun;
import com.sentinelflow.ai.prompt.PromptAssembler;
import com.sentinelflow.ai.repo.AiInvestigationRunRepository;
import com.sentinelflow.ai.tool.InvestigationAiTools;
import com.sentinelflow.ai.tool.ToolCallBudget;
import com.sentinelflow.ai.validation.ExplanationValidator;
import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.InvestigationSummary;
import com.sentinelflow.analytics.dto.TimelineEntry;
import com.sentinelflow.investigation.service.InvestigationApplicationService;
import com.sentinelflow.metrics.SentinelFlowMetrics;
import com.sentinelflow.observability.Md;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one AI investigation. This service is always present, but when
 * the capability is disabled every request fails fast with AI_UNAVAILABLE. The
 * LLM runs inside a per-request tool budget, its output is validated against
 * the persisted decision and the evidence universe, and every run is persisted
 * for audit regardless of outcome. The AI never mutates decision data and never
 * sits on the transaction critical path.
 */
@Service
public class AiInvestigationService {

    private final AiProperties properties;
    private final ObjectProvider<AiGateway> gatewayProvider;
    private final InvestigationAiTools tools;
    private final PromptAssembler promptAssembler;
    private final ExplanationValidator validator;
    private final AiInvestigationRunRepository runRepository;
    private final InvestigationApplicationService investigationService;
    private final ToolCallBudget toolCallBudget;
    private final ObjectMapper objectMapper;
    private final SentinelFlowMetrics metrics;

    public AiInvestigationService(
            AiProperties properties,
            ObjectProvider<AiGateway> gatewayProvider,
            InvestigationAiTools tools,
            PromptAssembler promptAssembler,
            ExplanationValidator validator,
            AiInvestigationRunRepository runRepository,
            InvestigationApplicationService investigationService,
            ToolCallBudget toolCallBudget,
            ObjectMapper objectMapper,
            SentinelFlowMetrics metrics) {
        this.properties = properties;
        this.gatewayProvider = gatewayProvider;
        this.tools = tools;
        this.promptAssembler = promptAssembler;
        this.validator = validator;
        this.runRepository = runRepository;
        this.investigationService = investigationService;
        this.toolCallBudget = toolCallBudget;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** Actor-aware explanation: records the authenticated principal on the audit run. */
    public InvestigationExplanation explain(UUID investigationId,
                                            InvestigationExplanationRequest request,
                                            String actorUsername) {
        metrics.aiRequested();
        requireEnabled();
        long started = System.nanoTime();
        String correlationId = UUID.randomUUID().toString();
        return Md.run(Md.of(Md.OP_AI, correlationId, null, null, investigationId.toString()), () -> {
            try {
                InvestigationExplanation answer = doExplain(investigationId, request, correlationId, started);
                persistSuccess(investigationId, request, correlationId, started, gatewayProvider.getIfAvailable(), answer, actorUsername);
                return answer;
            } catch (RuntimeException failure) {
                String code = persistFailure(investigationId, request, correlationId, started, failure, actorUsername);
                metrics.aiFailed(code, System.nanoTime() - started);
                throw translate(failure);
            }
        });
    }

    public InvestigationExplanation explain(UUID investigationId, InvestigationExplanationRequest request) {
        return explain(investigationId, request, null);
    }

    private InvestigationExplanation doExplain(UUID investigationId, InvestigationExplanationRequest request,
                                               String correlationId, long started) {
        // Fails fast with a 404 when the investigation does not exist: a
        // non-existent transaction can never reach the model provider.
        InvestigationSummary summary = investigationService.summary(investigationId);
        List<TimelineEntry> timeline = investigationService.timeline(investigationId);
        DecisionReplayResponse replay = investigationService.decisionReplay(investigationId);
        ExplanationValidator.AllowedArtifacts allowed = allowGrounding(replay, summary);

        AiGateway gateway = requireGateway();
        InvestigationExplanation answer;
        toolCallBudget.begin();
        try {
            PromptAssembler.PromptBundle prompt = promptAssembler.assemble(
                    new PromptAssembler.KernelContext(
                            summary, timeline, replay.policy(), request.requestType(), request.freeFormQuestion()));
            answer = gateway.generate(
                    new AiGateway.GenerationRequest(prompt.systemPrompt(), prompt.userQuestion(), tools));
        } finally {
            // Always closes the budget for this request, even on failure.
            toolCallBudget.end();
        }

        ExplanationValidator.ValidationResult validation = validator.validate(answer, allowed);
        if (!validation.valid()) {
            throw new AiResponseInvalidException(
                    "AI response failed validation: " + String.join("; ", validation.violations()));
        }
        metrics.aiSucceeded(System.nanoTime() - started, toolCallBudget.currentTotal());
        return answer;
    }

    private RuntimeException translate(RuntimeException failure) {
        if (failure instanceof AiProviderResponseException e) {
            return new AiResponseInvalidException(e.getMessage(), e);
        }
        if (failure instanceof AiProviderUnavailableException e) {
            return new AiUnavailableException(e.getMessage(), e);
        }
        if (failure instanceof AiUnavailableException || failure instanceof AiResponseInvalidException) {
            return failure;
        }
        return new AiUnavailableException("AI investigation failed unexpectedly", failure);
    }

    public List<AiInvestigationRun> runsFor(UUID investigationId) {
        return runRepository.findByInvestigationIdOrderByCreatedAtDesc(investigationId);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new AiUnavailableException("AI investigations are disabled");
        }
    }

    private AiGateway requireGateway() {
        AiGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new AiUnavailableException("AI gateway is not configured");
        }
        return gateway;
    }

    private ExplanationValidator.AllowedArtifacts allowGrounding(DecisionReplayResponse replay,
                                                                 InvestigationSummary summary) {
        Set<String> evidenceIds = replay.evidence().nodes().stream()
                .map(node -> node.id().toString())
                .collect(Collectors.toCollection(HashSet::new));
        Set<UUID> simulationIds = summary.policySimulations().stream()
                .map(s -> s.simulationId())
                .collect(Collectors.toSet());
        Set<UUID> counterfactualIds = summary.counterfactuals().stream()
                .map(c -> c.analysisId())
                .collect(Collectors.toSet());
        return new ExplanationValidator.AllowedArtifacts(
                evidenceIds, simulationIds, counterfactualIds,
                replay.decision().finalDecision(), replay.riskScore().score(),
                policyReference(replay));
    }

    private String policyReference(DecisionReplayResponse replay) {
        if (replay.policy() == null || replay.policy().name() == null || replay.policy().name().isBlank()) {
            return null;
        }
        String version = replay.policy().version();
        return version == null || version.isBlank()
                ? replay.policy().name()
                : replay.policy().name() + "/" + version;
    }

    private String persistFailure(UUID investigationId, InvestigationExplanationRequest request, String correlationId,
                                long startedNanos, RuntimeException failure, String actorUsername) {
        String code;
        String message;
        if (failure instanceof AiProviderResponseException e) {
            code = "AI_RESPONSE_INVALID";
            message = truncate(e.getMessage());
        } else if (failure instanceof AiProviderUnavailableException e) {
            code = "AI_UNAVAILABLE";
            message = truncate(e.getMessage());
        } else if (failure instanceof AiUnavailableException ai) {
            code = ai.getCode();
            message = truncate(ai.getMessage());
        } else if (failure instanceof AiResponseInvalidException ai) {
            code = ai.getCode();
            message = truncate(ai.getMessage());
        } else {
            code = "AI_UNEXPECTED";
            message = truncate(failure.getMessage());
        }
        logFailedRun(investigationId, request, correlationId, startedNanos, code, message, actorUsername);
        return code;
    }

private void persistSuccess(UUID investigationId, InvestigationExplanationRequest request, String correlationId,
                            long startedNanos, AiGateway gateway, InvestigationExplanation answer,
                            String actorUsername) {
        AiInvestigationRun run = AiInvestigationRun.builder()
                .investigationId(investigationId)
                .requestType(request.requestType())
                .freeFormQuestion(request.freeFormQuestion())
                .status(AiInvestigationRun.Status.SUCCEEDED)
                .provider(gateway.provider())
                .model(gateway.model())
                .toolCallCount(toolCallBudget.currentTotal())
                .latencyMs(msSince(startedNanos))
                .correlationId(correlationId)
                .actorUsername(actorUsername)
                .response(objectMapper.convertValue(answer, new TypeReference<>() {
                }))
                .build();
        runRepository.save(run);
    }

    private void logFailedRun(UUID investigationId, InvestigationExplanationRequest request, String correlationId,
                              long startedNanos, String code, String message, String actorUsername) {
        AiInvestigationRun run = AiInvestigationRun.builder()
                .investigationId(investigationId)
                .requestType(request.requestType())
                .freeFormQuestion(request.freeFormQuestion())
                .status(AiInvestigationRun.Status.FAILED)
                .toolCallCount(toolCallBudget.currentTotal())
                .latencyMs(msSince(startedNanos))
                .correlationId(correlationId)
                .errorCode(code)
                .errorMessage(message)
                .actorUsername(actorUsername)
                .build();
        runRepository.save(run);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private static long msSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}