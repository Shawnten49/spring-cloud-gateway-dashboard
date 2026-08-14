package com.gatewaydashboard;

import com.gatewaydashboard.route.ConfigRevisionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F13 单调 revision 水印集成测试：每次真源写入（创建/更新/停用/启用/删除）
 * 全局修订号严格 +1；同状态停用短路不产生写、不 bump。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RevisionWatermarkIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ConfigRevisionMapper revisionMapper;

    @Test
    void revisionIncrementsOnEveryRouteWrite() {
        String adminToken = login("admin", "admin123");
        String routeId = "rev-route-" + System.nanoTime();
        long start = revision();

        // create -> +1
        webTestClient.post().uri("/api/routes")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(routeJson(routeId, true))
                .exchange()
                .expectStatus().isOk();
        assertEquals(start + 1, revision(), "创建路由后修订号 +1");

        // update -> +1
        webTestClient.put().uri("/api/routes/{routeId}", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(routeJson(routeId, true).replace("\"order\":1", "\"order\":2"))
                .exchange()
                .expectStatus().isOk();
        assertEquals(start + 2, revision(), "更新路由后修订号 +1");

        // disable -> +1
        webTestClient.post().uri("/api/routes/{routeId}/enabled", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange()
                .expectStatus().isOk();
        assertEquals(start + 3, revision(), "停用路由后修订号 +1");

        // enable -> +1
        webTestClient.post().uri("/api/routes/{routeId}/enabled", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":true}")
                .exchange()
                .expectStatus().isOk();
        assertEquals(start + 4, revision(), "启用路由后修订号 +1");

        // delete -> +1
        webTestClient.delete().uri("/api/routes/{routeId}", routeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk();
        assertEquals(start + 5, revision(), "删除路由后修订号 +1");
    }

    @Test
    void sameStateEnableShortCircuitsWithoutBump() {
        String adminToken = login("admin", "admin123");
        long before = revision();

        // httpbin-get 种子路由已是启用状态：同状态停用/启用不产生写，修订号不变
        webTestClient.post().uri("/api/routes/httpbin-get/enabled")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":true}")
                .exchange()
                .expectStatus().isOk();

        assertEquals(before, revision(), "同状态启用短路不应 bump 修订号");
    }

    private long revision() {
        return revisionMapper.selectById(1).getRevision();
    }

    private String routeJson(String routeId, boolean enabled) {
        return """
                {"routeId":"%s","uri":"http://httpbin.org","order":1,"enabled":%s,
                 "predicates":[{"name":"Path","args":{"patterns":"/rev/**"}}],"filters":[],"metadata":{}}
                """.formatted(routeId, enabled);
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
