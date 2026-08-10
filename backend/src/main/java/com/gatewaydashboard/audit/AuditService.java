package com.gatewaydashboard.audit;

import com.gatewaydashboard.audit.AuditDtos.AuditLogResponse;
import com.gatewaydashboard.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_JSON_LENGTH = 5000;

    private final AuditLogRepository repository;

    public void record(String actor, String action, String routeId, String beforeJson, String afterJson, String ip) {
        AuditLog log = new AuditLog();
        log.setActorUsername(actor);
        log.setAction(action);
        log.setRouteId(routeId);
        log.setBeforeJson(truncate(beforeJson));
        log.setAfterJson(truncate(afterJson));
        log.setIp(ip);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogResponse> page(int page, int size) {
        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AuditLog> result = repository.findAllByOrderByCreatedAtDesc(pageable);
        return new PageResult<>(
                result.getContent().stream().map(AuditLogResponse::from).toList(),
                page,
                safeSize,
                result.getTotalElements());
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_JSON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_JSON_LENGTH);
    }
}
