package com.gatewaydashboard.gateway;

import com.gatewaydashboard.route.RouteDto.RouteResponse;

import java.time.Instant;
import java.util.List;

public final class GatewayStatusDtos {

    private GatewayStatusDtos() {
    }

    public record GatewayStatusResponse(String health, Instant lastRefreshAt,
                                        List<RouteResponse> effectiveRoutes,
                                        List<ExternalGatewayStatus> externalGateways) {
    }

    public record RouteSummary(String routeId, String uri, int order) {
    }

    public record PushInfo(Instant lastPushAt, boolean success, String error) {
    }

    public record ExternalGatewayStatus(String baseUrl, boolean online, PushInfo push,
                                        Instant lastCheckedAt, List<RouteSummary> effectiveRoutes,
                                        String error) {
    }

    /**
     * 外部网关 /internal/routes 接口的响应体。
     */
    public record ExternalRoutesResponse(int code, String message, List<RouteSummary> data) {
    }
}
