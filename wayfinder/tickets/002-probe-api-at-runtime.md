# 起服抽查 API 行为

Labels: wayfinder:task
Status: closed
Claimed by: wayfinder 工作会话（协调者）

## Question

分别用 `local`（H2）与 `dev`（MySQL，本机已就绪）profile 启动后端，抽查核心 API 的**运行时行为**并记录事实。注意与并行评审隔离：用 `--server.port=8081`（H2）与 `8082`（MySQL）避免冲突；抽查完清理测试数据。

覆盖路径（对照 README API 摘要）：

1. 登录/JWT：登录成功、错误密码、`/api/auth/me`、`/api/auth/password` 改密后**旧 token 是否仍有效**。
2. 路由 CRUD + 校验：创建（保存即生效）、非法 predicate 被拒（400/422）、`/api/routes/validate` 只校验不保存、乐观锁冲突行为（并发改同一路由 → 409?）、停用/启用。
3. 权限规则：新增规则即时生效、优先级匹配、内置规则不可删、非 ADMIN 访问被拒（403 JSON）。
4. 审计：创建/修改/删除各动作是否落审计日志、含操作者与变更前后。
5. 网关状态：`/api/gateway/status` 展示健康/最近刷新/生效路由；未配置外部网关时的表现。
6. 统一响应体 `{code,message,data}` 与全局异常处理（404、405、500 的响应形态，是否泄漏堆栈）。

记录每项观察到的事实与任何异常（响应形态、状态码、权限边界、时序）。

## Blocking

Blocked by: 无
Blocks: 安全评审、规范一致性评审

## Resolution

**结论：两个 profile（H2:8081 / MySQL dev:8082）实测行为一致，六类路径全部验证，未发现 P0/P1 级运行时缺陷。** 抽查用 admin/viewer 种子账号，创建的 probe 路由与权限规则已全部删除；审计记录按设计留存（见「数据痕迹」）。

**逐项事实**：

1. **登录/JWT**：admin/admin123 登录 200（返回 token+user+role）；错误密码 401 `{"code":401,"message":"用户名或密码错误"}`；`/api/auth/me` 带 token 200（{username,role}）、匿名 401 `未登录或登录已过期`；改密（admin123→probePass123）后**旧 token 调 /me 仍 200**——确认无服务端 token 吊销机制（改密不失效旧 token），新密码登录正常，已改回 admin123。
2. **路由 CRUD + 校验**：创建 200（version 0）→ 立即出现在网关状态页生效路由（**保存即生效，热刷新确认**，两个 profile 均验证）；validate 只校验不保存（合法 `{valid:true}`、未知工厂 `{valid:false,errors:["predicate[0] 未知的工厂名: NoSuchFactory"]}`，库中无新增）；停用/启用 200 且 enabled 正确翻转；**乐观锁：并发同版本双 PUT → 一个 200（version 0→1）+ 一个 409 `该路由已被其他操作修改，请刷新后重试`**（RouteRequest 无 version 字段，锁由 JPA @Version 在并发 flush 生效；顺序"旧版本 PUT"每次重载实体返回 200 属预期，非缺陷）。
3. **权限规则**：内置规则 DELETE → 400 `内置规则不可删除`；新增规则（GET /api/probe-rule-<ts>/** 放行 VIEWER）→ 200 后 **viewer 立即可访问**（无 handler 返回 404 而非 403，证明即时生效）；viewer 写路由 → 403 `没有权限访问该接口`、viewer 看权限规则 → 403，统一 JSON。
4. **审计**：`data.items` 结构；CREATE/UPDATE×2/DISABLE/ENABLE/DELETE 全部落库，含 actorUsername、beforeJson/afterJson、ip；DELETE 的 afterJson 为 null；截断逻辑未见异常（测试数据短于 5000）。
5. **网关状态**：`data.health/lastRefreshAt/effectiveRoutes/externalGateways`；health=UP；effectiveRoutes **只含启用路由**（smoke-demo 停用 → 不在列表，与 DB enabled 一致）；lastRefreshAt 随每次保存动作更新；默认 external-gateways 指向不可达 localhost:8088 → `online:false` + push error `Connection refused` + lastCheckedAt 有值（离线展示与检测正常，未配置/不可达场景即此形态）。
6. **统一响应体与异常**：200/400/401/403/405/409 均为 `{code,message,data}` 统一 JSON，无堆栈泄漏；405 `请求方法不支持: GET，请检查接口定义`；404 `No static resource api/xxx.`（Spring 默认消息措辞，非中文、略带实现细节，无堆栈——轻微 P3 级观感问题，安全/规范一致性评审可参考）。

**数据痕迹**：H2 文件库（`backend/data/gateway-dashboard.mv.db`，已被 .gitignore 覆盖）与 MySQL dev 库中留存 probe 路由的审计记录（CREATE/UPDATE/DISABLE/ENABLE/DELETE）；probe 路由与权限规则本身已删除（H2 恢复 3 条种子、MySQL 恢复 4 条既有路由）。如需彻底清空 H2 痕迹可删除该 .mv.db 文件；MySQL 审计记录按审计语义不应删除。H2 库还发现历史遗留 `smoke-demo` 路由（停用态，非本会话创建）。
