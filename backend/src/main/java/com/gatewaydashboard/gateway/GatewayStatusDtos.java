package com.gatewaydashboard.gateway;

import com.gatewaydashboard.route.RouteDto.RouteResponse;

import java.time.Instant;
import java.util.List;

public final class GatewayStatusDtos {

    private GatewayStatusDtos() {
    }

    public record GatewayStatusResponse(String health, Instant lastRefreshAt, List<RouteResponse> effectiveRoutes) {
    }
}
