package com.gatewaydashboard.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 8, message = "新密码长度至少 8 位") String newPassword) {
    }

    public record UserSummary(String username, String role) {
        public static UserSummary from(User user) {
            return new UserSummary(user.getUsername(), user.getRole());
        }
    }

    public record LoginResponse(String token, UserSummary user) {
    }
}
