package com.gatewaydashboard.route.controller;

import com.gatewaydashboard.route.service.RouteValidator;

import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.common.BlockingSupport;
import com.gatewaydashboard.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class RouteMetaController {

    private final RouteValidator routeValidator;

    @GetMapping("/factories")
    public Mono<ApiResponse<Map<String, List<String>>>> factories(@RequestParam String type) {
        List<String> names = switch (type) {
            case "predicate" -> List.copyOf(routeValidator.predicateFactoryNames());
            case "filter" -> List.copyOf(routeValidator.filterFactoryNames());
            default -> throw BusinessException.badRequest("type 仅支持 predicate 或 filter");
        };
        return BlockingSupport.call(() -> ApiResponse.ok(Map.of(type, names)));
    }
}
