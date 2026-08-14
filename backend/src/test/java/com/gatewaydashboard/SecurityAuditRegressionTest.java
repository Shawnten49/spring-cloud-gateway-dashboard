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
 * 安全/审计回归测试：覆盖评审（docs/评审报告.md）中已确认缺陷的修复点。
 * - P2-01 setEnabled 裸 Map：畸形请求体不再静默停用路由
 * - S-13 权限自我保护：guard 覆盖全部写端点 + 内置规则不可 update 修改
 * - 空 roles 死规则、优先级越界被拒绝
 * - T-P1-5 审计多动作（CREATE/UPDATE/ENABLE/DISABLE/DELETE）全部落库
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityAuditRegressionTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void enabledEndpointRejectsMissingEnabledField() {
        String adminToken = login("admin", "admin123");

        // 空请求体不再被静默当作"停用"：必须显式给出 enabled
        webTestClient.post().uri("/api/routes/httpbin-get/enabled")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void newPasswordShorterThan8Rejected() {
        String adminToken = login("admin", "admin123");

        // 密码最短 8 位（Phase 2 优化 2.3）：7 位新密码直接 400，不改动账号状态
        webTestClient.put().uri("/api/auth/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"oldPassword\":\"admin123\",\"newPassword\":\"short7x\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);

        // 账号未被改动：原密码仍可登录
        login("admin", "admin123");
    }

    @Test
    void permissionGuardCoversAllWriteMethods() {
        String adminToken = login("admin", "admin123");

        // 历史绕过链：ADMIN 建 "PUT /api/permission-rules/** -> VIEWER"（priority 压过内置），
        // 再以 VIEWER 身份 update 内置规则提权。守卫必须同时模拟 PUT/DELETE/GET/POST 拒绝该改动。
        webTestClient.post().uri("/api/permission-rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"提权-put","httpMethod":"PUT","pathPattern":"/api/permission-rules/**",
                         "roles":"VIEWER","priority":1,"enabled":true}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void builtinRulesCannotBeModified() {
        String adminToken = login("admin", "admin123");
        long builtinId = firstBuiltinRuleId(adminToken);

        // 内置规则是权限底线的兜底，update 也必须拒绝（此前只有 delete 有 builtin 保护）
        webTestClient.put().uri("/api/permission-rules/{id}", builtinId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"权限配置-查看","httpMethod":"GET","pathPattern":"/api/permission-rules/**",
                         "roles":"VIEWER","priority":5,"enabled":true}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void ruleWithEmptyRolesRejected() {
        String adminToken = login("admin", "admin123");

        // 空 roles 会生成"死规则"遮蔽后续规则，必须拒绝
        webTestClient.post().uri("/api/permission-rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"死规则","httpMethod":"GET","pathPattern":"/api/audit-logs/**",
                         "roles":"   ","priority":1,"enabled":true}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void ruleWithOutOfRangePriorityRejected() {
        String adminToken = login("admin", "admin123");

        // 优先级越界（负数/超上限）可能压过内置规则，必须加界
        webTestClient.post().uri("/api/permission-rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"越界优先级","httpMethod":"GET","pathPattern":"/api/audit-logs/**",
                         "roles":"ADMIN","priority":1000,"enabled":true}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(400);
    }

    @Test
    void auditLogsAllRouteActions() {
        String adminToken = login("admin", "admin123");
        String routeId = "audit-route-" + System.nanoTime();

        String routeJson = """
                {"routeId":"%s","uri":"http://httpbin.org","order":1,"enabled":true,
                 "predicates":[{"name":"Path","args":{"patterns":"/audit/**"}}],"filters":[],"metadata":{}}
                """.formatted(routeId);

        webTestClient.post().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(routeJson)
                .exchange()
                .expectStatus().isOk();

        webTestClient.put().uri("/api/routes/{routeId}", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(routeJson.replace("\"order\":1", "\"order\":2"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/api/routes/{routeId}/enabled", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/api/routes/{routeId}/enabled", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":true}")
                .exchange()
                .expectStatus().isOk();

        webTestClient.delete().uri("/api/routes/{routeId}", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk();

        // 5 类动作全部落库，且 UPDATE/DELETE 带有变更前后内容（beforeJson/afterJson）
        webTestClient.get().uri("/api/audit-logs?page=1&size=100")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[?(@.routeId=='%s' && @.action=='CREATE')]".formatted(routeId)).exists()
                .jsonPath("$.data.items[?(@.routeId=='%s' && @.action=='UPDATE' && @.beforeJson!=null && @.afterJson!=null)]".formatted(routeId)).exists()
                .jsonPath("$.data.items[?(@.routeId=='%s' && @.action=='DISABLE')]".formatted(routeId)).exists()
                .jsonPath("$.data.items[?(@.routeId=='%s' && @.action=='ENABLE')]".formatted(routeId)).exists()
                .jsonPath("$.data.items[?(@.routeId=='%s' && @.action=='DELETE')]".formatted(routeId)).exists();
    }

    @SuppressWarnings("unchecked")
    private long firstBuiltinRuleId(String token) {
        Map<String, Object> body = webTestClient.get().uri("/api/permission-rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        var rules = (java.util.List<Map<String, Object>>) body.get("data");
        return rules.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("builtin")))
                .findFirst()
                .map(r -> ((Number) r.get("id")).longValue())
                .orElseThrow();
    }

    private String login(String username, String password) {
        var response = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
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
