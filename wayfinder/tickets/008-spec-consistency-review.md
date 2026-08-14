# 规范一致性评审

Labels: wayfinder:research
Status: closed
Claimed by: wayfinder research 子代理（e86e9e08-59d5-4e78-9e0d-d43d4322a22b）

## Question

核对**代码实现与仓库自证文档的一致性**（基于「起服抽查 API 行为」的运行时事实 + 静态阅读）：

逐条核对 `docs/adr/0001-0005`、`README.md`、`CONTEXT.md`、`docs/使用手册.md` 中的关键声明是否属实：

1. 「保存即生效、无需重启」（热刷新是否真的即时；README 声称的路由校验强校验）。
2. 「停用 vs 删除」语义（停用保留配置可恢复）。
3. 权限规则「方法 + 路径 + 角色 + 优先级，首个命中生效，修改后即时生效」（对照 002 事实）。
4. 「数据库为唯一真源」与外部网关「推送失败不影响保存、5 秒轮询兜底」。
5. 「网关状态页展示外部网关实例：在线状态、最近推送、生效路由」。
6. 「操作审计：每次 创建/修改/删除/停用/启用 记录操作者、时间、变更前后、IP」。
7. 领域词汇（CONTEXT.md）与代码/接口命名、README API 摘要与实际路由是否一致。

产出：一致性结论 + 差异清单（每条标 P0-P3、声明出处 vs 实际行为、是代码错还是文档过时）。

## Blocking

Blocked by: 起服抽查 API 行为
Blocks: 合成评审报告

## Resolution

**结论：整体高度一致，无 P0/P1，未发现代码错。** 四份自证文档（ADR 0001-0005、README、CONTEXT、使用手册）与代码实现、002 运行时事实三方基本逐条吻合：保存即生效热刷新（afterCommit→RefreshRoutesEvent，双 profile 实测）、停用 vs 删除语义、权限规则"方法+路径+角色+优先级首个命中+即时生效"（viewer 即时获得/失去访问权实测）、内置规则不可删、审计 5 类动作全落库含 IP、README API 摘要表 17 端点逐条一致、使用手册示例 JSON 相符、ADR 决策全部落地。**10 条差异全部为文档过时/措辞宽于实现/文档缺口。**

**P2（3）**：
- P2-1 使用手册:383「RouteSyncScheduler 每 5 秒比较（行数, 最大版本号）」vs 代码 `SUM(version)`（RouteSyncScheduler.java:66-72）→ 文档过时，且与同手册:402「行数+版本号总和」自相矛盾
- P2-2 「单实例/多实例刷新传播留待后续」（CONTEXT:52、README:166、ADR 0002:7、使用手册:445）vs 已实现的外部网关多实例管理（推送列表 + 5s 轮询兜底）→ 边界章节未随 commit 5e15851/85e2a28 同步，表述矛盾
- P2-3 自我保护承诺（ADR 0005:5、使用手册:437-438「不得导致 ADMIN 失去权限配置模块访问权」）vs 实现仅守卫 POST /api/permission-rules/__guard__ 单一路径（PermissionRuleService:33,149-162）——PUT/GET/DELETE 可改为非 ADMIN 而保存成功，兜底规则使 ADMIN 名义不失权但把写接口开放给所有登录用户（权限提升路径，文档未提示；与测试充分性评审 P1-3 交叉印证）

**P3（7）**：404 未知路径返回 Spring 默认「No static resource」非统一响应体（README:134 声明过宽，002 实测）；改密后旧 token 仍有效但文档未声明；使用手册:384「需带 X-Internal-Token」vs 空 token 跳过校验；CONTEXT:33-34 审计词条未含停用/启用与 IP（README:12 更全）；「VIEWER 只读」与可改自己密码并存（措辞边界）；状态页「生效路由」实为 CompositeRouteDefinitionLocator 读 DB，与 CONTEXT「网关中实际生效」瞬时态有偏差（架构评审 F25，此处降级备注）；RouteRequest 无长度上限 vs DB 列 5000 等顺带观察（非文档矛盾）。

**完整发现**：`wayfinder/findings/spec-consistency-review.md`（逐项核对表 + 差异清单，含出处行号 vs 实现位置 vs 判定），一次性分支 `research/spec-consistency-review`，commit `1c45c4c`（未并入 main）。
