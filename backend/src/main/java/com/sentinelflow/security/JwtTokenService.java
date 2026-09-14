package com.sentinelflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final SecretKey signingKey;
    private final int ttlSeconds;

    public JwtTokenService(SecurityProperties props) {
        byte[] keyBytes = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttlSeconds = props.jwtTtlSeconds();
    }

    public String issue(AuthPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.username())
                .claim("role", principal.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /** Decode and validate the token, returning the principal if valid,
     *  or null on any parsing/expiration/claim error. */
    public AuthPrincipal decode(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthPrincipal(claims.getSubject(), claims.get("role", String.class));
        } catch (Exception e) {
            log.debug("JWT decode failed: {}", e.getMessage());
            return null;
        }
    }
}
