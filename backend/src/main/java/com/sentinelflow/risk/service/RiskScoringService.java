package com.sentinelflow.risk.service;

import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.ModelStatus;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.ModelVersionRepository;
import com.sentinelflow.risk.RiskFactor;
import com.sentinelflow.risk.RiskFactorRepository;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.risk.RiskScoreRepository;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.transaction.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RiskScoringService {

    private final RiskScoreRepository riskScoreRepository;
    private final RiskFactorRepository riskFactorRepository;
    private final ModelVersionRepository modelVersionRepository;

    public RiskScoringService(RiskScoreRepository riskScoreRepository,
                              RiskFactorRepository riskFactorRepository,
                              ModelVersionRepository modelVersionRepository) {
        this.riskScoreRepository = riskScoreRepository;
        this.riskFactorRepository = riskFactorRepository;
        this.modelVersionRepository = modelVersionRepository;
    }

    @Transactional
    public RiskScore createRiskScore(Transaction transaction, MlPrediction prediction, FeatureSnapshot featureSnapshot) {
        // Find or create model version
        ModelVersion modelVersion = modelVersionRepository.findByModelNameAndVersion(
                prediction.modelName(), prediction.modelVersion())
                .orElseGet(() -> createModelVersion(prediction));

        RiskScore riskScore = new RiskScore(
                transaction,
                modelVersion,
                prediction.riskScore(),
                prediction.prediction(),
                prediction.inferenceTimestamp(),
                (int) prediction.inferenceLatencyMs()
        );

        RiskScore saved = riskScoreRepository.save(riskScore);

        // Persist risk factors
        if (prediction.riskFactors() != null) {
            for (MlPrediction.RiskFactorDto factor : prediction.riskFactors()) {
                RiskFactor rf = new RiskFactor(
                        transaction,
                        factor.factorType(),
                        factor.description(),
                        factor.severity(),
                        "MODEL_FEATURE"
                );
                riskFactorRepository.save(rf);
            }
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<RiskScore> findLatestByTransaction(UUID transactionId) {
        return riskScoreRepository.findFirstByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    @Transactional(readOnly = true)
    public List<RiskFactor> findRiskFactorsByTransaction(UUID transactionId) {
        return riskFactorRepository.findByTransaction_Id(transactionId);
    }

    private ModelVersion createModelVersion(MlPrediction prediction) {
        ModelVersion mv = new ModelVersion(
                prediction.modelName(),
                prediction.modelVersion(),
                "gradient_boosted_trees",
                prediction.featureSchemaVersion(),
                null,
                null,
                ModelStatus.ACTIVE
        );
        mv.setActivatedAt(Instant.now());
        return modelVersionRepository.save(mv);
    }
}