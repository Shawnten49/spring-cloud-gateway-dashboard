package com.gatewaydashboard.audit.controller;

import com.gatewaydashboard.audit.service.AuditService;

import com.gatewaydashboard.audit.dto.AuditDtos.AuditLogResponse;
import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.common.BlockingSupport;
import com.gatewaydashboard.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public Mono<ApiResponse<PageResult<AuditLogResponse>>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return BlockingSupport.call(() -> ApiResponse.ok(auditService.page(page, size)));
    }
}
