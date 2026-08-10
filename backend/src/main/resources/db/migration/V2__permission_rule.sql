CREATE TABLE permission_rule (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    http_method  VARCHAR(16)  NOT NULL,
    path_pattern VARCHAR(256) NOT NULL,
    roles        VARCHAR(256) NOT NULL,
    priority     INT          NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    builtin      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 内置规则：与 v1 写死在 SecurityConfig 中的行为保持一致。
-- 优先级数字越小越先匹配；roles 支持 *（公开）、AUTHENTICATED（登录即可）、角色列表（如 ADMIN,VIEWER）。
INSERT INTO permission_rule (name, http_method, path_pattern, roles, priority, enabled, builtin) VALUES
('权限配置-查看', 'GET', '/api/permission-rules/**', 'ADMIN', 5, TRUE, TRUE),
('接口-查看', 'GET', '/api/**', 'AUTHENTICATED', 10, TRUE, TRUE),
('权限配置-新增', 'POST', '/api/permission-rules/**', 'ADMIN', 15, TRUE, TRUE),
('路由-校验', 'POST', '/api/routes/validate', 'AUTHENTICATED', 20, TRUE, TRUE),
('权限配置-修改', 'PUT', '/api/permission-rules/**', 'ADMIN', 25, TRUE, TRUE),
('账号-改密码', 'PUT', '/api/auth/password', 'AUTHENTICATED', 30, TRUE, TRUE),
('权限配置-删除', 'DELETE', '/api/permission-rules/**', 'ADMIN', 35, TRUE, TRUE),
('路由-新增/启用', 'POST', '/api/routes/**', 'ADMIN', 40, TRUE, TRUE),
('路由-修改', 'PUT', '/api/routes/**', 'ADMIN', 50, TRUE, TRUE),
('路由-删除', 'DELETE', '/api/routes/**', 'ADMIN', 60, TRUE, TRUE),
('默认-登录即可', '*', '/**', 'AUTHENTICATED', 999, TRUE, TRUE);
