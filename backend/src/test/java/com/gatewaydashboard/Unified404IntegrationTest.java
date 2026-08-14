package com.gatewaydashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 404 统一 JSON 回归测试（Phase 2 优化 2.1）：
 * 未匹配路径返回统一 ApiResponse 信封（code=404），且 OPTIONS 预检不受影响。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Unified404IntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void unknownPathReturnsUnifiedJson404() {
        String token = login();
        webTestClient.get().uri("/api/no-such-endpoint")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("接口不存在");
    }

    @Test
    void unknownActuatorPathReturnsUnifiedJson404() {
        String token = login();
        webTestClient.get().uri("/actuator/not-exposed")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(404);
    }

    @Test
    void anonymousUnknownPathStillUnauthorized() {
        // 未认证请求在任何路径都会被安全层拒绝（fail-closed 设计），不会走到 404
        webTestClient.get().uri("/api/no-such-endpoint")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(401);
    }

    @Test
    void corsPreflightStillWorks() {
        // 预检请求不应被 404 处理器误伤：应返回 CORS 头
        webTestClient.options().uri("/api/routes")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void knownApiPathStillReturnsUnifiedJson200() {
        String token = login();
        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200);
    }

    private String login() {
        var response = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\",\"password\":\"admin123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.Map.class)
                .returnResult()
                .getResponseBody();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
        return (String) data.get("token");
    }
}
