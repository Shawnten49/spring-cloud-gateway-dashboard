package com.gatewaydashboard.auth.dto;

import com.gatewaydashboard.auth.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 用户管理 DTO（需求文档 FR1–FR3 / 设计文档 §2）。
 * 注意：所有响应 DTO 均不包含 passwordHash（安全要求）。
 */
public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(max = 64, message = "用户名最长 64 字符")
            String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, message = "密码长度至少 8 位")
            String password,
            @NotBlank(message = "角色不能为空")
            @Pattern(regexp = "ADMIN|VIEWER", message = "角色仅支持 ADMIN 或 VIEWER")
            String role) {
    }

    /** 屏蔽/启用请求体：必须显式给出 enabled（空体/缺字段返回 400）。 */
    public record EnabledRequest(@NotNull(message = "enabled 不能为空") Boolean enabled) {
    }

    public record UserResponse(Long id, String username, String role, boolean enabled,
                               Instant createdAt, Instant updatedAt) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    user.isEnabled(),
                    user.getCreatedAt(),
                    user.getUpdatedAt());
        }
    }
}
