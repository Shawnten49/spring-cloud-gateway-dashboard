package com.gatewaydashboard.audit;

import com.gatewaydashboard.audit.AuditDtos.AuditLogResponse;
import com.gatewaydashboard.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository repository;

    public void record(String actor, AuditAction action, String routeId, String beforeJson, String afterJson, String ip) {
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

    /**
     * 截断到完整 JSON 边界（最后一个对象/数组结束符），避免审计内容成为非法 JSON。
     * 超长时记录日志便于排查，入库内容仍为可解析的完整 JSON。
     */
    private String truncate(String value) {
        if (value == null || value.length() <= AuditLog.JSON_COLUMN_LENGTH) {
            return value;
        }
        log.warn("审计快照超过 {} 字符，已截断（原始长度 {}）", AuditLog.JSON_COLUMN_LENGTH, value.length());
        String cut = value.substring(0, AuditLog.JSON_COLUMN_LENGTH);
        int boundary = Math.max(cut.lastIndexOf('}'), cut.lastIndexOf(']'));
        return boundary > 0 ? cut.substring(0, boundary) : cut;
    }
}
