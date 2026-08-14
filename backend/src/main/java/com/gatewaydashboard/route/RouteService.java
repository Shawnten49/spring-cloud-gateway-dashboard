package com.gatewaydashboard.route;

import com.gatewaydashboard.audit.AuditAction;
import com.gatewaydashboard.audit.AuditService;
import com.gatewaydashboard.common.BusinessException;
import com.gatewaydashboard.route.RouteDto.RouteRequest;
import com.gatewaydashboard.route.RouteDto.RouteResponse;
import com.gatewaydashboard.route.RouteDto.ValidationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteConfigMapper routeConfigMapper;
    private final RouteValidator validator;
    private final RouteAssembler assembler;
    private final AuditService auditService;
    private final ConfigRevisionMapper configRevisionMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<RouteResponse> list(String keyword) {
        List<RouteConfig> routes = (keyword == null || keyword.isBlank())
                ? routeConfigMapper.selectAllOrdered()
                : routeConfigMapper.searchByKeyword(keyword.trim());
        return routes.stream().map(assembler::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse get(String routeId) {
        return assembler.toResponse(find(routeId));
    }

    @Transactional
    public RouteResponse create(RouteRequest request, String actor, String ip) {
        ensureValid(request);
        if (routeConfigMapper.countByRouteId(request.routeId()) > 0) {
            throw BusinessException.conflict("路由 ID 已存在: " + request.routeId());
        }
        RouteConfig entity = assembler.toEntity(request);
        try {
            routeConfigMapper.insert(entity);
        } catch (DataIntegrityViolationException e) {
            // 并发创建同一 routeId：count 检查与 insert 之间存在竞态窗口，
            // 唯一约束冲突应返回 409 而非 500（DuplicateKeyException 是其子类）。
            throw BusinessException.conflict("路由 ID 已存在: " + request.routeId());
        }
        auditService.record(actor, AuditAction.CREATE, entity.getRouteId(), null, assembler.toJson(entity), ip);
        bumpRevision();
        scheduleRefreshAfterCommit();
        return assembler.toResponse(entity);
    }

    @Transactional
    public RouteResponse update(String routeId, RouteRequest request, String actor, String ip) {
        RouteConfig existing = find(routeId);
        if (!request.routeId().equals(routeId)) {
            throw BusinessException.badRequest("路由 ID 不可修改");
        }
        ensureValid(request);
        String before = assembler.toJson(existing);
        assembler.applyRequest(existing, request);
        // 乐观锁（XML 手写 WHERE version=#{version}）：受影响行数 0 = 版本冲突
        int updated = routeConfigMapper.updateByIdWithVersion(existing);
        if (updated == 0) {
            throw BusinessException.conflict("该路由已被其他操作修改，请刷新后重试");
        }
        existing.setVersion(existing.getVersion() + 1);
        auditService.record(actor, AuditAction.UPDATE, existing.getRouteId(), before, assembler.toJson(existing), ip);
        bumpRevision();
        scheduleRefreshAfterCommit();
        return assembler.toResponse(existing);
    }

    @Transactional
    public void delete(String routeId, String actor, String ip) {
        RouteConfig existing = find(routeId);
        routeConfigMapper.deleteById(existing.getId());
        auditService.record(actor, AuditAction.DELETE, existing.getRouteId(), assembler.toJson(existing), null, ip);
        bumpRevision();
        scheduleRefreshAfterCommit();
    }

    @Transactional
    public RouteResponse setEnabled(String routeId, boolean enabled, String actor, String ip) {
        RouteConfig existing = find(routeId);
        if (enabled) {
            ensureValid(assembler.toRequest(existing));
        }
        if (existing.isEnabled() == enabled) {
            return assembler.toResponse(existing);
        }
        String before = assembler.toJson(existing);
        existing.setEnabled(enabled);
        int updated = routeConfigMapper.updateByIdWithVersion(existing);
        if (updated == 0) {
            throw BusinessException.conflict("该路由已被其他操作修改，请刷新后重试");
        }
        existing.setVersion(existing.getVersion() + 1);
        auditService.record(actor, enabled ? AuditAction.ENABLE : AuditAction.DISABLE, existing.getRouteId(), before, assembler.toJson(existing), ip);
        bumpRevision();
        scheduleRefreshAfterCommit();
        return assembler.toResponse(existing);
    }

    /**
     * 全局修订号 +1（F13）：与路由写入同一事务，原子自增。
     * 内嵌网关轮询兜底（F5）与外部网关轮询（gateway-demo）据此感知任意写变更。
     */
    private void bumpRevision() {
        configRevisionMapper.bumpRevision();
    }

    /**
     * 事务提交后发布路由变更事件（route 包不依赖刷新/推送实现，由 refresh 包监听编排）：
     * 本地生效路由刷新 → 内嵌网关轮询标记 → 外部网关推送。
     */
    private void scheduleRefreshAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new RouteChangedEvent());
                }
            });
        } else {
            eventPublisher.publishEvent(new RouteChangedEvent());
        }
    }

    public ValidationResponse validateOnly(RouteRequest request) {
        return validator.validate(request);
    }

    private void ensureValid(RouteRequest request) {
        ValidationResponse validation = validator.validate(request);
        if (!validation.valid()) {
            throw BusinessException.badRequest(String.join("; ", validation.errors()));
        }
    }

    private RouteConfig find(String routeId) {
        RouteConfig route = routeConfigMapper.selectByRouteId(routeId);
        if (route == null) {
            throw BusinessException.notFound("路由不存在: " + routeId);
        }
        return route;
    }
}
