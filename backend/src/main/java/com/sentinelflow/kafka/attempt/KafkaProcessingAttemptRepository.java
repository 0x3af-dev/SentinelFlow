package com.sentinelflow.kafka.attempt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KafkaProcessingAttemptRepository extends JpaRepository<KafkaProcessingAttempt, UUID> {
    Optional<KafkaProcessingAttempt> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
    long countByStatus(KafkaProcessingAttempt.AttemptStatus status);
    long countByStatusIn(Collection<KafkaProcessingAttempt.AttemptStatus> statuses);
    long countByStatusAndUpdatedAtBefore(KafkaProcessingAttempt.AttemptStatus status, Instant before);
    List<KafkaProcessingAttempt> findTop20ByStatusOrderByUpdatedAtDesc(KafkaProcessingAttempt.AttemptStatus status);
    List<KafkaProcessingAttempt> findTop20ByStatusInOrderByUpdatedAtDesc(Collection<KafkaProcessingAttempt.AttemptStatus> statuses);
    List<KafkaProcessingAttempt> findTop20ByStatusAndUpdatedAtBefore(KafkaProcessingAttempt.AttemptStatus status, Instant before);
}
