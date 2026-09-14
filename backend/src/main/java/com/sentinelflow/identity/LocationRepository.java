package com.sentinelflow.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    List<Location> findByUserId(UUID userId);

    List<Location> findByUser_Id(UUID userId);

    long countByUserId(UUID userId);

    long countByUser_Id(UUID userId);
}
