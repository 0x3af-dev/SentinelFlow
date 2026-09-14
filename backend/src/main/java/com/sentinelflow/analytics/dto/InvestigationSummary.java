package com.sentinelflow.analytics.dto;

import java.util.List;

/**
 * Consolidated, read-only analyst view of an investigation: transaction
 * context, produced decision, signal evidence, and any analytical artifacts
 * (policy simulations / counterfactuals) recorded against the transaction.
 */
public record InvestigationSummary(
        String transactionReference,
        DecisionReplayResponse.TransactionInfo transaction,
        DecisionReplayResponse.RiskScoreInfo riskScore,
        DecisionReplayResponse.DecisionInfo decision,
        List<DecisionReplayResponse.RiskFactorInfo> riskFactors,
        List<DecisionReplayResponse.RuleInfo> triggeredRules,
        DisagreementInfo disagreement,
        int evidenceNodeCount,
        int evidenceEdgeCount,
        List<PolicySimulationResponse> policySimulations,
        List<CounterfactualResponse> counterfactuals,
        InvestigationMetadata investigation
) {
}