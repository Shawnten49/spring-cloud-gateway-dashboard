package com.gatewaydashboard.route;

import com.gatewaydashboard.route.RouteDto.RouteRequest;
import com.gatewaydashboard.route.RouteDto.Step;
import com.gatewaydashboard.route.RouteDto.ValidationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RouteValidatorTest {

    @Autowired
    private RouteValidator validator;

    @Test
    void validRoutePasses() {
        RouteRequest request = new RouteRequest(
                "demo-route", "http://httpbin.org", 0, true,
                List.of(new Step("Path", Map.of("patterns", "/demo/**"))),
                List.of(new Step("AddRequestHeader", Map.of("name", "X-Demo", "value", "1"))),
                Map.of("owner", "demo"));

        ValidationResponse response = validator.validate(request);
        assertTrue(response.valid(), response.errors().toString());
    }

    @Test
    void unknownFactoryFails() {
        RouteRequest request = new RouteRequest(
                "demo-route", "http://httpbin.org", 0, true,
                List.of(new Step("NoSuchPredicate", Map.of())),
                List.of(), Map.of());

        ValidationResponse response = validator.validate(request);
        assertFalse(response.valid());
        assertTrue(response.errors().stream().anyMatch(e -> e.contains("未知的工厂名")));
    }

    @Test
    void enabledRouteWithoutPredicateFails() {
        RouteRequest request = new RouteRequest(
                "demo-route", "http://httpbin.org", 0, true,
                List.of(), List.of(), Map.of());

        assertFalse(validator.validate(request).valid());
    }

    @Test
    void disabledRouteWithoutPredicatePasses() {
        RouteRequest request = new RouteRequest(
                "demo-route", "http://httpbin.org", 0, false,
                List.of(), List.of(), Map.of());

        assertTrue(validator.validate(request).valid());
    }

    @Test
    void unsupportedSchemeFails() {
        RouteRequest request = new RouteRequest(
                "demo-route", "ftp://example.com", 0, true,
                List.of(new Step("Path", Map.of("patterns", "/demo/**"))),
                List.of(), Map.of());

        assertFalse(validator.validate(request).valid());
    }

    @Test
    void nestedArgsValueFails() {
        RouteRequest request = new RouteRequest(
                "demo-route", "http://httpbin.org", 0, true,
                List.of(new Step("Path", Map.of("patterns", Map.of("nested", "x")))),
                List.of(), Map.of());

        assertFalse(validator.validate(request).valid());
    }
}
