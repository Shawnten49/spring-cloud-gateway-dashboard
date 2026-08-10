package com.gatewaydashboard.gateway;

import com.gatewaydashboard.gateway.GatewayStatusDtos.GatewayStatusResponse;
import com.gatewaydashboard.route.RouteAssembler;
import com.gatewaydashboard.route.RouteConfigRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GatewayStatusService {

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final RouteConfigRepository routeConfigRepository;
    private final RouteAssembler routeAssembler;
    private final RefreshTimestampListener refreshTimestampListener;

    public GatewayStatusService(@Qualifier("routeDefinitionLocator") RouteDefinitionLocator routeDefinitionLocator,
                                RouteConfigRepository routeConfigRepository,
                                RouteAssembler routeAssembler,
                                RefreshTimestampListener refreshTimestampListener) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.routeConfigRepository = routeConfigRepository;
        this.routeAssembler = routeAssembler;
        this.refreshTimestampListener = refreshTimestampListener;
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
        return routeDefinitionLocator.getRouteDefinitions()
                .map(routeAssembler::toResponse)
                .collectList()
                .map(routes -> new GatewayStatusResponse(
                        healthValue,
                        refreshTimestampListener.getLastRefreshAt(),
                        routes));
    }
}
