package com.gatewaydashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部网关实例列表（Route A 集成：网关工程内置 DbRouteDefinitionLocator + 刷新接口）。
 * 仪表盘每次保存路由后，会向这些网关推送刷新。
 */
@Component
@ConfigurationProperties(prefix = "gateway-dashboard")
public class ExternalGatewayProperties {

    private List<Gateway> externalGateways = new ArrayList<>();

    public List<Gateway> getExternalGateways() {
        return externalGateways;
    }

    public void setExternalGateways(List<Gateway> externalGateways) {
        this.externalGateways = externalGateways;
    }

    public static class Gateway {

        private String baseUrl;
        private String token;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
