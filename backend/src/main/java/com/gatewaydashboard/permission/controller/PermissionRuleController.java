package com.gatewaydashboard.permission.controller;

import com.gatewaydashboard.permission.service.PermissionRuleService;

import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.common.BlockingSupport;
import com.gatewaydashboard.permission.dto.PermissionRuleDtos.RuleRequest;
import com.gatewaydashboard.permission.dto.PermissionRuleDtos.RuleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/permission-rules")
@RequiredArgsConstructor
public class PermissionRuleController {

    private final PermissionRuleService permissionRuleService;

    @GetMapping
    public Mono<ApiResponse<List<RuleResponse>>> list() {
        return BlockingSupport.call(() -> ApiResponse.ok(permissionRuleService.list()));
    }

    @PostMapping
    public Mono<ApiResponse<RuleResponse>> create(@Valid @RequestBody RuleRequest request) {
        return BlockingSupport.call(() -> ApiResponse.ok(permissionRuleService.create(request)));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<RuleResponse>> update(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        return BlockingSupport.call(() -> ApiResponse.ok(permissionRuleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable Long id) {
        return BlockingSupport.call(() -> {
            permissionRuleService.delete(id);
            return ApiResponse.<Void>ok();
        });
    }
}
