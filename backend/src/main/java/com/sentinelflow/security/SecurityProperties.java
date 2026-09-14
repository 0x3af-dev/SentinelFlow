package com.sentinelflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelflow.security")
public record SecurityProperties(
        String jwtSecret,
        int jwtTtlSeconds,
        String internalApiKey,
        boolean seedDemoUsers,
        RateLimitProperties rateLimit,
        String[] corsAllowedOrigins
) {
    public record RateLimitProperties(
            int maxAttempts,
            int windowSeconds,
            int cooldownSeconds
    ) {
    }
}
