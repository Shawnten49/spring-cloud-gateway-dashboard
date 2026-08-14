package com.gatewaydashboard.route;

import com.gatewaydashboard.common.BlockingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbRouteDefinitionLocator implements RouteDefinitionLocator {

    private final RouteConfigRepository repository;
    private final RouteAssembler assembler;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        // 阻塞 JPA 查询卸到 boundedElastic，避免在 Netty 事件循环上执行 JDBC。
        // 单行配置损坏（非法 JSON 等）只跳过该行并记录 ERROR：不能让一条坏数据
        // 冻结全部生效路由（刷新失败 → 状态页 500、流量路由停滞）；管理端列表/详情接口
        // 仍会暴露该行错误，便于管理员定位修复。
        return BlockingSupport.call(repository::findAllByEnabledTrueOrderByOrderNoAscIdAsc)
                .flatMapMany(Flux::fromIterable)
                .map(assembler::toDefinition)
                .onErrorContinue((error, row) ->
                        log.error("跳过无法解析的路由行 routeId={}（{}）", ((RouteConfig) row).getRouteId(), error.getMessage()));
    }
}
