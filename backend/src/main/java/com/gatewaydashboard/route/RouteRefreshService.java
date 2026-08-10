package com.gatewaydashboard.route;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteRefreshService {

    private final ApplicationEventPublisher eventPublisher;

    public void refresh() {
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
