package com.gatewaydashboard.route;

import com.gatewaydashboard.common.BlockingSupport;
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
        // 阻塞 JPA 查询卸到 boundedElastic，避免在 Netty 事件循环上执行 JDBC。
        return BlockingSupport.call(repository::findAllByEnabledTrueOrderByOrderNoAscIdAsc)
                .flatMapMany(Flux::fromIterable)
                .map(assembler::toDefinition);
    }
}
