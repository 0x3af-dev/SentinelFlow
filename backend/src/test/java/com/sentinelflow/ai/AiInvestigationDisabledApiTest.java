package com.sentinelflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinelflow.ai.gateway.AiGateway;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * With the default configuration (sentinelflow.ai.enabled=false) the AI
 * investigator fails fast with a controlled 503/AI_UNAVAILABLE and the
 * application still boots with no OpenAI beans, no API key, and no LLM on any
 * production path.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@Testcontainers
class AiInvestigationDisabledApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    ObjectProvider<AiGateway> gatewayProvider;

    @Test
    void explanationRequestWhenDisabledReturns503AiUnavailable() {
        webTestClient.post()
                .uri("/api/investigations/{id}/explanations", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"requestType\":\"SUMMARIZE\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("AI_UNAVAILABLE");
    }

    @Test
    void disabledContextRegistersNoGatewayBean() {
        assertThat(gatewayProvider.getIfAvailable()).isNull();
    }

    @Test
    void invalidRequestBodyReturns400() {
        webTestClient.post()
                .uri("/api/investigations/{id}/explanations", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"freeFormQuestion\":\"surplus field\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }
}