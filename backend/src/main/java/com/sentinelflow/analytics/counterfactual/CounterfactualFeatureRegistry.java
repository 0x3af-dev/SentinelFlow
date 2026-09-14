package com.sentinelflow.analytics.counterfactual;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Bounded set of numeric features a counterfactual analysis may modify. Only
 * features the model actually consumes are editable; each carries a documented
 * domain so edited values stay within sane, model-plausible bounds.
 */
@Component
public class CounterfactualFeatureRegistry {

    public record FeatureSpec(String name, String description, double min, double max, boolean integral) {
        public boolean withinBounds(double value) {
            return value >= min && value <= max;
        }
    }

    private static final Map<String, FeatureSpec> FEATURES = new LinkedHashMap<>();

    static {
        register("transaction_amount", "Transaction amount", 0, 1_000_000_000d, false);
        register("user_avg_transaction_amount", "User average transaction amount", 0, 1_000_000_000d, false);
        register("user_transaction_count_24h", "User transaction count (24h)", 0, 100_000d, true);
        register("user_transaction_count_7d", "User transaction count (7d)", 0, 500_000d, true);
        register("user_transaction_count_30d", "User transaction count (30d)", 0, 2_000_000d, true);
        register("user_total_volume_24h", "User total volume (24h)", 0, 10_000_000_000d, false);
        register("user_total_volume_7d", "User total volume (7d)", 0, 10_000_000_000d, false);
        register("user_total_volume_30d", "User total volume (30d)", 0, 10_000_000_000d, false);
        register("transactions_last_10_minutes", "Transactions in last 10 minutes", 0, 10_000d, true);
        register("transactions_last_1_hour", "Transactions in last 1 hour", 0, 50_000d, true);
        register("transactions_last_24_hours", "Transactions in last 24 hours", 0, 100_000d, true);
        register("device_transaction_count", "Device transaction count", 0, 1_000_000d, true);
        register("device_user_count", "Users seen on device", 1, 10_000d, true);
        register("location_transaction_count", "Location transaction count", 0, 1_000_000d, true);
        register("merchant_transaction_count", "Merchant transaction count", 0, 1_000_000d, true);
        register("merchant_category_frequency", "Merchant category frequency", 0, 1d, false);
        register("device_age_days", "Device age in days", 0, 3_650d, false);
        register("location_age_days", "Location age in days", 0, 3_650d, false);
        register("user_account_age_days", "User account age in days", 0, 3_650d, false);
    }

    private static void register(String name, String description, double min, double max, boolean integral) {
        FEATURES.put(name, new FeatureSpec(name, description, min, max, integral));
    }

    public FeatureSpec spec(String name) {
        return FEATURES.get(name);
    }

    public boolean isSupported(String name) {
        return FEATURES.containsKey(name);
    }
}