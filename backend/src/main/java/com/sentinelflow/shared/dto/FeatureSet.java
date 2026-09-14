package com.sentinelflow.shared.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record FeatureSet(
    String featureSchemaVersion,
    Instant generatedAt,
    Map<String, Object> features
) {

    public static FeatureSet of(String featureSchemaVersion, Map<String, Object> features) {
        return new FeatureSet(featureSchemaVersion, Instant.now(), features);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = features.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        // Handle numeric conversions
        if (value instanceof Number n) {
            if (type == Integer.class) return type.cast(n.intValue());
            if (type == Long.class) return type.cast(n.longValue());
            if (type == Double.class) return type.cast(n.doubleValue());
            if (type == BigDecimal.class) return type.cast(BigDecimal.valueOf(n.doubleValue()));
        }
        if (type == Boolean.class && value instanceof Number n) {
            return type.cast(n.intValue() != 0);
        }
        if (type == String.class) {
            return type.cast(value.toString());
        }
        return type.cast(value);
    }

    public Double getDouble(String key) {
        Object v = features.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        return get(key, Double.class);
    }

    public Long getLong(String key) {
        Object v = features.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return get(key, Long.class);
    }

    public Integer getInteger(String key) {
        Object v = features.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return get(key, Integer.class);
    }

    public Boolean getBoolean(String key) {
        Object v = features.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return get(key, Boolean.class);
    }

    public String getString(String key) {
        return get(key, String.class);
    }

    public BigDecimal getBigDecimal(String key) {
        Object v = features.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return get(key, BigDecimal.class);
    }
}