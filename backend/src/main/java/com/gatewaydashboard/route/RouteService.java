package com.gatewaydashboard.route;

import com.gatewaydashboard.audit.AuditService;
import com.gatewaydashboard.common.BusinessException;
import com.gatewaydashboard.route.RouteDto.RouteRequest;
import com.gatewaydashboard.route.RouteDto.RouteResponse;
import com.gatewaydashboard.route.RouteDto.ValidationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteConfigRepository repository;
    private final RouteValidator validator;
    private final RouteAssembler assembler;
    private final RouteRefreshService refreshService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RouteResponse> list(String keyword) {
        List<RouteConfig> routes = (keyword == null || keyword.isBlank())
                ? repository.findAllByOrderByOrderNoAscIdAsc()
                : repository.search(keyword.trim());
        return routes.stream().map(assembler::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse get(String routeId) {
        return assembler.toResponse(find(routeId));
    }

    @Transactional
    public RouteResponse create(RouteRequest request, String actor, String ip) {
        ensureValid(request);
        if (repository.existsByRouteId(request.routeId())) {
            throw BusinessException.conflict("路由 ID 已存在: " + request.routeId());
        }
        RouteConfig entity = assembler.toEntity(request);
        entity = repository.save(entity);
        refreshService.refresh();
        auditService.record(actor, "CREATE", entity.getRouteId(), null, assembler.toJson(entity), ip);
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
        RouteConfig saved;
        try {
            saved = repository.saveAndFlush(existing);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw BusinessException.conflict("该路由已被其他操作修改，请刷新后重试");
        }
        refreshService.refresh();
        auditService.record(actor, "UPDATE", saved.getRouteId(), before, assembler.toJson(saved), ip);
        return assembler.toResponse(saved);
    }

    @Transactional
    public void delete(String routeId, String actor, String ip) {
        RouteConfig existing = find(routeId);
        repository.delete(existing);
        refreshService.refresh();
        auditService.record(actor, "DELETE", existing.getRouteId(), assembler.toJson(existing), null, ip);
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
        RouteConfig saved = repository.saveAndFlush(existing);
        refreshService.refresh();
        auditService.record(actor, enabled ? "ENABLE" : "DISABLE", saved.getRouteId(), before, assembler.toJson(saved), ip);
        return assembler.toResponse(saved);
    }

    public ValidationResponse validateOnly(RouteRequest request) {
        return validator.validate(request, request.routeId());
    }

    private void ensureValid(RouteRequest request) {
        ValidationResponse validation = validator.validate(request, request.routeId());
        if (!validation.valid()) {
            throw BusinessException.badRequest(String.join("; ", validation.errors()));
        }
    }

    private RouteConfig find(String routeId) {
        return repository.findByRouteId(routeId)
                .orElseThrow(() -> BusinessException.notFound("路由不存在: " + routeId));
    }
}
