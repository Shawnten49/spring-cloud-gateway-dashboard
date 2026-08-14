# 架构与设计评审发现（ticket 003）

- 分支：`research/architecture-review`
- 评审方式：静态阅读（未启动服务、未跑测试、未改任何现有文件）
- 评审范围：`backend/src/main/java` 全部（route/auth/permission/audit/gateway/config/common）、`gateway-demo` 工程、`docs/adr/0001-0005`、`README.md`、`CONTEXT.md`、Flyway 迁移脚本、两处 pom.xml
- 分级：P0 阻断（数据损坏/安全漏洞/核心功能不可用）；P1 高（明确功能错误，须尽快处理）；P2 中（设计缺陷/边界竞态）；P3 低（改进建议）

## 总体判断

**架构骨架是好的，忠于 ADR 决策**：「数据库为唯一真源 + 自定义 RouteDefinitionLocator」忠实落地 ADR 0001/0002；「保存即生效」用 `TransactionSynchronization.afterCommit` 保证"先提交、后刷新"，时序正确（ADR 0003）；权限 DB 化 + 内存缓存 + fail-closed 默认拒绝 + 自我保护 guard，与 ADR 0005 一致；外部网关"推送 + 轮询校验和兜底"双通道设计合理，失败可自愈（最多延迟一个轮询周期）。包划分总体清晰，`common`/`auth`/`audit` 无反向依赖。

**没有 P0/P1 级问题**。主要风险集中在 P2：route↔config 包级环、WebFlux 事件循环上的阻塞 JPA 调用（同进程内嵌网关放大了影响）、推送 fire-and-forget 无超时、轮询校验和 (COUNT, SUM(version)) 的理论碰撞 + `markRefreshed` 竞态窗口、"生效路由"状态页实为 DB 直读而非网关内存缓存。

---

## 一、模块分层与依赖方向

### F1. [P2] route ↔ config 包级双向依赖（环）
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:5,27`（route → config）；`backend/src/main/java/com/gatewaydashboard/config/SeedDataInitializer.java:6-9,43,51`（config → route 的 `RouteConfig`/`RouteConfigRepository`/`RouteDto.Step`/`RouteRefreshService`）
- **为什么**：`route` 业务包依赖 `config` 的 `ExternalGatewayRefreshService`（写路径），而 `config` 又依赖 `route`（种子初始化）。无类级编译环，但包级依赖成环，违反"config=基础设施、只被依赖"的分层直觉；ADR 0002 承诺"管理 API 按独立模块组织，未来拆分时改动可控"，此环会随拆分变痛。
- **建议修法**：把 `ExternalGatewayRefreshService`/`ExternalGatewayProperties` 迁到独立的 `gateway`（或 `integration`）包，`route` 依赖它、`config` 不再依赖 `route`；`SeedDataInitializer` 属启动引导，可留在 `config`（引导依赖业务包可接受）或单独 bootstrap 包。目标：`route → integration`、`config → route` 单向，无互指。

### F2. [P3] gateway 包职责偏"读取 route 内部"，耦合略多
- **位置**：`backend/src/main/java/com/gatewaydashboard/gateway/GatewayStatusService.java:4-7,19-20,24-33`
- **为什么**：状态服务直接注入 route 的 Repository/Assembler/DTO，本质是"读 route + 聚合外部网关"。同层读取可接受，但若未来把状态页拆成独立服务，需先给 route 补只读查询门面。
- **建议修法**：可暂不动；拆分时在 route 包提供只读服务接口，gateway 只依赖接口。

### F3. [P3] config 包职责过载（安全 + 推送 + 种子）
- **位置**：`config/SecurityConfig.java`、`config/ExternalGatewayRefreshService.java`、`config/SeedDataInitializer.java`
- **为什么**：config 是"杂项"包而非单一职责；与 F1 的修复联动。
- **建议修法**：随 F1 拆出 integration 后，config 只留安全与属性绑定。

---

## 二、真源设计（ADR 0001/0002）

### F4. [P2] 双份平行的 `DbRouteDefinitionLocator` 实现，配置语义易漂移
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/DbRouteDefinitionLocator.java`（JPA 版）+ `backend/src/main/java/com/gatewaydashboard/route/RouteAssembler.java`；`gateway-demo/src/main/java/com/example/gatewaydemo/route/DbRouteDefinitionLocator.java`（JdbcTemplate 版，`toDefinition`/`readSteps` 自行解析）
- **为什么**：同一份"DB 行 → RouteDefinition"逻辑在 backend 与 gateway-demo 各写一遍（backend 走 JPA+Assembler，demo 走裸 JDBC+手写解析）。ADR 0001 的定位是"后台与网关读写同一张表、同一套配置语义"，但实现没有共享代码，未来改配置语义（如 metadata 结构、新增列）要同步改两处，容易漏改导致后台保存的路由在外部网关解析失败（如 JSON 字段名不一致）。
- **建议修法**：抽一个共享模块（如 `gateway-integration`：locator + 行解析 + 刷新接口/轮询），backend 与 gateway-demo 依赖同一份；至少把解析逻辑（`readSteps`/`coerceArgs`/`toDefinition`）提取为公共工具类。另：ADR 0001 说"保留切换 Nacos 抽象余地"——当前直接实现 Spring 的 `RouteDefinitionLocator` 接口即满足抽象，无需额外动作。

### F5. [P2] 内嵌网关没有"外部变更"兜底（与 gateway-demo 不对称）
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:104-117`（只有本进程写路径 afterCommit 刷新）；对比 `gateway-demo/.../route/RouteSyncScheduler.java:40-52`（外部网关有 5s 轮询兜底）
- **为什么**：backend 内嵌网关的刷新完全依赖"写操作经过 RouteService"。一旦出现第二个 dashboard 实例共库（当前单实例限制只是约定，`route_config` 表本身无实例归属），实例 B 的内嵌网关对实例 A 的写入**永远不刷新**（内嵌网关没有 poll，也没有收到推送——推送只发给 external-gateways 配置的地址）。README 已声明单实例边界，但"外部网关有兜底、内嵌网关没有"的不对称会在未来多实例时静默失效。
- **建议修法**：多实例版本为内嵌网关也启用 RouteSyncScheduler（backend 侧没有版本列读取，但可加一个只读 checksum 轮询或监听外部变更）；或引入 Redis pub/sub（README 预留方向）。至少把这一点写进多实例落点文档。

### F6. [P3] `route_config` 无"变更来源"字段，多写者不可区分
- **位置**：`backend/src/main/resources/db/migration/V1__init.sql:12-25`
- **为什么**：只有 version/timestamps；审计在应用层且只覆盖仪表盘写路径。未来多写者（脚本/网关直写）无追溯依据。
- **建议修法**：加 `updated_by`/`source` 列；或在 ADR 中明确"仪表盘是唯一写者"约束。

---

## 三、保存即生效（ADR 0003）

### F7. [P3] afterCommit 刷新时序正确，但存在毫秒级生效窗口（可接受）
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:100-117`
- **为什么**：用 `TransactionSynchronization.afterCommit` 先提交后刷新，读到的必是已提交数据，符合 ADR 0003；这是正确做法（若在提交前刷新会读到旧数据）。残余窗口：`RefreshRoutesEvent` 发布后，SCG `CachingRouteLocator` 的重新加载是异步 subscribe，事件返回后到缓存更新之间网关仍命中旧缓存——毫秒级，热刷新场景可接受。另注：afterCommit 若抛异常会冒泡给调用方（当前 `refresh()`/`refreshAll()` 均吞异常或异步，实际安全）。
- **建议修法**：无需修；如需严格语义，可等缓存更新完成再返回（成本不值）。

### F8. [P2] `create()` 并发重复 routeId 返回 500 而非 409
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:43-53`（`existsByRouteId` 检查后 save，check-then-act 非原子）；`backend/src/main/java/com/gatewaydashboard/common/GlobalExceptionHandler.java:69-74`（兜底 500）
- **为什么**：并发两个同 routeId 创建请求都通过 `existsByRouteId`，一个成功、另一个在唯一约束上抛 `DataIntegrityViolationException` → 500。数据完整性有唯一索引保底（不会脏数据），但 API 语义错误（应为 409），且 500 会误导前端。
- **建议修法**：捕获 `DataIntegrityViolationException`（`saveAndFlush` 后）映射为 409（与 `ObjectOptimisticLockingFailureException` 处理并列）；或依赖 DB 唯一约束 + 冲突映射，去掉前置 exists 检查。

### F9. [P3] `delete()` 无乐观锁保护（并发 update+delete 静默赢）
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:76-81`
- **为什么**：`@Version` 只作用于 UPDATE；Hibernate 默认 DELETE 不带版本条件。并发"一个请求更新、一个请求删除同一路由"时，删除在更新提交后执行则更新结果静默消失，无 409 提示。管理后台低并发场景风险低。
- **建议修法**：如需严格，改软删除或对 delete 显式做版本校验（如先 `saveAndFlush` 一次触发版本检查再删）；或接受现状并记录为已知边界。

### F10. [P3] 审计与业务同事务：审计故障会回滚路由保存
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:50,70,79,95`；`backend/src/main/java/com/gatewaydashboard/audit/AuditService.java:20-29`
- **为什么**：审计插入与路由变更同事务，保证"凡生效必有审计"（符合 ADR 0003 定位），但 audit_log 表满/故障时路由保存整体失败。属设计取舍。
- **建议修法**：维持现状即可；若想"审计尽力而为"，给 `record` 加 `REQUIRES_NEW` 或异步写，同时接受审计可能缺失。

---

## 四、外部网关双通道（推送 + 轮询）

### F11. [P2] 推送 fire-and-forget 且无超时控制
- **位置**：`backend/src/main/java/com/gatewaydashboard/config/ExternalGatewayRefreshService.java:38-52`
- **为什么**：`webClient.post().subscribe(...)` 不保留 Disposable、无 `.timeout()`。外部网关不可达（如 IP 黑洞）时请求长时间悬挂：pushRecords 不更新（状态页"最近推送"陈旧）、连接在事件循环上堆积。注释明确"尽力而为 + 轮询兜底"，故不算 P1，但悬挂请求是对资源的真实浪费。
- **建议修法**：加 `.timeout(Duration.ofSeconds(3))`（与状态页探测一致）；推送记录增加 pending 状态；对同一网关的并发推送做合并/节流（短时间多次保存只推最后一次）。

### F12. [P2] `markRefreshed` 可能盖掉一次未被 refresh 观察到的变更（窄窗口竞态）
- **位置**：`gateway-demo/src/main/java/com/example/gatewaydemo/route/RouteSyncScheduler.java:40-64`
- **为什么**：推送到达 → `refresh()`（异步 fetch DB）→ `markRefreshed()` 立即读当前校验和并标记"已刷新"。若在 refresh 的 DB 读取与 markRefreshed 的校验和读取之间恰好有新的提交（亚毫秒窗口），新变更被标记为已刷新但网关内存并未加载 → 该变更直到下次写入/重启才生效。且 `poll()`（调度线程）与 `markRefreshed()`（请求线程）并发 check-then-act 无锁，volatile 只保证可见性不保证原子性。重复刷新本身无害（幂等重读），所以"防重复"优化引入的竞态收益小于风险。
- **建议修法**：最简——去掉 `lastRefreshedChecksum` 二次判断，poll 只在 `current != lastChecksum` 时刷新（推送后 poll 可能再刷一次，无害）；或给 poll/markRefreshed 加同一把锁并在锁内重读校验和。

### F13. [P2] 校验和 (COUNT, SUM(version)) 存在理论碰撞，轮询兜底可漏检
- **位置**：`gateway-demo/src/main/java/com/example/gatewaydemo/route/RouteSyncScheduler.java:66-77`（`CONCAT(COUNT(*), ':', COALESCE(SUM(version),0))`）
- **为什么**：SUM(version) 对"仅更新"是单调的（每次 +1），修复了 MAX(version) 漏检非最大版本行的问题；但 DELETE 会使 SUM 减小，**删除 + 插入 + 多次更新的组合可产生相同的 (count, sum)**。例：版本 `[1,2,3]`（count=3, sum=6）与"删除 v3 行 + 插入两新行 + 数次更新"得到的 `[0,0,6]`（count=3, sum=6）校验和相同而配置不同 → 轮询漏检（此时若推送也失败，变更永远不生效）。概率低（需要巧合序列），但"兜底通道"不应有理论漏洞。
- **建议修法**：改用单调的全局水印——如新增 `route_sync` 单行表存 `revision`（每次写路由时 +1），轮询只比较该值；或聚合 (COUNT, SUM(version), SUM(id)) 增强签名；或对每行算 hash 后求和。推荐水印方案（也顺带解决多实例问题）。

### F14. [P3] 推送与轮询并发时会产生重复刷新（无害）
- **位置**：`gateway-demo/.../route/RouteSyncScheduler.java:40-52` 与 `RouteRefreshController.java:52-53`
- **为什么**：push 与 poll 无互斥，最多多刷一次，刷新幂等。仅日志噪音。
- **建议修法**：随 F12 一并处理。

### F15. [P2] `/internal/routes/refresh` 的 token 校验默认放行空 token
- **位置**：`gateway-demo/src/main/java/com/example/gatewaydemo/route/RouteRefreshController.java:67-74`
- **为什么**：`internalToken` 为空/空白时直接 `return`（放行）。若运维误配空 token（以为该配置可省略），`/internal/routes/refresh` 与 `/internal/routes` GET 完全公开——任何能触达 8088 端口的人都能强制网关刷新/枚举生效路由。另 `token.equals(...)` 非恒定时间比较（内网场景低风险）。
- **建议修法**：默认拒绝（token 必须配置且匹配才放行）；比较改用 `MessageDigest.isEqual`；文档明确"生产必须配置强随机 token + 内网访问限制"。

---

## 五、权限规则 DB 化（ADR 0005）

### F16. [P3] 权限缓存 reload 发生在事务提交前，与其他模块的 afterCommit 模式不一致
- **位置**：`backend/src/main/java/com/gatewaydashboard/permission/PermissionRuleService.java:54-95,100-106`
- **为什么**：`create/update/delete` 内 `repository.save(...)` 后立即 `reload()`（依赖 Hibernate auto-flush 使同事务查询可见）。窗口内其他线程可看到"库中尚无、缓存已有"的新规则（半生效）；若事务在 reload 后回滚（当前代码路径几乎不可能，但模式不健壮），缓存与库永久不一致。RouteService 特意用 afterCommit，两处风格相悖。
- **建议修法**：统一为事务提交后 `reload()`（复制 RouteService 的 `TransactionSynchronization` 模式），保证"缓存更新 = 已提交"。

### F17. [P3] 自我保护 guard 单事务内快照判断，极端并发可绕过（风险极低）
- **位置**：`backend/src/main/java/com/gatewaydashboard/permission/PermissionRuleService.java:149-162`
- **为什么**：guard 用 `repository.findAll()` 快照 + 新规则模拟匹配 `POST /api/permission-rules/__guard__`，能防删除/修改唯一 ADMIN 规则和高优先级非 ADMIN 规则遮蔽。极端并发（两个事务各自快照通过、合并后 ADMIN 失去访问）理论上可构造，但需同时改两条含 ADMIN 规则且每条各自 guard 通过——当前种子规则结构下难以构造。fail-closed 默认拒绝（`match()==null → deny`）是正确的安全方向。
- **建议修法**：可接受；补充并发修改规则的集成测试即可；guard 只覆盖应用写路径，直连 DB 改规则超出边界（文档注明）。

### F18. [P3] JWT 角色在 token 内固化，角色变更/停用最长 12h 不生效
- **位置**：`backend/src/main/java/com/gatewaydashboard/auth/JwtService.java:27-35`；`backend/src/main/java/com/gatewaydashboard/auth/JwtAuthenticationFilter.java:29-31`
- **为什么**：过滤链直接从 token 取 `role` claim 构造 authorities，不查库。当前无角色管理 API，实际风险低；但"停用用户"（`User.enabled`）对已签发 token 无效。
- **建议修法**：v2 引入角色变更/用户管理时，改为过滤链查库（或缩短 token 有效期 + 黑名单）。

---

## 六、并发与事务边界

### F19. [P2] WebFlux 事件循环线程上执行阻塞 JPA/JDBC 调用（同进程内嵌网关放大影响）
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteController.java:33-34,53-54`、`audit/AuditController.java:24`、`permission/PermissionRuleController.java:28-34`、`gateway/GatewayStatusService.java:39,45`（`Mono.just(...)` 内直接调 blocking service；`routeConfigRepository.count()` 直调）；`gateway-demo/.../route/DbRouteDefinitionLocator.java:42-51`（locator 内 blocking JDBC）
- **为什么**：所有管理 API 在 handler 线程（Reactor Netty 事件循环）同步执行 JPA 查询。**由于 ADR 0002 是"管理后台 + 网关同进程"，网关的业务转发与这些阻塞调用共享同一批事件循环线程**：管理 API 的慢查询/高并发会直接拖慢业务转发延迟。当前低流量可运行，但这是同进程架构下最值得提前修正的隐患。
- **建议修法**：阻塞调用包 `Mono.fromCallable(() -> service.xxx()).subscribeOn(Schedulers.boundedElastic())`（Controller 层统一封装）；或引入响应式数据访问（R2DBC）作为长期方向；`gateway-demo` locator 的 blocking JDBC 与 SCG 官方 `JdbcRouteDefinitionRepository` 同款，可接受。

### F20. [P2] 多写者并发总评：update 有乐观锁，create/delete 边界有缺口
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:64-69`（update 捕获 `ObjectOptimisticLockingFailureException` → 409）、`:93-94`（setEnabled 的 OOLFE 走 `GlobalExceptionHandler.java:27-31` 兜底 409）
- **为什么**：并发 update 同一路由 → 409，正确且双保险；但并发 create 同 ID → 500（F8）、并发 update+delete → 删除静默赢（F9）。不同路由并发写 → 各自 afterCommit 刷新，以 DB 为真源最终一致，无问题。总体"核心冲突可感知，边界缺口可接受"。
- **建议修法**：落实 F8/F9 后，此条目闭环。

### F21. [P3] refresh 与写入时序：以 DB 为真源保证最终一致（正面）
- **位置**：`backend/src/main/java/com/gatewaydashboard/route/RouteService.java:100-117` + `backend/.../route/DbRouteDefinitionLocator.java:17-20`
- **为什么**：任何写提交后必然触发一次重读 DB 的刷新；刷新永远读最新已提交状态，不存在"旧配置覆盖新配置"。短暂滞后（F7 窗口）后收敛。
- **建议修法**：无需改；可作为后续集成测试的断言点。

---

## 七、扩展点与单实例边界

### F22. [P3] 多实例落点清晰但代码未留 seam：刷新传播机制不可替换
- **位置**：`README.md:166-167`、`docs/adr/0002-embedded-admin-in-gateway-process.md:7`（均已声明单实例、预留 Redis pub/sub）；`backend/.../route/RouteRefreshService.java:14-16` 直接 `publishEvent(RefreshRoutesEvent)`
- **为什么**：刷新传播硬编码为进程内事件；未来多实例（无论 Redis pub/sub 还是轮询）需要替换传播机制，当前无端口（接口）可插拔。外部网关侧已有 `RouteSyncScheduler` 轮询模式可复用。
- **建议修法**：为"刷新传播"定义端口（如 `RouteRefreshPort`），单实例实现 = 进程内事件 + 外部网关推送；多实例实现 = pub/sub + 轮询。改动小、收益明确。

### F23. [P3] gateway-demo 无 Flyway/建表保障，依赖 backend 先迁移
- **位置**：`gateway-demo/pom.xml`（无 flyway 依赖）；`gateway-demo/.../route/RouteSyncScheduler.java:66-77`（表不存在时每 5s warn 一次）
- **为什么**：demo 直连 `route_config`，若库是全新库且未先启动 backend，表不存在，poll 持续告警、locator 加载失败。README 未强调启动顺序。
- **建议修法**：README 注明"先启 backend（Flyway 建表）再启 gateway-demo"；或在 demo 中引入 flyway。

### F24. [P3] 种子账号密码硬编码于代码
- **位置**：`backend/src/main/java/com/gatewaydashboard/config/SeedDataInitializer.java:34-41`（admin/admin123、viewer/viewer123）
- **为什么**：演示项目可接受（README 已文档化），但交付/演示环境易被忽略而带默认口令上线。
- **建议修法**：支持环境变量覆盖种子密码，首次启动日志提示修改。

---

## 八、杂项（P3）

### F25. [P3] "网关状态"页的"生效路由"实为数据库直读，非网关内存生效状态
- **位置**：`backend/src/main/java/com/gatewaydashboard/gateway/GatewayStatusService.java:24`（`@Qualifier("routeDefinitionLocator")` 注入的是无缓存的 `CompositeRouteDefinitionLocator`，而非 `CachingRouteLocator`）、`:45-46`；`gateway-demo/.../route/RouteRefreshController.java:38,61-64`（同样取 composite 直读库）
- **为什么**：SCG 自动配置中 `routeDefinitionLocator` bean = 无缓存 composite（直接查 DB），带缓存的 `cachedCompositeRouteLocator` 才是网关实际生效的源。因此状态页展示的是"数据库中的启用路由"而非"网关内存生效路由"——正常时序下两者一致，但 (a) 无法真正验证网关已加载（产品定位"用于验证保存即生效"被削弱）；(b) refresh 异步窗口内展示与运行态不一致；(c) 每次打开状态页都全表查库。
- **建议修法**：注入 `CachingRouteLocator`（或 `RouteLocator` bean `cachedCompositeRouteLocator`）展示真实生效路由；若想同时展示"库中配置 vs 网关生效"差异，可两列对比（更有诊断价值）。

### F26. [P3] CORS 双配置（Security `.cors` + 独立 `CorsWebFilter` bean）
- **位置**：`backend/src/main/java/com/gatewaydashboard/config/SecurityConfig.java:39-40`（`.cors(cors -> {})` 空配置）与 `:77-87`（`CorsWebFilter` bean）
- **为什么**：security 内 CORS 空 customizer 无配置源，实际 CORS 头来自独立 `CorsWebFilter`；两者叠加可能重复设置 `Access-Control-Allow-Origin` 响应头（部分浏览器对重复头行为不一致）。
- **建议修法**：只保留其一——在 security 链中配置 `CorsConfigurationSource` 并删除独立 filter，或反之。

### F27. [P3] 审计 JSON 截断可能切在 UTF-16 代理对中间
- **位置**：`backend/src/main/java/com/gatewaydashboard/audit/AuditService.java:44-49`（`substring(0, MAX_JSON_LENGTH)`）
- **为什么**：Java `String.substring` 按 UTF-16 code unit 截断，含 emoji/生僻字时可能截出孤立代理项，写库/反序列化时可能出错。
- **建议修法**：截断后若末尾是高代理，去掉该 code unit；或按 code point 截断。

### F28. [P3] 状态页聚合外部网关为串行等待，慢网关拖慢整页
- **位置**：`backend/src/main/java/com/gatewaydashboard/gateway/ExternalGatewayStatusService.java:46-54`
- **为什么**：`Mono.zip` 等待全部网关（各 3s 超时），一个慢/不可达网关使状态页整体延迟至超时。
- **建议修法**：给整体 zip 加总超时，或单网关失败立即降级（已有 onErrorResume，主要补总超时）。

---

## 优先级汇总

- **P0**：无
- **P1**：无
- **P2**：F1（route↔config 环）、F4（双份 locator）、F5（内嵌网关无兜底）、F8（create 并发 500）、F11（推送无超时）、F12（markRefreshed 竞态）、F13（校验和碰撞）、F15（内部接口空 token 放行）、F19（事件循环阻塞）、F20（create/delete 并发缺口）
- **P3**：F2、F3、F6、F7、F9、F10、F14、F16、F17、F18、F21、F22、F23、F24、F25、F26、F27、F28

## 建议优先处理顺序

1. **F15**（内部接口鉴权，安全面，改动一行语义即可）
2. **F19**（事件循环阻塞，同进程网关放大的性能隐患，改动面小收益大）
3. **F12 + F13**（轮询兜底的可靠性，兜底通道不应有理论漏洞）
4. **F8**（错误语义，一行 catch）
5. **F11**（推送超时）
6. **F1 / F4**（模块化与复制代码，投入较大，可在重构窗口处理）
