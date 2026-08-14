# 架构与设计评审

Labels: wayfinder:research
Status: closed
Claimed by: wayfinder research 子代理（2571cd1e-20b7-40e1-a077-aac6a68fe2ae）

## Question

评审整体架构与设计（静态阅读，不启动服务）：

1. **模块分层**：`route` / `auth` / `permission` / `audit` / `gateway` / `config` / `common` 各包的职责边界与依赖方向是否清晰、有无环或反向依赖。
2. **真源设计**：「数据库为路由唯一真源」+ 自定义 `RouteDefinitionLocator` 接入 Spring Cloud Gateway——对照 ADR 0001、0002 判断实现是否忠于决策。
3. **保存即生效**：写库 → 事务提交 → `RefreshRoutesEvent` 热刷新的时序与失败路径（对照 ADR 0003）。
4. **外部网关集成**：推送 `/internal/routes/refresh` + 5 秒轮询校验和兜底——双通道的一致性、竞态（`RouteSyncScheduler` 轮询与推送并发）、失败恢复。
5. **权限规则 DB 化**：动态 `AuthorizationManager` + 内存缓存 + 自我保护（内置规则/锁死防护）设计（对照 ADR 0005）。
6. **并发与事务边界**：乐观锁版本、同一真源多写者（仪表盘 + 外部网关）、refresh 与写入的时序。
7. **扩展点与单实例边界**：单实例限制、未来多实例的落点。

产出：架构判断（好坏与风险）+ 发现列表（每条标 P0-P3、位置、为什么、建议修法）。已知疑点供核查：`RouteSyncScheduler` 轮询/推送竞态、推送 fire-and-forget。

## Blocking

Blocked by: 无
Blocks: 合成评审报告

## Resolution

**结论：架构骨架良好，忠于 ADR 决策，无 P0/P1。** 数据库唯一真源 + 自定义 RouteDefinitionLocator 忠实落地 ADR 0001/0002；保存即生效用 `afterCommit` 保证先提交后刷新（ADR 0003 时序正确）；权限 DB 化 + fail-closed + 自我保护与 ADR 0005 一致；外部网关"推送 + 5s 轮询校验和兜底"双通道合理、失败可自愈；包划分清晰（common/auth/audit 无反向依赖）。风险集中在 P2×10 + P3×18。

**P2 要点（10）**：
- F15 `gateway-demo/RouteRefreshController:67-74` — 内部 token 为空/空白时**直接放行**，误配即内部接口公开（应默认拒绝 + 恒定时间比较）
- F19 阻塞 JPA 全部跑在 WebFlux 事件循环线程（Controller + GatewayStatusService；应 `subscribeOn(boundedElastic)`，长期可 R2DBC）
- F12 `RouteSyncScheduler:40-64` — markRefreshed 竞态：push 与 poll 并发 check-then-act，存在漏生效窗口（去二次判断或加锁）
- F13 校验和 (COUNT,SUM(version)) 理论碰撞：删除+插入+多次更新可同值 → 轮询漏检（应用单调 revision 水印）
- F8 `RouteService.create()` 并发同 routeId 抛 500 而非 409（捕获 DataIntegrityViolationException 映射 409）
- F11 推送 fire-and-forget 无超时：网关不可达时悬挂/连接堆积（应 timeout + 节流）
- F1 route ↔ config 包级环（业务包依赖 config 推送、config 种子依赖 route；推送服务应迁独立包）
- F4 backend 与 gateway-demo 双份平行 DbRouteDefinitionLocator（JPA vs JdbcTemplate），语义易漂移
- F5 内嵌网关无外部变更兜底（与外部网关不对称，多实例共库时静默失效）
- F25 「网关状态」页"生效路由"实为 DB 直读（注入的是无缓存 CompositeRouteDefinitionLocator 而非 CachingRouteLocator），无法真正验证"网关已加载"，且每开状态页全表查库

**P3 要点（18，详见文件）**：gateway 包耦合 route 内部；config 包职责过载；route_config 无变更来源字段；delete 无乐观锁；审计与业务同事务；权限缓存 reload 在事务提交前（风格不一致）；guard 单事务快照；JWT 角色固化 12h；多实例刷新无 seam；gateway-demo 依赖 backend 先建表；种子账号硬编码；CORS 双配置；审计截断切中代理对；状态页聚合外部网关串行等 3s 等。

**建议修复优先级**（子代理给出）：F15 → F19 → F12+F13 → F8 → F11 → F1/F4。

**完整发现**：`wayfinder/findings/architecture-review.md`（28 条，逐条 文件:行号），一次性分支 `research/architecture-review`，commit `211c9fc`（未并入 main）。
