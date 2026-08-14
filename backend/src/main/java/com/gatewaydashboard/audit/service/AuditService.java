package com.gatewaydashboard.audit.service;

import com.gatewaydashboard.audit.AuditAction;
import com.gatewaydashboard.audit.entity.AuditLog;
import com.gatewaydashboard.audit.mapper.AuditLogMapper;

import com.gatewaydashboard.audit.dto.AuditDtos.AuditLogResponse;
import com.gatewaydashboard.common.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public void record(String actor, AuditAction action, String routeId, String beforeJson, String afterJson, String ip) {
        AuditLog log = new AuditLog();
        log.setActorUsername(actor);
        log.setAction(action);
        log.setRouteId(routeId);
        log.setBeforeJson(truncate(beforeJson));
        log.setAfterJson(truncate(afterJson));
        log.setIp(ip);
        auditLogMapper.insert(log);
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogResponse> page(int page, int size) {
        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        IPage<AuditLog> result = auditLogMapper.selectPageOrdered(new Page<>(safePage, safeSize));
        return new PageResult<>(
                result.getRecords().stream().map(AuditLogResponse::from).toList(),
                page,
                safeSize,
                result.getTotal());
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
