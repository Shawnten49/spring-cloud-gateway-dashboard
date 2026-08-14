package com.gatewaydashboard.refresh;

import com.gatewaydashboard.route.ConfigRevision;
import com.gatewaydashboard.route.ConfigRevisionMapper;
import com.gatewaydashboard.route.RouteRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内嵌网关轮询兜底（F5）逻辑回归测试：revision 变化触发刷新一次、
 * markRefreshed 抑制重复、后续变化兜底触发、enabled=false 不轮询。
 */
class EmbeddedGatewaySyncSchedulerTest {

    private ConfigRevisionMapper repository;
    private RouteRefreshService refreshService;

    @BeforeEach
    void setUp() {
        repository = mock(ConfigRevisionMapper.class);
        refreshService = mock(RouteRefreshService.class);
        when(repository.selectById(1)).thenReturn(revision(5L));
    }

    private EmbeddedGatewaySyncScheduler scheduler(boolean enabled) {
        EmbeddedGatewaySyncScheduler scheduler =
                new EmbeddedGatewaySyncScheduler(repository, refreshService, enabled);
        scheduler.init();
        return scheduler;
    }

    private ConfigRevision revision(long value) {
        ConfigRevision revision = new ConfigRevision();
        revision.setRevision(value);
        return revision;
    }

    @Test
    void revisionChangeTriggersRefreshExactlyOnce() {
        EmbeddedGatewaySyncScheduler scheduler = scheduler(true);
        when(repository.selectById(1)).thenReturn(revision(6L));

        scheduler.poll();
        scheduler.poll();

        verify(refreshService, times(1)).refresh();
    }

    @Test
    void markRefreshedSuppressesDuplicateRefreshForSameChange() {
        EmbeddedGatewaySyncScheduler scheduler = scheduler(true);
        scheduler.markRefreshed();
        scheduler.poll();
        scheduler.poll();

        verify(refreshService, never()).refresh();
    }

    @Test
    void subsequentChangeStillTriggersFallback() {
        EmbeddedGatewaySyncScheduler scheduler = scheduler(true);
        scheduler.markRefreshed();
        scheduler.poll();
        verify(refreshService, never()).refresh();

        when(repository.selectById(1)).thenReturn(revision(6L));
        scheduler.poll();

        verify(refreshService, times(1)).refresh();
    }

    @Test
    void disabledSchedulerDoesNotPoll() {
        EmbeddedGatewaySyncScheduler scheduler = scheduler(false);
        when(repository.selectById(1)).thenReturn(revision(6L));

        scheduler.poll();

        verify(refreshService, never()).refresh();
    }
}
