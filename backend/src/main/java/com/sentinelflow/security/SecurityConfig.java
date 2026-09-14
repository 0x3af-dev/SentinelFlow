package com.sentinelflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stateless, backend-authoritative security for SentinelFlow. Authentication
 * is a self-contained HS256 JWT issued by {@link AuthController} after a BCrypt
 * password check; authorization is role-based and configured centrally here.
 * There is deliberately no session, no security bypass for tests, and never a
 * client-supplied identity field: controllers derive the actor exclusively from
 * the authenticated principal.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtAuthenticationWebFilter jwtAuthenticationWebFilter(JwtTokenService tokenService) {
        return new JwtAuthenticationWebFilter(tokenService);
    }

    @Bean
    InternalApiKeyWebFilter internalApiKeyWebFilter(SecurityProperties props) {
        return new InternalApiKeyWebFilter(props);
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            SecurityProperties props,
            ObjectMapper objectMapper,
            JwtAuthenticationWebFilter jwtFilter,
            InternalApiKeyWebFilter internalApiKeyFilter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(props)))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/**", "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .pathMatchers("/api/operations/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers("/internal/**").hasAnyRole("SERVICE", "ADMIN")
                        .pathMatchers("/actuator/metrics/**", "/actuator/flyway/**").hasAnyRole("OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/investigations")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/investigations/*/events")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/policy-lab/simulate")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/policy-lab/simulate-batch")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/counterfactuals")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/transactions/*/process")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/investigations/*/explanations")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .pathMatchers("/api/investigations/**")
                        .hasAnyRole("ANALYST", "INVESTIGATOR", "ADMIN")
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jsonEntryPoint(objectMapper, HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED", "Valid credentials are required."))
                        .accessDeniedHandler((exchange, denied) -> {
                            LoggerFactory.getLogger(SecurityConfig.class).debug(
                                    "Access denied {} {}",
                                    exchange.getRequest().getMethod(),
                                    exchange.getRequest().getPath().value());
                            return writeJson(exchange, objectMapper, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                                    "You do not have permission to access this resource.");
                        }))
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(internalApiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private ServerAuthenticationEntryPoint jsonEntryPoint(ObjectMapper objectMapper,
                                                          HttpStatus status, String code, String message) {
        return (exchange, e) -> writeJson(exchange, objectMapper, status, code, message);
    }

    /** Writes a stable {@code {code,message}} JSON envelope. Stack traces,
     *  internal details and credentials are never rendered to clients. */
    static Mono<Void> writeJson(ServerWebExchange exchange, ObjectMapper objectMapper,
                                HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of("code", code, "message", message));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(props.corsAllowedOrigins()));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Internal-Api-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}