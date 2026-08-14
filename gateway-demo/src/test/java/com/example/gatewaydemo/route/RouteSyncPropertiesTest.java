package com.example.gatewaydemo.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * fail-closed 配置回归测试（评审 F15 / S-05）：内部 token 为空必须拒绝启动，
 * 杜绝"未配置 token = 内部接口公开"。
 */
class RouteSyncPropertiesTest {

    @Test
    void blankTokenRejectedAtStartup() {
        RouteSyncProperties properties = new RouteSyncProperties();
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void configuredTokenAccepted() {
        RouteSyncProperties properties = new RouteSyncProperties();
        properties.setInternalToken("strong-random-token");
        assertDoesNotThrow(properties::validate);
    }
}
