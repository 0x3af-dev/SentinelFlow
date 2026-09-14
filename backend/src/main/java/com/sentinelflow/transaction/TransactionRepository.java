package com.sentinelflow.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByTransactionReference(String transactionReference);

    List<Transaction> findByUserId(UUID userId);

    List<Transaction> findByUser_Id(UUID userId);

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByUserIdAndTransactionTimestampAfter(UUID userId, Instant timestamp);

    List<Transaction> findByUser_IdAndTransactionTimestampAfter(UUID userId, Instant timestamp);

    List<Transaction> findByUserIdAndDeviceId(UUID userId, UUID deviceId);

    List<Transaction> findByUser_IdAndDevice_Id(UUID userId, UUID deviceId);

    List<Transaction> findByUserIdAndLocationId(UUID userId, UUID locationId);

    List<Transaction> findByUser_IdAndLocation_Id(UUID userId, UUID locationId);

    long countByDeviceId(UUID deviceId);

    long countByDevice_Id(UUID deviceId);

    long countByLocationId(UUID locationId);

    long countByLocation_Id(UUID locationId);

    @Query("SELECT DISTINCT t.user.id FROM Transaction t WHERE t.device.id = :deviceId")
    List<UUID> findDistinctUserIdsByDeviceId(UUID deviceId);

    Optional<Transaction> findFirstByUserIdOrderByTransactionTimestampAsc(UUID userId);

    Optional<Transaction> findFirstByUser_IdOrderByTransactionTimestampAsc(UUID userId);

    Optional<Transaction> findFirstByUserIdOrderByTransactionTimestampDesc(UUID userId);

    Optional<Transaction> findFirstByUser_IdOrderByTransactionTimestampDesc(UUID userId);

    List<Transaction> findByUser_IdOrderByTransactionTimestampDesc(UUID userId);
}
