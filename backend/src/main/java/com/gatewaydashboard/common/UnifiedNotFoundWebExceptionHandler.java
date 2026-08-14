package com.gatewaydashboard.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 未匹配路径 404 统一 JSON 信封（低优先级优化 2.1）。
 *
 * WebFlux 中"无处理器命中"的 404 不会进入 @RestControllerAdvice，
 * 默认由 DefaultErrorWebExceptionHandler 渲染成非统一格式的 error JSON。
 * 本处理器以 @Order(-2) 挂在默认错误处理器（order -1）之前，仅接管 404，
 * 其余异常原样放行给后续处理器/默认链。
 *
 * 边界：只处理"未匹配路径/资源"的 404，不影响网关代理转发（匹配路由不走此处）、
 * 401/403（安全过滤器链处理）、405/400（@RestControllerAdvice 处理）。
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class UnifiedNotFoundWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (isNotFound(ex, exchange)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                byte[] body = objectMapper.writeValueAsBytes(ApiResponse.fail(404, "接口不存在"));
                return exchange.getResponse().writeWith(
                        Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
            } catch (Exception e) {
                return Mono.error(e);
            }
        }
        return Mono.error(ex);
    }

    private boolean isNotFound(Throwable ex, ServerWebExchange exchange) {
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatusCode.valueOf(404).equals(rse.getStatusCode());
        }
        return exchange.getResponse().getStatusCode() != null
                && exchange.getResponse().getStatusCode().value() == 404;
    }
}
