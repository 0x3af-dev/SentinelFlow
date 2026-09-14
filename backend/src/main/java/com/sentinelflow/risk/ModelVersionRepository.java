package com.sentinelflow.risk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {
    Optional<ModelVersion> findByModelNameAndVersion(String modelName, String version);

    List<ModelVersion> findByModelName(String modelName);
}
