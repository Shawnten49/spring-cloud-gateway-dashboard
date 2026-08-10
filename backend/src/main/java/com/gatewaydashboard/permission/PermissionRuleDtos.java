package com.gatewaydashboard.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public final class PermissionRuleDtos {

    private PermissionRuleDtos() {
    }

    public record RuleRequest(
            @NotBlank(message = "规则名称不能为空") String name,
            @NotBlank(message = "HTTP 方法不能为空")
            @Pattern(regexp = "\\*|GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD", message = "方法仅支持 GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD/*")
            String httpMethod,
            @NotBlank(message = "路径不能为空") String pathPattern,
            @NotBlank(message = "角色不能为空") String roles,
            Integer priority,
            Boolean enabled) {
    }

    public record RuleResponse(Long id, String name, String httpMethod, String pathPattern,
                               String roles, int priority, boolean enabled, boolean builtin,
                               Instant createdAt, Instant updatedAt) {

        public static RuleResponse from(PermissionRule rule) {
            return new RuleResponse(
                    rule.getId(),
                    rule.getName(),
                    rule.getHttpMethod(),
                    rule.getPathPattern(),
                    rule.getRoles(),
                    rule.getPriority(),
                    rule.isEnabled(),
                    rule.isBuiltin(),
                    rule.getCreatedAt(),
                    rule.getUpdatedAt());
        }
    }
}
