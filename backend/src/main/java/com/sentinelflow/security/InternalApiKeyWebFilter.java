package com.sentinelflow.security;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Authenticates service-to-service callers of {@code /internal/**} endpoints
 * with a shared secret carried in the {@code X-Internal-Api-Key} header,
 * granting the {@code ROLE_SERVICE} authority. Requests to other paths pass
 * through untouched. An existing valid JWT (e.g. an ADMIN bearer token) is
 * honored instead: if the exchange is already authenticated the internal key
 * filter does not override it because the AuthorizationFilter consults the
 * already-present security context first.
 */
public class InternalApiKeyWebFilter extends AuthenticationWebFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    public InternalApiKeyWebFilter(SecurityProperties props) {
        super((org.springframework.security.authentication.ReactiveAuthenticationManager)
                authentication -> reactor.core.publisher.Mono.just(authentication));
        setServerAuthenticationConverter(exchange -> {
            String path = exchange.getRequest().getPath().value();
            if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
                return reactor.core.publisher.Mono.empty();
            }
            String apiKey = exchange.getRequest().getHeaders().getFirst(INTERNAL_API_KEY_HEADER);
            if (apiKey == null || apiKey.isBlank() || !apiKey.equals(props.internalApiKey())) {
                return reactor.core.publisher.Mono.empty();
            }
            return reactor.core.publisher.Mono.just(new UsernamePasswordAuthenticationToken(
                    new AuthPrincipal("internal", "SERVICE"), null,
                    List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        });
        setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
    }
}