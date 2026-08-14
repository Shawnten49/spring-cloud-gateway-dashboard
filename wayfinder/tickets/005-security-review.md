# 安全评审

Labels: wayfinder:research
Status: closed
Claimed by: wayfinder research 子代理（844a6628-0100-4c95-9e49-fa6a5d171524）

## Question

评审安全（基于「起服抽查 API 行为」的运行时事实 + 静态阅读）：

1. **JWT**：默认 secret（application.yml 硬编码，生产可被 `JWT_SECRET` 覆盖）、过期 12h、算法与密钥强度、改密后旧 token 是否仍有效（对照 002 事实）。
2. **认证**：种子账号 `admin123` / `viewer123` 硬编码（SeedDataInitializer）、登录失败响应是否泄漏信息、401/403 的统一 JSON（不再触发浏览器 Basic 弹框的修复是否到位）。
3. **授权**：权限规则优先级匹配正确性、自我保护（内置规则不可删/锁死防护）、动态 AuthorizationManager 的匹配与缓存一致性。
4. **外部网关信任边界**：`gateway-demo` 内部 token 为空即免鉴权、token 比对方式（== vs 常数时间）、`/internal/*` 接口暴露面。
5. **传输与存储**：明文 DB 密码（dev profile）、CORS 配置、前端 JWT 存 localStorage 的 XSS 暴露面、审计日志是否可能被绕过。
6. **其它**：敏感信息泄漏（错误响应、日志、统一响应体）、越权路径（VIEWER 调 ADMIN 接口、未认证访问）。

产出：安全判断 + 发现列表（每条标 P0-P3、位置、为什么、建议修法）。已知疑点供核查：JWT 默认 secret、种子账号硬编码、gateway-demo 免鉴权、明文 DB 密码、localStorage JWT、改密后旧 token 仍有效。

## Blocking

Blocked by: 起服抽查 API 行为
Blocks: 合成评审报告

## Resolution

**结论：无 P0；默认配置下存在 3 个 P1 接管路径（全仓最高风险集中在"默认值治理"）。** 认证授权运行时行为与设计一致（002 实测对照全部吻合），fail-closed、统一 JSON 无堆栈、无 SQL 注入、无 v-html XSS 汇点均核实为有效控制。

**P1（3，均为默认配置可接管）**：
- S-01 JWT 密钥默认值公开可伪造任意 ADMIN token：application.yml:27 + docker-compose.yml:31 + JwtService.java:23。建议生产 profile 启动强校验 JWT_SECRET 必填且非默认值
- S-02 默认口令 admin123/viewer123 硬编码 + 登录页明文展示：SeedDataInitializer.java:34-41、LoginView.vue:45-47。建议 env 注入/随机生成 + 首登强制改密 + 删除页面展示
- S-03 compose 暴露 MySQL 3306 + 公开口令（gateway/gateway123、root123）：docker-compose.yml:8-13、application-dev.yml:5——直连 DB 完全绕过应用鉴权，且改 route_config 5 秒内被网关轮询生效。建议不发布 3306/强随机口令/secrets

**P2（4）**：
- S-04 无 token 吊销 + 角色固化 12h（改密后旧 token 仍有效，002 实测；定性 P2：需先有合法 token 才成害，但击穿改密/停用/降权的补救路径）。建议 token_version/jti 吊销
- S-05 外部网关内部接口：空 token 放行 + 默认 token 公开 + 非恒定时间比较（gateway-demo RouteRefreshController.java:67-74、RouteSyncProperties.java:11，交叉架构 F15）。建议默认拒绝 + MessageDigest.isEqual + 强随机 token
- S-06 审计 IP 信任 X-Forwarded-For 可伪造（SecurityUtils.java:17-25、nginx.conf:10；交叉代码质量 P3-18，安全视角上调 P2——审计是 ADR 0003 唯一补偿控制）。建议受信代理 X-Real-IP
- S-13（协调者交叉转发后**从 P3 上调 P2**）：guard 只模拟 POST + update 无 builtin 保护。攻击面：ADMIN 建 `PUT /api/permission-rules/** → VIEWER`（priority 无界，0/负数压过内置）→ 公开凭据的 VIEWER 获 PUT → update 任意规则**含内置**（PermissionRuleService:70-81）→ 内置路由写规则改 VIEWER/`*` → VIEWER 获路由增删改（网关流量劫持/DoS）。守卫承诺（ADR 0005）与实现不一致、006 确认测试盲区。修：守卫改不变式列表（权限模块全部写端点可达 + 路由写面最小角色=ADMIN）+ update 禁改内置规则 roles/priority/enabled + priority 加界（0-999）+ 补提权链集成测试

**P3（11）**：VIEWER 可读审计（V2:18）；登录无速率限制；密码仅 6 位；JWT 无 iss/aud；404「No static resource」泄漏；localStorage JWT（无现成 XSS 汇点，纵深防御）；校验错误透传根异常；CORS 双配置 + 硬编码 5173；gateway-demo XFF 日志伪造；Boot 3.5 EOL 供应链；无效 token 静默吞异常无观测。

**建议修复优先级**：S-01/S-02/S-03（默认值治理，一个 PR）→ S-05（一行语义）→ S-04 → S-06 → P3 按成本。

**完整发现**：`wayfinder/findings/security-review.md`（每条 文件:行号/为什么/建议修法；含 S-13 上调后的完整攻击面），一次性分支 `research/security-review`，commit `94983947`（父 7278f0c，未并入 main）。
