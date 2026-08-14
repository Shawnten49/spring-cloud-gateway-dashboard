package com.gatewaydashboard.config;

import com.gatewaydashboard.route.ConfigRevision;
import com.gatewaydashboard.route.ConfigRevisionRepository;
import com.gatewaydashboard.route.RouteRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 内嵌网关多实例兜底（评审 F5）：定期比较全局修订号（F13 水印），
 * 发生变化即触发本地 RefreshRoutesEvent。
 *
 * - 本实例写路径（RouteService afterCommit）已即时刷新并调用 {@link #markRefreshed()}，
 *   轮询只兜底"他实例/直连数据库"造成的变更，避免同一次变更重复刷新；
 * - 与 gateway-demo 的 RouteSyncScheduler 同构（synchronized + lastSeen/lastRefreshed 双标记）；
 * - 可配置：gateway-dashboard.route-sync.enabled（默认 true）与 poll-interval-ms（默认 5000）。
 */
@Slf4j
@Component
public class EmbeddedGatewaySyncScheduler {

    private final ConfigRevisionRepository configRevisionRepository;
    private final RouteRefreshService refreshService;
    private final boolean enabled;

    private volatile long lastSeenRevision = -1;
    private volatile long lastRefreshedRevision = -1;

    public EmbeddedGatewaySyncScheduler(ConfigRevisionRepository configRevisionRepository,
                                        RouteRefreshService refreshService,
                                        @Value("${gateway-dashboard.route-sync.enabled:true}") boolean enabled) {
        this.configRevisionRepository = configRevisionRepository;
        this.refreshService = refreshService;
        this.enabled = enabled;
    }

    @PostConstruct
    public void init() {
        long current = currentRevision();
        lastSeenRevision = current;
        lastRefreshedRevision = current;
    }

    @Scheduled(fixedDelayString = "${gateway-dashboard.route-sync.poll-interval-ms:5000}")
    public synchronized void poll() {
        if (!enabled) {
            return;
        }
        long current = currentRevision();
        if (current < 0 || current == lastSeenRevision) {
            return;
        }
        lastSeenRevision = current;
        // 该变更可能已由本地写路径的即时刷新处理，避免重复刷新
        if (current != lastRefreshedRevision) {
            lastRefreshedRevision = current;
            refreshService.refresh();
            log.info("检测到路由配置变化（revision={}），已触发内嵌网关刷新", current);
        }
    }

    /**
     * 本地写路径刷新后调用：把当前修订号标记为"已刷新"，
     * 使轮询不会为同一次变更重复触发；后续修订号再次变化，轮询仍会兜底。
     */
    public synchronized void markRefreshed() {
        long current = currentRevision();
        if (current >= 0) {
            lastSeenRevision = current;
            lastRefreshedRevision = current;
        }
    }

    private long currentRevision() {
        try {
            return configRevisionRepository.findById(1)
                    .map(ConfigRevision::getRevision)
                    .orElse(-1L);
        } catch (Exception e) {
            log.warn("读取路由配置修订号失败: {}", e.getMessage());
            return -1L;
        }
    }
}
