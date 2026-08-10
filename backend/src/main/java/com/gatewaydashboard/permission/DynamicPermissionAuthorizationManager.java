package com.gatewaydashboard.permission;

import com.gatewaydashboard.permission.PermissionRuleService.CachedRule;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 动态授权：按（方法, 路径）匹配 permission_rule 表中最优先的规则并决策。
 * 规则修改后通过 PermissionRuleService.reload() 即时生效，无需重启。
 */
@Component
@RequiredArgsConstructor
public class DynamicPermissionAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final PermissionRuleService permissionRuleService;

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        ServerWebExchange exchange = context.getExchange();
        String method = exchange.getRequest().getMethod() == null ? "" : exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();

        CachedRule rule = permissionRuleService.match(method, path);
        if (rule == null) {
            return Mono.just(new AuthorizationDecision(false));
        }
        if (rule.roles().contains("*")) {
            return Mono.just(new AuthorizationDecision(true));
        }
        if (rule.roles().contains("AUTHENTICATED")) {
            return authentication
                    .map(auth -> new AuthorizationDecision(auth.isAuthenticated()))
                    .defaultIfEmpty(new AuthorizationDecision(false));
        }
        return authentication
                .map(auth -> {
                    if (!auth.isAuthenticated()) {
                        return new AuthorizationDecision(false);
                    }
                    Set<String> authorities = new HashSet<>();
                    for (GrantedAuthority authority : auth.getAuthorities()) {
                        String value = authority.getAuthority();
                        authorities.add(value.startsWith("ROLE_") ? value.substring(5) : value);
                    }
                    return new AuthorizationDecision(permissionRuleService.isAllowed(rule, authorities));
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
