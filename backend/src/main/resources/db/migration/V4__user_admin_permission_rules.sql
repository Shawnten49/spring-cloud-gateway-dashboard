-- V4: 用户管理模块内置权限规则（见 docs/用户管理模块-设计文档.md）
-- 优先级修正说明：必须小于"接口-查看 GET /api/** AUTHENTICATED(10)"，
-- 否则 GET /api/users 会先命中 AUTHENTICATED 规则导致 VIEWER 可查看用户列表。
-- 采用与"权限配置-查看(5)"相同的先精确后兜底模式：用户管理规则置于 6/7/8。

INSERT INTO permission_rule (name, http_method, path_pattern, roles, priority, enabled, builtin) VALUES
('用户管理-查看', 'GET',  '/api/users/**', 'ADMIN', 6, TRUE, TRUE),
('用户管理-新增', 'POST', '/api/users/**', 'ADMIN', 7, TRUE, TRUE),
('用户管理-屏蔽', 'PUT',  '/api/users/**', 'ADMIN', 8, TRUE, TRUE);
