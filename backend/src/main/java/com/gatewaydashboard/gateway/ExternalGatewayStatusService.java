package com.gatewaydashboard.gateway;

import com.gatewaydashboard.config.ExternalGatewayProperties;
import com.gatewaydashboard.config.ExternalGatewayProperties.Gateway;
import com.gatewaydashboard.config.ExternalGatewayRefreshService;
import com.gatewaydashboard.config.ExternalGatewayRefreshService.PushRecord;
import com.gatewaydashboard.gateway.GatewayStatusDtos.ExternalGatewayStatus;
import com.gatewaydashboard.gateway.GatewayStatusDtos.ExternalRoutesResponse;
import com.gatewaydashboard.gateway.GatewayStatusDtos.PushInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合外部网关实例的状态：在线与否、生效路由、最近一次推送结果。
 */
@Slf4j
@Service
public class ExternalGatewayStatusService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final ExternalGatewayProperties properties;
    private final ExternalGatewayRefreshService refreshService;
    private final WebClient webClient;

    public ExternalGatewayStatusService(ExternalGatewayProperties properties,
                                        ExternalGatewayRefreshService refreshService,
                                        WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.refreshService = refreshService;
        this.webClient = webClientBuilder.build();
    }

    public Mono<List<ExternalGatewayStatus>> fetchAll() {
        List<Gateway> gateways = properties.getExternalGateways();
        if (gateways.isEmpty()) {
            return Mono.just(List.of());
        }
        List<Mono<ExternalGatewayStatus>> monos = gateways.stream().map(this::fetchOne).toList();
        return Mono.zip(monos, array -> {
            List<ExternalGatewayStatus> result = new ArrayList<>(array.length);
            for (Object item : array) {
                result.add((ExternalGatewayStatus) item);
            }
            return result;
        });
    }

    private Mono<ExternalGatewayStatus> fetchOne(Gateway gateway) {
        String baseUrl = gateway.getBaseUrl();
        String url = baseUrl.endsWith("/") ? baseUrl + "internal/routes" : baseUrl + "/internal/routes";
        return webClient.get()
                .uri(url)
                .header("X-Internal-Token", gateway.getToken())
                .retrieve()
                .bodyToMono(ExternalRoutesResponse.class)
                .timeout(REQUEST_TIMEOUT)
                .map(response -> new ExternalGatewayStatus(
                        baseUrl,
                        true,
                        toPushInfo(refreshService.getPushRecord(baseUrl)),
                        Instant.now(),
                        response.data() == null ? List.of() : response.data(),
                        null))
                .onErrorResume(error -> Mono.just(new ExternalGatewayStatus(
                        baseUrl,
                        false,
                        toPushInfo(refreshService.getPushRecord(baseUrl)),
                        Instant.now(),
                        List.of(),
                        error.getMessage())));
    }

    private PushInfo toPushInfo(PushRecord record) {
        return record == null ? null : new PushInfo(record.lastPushAt(), record.success(), record.error());
    }
}
