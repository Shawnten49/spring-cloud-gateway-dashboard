package com.gatewaydashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

/**
 * S-04 token 吊销集成测试：改密后该用户此前签发的全部 token 立即失效。
 * 用例末尾恢复原密码，保证共享测试库（H2）中后续用例可继续用 admin123 登录。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TokenRevocationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void changePasswordRevokesPreviouslyIssuedTokens() {
        String oldToken = login("admin", "admin123");

        // 改密前旧 token 可用
        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(oldToken))
                .exchange()
                .expectStatus().isOk();

        // 改密（使用旧 token）
        webTestClient.put().uri("/api/auth/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(oldToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"oldPassword\":\"admin123\",\"newPassword\":\"admin123-new\"}")
                .exchange()
                .expectStatus().isOk();

        // 旧 token 立即失效（S-04 核心断言）
        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(oldToken))
                .exchange()
                .expectStatus().isUnauthorized();

        // 新密码可登录，新 token 可用
        String newToken = login("admin", "admin123-new");
        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(newToken))
                .exchange()
                .expectStatus().isOk();

        // 恢复原密码（保证后续用例稳定）
        webTestClient.put().uri("/api/auth/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(newToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"oldPassword\":\"admin123-new\",\"newPassword\":\"admin123\"}")
                .exchange()
                .expectStatus().isOk();

        // 恢复后 newToken 同样失效；原密码可再次登录
        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(newToken))
                .exchange()
                .expectStatus().isUnauthorized();

        String restoredToken = login("admin", "admin123");
        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(restoredToken))
                .exchange()
                .expectStatus().isOk();
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
