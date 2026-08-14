package com.example.gatewaydemo.route;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 兜底同步：定期比较 route_config 表的（行数, 最大版本号），
 * 发生变化即触发路由刷新。即使仪表盘的主动推送失败，路由也会在数秒内生效。
 */
@Component
public class RouteSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(RouteSyncScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final RouteRefreshPublisher refreshPublisher;
    private final RouteSyncProperties properties;

    private volatile String lastChecksum = "";
    private volatile String lastRefreshedChecksum = "";

    public RouteSyncScheduler(JdbcTemplate jdbcTemplate,
                              RouteRefreshPublisher refreshPublisher,
                              RouteSyncProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.refreshPublisher = refreshPublisher;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        lastChecksum = currentChecksum();
        lastRefreshedChecksum = lastChecksum;
    }

    @Scheduled(fixedDelayString = "${gateway-dashboard.route-sync.poll-interval-ms:5000}")
    public synchronized void poll() {
        String current = currentChecksum();
        if (current != null && !current.equals(lastChecksum)) {
            lastChecksum = current;
            // 该变更可能已由主动推送（/internal/routes/refresh）处理，避免重复刷新
            if (!current.equals(lastRefreshedChecksum)) {
                lastRefreshedChecksum = current;
                refreshPublisher.refresh();
                log.info("检测到路由配置变化（{}），已触发刷新", current);
            }
        }
    }

    /**
     * 主动推送刷新后调用：把当前数据库校验和标记为"已刷新"，
     * 使轮询不会为同一次变更重复触发；后续若校验和再次变化，轮询仍会兜底。
     * synchronized 与 poll() 互斥，避免"推送标记"与"轮询判断"之间的竞态导致重复刷新。
     */
    public synchronized void markRefreshed() {
        String current = currentChecksum();
        if (current != null) {
            lastChecksum = current;
            lastRefreshedChecksum = current;
        }
    }

    private String currentChecksum() {
        try {
            // 行数覆盖新建/删除；版本号总和覆盖任何一行的更新（每次更新 version 必然 +1）。
            // 不能用 MAX(version)：更新非最大版本号的行时 MAX 不变，会漏掉变更。
            return jdbcTemplate.queryForObject(
                    "SELECT CONCAT(COUNT(*), ':', COALESCE(SUM(version), 0)) FROM route_config",
                    String.class);
        } catch (Exception e) {
            log.warn("读取路由配置版本失败: {}", e.getMessage());
            return null;
        }
    }
}
