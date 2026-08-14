package com.gatewaydashboard.config;

import com.gatewaydashboard.GatewayDashboardApplication;
import com.gatewaydashboard.route.DbRouteDefinitionLocator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 外部网关实例列表（Route A 集成：网关工程内置 DbRouteDefinitionLocator + 刷新接口）。
 * 仪表盘每次保存路由后，会向这些网关推送刷新。
 *
 * record 构造器绑定（Spring Boot 3 对 @ConfigurationProperties record 自动启用）；
 * 通过 GatewayDashboardApplication 上的 @ConfigurationPropertiesScan 注册。
 */
@ConfigurationProperties(prefix = "gateway-dashboard")
public record ExternalGatewayProperties(List<Gateway> externalGateways) {

    public ExternalGatewayProperties {
        externalGateways = externalGateways == null ? List.of() : List.copyOf(externalGateways);
    }

    public record Gateway(String baseUrl, String token) {
    }
}
