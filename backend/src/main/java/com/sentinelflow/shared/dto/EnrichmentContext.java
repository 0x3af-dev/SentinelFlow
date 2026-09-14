package com.sentinelflow.shared.dto;

import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.User;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EnrichmentContext(
    Transaction transaction,
    User user,
    Device device,
    Location location,
    Merchant merchant,
    UserBehaviorProfile userBehavior,
    DeviceProfile deviceProfile,
    LocationProfile locationProfile,
    MerchantProfile merchantProfile
) {

    public record UserBehaviorProfile(
        long transactionCount24h,
        long transactionCount7d,
        long transactionCount30d,
        BigDecimal totalVolume24h,
        BigDecimal totalVolume7d,
        BigDecimal totalVolume30d,
        BigDecimal averageTransactionAmount,
        Instant firstTransactionAt,
        Instant lastTransactionAt,
        int knownDeviceCount,
        int knownLocationCount
    ) {}

    public record DeviceProfile(
        boolean isNewDevice,
        long deviceTransactionCount,
        long deviceUserCount,
        Instant firstSeenAt,
        Instant lastSeenAt
    ) {}

    public record LocationProfile(
        boolean isNewLocation,
        long locationTransactionCount,
        String country,
        String region,
        String city
    ) {}

    public record MerchantProfile(
        long merchantTransactionCount,
        String category,
        String country
    ) {}
}