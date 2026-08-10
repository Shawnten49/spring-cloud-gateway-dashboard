package com.gatewaydashboard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewaydashboard.auth.JwtAuthenticationFilter;
import com.gatewaydashboard.common.ApiResponse;
import com.gatewaydashboard.permission.DynamicPermissionAuthorizationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DynamicPermissionAuthorizationManager dynamicPermissionAuthorizationManager;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {
                })
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, ex) ->
                                writeJson(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录或登录已过期"))
                        .accessDeniedHandler((exchange, ex) ->
                                writeJson(exchange, HttpStatus.FORBIDDEN, 403, "没有权限访问该接口")))
                .authorizeExchange(exchanges -> exchanges
                        // 引导规则：保证登录与健康检查永远可用
                        .pathMatchers("/api/auth/login", "/actuator/health").permitAll()
                        // 其余接口的权限规则全部来自数据库（permission_rule 表），可动态配置
                        .anyExchange().access(dynamicPermissionAuthorizationManager))
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * 未认证/无权限时返回统一 JSON（而非浏览器 Basic 认证弹框）。
     */
    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(new ApiResponse<Void>(code, message, null));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
