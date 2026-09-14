package com.sentinelflow.investigation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {
    Optional<Investigation> findByInvestigationReference(String investigationReference);
    List<Investigation> findByTransactionId(UUID transactionId);
    List<Investigation> findByTransactionIdOrderByOpenedAtDesc(UUID transactionId);
    List<Investigation> findAllByOrderByOpenedAtDesc();
    List<Investigation> findByStatus(InvestigationStatus status);
    List<Investigation> findByAssignedTo(String assignedTo);
    List<Investigation> findByResolution(InvestigationResolution resolution);
}