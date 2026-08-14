package com.gatewaydashboard.route;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RouteDto {

    private RouteDto() {
    }

    /** routeId 合法字符集（DTO 校验、RouteValidator、数据库长度三处共用同一事实源）。 */
    public static final String ROUTE_ID_PATTERN = "[A-Za-z0-9_.-]{1,128}";

    public record RouteRequest(
            @NotBlank(message = "路由 ID 不能为空")
            @Pattern(regexp = ROUTE_ID_PATTERN, message = "只能包含字母、数字、点、下划线、连字符")
            String routeId,
            @NotBlank(message = "目标地址不能为空")
            String uri,
            Integer order,
            Boolean enabled,
            List<Step> predicates,
            List<Step> filters,
            Map<String, Object> metadata) {
    }

    /**
     * 启用/停用请求体：必须显式给出 enabled，避免畸形请求体（如空对象）被静默当作停用处理。
     */
    public record EnabledRequest(@NotNull(message = "enabled 不能为空") Boolean enabled) {
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
