package com.gatewaydashboard.gateway;

import com.gatewaydashboard.common.BlockingSupport;
import com.gatewaydashboard.gateway.GatewayStatusDtos.ExternalGatewayStatus;
import com.gatewaydashboard.gateway.GatewayStatusDtos.GatewayStatusResponse;
import com.gatewaydashboard.route.RouteAssembler;
import com.gatewaydashboard.route.RouteConfigMapper;
import com.gatewaydashboard.route.RouteDto.RouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网关状态聚合。"生效路由"取自 CachingRouteLocator（当前在内存中真正生效的路由集合），
 * 而非直接读数据库，保证与"保存即生效"的产品语义一致（评审 F25）；
 * predicates/filters 的结构化内容按 routeId 从路由定义（真源）匹配填充，
 * 因为运行态 Route 只保留解析后的谓词闭包、无法还原为结构化 Step。
 */
@Slf4j
@Service
public class GatewayStatusService {

    private final RouteLocator cachedRouteLocator;
    private final RouteDefinitionLocator routeDefinitionLocator;
    private final RouteConfigMapper routeConfigMapper;
    private final RouteAssembler routeAssembler;
    private final RefreshTimestampListener refreshTimestampListener;
    private final ExternalGatewayStatusService externalGatewayStatusService;

    public GatewayStatusService(@Qualifier("cachedCompositeRouteLocator") RouteLocator cachedRouteLocator,
                                @Qualifier("routeDefinitionLocator") RouteDefinitionLocator routeDefinitionLocator,
                                RouteConfigMapper routeConfigMapper,
                                RouteAssembler routeAssembler,
                                RefreshTimestampListener refreshTimestampListener,
                                ExternalGatewayStatusService externalGatewayStatusService) {
        this.cachedRouteLocator = cachedRouteLocator;
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.routeConfigMapper = routeConfigMapper;
        this.routeAssembler = routeAssembler;
        this.refreshTimestampListener = refreshTimestampListener;
        this.externalGatewayStatusService = externalGatewayStatusService;
    }

    public Mono<GatewayStatusResponse> status() {
        // 健康探针也是阻塞 JPA：卸到 boundedElastic，并记录失败原因而非静默吞掉。
        Mono<String> health = BlockingSupport.call(() -> {
            routeConfigMapper.countAll();
            return "UP";
        }).onErrorResume(error -> {
            log.warn("网关健康检查失败（数据库不可用）: {}", error.getMessage());
            return Mono.just("DOWN");
        });
        Mono<Map<String, RouteDefinition>> definitions = routeDefinitionLocator.getRouteDefinitions()
                .collect(Collectors.toMap(RouteDefinition::getId, d -> d, (a, b) -> a));
        Mono<List<RouteResponse>> embeddedRoutes = Mono.zip(cachedRouteLocator.getRoutes().collectList(), definitions,
                (routes, defs) -> routes.stream()
                        .map(route -> routeAssembler.toResponse(route, defs.get(route.getId())))
                        .toList());
        Mono<List<ExternalGatewayStatus>> externalGateways = externalGatewayStatusService.fetchAll();
        return Mono.zip(health, embeddedRoutes, externalGateways)
                .map(tuple -> new GatewayStatusResponse(
                        tuple.getT1(),
                        refreshTimestampListener.getLastRefreshAt(),
                        tuple.getT2(),
                        tuple.getT3()));
    }
}
