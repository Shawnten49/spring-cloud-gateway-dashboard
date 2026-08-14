package com.gatewaydashboard.auth;

import com.gatewaydashboard.auth.AuthDtos.ChangePasswordRequest;
import com.gatewaydashboard.auth.AuthDtos.LoginRequest;
import com.gatewaydashboard.auth.AuthDtos.LoginResponse;
import com.gatewaydashboard.auth.AuthDtos.UserSummary;
import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.common.BlockingSupport;
import com.gatewaydashboard.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                  ServerWebExchange exchange) {
        return BlockingSupport.call(() ->
                ApiResponse.ok(authService.login(request, SecurityUtils.clientIp(exchange))));
    }

    @GetMapping("/me")
    public Mono<ApiResponse<UserSummary>> me() {
        return SecurityUtils.currentUsername()
                .flatMap(username -> BlockingSupport.call(() -> ApiResponse.ok(authService.me(username))));
    }

    @PutMapping("/password")
    public Mono<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return SecurityUtils.currentUsername()
                .flatMap(username -> BlockingSupport.call(() -> {
                    authService.changePassword(username, request);
                    return ApiResponse.<Void>ok();
                }));
    }
}
