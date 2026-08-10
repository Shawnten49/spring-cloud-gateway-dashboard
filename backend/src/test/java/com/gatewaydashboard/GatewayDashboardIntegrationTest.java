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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayDashboardIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void loginFailsWithWrongPassword() {
        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\",\"password\":\"wrong-password\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() {
        webTestClient.get().uri("/api/routes")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void fullRouteLifecycle() {
        String adminToken = login("admin", "admin123");
        String viewerToken = login("viewer", "viewer123");
        String routeId = "it-route-" + System.nanoTime();

        // 种子路由可见
        webTestClient.get().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[?(@.routeId=='httpbin-get')]").exists();

        // 创建路由
        String createJson = """
                {"routeId":"%s","uri":"http://httpbin.org","order":5,"enabled":true,
                 "predicates":[{"name":"Path","args":{"patterns":"/test/**"}}],
                 "filters":[{"name":"AddRequestHeader","args":{"name":"X-Demo","value":"1"}}],
                 "metadata":{"owner":"it"}}
                """.formatted(routeId);
        webTestClient.post().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createJson)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.routeId").isEqualTo(routeId);

        // 网关状态包含新路由（保存即生效）
        webTestClient.get().uri("/api/gateway/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.health").isEqualTo("UP")
                .jsonPath("$.data.effectiveRoutes[?(@.routeId=='%s')]".formatted(routeId)).exists();

        // 停用后不再生效，但配置仍在
        webTestClient.post().uri("/api/routes/{routeId}/enabled", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.enabled").isEqualTo(false);

        webTestClient.get().uri("/api/gateway/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.effectiveRoutes[?(@.routeId=='%s')]".formatted(routeId)).doesNotExist();

        webTestClient.get().uri("/api/routes/{routeId}", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.enabled").isEqualTo(false);

        // 重复创建冲突
        webTestClient.post().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        // 校验接口：未知工厂名不通过
        webTestClient.post().uri("/api/routes/validate")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"routeId":"validate-route","uri":"http://httpbin.org","enabled":true,
                         "predicates":[{"name":"NoSuchFactory","args":{}}],"filters":[],"metadata":{}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.valid").isEqualTo(false);

        // 只读用户不能写
        webTestClient.post().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"routeId":"viewer-route","uri":"http://httpbin.org","enabled":true,
                         "predicates":[{"name":"Path","args":{"patterns":"/x/**"}}],"filters":[],"metadata":{}}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        // 审计日志包含 CREATE 记录
        webTestClient.get().uri("/api/audit-logs?page=1&size=100")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[?(@.routeId=='%s' && @.action=='CREATE')]".formatted(routeId)).exists();
    }

    private String login(String username, String password) {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        var response = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
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
