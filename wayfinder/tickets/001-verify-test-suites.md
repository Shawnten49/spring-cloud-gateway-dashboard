# 验证测试套件跑通

Labels: wayfinder:task
Status: closed
Claimed by: wayfinder 工作会话（协调者）

## Question

跑通后端与前端测试套件并记录**事实**（这是 task，产出事实而非结论）：

1. `backend`：`mvn test`（test profile，H2）。记录：通过/失败/跳过数、总用例数、耗时、有无 flaky 或环境相关失败、依赖能否拉取。
2. `frontend`：`npm test`（Vitest）。记录：通过/失败数、覆盖范围。
3. 顺带核对测试与扫描盘点的差异（此前盘点：12 个后端测试覆盖 401/403、动态权限即时生效、审计 CREATE、外部网关离线、路由校验）。

## Blocking

Blocked by: 无
Blocks: 测试充分性评审

## Resolution

**结论：两套测试套件全部跑通，无 flaky、无环境相关失败，与扫描盘点基线完全一致。**

**后端**（`backend`，`mvn test`，test profile/H2，Java 21 + Maven 3.9.16）：`BUILD SUCCESS`，**12/12 通过**（Failures 0 / Errors 0 / Skipped 0），总耗时 8.8s，依赖从本地 m2 缓存正常解析。
- ExternalGatewayStatusIntegrationTest 1 测（0.54s）、GatewayDashboardIntegrationTest 4 测（0.28s）、PermissionRuleIntegrationTest 1 测（2.25s）、RouteValidatorTest 6 测。
- 运行日志确认覆盖路径：401/403 拒绝、405 统一 JSON、路由生命周期（创建→生效→停用）、动态权限即时生效/删除即时失效/内置不可删/自我保护 400、审计 CREATE、外部网关离线状态。
- 期间出现的 `外部网关 http://localhost:19999 路由刷新失败: Connection refused` WARN 为 test profile 预期设计（不可达端口 + 推送 fire-and-forget），不影响结论。

**前端**（`frontend`，`npm test`，Vitest 3.2.7）：**4/4 通过**（1 个测试文件 `src/utils/routeJson.test.ts`，175ms）。唯一测试仍是 utils 的 JSON 工具；组件/store/router/api 无测试（与盘点一致）。

**供「测试充分性评审」使用的事实**：后端 12 测全绿但断言多为状态码级；无外部网关推送（refreshAll）测试、无乐观锁 409 测试、无改密测试、审计仅断言 CREATE；gateway-demo 零测试；前端仅 utils 有测试。覆盖缺口与失败模式的具体评估属「测试充分性评审」的决议范围，本 ticket 只提供运行事实。
