package com.example.gatewaydemo.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 网关内部管理接口（供 Gateway Dashboard 推送刷新 / 查看生效路由）。
 * 生产环境请将 internal-token 改为强随机值，并限制为内网访问。
 */
@RestController
@RequestMapping("/internal/routes")
public class RouteRefreshController {

    private static final Logger log = LoggerFactory.getLogger(RouteRefreshController.class);

    private final RouteRefreshPublisher refreshPublisher;
    private final RouteSyncProperties properties;
    private final RouteLocator cachedRouteLocator;
    private final RouteSyncScheduler routeSyncScheduler;

    public RouteRefreshController(RouteRefreshPublisher refreshPublisher,
                                  RouteSyncProperties properties,
                                  @Qualifier("cachedCompositeRouteLocator") RouteLocator cachedRouteLocator,
                                  RouteSyncScheduler routeSyncScheduler) {
        this.refreshPublisher = refreshPublisher;
        this.properties = properties;
        this.cachedRouteLocator = cachedRouteLocator;
        this.routeSyncScheduler = routeSyncScheduler;
    }

    @PostMapping("/refresh")
    public Mono<Map<String, Object>> refresh(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            ServerWebExchange exchange) {
        requireToken(token);
        log.info("收到外部通知刷新请求（来源 {}），触发路由刷新", clientIp(exchange));
        refreshPublisher.refresh();
        routeSyncScheduler.markRefreshed();
        return Mono.just(Map.of("code", 200, "message", "ok"));
    }

    @GetMapping
    public Mono<Map<String, Object>> effective(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireToken(token);
        // 展示缓存中"真正生效"的路由（CachingRouteLocator），与仪表盘状态页语义一致
        return cachedRouteLocator.getRoutes()
                .map(RouteRefreshController::toSummary)
                .collectList()
                .map(routes -> Map.of("code", 200, "message", "ok", "data", routes));
    }

    /**
     * 校验内部 token：恒定时长比较；配置缺失在启动时已被 RouteSyncProperties 拦截（fail-closed）。
     */
    private void requireToken(String token) {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.getInternalToken().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }

    private static Map<String, Object> toSummary(Route route) {
        return Map.of(
                "routeId", route.getId(),
                "uri", route.getUri() == null ? "" : route.getUri().toString(),
                "order", route.getOrder());
    }

    private static String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
