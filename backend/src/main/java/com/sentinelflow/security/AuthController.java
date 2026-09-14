package com.sentinelflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stateless authentication endpoints. Login verifies a BCrypt password hash
 * against {@code security_users} and — on success — returns a short-lived
 * HS256 JWT plus the actor's username and role. Identity is never supplied by
 * the client; role comes exclusively from the security user record. Login is
 * rate limited in-memory per username. Logout is a UI-consistency no-op
 * because the token is client-held and thrown away client-side.
 */
@RestController
@RequestMapping("/api/auth")
@EnableConfigurationProperties(SecurityProperties.class)
public class AuthController {

    record LoginRequest(String username, String password) {
    }

    record LoginResponse(String token, String username, String role, int expiresInSeconds) {
    }

    private final SecurityUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final LoginRateLimiter rateLimiter;
    private final SecurityProperties props;
    private final AuditEventService audit;
    private final ObjectMapper objectMapper;

    public AuthController(SecurityUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenService jwtTokenService,
                          LoginRateLimiter rateLimiter,
                          SecurityProperties props,
                          AuditEventService audit,
                          ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/login")
    public Mono<Void> login(@RequestBody(required = false) LoginRequest request, ServerWebExchange exchange) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null) {
            return write(exchange, HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "username and password are required");
        }
        String username = request.username().trim();
        if (!rateLimiter.tryAcquire(username)) {
            audit.record("AUTHENTICATION_LOCKED_OUT", "SECURITY_USER", null, null,
                    Map.of("username", username));
            return write(exchange, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Try again later.");
        }

        var user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isEnabled()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiter.recordFailure(username);
            audit.record("AUTHENTICATION_FAILED", "SECURITY_USER", user == null ? null : user.getId(),
                    null, Map.of("username", username));
            return write(exchange, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Valid credentials are required");
        }

        rateLimiter.recordSuccess(username);
        AuthPrincipal principal = new AuthPrincipal(user.getUsername(), user.getRole());
        String token = jwtTokenService.issue(principal);
        audit.record("AUTHENTICATION_SUCCESS", "SECURITY_USER", user.getId(), principal,
                Map.of("role", user.getRole()));
        return write(exchange, HttpStatus.OK, new LoginResponse(
                token, user.getUsername(), user.getRole(), props.jwtTtlSeconds()));
    }

    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        return exchange.getResponse().setComplete();
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public Mono<Void> me(org.springframework.security.core.Authentication authentication,
                         ServerWebExchange exchange) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return write(exchange, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Valid credentials are required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal ap) {
            return write(exchange, HttpStatus.OK, Map.of(
                    "username", ap.username(),
                    "role", ap.role()));
        }
        return write(exchange, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Valid credentials are required");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, Object body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        return write(exchange, status, Map.of("code", code, "message", message));
    }
}