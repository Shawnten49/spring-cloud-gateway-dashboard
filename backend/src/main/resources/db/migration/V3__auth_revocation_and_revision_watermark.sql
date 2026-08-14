-- V3: S-04 用户级 token 吊销 + F13 全局单调 revision 水印
-- 对应 docs/优化方案.md Phase 1（S-04 / F13 / F5）

-- S-04: 用户 token 版本号。每次改密/停用 +1，JWT 携带 ver claim，
-- 过滤器比对版本，旧 token 立即失效（吊销粒度：按用户全部 token）。
ALTER TABLE sys_user ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;

-- F13: 路由配置全局单调修订号（单一真源写入口 = RouteService 同事务内 +1）。
-- 行级 @Version 只对单行更新单调，无法表达"任意行新增/删除"的全局次序；
-- 独立单行表提供严格单调序列，替代 gateway-demo 轮询的 (COUNT, SUM(version)) 校验和，
-- 消除碰撞漏检，并作为 F5 内嵌网关轮询兜底与外部网关轮询的同一事实源。
CREATE TABLE config_revision (
    id       TINYINT PRIMARY KEY,
    revision BIGINT NOT NULL
);

INSERT INTO config_revision (id, revision) VALUES (1, 0);
