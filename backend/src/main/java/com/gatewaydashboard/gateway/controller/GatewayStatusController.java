package com.gatewaydashboard.gateway.controller;

import com.gatewaydashboard.gateway.service.GatewayStatusService;

import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.gateway.dto.GatewayStatusDtos.GatewayStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
public class GatewayStatusController {

    private final GatewayStatusService gatewayStatusService;

    @GetMapping("/status")
    public Mono<ApiResponse<GatewayStatusResponse>> status() {
        return gatewayStatusService.status().map(ApiResponse::ok);
    }
}
