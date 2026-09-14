package com.sentinelflow.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** In-memory, bounded login rate limiter (single-node only). Failed attempts
 *  accumulate per username until a max threshold is reached, after which the
 *  username is locked out for a configurable cooldown window. A successful
 *  login immediately resets the counter.  This is intentionally simple and
 *  non-distributed; a production deployment that needs distributed rate
 *  limiting should layer a Redis-backed guard in front of this. */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final int maxAttempts;
    private final int windowSeconds;
    private final int cooldownSeconds;
    private final SecureRandom random = new SecureRandom();
    private final java.util.concurrent.ConcurrentHashMap<String, Entry> attempts = new java.util.concurrent.ConcurrentHashMap<>();

    public LoginRateLimiter(SecurityProperties props) {
        var rl = props.rateLimit();
        this.maxAttempts = rl != null ? rl.maxAttempts() : 8;
        this.windowSeconds = rl != null ? rl.windowSeconds() : 900;
        this.cooldownSeconds = rl != null ? rl.cooldownSeconds() : 900;
    }

    /** Returns {@code true} if the request is allowed. Returns {@code false} if
     *  the username is temporarily locked out due to too many recent failures. */
    public boolean tryAcquire(String username) {
        long now = epochSeconds();
        Entry e = attempts.get(username);
        if (e != null && e.isLocked(now)) {
            log.debug("Login rate limited for user={}", username);
            return false;
        }
        return true;
    }

    /** Record a failed attempt. */
    public void recordFailure(String username) {
        long now = epochSeconds();
        attempts.compute(username, (k, existing) -> {
            if (existing == null || existing.isExpired(now, windowSeconds)) {
                return new Entry(1, now, 0);
            }
            int count = existing.failures + 1;
            if (count >= maxAttempts) {
                // Lock for at least one second so a same-second retry cannot bypass it.
                return new Entry(count, existing.firstAttemptAt, now + Math.max(cooldownSeconds, 1));
            }
            return new Entry(count, existing.firstAttemptAt, 0);
        });
    }

    /** Clear any lockout for the user on successful authentication. */
    public void recordSuccess(String username) {
        attempts.remove(username);
    }

    /** Test-only: ensure a completely fresh state between integration test classes
     *  that share the same JVM. */
    public void reset() {
        attempts.clear();
    }

    private static long epochSeconds() {
        return Instant.now().getEpochSecond();
    }

    record Entry(int failures, long firstAttemptAt, long lockedUntilEpoch) {
        boolean isLocked(long now) {
            return lockedUntilEpoch > 0 && now < lockedUntilEpoch;
        }

        boolean isExpired(long now, int windowSeconds) {
            return (now - firstAttemptAt) > windowSeconds;
        }
    }
}
