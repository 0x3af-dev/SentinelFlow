package com.sentinelflow.decision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionPolicyRepository extends JpaRepository<DecisionPolicy, UUID> {
    Optional<DecisionPolicy> findByPolicyNameAndVersion(String policyName, String version);

    List<DecisionPolicy> findByPolicyName(String policyName);

    Optional<DecisionPolicy> findByPolicyNameAndStatus(String policyName, PolicyStatus status);
}
