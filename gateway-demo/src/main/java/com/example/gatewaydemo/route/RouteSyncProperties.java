package com.example.gatewaydemo.route;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内部接口（/internal/routes/**）配置。
 * 安全语义（fail-closed）：token 为空时直接拒绝启动，杜绝"未配置 token = 接口公开"的默认放行。
 */
@Component
@ConfigurationProperties(prefix = "gateway-dashboard.route-sync")
public class RouteSyncProperties {

    private long pollIntervalMs = 5000;
    private String internalToken = "";

    @PostConstruct
    public void validate() {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("gateway-dashboard.route-sync.internal-token 未配置："
                    + "内部管理接口拒绝在无 token 的情况下启动，请设置强随机 token（如 openssl rand -hex 32）");
        }
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}
