package com.gatewaydashboard.auth.controller;

import com.gatewaydashboard.auth.dto.UserAdminDtos.CreateUserRequest;
import com.gatewaydashboard.auth.dto.UserAdminDtos.EnabledRequest;
import com.gatewaydashboard.auth.dto.UserAdminDtos.UserResponse;
import com.gatewaydashboard.auth.service.UserAdminService;
import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.common.BlockingSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 用户管理接口（仅 ADMIN，权限由 permission_rule 动态规则控制）。
 * 需求 FR1–FR5：查看/新增/屏蔽/启用；不提供删除接口。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public Mono<ApiResponse<List<UserResponse>>> list(@RequestParam(required = false) String keyword) {
        return BlockingSupport.call(() -> ApiResponse.ok(userAdminService.list(keyword)));
    }

    @PostMapping
    public Mono<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return BlockingSupport.call(() -> ApiResponse.ok(userAdminService.create(request)));
    }

    @PutMapping("/{id}/enabled")
    public Mono<ApiResponse<UserResponse>> setEnabled(@PathVariable Long id,
                                                      @Valid @RequestBody EnabledRequest body) {
        return BlockingSupport.call(() -> ApiResponse.ok(userAdminService.setEnabled(id, body.enabled())));
    }
}
