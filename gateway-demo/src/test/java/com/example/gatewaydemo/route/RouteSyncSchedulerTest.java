package com.example.gatewaydemo.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 轮询兜底同步回归测试（评审 F12）：校验和变化触发刷新、主动推送后不再重复刷新、
 * 校验和再次变化仍会兜底触发。
 */
class RouteSyncSchedulerTest {

    private JdbcTemplate jdbcTemplate;
    private RouteRefreshPublisher refreshPublisher;
    private RouteSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        refreshPublisher = mock(RouteRefreshPublisher.class);
        RouteSyncProperties properties = new RouteSyncProperties();
        scheduler = new RouteSyncScheduler(jdbcTemplate, refreshPublisher, properties);
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Class<String>>any()))
                .thenReturn("2:100");
        scheduler.init();
    }

    @Test
    void checksumChangeTriggersRefreshExactlyOnce() {
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Class<String>>any()))
                .thenReturn("3:150");

        scheduler.poll();
        scheduler.poll();

        verify(refreshPublisher, times(1)).refresh();
    }

    @Test
    void markRefreshedSuppressesDuplicateRefreshForSameChange() {
        // 推送已处理该变更：markRefreshed 后轮询不应重复刷新
        scheduler.markRefreshed();
        scheduler.poll();
        scheduler.poll();

        verify(refreshPublisher, never()).refresh();
    }

    @Test
    void subsequentChangeStillTriggersFallback() {
        // 第一次变更被推送处理
        scheduler.markRefreshed();
        scheduler.poll();
        verify(refreshPublisher, never()).refresh();

        // 新的变更到来，轮询兜底必须触发
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Class<String>>any()))
                .thenReturn("4:200");
        scheduler.poll();

        verify(refreshPublisher, times(1)).refresh();
    }
}
