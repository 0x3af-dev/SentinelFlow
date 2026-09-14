package com.sentinelflow.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByDeviceReference(String deviceReference);

    List<Device> findByUserId(UUID userId);

    List<Device> findByUser_Id(UUID userId);

    long countByUserId(UUID userId);

    long countByUser_Id(UUID userId);
}
