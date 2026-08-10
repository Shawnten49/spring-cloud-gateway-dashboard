package com.gatewaydashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExternalGatewayStatusIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void statusIncludesExternalGatewayOfflineState() {
        String token = login("admin", "admin123");

        webTestClient.get().uri("/api/gateway/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.health").isEqualTo("UP")
                .jsonPath("$.data.effectiveRoutes").isArray()
                .jsonPath("$.data.externalGateways.length()").isEqualTo(1)
                .jsonPath("$.data.externalGateways[0].baseUrl").isEqualTo("http://localhost:19999")
                .jsonPath("$.data.externalGateways[0].online").isEqualTo(false)
                .jsonPath("$.data.externalGateways[0].effectiveRoutes.length()").isEqualTo(0)
                .jsonPath("$.data.externalGateways[0].error").isNotEmpty();
    }

    private String login(String username, String password) {
        var response = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return (String) data.get("token");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
