package com.gatewaydashboard.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 默认值治理回归测试（评审 P1-A / S-01）：
 * 非开发 profile 下使用公开的默认 JWT 密钥必须拒绝启动。
 */
class JwtServiceTest {

    private static final String DEV_DEFAULT_SECRET = "gateway-dashboard-dev-secret-change-me-0123456789";
    private static final String STRONG_SECRET = "a-very-long-random-secret-that-is-at-least-32-bytes-0123456789";

    private Environment environment(String... profiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        return env;
    }

    @Test
    void prodProfileRejectsDefaultSecret() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(DEV_DEFAULT_SECRET, 12, environment("prod")));
    }

    @Test
    void devProfileAllowsDefaultSecret() {
        assertDoesNotThrow(() -> new JwtService(DEV_DEFAULT_SECRET, 12, environment("dev")));
    }

    @Test
    void prodProfileAcceptsStrongSecret() {
        assertDoesNotThrow(() -> new JwtService(STRONG_SECRET, 12, environment("prod")));
    }

    @Test
    void shortSecretRejectedRegardlessOfProfile() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("too-short", 12, environment("dev")));
    }
}
