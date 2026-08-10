package com.gatewaydashboard.audit;

import java.time.Instant;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogResponse(Long id, String actorUsername, String action, String routeId,
                                   String beforeJson, String afterJson, String ip, Instant createdAt) {

        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getActorUsername(),
                    log.getAction(),
                    log.getRouteId(),
                    log.getBeforeJson(),
                    log.getAfterJson(),
                    log.getIp(),
                    log.getCreatedAt());
        }
    }
}
