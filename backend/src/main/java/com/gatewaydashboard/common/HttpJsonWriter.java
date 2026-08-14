package com.gatewaydashboard.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 统一 JSON 错误响应写出（安全链 401/403 与 404 处理器共用，避免重复实现）。
 */
public final class HttpJsonWriter {

    private HttpJsonWriter() {
    }

    public static Mono<Void> writeError(ServerWebExchange exchange, ObjectMapper objectMapper,
                                        HttpStatusCode status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiResponse.fail(code, message));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
