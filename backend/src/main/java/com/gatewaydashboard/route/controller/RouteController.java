package com.gatewaydashboard.route.controller;

import com.gatewaydashboard.route.service.RouteService;

import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.common.BlockingSupport;
import com.gatewaydashboard.common.SecurityUtils;
import com.gatewaydashboard.route.dto.RouteDto.EnabledRequest;
import com.gatewaydashboard.route.dto.RouteDto.RouteRequest;
import com.gatewaydashboard.route.dto.RouteDto.RouteResponse;
import com.gatewaydashboard.route.dto.RouteDto.ValidationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    public Mono<ApiResponse<List<RouteResponse>>> list(@RequestParam(required = false) String keyword) {
        return BlockingSupport.call(() -> ApiResponse.ok(routeService.list(keyword)));
    }

    @GetMapping("/{routeId}")
    public Mono<ApiResponse<RouteResponse>> get(@PathVariable String routeId) {
        return BlockingSupport.call(() -> ApiResponse.ok(routeService.get(routeId)));
    }

    @PostMapping
    public Mono<ApiResponse<RouteResponse>> create(@Valid @RequestBody RouteRequest request,
                                                   ServerWebExchange exchange) {
        return SecurityUtils.currentUsername()
                .flatMap(actor -> BlockingSupport.call(() ->
                        ApiResponse.ok(routeService.create(request, actor, SecurityUtils.clientIp(exchange)))));
    }

    @PutMapping("/{routeId}")
    public Mono<ApiResponse<RouteResponse>> update(@PathVariable String routeId,
                                                   @Valid @RequestBody RouteRequest request,
                                                   ServerWebExchange exchange) {
        return SecurityUtils.currentUsername()
                .flatMap(actor -> BlockingSupport.call(() ->
                        ApiResponse.ok(routeService.update(routeId, request, actor, SecurityUtils.clientIp(exchange)))));
    }

    @DeleteMapping("/{routeId}")
    public Mono<ApiResponse<Void>> delete(@PathVariable String routeId, ServerWebExchange exchange) {
        return SecurityUtils.currentUsername()
                .flatMap(actor -> BlockingSupport.call(() -> {
                    routeService.delete(routeId, actor, SecurityUtils.clientIp(exchange));
                    return ApiResponse.<Void>ok();
                }));
    }

    @PostMapping("/{routeId}/enabled")
    public Mono<ApiResponse<RouteResponse>> setEnabled(@PathVariable String routeId,
                                                       @Valid @RequestBody EnabledRequest body,
                                                       ServerWebExchange exchange) {
        return SecurityUtils.currentUsername()
                .flatMap(actor -> BlockingSupport.call(() ->
                        ApiResponse.ok(routeService.setEnabled(routeId, body.enabled(), actor, SecurityUtils.clientIp(exchange)))));
    }

    @PostMapping("/validate")
    public Mono<ApiResponse<ValidationResponse>> validate(@RequestBody RouteRequest request) {
        return BlockingSupport.call(() -> ApiResponse.ok(routeService.validateOnly(request)));
    }
}
