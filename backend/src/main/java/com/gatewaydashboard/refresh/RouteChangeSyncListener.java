package com.gatewaydashboard.refresh;

import com.gatewaydashboard.refresh.service.ExternalGatewayRefreshService;

import com.gatewaydashboard.route.RouteChangedEvent;
import com.gatewaydashboard.route.service.RouteRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 路由变更同步编排（事件驱动，route 包只发布事件、不依赖本包）：
 * 真源写入提交后依次执行——本地生效路由刷新（含预热）、内嵌网关轮询标记（避免重复刷新）、
 * 外部网关推送。顺序固定，保证"保存即生效"语义。
 */
@Component
@RequiredArgsConstructor
public class RouteChangeSyncListener {

    private final RouteRefreshService refreshService;
    private final EmbeddedGatewaySyncScheduler embeddedGatewaySyncScheduler;
    private final ExternalGatewayRefreshService externalGatewayRefreshService;

    @EventListener
    public void onRouteChanged(RouteChangedEvent event) {
        refreshService.refresh();
        embeddedGatewaySyncScheduler.markRefreshed();
        externalGatewayRefreshService.refreshAll();
    }
}
