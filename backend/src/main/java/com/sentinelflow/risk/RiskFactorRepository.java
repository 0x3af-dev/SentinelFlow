package com.sentinelflow.risk;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskFactorRepository extends JpaRepository<RiskFactor, UUID> {
    List<RiskFactor> findByTransactionId(UUID transactionId);

    List<RiskFactor> findByTransaction_Id(UUID transactionId);
}
