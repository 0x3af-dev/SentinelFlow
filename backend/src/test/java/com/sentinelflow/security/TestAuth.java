package com.sentinelflow.security;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Authenticates WebTestClient requests against the real, stateless JWT flow:
 * logs in as a seeded demo user, extracts the token, and re-issues the client
 * with the Bearer header. This never bypasses security — it exercises the exact
 * production authentication path (POST /api/auth/login → BCrypt → JWT → filter).
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static String token(WebTestClient web, String username, String password) {
        byte[] body = web.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty()
                .returnResult()
                .getResponseBodyContent();
        if (body == null) {
            throw new IllegalStateException("Login response body missing for " + username);
        }
        String json = new String(body, StandardCharsets.UTF_8);
        String marker = "\"token\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        if (start < marker.length() || end < 0) {
            throw new IllegalStateException("Could not parse token from login response for " + username);
        }
        return json.substring(start, end);
    }

    public static String analyst(WebTestClient web) {
        return token(web, "analyst", "analyst-demo");
    }

    public static String operator(WebTestClient web) {
        return token(web, "operator", "operator-demo");
    }

    public static String admin(WebTestClient web) {
        return token(web, "admin", "admin-demo");
    }

    public static String investigator(WebTestClient web) {
        return token(web, "investigator", "investigator-demo");
    }

    public static WebTestClient as(WebTestClient web, String username, String password) {
        return withToken(web, token(web, username, password));
    }

    public static WebTestClient asAnalyst(WebTestClient web) {
        return withToken(web, analyst(web));
    }

    public static WebTestClient asInvestigator(WebTestClient web) {
        return withToken(web, investigator(web));
    }

    public static WebTestClient asOperator(WebTestClient web) {
        return withToken(web, operator(web));
    }

    public static WebTestClient asAdmin(WebTestClient web) {
        return withToken(web, admin(web));
    }

    public static WebTestClient withToken(WebTestClient web, String token) {
        return web.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }
}