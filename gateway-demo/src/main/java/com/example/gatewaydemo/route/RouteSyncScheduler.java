package com.example.gatewaydemo.route;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 兜底同步：定期比较 config_revision 单行表的全局修订号（F13 水印），
 * 发生变化即触发路由刷新。即使仪表盘的主动推送失败，路由也会在数秒内生效。
 * 修订号由仪表盘 RouteService 每次真源写入在同一事务内原子 +1，严格单调，无碰撞漏检
 * （替代原 (COUNT, SUM(version)) 校验和方案）。
 */
@Component
public class RouteSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(RouteSyncScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final RouteRefreshPublisher refreshPublisher;
    private final RouteSyncProperties properties;

    private volatile String lastRevision = "";
    private volatile String lastRefreshedRevision = "";

    public RouteSyncScheduler(JdbcTemplate jdbcTemplate,
                              RouteRefreshPublisher refreshPublisher,
                              RouteSyncProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.refreshPublisher = refreshPublisher;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        lastRevision = currentRevision();
        lastRefreshedRevision = lastRevision;
    }

    @Scheduled(fixedDelayString = "${gateway-dashboard.route-sync.poll-interval-ms:5000}")
    public synchronized void poll() {
        String current = currentRevision();
        if (current != null && !current.equals(lastRevision)) {
            lastRevision = current;
            // 该变更可能已由主动推送（/internal/routes/refresh）处理，避免重复刷新
            if (!current.equals(lastRefreshedRevision)) {
                lastRefreshedRevision = current;
                refreshPublisher.refresh();
                log.info("检测到路由配置变化（revision={}），已触发刷新", current);
            }
        }
    }

    /**
     * 主动推送刷新后调用：把当前修订号标记为"已刷新"，
     * 使轮询不会为同一次变更重复触发；后续修订号再次变化，轮询仍会兜底。
     * synchronized 与 poll() 互斥，避免"推送标记"与"轮询判断"之间的竞态导致重复刷新。
     */
    public synchronized void markRefreshed() {
        String current = currentRevision();
        if (current != null) {
            lastRevision = current;
            lastRefreshedRevision = current;
        }
    }

    private String currentRevision() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT revision FROM config_revision WHERE id = 1",
                    String.class);
        } catch (Exception e) {
            log.warn("读取路由配置修订号失败: {}", e.getMessage());
            return null;
        }
    }
}
