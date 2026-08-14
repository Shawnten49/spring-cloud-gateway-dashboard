package com.gatewaydashboard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewaydashboard.auth.User;
import com.gatewaydashboard.auth.UserRepository;
import com.gatewaydashboard.route.RouteConfig;
import com.gatewaydashboard.route.RouteConfigRepository;
import com.gatewaydashboard.route.RouteDto.Step;
import com.gatewaydashboard.route.RouteRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 首次启动种子数据：预置账号与示例路由。
 * 口令可配置（gateway-dashboard.seed.*，生产用 GD_ADMIN_PASSWORD / GD_VIEWER_PASSWORD 环境变量覆盖）；
 * 需要新建账号但口令为空时拒绝启动，防止出现空口令/弱口令的默认管理员（评审 P1-A / S-02）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RouteConfigRepository routeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final RouteRefreshService routeRefreshService;

    @Value("${gateway-dashboard.seed.admin-password:}")
    private String adminPassword;

    @Value("${gateway-dashboard.seed.viewer-password:}")
    private String viewerPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean seeded = false;
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(buildUser("admin", requireSeedPassword("admin", adminPassword), "ADMIN"));
            seeded = true;
        }
        if (!userRepository.existsByUsername("viewer")) {
            userRepository.save(buildUser("viewer", requireSeedPassword("viewer", viewerPassword), "VIEWER"));
            seeded = true;
        }
        if (!routeRepository.existsByRouteId("httpbin-get")) {
            routeRepository.save(buildRoute("httpbin-get", "http://httpbin.org", "/get"));
            seeded = true;
        }
        if (!routeRepository.existsByRouteId("httpbin-anything")) {
            routeRepository.save(buildRoute("httpbin-anything", "http://httpbin.org", "/anything"));
            seeded = true;
        }
        if (seeded) {
            routeRefreshService.refresh();
        }
    }

    private String requireSeedPassword(String username, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("预置账号 " + username + " 不存在且未配置初始口令："
                    + "请通过环境变量 GD_ADMIN_PASSWORD / GD_VIEWER_PASSWORD（或配置 gateway-dashboard.seed.*）提供强口令");
        }
        return password;
    }

    private User buildUser(String username, String password, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private RouteConfig buildRoute(String routeId, String uri, String path) {
        RouteConfig route = new RouteConfig();
        route.setRouteId(routeId);
        route.setUri(uri);
        route.setOrderNo(0);
        route.setEnabled(true);
        try {
            route.setPredicatesJson(objectMapper.writeValueAsString(List.of(new Step("Path", Map.of("patterns", path)))));
        } catch (Exception e) {
            throw new IllegalStateException("种子路由断言序列化失败: " + routeId, e);
        }
        route.setFiltersJson("[]");
        route.setMetadataJson("{}");
        return route;
    }
}
