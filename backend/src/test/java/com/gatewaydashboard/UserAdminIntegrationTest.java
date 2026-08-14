package com.gatewaydashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

/**
 * 用户管理模块集成测试（需求 FR1–FR5 / 设计文档 §5.1）：
 * ADMIN 增/屏蔽/启用全流程 + 吊销、admin 特殊保护、VIEWER 403、无删除接口 405、
 * 校验（重复/密码/角色/enabled）、响应不含密码哈希。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserAdminIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void adminFullLifecycle() {
        String adminToken = login("admin", "admin123");
        String username = "it-user-" + System.nanoTime();

        // 新增用户
        webTestClient.post().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"%s","password":"password123","role":"VIEWER"}
                        """.formatted(username))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.username").isEqualTo(username)
                .jsonPath("$.data.role").isEqualTo("VIEWER")
                .jsonPath("$.data.enabled").isEqualTo(true)
                .jsonPath("$.data.passwordHash").doesNotExist();

        // 列表可见 + 响应不含密码哈希
        webTestClient.get().uri("/api/users?keyword={kw}", username)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.username=='%s')]".formatted(username)).exists()
                .jsonPath("$.data[0].passwordHash").doesNotExist();

        // 新用户可登录
        String userToken = login(username, "password123");

        // 屏蔽 → 登录 401 + 旧 token 立即吊销（S-04）
        Long userId = userIdOf(adminToken, username);
        webTestClient.put().uri("/api/users/{id}/enabled", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.enabled").isEqualTo(false);

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"%s","password":"password123"}
                        """.formatted(username))
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                .exchange()
                .expectStatus().isUnauthorized();

        // 启用 → 可重新登录
        webTestClient.put().uri("/api/users/{id}/enabled", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.enabled").isEqualTo(true);

        login(username, "password123");
    }

    @Test
    void adminSpecialUserCannotBeBlocked() {
        String adminToken = login("admin", "admin123");
        Long adminId = userIdOf(adminToken, "admin");

        webTestClient.put().uri("/api/users/{id}/enabled", adminId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("admin 为特殊用户，不允许屏蔽");
    }

    @Test
    void viewerIsForbiddenOnAllEndpoints() {
        String viewerToken = login("viewer", "viewer123");

        webTestClient.get().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.post().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"x\",\"password\":\"password123\",\"role\":\"VIEWER\"}")
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.put().uri("/api/users/1/enabled")
                .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteEndpointDoesNotExist() {
        String adminToken = login("admin", "admin123");
        // 未提供删除接口：路径无任何 handler → 统一 JSON 404（接口不存在）
        webTestClient.delete().uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo(404);
    }

    @Test
    void validationRules() {
        String adminToken = login("admin", "admin123");

        // 用户名重复 → 409
        webTestClient.post().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"viewer\",\"password\":\"password123\",\"role\":\"VIEWER\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        // 密码 < 8 → 400
        webTestClient.post().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"short-pass\",\"password\":\"short7x\",\"role\":\"VIEWER\"}")
                .exchange()
                .expectStatus().isBadRequest();

        // 非法角色 → 400
        webTestClient.post().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"bad-role\",\"password\":\"password123\",\"role\":\"SUPER\"}")
                .exchange()
                .expectStatus().isBadRequest();

        // 空 enabled 体 → 400
        webTestClient.put().uri("/api/users/1/enabled")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();

        // 用户不存在 → 404
        webTestClient.put().uri("/api/users/999999/enabled")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @SuppressWarnings("unchecked")
    private Long userIdOf(String token, String username) {
        Map<String, Object> body = webTestClient.get().uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        var users = (java.util.List<Map<String, Object>>) body.get("data");
        return users.stream()
                .filter(u -> username.equals(u.get("username")))
                .map(u -> ((Number) u.get("id")).longValue())
                .findFirst()
                .orElseThrow();
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
