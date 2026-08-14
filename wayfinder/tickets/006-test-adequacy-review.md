# 测试充分性评审

Labels: wayfinder:research
Status: closed
Claimed by: wayfinder research 子代理（03ab64d6-28bc-49d0-ba2a-e9ddf11a5138）

## Question

基于「验证测试套件跑通」的运行结果 + 全仓测试盘点，评审测试充分性：

1. **现有覆盖**：12 个后端测试 + 前端 utils Vitest 各自覆盖了什么、验证的断言强度（只验状态码还是验数据？）。
2. **缺口**：用「哪些 bug 会溜过测试网」的风险视角评估——外部网关推送路径、乐观锁 409、改密、审计各动作（修改/删除/停用）、权限规则优先级与自我保护、gateway-demo（无任何测试）、前端除 utils 外的组件/store/路由守卫。对照 001 的运行结果判断测试是否真绿且稳定。
3. **结构与工具**：测试可维护性（命名、断言、fixture）、是否覆盖失败路径（校验拒绝、外部网关离线）。

产出：充分性判断 + 缺口清单（每条标 P0-P3、缺什么测试、为什么重要、建议补法）。

## Blocking

Blocked by: 验证测试套件跑通
Blocks: 合成评审报告

## Resolution

**结论：方向正确、深度不足——"冒烟 + 关键成功路径"级，非"充分"级。无 P0，P1×6 / P2×5 / P3×4。**

**最强反证**：架构评审确认的 7 类缺陷（create 并发 500 非 409、内部 token 空放行、markRefreshed 竞态、校验和碰撞、审计序列化静默 null、状态页"生效路由"实为定位器读 DB、**guard 只防 POST 不防 PUT/DELETE**）**全部溜过测试网**——对应失败路径一个测试都没有；其中"状态页生效路由"反被现有测试（`GatewayDashboardIntegrationTest.java:88`）固化背书（无"真实流量穿过网关"的断言）。

**P1（6）**：
- P1-1 外部网关推送/在线链路零测试（ExternalGatewayRefreshService/StatusService：URL 拼接、X-Internal-Token 头、PushRecord、在线分支、失败不阻断保存全无断言；001 中 19999 WARN 恰证明路径被执行但从未被验证）。补法：MockWebServer/WireMock
- P1-2 已确认缺陷无回归测试（并发唯一冲突 → 500 而非 409、markRefreshed 竞态、token 空放行）
- P1-3 权限规则边界缺失：空白 roles 产生"死规则"遮蔽后续规则、`roles:"*"` 匿名也放行、httpMethod `*`、**guard 仅查 POST + 合成路径——加 `PUT /api/permission-rules/**` VIEWER 规则即可绕过守卫锁死 ADMIN 的 PUT**（评审新发现的设计洞，已转发安全评审交叉核查）
- P1-4 改密流程零测试（错旧密码、新密码<6 位、改后旧 JWT 仍有效——应固化为已知边界）
- P1-5 审计仅 CREATE 有断言（UPDATE/DELETE/ENABLE/DISABLE、before/after 内容、5000 截断、同状态 /enabled 短路不记审计均无测试）
- P1-6 gateway-demo 整个模块零测试（token 校验、轮询校验和、markRefreshed、双套 DbRouteDefinitionLocator 裸奔）

**P2（5）**：乐观锁 409 无测试（@Version 仅靠代码推断）；API 失败路径缺失（校验 400、404、/enabled 空 body 默认 disable、meta/factories、搜索、审计分页钳制）；JWT 安全路径（坏/过期 token、viewer×GET 接口矩阵、禁用账号）；前端组件/store/router/api 零测试（vite.config 为 node 环境、无 @vue/test-utils/happy-dom）；无覆盖率工具/门槛（pom 无 jacoco、vitest 无 coverage）。

**P3（4）**：结构与可维护性（PermissionRule 单方法 8 步长测、jsonPath 字符串断言脆弱、无 @DisplayName）；前端 utils 边角（toRequestJson 零测试、enabled 默认、order NaN、非对象 JSON、正则分支）；稳定性隐患（19999 端口占用依赖、"创建后立即生效"时序耦合、H2 DB_CLOSE_DELAY 跨类数据残留）；RouteValidatorTest 用全上下文测纯校验器 + `existingRouteId` 死参数未被测试暴露（RouteValidator.java:47）。

**现有覆盖要点（供合成平衡）**：12 后端测中断言多为状态码级；`fullRouteLifecycle` 有数据级断言且验证了本地热刷新链真实打通；`PermissionRuleIntegrationTest` 的动态权限即时生效/失效双向验证含金量最高；`RouteValidatorTest` 是唯一逻辑级测试；前端 4 测覆盖 51 行工具函数但缺 5 个边角分支。

**完整发现**：`wayfinder/findings/test-adequacy-review.md`（233 行，逐条 文件:行号），一次性分支 `research/test-adequacy-review`，commit `50b0319`（未并入 main）。
