package com.gatewaydashboard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewaydashboard.auth.User;
import com.gatewaydashboard.auth.UserRepository;
import com.gatewaydashboard.route.RouteConfig;
import com.gatewaydashboard.route.RouteConfigRepository;
import com.gatewaydashboard.route.RouteDto.Step;
import com.gatewaydashboard.route.RouteRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SeedDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RouteConfigRepository routeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final RouteRefreshService routeRefreshService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        boolean seeded = false;
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(buildUser("admin", "admin123", "ADMIN"));
            seeded = true;
        }
        if (!userRepository.existsByUsername("viewer")) {
            userRepository.save(buildUser("viewer", "viewer123", "VIEWER"));
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

    private User buildUser(String username, String password, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private RouteConfig buildRoute(String routeId, String uri, String path) throws Exception {
        RouteConfig route = new RouteConfig();
        route.setRouteId(routeId);
        route.setUri(uri);
        route.setOrderNo(0);
        route.setEnabled(true);
        route.setPredicatesJson(objectMapper.writeValueAsString(List.of(new Step("Path", Map.of("patterns", path)))));
        route.setFiltersJson("[]");
        route.setMetadataJson("{}");
        return route;
    }
}
