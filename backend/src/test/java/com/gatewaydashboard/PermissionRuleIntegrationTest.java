package com.gatewaydashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PermissionRuleIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @SuppressWarnings("unchecked")
    void permissionRulesAreDynamicAndProtected() {
        String adminToken = login("admin", "admin123");
        String viewerToken = login("viewer", "viewer123");

        // 1. viewer 无权查看权限规则
        webTestClient.get().uri("/api/permission-rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .exchange()
                .expectStatus().isForbidden();

        // 2. admin 可查看内置规则
        Map<String, Object> rulesBody = webTestClient.get().uri("/api/permission-rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        List<Map<String, Object>> rules = (List<Map<String, Object>>) rulesBody.get("data");
        assertTrue(rules.size() >= 11, "应包含内置规则");
        Map<String, Object> builtinRule = rules.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("builtin")))
                .findFirst()
                .orElseThrow();

        // 3. 新增规则：临时允许 VIEWER 创建路由（优先级高于内置 ADMIN 规则）
        Map<String, Object> created = postRule(adminToken, """
                {"name":"测试-viewer可建路由","httpMethod":"POST","pathPattern":"/api/routes",
                 "roles":"ADMIN,VIEWER","priority":1,"enabled":true}
                """);
        assertNotNull(created.get("id"));
        long tempRuleId = ((Number) created.get("id")).longValue();

        // 4. 规则修改后即时生效：viewer 现在可以创建路由
        String routeId1 = "perm-route-" + System.nanoTime();
        webTestClient.post().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(routeJson(routeId1))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.routeId").isEqualTo(routeId1);

        // 5. 删除规则后即时生效：viewer 又被拒绝
        webTestClient.delete().uri("/api/permission-rules/{id}", tempRuleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk();

        String routeId2 = "perm-route-" + System.nanoTime();
        webTestClient.post().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(routeJson(routeId2))
                .exchange()
                .expectStatus().isForbidden();

        // 6. 内置规则不可删除
        long builtinId = ((Number) builtinRule.get("id")).longValue();
        webTestClient.delete().uri("/api/permission-rules/{id}", builtinId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isBadRequest();

        // 7. 自我保护：新建会导致 ADMIN 失去权限配置访问权的规则被拒绝
        Map<String, Object> guard = postRuleExpect(adminToken, """
                {"name":"锁死","httpMethod":"POST","pathPattern":"/api/permission-rules/**",
                 "roles":"VIEWER","priority":1,"enabled":true}
                """, 400);
        assertEquals(400, guard.get("code"));

        // 8. 自我保护：把内置的权限配置写规则改成仅 VIEWER 也被拒绝
        Map<String, Object> guard2 = putRuleExpect(adminToken, builtinId, """
                {"name":"权限配置-新增","httpMethod":"POST","pathPattern":"/api/permission-rules/**",
                 "roles":"VIEWER","priority":15,"enabled":true}
                """, 400);
        assertEquals(400, guard2.get("code"));
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

    private Map<String, Object> postRule(String token, String json) {
        Map<String, Object> body = postRuleExpect(token, json, 200);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return data;
    }

    private Map<String, Object> postRuleExpect(String token, String json, int status) {
        return webTestClient.post().uri("/api/permission-rules")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.valueOf(status))
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private Map<String, Object> putRuleExpect(String token, long id, String json, int status) {
        return webTestClient.put().uri("/api/permission-rules/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.valueOf(status))
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private String routeJson(String routeId) {
        return """
                {"routeId":"%s","uri":"http://httpbin.org","order":1,"enabled":true,
                 "predicates":[{"name":"Path","args":{"patterns":"/perm/**"}}],"filters":[],"metadata":{}}
                """.formatted(routeId);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
