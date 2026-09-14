package com.sentinelflow.transaction;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByExternalReference(String externalReference);
}
