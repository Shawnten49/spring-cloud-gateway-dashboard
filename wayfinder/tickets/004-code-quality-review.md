# 代码质量与可维护性评审

Labels: wayfinder:research
Status: closed
Claimed by: wayfinder research 子代理（55f0d8d9-e562-4689-94a5-6c6739a1205f）

## Question

按 `java-coding-standards` 技能与通用最佳实践评审代码质量（后端 + 前端，静态阅读）：

1. **后端**：命名、不可变性、Optional 用法、流与集合、异常处理（BusinessException / GlobalExceptionHandler 的一致性、异常是否被吞）、响应式/WebFlux 模式是否恰当、DTO 与实体分离、重复与过度设计、可读性与注释。
2. **前端**：Vue3 组合式风格、store（Pinia）设计、类型安全（TS 严格度）、API 层封装、错误处理与 loading 态、组件职责。
3. **整体可维护性**：魔法值、硬编码、死代码、结构一致性（可对照 `CONTEXT.md` 领域词汇判断命名是否贴合领域语言）。

产出：质量判断 + 发现列表（每条标 P0-P3、文件位置、为什么、建议修法）。已知疑点供核查：审计序列化失败静默返回 null。

## Blocking

Blocked by: 无
Blocks: 合成评审报告

## Resolution

**结论：质量中上（B+），无 P0。** 分层清晰、命名贴合 CONTEXT.md 领域词汇、record DTO 与实体分离良好、Optional/乐观锁/统一异常出口规范、前端 strict TS + 薄 API 层。问题集中在四类：WebFlux 事件循环上跑阻塞 JPA（架构性）、多处静默吞异常/静默丢数据、默认凭据与密钥、校验规则三层重复。

**P1（4）**：① 阻塞式 JPA 全压在 Netty 事件循环线程（RouteController/AuditController/PermissionRuleController/AuthController/DbRouteDefinitionLocator/GatewayStatusService 等 `Mono.just(阻塞调用)`，应 `subscribeOn(boundedElastic)`）；② 审计序列化失败静默返回 null（RouteAssembler:98-104，疑点确认）；③ 默认口令 admin123/viewer123 硬编码且 LoginView 公开展示；④ 默认 JWT 密钥与内部 token 硬编码（application.yml:27,32-33），默认 profile 即 dev。

**P2（8）**：setEnabled 裸 Map 静默停用路由；自锁守卫只模拟 POST（PUT/DELETE 未保护，ADR 0005 承诺不完整）；fire-and-forget subscribe 无 Disposable 管理；健康检查吞异常；校验规则三层重复（routeId 正则/httpMethod 白名单）；CORS 硬编码 localhost:5173；DB 口令明文入库；审计截断产生非法 JSON 且 5000 常量两处重复。

**P3（20）**：动作码字符串化/前端 actionType 漏 ENABLE、乐观锁双重处理、校验 handler 分支重复、三种响应式写法并存、toResponse 硬编码值、toLowerCase 缺 Locale.ROOT、AUTHENTICATED 死分支、守卫重复排序、魔法字符串、ApiResponse.data 类型谎言、前端死代码、VIEWER 可直达 /permissions、视图缺错误兜底、zip 强转未检查、风格不一致、手写 declare process、英文报错直出、clientIp 信任 X-Forwarded-For、search 前导通配符、page 回显矛盾。

**亮点**（供合成平衡）：包结构/命名贴合领域、DTO-实体分离 + Assembler、@Version + open-in-view:false + Flyway、Optional 规范、统一异常出口、权限内存缓存 AtomicReference + 预编译 PathPattern、前端 strict TS、有少量测试。

**完整发现**：`wayfinder/findings/code-quality-review.md`（32 条，逐条 文件:行号），一次性分支 `research/code-quality-review`，commit `c884460`（未并入 main）。
