# 测试充分性评审 — 完整发现

Labels: wayfinder:research
Ticket: [测试充分性评审](../tickets/006-test-adequacy-review.md)
分支：`research/test-adequacy-review`（从 main @ bf1b7d7 切出）
方法：静态阅读全部测试与相关业务代码；未运行任何测试（运行事实以 [验证测试套件跑通](../tickets/001-verify-test-suites.md) 的 12/12 全绿 / 前端 4/4 为基线）。

---

## 0. 总体判断

**方向正确、深度不足：属于"冒烟 + 关键成功路径"级别，不是"充分"级别。**

- 后端 12 测（4 类）验证了主要成功路径与少数关键失败路径（401/403/405/409/400 自我保护/离线状态），但**断言以状态码级为主**，数据级断言只集中在 1 个长测里；纯逻辑单元测试仅 1 类（RouteValidator）。
- 前端 1450 行逻辑只有 51 行工具函数（`routeJson.ts`）有 4 个用例，组件/store/router/api 全部零测试。
- gateway-demo（外部网关侧全部业务代码，含已确认缺陷所在文件）**整个模块零测试**。
- 最强证据：架构评审已确认的缺陷（create 500 非 409、内部 token 空放行、markRefreshed 竞态、校验和碰撞、审计序列化静默 null）**全部溜过测试网**——因为对应的失败路径一个测试都没有；本次评审还新发现一个 guard 设计洞（见 P1-3），同样无测试。
- 分级结论：**P0 无**（测试缺口本身不构成"阻断上线"的单一断点，最重缺陷在架构评审维度为 P2，其缺测试为 P1）；**P1 ×6、P2 ×5、P3 ×4**。

---

## 1. 现有覆盖逐类评估（断言强度）

### 1.1 GatewayDashboardIntegrationTest（4 测）— 断言强度：状态码级为主，1 个长测含数据断言

文件：`backend/src/test/java/com/gatewaydashboard/GatewayDashboardIntegrationTest.java`

| 用例 | 覆盖 | 断言强度 |
|---|---|---|
| `loginFailsWithWrongPassword`（:21-28） | 错密码登录拒绝 | 仅状态码 401 |
| `protectedEndpointRejectsAnonymousRequest`（:30-39） | 匿名 401 + 无 WWW-Authenticate + 统一 JSON | 状态码 + `code==401` + `message` 非空（**message 内容不校验**） |
| `wrongMethodReturnsJson405`（:41-49） | 405 统一 JSON | 状态码 + `code==405` + `message` 非空 |
| `fullRouteLifecycle`（:51-156） | 种子路由可见 → 创建 → 本地生效 → 停用 → 不生效 → 配置仍在 → 重复创建 409 → validate 未知工厂 false → viewer 写 403 → 审计 CREATE 存在 | **唯一数据级断言**：`data[?(@.routeId==…)]` 的存在/不存在、`enabled==false`、`valid==false`、审计条目存在 |

评价：
- 生命周期测试是**有价值的端到端验证**——它证明「创建 → RefreshRoutesEvent → DbRouteDefinitionLocator 重读 → /api/gateway/status 反映」这条本地热刷新链是通的（:82-88、:100-105），这是"保存即生效"（ADR 0003）的核心证据。
- 但注意其边界：断言的是**定位器重读 DB 后暴露的路由定义**，不是**网关真实转发流量**。没有任何测试发一个实际请求穿过网关断言路由真的转发（例如 GET 一个被代理的路径返回 200）。"路由可用"这一层从未被验证。
- 审计断言只查 `action=='CREATE'` 存在（:155），不验 before/after 内容——`RouteAssembler.toJson` 序列化失败静默返回 null（`RouteAssembler.java:98-104`）时审计会写成 null，此测试抓不到。
- 长测任一中间断言失败即整测红，定位成本高（见 P3-12）。

### 1.2 PermissionRuleIntegrationTest（1 个 8 步长测）— 断言强度：状态码级 + 少量数据流

文件：`backend/src/test/java/com/gatewaydashboard/PermissionRuleIntegrationTest.java`

覆盖（:27-107）：viewer 看规则 403 → admin 看内置规则（`size>=11`、取到 builtin 规则）→ 新增 VIEWER 可建路由规则**即时生效**（viewer POST 200）→ 删除**即时失效**（viewer POST 403）→ 内置规则 DELETE 400 → 两类自我保护（新增/修改出 VIEWER-only 的权限配置写规则）均 400。

评价：
- 动态权限"即时生效/即时失效"两个方向都被验证，是这套测试里**含金量较高**的一块。
- 但**全部通过 HTTP 黑盒断言状态码**；规则 id 靠 `(Number)created.get("id")` 提取后反哺后续请求（:60、:88），是唯一的"数据流"。
- **自我保护只覆盖了 POST 写路径**。`guardAdminSelfAccess` 只用合成路径 `/api/permission-rules/__guard__` + 方法 **POST** 做检查（`PermissionRuleService.java:33,149-162`），测试也就只测了 POST 场景（:95-106）。PUT/DELETE 的锁死场景**代码没防、测试也没测**——这是本次评审新发现的真实设计洞（详见 P1-3）。
- 无空 roles / `*` 角色 / httpMethod `*` / 优先级并列决胜 / 并发 reload 任何边界测试。

### 1.3 ExternalGatewayStatusIntegrationTest（1 测）— 断言强度：只验离线分支

文件：`backend/src/test/java/com/gatewaydashboard/ExternalGatewayStatusIntegrationTest.java`（:20-36）

断言 `online==false`、`effectiveRoutes.length()==0`、`error` 非空（:32-35）。这是**测试配置刻意为之**（`application-test.yml:9-12` 指向不可达端口 19999，001 记录的 WARN 即此）。

评价：
- 只覆盖 `ExternalGatewayStatusService.fetchOne` 的 `onErrorResume` 离线分支（`ExternalGatewayStatusService.java:72-78`）。
- **在线分支（:65-71，成功解析响应、聚合生效路由、PushInfo）零覆盖**；`ExternalGatewayRefreshService.refreshAll` 的推送成功/失败/401/多网关（`ExternalGatewayRefreshService.java:31-53`）零覆盖。也就是说"外部网关"这个跨进程链路，测试只证明了"它挂了我能显示挂了"，没证明"它活着我能推/能看"。
- `error` 只断言非空不校验内容；依赖本机 19999 无人占用（若 CI 机器上有服务占用该端口，断言会翻绿变假——稳定性隐患，见 P3-14）。

### 1.4 RouteValidatorTest（6 测）— 断言强度：唯一真正的逻辑级测试

文件：`backend/src/test/java/com/gatewaydashboard/route/RouteValidatorTest.java`

覆盖：合法通过（:24-34）、未知 predicate 工厂失败且错误文案含"未知的工厂名"（:36-46）、启用无 predicate 失败（:48-55）、停用无 predicate 通过（:57-64）、非法 scheme ftp 失败（:66-74）、嵌套 args 值失败（:76-84）。

评价：
- 命名清晰、断言有内容（`errors()` 文案），是 4 个类里可读性最好的。
- 缺点：**仍是 `@SpringBootTest` 全上下文**（:17-18，MOCK 环境），一个纯函数式校验器拉起了整个 Spring 上下文——维护成本与隔离性都不如轻量测试（见 P3-15）。
- 未覆盖分支：routeId 空/非法字符（:50-54）、uri 为空/非法 URI（URI.create 抛异常路径，:59-67）、filter 未知工厂/参数不合法、args 为 null 值（:106-109 的 null→"" 分支）、step 缺 name（:93-96）、enabled 为 null 的默认语义（:70）。
- **`existingRouteId` 参数在 `RouteValidator.validate` 签名里有（`RouteValidator.java:47`）但实现完全未使用**——一个死参数，测试也没能暴露它。

### 1.5 前端 routeJson.test.ts（4 测）— 断言强度：中

文件：`frontend/src/utils/routeJson.test.ts`（vs `frontend/src/utils/routeJson.ts` 51 行）

覆盖：normalizeRequest 字段/order 字符串→数字（:5-18）、parseRequestJson 合法 JSON（:20-33）、畸形 JSON 抛"JSON 格式错误"（:35-37）、validateRequestClient 空表单 3 错/合法 0 错（:39-42）。

评价：
- 唯一亮点是畸形 JSON 的抛错断言（:35-37）。
- 未覆盖：`toRequestJson`（**零测试**，:3）、normalizeRequest 的 `enabled` 默认 true（`raw.enabled !== false`，routeJson.ts:33）、order 非数字字符串 → NaN（:32）、metadata/predicates 非对象输入（:19-28 的兜底）、validateRequestClient 的 routeId 正则分支（:43-45 第 2 条错误）与"启用无 predicate"分支（:47-49）、parseRequestJson 对 `"123"`/`"null"` 等非对象 JSON 的行为。
- 测试环境为 `node`（`vite.config.ts:23`），无 DOM 环境、无 `@vue/test-utils`/happy-dom 依赖（`frontend/package.json` devDeps 无）——**这是"前端无组件测试"的工具性原因**。

---

## 2. 缺口清单（P0-P3）

### P0：无

测试缺口层面不存在单一"阻断上线"断点。说明：最重的缺陷（内部 token 空放行、create 500 非 409）在架构评审维度已被定为 P2；本维度对应结论是"这些缺陷的**回归测试缺失**"属 P1（下条），而非 P0。

### P1-1｜外部网关推送（refreshAll）/在线链路零测试 —— 无 mock 服务器测试

- 位置：`ExternalGatewayRefreshService.java`（推送）、`ExternalGatewayStatusService.java`（聚合）均无任何测试；`GatewayDashboardIntegrationTest`/`ExternalGatewayStatusIntegrationTest` 只覆盖离线分支。
- 缺什么：① 推送成功：mock 一个 HTTP 服务器接收 `POST {baseUrl}/internal/routes/refresh`，断言 URL 拼接（:37）、`X-Internal-Token` 头（:40）、`pushRecords` 记 success=true（:44-46）；② 推送失败/401/超时：断言 success=false + error 记录、且**不影响保存结果**（fire-and-forget 语义，:43-51）；③ 多网关逐个推送；④ 在线状态聚合：mock 返回 `/internal/routes` 的合法响应，断言 `online=true`、effectiveRoutes、PushInfo 透传（`ExternalGatewayStatusService.java:65-71`）。
- 为什么：推送是"保存即生效"跨进程链路的**执行端**（ADR 0001/0003 的另一半），且是 fire-and-forget——失败只 `log.warn`（:50）。一旦 URL 拼接、token 头、响应解析任一环节出错，测试全绿但外部网关永远收不到刷新。001 的运行事实里"19999 WARN"恰好证明该路径**被执行过但从未被断言过**。
- 补法：OkHttp MockWebServer 或 WireMock；test profile 的 external-gateways 指向 mock 端口；推送后断言 mock 收到请求（含 header），再断言 `getPushRecord`。

### P1-2｜已确认缺陷无回归测试（create 500 非 409、markRefreshed 竞态、内部 token 空放行）

- 位置：
  - create 并发唯一冲突 → `RouteService.create` 无 catch（`RouteService.java:43-53`），走 `GlobalExceptionHandler` 兜底 500（`GlobalExceptionHandler.java:69-74`）。现有 `fullRouteLifecycle` 只测了**顺序**重复创建走 `existsByRouteId` 预检 → 409（`RouteService.java:45-47`），测不到**并发**下两请求同时过预检、DB 唯一约束抛 `DataIntegrityViolationException` → 500 的路径。
  - gateway-demo `RouteSyncScheduler` 的 `poll()`/`markRefreshed()` 非原子读改写竞态（`RouteSyncScheduler.java:23-24,40-64`，volatile 双字段）。
  - `RouteRefreshController.requireToken` 的"token 配置为空则跳过校验"（`gateway-demo/.../RouteRefreshController.java:67-74`）。
- 缺什么：① 并发/直接改库制造唯一冲突，断言返回 409 而非 500；② gateway-demo 的 scheduler 单测：注入 mock JdbcTemplate，断言校验和变化触发刷新、`markRefreshed` 后不重复触发、异常时返回 null 不崩（:66-77）；③ `RouteRefreshController` 的 token 测试：正确/错误/缺失 token 三态 + **token 为空配置时匿名可调用**（把已知缺陷固化为可观测行为）。
- 为什么：这些是评审**已确认存在**的缺陷，而测试网一条都没抓住——这是"充分性不足"最直接的反证。不补回归测试，后续"修复"无法证明修好、也无法防回退。
- 补法：见上，均为常规 mock/并发测试；gateway-demo 至少加 `@WebFluxTest(RouteRefreshController)` 级切片测试。

### P1-3｜权限规则边界缺失：空 roles、`*` 覆盖全部、PUT/DELETE 自锁守卫洞

- 位置：`PermissionRuleDtos.java:13-21`（roles 仅 `@NotBlank`）、`PermissionRuleService.java:131-144`（validateRequest **不校验 roles 内容**）、:178-185（normalizeRoles：纯空白串 → 空字符串 → `CachedRule` roles=Set.of("") → 永不匹配）、`DynamicPermissionAuthorizationManager.java:39-41`（roles 含 `*` → **不检查认证直接放行**）、`PermissionRuleService.java:149-162`（guard 只查 POST + 合成路径）。
- 缺什么：
  1. `roles:" , "`（空白串）建规则：当前会成功保存一条**死规则**（不匹配任何人），且高优先级死规则会遮蔽其后所有规则 → 相关接口全员 403——无测试。
  2. `roles:"*"` 建规则：按实现（DynamicPermissionAuthorizationManager.java:39-41）**匿名请求也放行**——若配置成 `*` + `/api/routes/**` 高优先级，管理接口公开。语义是否 intended 存疑，但**行为从未被测试固化**。
  3. httpMethod `*`（匹配所有方法，`PermissionRuleService.java:203-208`）：与 roles 组合的放行面无人测试。
  4. **guard 只防 POST，不防 PUT/DELETE**（:158 硬编码 `"POST"` + `SELF_GUARD_PATH`）：新增一条 `PUT /api/permission-rules/**` roles=VIEWER priority=1 的规则，guard 匹配 POST 仍命中内置 ADMIN 规则 → **放行**；保存后 ADMIN 的 PUT 写接口被遮蔽 → 403 → **admin 被锁死且无法用 UI 自解**（只能直接改库）。这是本次评审**新发现的设计洞**，现有测试（:95-106）恰好只测了 POST 场景所以全绿。
- 为什么：权限规则是动态授权全站安全的地基（ADR 0005），边界行为（空/通配/守卫覆盖面）直接决定"谁能不能碰什么"，错配即安全事件或自锁事故；guard 的 POST-only 是确定性可复现的锁死路径。
- 补法：① 集成测试逐条验证上述 1-3（建后断言实际 HTTP 行为 + 清理）；② 对 PUT/DELETE 复制 :95-106 的自保护断言——**当前会失败（400 预期落空），即测出 guard 洞**，随后修 `guardAdminSelfAccess` 同时检查 POST/PUT/DELETE 三方法；③ 加 roles 内容校验（拒绝空白/未知角色）时用测试钉住。

### P1-4｜改密流程零测试

- 位置：`AuthService.changePassword`（`AuthService.java:41-50`）、`AuthController` PUT `/api/auth/password`（`AuthController.java:37-42`）、`AuthDtos.ChangePasswordRequest`（`AuthDtos.java:14-16`，新密码 `@Size(min=6)`）。
- 缺什么：① 正确改密后：旧密码登录 401、新密码登录 200、`/api/auth/me` 正常；② 原密码错误 → 400（:45-46）；③ 新密码 <6 位 → 400（bean validation）；④ **改密后旧 JWT 仍有效**（已知设计缺陷：无服务端吊销，project-inventory §7-10）——至少用一个测试把该行为固化为"已知边界"，防止将来误以为已吊销；⑤ viewer 改自己密码成功、互不影响。
- 为什么：改密是账号安全关键路径，目前全站唯一一个"改了什么后登录态如何"的测试是登录错密码 401。缺测试意味着：BCrypt 重编码、事务、旧 token 语义任何一处回归都无声无息。
- 补法：一个 `AuthIntegrationTest`（或并入现有集成类）覆盖 ①-④，5 个用例以内。

### P1-5｜审计仅 CREATE 有断言

- 位置：`AuditService.java`（record :20-29、truncate 5000 :44-49、分页钳制 :32-35）、`RouteService.java`（UPDATE :70、DELETE :79、ENABLE/DISABLE :95、同状态短路不记审计 :89-91）。
- 缺什么：① UPDATE/DELETE/ENABLE/DISABLE 各动作的审计条目存在性 + `action` 值；② before/after 内容：UPDATE 有 before≠after、DELETE 的 after 为 null（:79）、CREATE 的 before 为 null（:50）；③ 超 5000 字符的 predicates/metadata 落库被截断（`AuditService.java:44-49`）；④ **同状态重复 POST /enabled 不产生审计**（`RouteService.java:89-91` 短路 return）——这是文档没写的行为，测试应钉住；⑤ 分页钳制（size>100 落 100、page<1 落 1，:33-34）。
- 为什么：审计是"保存即生效、无草稿发布"模式下唯一的追溯手段（ADR 0003）。动作枚举写错（如把 DISABLE 记成 ENABLE）、before/after 丢失（`RouteAssembler.toJson` 静默 null，`RouteAssembler.java:98-104`）、截断失效——现有测试一个都抓不住。
- 补法：在 `fullRouteLifecycle` 扩展或新建审计测试，对每个动作断言 action + before/after 关键字段。

### P1-6｜gateway-demo 零测试（整个模块）

- 位置：`gateway-demo/` 无 `src/test`（glob 全仓仅 backend 4 类 + 前端 1 文件）。模块内容：`RouteRefreshController`（token 校验 + 刷新 + 生效路由查询）、`RouteSyncScheduler`（轮询校验和 + markRefreshed）、`DbRouteDefinitionLocator`（JdbcTemplate 直读，与 backend JPA 版**两套并行实现**）、`RouteRefreshPublisher`、`RouteSyncProperties`。
- 缺什么：见 P1-2（token 三态、scheduler 竞态）+ `DbRouteDefinitionLocator` 从 DB 行到 `RouteDefinition` 的映射（含 predicates/filters/metadata JSON 解析失败路径）；`RouteSyncProperties` 配置绑定（默认 token）。
- 为什么：这是外部网关的**实际运行代码**，是 ADR 0001"网关侧"的执行者，零测试意味着该模块任何改动（如已发生的 SUM(version) 修复，bf1b7d7）都无安全网；且已确认缺陷（token 空放行、markRefreshed 竞态、校验和碰撞）全在这一模块。
- 补法：pom 加 test 依赖（spring-boot-starter-test + H2 或 mock），至少覆盖 RouteRefreshController（@WebFluxTest）与 RouteSyncScheduler（纯单测 mock JdbcTemplate）——这是全仓性价比最高的补测点之一。

### P2-7｜乐观锁 409 无测试

- 位置：`RouteConfig.@Version`（`RouteConfig.java:50-52`）、`RouteService.update` 显式 catch → 409（`RouteService.java:65-69`）、`GlobalExceptionHandler` 兜底 OOLFE → 409（`GlobalExceptionHandler.java:27-31`）。
- 缺什么：制造版本冲突的测试——两个"客户端"先后修改同一路由（第二个带着已过期版本提交），断言 409 + message"已被其他操作修改"；同时验证 409 后配置未被污染、重新拉取可继续编辑。注：`RouteRequest` 无 version 字段（`RouteDto.java:15-26`），冲突只能靠并发/直接改库 bump version 触发——测试需要 H2 直连或第二个请求在事务未提交时并发提交。
- 为什么：乐观锁是并发写安全的核心机制，当前"409 能返回"仅靠代码阅读推断；`setEnabled`/`delete` 甚至没有本地 catch（依赖全局兜底 :27-31），任何一处版本语义变化（如 flush 时机、级联）都可能让 409 变 500。
- 补法：并发 PUT（两个线程/两个 WebTestClient 交错）或测试内直接 `routeConfigRepository` 改 version 后走 API PUT。

### P2-8｜API 失败路径覆盖缺失（校验拒绝、404、enabled 边界、meta、搜索、审计钳制）

- 位置：`RouteService.ensureValid` → 400（`RouteService.java:123-128`）、update 改 routeId → 400（:58-60）、setEnabled 启用时校验既有配置 → 400（:86-88）、`RouteController.setEnabled` body 缺 `enabled` 默认 disable（`RouteController.java:66-68`）、get/update/delete 不存在 → 404（`RouteService.java:130-133`）、`/api/meta/factories`（`RouteMetaController`）、搜索 keyword（`RouteService.java:30-35` + `RouteConfigRepository.search`）、审计分页钳制（`AuditService.java:32-35`）。
- 缺什么：以上每条对应的"坏输入"测试。目前 API 级只有成功路径 + 3 个安全状态码。
- 为什么：这些是 Controller/Service 层最容易被重构改坏的边界（例如 `/enabled` 空 body 默认 disable 是**危险默认**——前端误发空 body 会停掉一条路由），全无测试意味着行为只靠文档，不靠验证。
- 补法：在现有集成类各加 1-2 个坏输入用例；`/api/meta/factories` 与搜索各 1 测。

### P2-9｜JWT 安全路径测试缺失

- 位置：`JwtAuthenticationFilter`（无效 token 静默吞 → 匿名 → 401，`JwtAuthenticationFilter.java:34`）、`JwtService`（过期 12h）、种子规则 `GET /api/**` AUTHENTICATED（`V2__permission_rule.sql:18`）。
- 缺什么：① 乱写 token → 401（当前只有"无 token"测试）；② 过期 token → 401；③ viewer GET `/api/audit-logs`、`/api/gateway/status` 的行为（按种子规则 viewer 可读审计与网关状态——**从未被测试钉住**）；④ `/api/auth/me` 需登录；⑤ 登录接口对已禁用用户的行为（`AuthService.java:25-27`，种子无禁用账号，需测试自造）。
- 为什么：401/403 是全站安全边界，现有测试只覆盖了"无 token"与"viewer 写 403"两种，其他组合（坏 token、角色-接口矩阵）全靠推断。
- 补法：集成测试加坏/过期 token 用例 + viewer×各 GET 接口矩阵断言。

### P2-10｜前端组件/store/router/api 零测试（且缺 DOM 测试设施）

- 位置：`frontend/src` 约 1450 行，仅 `utils/routeJson.ts` 有测试。重点未测逻辑：`RouteEditorDrawer.save()` 的"客户端校验 → `/api/routes/validate` → 通过才 create/update"编排（`RouteEditorDrawer.vue:137-171`，含双模式表单/JSON、argsText 解析 :115-123）、`stores/auth.ts` 的 token/localStorage 持久化与 logout、`router/index.ts:16-25` 登录守卫、`api/http.ts:12-37` 的 token 注入与 401 清 token 跳登录、各 view 的确认弹窗/表格交互（`RouteListView.vue:58`、`PermissionRuleView.vue:94`、`App.vue` 改密弹窗 :37-55）。
- 缺什么：① store 与 http 拦截器（node 环境 + mock axios 即可测）；② router guard（`createMemoryHistory` + 假 store）；③ 组件测试（需要先加 `@vue/test-utils` + `happy-dom`/`jsdom`，`package.json` 当前无）；④ `vite.config.ts` test 配置加 `environment`/`setupFiles`。
- 为什么：前端是"用户唯一操作面"，validate-before-save 顺序错了（先保存后校验）会导致非法路由直落库；auth store 与拦截器是登录态唯一来源。当前零测试 = 任何重构无安全网。
- 补法：按 store/拦截器 → router guard → RouteEditorDrawer 的顺序补，各 3-5 测；工具链补齐后再扩 view 级。

### P2-11｜无覆盖率工具/门槛

- 位置：`backend/pom.xml` 无 jacoco（grep 无命中）；`frontend/package.json:10` `test: "vitest run"` 无 `--coverage`，无 coverage 依赖。
- 缺什么：行/分支覆盖率指标与门槛（如后端 ≥70%、前端 utils ≥80%）。
- 为什么：没有量化手段，"12 测覆盖了 40% 还是 80%"只能人肉估；且后续补测无法证明收敛。
- 补法：后端加 jacoco + surefire 联动（test profile 内即可）；前端 `vitest --coverage`；在 CI（若有）设门槛。

### P3-12｜测试结构与可维护性

- 位置：`PermissionRuleIntegrationTest` 单方法 8 步长测（:27-107）；`GatewayDashboardIntegrationTest.fullRouteLifecycle` 同模式（:51-156）；断言多为状态码 + `jsonPath` 字符串（脆弱，改字段名即挂且报错难读）；类无 `@DisplayName`。
- 为什么：长测定位失败成本高（一步挂全红、堆栈指向 jsonPath 而非业务步骤）；jsonPath 字符串与 DTO 字段耦合，重构时测试先行破碎。
- 建议：长测按业务阶段拆成多个 @Test（共享 login helper）；对关键响应用 `expectBody(GatewayStatusResponse.class)` 等强类型断言替代部分 jsonPath；加 @DisplayName 中文名。

### P3-13｜前端 utils 测试边角缺失

- 位置：`routeJson.test.ts`（缺 `toRequestJson`、enabled 默认 true、order NaN、非对象 JSON、正则分支等——见 1.5）。
- 为什么：工具函数是全前端唯一被测代码，边角不补则"唯一绿洲"也不完整。
- 建议：5 个用例内补齐 1.5 所列分支。

### P3-14｜测试稳定性隐患

- 位置：`ExternalGatewayStatusIntegrationTest` 依赖本机 19999 无人占用（若 CI 有服务占用，`online==false` 断言翻绿变假）；`fullRouteLifecycle` 的"创建后立即查 status 生效"（:82-88）隐含时序依赖——当前 `RefreshRoutesEvent` 同步发布所以稳定，但若未来改异步（@Async/消息队列）该断言会 flaky；H2 `DB_CLOSE_DELAY=-1`（`application-test.yml:3`）+ 三个集成类共享上下文，测试间数据残留（`it-route-*`/`perm-route-*`）靠唯一命名避免——新增测试若用固定 routeId 会互相污染。
- 为什么：001 记录"无 flaky"是基于当前 12 测；上述三点是**未来新增测试最可能的 flaky 来源**，应现在立规矩。
- 建议：mock 端口选 19999 并显式注释约束；刷新时序断言加容忍或改同步语义测试；约定测试 routeId 必须 `System.nanoTime()` 后缀。

### P3-15｜RouteValidatorTest 上下文过重 + 死参数未被发现

- 位置：`RouteValidatorTest` 用 `@SpringBootTest`（MOCK，`RouteValidatorTest.java:17-18`）测试一个纯校验器；`RouteValidator.validate` 的 `existingRouteId` 参数从未被使用（`RouteValidator.java:47`）。
- 为什么：全上下文启动拖慢单测且引入无关 bean 故障面；死参数说明校验器与调用方（`RouteService.validateOnly` 传 `request.routeId()`，`RouteService.java:120`）之间存在"看似有用实则无效"的接口——测试本应暴露。
- 建议：改 `@SpringJUnitConfig`/手动装配（RouteValidator 依赖 ApplicationContext + ConfigurationService，可用 `@MockBean` ConfigurationService 或轻量 context）；删参数或用上它（如校验 update 时 routeId 一致性）。

---

## 3. 结构与工具评估

- **上下文复用**：3 个集成类（GatewayDashboard/PermissionRule/ExternalGatewayStatus）同为 `@SpringBootTest(RANDOM_PORT)+@ActiveProfiles("test")`，Spring TestContext 缓存可共享 1 个上下文；但 `PermissionRuleIntegrationTest` 带 `@DirtiesContext(AFTER_CLASS)`（:21），会**废弃共享上下文**，其后的类需重建（若按字母序它排中间）。当前 12 测 8.8s 可接受，但新增集成类时应意识到"一个 @DirtiesContext 拉高全批成本"。
- **H2 生命周期**：`DB_CLOSE_DELAY=-1`（application-test.yml:3）使内存库跨上下文存活；种子数据靠 `existsBy*` 幂等（`SeedDataInitializer.java:34-49`）不会重复插入，但**测试写入的数据跨类残留**（依赖唯一命名，见 P3-14）。
- **失败路径覆盖**：校验拒绝只有 RouteValidator 单元级 + validate 接口 1 例；外部网关离线 1 例；其余失败路径（400/404/409 并发/改密 400/超长输入）全缺（P2-7/P2-8）。
- **工具链**：无 jacoco/覆盖率门槛（P2-11）；前端无 DOM 测试设施（P2-10）；gateway-demo 无测试依赖（P1-6）。
- **命名/断言纪律**：RouteValidatorTest 最好，其余多为状态码级；中文注释与 JSON 文本块（text block）可读性尚可。

---

## 4. 反向推断：已确认缺陷 vs 测试网（充分性不足的最强证据）

| 架构评审已确认缺陷 | 对应测试本应抓住它的位置 | 现状 |
|---|---|---|
| create 并发唯一冲突 → **500 而非 409**（架构 P2） | `RouteService.create`（:43-53）+ 全局兜底（`GlobalExceptionHandler.java:69-74`） | 测试只覆盖顺序重复创建走预检 409（`GatewayDashboardIntegrationTest.java:114-120`），并发窗口零测试 → **溜过** |
| **内部 token 空即放行**（架构 P2） | `gateway-demo/.../RouteRefreshController.java:67-74` | gateway-demo 零测试 → **溜过** |
| **markRefreshed 竞态**（架构 P2） | `RouteSyncScheduler.java:23-24,40-64` | 零测试 → **溜过** |
| 校验和碰撞（COUNT+SUM 仍有碰撞面，架构 P2） | `RouteSyncScheduler.java:66-77` | 零测试 → **溜过** |
| **审计序列化静默 null**（架构/质量 P1） | `RouteAssembler.toJson`（:98-104）→ 审计 before/after 为 null | 审计断言只查存在性（`GatewayDashboardIntegrationTest.java:155`），不查内容 → **溜过** |
| **状态页"生效路由"实为定位器读 DB**（架构 P2） | `GatewayStatusService.java:45-47`（routeDefinitionLocator） | 现有测试反而**固化了**该行为：断言的是"DB 内容进入定位器输出"，从未有"真实流量穿过网关被路由"的断言 → **缺陷行为被测试背书** |
| **guard 仅防 POST 写路径**（本次新发现） | `PermissionRuleService.java:149-162` | 测试只复制了 POST 场景（`PermissionRuleIntegrationTest.java:95-106`）→ **溜过** |

## 5. 结论

充分性判断：**不充分（后端中等偏下、前端严重不足、gateway-demo 缺失）**。现有 12 后端测 + 4 前端测构成"可重复的冒烟与关键路径验证"，能防住 401/403/405、本地热刷新链、动态权限即时生效这类大回归，但**对跨进程推送、并发与乐观锁、安全边界（token/改密/`*` 角色）、审计内容、以及 gateway-demo 全部逻辑没有任何防护**——且评审确认的 7 类缺陷全部位于无测试区域，即为实证。

修复优先级建议：先补 P1-1（外部网关 mock 推送）与 P1-6（gateway-demo 切片测试）——这两块覆盖已确认缺陷所在区域且成本低；再补 P1-3（权限边界，能立即暴露 guard 洞）、P1-4（改密）、P1-5（审计多动作）；随后 P1-2 的 create 409 回归与 P2 各项按依赖排序；工具链（P2-11 覆盖率）建议随补测同步引入。

（本文件为评审产出，未修改任何现有代码/测试文件；本次评审未运行测试。）
