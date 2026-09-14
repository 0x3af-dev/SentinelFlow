package com.sentinelflow.kafka.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(OutboxEvent.OutboxStatus status);
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
