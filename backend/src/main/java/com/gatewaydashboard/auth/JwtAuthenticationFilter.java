package com.gatewaydashboard.auth;

import com.gatewaydashboard.auth.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtService jwtService;
    private final UserAuthStateCache userAuthStateCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                String username = claims.getSubject();
                String role = claims.get("role", String.class);
                long tokenVersion = claims.get("ver") instanceof Number n ? n.longValue() : 0L;
                // S-04 吊销校验：改密/停用后 token_version +1，旧 token 的 ver 不再匹配 → 视为未认证
                if (!userAuthStateCache.isTokenValid(username, tokenVersion)) {
                    log.debug("JWT 已吊销或用户状态变化（{}）: {}", username, exchange.getRequest().getPath());
                    return chain.filter(exchange);
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        username, null, AuthorityUtils.createAuthorityList("ROLE_" + role));
                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            } catch (JwtException | IllegalArgumentException e) {
                // 无效 token：不注入认证信息，受保护接口会返回 401；记录 DEBUG 便于排查异常流量
                log.debug("无效 JWT（{}）: {}", exchange.getRequest().getPath(), e.getMessage());
            }
        }
        return chain.filter(exchange);
    }
}
