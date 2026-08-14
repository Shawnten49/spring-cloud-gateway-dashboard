# 代码质量与可维护性评审 — findings

- 评审范围：`backend/src/main/java` 全部 44 个类 + `frontend/src` 全部源码（静态阅读，未启动服务、未跑测试、未改动代码）
- 评审依据：`java-coding-standards` 技能约定 + 通用最佳实践；领域词汇对照 `CONTEXT.md` 与 `docs/adr/`
- 分支：`research/code-quality-review`（一次性分支，未并入 main）

## 总体判断

**整体质量中上（B+）**：分层清晰（common / route / permission / audit / auth / gateway / config），命名基本贴合 `CONTEXT.md` 领域词汇（路由配置、保存即生效、生效路由、停用、权限规则、内置规则均有对应代码实体）；DTO 与实体分离良好（entity 与 record DTO + Assembler 转换）；不可变性与 Optional 用法规范（`findByXxx().orElseThrow(...)` 随处可见，无 `.get()` 滥用）；`@Version` 乐观锁、统一 `GlobalExceptionHandler`、审计日志、`open-in-view: false` 等都是正确选择。前端使用 Vue3 组合式 API + Pinia + `strict: true` 的 TS，API 层封装薄而清晰，且有少量单测。

**主要问题集中在五类**：

1. **WebFlux 与阻塞式 JPA 混用**（P1，架构性）——所有控制器用 `Mono.just(阻塞调用)` 把 JDBC 查询直接压在 Netty 事件循环线程上，`RouteDefinitionLocator` 与健康检查同样阻塞；
2. **静默吞异常/静默数据丢失**（P1/P2）——`RouteAssembler.toJson` 序列化失败返回 null（ticket 疑点确认）、健康检查 `catch (Exception ignored)`；
3. **安全默认值**（P1）——默认管理员口令硬编码且展示在登录页、默认 JWT 密钥/内部 token 硬编码；
4. **校验规则三层重复**（P2）——routeId 正则、httpMethod 白名单在 bean 校验、服务层、前端各一份；
5. **大量 P3 级一致性/死代码/魔法值问题**（详见清单）。

**无 P0**：未发现会导致数据损坏或系统不可用的直接缺陷；P1 均为"在真实部署/流量下会暴露"的问题。

---

## 发现清单

### P1-01 阻塞式 JPA 调用直接跑在 WebFlux 事件循环线程上（响应式反模式）

- **位置**：
  - `backend/src/main/java/com/gatewaydashboard/route/RouteController.java:33-35, 38-40, 43-47, 49-55, 57-62, 64-71, 73-76`
  - `backend/src/main/java/com/gatewaydashboard/audit/AuditController.java:24`
  - `backend/src/main/java/com/gatewaydashboard/permission/PermissionRuleController.java:27-45`
  - `backend/src/main/java/com/gatewaydashboard/auth/AuthController.java:27-41`
  - `backend/src/main/java/com/gatewaydashboard/route/RouteMetaController.java:23-30`
  - `backend/src/main/java/com/gatewaydashboard/route/DbRouteDefinitionLocator.java:17-20`
  - `backend/src/main/java/com/gatewaydashboard/gateway/GatewayStatusService.java:37-44`
- **为什么**：应用选用 WebFlux（ADR 0004，因网关本体是 WebFlux），但数据访问全是阻塞式 JPA/JDBC。控制器里 `Mono.just(routeService.list(keyword))` 的实参在 handler 被调用时**立即求值**，即整个查询（含连接池获取、SQL、事务）发生在 Netty 事件循环线程上；`DbRouteDefinitionLocator.getRouteDefinitions()` 的 `Flux.fromIterable(repository.findAll...())` 与 `GatewayStatusService.status()` 的 `routeConfigRepository.count()` 同理。事件循环线程数量 ≈ CPU 核数，一旦并发请求或 DB 变慢，全部请求互相阻塞直至超时——这是 Spring 官方明确警告的 WebFlux 反模式（"blocking call inside reactive pipeline"）。
- **建议修法**：每个阻塞调用点用 `Mono.fromCallable(() -> routeService.xxx(...)).subscribeOn(Schedulers.boundedElastic())` 包裹（`RouteController.delete` 的 `doOnNext` 改为 `flatMap` + 同样包裹）；`DbRouteDefinitionLocator` 用 `Mono.fromCallable(...).subscribeOn(boundedElastic()).flatMapMany(Flux::fromIterable)`。更彻底的方案是把管理 API 拆成独立 Spring MVC 服务（网关保持 WebFlux），或迁移 R2DBC——对当前规模，`boundedElastic` 卸载即可。

### P1-02 `RouteAssembler.toJson` 序列化失败静默返回 null → 审计内容静默丢失（ticket 疑点确认）

- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteAssembler.java:98-104`；消费点 `RouteService.java:50, 62, 70, 79, 92, 95`；`audit/AuditService.java:20-29`；`audit/AuditLog.java:36-40`（before/after 可空列）
- **为什么**：`toJson` 的 `catch (Exception e) { return null; }` 把序列化失败完全吞掉，调用方（审计记录 before/after JSON）拿到 null 照常入库，审计日志里该条记录的变更前后内容显示"（无）"，且无人知晓、无日志。这与同文件其他序列化/反序列化方法（`writeList:172-178`、`writeMap:180-186`、`readSteps:150-159`、`readMetadata:161-170` 均 `throw new IllegalStateException`）的处理方式明显不一致，是"静默失败"坏味道，直接违背 ADR 0003"审计日志保证每次生效动作可追溯"的承诺。
- **建议修法**：`toJson` 失败时至少 `log.error`（带 routeId 上下文）并返回显式占位串（如 `"[serialization failed]"`）而非 null，使审计行仍可读、失败可观测；或让它抛异常并在审计边界统一 catch + 记日志，避免拖垮主事务。

### P1-03 默认账号口令硬编码且公开展示在登录页

- **位置**：`backend/src/main/java/com/gatewaydashboard/config/SeedDataInitializer.java:34-41`（admin/admin123、viewer/viewer123）；`frontend/src/views/LoginView.vue:45-47`（登录页直接展示预置账号）
- **为什么**：任何部署了该应用的实例都会自带可预测的 ADMIN 口令，且登录页把它印出来，等于向所有访问者公开超级管理员凭据。本应用 ADMIN 可改路由、改权限规则，口令泄露后果严重。这是学习/演示项目常见但上线即高危的默认凭据问题。
- **建议修法**：seed 口令改为从环境变量读取（无则随机生成并打印一次）；登录页去掉预置账号展示；生产环境强制首次登录改密（或至少文档/启动检查提示）。后续可引入 `spring-boot-starter-actuator` 的配置检查或启动时校验 `JWT_SECRET`/默认口令是否被覆盖。

### P1-04 默认 JWT 密钥与内部网关 token 硬编码在配置文件

- **位置**：`backend/src/main/resources/application.yml:27`（`secret: ${JWT_SECRET:gateway-dashboard-dev-secret-change-me-0123456789}`）、`:32-33`（外部网关 `token: gd-internal-token-dev`）；且 `:5` 默认 profile 就是 `dev`
- **为什么**：JWT 密钥若未用环境变量覆盖，攻击者可用公开的默认值伪造任意 ADMIN 的 token（jjwt 直接 `hmacShaKeyFor(secret)`）；`X-Internal-Token` 同理可伪造推送。注释虽提示生产覆盖，但"默认可用 + 默认 profile 为 dev"的组合让误部署风险很高。
- **建议修法**：启动时强制校验（`@PostConstruct` 或 `Environment` 检查：生产 profile 下密钥必须来自环境变量，否则启动失败）；内部 token 至少换随机值并外置。可参考 `application-test.yml` 的做法把默认值收敛到 dev 专用配置。

### P2-01 `setEnabled` 用裸 `Map` 取 "enabled"，缺失/畸形请求体会静默停用路由

- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteController.java:64-71`（`body.get("enabled")`，`Boolean.TRUE.equals(...)` 兜底）
- **为什么**：`@RequestBody Map<String, Boolean>` 无结构校验，请求体 `{}`、`{"enabled": null}` 或字段拼错都会被当成 `false`——即**静默停用一条生产路由**，且无校验提示（停用路径不触发 `ensureValid`）。魔法字符串 "enabled" 也无类型约束。
- **建议修法**：定义一个 `record SetEnabledRequest(@NotNull Boolean enabled)` 并加 `@Valid`，缺失字段直接 400；或改用 `@RequestParam Boolean enabled`。

### P2-02 权限自我保护守卫只验证一个虚构路径 + 单方法，ADMIN 写权限保护不完整

- **位置**：`backend/src/main/java/com/gatewaydashboard/permission/PermissionRuleService.java:33, 149-162`（`SELF_GUARD_PATH = "/api/permission-rules/__guard__"`，仅 `CachedRule.match(cached, "POST", SELF_GUARD_PATH)`）
- **为什么**：ADR 0005 承诺"任何规则改动不得导致 ADMIN 失去对权限配置模块的访问权"，但守卫只模拟 POST 到 `/api/permission-rules/__guard__` 这一个请求。若新规则允许 POST `/api/permission-rules/**` 却拒绝 PUT/DELETE（或规则路径精确匹配了 `__guard__` 而放行了其他写接口），守卫会通过而 ADMIN 实际失去更新/删除能力，把自己锁死。守卫路径本身还是不存在于任何路由表的"假路径"，属于魔法值。
- **建议修法**：对权限模块的全部写端点（POST/PUT/DELETE `/api/permission-rules`、`/api/permission-rules/{id}`、以及 `/api/meta` 等依赖接口）逐一模拟匹配；或抽出"ADMIN 必须能访问权限模块所有写接口"为显式函数并配单测。

### P2-03 `ExternalGatewayRefreshService` fire-and-forget `subscribe` 无生命周期管理

- **位置**：`backend/src/main/java/com/gatewaydashboard/config/ExternalGatewayRefreshService.java:31-53`（`:43-51` 直接 `.subscribe(...)`）
- **为什么**：每次保存路由都会从 `afterCommit` 里发起 N 个异步 WebClient 请求，`Disposable` 无人持有：应用关闭时在途请求直接丢失、无法取消；错误只能写内存 map，无法反馈给调用方。虽注释说明"尽力而为"且网关侧有轮询兜底，但"异步发起+无人管理"仍是可观测性/资源管理缺口。
- **建议修法**：改为返回 `Mono<Void>`（`Mono.when(...)` 汇聚）由调用方订阅或记录；至少持有 `Disposable` 并在 `@PreDestroy` 时 `dispose()`；把失败记录持久化或暴露为可查询状态。

### P2-04 健康检查 `catch (Exception ignored)` 静默吞异常

- **位置**：`backend/src/main/java/com/gatewaydashboard/gateway/GatewayStatusService.java:37-44`
- **为什么**：`catch (Exception ignored)` 空捕获，DB 故障细节（连接串、异常类型）完全不记录，出问题只能看到 UP/DOWN 二值；且 `count()` 这个阻塞查询本身就跑在事件循环线程上（见 P1-01）。健康检查应可观测。
- **建议修法**：catch 里 `log.warn("health check failed", e)`（或 debug 级）；`count()` 调用同样包 `Mono.fromCallable(...).subscribeOn(boundedElastic())`；可考虑用 Spring Actuator 的 `HealthIndicator` 代替手写探测。

### P2-05 校验规则三层重复，漂移风险高

- **位置**：
  - routeId 正则 `[A-Za-z0-9_.-]{1,128}`：`route/RouteDto.java:17`（@Pattern）↔ `route/RouteValidator.java:52` ↔ `frontend/src/utils/routeJson.ts:43`
  - httpMethod 白名单：`permission/PermissionRuleDtos.java:16`（@Pattern 正则）↔ `permission/PermissionRuleService.java:31-32`（ALLOWED_METHODS）↔ `frontend/src/views/PermissionRuleView.vue:13`（methodOptions）
- **为什么**：同一规则在 bean 校验、服务层、前端各写一遍，改一处漏两处是必然的（例如前端已出现第三条 routeId 副本）。这正是"单一事实来源"缺失。
- **建议修法**：后端至少收敛到一处（如把正则提为 `RouteValidator` 常量并让 DTO 校验委托给它，或在 service 里只保留一处）；前端规则保留为 UX 预检，但加注释指向后端为权威源，并加"前后端规则一致性"的测试断言。

### P2-06 CORS 来源硬编码 + 空 `cors()` lambda

- **位置**：`backend/src/main/java/com/gatewaydashboard/config/SecurityConfig.java:39-40`（`.cors(cors -> {})` 空实现）、`:77-87`（`allowedOriginPatterns(List.of("http://localhost:5173", "http://127.0.0.1:5173"))`）
- **为什么**：来源写死本地前端地址，换环境/域名必须改代码；`.cors(cors -> {})` 是无效空 lambda（真正的 CORS 交给单独的 `CorsWebFilter`，两处配置并存易让人误读谁生效）。
- **建议修法**：来源改为 `@Value`/`@ConfigurationProperties` 配置（如 `gateway-dashboard.cors.allowed-origins`）；删除空的 `.cors(...)` 或改用它配置并去掉重复的 `CorsWebFilter`，二选一。

### P2-07 数据库口令硬编码在仓库内配置文件

- **位置**：`backend/src/main/resources/application-dev.yml:3-5`（`gateway / gateway123`）；另 `application-local.yml` H2 无口令可接受
- **为什么**：MySQL 口令以明文进 Git 历史；dev 是默认 profile，误用即暴露。属常见但应修正的配置卫生问题。
- **建议修法**：口令走环境变量（`${DB_PASSWORD:...}`）或仅保留在本地未入库的 profile/`.env`；dev 默认口令至少加醒目注释并指向环境变量注入方式。

### P2-08 审计 JSON 截断会产生非法 JSON 片段，5000 魔数两处重复

- **位置**：`backend/src/main/java/com/gatewaydashboard/audit/AuditService.java:16, 44-49`（`MAX_JSON_LENGTH = 5000`，`substring(0, 5000)`）；`audit/AuditLog.java:36-40`（列长同为 5000）
- **为什么**：截断按字符硬切，长路由配置的 before/after JSON 会以半个 token 结尾（如 `{"uri":"http://...` 缺右引号），存进库的是**不可解析的 JSON 碎片**；`5000` 在 `AuditService` 与 `AuditLog` 两处字面量重复，改一处会漏。当前前端只按原始文本展示所以不炸，但任何想解析审计内容的消费者都会失败。
- **建议修法**：截断到完整 JSON 边界（解析失败时退回"已截断"占位），或干脆不截断（列改 TEXT）；`5000` 收敛为共享常量（如 `AuditLog.MAX_JSON_LENGTH` 并在列注释说明）。

### P3-01 审计动作码字符串化贯穿前后端，且前端映射不完整

- **位置**：`route/RouteService.java:50, 70, 79, 95`（"CREATE"/"UPDATE"/"DELETE"/"ENABLE"/"DISABLE"）；`audit/AuditLog.java:31`（action 列）；`frontend/src/views/AuditLogView.vue:23-34`（actionType 只 switch CREATE/UPDATE/DELETE，ENABLE/DISABLE 落到默认 info）
- **为什么**：动作类型是领域概念（对应 CONTEXT.md"保存、停用"语义），用裸字符串在三个层传递，拼写无编译期保障；前端映射漏了 ENABLE/DISABLE 导致启用/停用日志显示为灰色 info 标签，语义失真。
- **建议修法**：后端定义 `enum AuditAction { CREATE, UPDATE, DELETE, ENABLE, DISABLE }`（DB 存 name 或序数），前端 `actionType` 补全 ENABLE/DISABLE 分支并用常量/联合类型对齐。

### P3-02 乐观锁冲突处理重复两处

- **位置**：`route/RouteService.java:65-69`（catch 后转 BusinessException.conflict，文案与全局 handler 一致）；`common/GlobalExceptionHandler.java:27-31`（同样处理 `ObjectOptimisticLockingFailureException` → 409）
- **为什么**：同一异常、同一文案处理逻辑存在两处，service 里那层对 `update` 之外路径（如 `setEnabled` 的 saveAndFlush）不生效，行为不统一且维护易漂移。
- **建议修法**：删掉 service 内 catch（或保留但去掉重复文案），统一交给 `GlobalExceptionHandler`；把文案提为常量。

### P3-03 `GlobalExceptionHandler.handleValidation` 两个分支重复

- **位置**：`common/GlobalExceptionHandler.java:33-48`（`MethodArgumentNotValidException` 与 `WebExchangeBindException` 两分支代码几乎相同）
- **为什么**：两个异常在 Spring 6 都继承 `org.springframework.validation.BindException`，可合并为一个 handler 参数，减少重复。
- **建议修法**：`@ExceptionHandler(BindException.class)` 单一 handler（MethodArgumentNotValidException/WebExchangeBindException 均适用），内部直接取 `getBindingResult().getFieldErrors()`。

### P3-04 控制器响应式写法三种风格并存

- **位置**：`route/RouteController.java:58-62`（delete 用 `doOnNext` + `thenReturn`）、`auth/AuthController.java:38-42`（同款）、`permission/PermissionRuleController.java:42-46`（delete 用 `Mono.fromRunnable`）、其余端点用 `Mono.just`/`map`
- **为什么**：同一个"写操作 + 返回 ok"的模式有三种实现；`doOnNext` 语义是"副作用且忽略结果"，把真正的业务操作放进去是误用（异常虽会传播，但可读性差）。
- **建议修法**：统一为 `Mono.fromCallable(() -> { service.delete(...); return ApiResponse.ok(); }).subscribeOn(Schedulers.boundedElastic())`（顺带解决 P1-01）。

### P3-05 `RouteAssembler.toResponse(RouteDefinition)` 硬编码语义字段

- **位置**：`route/RouteAssembler.java:63-74`（`enabled=true`、`version=0`、`updatedAt=null` 硬编码）
- **为什么**：网关"生效路由"列表本身没有启用/版本概念，但硬编码 `true`/`0`/`null` 会让状态页的"启用/停用"展示失真（生效路由永远显示启用），且未来读这些字段易踩坑。
- **建议修法**：加注释说明语义（"生效路由来自网关运行时，无启用/版本概念"），或把 `RouteResponse` 相应字段改为可空并如实填 null。

### P3-06 `toLowerCase()` 缺 `Locale.ROOT`

- **位置**：`route/RouteValidator.java:62`（`scheme.toLowerCase()`）
- **为什么**：未指定 Locale 的 `toLowerCase` 在土耳其语环境下行为不同（经典 i 陷阱），且与 `PermissionRuleService.java:112, 132, 171, 182` 等处规范使用 `Locale.ROOT` 不一致。
- **建议修法**：`scheme.toLowerCase(Locale.ROOT)`。

### P3-07 `PermissionRuleService.isAllowed` 的 AUTHENTICATED 分支是死代码

- **位置**：`permission/PermissionRuleService.java:121-129`（`:125-127` 的 AUTHENTICATED 分支）；`permission/DynamicPermissionAuthorizationManager.java:42-46`
- **为什么**：manager 的 `check` 在调用 `isAllowed` 之前已单独处理 `*` 与 `AUTHENTICATED` 并 return，`isAllowed` 永远收不到含 AUTHENTICATED 的规则，该分支不可达。死分支会误导读者以为那里处理了认证逻辑。
- **建议修法**：删除 `isAllowed` 中 `*`/`AUTHENTICATED` 分支（或把完整决策逻辑收拢到一处，明确"谁负责什么"）。

### P3-08 `guardAdminSelfAccess` 重复实现排序逻辑

- **位置**：`permission/PermissionRuleService.java:150-157`（手写 priority+id 排序）与 `PermissionRuleRepository.java:9`（`findAllByOrderByPriorityAscIdAsc`）
- **为什么**：同一"按优先级、再按 id"的排序规则在 repository 派生查询和内存排序各一份，修改匹配语义时极易漏改其一（守卫用错顺序会导致自锁误判）。
- **建议修法**：抽取 `Comparator.comparingInt(PermissionRule::getPriority).thenComparing(r -> r.getId() == null ? Long.MAX_VALUE : r.getId())` 为共享常量，或让守卫直接复用 `CachedRule` 的排序工具。

### P3-09 若干魔法字符串/字面量散落

- **位置**：
  - `permission/PermissionRuleService.java:33`（`SELF_GUARD_PATH` 假路径，见 P2-02）
  - `gateway/ExternalGatewayStatusService.java:58` 与 `config/ExternalGatewayRefreshService.java:37`（"internal/routes" / "internal/routes/refresh" 路径散落两处）
  - `gateway/GatewayStatusService.java:37, 40`（"DOWN"/"UP" 字面量）
  - `frontend/src/views/GatewayStatusView.vue:38-40`（'UP' 比较）
- **为什么**：跨服务契约路径、健康状态值没有共享常量/枚举，改契约要全文搜索。
- **建议修法**：路径提为常量（如 `ExternalGatewayContract.ROUTES_REFRESH_PATH`）；健康状态用枚举或至少统一常量；前端与后端状态值对齐方式加注释。

### P3-10 前端 `ApiResponse.data` 类型声明与后端实际序列化不一致

- **位置**：`frontend/src/types.ts:4`（`data: T` 必填）；`backend/src/main/resources/application.yml:13`（`jackson.default-property-inclusion: non_null`，null 的 data 被省略）；`frontend/src/api/http.ts:39-57`（`return res.data.data`）
- **为什么**：后端失败响应/空 data 时 JSON 里根本没有 `data` 键，前端却声明为必填 `T`——运行时实际是 `undefined`。目前因"失败即 reject、void 不取值"侥幸没炸，但这是类型谎言，未来谁直接消费 `res.data.data` 就会踩。
- **建议修法**：`ApiResponse<T>` 的 `data` 改为 `data?: T`，或后端去掉全局 `non_null`（只对 data 字段特殊处理）；`get` 等封装对"成功但无 data"给出明确断言/空值策略。

### P3-11 前端死代码与常量重复

- **位置**：
  - `frontend/src/api/routes.ts:7`（`routesApi.get(routeId)` 全项目无调用——编辑用的是行数据）
  - `frontend/src/stores/auth.ts:36-39`（`fetchMe`/`authApi.me` 无调用者）
  - `frontend/src/api/http.ts:5` 与 `frontend/src/stores/auth.ts:5`（`TOKEN_KEY = 'gateway-dashboard-token'` 两处重复）
- **为什么**：死代码增加维护面；token 键名重复定义，改名要改两处。`fetchMe` 未被调用还意味着登录后用户信息只来自 localStorage，可能过期（角色变更后不刷新直到重新登录）。
- **建议修法**：删掉未使用的 `routesApi.get` 与 `fetchMe`（或在路由守卫里真正启用 `fetchMe` 校验 token 有效性）；`TOKEN_KEY`/`USER_KEY` 收敛到一个常量模块。

### P3-12 前端路由守卫无角色校验，`/permissions` 可被 VIEWER 直接访问

- **位置**：`frontend/src/router/index.ts:16-25`（只判 `isLoggedIn`）；`frontend/src/App.vue:77`（仅菜单用 `auth.isAdmin` 隐藏）
- **为什么**：菜单隐藏不等于防护：VIEWER 手动输入 `/permissions` URL 会加载页面，只有请求被后端 403 时才报错。属纵深防御缺失（后端是对的，前端应尽早拦截提升体验并减少误报）。
- **建议修法**：在 `beforeEach` 里加 `meta.requiresAdmin` 路由元信息，`!auth.isAdmin` 时重定向回 `/routes`。

### P3-13 前端若干页面缺少错误兜底（未捕获的 Promise rejection）

- **位置**：`frontend/src/views/RouteListView.vue:28-33`（`loadFactories` 无 try/catch，且 `onMounted` 里 `loadRoutes()`/`loadFactories()` 无 catch）、`:19-26`（`loadRoutes` 只 finally）；`frontend/src/components/RouteEditorDrawer.vue:153-170`（`save` 无 catch）；`frontend/src/views/PermissionRuleView.vue:55-73`（`save` 无 catch）
- **为什么**：错误虽由 http 拦截器统一 toast，但 async 函数内抛出的异常会成为未处理的 Promise rejection（控制台报错，测试/监控里是噪音）；部分场景（如 meta 接口失败）工厂列表静默为空，下拉框只剩 allow-create 手工输入，行为难懂。
- **建议修法**：视图层统一 `catch`（可复用"仅拦截器提示"模式），或把 loading/错误态收敛为一个 `useAsync` 组合式工具；`loadFactories` 失败至少兜底空数组。

### P3-14 `ExternalGatewayStatusService.fetchAll` 未检查类型转换

- **位置**：`gateway/ExternalGatewayStatusService.java:47-53`（`Mono.zip(monos, array -> { ... (ExternalGatewayStatus) item; })`）
- **为什么**：`Mono.zip` 聚合回调里对 `Object` 强转，编译期无保障；同时 `Mono.zip` 对"1 个元素"的列表语义与"多元素"有细微差别，需注释说明。
- **建议修法**：用 `Mono.zip(monos, objects -> Arrays.stream(objects).map(ExternalGatewayStatus.class::cast).toList())` 或先 `.map(...)` 合并；或改用 `Flux.merge(monos).collectList()`。

### P3-15 手写构造器 + `@Qualifier` 与项目 `@RequiredArgsConstructor` 风格不一致

- **位置**：`gateway/GatewayStatusService.java:24-34`（手写构造器 + `@Qualifier("routeDefinitionLocator")`）；`config/ExternalGatewayRefreshService.java:26-29`、`gateway/ExternalGatewayStatusService.java:33-39`（手写构造器）；其余服务均用 `@RequiredArgsConstructor`
- **为什么**：同一代码库两种 DI 写法，且 `@Qualifier` 未说明为何存在多个 `RouteDefinitionLocator` bean（读代码时需猜测）；`ExternalGatewayRefreshService` 放 `config` 包而 `ExternalGatewayStatusService` 放 `gateway` 包，同类职责跨包放置。
- **建议修法**：统一 `@RequiredArgsConstructor`（需要 `@Qualifier` 的字段用 `@Qualifier` 标注即可）；把 `ExternalGatewayRefreshService` 移到 `gateway` 包或合并进 `ExternalGatewayStatusService`。

### P3-16 `vite.config.ts` 手写 `declare const process` 代替 `@types/node`

- **位置**：`frontend/vite.config.ts:3`
- **为什么**：为用 `process.env` 手写全局声明是临时 hack，与项目其他 TS 代码风格不符，换环境/升级时易碎。
- **建议修法**：devDependencies 加 `@types/node`，删除 `declare`。

### P3-17 表单 JSON 解析错误直接暴露英文原始报错

- **位置**：`frontend/src/components/RouteEditorDrawer.vue:115-123`（`parseArgs` 里 `JSON.parse` 抛 `SyntaxError`）、`:142-144`（`catch` 后 `ElMessage.error((e as Error).message)`）
- **为什么**：`Unexpected token } in JSON at position 5` 这类引擎英文消息直接弹给中文用户，且位置信息对普通用户无意义。
- **建议修法**：统一包装成中文提示（"参数 JSON 格式错误，请检查引号与逗号"），可把解析错误收敛到 `routeJson.ts` 的 `parseArgs` 工具并配单测（现有 `routeJson.test.ts` 已覆盖 `parseRequestJson`，可扩展）。

### P3-18 `SecurityUtils.clientIp` 信任 `X-Forwarded-For` 且 IPv6 展示不友好

- **位置**：`backend/src/main/java/com/gatewaydashboard/common/SecurityUtils.java:17-25`
- **为什么**：`X-Forwarded-For` 可被客户端伪造（审计里 IP 字段不可信）；无代理时 IPv6 地址以压缩形式（`0:0:...:1`）落库，与 IPv4 展示风格不一致；`getRemoteAddress()` 为 null 时返回 null 与 `AuditLog.ip` 可空列耦合（可接受但应注释）。
- **建议修法**：仅从受信代理（或 `Forwarded` 头）取值并注释信任边界；IPv6 用 `InetAddress.getHostAddress()` 后按需规范化；返回 `Optional<String>` 替代隐式 null。

### P3-19 `RouteConfigRepository.search` 前导通配符导致无法走索引

- **位置**：`route/RouteConfigRepository.java:20-22`（`like lower(concat('%', :keyword, '%'))`）
- **为什么**：`%keyword%` 前导通配使 `route_id`/`uri` 索引失效（全表扫描），表增长后搜索变慢；`lower(...)` 也阻止普通索引（需函数索引）。
- **建议修法**：当前规模可接受，加注释注明；如需优化改为前缀匹配或引入全文索引/ES；至少确保 `route_id` 有索引（`unique` 列已有）。

### P3-20 `AuditService.page` 返回请求页码而非规范化页码

- **位置**：`audit/AuditService.java:32-42`（`new PageResult<>(..., page, safeSize, ...)` 回显原始 `page`，而查询用 `safePage`）
- **为什么**：当调用方传 `page=0`（非法）时，查询落在第 0 页但响应 `page=0`，前后矛盾；`size` 越界被静默 clamp 也无提示。
- **建议修法**：响应回显规范化后的值（`safePage + 1`），或对非法分页参数直接 400（与 P2 系列"fail fast"一致）。

---

## 做得好的地方（供合成报告平衡参考）

- 包结构按领域划分（route/permission/audit/auth/gateway），命名贴合 `CONTEXT.md` 词汇（routeId↔路由 ID、enabled↔停用/启用、builtin↔内置规则、真源落库↔数据库真源）；
- DTO 一律 record，实体与 DTO 分离 + `RouteAssembler` 集中转换；`@Version` 乐观锁 + `open-in-view: false` + Flyway 管理 schema；
- `Optional` 用法规范（`orElseThrow` + `BusinessException`），未发现 `.get()` 滥用；
- 异常有统一出口 `GlobalExceptionHandler`，兜底 handler 记 `log.error` 且不泄露内部细节；
- 权限模块的内存缓存 `AtomicReference<List<CachedRule>>` + 预编译 `PathPattern` 设计合理，且规则改动即时生效；
- 前端 TS `strict: true`，API 层封装薄而统一，`routeJson.test.ts` / 后端 `RouteValidatorTest` 等少量测试存在且针对核心逻辑。

## 统计

| 级别 | 数量 | 主题 |
|---|---|---|
| P0 | 0 | — |
| P1 | 4 | 事件循环阻塞；审计静默丢失；默认凭据；默认密钥 |
| P2 | 8 | setEnabled 静默停用；自锁守卫不完整；fire-and-forget；健康检查吞异常；校验三层重复；CORS 硬编码；DB 口令入库；审计截断非法 JSON |
| P3 | 20 | 风格一致性、死代码、魔法值、类型谎言、Locale、未捕获 rejection 等 |
