# 规范一致性评审 — 完整发现

Ticket: [008-spec-consistency-review](../tickets/008-spec-consistency-review.md)
分支: `research/spec-consistency-review`（一次性 research 分支，基于 main）
方法: 静态阅读代码（backend / gateway-demo / frontend）+ 对照 [002 起服抽查 API 行为](../tickets/002-probe-api-at-runtime.md) 的运行时事实 + 核对 `docs/adr/0001-0005`、`README.md`、`CONTEXT.md`、`docs/使用手册.md`。未启动服务、未跑测试、未修改任何现有文件。

---

## 一、一致性结论（总）

**整体高度一致，无 P0/P1 级差异。** 四份自证文档（5 篇 ADR、README、CONTEXT、使用手册）的核心声明与代码实现、以及 ticket 002 的运行时事实基本逐条对得上：

- 「保存即生效、热刷新、无需重启」— 实现与实测双重确认 ✓
- 「停用 vs 删除」语义、权限规则「方法+路径+角色+优先级、首个命中、即时生效」、内置规则不可删 ✓
- 数据库真源 + 外部网关「推送失败不影响保存、5s 轮询兜底」✓
- 网关状态页（健康/最近刷新/生效路由/外部网关实例）✓
- 操作审计（5 类动作 × 操作者/时间/变更前后/IP）✓
- README API 摘要表 17 个端点与控制器方法/路径/权限逐条一致 ✓
- 使用手册示例 JSON 与校验规则、页面操作描述、外部网关对接章节 ✓
- ADR 0001-0005 的决策均落地 ✓

发现的差异集中在 **3 条 P2（文档过时/措辞宽于实现）** 与 **7 条 P3（措辞/观感/文档缺口）**，全部是「文档过时或措辞不严谨」，**未发现代码错**。

---

## 二、逐项核对（核对重点 1-7）

### 1. README 功能清单 vs 代码/行为 — 一致 ✓

| README 声明（出处） | 实现位置 | 002 实测 | 结论 |
|---|---|---|---|
| 登录认证：用户表 + JWT(12h)、ADMIN/VIEWER、预置 admin/viewer（README:7） | `auth/`（User、AuthService、JwtService expireHours=12）、`config/SeedDataInitializer`（admin123/viewer123） | 登录 200 / 错密码 401 / /me 200 | ✓ |
| 权限配置：方法+路径+角色+优先级、修改后即时生效（README:8） | `permission/`（permission_rule 表、`PermissionRuleService.reload()` + AtomicReference、`DynamicPermissionAuthorizationManager` 按优先级首个命中） | 新增规则后 viewer 立即获得访问权、删除后立即失效 | ✓ |
| 路由管理：增删改查/停用启用/服务端强校验（README:9） | `route/`（RouteController/RouteService/RouteValidator，工厂名+ConfigurationService 试绑定） | 创建 200、非法工厂 400、validate 只校验不保存 | ✓ |
| 动态生效：DB 唯一真源、保存/删除/停用触发 RefreshRoutesEvent（README:10） | `RouteService.scheduleRefreshAfterCommit`（事务提交后 refresh + 推外部网关）、`DbRouteDefinitionLocator` 只读启用路由 | 创建后立即出现在生效路由（两 profile 均验证） | ✓ |
| 网关状态：健康/最近刷新/生效路由（README:11） | `gateway/`（GatewayStatusService 健康=DB count、RefreshTimestampListener、effectiveRoutes） | health=UP、lastRefreshAt 随保存更新、停用后不在生效列表 | ✓ |
| 操作审计：每次 创建/修改/删除/停用/启用 记录操作者、时间、变更前后、IP（README:12） | `audit/`（AuditService.record：actor/action/routeId/before/after/ip + createdAt） | CREATE/UPDATE/DISABLE/ENABLE/DELETE 全落库，DELETE afterJson=null | ✓ |
| 高级 JSON 模式（README:13） | `frontend/src/components/RouteEditorDrawer.vue` 表单/JSON 双模式，保存前先调 /validate | —（静态确认） | ✓ |

### 2. README API 摘要表 vs 实际路由 — 一致 ✓

逐条核对 `RouteController` / `AuthController` / `PermissionRuleController` / `RouteMetaController` / `GatewayStatusController` / `AuditController`：

- 17 个端点的方法、路径、说明与 README:114-132 完全一致，无多无少（另有的 `/actuator/health` 属基础设施，README 未列入 API 摘要，可接受）。
- 权限要求逐条对照 V2 种子规则（`V2__permission_rule.sql`）核实：GET /api/** → AUTHENTICATED(10)、POST /api/routes/validate → AUTHENTICATED(20，优先级低于 40 的 POST /api/routes/** ADMIN，故 validate 仅需登录，与 README「登录」一致)、POST/PUT/DELETE /api/routes/** → ADMIN(40/50/60)、/api/permission-rules 四方法 → ADMIN(5/15/25/35)。002 实测 viewer 看规则 403、写路由 403 与之相符。
- 统一响应体 `{code,message,data}`（README:134）：200/400/401/403/405/409 均 JSON 无堆栈（002 实测）；**唯一例外是 404 未知路径**（见差异 P3-1）。

### 3. CONTEXT.md 领域词汇 vs 代码/接口语义 — 基本一致，2 处措辞偏窄（P2-2、P3-4/P3-5）

| 词条（CONTEXT 行） | 代码/接口 | 结论 |
|---|---|---|
| 真源（:11-12）数据库唯一权威位置 | route_config 表 + 两套 DbRouteDefinitionLocator（backend JPA / gateway-demo JdbcTemplate） | ✓ |
| 保存（:15-17）写入真源立即生效、保存前必须校验 | RouteService.create/update + validator + afterCommit 刷新 | ✓ |
| 发布（:19-20）v2 概念、当前保存即发布 | 无草稿状态机 | ✓ |
| 生效路由（:22-24）网关中实际生效、状态页展示 | GatewayStatusService.effectiveRoutes ← RouteDefinitionLocator（复合定位器，DB 直读；语义细节见 P3-6） | ✓（P3 备注） |
| 停用（:26-28）保留配置可恢复，vs 删除不可恢复 | setEnabled(false) 仅翻转 enabled；delete 删行 | ✓ |
| 路由校验（:30-31）保存前检查 | RouteValidator | ✓ |
| 操作审计（:33-34）「每次保存、删除动作」 | AuditService 实际记录 CREATE/UPDATE/DISABLE/ENABLE/DELETE + IP | ⚠️ 词条偏窄（P3-4） |
| 用户/角色（:36-42）ADMIN 可读写、VIEWER 只能查看 | 种子账号 + 规则 | ⚠️「VIEWER 只能查看」与可改自己密码并存（P3-5） |
| 权限规则（:44-46）方法+路径+角色+优先级、首个命中、即时生效 | permission 模块 + AtomicReference reload | ✓ |
| 内置规则（:48-49）不可删除 | PermissionRuleService.delete 拒绝 builtin | ✓（002 实测 400） |
| 网关实例（:51-52）「当前版本为单实例部署」 | backend 内嵌网关 + external-gateways **列表**管理多个外部网关实例 | ⚠️ 过时（P2-2） |

### 4. ADR 各决策 vs 实现 — 一致（除 2 处声明范围问题）

- **0001**（DB 真源/共用表/热刷新/保留 Nacos 抽象余地）：✓ `DbRouteDefinitionLocator implements RouteDefinitionLocator`，backend 与 gateway-demo 共读 route_config 表；保存后 RefreshRoutesEvent。
- **0002**（内嵌于网关进程/API 按模块/只管理路由/多实例刷新传播留待多实例版本）：✓ 内嵌与模块化属实、无全局过滤器管理；**「多实例刷新传播（消息总线/轮询）留待多实例版本」与已实现的外部网关推送+轮询冲突（P2-2）**。
- **0003**（保存即生效/审计弥补无草稿）：✓ RouteService + AuditService；无草稿/发布两阶段。
- **0004**（Boot 3.5.x + Cloud 2025.0.x + gateway-server-webflux）：✓ pom.xml Boot 3.5.16 / Cloud 2025.0.3 / `spring-cloud-starter-gateway-server-webflux`；README:44「Gateway 4.3.x」与 4.3.5 相符。注：gateway-demo 用 Cloud 2025.0.0 + 经典 `spring-cloud-starter-gateway`，与 backend 不一致但 ADR 0004 只约束 backend（交付物/架构评审已另记）。
- **0005**（权限 DB 化/ReactiveAuthorizationManager/优先级/自我保护）：✓ 规则库化、动态授权管理器、优先级匹配、内置规则种子与 v1 硬编码（git show f403f5e:SecurityConfig）行为一致（GET/POST-validate/PUT-password → AUTHENTICATED；POST/PUT/DELETE /api/routes/** → ADMIN；兜底 anyExchange → authenticated，对应 `* /**` AUTHENTICATED 999）；**「任何规则改动不得导致 ADMIN 失去权限配置模块访问权」仅实现 POST 单路径守卫（P2-3）**。

### 5. 使用手册 vs 行为 — 基本一致，1 处内部矛盾（P2-1）+ 若干 P3

- 示例路由 JSON（:175-191，order-service 完整示例）与第 7 章 Predicate/Filter 示例逐条按 RouteValidator 语义核对：Path/Method/Host/Header/Query/Cookie/RemoteAddr/Weight、StripPrefix/AddRequestHeader/Retry/RequestSize/DedupeResponseHeader 等 args 均能被网关工厂绑定器接受（字符串/数字/布尔值 + 逗号分隔列表转换），**可通过 validate**；集成测试 `RouteValidatorTest` 的合法用例（Path+AddRequestHeader）亦通过。
- 校验规则 5.2（routeId 格式/唯一性/不可改 ID、uri 协议白名单、启用需 ≥1 predicate、工厂名存在、参数试绑定、值类型白名单）：与 RouteValidator/RouteService/RouteDto 逐条相符 ✓。
- 页面操作（6.2 新建/6.3 JSON 模式/6.4 编辑 ID 禁改/6.5 停用启用前重新校验/6.6 删除二次确认+审计/6.7 状态页刷新验证/6.8 审计展开行）：与 RouteListView/GatewayStatusView/AuditLogView/RouteEditorDrawer 逐条相符 ✓；「启用前会再次校验」对应 `RouteService.setEnabled`（enabled=true 时 ensureValid）✓。
- 外部网关对接（第 10 章）：6 个类、`@EnableScheduling`、`X-Internal-Token`、5s 轮询、推送后 markRefreshed 防重复刷新、重启从库重载、状态可视化——与 gateway-demo 实现逐条相符 ✓；**仅 :383「（行数, 最大版本号）」与代码 SUM(version) 不符（P2-1）**，且与同手册 :402「（行数 + 版本号总和）」自相矛盾。
- 权限配置（第 11 章）：引导规则两条（login/health）✓；规则字段/角色语义/优先级匹配/即时生效 ✓；自我保护「两类危险操作」与实现/测试（PermissionRuleIntegrationTest 场景 7/8）相符，但范围问题见 P2-3。
- FAQ（第 9 章）：Q3（停用不出现在生效列表）、Q10（401/403 JSON 不弹 Basic 框，对应 SecurityConfig writeJson + commit e47ab58）✓。

### 6. 已知边界 vs 实际 — 1 处过时（P2-2）

README:164-168 / 使用手册:441-450 声称「单实例网关；多实例刷新传播（Redis pub/sub 等）留待后续版本」「v1 按单实例设计」——但仓库已实现「管理独立部署网关」功能（README:21-40、使用手册第 10 章、gateway-demo），即通过 HTTP 推送 + 5s 版本轮询向**多个外部网关实例**传播刷新。该边界声明与功能描述自相矛盾，属文档过时（外部网关功能为后续 commit 85e2a28 等加入，边界章节未同步更新）。「无草稿/发布」与「只管理路由不管理全局过滤器」两条边界仍成立 ✓。

### 7. 领域词汇与 README API 摘要 — 已并入第 2、3 节

接口命名（routeId/uri/order/predicates/filters/metadata/enabled、effectiveRoutes、externalGateways、actorUsername/beforeJson/afterJson/ip、permission_rule 的 httpMethod/pathPattern/roles/priority/builtin）与 CONTEXT 词条、README 摘要表一致 ✓。

---

## 三、差异清单（P0-P3）

### P0 — 无

### P1 — 无

### P2（3 条，全部为「文档过时/措辞宽于实现」，无代码错）

**P2-1 使用手册内部矛盾：轮询校验和表述过时（「最大版本号」→ 已改为 SUM(version)）**
- 声明出处：`docs/使用手册.md:383`（10.1 表格「RouteSyncScheduler | 每 5 秒比较（行数, 最大版本号），变化即刷新」）。
- 实际行为：`gateway-demo/.../RouteSyncScheduler.java:66-72` 校验和为 `SELECT CONCAT(COUNT(*), ':', COALESCE(SUM(version), 0))`——**版本号总和**，非最大版本号（commit bf1b7d7 专为此修复「更新非最大版本行/多行同版本时漏检」）。
- 判定：**文档过时**。且同手册 :402 已写「（行数 + 版本号总和）」，:383 未同步，属文档内部不一致。README:38「5 秒版本轮询兜底」措辞宽泛不冲突。

**P2-2 「单实例/多实例刷新传播留待后续」表述与新功能自相矛盾**
- 声明出处：`CONTEXT.md:52`（网关实例「当前版本为单实例部署」）、`README.md:166`（「单实例网关；多实例刷新传播（Redis pub/sub 等）留待后续版本」）、`docs/adr/0002.md:7`（「多实例刷新传播（消息总线/轮询）留待多实例版本」）、`docs/使用手册.md:445`（「v1 按单实例设计」）。
- 实际行为：仓库已实现外部网关管理——`config/ExternalGatewayRefreshService.refreshAll()`（保存后向 external-gateways **列表**推送）+ `gateway-demo/.../RouteSyncScheduler`（5s 版本轮询兜底）+ 状态页聚合（`gateway/ExternalGatewayStatusService`）。即已具备向多实例传播刷新（HTTP 推送 + 轮询）的能力，README:21-40 与使用手册第 10 章自证了这一点。
- 判定：**文档过时/表述矛盾**（外部网关功能为后续 commit 5e15851、85e2a28 加入，边界与 CONTEXT 词条未同步）。建议改为「内嵌网关为单实例；外部网关通过推送+轮询管理」之类表述。

**P2-3 权限自我保护承诺宽于实现：仅守卫 POST 单一路径**
- 声明出处：`docs/adr/0005.md:5`（「任何规则改动不得导致 ADMIN 失去对权限配置模块的访问权（否则拒绝保存）」）、`docs/使用手册.md:437-438`（「系统会拦截两类危险操作：删除内置规则；以及『保存后 ADMIN 将失去权限配置模块访问权』的改动」）。
- 实际行为：`permission/PermissionRuleService.guardAdminSelfAccess` 只对 `POST /api/permission-rules/__guard__` 这一个代表路径做匹配断言（:33, :149-162）；PUT/GET/DELETE 的内置规则可被改为不含 ADMIN 而保存成功。由于兜底种子规则 `* /**` AUTHENTICATED(999) 存在，ADMIN 名义上不会「失去访问权」（字面不违约），但实际效果是把对应写接口对**所有登录用户**开放（权限提升路径），文档未提示。集成测试 PermissionRuleIntegrationTest 场景 7/8 也只覆盖 POST 场景。
- 判定：**措辞宽于实现**（实现刻意只守 POST；测试充分性评审 ticket 006 已另记「guard 只防 POST」）。建议文档写明「仅拦截会移除 ADMIN 对权限配置模块写权限的改动（以 POST 为代表路径）」，或实现扩展到 GET/PUT/DELETE 代表路径。

### P3（7 条，观感/措辞/文档缺口）

**P3-1 统一响应体有例外：404 未知路径非统一 JSON**
- 声明出处：`README.md:134`（「统一响应体：{code,message,data}」）、`docs/使用手册.md:351`（401/403 JSON 声称）。
- 实际行为：`common/GlobalExceptionHandler` 未覆盖 WebFlux 默认 404（未知路径落到静态资源处理器），002 实测返回 Spring 默认措辞 `No static resource api/xxx.`，非 `{code,message,data}` 形态。401/403/405/409 均统一 JSON。
- 判定：**文档过时/措辞过宽**（002 已标 P3 观感问题）。建议 README 统一响应体声明注明「404 未知路径除外」或补一个 404 JSON handler。

**P3-2 改密不吊销旧 token：行为存在但文档未声明**
- 声明出处：`README.md:7,108`（仅声明 JWT 12 小时过期）、`docs/使用手册.md:117`（「登录后可在右上角用户菜单修改自己的密码」，未提 token 影响）。
- 实际行为：`auth/AuthService.changePassword` 仅更新密码哈希；JWT 无版本/吊销机制，002 实测改密后旧 token 调 /me 仍 200。无文档声称「改密后旧 token 失效」，故非矛盾，属**文档缺口**（安全评审维度已另行记录）。建议在使用手册 FAQ 或已知边界补充说明。

**P3-3 「需带 X-Internal-Token」措辞不严谨**
- 声明出处：`docs/使用手册.md:384`（RouteRefreshController「需带 `X-Internal-Token`」）。
- 实际行为：`gateway-demo/.../RouteRefreshController.requireToken`（:67-74）——**internal-token 配置为空时跳过鉴权**（默认配置非空故默认强制）。
- 判定：**措辞不严谨**（默认行为相符）。建议注明「token 配置为空时跳过校验」。

**P3-4 CONTEXT「操作审计」词条表述偏窄**
- 声明出处：`CONTEXT.md:33-34`（「对路由配置的每次保存、删除动作留下的记录：操作者、时间、目标路由、变更前后内容」）。
- 实际行为：`audit/AuditService.record` 记录 CREATE/UPDATE/DISABLE/ENABLE/DELETE 五类动作且含 IP（README:12 表述更全）。
- 判定：**文档过时/表述偏窄**（「保存、删除」未覆盖停用/启用与 IP）。建议与 README 对齐。

**P3-5 「VIEWER 只读」与可修改自己密码并存**
- 声明出处：`README.md:7`（VIEWER（只读））、`CONTEXT.md:41`（VIEWER 只能查看）、`docs/使用手册.md:114`（VIEWER 仅查看）。
- 实际行为：`PUT /api/auth/password` 规则为 AUTHENTICATED(30)，VIEWER 可改自己密码（写操作）；路由/规则写操作确实 403（002 实测）。
- 判定：**措辞边界模糊**（语境限定于路由配置时成立，字面上「只读」不严格）。建议限定「对路由/权限配置只读」。

**P3-6 状态页「生效路由」实为定位器 DB 直读，与 CONTEXT「网关中实际生效」语义在瞬时态有偏差**
- 声明出处：`CONTEXT.md:22-24`（生效路由=「当前已在网关中实际生效、参与转发匹配」）、`docs/使用手册.md:44`（GatewayStatusService 只读展示当前生效路由）。
- 实际行为：`gateway/GatewayStatusService.status` 经 `@Qualifier("routeDefinitionLocator")` 注入的是 Gateway 4.3.5 `GatewayAutoConfiguration` 的 `@Primary CompositeRouteDefinitionLocator`（内含 DbRouteDefinitionLocator，每次请求直查 DB 启用路由），而非网关内存路由表 `CachingRouteLocator`。正常情况下二者在刷新后一致；若刷新事件未处理完，状态页可能先行展示 DB 内容。架构评审 ticket 003 已记为 P2「状态页生效路由实为 DB 直读」。
- 判定：**实现语义与文档措辞的细微偏差**（常态一致，属可接受的实现选择，此处按规范一致性降为 P3 备注）。

**P3-7 顺带观察（非差异，供参考）**：`RouteRequest` 的 predicates/filters/metadata 无长度上限而 DB 列为 VARCHAR(5000)，超长配置落库报错，手册 5.2 校验规则未提及该边界（无矛盾，仅文档未覆盖）；H2 历史遗留 `smoke-demo` 停用路由（002 事实）与「首次启动写入 2 条 httpbin 示例路由」声明不冲突（历史数据）。

---

## 四、结论摘要（供合成报告引用）

- 一致性等级：**一致（高）**——核心链路（保存即生效/停用语义/动态权限/审计/外部网关/API 面）文档与代码、运行时事实三方吻合；17 个 API 端点与方法/权限逐条一致。
- 差异共 **10 条**：P0×0、P1×0、P2×3、P3×7；**无代码错**，全部为文档过时/措辞宽于实现/文档缺口。
- 三条 P2 建议优先修订：① 使用手册:383 轮询校验和表述（SUM(version)）；② 单实例/多实例边界与外部网关功能自相矛盾（CONTEXT:52、README:166、ADR 0002:7、使用手册:445）；③ 权限自我保护仅守卫 POST 路径，承诺宽于实现（ADR 0005:5、使用手册:437-438）。
