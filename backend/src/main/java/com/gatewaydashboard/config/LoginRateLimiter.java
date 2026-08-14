package com.gatewaydashboard.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流（低优先级优化 2.2，Bucket4j 令牌桶，按客户端 IP）。
 *
 * - 每 IP 一个令牌桶：容量 capacity、匀速补充 refill/分钟；
 * - 每次登录尝试消耗 1 个令牌，桶空即拒绝（429）；
 * - 登录成功后移除该 IP 的桶（成功用户不积累计数）；
 * - 内存存储，单实例生效（多实例各自计数，MVP 可接受），重启清零。
 */
@Component
public class LoginRateLimiter {

    /** 桶数量上限，防止异常 IP 洪泛导致内存膨胀（超限时整体清空，重新计数）。 */
    private static final int MAX_BUCKETS = 10_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int refillPerMinute;

    public LoginRateLimiter(@Value("${gateway-dashboard.security.login-rate-limit.capacity:10}") int capacity,
                            @Value("${gateway-dashboard.security.login-rate-limit.refill-per-minute:10}") int refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    /**
     * 尝试消耗一个令牌。返回 false 表示已超限（应拒绝本次登录尝试）。
     */
    public boolean tryConsume(String key) {
        if (buckets.size() >= MAX_BUCKETS) {
            buckets.clear();
        }
        return buckets.computeIfAbsent(key, this::newBucket).tryConsume(1);
    }

    /**
     * 登录成功后调用：移除该 IP 的桶，避免误伤正常用户。
     */
    public void reset(String key) {
        buckets.remove(key);
    }

    private Bucket newBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity,
                        Refill.greedy(refillPerMinute, Duration.ofMinutes(1))))
                .build();
    }
}
