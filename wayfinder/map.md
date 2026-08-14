# 全面评审报告（六维 + 动手验证）

Labels: wayfinder:map

## Destination

交付一份 `docs/评审报告.md`：对 spring-cloud-gateway-dashboard（Spring Cloud Gateway 管理后台）做**六维评审**（架构与设计、代码质量与可维护性、安全、测试充分性、交付物与部署、规范一致性），配**动手验证**（后端/前端测试套件跑通、H2 与 MySQL 起服抽查 API、Docker 静态审阅），发现按 **P0-P3 分级**并附「为什么 / 建议修法 / 位置」。

地图闭合 = 六张评审 ticket 全部关闭，且 `docs/评审报告.md` 合成交付。

## Notes

- **领域**：Spring Cloud Gateway 管理后台评审；报告与全部产出用中文。
- **评审基准**：仓库自证（`docs/adr/0001-0005`、`README.md`、`CONTEXT.md`、`docs/使用手册.md`）+ 通用最佳实践。执行评审的子代理应加载 `java-coding-standards`、`code-review`、`code-review-and-quality` 技能。
- **事实基线**：`wayfinder/project-inventory.md` 是本 effort 的只读扫描清单（结构/测试/疑点），工作会话以它为基线，不必重复全量扫描。
- **分级**：P0 阻断上线必须修 / P1 高优先尽快修 / P2 中优先计划修 / P3 低优先可搁置。每条发现附「为什么」与建议修法。
- **报告形态**：地图驱动——各 ticket 的决议即素材，最后合成 `docs/评审报告.md`（摘要、方法、六维发现、P0-P3 分级问题清单、结论建议）。
- **动手验证环境事实**：本机 Java 21 / Maven 3.9 / Node 24 齐备；MySQL 8.4 在 127.0.0.1:3306 运行且 `gateway_dashboard` 库与 `gateway` 账号已建好；**无 Docker**（交付物维度只能静态审阅，标注「未实机构建验证」）。
- **本 effort 携带执行**（非纯决策地图）：目的地即报告交付物，task ticket（验证/合成）属地图之内。
- **追踪器**：本地 Markdown（本 `wayfinder/` 目录），约定见 `wayfinder/README.md`：Labels 行模拟标签；`Status: open/claimed/closed` + `Claimed by:` 表示认领；`Blocked by:`/`Blocks:` 为正文阻塞约定（Markdown 无原生依赖）；**frontier** = open 且未认领且无未关闭阻塞者的 ticket。
- **引用惯例**：所有讨论与记录按 ticket **名称**指代，不裸用编号。

## Decisions so far

<!-- 每关闭一张 ticket 追加一行：- [ticket 名称](tickets/NNN-*.md) — 一句话结论。只记录真正走通的路线 -->

- [代码质量与可维护性评审](tickets/004-code-quality-review.md) — 质量中上（B+）无 P0；阻塞 JPA 压在事件循环、审计序列化静默 null、默认凭据/密钥、校验规则三层重复等 P1×4/P2×8/P3×20（分支 research/code-quality-review@c884460）
- [架构与设计评审](tickets/003-architecture-review.md) — 骨架良好忠于 ADR、无 P0/P1；内部 token 空即放行、事件循环阻塞、markRefreshed 竞态、校验和碰撞、create 500 非 409、状态页"生效路由"实为 DB 直读等 P2×10/P3×18（分支 research/architecture-review@211c9fc）
- [交付物与部署评审](tickets/007-deliverables-deploy-review.md) — 无 P0 但六维中最不成熟：Dockerfile 缺 .dockerignore（macOS 宿主构建大概率失败，未实机构建验证）、无 prod profile/明文凭据、compose 无 gateway-demo；ADR 0004 Boot 3.5 OSS 2026-06 EOL 属实（分支 research/deliverables-deploy-review@7648d66）
- [验证测试套件跑通](tickets/001-verify-test-suites.md) — 两套套件全绿：后端 12/12（8.8s，无 flaky，19999 WARN 为 test profile 预期）、前端 4/4（仅 utils）；与盘点基线一致，缺口事实（无推送/409/改密测试、gateway-demo 零测试）移交测试充分性评审
- [起服抽查 API 行为](tickets/002-probe-api-at-runtime.md) — H2/MySQL 双 profile 六类路径实测一致：保存即生效热刷新确认、并发乐观锁 409 正常、权限规则即时生效、审计动作齐全、外部网关离线展示正常、统一 JSON 无堆栈；改密后旧 token 仍有效（无吊销机制）、404 消息含 Spring 默认措辞（均 P3 级）
- [测试充分性评审](tickets/006-test-adequacy-review.md) — "冒烟+关键成功路径"级非充分级：架构确认的 7 类缺陷全溜过测试网；P1×6（外部网关链路/已确认缺陷回归/权限边界含 guard 只防 POST/改密/审计仅 CREATE/gateway-demo 零测试）+P2×5+P3×4（分支 research/test-adequacy-review@50b0319）
- [规范一致性评审](tickets/008-spec-consistency-review.md) — 高度一致无 P0/P1、未发现代码错；10 条差异全为文档过时/措辞宽于实现：P2×3（手册 SUM(version) 过时且自相矛盾、单实例声明 vs 已实现外部网关多实例、自我保护承诺宽于 guard 只防 POST）+P3×7（分支 research/spec-consistency-review@1c45c4c）
- [安全评审](tickets/005-security-review.md) — 无 P0，默认配置 3 个 P1 接管路径（JWT 默认密钥可伪造 ADMIN、默认口令公开、compose 暴露 MySQL 3306 可绕过鉴权）+P2×4（无 token 吊销/角色固化、内部接口空 token 放行、审计 IP 信任 XFF、guard 提权洞 S-13 上调 P2）+P3×11；运行时控制（fail-closed/统一 JSON/无 SQL 注入）核实有效（分支 research/security-review@94983947）
- [合成评审报告](tickets/009-synthesize-review-report.md) — ✅ **地图闭合**：`docs/评审报告.md` 交付（健康度 B-、P0 无、P1 产品缺陷 4 组 + 测试缺口 6 条、P2 去重 27 条、修复路线 6 步）；六维全部关闭，目的地达成

## Not yet specified

- 评审已完成（无 P0、P1 集中在默认值治理）。是否开启后续修复 effort——本 effort 明确排除修复；若用户决定修复，属新 effort（可能重绘目的地），届时以 `docs/评审报告.md` 的 §5.2 分级清单为 backlog 源头，再行规划。

## Out of scope

<!-- 用户明确排除；永不毕业。返回仅当目的地被重绘 -->

- **性能/负载压测**（需压测环境与基准目标，已排除）
- **完整渗透测试**（已排除，仅静态安全审阅 + 逻辑检查）
- **UI/UX 视觉评审**（已排除，只看功能正确性与代码质量）
- **修复实现**（已排除，本 effort 只评审不改码）
- **业务需求正确性核对**（已排除，只审代码与其自证文档的一致性）
