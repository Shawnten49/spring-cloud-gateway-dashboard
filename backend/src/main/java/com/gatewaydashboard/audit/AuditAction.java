package com.gatewaydashboard.audit;

/**
 * 操作审计动作类型（替代魔法字符串，避免拼写漂移；DB 按枚举名存储，见 AuditLog.action）。
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    ENABLE,
    DISABLE
}
