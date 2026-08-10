package com.gatewaydashboard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路由保存成功后，向配置的外部网关推送刷新（尽力而为，失败不影响保存结果；
 * 网关侧有版本轮询兜底，最多延迟一个轮询周期生效）。
 */
@Slf4j
@Service
public class ExternalGatewayRefreshService {

    private final ExternalGatewayProperties properties;
    private final WebClient webClient;
    private final Map<String, PushRecord> pushRecords = new ConcurrentHashMap<>();

    public record PushRecord(Instant lastPushAt, boolean success, String error) {
    }

    public ExternalGatewayRefreshService(ExternalGatewayProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    public void refreshAll() {
        for (ExternalGatewayProperties.Gateway gateway : properties.getExternalGateways()) {
            String baseUrl = gateway.getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                continue;
            }
            String url = baseUrl.endsWith("/") ? baseUrl + "internal/routes/refresh" : baseUrl + "/internal/routes/refresh";
            webClient.post()
                    .uri(url)
                    .header("X-Internal-Token", gateway.getToken())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            body -> {
                                pushRecords.put(baseUrl, new PushRecord(Instant.now(), true, null));
                                log.info("外部网关 {} 路由刷新成功", baseUrl);
                            },
                            error -> {
                                pushRecords.put(baseUrl, new PushRecord(Instant.now(), false, error.getMessage()));
                                log.warn("外部网关 {} 路由刷新失败: {}", baseUrl, error.getMessage());
                            });
        }
    }

    public PushRecord getPushRecord(String baseUrl) {
        return pushRecords.get(baseUrl);
    }
}
