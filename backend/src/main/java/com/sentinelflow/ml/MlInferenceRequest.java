package com.sentinelflow.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlInferenceRequest(
    @JsonProperty("transaction_amount") double transactionAmount,
    @JsonProperty("transaction_hour") Integer transactionHour,
    @JsonProperty("transaction_day_of_week") Integer transactionDayOfWeek,
    @JsonProperty("transaction_timestamp") String transactionTimestamp,
    @JsonProperty("user_transaction_count_24h") Integer userTransactionCount24h,
    @JsonProperty("user_transaction_count_7d") Integer userTransactionCount7d,
    @JsonProperty("user_transaction_count_30d") Integer userTransactionCount30d,
    @JsonProperty("user_total_volume_24h") Double userTotalVolume24h,
    @JsonProperty("user_total_volume_7d") Double userTotalVolume7d,
    @JsonProperty("user_total_volume_30d") Double userTotalVolume30d,
    @JsonProperty("user_avg_transaction_amount") Double userAvgTransactionAmount,
    @JsonProperty("user_account_age_days") Double userAccountAgeDays,
    @JsonProperty("user_created_at") String userCreatedAt,
    @JsonProperty("transactions_last_10_minutes") Integer transactionsLast10Minutes,
    @JsonProperty("transactions_last_1_hour") Integer transactionsLast1Hour,
    @JsonProperty("transactions_last_24_hours") Integer transactionsLast24Hours,
    @JsonProperty("is_new_device") Integer isNewDevice,
    @JsonProperty("device_transaction_count") Integer deviceTransactionCount,
    @JsonProperty("device_user_count") Integer deviceUserCount,
    @JsonProperty("device_age_days") Double deviceAgeDays,
    @JsonProperty("device_first_seen_at") String deviceFirstSeenAt,
    @JsonProperty("is_new_location") Integer isNewLocation,
    @JsonProperty("location_transaction_count") Integer locationTransactionCount,
    @JsonProperty("location_age_days") Double locationAgeDays,
    @JsonProperty("location_first_seen_at") String locationFirstSeenAt,
    @JsonProperty("merchant_transaction_count") Integer merchantTransactionCount,
    @JsonProperty("merchant_category_frequency") Double merchantCategoryFrequency,
    @JsonProperty("channel") String channel,
    @JsonProperty("transaction_type") String transactionType,
    @JsonProperty("merchant_category") String merchantCategory,
    @JsonProperty("merchant_country") String merchantCountry,
    @JsonProperty("device_type") String deviceType,
    @JsonProperty("device_platform") String devicePlatform,
    @JsonProperty("user_country") String userCountry,
    @JsonProperty("user_region") String userRegion
) {
    public static MlInferenceRequest fromFeatures(Map<String, Object> features) {
        return new MlInferenceRequest(
            getDouble(features, "transaction_amount"),
            getInt(features, "transaction_hour"),
            getInt(features, "transaction_day_of_week"),
            getString(features, "transaction_timestamp"),
            getInt(features, "user_transaction_count_24h"),
            getInt(features, "user_transaction_count_7d"),
            getInt(features, "user_transaction_count_30d"),
            getDouble(features, "user_total_volume_24h"),
            getDouble(features, "user_total_volume_7d"),
            getDouble(features, "user_total_volume_30d"),
            getDouble(features, "user_avg_transaction_amount"),
            getDouble(features, "user_account_age_days"),
            getString(features, "user_created_at"),
            getInt(features, "transactions_last_10_minutes"),
            getInt(features, "transactions_last_1_hour"),
            getInt(features, "transactions_last_24_hours"),
            getInt(features, "is_new_device"),
            getInt(features, "device_transaction_count"),
            getInt(features, "device_user_count"),
            getDouble(features, "device_age_days"),
            getString(features, "device_first_seen_at"),
            getInt(features, "is_new_location"),
            getInt(features, "location_transaction_count"),
            getDouble(features, "location_age_days"),
            getString(features, "location_first_seen_at"),
            getInt(features, "merchant_transaction_count"),
            getDouble(features, "merchant_category_frequency"),
            getString(features, "channel", "ONLINE"),
            getString(features, "transaction_type", "PURCHASE"),
            getString(features, "merchant_category", "OTHER"),
            getString(features, "merchant_country", "IN"),
            getString(features, "device_type", "UNKNOWN"),
            getString(features, "device_platform", "UNKNOWN"),
            getString(features, "user_country", "IN"),
            getString(features, "user_region", "UNKNOWN")
        );
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    private static Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v == null ? defaultValue : v.toString();
    }
}