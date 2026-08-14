package com.gatewaydashboard.gateway;

import com.gatewaydashboard.config.ExternalGatewayProperties;
import com.gatewaydashboard.config.ExternalGatewayProperties.Gateway;
import com.gatewaydashboard.gateway.GatewayStatusDtos.ExternalGatewayStatus;
import com.gatewaydashboard.gateway.GatewayStatusDtos.ExternalRoutesResponse;
import com.gatewaydashboard.gateway.GatewayStatusDtos.PushInfo;
import com.gatewaydashboard.refresh.ExternalGatewayRefreshService;
import com.gatewaydashboard.refresh.ExternalGatewayRefreshService.PushRecord;
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
        List<Gateway> gateways = properties.externalGateways().stream()
                .filter(g -> g.baseUrl() != null && !g.baseUrl().isBlank())
                .toList();
        if (gateways.isEmpty()) {
            return Mono.just(List.of());
        }
        List<Mono<ExternalGatewayStatus>> monos = gateways.stream().map(this::fetchOne).toList();
        return zipAll(monos);
    }

    /**
     * 并发聚合并按输入顺序返回（Mono.zip 的 Object[] 回调不可避免一次受检强转，
     * 收敛到唯一一处并标注，调用侧保持类型安全）。
     */
    @SuppressWarnings("unchecked")
    private static <T> Mono<List<T>> zipAll(List<Mono<T>> monos) {
        return Mono.zip(monos, array -> {
            List<T> result = new ArrayList<>(array.length);
            for (Object item : array) {
                result.add((T) item);
            }
            return result;
        });
    }

    private Mono<ExternalGatewayStatus> fetchOne(Gateway gateway) {
        String baseUrl = gateway.baseUrl();
        String url = baseUrl.endsWith("/") ? baseUrl + "internal/routes" : baseUrl + "/internal/routes";
        return webClient.get()
                .uri(url)
                .header("X-Internal-Token", gateway.token())
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
