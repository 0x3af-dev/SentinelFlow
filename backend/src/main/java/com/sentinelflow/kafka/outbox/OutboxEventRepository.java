package com.sentinelflow.kafka.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxEvent.OutboxStatus status);
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
    long countByStatus(OutboxEvent.OutboxStatus status);
    Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtDesc(OutboxEvent.OutboxStatus status);
}
