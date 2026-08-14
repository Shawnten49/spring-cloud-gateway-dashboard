# 安全评审发现（ticket 005）

- 分支：`research/security-review`（一次性分支，未并入 main）
- 评审方式：**静态阅读**（未启动服务、未跑测试、未改动任何现有文件）；引用 ticket 002「起服抽查 API 行为」的已实测运行时事实
- 评审范围：backend `auth` / `permission` / `config` / `common` 包、`gateway-demo` 的 `RouteRefreshController`/`RouteSyncProperties`、`backend`+`gateway-demo` 的 resources yml、`db/migration/V1/V2`、`docker/docker-compose.yml`、`frontend/src`（stores/auth.ts、api/http.ts、LoginView.vue、router、App.vue、AuditLogView.vue）
- 分级口径（与 003 架构评审一致）：P0 阻断（数据损坏/安全漏洞/核心功能不可用）；P1 高（真实部署即暴露的高危问题）；P2 中（控制失效/边界缺陷，需前置条件）；P3 低（加固/观感/纵深防御）

## 总体安全判断

**安全基线总体合格，但「开箱即用」的默认配置存在三个可被匿名利用的 P1 级接管路径（JWT 密钥公开可伪造、默认管理员口令公开、MySQL 直连凭据公开），任一命中即完全控制网关配置面。** 认证与授权的运行时行为与设计一致（002 实测：错误密码 401 统一文案、viewer 写路由/看规则 403、内置规则不可删、规则即时生效），fail-closed 与统一 JSON 响应无堆栈泄漏是正确实现。主要缺口集中在：**密钥/口令/凭据的默认值治理（P1）、JWT 生命周期与吊销（P2）、外部网关内部接口信任边界（P2）、审计属性可伪造（P2）、权限自我保护守卫不完整（P2，可致 ADMIN 自锁 + VIEWER 提权到路由写，见 S-13）**。未发现 P0（无远程代码执行、无注入、无未认证任意文件访问、无现成 XSS 汇点）。

---

## 威胁模型（谁攻击 / 怎么攻击 / 影响）

| 攻击者 | 入口 | 路径 | 影响 |
|---|---|---|---|
| A. 匿名网络访问者（能触达 8080/8088/3306） | 登录页、/api、/internal/routes、MySQL 端口 | 默认口令登录；伪造 JWT；直连数据库 | 完全接管（改路由=流量劫持/数据外泄、改权限规则、删审计） |
| B. 低权用户（VIEWER） | /api | 读审计、读路由、伪造 XFF | 信息读取、取证混淆（无写面，后端兜底 403） |
| C. 失陷会话/泄露 token | 携带合法 token | 改密后 token 仍有效 12h；降权/停用不即时生效 | 持续越权窗口 |
| D. 运维误配 | 配置项 | internal-token 留空/默认、compose 3306 暴露 | 内部接口公开、DB 裸奔 |

---

## 发现清单

### P1

#### S-01 [P1] JWT 签名密钥默认值公开，可离线伪造任意 ADMIN token（默认配置即认证绕过）

- **位置**：
  - `backend/src/main/resources/application.yml:27` — `secret: ${JWT_SECRET:gateway-dashboard-dev-secret-change-me-0123456789}`（默认值硬编码，49 字节=392 bit）
  - `docker/docker-compose.yml:31` — `JWT_SECRET: gateway-dashboard-compose-secret-change-me-0123456789`（交付部署路径的密钥同样是**仓库内公开的固定字符串**）
  - `auth/JwtService.java:23`（`Keys.hmacShaKeyFor(secret.getBytes(...))`）、`:27-36`（generate 只签 sub+role）
  - `auth/JwtAuthenticationFilter.java:26-37`（只验签+过期，不校验密钥来源/用户状态）
- **为什么**：jjwt 0.12.6 `signWith(key)` 按密钥长度自动选 HS384（392 bit → HS384），算法本身安全；但**密钥是仓库内公开的常量**。攻击者只需知道该字符串，即可用 jjwt 自行签发 `sub=admin, role=ADMIN` 的合法 token，**无需任何凭据**直接获得管理员权限（改路由=把网关流量导向攻击者服务器、改权限规则、读/改审计、删路由=DoS）。compose 是唯一文档化部署路径，其中 `JWT_SECRET` 同样是固定公开值 → 该路径下 8080 若可达公网，等于未认证接管（接近 P0，但需"部署时未改密钥"这一条件，故定 P1）。
- **建议修法**：① 启动时强校验——生产 profile 下 `JWT_SECRET` 必须来自环境变量且不等于任何已知默认值，否则启动失败（`@PostConstruct`/`Environment` 检查）；② compose 改为 `JWT_SECRET: ${JWT_SECRET:?}` 强制注入；③ 部署时用 `openssl rand -base64 48` 生成随机密钥；④ 密钥轮换机制与 `kid` 头。

#### S-02 [P1] 默认管理员口令硬编码且明文印在登录页（默认凭据漏洞）

- **位置**：`config/SeedDataInitializer.java:34-41`（`admin/admin123`、`viewer/viewer123`，BCrypt 落库）；`frontend/src/views/LoginView.vue:45-47`（登录页明文展示"预置账号：admin / admin123（管理员）…"）
- **为什么**：任何全新库部署都会重建这两个已知账号（`:34` `:38` 仅按用户名存在性跳过），且登录页把 ADMIN 口令直接公开展示——等于向每个访问者发放超级管理员凭据。本应用 ADMIN 可增删路由（网关流量劫持/外泄）、改权限规则、读审计，口令泄露=完全接管。002 实测 admin/admin123 可正常登录，证实默认态可直入。
- **建议修法**：① seed 口令改从环境变量读取，未配置则随机生成并仅启动日志打印一次；② 删除 LoginView 的预置账号展示（或仅展示用户名不展示口令）；③ 首次登录强制改密（或启动检查默认口令未改则告警/拒绝登录）；④ 与 S-01 合并做"生产 profile 启动安全检查"。

#### S-03 [P1] docker-compose 将 MySQL 3306 映射到宿主机全接口 + 已知口令，可直连数据库完全绕过应用鉴权

- **位置**：`docker/docker-compose.yml:8-13`（`MYSQL_ROOT_PASSWORD: root123`、`MYSQL_USER: gateway`、`MYSQL_PASSWORD: gateway123`、`"3306:3306"` 绑定所有网卡）；`backend/src/main/resources/application-dev.yml:4-5` 与 `gateway-demo/src/main/resources/application.yml:8-10`（`gateway/gateway123` 明文入库）
- **为什么**：compose 交付路径把 MySQL 端口发布到宿主机任意接口，口令是仓库公开常量。攻击者 `mysql -h <host> -ugateway -pgateway123` 直连后：读改写 `route_config`（**gateway-demo 的 DbRouteDefinitionLocator 每 5 秒直读该表生效，改路由=改网关行为**）、改 `permission_rule`（下次 reload 即生效）、读/改 `audit_log`（抹除痕迹）、读 `sys_user` 或直接插一个 ADMIN 用户——**完全绕过应用全部认证与审计**。`useSSL=false`（application-dev.yml:3 / gateway-demo application.yml:8）使链路上凭据/数据明文。
- **建议修法**：① compose 不发布 3306（或 `127.0.0.1:3306:3306`）；② 数据库口令用 secrets/环境变量注入强随机值，应用侧 `SPRING_DATASOURCE_PASSWORD` 覆盖；③ 数据库账号最小权限（`gateway` 仅 DML，DDL/管理员账号分离）；④ MySQL 开 TLS 或限制来源 IP；⑤ 补充说明"交付参考"文件上线的前置条件。

---

### P2

#### S-04 [P2] 无 token 吊销/黑名单，角色固化在 JWT 12h：改密/停用/降权后旧 token 仍有效（ticket 疑点定性：**P2**，不是 P1 也不是 P3）

- **位置**：`auth/JwtAuthenticationFilter.java:26-37`（只验签+exp，**不查用户 enabled/角色**）；`auth/JwtService.java:33`（exp 12h）；`auth/AuthService.java:41-50`（改密只换 hash，不失效旧 token）；`User.java:38`（enabled 字段存在但仅在登录时检查 `AuthService.java:25-27`）
- **为什么（威胁模型判断）**：002 已实测"改密后旧 token 调 /me 仍 200"。定性为 P2 的理由：该缺陷**不可独立利用**——攻击者必须先获得一个合法 token（XSS/日志泄漏/共享机器）或管理员必须先对账号做降权/停用操作，它才产生危害；危害是**有界窗口**（≤12h）内的"补救控制失效"：失陷后唯一可用的响应（改密/停用/降权）不生效，被降权的 ADMIN 旧 token 仍带 `ROLE_ADMIN` 继续写路由。它不构成 P1 是因为当前系统的支配性威胁（S-01/S-02 默认值）根本不需要偷 token；也不只是 P3，因为它直接击穿账号生命周期管理的标准补救路径，且与 S-12（token 窃取面）组合后放大。若未来修复了 S-01/S-02 后重新评估，可上调至 P1。
- **建议修法**：① `sys_user` 加 `token_version`，改密/停用/降权时 `+1`，JWT 携带 `jti`/版本号，过滤器验签后查用户当前版本比对（DB 或短 TTL 缓存），不匹配即 401；② 或引入服务端黑名单（内存/Redis）记录被吊销的 `jti` 至过期；③ 至少将 12h 缩短（如 2-4h）并文档化"改密后旧会话最长存活"边界；④ 过滤器在验签后校验 `enabled`（低开销缓存）。

#### S-05 [P2] 外部网关内部接口鉴权薄弱：空 token 放行 + 默认 token 公开 + 非恒定时间比较（交叉 003-F15）

- **位置**：`gateway-demo/.../RouteRefreshController.java:67-74`（`internalToken` 为 null/空白 → **直接 return 放行**）、`:71`（`token.equals(...)` 非恒定时间比较）；`gateway-demo/.../RouteSyncProperties.java:11`（`internalToken = "gd-internal-token-dev"` 硬编码默认）；`gateway-demo/src/main/resources/application.yml:22` 与 `backend/src/main/resources/application.yml:33`（同一公开默认 token）
- **为什么**：攻击者只要能触达 gateway-demo:8088：① 用仓库公开的默认 token 即可 `GET /internal/routes`——**读取全部生效路由的 routeId+uri+order**（内部拓扑/目标地址信息泄漏）并 `POST /internal/routes/refresh`（触发刷新抖动，低成本骚扰）；② 若运维将 internal-token 留空（配置里去掉即可触发 `:68` 的放行分支），则**两个接口完全无鉴权**；③ `String.equals` 非恒定时间，理论存在时序侧信道逐字符探测 token（网络噪声下实践困难，但属应修的坏味道）。后端侧 `ExternalGatewayRefreshService.java:38-41` 推送用同一公开 token，无独立影响。
- **建议修法**：① `requireToken` 改默认拒绝：token 配置为空时**拒绝所有请求**并启动日志告警（"内部接口未配置 token，已拒绝访问"）；② 改用 `MessageDigest.isEqual`/`Mac` 恒定时间比较；③ token 用环境变量注入强随机值，且与 backend 的 `external-gateways[].token` 保持一致；④ 文档/部署限制 `/internal/*` 仅内网可达（网络层白名单）。

#### S-06 [P2] 审计 IP 信任 `X-Forwarded-For` 可伪造，削弱 ADR 0003 的补偿控制（交叉 004-P3-18，安全视角上调）

- **位置**：`common/SecurityUtils.java:17-25`（取 `X-Forwarded-For` 首段）；`frontend/nginx.conf:10`（`proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`——客户端可自带 XFF，nginx 只是**追加**，首段仍是攻击者伪造值）；消费点 `RouteController.java:46,54,60,70`（audit ip 落库）
- **为什么**：本项目架构决策 ADR 0003 明确"保存即生效（无两阶段发布），用审计日志弥补追溯"——**审计是本系统唯一的事后追溯控制**。而 `clientIp` 直接信任客户端可控头：攻击者（或内部人员）发起任意写操作时带上 `X-Forwarded-For: 203.0.113.9`，审计里就记录成该 IP，归因被抹除；在 nginx 部署形态下该伪造**端到端成立**（nginx 追加真实 IP 到第二段，后端只取首段）。这是取证控制的属性完整性缺陷。
- **建议修法**：① nginx 改为 `proxy_set_header X-Real-IP $remote_addr;` 并**覆盖**（而非追加）XFF，后端只从受信代理读 `X-Real-IP`，直连形态下忽略 XFF；② 或在后端配置受信代理列表，仅当连接来自受信代理时才采信 XFF；③ 代码注释信任边界；④ IPv6 规范化（沿用 004 建议）。

---

### P3

#### S-07 [P3] VIEWER 可读审计日志（含操作者、IP、路由变更前后全量配置）

- **位置**：`db/migration/V2__permission_rule.sql:18`（内置规则 `GET /api/** → AUTHENTICATED`，优先级 10）；`audit/AuditController.java:20-25`
- **为什么**：按内置规则，任何登录用户（含只读 VIEWER）可 `GET /api/audit-logs` 看到所有操作者用户名、来源 IP、路由 before/after 全量配置。增量泄漏有限（路由配置 VIEWER 本就可读 `GET /api/**`，见同规则），但操作者行为画像与 IP 属于"谁做了什么"的敏感元数据；对只读角色属设计可接受，但若角色语义要收紧（如审计仅 ADMIN），当前规则无法区分。**后端无写面，无越权风险**。
- **建议修法**：若需收紧，在 `V2` 之上加高优先级规则 `GET /api/audit-logs/** → ADMIN`（优先级 <10）；并在 002 已确认的 403 兜底之外给前端菜单加角色控制（App.vue:76 已对 permissions 做 `v-if="auth.isAdmin"`，audit 未做）。

#### S-08 [P3] 登录无速率限制/账号锁定，可在线爆破

- **位置**：`auth/AuthService.java:22-31`（login 直接查库比对，无节流）；`frontend/src/views/LoginView.vue:45-47`（页面已公开用户名 admin/viewer，爆破只需猜口令）
- **为什么**：修复 S-02 默认口令后，攻击者仍可对已知用户名做在线口令爆破（BCrypt cost 10 只拖慢服务端，不阻止攻击）；无锁定/退避意味着 6 位短口令（见 S-09）在无日志告警下可被逐步试出。
- **建议修法**：登录失败计数 + 退避/锁定（内存或 Redis），或接入网关层限流；对 login 端点做失败告警；至少记录失败登录审计。

#### S-09 [P3] 密码策略过弱：仅 `@Size(min = 6)`

- **位置**：`auth/AuthDtos.java:14-16`（ChangePasswordRequest 新密码最小 6 位，无复杂度/长度上限约束）
- **为什么**：6 位口令空间（纯数字 10^6 / 小写字母 26^6≈3 亿）在现代算力下可离线枚举；配合 S-08 在线爆破风险放大。无最大长度限制（BCrypt 72 字节上限未校验）属于输入卫生问题。
- **建议修法**：min 10-12 + 至少两类字符 + 拒绝常见弱口令列表；上限 64；与 S-02 的"首登强制改密"联动。

#### S-10 [P3] JWT 无 `iss`/`aud` 校验，且任意持有密钥的服务均可互签

- **位置**：`auth/JwtService.java:38-44`（parse 仅 `verifyWith(key)` + 自动验 exp，不校验 `iss`/`aud`）；`:27-36`（token 只含 sub/role/iat/exp）
- **为什么**：共享密钥体系下，任何知道该密钥的服务（含被 S-01 公开的默认值）签发的 token 都被接受；无 `iss`/`aud` 使 token 可在不同服务间串用（当前单服务影响小，拆分后放大）。属加固项。
- **建议修法**：签发时加 `issuer("gateway-dashboard")` 与 `audience`，parse 时 `.requireIssuer(...)`；密钥轮换带 `kid`。

#### S-11 [P3] 未知路径 404 泄漏实现细节「No static resource api/xxx.」

- **位置**：运行时事实（ticket 002 §6，404 `No static resource api/xxx.`）；`common/GlobalExceptionHandler.java`（未覆盖 Spring WebFlux 资源处理器的 404 分支，`ResponseStatusException` 分支 `:62-67` 会透传 reason）
- **为什么**：404 文案暴露"请求被当作静态资源查找"的实现细节与请求路径回显，虽无堆栈，但对探测者可辅助确认后端类型/路径结构；属于信息泄漏观感问题（002 已标记 P3）。
- **建议修法**：加统一的 404 兜底（自定义 `ResourceWebHandler` 或 `ResponseStatusException` 文案映射），返回中文统一 `{code:404, message:"接口不存在"}`。

#### S-12 [P3] 前端 JWT 存 localStorage，XSS 即窃取（纵深防御缺口；当前无现成 XSS 汇点）

- **位置**：`frontend/src/stores/auth.ts:18,33-34`、`frontend/src/api/http.ts:13`
- **为什么**：已 grep 核实前端**无 `v-html`/`innerHTML` 汇点**（Vue 默认转义），当前无已知存储型 XSS 可达；但 token 存 localStorage 意味着任何未来 XSS（或浏览器扩展/同源脚本注入）可 `localStorage.getItem('gateway-dashboard-token')` 直接窃取 ADMIN 会话，且与 S-04 的"无吊销"叠加后无法通过改密补救。审计 before/after JSON 在 `AuditLogView.vue` 以文本渲染（无 v-html），风险面当前可控。
- **建议修法**：改 httpOnly+SameSite cookie 承载 token（配合 CSRF 防护），或至少：sessionStorage + 登录后 `fetchMe` 校验 + 页面级 CSP；把改密接口做成"改密后强制重登"（配合 S-04 的吊销）。

#### S-13 [P2] 权限自我保护 guard 只模拟 POST 单路径——PUT/DELETE 写面未受保护，可致 ADMIN 自锁 + VIEWER 提权到路由写（交叉 004-P2-02、006 测试充分性；本评审初评 P3，按完整攻击面展开后上调 P2）

- **位置**：`permission/PermissionRuleService.java:33`（`SELF_GUARD_PATH="/api/permission-rules/__guard__"`）、`:149-162`（`guardAdminSelfAccess` 仅 `CachedRule.match(cached, "POST", SELF_GUARD_PATH)` 一条模拟）、`:70-81`（`update` 无 builtin 保护，对比 `delete` 有 `:86-88`）、`:111-119` 与 `CachedRule.matches:203-208`（priority 升序首条命中；httpMethod `*` 全匹配）
- **为什么（完整攻击面，逐项推演）**：守卫的唯一不变式是「`POST /api/permission-rules/**` 仍被 ADMIN 命中」（`:158`），其余维度（PUT/DELETE/GET、其它路径、其它角色）完全不在守卫视野：
  1. **触发前提**（需 ADMIN 或失陷 admin 会话先误配/恶意创建，非匿名/VIEWER 独立可达）：新增 `PUT /api/permission-rules/** → VIEWER`，priority < 25（内置 PUT 规则 5 的优先级，`V2__permission_rule.sql:21`；`RuleRequest.priority` 无 `@Min/@Max` 边界，`PermissionRuleDtos.java:20`，负数亦可）→ `create()` 的守卫模拟 POST 命中内置规则 3（ADMIN）→ **守卫通过** → 规则 reload 即时生效。
  2. **ADMIN 自锁（可用性）**：此后 `PUT /api/permission-rules/**` 首条命中 VIEWER 规则 → ADMIN 无法再 update 任何规则；且 roles 是**白名单而非并集**（`isAllowed:121-129` 的 `anyMatch`），`roles="VIEWER"` 使 ADMIN 同时失去该路径写面。恢复路径：仍可 POST 新建更高优先级 `PUT → ADMIN` 覆盖规则（守卫保证 POST 不被锁死），故**非永久锁死**，但已造成实际可用性中断。
  3. **VIEWER 提权（核心，安全影响）**：获得 PUT 的 VIEWER（默认凭据 `viewer123` 公开，见 S-02）可 `update` **任意规则，含内置规则**（`update` 无 builtin 检查，`:70-81`）——把内置路由写规则 8/9/10（`V2__permission_rule.sql:24-26`：POST/PUT/DELETE `/api/routes/** → ADMIN`）的 roles 改为 `VIEWER`/`*`：守卫只模拟 POST 权限配置路径（`:158`），不受影响 → **VIEWER 获得路由增删改**（= 网关流量劫持/DoS 面），且因白名单语义 **ADMIN 同步失去路由写**。同理 VIEWER 可接管规则 1/5/7（GET/PUT/DELETE 权限配置），除 POST 外全部写面沦陷。
  4. **`*` 方法规则**：`* /api/permission-rules/** → VIEWER` 同时覆盖 POST——恰好被守卫拦截（模拟 POST 命中 VIEWER → `:159-160` 拒绝），即守卫对该路径的 `*` 规则**有效**；但守卫**完全不覆盖其它路径**，ADMIN 误配 `* /api/routes/** → VIEWER`（或其它任何路径的 `*` 放行）时无任何保护。
  5. **空 roles**：`RuleRequest.roles` 有 `@NotBlank`（`PermissionRuleDtos.java:19`）但内容不校验；`normalizeRoles`（`:178-185`）过滤空串后可能得到空角色集 → 该规则匹配不到任何用户 → **fail-closed 拒绝**（安全方向正确，无绕过面）；攻击面不依赖空 roles。
  6. **守卫对该不变式的保护本身是完备的**：即使把内置规则 3 的 httpMethod 改为 GET（POST 落到默认 `* /**` 规则 11，AUTHENTICATED → 非 ADMIN）也会被守卫拦——问题在于**不变式只有一个维度**，与 ADR 0005「任何规则改动不得导致 ADMIN 失去对权限配置模块的访问权」的承诺（覆盖全部写端点）不一致。
- **测试盲区（交叉 006 测试充分性评审）**：`PermissionRuleIntegrationTest` 只复制了 POST 语义（内置不可删、两类自我保护 400 均走 POST 模拟），PUT/DELETE 自锁与 VIEWER 提权链**无用例** → 测试全绿属覆盖缺口而非实现安全。
- **建议修法**：① 守卫从「单 POST 模拟」改为**不变式列表**：逐一断言 ADMIN 对权限模块全部写端点（POST/PUT/DELETE `/api/permission-rules` 与 `/{id}`）可达，且**任何改动的 roles 变更不得让非 ADMIN 角色获得 `/api/routes/**` 与 `/api/permission-rules/**` 的写面**（把"路由写面最小角色=ADMIN"作为硬不变式）；② `update` 禁止修改内置规则的 roles/priority/enabled（与「内置不可删」语义一致）；③ `RuleRequest.priority` 加界（如 0-999）；④ 补 PUT/DELETE/`*` 方法/优先级翻转/VIEWER 提权链的集成测试。

#### S-14 [P3] 路由校验错误透传内部根异常消息

- **位置**：`route/RouteValidator.java:112-117,120-127`（`rootMessage(e)` 提取最深层异常消息拼进 400 文案）；入口 `POST /api/routes/validate` 对 AUTHENTICATED 开放（V2 规则 4，`V2__permission_rule.sql:20`）
- **为什么**：校验失败文案可能带框架内部类名/属性路径（如绑定失败的配置键），对登录用户回显；无堆栈，信息量低，属轻微泄漏。002 实测 `未知的工厂名: NoSuchFactory` 属预期业务文案，未发现堆栈。
- **建议修法**：对外统一"参数不合法"，内部 detail 只进日志；或仅对 ADMIN 回显细节。

#### S-15 [P3] CORS 双重配置 + 硬编码 localhost:5173

- **位置**：`config/SecurityConfig.java:39-40`（`.cors(cors -> {})` 空配置与 `:77-87` 独立 `CorsWebFilter` bean 并存，实际生效的是后者）、`:80`（allowedOrigins 硬编码 `http://localhost:5173`/`127.0.0.1:5173`）
- **为什么**：双配置是维护陷阱（改一处忘另一处即行为漂移）；生产部署走 nginx 同源代理（`frontend/nginx.conf:7-11`）本不需要 CORS，硬编码的 dev 来源在交付物里成为"默认开着的跨域许可"。`allowCredentials=true` + `allowedHeaders=*` 组合合规（Spring 会回显实际头），未构成漏洞，属配置卫生。
- **建议修法**：删除 `.cors(cors -> {})` 空块；allowedOrigins 改为配置项注入，生产环境置空（同源）。

#### S-16 [P3] gateway-demo 刷新日志同样信任 X-Forwarded-For

- **位置**：`gateway-demo/.../RouteRefreshController.java:83-91`（`clientIp` 与 backend `SecurityUtils` 同款实现）
- **为什么**：`:51` 的"来源 IP"日志可被伪造（与 S-06 同根因），影响仅日志归因，无审计落库，故 P3。
- **建议修法**：与 S-06 统一处理（受信代理取 `X-Real-IP`）。

#### S-17 [P3] 依赖生命周期：Boot 3.5.16 / Spring Cloud 2025.0.x 已过/临近 OSS 支持期，安全补丁停更

- **位置**：`backend/pom.xml:10,22`（Boot 3.5.16、Spring Cloud 2025.0.3）；`gateway-demo/pom.xml:7,23,30`（Boot 3.5.16、Spring Cloud 2025.0.0 + Alibaba 2025.0.0.0）；ADR 0004 自述 Boot 3.5 OSS 支持已于 2026-06 结束
- **为什么**：安全评审口径下，无安全补丁的框架版本意味着未来 CVE 只能靠升级修复，而双工程版本线不一致（2025.0.3 vs 2025.0.0，starter artifact 亦不同）抬高升级成本；属于供应链/合规风险而非现役漏洞。
- **建议修法**：规划升级到仍在支持期的 Boot 3.5.x 末版+受支持 Spring Cloud 线（或直接 Boot 4.x/Cloud 2026.x 评估），统一两工程版本线。

#### S-18 [P3] 无效/过期 token 静默吞异常，无观测

- **位置**：`auth/JwtAuthenticationFilter.java:34-36`（`catch (JwtException | IllegalArgumentException e) {}` 空吞）
- **为什么**：行为正确（fail-safe → 401，002 实测统一 JSON），但被吞的异常（过期/篡改/密钥不符）无任何日志，攻击探测（伪造 token 刷接口）与正常过期无法区分，缺少安全观测面。
- **建议修法**：debug 级日志记录异常类型（勿记 token 明文），或对连续无效 token 计数告警。

---

## 已核实为有效的安全控制（正面，防止重复评审）

1. **无 SQL 注入面**：`RouteConfigRepository.java:20-22` JPQL 参数化、gateway-demo `JdbcTemplate` 占位符，全仓无字符串拼接 SQL。
2. **口令存储**：BCrypt（`SecurityConfig.java:72-75`，默认 cost 10）哈希落库，无明文/可逆存储。
3. **fail-closed 默认拒绝**：`DynamicPermissionAuthorizationManager.java:36-38` 无规则匹配即拒绝；`permission_rule` 内存缓存按优先级首条命中（`PermissionRuleService.java:111-119`），优先级语义正确（002 实测新增规则即时生效、404 而非 403 证明匹配路径正确）。
4. **认证信息不泄漏**：登录失败统一"用户名或密码错误"（`AuthService.java:23-29`，未知用户与错密码同文案，无用户名枚举）；401/403 统一 JSON（`SecurityConfig.java:44-48`）不再触发浏览器 Basic 弹框，且全局异常处理器不泄漏堆栈（`GlobalExceptionHandler.java:69-74`，002 实测 400/401/403/405/409 全为 `{code,message,data}`）。
5. **actuator 最小暴露**：仅 `health`（`application.yml:18-22`），且 health 是 permitAll 的轻量检查（`SecurityConfig.java:51`）。
6. **JWT 实现健壮性**：jjwt 0.12.6 标准 API，`Keys.hmacShaKeyFor` 强制 ≥256-bit 密钥（弱密钥启动即抛 WeakKeyException）；`verifyWith` 验签 + 自动验 exp；无 `alg:none` 降级风险。
7. **前端无 XSS 汇点**：全仓 grep 无 `v-html`/`innerHTML`；审计/路由 JSON 均以文本插值渲染（Vue 默认转义）。
8. **写面权限收敛**：内置规则下所有写接口（路由增删改、权限规则 CRUD）仅 ADMIN；VIEWER 仅 GET + 自改密码 + validate（`V2__permission_rule.sql:16-27`；002 实测 viewer 写路由/看规则 403）。改密/自服务端点 `PUT /api/auth/password` 走 AUTHENTICATED（`V2:22`）且以当前登录用户为准（`AuthController.java:37-42`），无横向改密面。
9. **CSRF 关闭可接受**：Bearer 头 + CORS 白名单（S-15）组合下，跨站无法携带自定义头，CSRF 风险已被结构化解。
10. **权限规则写操作与内存缓存一致性**：CRUD 在同一事务内 `reload()`（`PermissionRuleService.java:54-95,100-106`），002 实测即时生效；内置规则 DELETE 被拒（`:86-88`，实测 400）。

---

## 与并行评审交叉引用

| 本评审 | 003 架构 | 004 代码质量 | 一致结论 |
|---|---|---|---|
| S-05 | F15（P2） | — | `RouteRefreshController.java:67-74` 空 token 放行：误配即公开 |
| S-06 | — | P3-18 | XFF 信任：004 定 P3（代码质量视角），本评审按"审计为 ADR 0003 补偿控制"定 P2 |
| S-13 | — | P2-02 / 006 | guard 只模拟 POST：004 定 P2（自锁可用性视角）、006 确认测试盲区；本评审按攻击面展开（VIEWER 提权到路由写）定 **P2** |
| S-01/S-02 | F??（种子账号 P3 提及） | P1-03/P1-04 | 默认密钥/口令：一致认为部署即高危 |
| S-07 | — | — | 审计可读性：本评审新增 |

## 建议修复优先级

S-01/S-02/S-03（默认值治理，一个 PR 可覆盖：启动强校验 + 口令/密钥外置 + compose 加固）→ S-05（一行语义改动：空 token 改默认拒绝 + 恒定时间比较）→ S-13（守卫改不变式列表，改动面小但堵住提权链）→ S-04（token_version 吊销）→ S-06（受信代理取 IP）→ 其余 P3 按成本排序。
