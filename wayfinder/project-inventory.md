# 项目事实清单（评审 effort 参考）

> 来源：wayfinder 绘制阶段的只读扫描（2025-08，charting 子代理）。纯事实，无建议。后续评审 ticket 与「工作地图」会话以此为基线，不必重复全量扫描。

仓库：`spring-cloud-gateway-dashboard`（Java 21 + Spring Boot 3.5 WebFlux 后端 + Vue 3 前端 + gateway-demo 网关工程）。

## 1. 后端结构（backend/src/main/java，47 个类，约 2218 行）

### com.gatewaydashboard.route（10 文件，717 行）
- `DbRouteDefinitionLocator.java`(21)：从 DB 读启用路由转 `RouteDefinition`，实现网关路由真源（内嵌网关用）
- `RouteAssembler.java`(187)：RouteConfig/RouteRequest/RouteDefinition/RouteResponse 间 JSON 与对象互转；`toJson()` 失败返回 null
- `RouteConfig.java`(61)：`route_config` 表实体，含 `@Version` 乐观锁字段
- `RouteConfigRepository.java`(23)：路由查询；`search()` 用 JPQL 参数化 `concat/like` 关键字搜索
- `RouteController.java`(77)：`/api/routes` 全部端点（list/get/create/update/delete/enabled/validate）
- `RouteDto.java`(38)：DTO；routeId 有 `@NotBlank+@Pattern`，uri 仅 `@NotBlank`，predicates/filters/metadata 无长度限制
- `RouteMetaController.java`(31)：`/api/meta/factories?type=predicate|filter` 返回可用工厂名
- `RouteRefreshService.java`(17)：发布 `RefreshRoutesEvent` 触发本地网关热刷新
- `RouteService.java`(134)：CRUD 业务；乐观锁冲突转 409；事务提交后（`afterCommit`）刷新本地 + 推送外部网关
- `RouteValidator.java`(128)：用网关自带工厂 + `ConfigurationService` 对断言/过滤器做试绑定校验，白名单 scheme（http/https/ws/wss/lb）

### com.gatewaydashboard.auth（7 文件，264 行）
- `AuthController.java`(43)：`/api/auth` login/me/password
- `AuthDtos.java`(26)：LoginRequest/ChangePasswordRequest（新密码最小 6 位）
- `AuthService.java`(51)：登录校验、BCrypt 匹配、签发 JWT、改密
- `JwtAuthenticationFilter.java`(40)：WebFilter 解析 Bearer token 注入 SecurityContext；无效 token 静默忽略
- `JwtService.java`(45)：jjwt HMAC-SHA 签发/解析，secret 来自配置，过期 12 小时
- `User.java`(47)：`sys_user` 实体
- `UserRepository.java`(12)：用户查询

### com.gatewaydashboard.audit（5 文件，159 行）
- `AuditController.java`(26)：`/api/audit-logs` 分页
- `AuditDtos.java`(25)：AuditLogResponse
- `AuditLog.java`(48)：`audit_log` 实体（actor/action/routeId/before/after/ip）
- `AuditLogRepository.java`(10)：按时间倒序分页
- `AuditService.java`(50)：记录审计，before/after JSON 截断到 5000 字符；page/size 服务端钳制

### com.gatewaydashboard.permission（6 文件，435 行）
- `DynamicPermissionAuthorizationManager.java`(61)：`ReactiveAuthorizationManager`，按（方法,路径）匹配首条规则决策
- `PermissionRule.java`(56)：`permission_rule` 实体
- `PermissionRuleController.java`(47)：`/api/permission-rules` CRUD
- `PermissionRuleDtos.java`(42)：RuleRequest，httpMethod 白名单 `*|GET|POST|...`
- `PermissionRuleRepository.java`(10)：按优先级查询
- `PermissionRuleService.java`(219)：CRUD + `AtomicReference` 内存规则缓存 + reload 即时生效 + `guardAdminSelfAccess` 自我保护（防 ADMIN 锁死）

### com.gatewaydashboard.gateway（5 文件，218 行）
- `ExternalGatewayStatusService.java`(84)：聚合外部网关在线/生效路由/推送记录，3s 超时
- `GatewayStatusController.java`(22)：`/api/gateway/status`
- `GatewayStatusDtos.java`(34)：状态 DTO
- `GatewayStatusService.java`(55)：内嵌网关健康检查（DB count）+ 生效路由 + 外部网关聚合
- `RefreshTimestampListener.java`(23)：监听 `RefreshRoutesEvent` 记录最近刷新时间

### com.gatewaydashboard.config（4 文件，269 行）
- `ExternalGatewayProperties.java`(48)：`gateway-dashboard.external-gateways` 列表绑定
- `ExternalGatewayRefreshService.java`(58)：保存后向外部网关 POST `/internal/routes/refresh`，fire-and-forget `subscribe`，失败仅 warn
- `SecurityConfig.java`(88)：WebFlux 安全链（CSRF 关、JWT filter、动态授权、统一 401/403 JSON、BCrypt、CORS）
- `SeedDataInitializer.java`(75)：启动种子：admin/admin123、viewer/viewer123、2 条 httpbin 示例路由

### com.gatewaydashboard.common（5 文件，156 行）+ 主类
- `ApiResponse.java`(16) 统一响应体 / `BusinessException.java`(33) 业务异常 / `GlobalExceptionHandler.java`(75) 全局异常转 JSON / `PageResult.java`(6) / `SecurityUtils.java`(26) 当前用户 + 客户端 IP（取 X-Forwarded-For 首段）
- `GatewayDashboardApplication.java`(12)：Spring Boot 入口

### resources 配置
- `application.yml`(37)：默认 profile=dev；JPA ddl-auto=none + Flyway 开；端口 8080；actuator 仅暴露 health；JWT secret 默认 `gateway-dashboard-dev-secret-change-me-0123456789`（可被 `JWT_SECRET` 覆盖）、expire-hours=12；external-gateways 指向 `http://localhost:8088` + token `gd-internal-token-dev`
- `application-dev.yml`(6)：MySQL `jdbc:mysql://localhost:3306/gateway_dashboard`，user/pass `gateway/gateway123` 明文
- `application-local.yml`(8)：H2 文件库（无 MySQL 时兜底）
- `application-test.yml`(12)：H2 内存库；外部网关指向不可达端口 19999
- `db/migration/V1__init.sql`(39)：sys_user/route_config/audit_log 三表；`V2__permission_rule.sql`(27)：permission_rule 表 + 11 条内置规则种子（ADMIN/AUTHENTICATED 语义）

## 2. 后端测试（backend/src/test，4 个类，12 个 @Test，surefire 记录全部通过）
- `GatewayDashboardIntegrationTest.java`(178，4 测)：错密码 401；匿名 401（无 WWW-Authenticate）；错误方法 405 JSON；完整路由生命周期（创建→生效→停用→不生效→重复创建 409→validate 未知工厂 false→viewer 写 403→审计 CREATE 存在）
- `PermissionRuleIntegrationTest.java`(166，1 个长测)：viewer 看规则 403→新增规则即时生效→删除即时失效→内置不可删→两类自我保护 400
- `ExternalGatewayStatusIntegrationTest.java`(57，1 测)：外部网关离线状态展示（online=false、空路由、error 非空）
- `RouteValidatorTest.java`(85，6 测)：合法通过/未知工厂/启用无 predicate 失败/停用无 predicate 通过/非法 scheme/嵌套参数值失败
- 覆盖到：安全 401/403、动态权限、审计（仅 CREATE）、外部网关状态（仅离线场景）
- 缺失区域：外部网关主动推送（refreshAll）无测试（无 mock 服务器）；乐观锁 409 无测试；改密流程无测试；审计 UPDATE/DELETE/ENABLE 无断言；`/api/meta/factories`、搜索接口无测试；gateway-demo 无任何测试（无 src/test）；权限规则边界（空 roles、`*` 覆盖全部、并发 reload）无测试

## 3. 前端结构（frontend/src，约 1450 行）
- `api/`：`http.ts`(57) axios 封装（token 注入 localStorage、401 清空跳登录、错误 ElMessage）；`auth.ts`(10)、`routes.ts`(15)、`audit.ts`(7)、`permissions.ts`(10)、`gateway.ts`(6)、`meta.ts`(6)
- `stores/auth.ts`(50)：Pinia 认证 store，token/user 持久化到 localStorage
- `router/index.ts`(27)：5 路由 + 登录守卫（无角色级路由权限，靠菜单隐藏 + 后端 403）
- `utils/routeJson.ts`(51)：JSON 解析/规范化/客户端校验；`routeJson.test.ts`(43)：4 个 vitest 用例（vitest 唯一测试，`environment: node`，只测 utils）
- `views/`：`LoginView.vue`(64，页面明文展示预置账号密码)、`RouteListView.vue`(133)、`GatewayStatusView.vue`(119)、`AuditLogView.vue`(94，展开行看 before/after JSON)、`PermissionRuleView.vue`(208)
- `components/RouteEditorDrawer.vue`(254)：表单/JSON 双模式路由编辑抽屉，保存前先调 `/api/routes/validate`
- 其他：`App.vue`(119 布局+改密弹窗)、`types.ts`(115)、`main.ts`(14)、`style.css`(48)、`env.d.ts`(8)
- 测试覆盖：仅 utils 的 JSON 工具；无组件/store/router/api 测试

## 4. 网关 demo（gateway-demo，7 文件 391 行）
- 与 backend 共用同一 MySQL `gateway_dashboard` 库的 `route_config` 表（路由真源），端口 8088
- `DbRouteDefinitionLocator.java`(122)：JdbcTemplate 直读表（非 JPA），与 backend 的实体版是两套并行实现
- `RouteRefreshController.java`(92)：`POST /internal/routes/refresh` + `GET /internal/routes`，`X-Internal-Token` 校验；**token 配置为空则跳过校验**
- `RouteSyncScheduler.java`(78)：每 5s 轮询 `COUNT(*):SUM(version)` 校验和，变化即刷新（兜底）；`markRefreshed()` 防重复
- `RouteRefreshPublisher.java`(19)、`RouteSyncProperties.java`(28，默认 token `gd-internal-token-dev`)、`GatewayDemoApplication.java`(13)
- 集成方式：backend 保存路由 → `ExternalGatewayRefreshService` 推 `/internal/routes/refresh`（推送）→ 网关内 `RouteRefreshPublisher` 发 RefreshRoutesEvent → 从 DB 重载；轮询兜底
- 依赖：Boot 3.5.16 + Spring Cloud **2025.0.0**（backend 为 2025.0.3）+ nacos-discovery + loadbalancer；用经典 `spring-cloud-starter-gateway`（backend 用新的 `gateway-server-webflux`）

## 5. Docker 交付物（docker/）
- `docker-compose.yml`(48)：mysql:8.4 + backend（build ../backend）+ frontend（build ../frontend，nginx 8088:80）；mysql 有 healthcheck，backend 无；**文件头自述"本机没有 Docker 环境，此文件未在本机验证过"**（README 亦标注"交付参考，未验证"）
- `backend/Dockerfile`：maven:3.9-eclipse-temurin-21 构建（-DskipTests）→ eclipse-temurin:21-jre，jar `gateway-dashboard-backend-0.1.0-SNAPSHOT.jar`
- `frontend/Dockerfile`：node:24-alpine 构建 → nginx:1.27-alpine，复制 dist + nginx.conf
- `frontend/nginx.conf`：`/api/` 代理到 `backend:8080`，SPA fallback
- 明显问题：compose 无 gateway-demo 服务（外部网关场景未容器化）；明文密码（root123/gateway123）；backend 无 healthcheck；环境变量覆盖齐全但未实跑验证

## 6. 文档
- ADR 5 篇各一句话：
  - 0001：路由真源选数据库而非 YAML/Nacos（后台与网关共用 route_config 表，热刷新生效）
  - 0002：管理后台内嵌于网关进程（单实例 MVP），API 按模块组织便于日后拆分
  - 0003：保存即生效（无草稿/发布两阶段），用审计日志弥补追溯
  - 0004：选 Boot 3.5.x + Spring Cloud 2025.0.x WebFlux Gateway（并自述 Boot 3.5 OSS 支持已于 2026-06 结束）
  - 0005：权限规则数据库化动态生效（ReactiveAuthorizationManager + 优先级匹配 + 自我保护）
- `docs/使用手册.md`：450 行，12 章（能力/原理/环境/启动/概念/页面操作/示例/API 脚本/FAQ/外部网关对接/权限配置/已知边界）

## 7. 代码卫生
- TODO/FIXME/HACK/XXX：**0 处**（唯一命中是使用手册里的"xxx"小写文本，非代码注释）
- 硬编码凭据：JWT 默认 secret `backend/src/main/resources/application.yml:27`；内部令牌 `application.yml:33`、`gateway-demo/src/main/resources/application.yml:22`、`gateway-demo/.../RouteSyncProperties.java:14`；种子账号 `SeedDataInitializer.java:35,39`（admin123/viewer123）；docker-compose `docker/docker-compose.yml:8,11,30,31`（root123/gateway123/compose secret）
- 明文密码：`application-dev.yml:5`、`gateway-demo/src/main/resources/application.yml:10`、compose 多处
- CORS：`SecurityConfig.java:80-83` 仅限 localhost:5173/127.0.0.1:5173 + credentials + 任意 header（非通配，较克制）；`cors(cors -> {})` 与独立 `CorsWebFilter` bean 双重配置
- SQL 拼接：未发现；两处查询均为参数化（JPQL concat / JdbcTemplate 占位符）
- 输入校验缺口：RouteRequest 的 predicates/filters/metadata 无长度上限（DB 列 5000，超长落库报错）；`/enabled` 的 body map 无校验；AuditController page/size 靠 service 钳制
- 线程安全：PermissionRuleService/RefreshTimestampListener 用 AtomicReference（安全）；`RouteSyncScheduler.java:23-24,41,58` 两个 volatile 字段在 `poll()`（定时线程）与 `markRefreshed()`（HTTP 线程）间有非原子读改写竞态
- 异常被吞/静默：`RouteAssembler.java:102` 序列化失败返回 null；`GatewayStatusService.java:41` catch Exception ignored；`JwtAuthenticationFilter.java:34` 静默吞 JwtException；`RouteSyncScheduler` currentChecksum catch 返回 null；`ExternalGatewayRefreshService.java:43` 推送失败仅 log.warn（设计为尽力而为）
- 其他：前端 JWT 存 localStorage（`stores/auth.ts:18,33`、`api/http.ts:13`，XSS 可窃取）；`LoginView.vue:46` 页面明文展示预置账号密码；`RouteRefreshController.java:68-71` 内部 token 为空则绕过鉴权；无 JWT 服务端注销/刷新机制；ADR 0004 自述 Boot 3.5 OSS 已 EOL

## 8. 依赖与版本
- backend（pom.xml）：Spring Boot 3.5.16（parent）、Spring Cloud **2025.0.3**、Java 21、jjwt 0.12.6、mysql-connector-j/H2/Flyway/Lombok 均走 parent 管理；starter 用 `spring-cloud-starter-gateway-server-webflux`
- gateway-demo（pom.xml）：Boot 3.5.16、Spring Cloud **2025.0.0**、Spring Cloud Alibaba **2025.0.0.0**（nacos-discovery）、`spring-cloud-starter-gateway`（经典 artifact）
- frontend（package.json）：vue ^3.5.13、vue-router ^4.5.0、pinia ^2.3.0、element-plus ^2.9.1、axios ^1.7.9；dev：vite ^6.0.5、vitest ^3.0.5、typescript ~5.6.3、vue-tsc ^2.1.10
- 可疑/不一致点：backend(2025.0.3) 与 gateway-demo(2025.0.0) Spring Cloud 版本不一致且 starter artifact 不同；Boot 3.5 已过 OSS 支持期（ADR 自述）；Dockerfile 用 node:24 / mysql:8.4 / nginx:1.27（均较新）

## 值得注意的风险/疑点（10 条）
1. `backend/src/main/resources/application.yml:27` — JWT secret 有硬编码默认值（虽可 env 覆盖），dev 默认串含 "change-me"
2. `backend/src/main/java/com/gatewaydashboard/config/SeedDataInitializer.java:35,39` — 种子账号密码 admin123/viewer123 硬编码，且 LoginView 明文展示
3. `gateway-demo/src/main/java/com/example/gatewaydemo/route/RouteRefreshController.java:68-71` — internal-token 为空时刷新/查询接口完全无鉴权
4. `docker/docker-compose.yml`（整文件）— 自述未在本机验证；无 gateway-demo 服务；明文数据库密码；backend 无 healthcheck
5. `gateway-demo/src/main/java/com/example/gatewaydemo/route/RouteSyncScheduler.java:23-24` — poll() 与 markRefreshed() 并发时校验和字段存在非原子竞态（可能漏刷/重刷）
6. `backend/src/main/java/com/gatewaydashboard/config/ExternalGatewayRefreshService.java:43` — 推送 fire-and-forget 且无测试覆盖，推送失败只靠轮询兜底
7. `backend/src/main/java/com/gatewaydashboard/route/RouteAssembler.java:102` — 审计序列化失败静默返回 null，审计内容可能缺失且无告警
8. `backend/src/main/resources/application-dev.yml:5` 与 `gateway-demo/src/main/resources/application.yml:10` — MySQL 密码明文入库进 git（`gateway/gateway123`）
9. 后端与 gateway-demo Spring Cloud 版本线不一致（2025.0.3 vs 2025.0.0，starter artifact 也不同）
10. `frontend/src/stores/auth.ts:18` — JWT 存 localStorage（XSS 可窃取）+ 无服务端 token 吊销机制（改密后旧 token 仍有效）

（后端 12 个测试 surefire 全绿、前端 dist 已构建为已运行证据；全仓 0 处 TODO/FIXME/HACK/XXX 注释。）
