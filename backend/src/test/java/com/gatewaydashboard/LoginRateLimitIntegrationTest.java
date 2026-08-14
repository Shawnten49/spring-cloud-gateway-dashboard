package com.gatewaydashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 登录限流回归测试（Phase 2 优化 2.2，Bucket4j 令牌桶）：
 * 独立上下文覆盖为小容量（capacity=3），连续失败第 4 次起返回 429 统一 JSON。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "gateway-dashboard.security.login-rate-limit.capacity=3",
        "gateway-dashboard.security.login-rate-limit.refill-per-minute=1"
})
class LoginRateLimitIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void exceedingLimitReturns429() {
        // 前 3 次：凭据错误 → 401（限流未触发）
        for (int i = 0; i < 3; i++) {
            webTestClient.post().uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"admin\",\"password\":\"wrong-password\"}")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        // 第 4 次：令牌耗尽 → 429 统一 JSON（即使凭据正确也拒绝）
        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\",\"password\":\"admin123\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectBody()
                .jsonPath("$.code").isEqualTo(429)
                .jsonPath("$.message").isNotEmpty();
    }
}
