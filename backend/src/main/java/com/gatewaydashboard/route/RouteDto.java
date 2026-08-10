package com.gatewaydashboard.route;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RouteDto {

    private RouteDto() {
    }

    public record RouteRequest(
            @NotBlank(message = "路由 ID 不能为空")
            @Pattern(regexp = "[A-Za-z0-9_.-]{1,128}", message = "只能包含字母、数字、点、下划线、连字符")
            String routeId,
            @NotBlank(message = "目标地址不能为空")
            String uri,
            Integer order,
            Boolean enabled,
            List<Step> predicates,
            List<Step> filters,
            Map<String, Object> metadata) {
    }

    public record Step(String name, Map<String, Object> args) {
    }

    public record RouteResponse(String routeId, String uri, int order, boolean enabled,
                                List<Step> predicates, List<Step> filters,
                                Map<String, Object> metadata, long version, Instant updatedAt) {
    }

    public record ValidationResponse(boolean valid, List<String> errors) {
    }
}
