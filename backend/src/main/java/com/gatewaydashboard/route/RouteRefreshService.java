package com.gatewaydashboard.route;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteRefreshService {

    private static final Duration WARMUP_TIMEOUT = Duration.ofSeconds(3);

    private final ApplicationEventPublisher eventPublisher;
    private final @Qualifier("cachedCompositeRouteLocator") RouteLocator cachedRouteLocator;

    public void refresh() {
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        // 预热：刷新事件只是失效缓存（CachingRouteLocator 惰性重建），若重建中的一次读取
        // 恰与失效交错，可能把过期快照重新写回缓存（reactor-cache CacheFlux 完成时无条件 put）。
        // 在返回前同步等一次重建完成，保证"保存即生效"对下一个读者确定可见。
        try {
            cachedRouteLocator.getRoutes().collectList().block(WARMUP_TIMEOUT);
        } catch (Exception e) {
            // 预热失败不影响已发布的刷新事件：下一次读取仍会触发重建
            log.warn("生效路由预热失败（不影响刷新事件）: {}", e.getMessage());
        }
    }
}
