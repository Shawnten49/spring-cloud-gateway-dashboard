package com.gatewaydashboard.route;

/**
 * 路由配置已变更领域事件：RouteService 在任何真源写操作提交后发布。
 *
 * 由 refresh 包的路由变更同步监听器消费（本地生效路由刷新 + 内嵌网关轮询标记 + 外部网关推送），
 * 使 route 包不再反向依赖刷新/推送实现，打破包级循环依赖。
 */
public record RouteChangedEvent() {
}
