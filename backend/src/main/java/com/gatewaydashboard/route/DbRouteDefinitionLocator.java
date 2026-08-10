package com.gatewaydashboard.route;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class DbRouteDefinitionLocator implements RouteDefinitionLocator {

    private final RouteConfigRepository repository;
    private final RouteAssembler assembler;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(repository.findAllByEnabledTrueOrderByOrderNoAscIdAsc())
                .map(assembler::toDefinition);
    }
}
