package com.sentinelflow.security;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Parses the {@code Authorization: Bearer <jwt>} header and, when the token is
 * valid, authenticates the exchange with a {@link AuthPrincipal} principal and
 * a single {@code ROLE_<role>} authority. Expired, malformed or absent tokens
 * are treated as anonymous — the authorize rules then return 401 for protected
 * paths. The token is already cryptographically verified before use, so the
 * authentication manager is a passthrough: this filter never trusts any field
 * the client could have put in the request body.
 */
public class JwtAuthenticationWebFilter extends AuthenticationWebFilter {

    private static final String BEARER = "Bearer ";

    public JwtAuthenticationWebFilter(JwtTokenService tokenService) {
        this(tokenService, (org.springframework.security.authentication.ReactiveAuthenticationManager)
                authentication -> reactor.core.publisher.Mono.just(authentication));
    }

    JwtAuthenticationWebFilter(JwtTokenService tokenService, ReactiveAuthenticationManager manager) {
        super(manager);
        setServerAuthenticationConverter(exchange -> {
            String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith(BEARER)) {
                return reactor.core.publisher.Mono.empty();
            }
            AuthPrincipal principal = tokenService.decode(header.substring(BEARER.length()).trim());
            if (principal == null) {
                return reactor.core.publisher.Mono.empty();
            }
            return reactor.core.publisher.Mono.just(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))));
        });
        setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
    }
}