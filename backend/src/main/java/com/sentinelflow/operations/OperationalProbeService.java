package com.sentinelflow.operations;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.gateway.AiGateway;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Lightweight dependency probes for the operational summary. Results are
 * time-cached (default 5s) so a terminal frontend page never spikes the
 * dependencies. Probes are informational: they never flip core liveness or
 * readiness, and they never write to any dependency.
 */
@Component
public class OperationalProbeService {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<String> kafkaBootstrapServers;
    private final boolean kafkaEnabled;
    private final String mlServiceUrl;
    private final WebClient webClient;
    private final ObjectProvider<AiGateway> aiGateway;
    private final AiProperties aiProperties;
    private final long probeTtlMillis;
    private final Map<String, CachedProbe<?>> cache = new ConcurrentHashMap<>();

    public OperationalProbeService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("kafkaBootstrapServers") ObjectProvider<String> kafkaBootstrapServers,
            @Value("${sentinelflow.kafka.enabled:true}") boolean kafkaEnabled,
            @Value("${ml.service.url:http://localhost:8001}") String mlServiceUrl,
            WebClient.Builder webClientBuilder,
            ObjectProvider<AiGateway> aiGateway,
            AiProperties aiProperties,
            @Value("${sentinelflow.operations.probe-ttl-ms:5000}") long probeTtlMillis) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.kafkaEnabled = kafkaEnabled;
        this.mlServiceUrl = mlServiceUrl;
        this.webClient = webClientBuilder.build();
        this.aiGateway = aiGateway;
        this.aiProperties = aiProperties;
        this.probeTtlMillis = probeTtlMillis;
    }

    public DependencyProbeResult postgres() {
        return cached("postgres", () -> {
            try {
                Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                return one != null && one == 1
                        ? DependencyProbeResult.healthy("SELECT 1 ok")
                        : DependencyProbeResult.degraded("SELECT 1 returned unexpected value");
            } catch (Exception e) {
                return DependencyProbeResult.unavailable(e.getClass().getSimpleName());
            }
        });
    }

    public DependencyProbeResult kafka() {
        if (!kafkaEnabled) {
            return DependencyProbeResult.disabled("sentinelflow.kafka.enabled=false");
        }
        return cached("kafka", () -> {
            String bootstrap = kafkaBootstrapServers.getIfAvailable();
            if (bootstrap == null || bootstrap.isBlank()) {
                return DependencyProbeResult.unavailable("no bootstrap servers configured");
            }
            Map<String, Object> config = Map.of(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                    AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) PROBE_TIMEOUT.toMillis());
            try (AdminClient admin = AdminClient.create(config)) {
                var nodes = admin.describeCluster().nodes().get(PROBE_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                return DependencyProbeResult.healthy("cluster reachable, " + nodes.size() + " brokers");
            } catch (Exception e) {
                return DependencyProbeResult.unavailable(e.getClass().getSimpleName());
            }
        });
    }

    public DependencyProbeResult ml() {
        return cached("ml", () -> {
            try {
                Map<?, ?> body = webClient.get()
                        .uri(mlServiceUrl + "/health")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(PROBE_TIMEOUT)
                        .block();
                if (body == null) {
                    return DependencyProbeResult.degraded("empty /health payload");
                }
                boolean modelLoaded = Boolean.TRUE.equals(body.get("model_loaded"));
                return modelLoaded
                        ? DependencyProbeResult.healthy("model loaded")
                        : DependencyProbeResult.degraded("model not loaded");
            } catch (Exception e) {
                return DependencyProbeResult.unavailable(e.getClass().getSimpleName());
            }
        });
    }

    public DependencyProbeResult ai() {
        if (!aiProperties.isEnabled()) {
            return DependencyProbeResult.disabled("sentinelflow.ai.enabled=false");
        }
        AiGateway gateway = aiGateway.getIfAvailable();
        return gateway != null
                ? DependencyProbeResult.healthy("gateway configured")
                : DependencyProbeResult.unavailable("gateway bean not configured");
    }

    @SuppressWarnings("unchecked")
    private DependencyProbeResult cached(String key, java.util.function.Supplier<DependencyProbeResult> probe) {
        CachedProbe<DependencyProbeResult> cached = (CachedProbe<DependencyProbeResult>) cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMillis < probeTtlMillis) {
            return cached.value;
        }
        DependencyProbeResult fresh = probe.get();
        cache.put(key, new CachedProbe<>(fresh, now));
        return fresh;
    }

    private record CachedProbe<T>(T value, long atMillis) {
    }
}