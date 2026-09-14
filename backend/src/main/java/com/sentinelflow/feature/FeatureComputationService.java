package com.sentinelflow.feature;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class FeatureComputationService {

    public static final String FEATURE_SCHEMA_VERSION = "fs-v1";

    public FeatureSet compute(EnrichmentContext ctx) {
        Map<String, Object> features = new HashMap<>();

        // Transaction features
        ZonedDateTime txnTime = ctx.transaction().getTransactionTimestamp().atZone(ZoneOffset.UTC);
        features.put("transaction_amount", ctx.transaction().getAmount().doubleValue());
        features.put("transaction_hour", txnTime.getHour());
        features.put("transaction_day_of_week", txnTime.getDayOfWeek().getValue() % 7);
        features.put("transaction_timestamp", ctx.transaction().getTransactionTimestamp().toString());

        // User behavioral features
        EnrichmentContext.UserBehaviorProfile ub = ctx.userBehavior();
        features.put("user_transaction_count_24h", ub.transactionCount24h());
        features.put("user_transaction_count_7d", ub.transactionCount7d());
        features.put("user_transaction_count_30d", ub.transactionCount30d());
        features.put("user_total_volume_24h", ub.totalVolume24h().doubleValue());
        features.put("user_total_volume_7d", ub.totalVolume7d().doubleValue());
        features.put("user_total_volume_30d", ub.totalVolume30d().doubleValue());
        features.put("user_avg_transaction_amount", ub.averageTransactionAmount().doubleValue());
        
        if (ub.firstTransactionAt() != null) {
            long accountAgeDays = java.time.Duration.between(ub.firstTransactionAt(), ctx.transaction().getTransactionTimestamp()).toDays();
            features.put("user_account_age_days", (double) Math.max(0, accountAgeDays));
            features.put("user_created_at", ub.firstTransactionAt().toString());
        } else {
            features.put("user_account_age_days", 0.0);
        }

        // Velocity features
        features.put("transactions_last_10_minutes", calculateVelocity10Minutes(ctx));
        features.put("transactions_last_1_hour", calculateVelocity1Hour(ctx));
        features.put("transactions_last_24_hours", ub.transactionCount24h());

        // Device features
        EnrichmentContext.DeviceProfile dp = ctx.deviceProfile();
        features.put("is_new_device", dp.isNewDevice() ? 1 : 0);
        features.put("device_transaction_count", dp.deviceTransactionCount());
        features.put("device_user_count", dp.deviceUserCount());
        
        if (dp.firstSeenAt() != null) {
            long deviceAgeDays = java.time.Duration.between(dp.firstSeenAt(), ctx.transaction().getTransactionTimestamp()).toDays();
            features.put("device_age_days", (double) Math.max(0, deviceAgeDays));
            features.put("device_first_seen_at", dp.firstSeenAt().toString());
        } else {
            features.put("device_age_days", 0.0);
        }
        
        if (ctx.device() != null) {
            features.put("device_type", ctx.device().getDeviceType() != null ? ctx.device().getDeviceType() : "UNKNOWN");
            features.put("device_platform", ctx.device().getPlatform() != null ? ctx.device().getPlatform() : "UNKNOWN");
        } else {
            features.put("device_type", "UNKNOWN");
            features.put("device_platform", "UNKNOWN");
        }

        // Location features
        EnrichmentContext.LocationProfile lp = ctx.locationProfile();
        features.put("is_new_location", lp.isNewLocation() ? 1 : 0);
        features.put("location_transaction_count", lp.locationTransactionCount());
        
        if (ctx.location() != null && ctx.location().getFirstSeenAt() != null) {
            long locationAgeDays = java.time.Duration.between(ctx.location().getFirstSeenAt(), ctx.transaction().getTransactionTimestamp()).toDays();
            features.put("location_age_days", (double) Math.max(0, locationAgeDays));
            features.put("location_first_seen_at", ctx.location().getFirstSeenAt().toString());
        } else {
            features.put("location_age_days", 0.0);
        }
        
        if (ctx.location() != null) {
            features.put("user_country", ctx.location().getCountry() != null ? ctx.location().getCountry() : "IN");
            features.put("user_region", ctx.location().getRegion() != null ? ctx.location().getRegion() : "UNKNOWN");
        } else {
            features.put("user_country", "IN");
            features.put("user_region", "UNKNOWN");
        }

        // Merchant features
        EnrichmentContext.MerchantProfile mp = ctx.merchantProfile();
        features.put("merchant_transaction_count", mp.merchantTransactionCount());
        features.put("merchant_category_frequency", 0.5); // Placeholder
        
        if (ctx.merchant() != null) {
            features.put("merchant_category", ctx.merchant().getCategory() != null ? ctx.merchant().getCategory() : "OTHER");
            features.put("merchant_country", ctx.merchant().getCountry() != null ? ctx.merchant().getCountry() : "IN");
        } else {
            features.put("merchant_category", "OTHER");
            features.put("merchant_country", "IN");
        }

        // Transaction channel/type
        features.put("channel", ctx.transaction().getChannel() != null ? ctx.transaction().getChannel() : "ONLINE");
        features.put("transaction_type", ctx.transaction().getTransactionType() != null ? ctx.transaction().getTransactionType() : "PURCHASE");

        return FeatureSet.of(FEATURE_SCHEMA_VERSION, features);
    }

    private long calculateVelocity10Minutes(EnrichmentContext ctx) {
        // In a real implementation, this would query transactions in the last 10 minutes
        // For now, return a reasonable approximation based on 24h count
        long count24h = ctx.userBehavior().transactionCount24h();
        return Math.min(count24h, 20); // Cap at reasonable max
    }

    private long calculateVelocity1Hour(EnrichmentContext ctx) {
        // In a real implementation, this would query transactions in the last 1 hour
        long count24h = ctx.userBehavior().transactionCount24h();
        return Math.min(count24h / 2, 50); // Rough approximation
    }
}