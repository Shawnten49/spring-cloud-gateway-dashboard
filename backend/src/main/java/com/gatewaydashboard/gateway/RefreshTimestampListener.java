package com.gatewaydashboard.gateway;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RefreshTimestampListener {

    private final AtomicReference<Instant> lastRefreshAt = new AtomicReference<>(Instant.now());

    @EventListener
    public void onRefresh(RefreshRoutesEvent event) {
        lastRefreshAt.set(Instant.now());
    }

    public Instant getLastRefreshAt() {
        return lastRefreshAt.get();
    }
}
