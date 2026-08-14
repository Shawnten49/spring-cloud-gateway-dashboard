package com.gatewaydashboard.common;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * 响应式边界工具：把阻塞调用（JDBC/JPA/IO）从 Netty 事件循环线程卸到 boundedElastic 调度器执行。
 * 本工程是 WebFlux + 同进程内嵌网关（ADR 0002），管理 API 若直接在事件循环上跑 JPA，
 * 慢查询会拖慢网关业务转发的全部请求，因此所有阻塞服务调用必须经此出口。
 */
public final class BlockingSupport {

    private BlockingSupport() {
    }

    /**
     * 在 boundedElastic 线程上执行阻塞调用并异步返回结果。
     */
    public static <T> Mono<T> call(Callable<T> callable) {
        return Mono.fromCallable(callable)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 在 boundedElastic 线程上执行无返回值的阻塞操作。
     */
    public static Mono<Void> run(Runnable runnable) {
        return Mono.<Void>fromRunnable(runnable)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
