package com.gatewaydashboard;

import com.gatewaydashboard.refresh.EmbeddedGatewaySyncScheduler;
import com.gatewaydashboard.route.ConfigRevisionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F5 多实例兜底端到端验证：模拟"他实例/直连数据库"的写入（绕过本地刷新路径直接 bump 修订号），
 * 内嵌网关轮询兜底必须感知并触发刷新（RefreshRoutesEvent）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExternalChangeFallbackIntegrationTest {

    /** 记录 RefreshRoutesEvent 发布次数（含内嵌网关轮询兜底触发）。 */
    static class RefreshEventCounter {
        final AtomicInteger count = new AtomicInteger();

        @EventListener
        public void onRefresh(RefreshRoutesEvent event) {
            count.incrementAndGet();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CounterConfig {
        @Bean
        RefreshEventCounter refreshEventCounter() {
            return new RefreshEventCounter();
        }
    }

    @Autowired
    private EmbeddedGatewaySyncScheduler scheduler;

    @Autowired
    private ConfigRevisionRepository revisionRepository;

    @Autowired
    private RefreshEventCounter counter;

    @Test
    void externalRevisionChangeIsPickedUpByPollingFallback() {
        // 记录当前已发布的刷新次数与修订号
        int before = counter.count.get();
        long revisionBefore = revisionRepository.findById(1).orElseThrow().getRevision();

        // 模拟他实例写入：绕过 RouteService 本地刷新路径，直接 bump 全局修订号
        revisionRepository.bumpRevision();
        assertTrue(revisionRepository.findById(1).orElseThrow().getRevision() > revisionBefore,
                "外部写入应使修订号 +1");

        // 手动触发一次轮询（模拟调度器到点；调度器线程也可能已触发，二者都算兜底生效）
        scheduler.poll();

        // 兜底必须触发至少一次刷新
        assertTrue(counter.count.get() > before,
                "外部修订号变化后，内嵌网关轮询兜底应触发 RefreshRoutesEvent");
    }
}
