package com.sentinelflow.kafka.attempt;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KafkaProcessingAttemptRepository extends JpaRepository<KafkaProcessingAttempt, UUID> {
    Optional<KafkaProcessingAttempt> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
}
