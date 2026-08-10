package com.gatewaydashboard.gateway;

import com.gatewaydashboard.gateway.GatewayStatusDtos.GatewayStatusResponse;
import com.gatewaydashboard.route.RouteAssembler;
import com.gatewaydashboard.route.RouteConfigRepository;
import com.gatewaydashboard.gateway.GatewayStatusDtos.ExternalGatewayStatus;
import com.gatewaydashboard.route.RouteDto.RouteResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GatewayStatusService {

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final RouteConfigRepository routeConfigRepository;
    private final RouteAssembler routeAssembler;
    private final RefreshTimestampListener refreshTimestampListener;
    private final ExternalGatewayStatusService externalGatewayStatusService;

    public GatewayStatusService(@Qualifier("routeDefinitionLocator") RouteDefinitionLocator routeDefinitionLocator,
                                RouteConfigRepository routeConfigRepository,
                                RouteAssembler routeAssembler,
                                RefreshTimestampListener refreshTimestampListener,
                                ExternalGatewayStatusService externalGatewayStatusService) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.routeConfigRepository = routeConfigRepository;
        this.routeAssembler = routeAssembler;
        this.refreshTimestampListener = refreshTimestampListener;
        this.externalGatewayStatusService = externalGatewayStatusService;
    }

    public Mono<GatewayStatusResponse> status() {
        String health = "DOWN";
        try {
            routeConfigRepository.count();
            health = "UP";
        } catch (Exception ignored) {
            // 数据库不可用时标记 DOWN
        }
        final String healthValue = health;
        Mono<List<RouteResponse>> embeddedRoutes = routeDefinitionLocator.getRouteDefinitions()
                .map(routeAssembler::toResponse)
                .collectList();
        Mono<List<ExternalGatewayStatus>> externalGateways = externalGatewayStatusService.fetchAll();
        return Mono.zip(embeddedRoutes, externalGateways, (routes, externals) -> new GatewayStatusResponse(
                healthValue,
                refreshTimestampListener.getLastRefreshAt(),
                routes,
                externals));
    }
}
