package com.sentinelflow.decision.service;

import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.decision.FinalDecision;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.shared.dto.PolicyEvaluationResult;
import com.sentinelflow.transaction.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class DecisionService {

    private final DecisionRecordRepository repository;

    public DecisionService(DecisionRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DecisionRecord createDecision(Transaction transaction, RiskScore riskScore,
                                         DecisionPolicy policy, PolicyEvaluationResult evaluation) {
        // Check for existing decision with same lineage (idempotency)
        Optional<DecisionRecord> existing = repository.findByTransactionIdAndRiskScoreIdAndPolicyId(
                transaction.getId(), riskScore.getId(), policy.getId());

        if (existing.isPresent()) {
            return existing.get();
        }

        DecisionRecord record = new DecisionRecord(
                transaction,
                riskScore,
                policy,
                FinalDecision.valueOf(evaluation.decision()),
                evaluation.evaluatedAt(),
                evaluation.reason()
        );

        return repository.save(record);
    }

    @Transactional(readOnly = true)
    public Optional<DecisionRecord> findLatestByTransaction(UUID transactionId) {
        return repository.findFirstByTransactionIdOrderByCreatedAtDesc(transactionId);
    }
}