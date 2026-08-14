package com.example.gatewaydemo.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内部管理接口安全回归测试（评审 F15 / S-05）：
 * - token 缺失/错误 → 401（此前"未配置 token 即放行"的默认放行已修复为 fail-closed）
 * - token 正确 → 触发刷新并标记校验和
 */
class RouteRefreshControllerTest {

    private static final String VALID_TOKEN = "test-internal-token";

    private RouteRefreshPublisher refreshPublisher;
    private RouteSyncScheduler routeSyncScheduler;
    private RouteLocator routeLocator;
    private RouteRefreshController controller;

    @BeforeEach
    void setUp() {
        refreshPublisher = mock(RouteRefreshPublisher.class);
        routeSyncScheduler = mock(RouteSyncScheduler.class);
        routeLocator = mock(RouteLocator.class);
        when(routeLocator.getRoutes()).thenReturn(reactor.core.publisher.Flux.empty());

        RouteSyncProperties properties = new RouteSyncProperties();
        properties.setInternalToken(VALID_TOKEN);
        controller = new RouteRefreshController(
                refreshPublisher, properties, routeLocator, routeSyncScheduler);
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/internal/routes/refresh"));
    }

    @Test
    void missingTokenIsRejected() {
        assertThrows(ResponseStatusException.class, () -> controller.refresh(null, exchange()).block(),
                "缺失 token 必须 401（fail-closed）");
    }

    @Test
    void wrongTokenIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.refresh("wrong-token", exchange()).block());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(refreshPublisher, never()).refresh();
    }

    @Test
    void validTokenTriggersRefreshAndMark() {
        Map<String, Object> body = controller.refresh(VALID_TOKEN, exchange()).block();
        assertEquals(200, body.get("code"));
        verify(refreshPublisher).refresh();
        verify(routeSyncScheduler).markRefreshed();
    }

    @Test
    void effectiveRoutesRequireValidToken() {
        assertThrows(ResponseStatusException.class,
                () -> controller.effective(null).block());
        assertThrows(ResponseStatusException.class,
                () -> controller.effective("wrong-token").block());

        Map<String, Object> body = controller.effective(VALID_TOKEN).block();
        assertEquals(200, body.get("code"));
        assertEquals(java.util.List.of(), body.get("data"));
    }
}
