# 文档中心

Gateway Dashboard 项目文档目录导航。

## 分类结构

```
docs/
├── README.md            # 本文档（索引）
├── 使用手册.md           # 用户使用手册（动态路由原理、页面操作、API 脚本化）
├── adr/                 # 架构决策记录（ADR 0001–0005）
├── review/              # 评审与优化报告（历次审查/优化产出）
├── proposals/           # 方案文档（实施前的设计与决策记录，多数已实施）
└── modules/             # 业务模块的需求/设计文档
```

## 评审与优化报告（review/）

| 文档 | 内容 |
|---|---|
| [评审报告.md](review/评审报告.md) | wayfinder 六维评审（架构/代码质量/安全/测试/交付物/规范），P0-P3 分级问题清单 |
| [优化报告.md](review/优化报告.md) | 第一批优化（评审 P1/P2 修复）落地记录与剩余 backlog |
| [质量优化报告.md](review/质量优化报告.md) | 五轴审查（正确性/可读性/架构/安全/性能）修复记录 |
| [编码规范优化报告.md](review/编码规范优化报告.md) | java-coding-standards 规范审查与 S1–S7 优化记录 |

## 方案文档（proposals/）

| 文档 | 内容 | 状态 |
|---|---|---|
| [优化方案.md](proposals/优化方案.md) | 第二批优化方案（Phase 1 较大改动 + Phase 2 低优先级） | ✅ 已实施 |
| [maven多模块方案.md](proposals/maven多模块方案.md) | 根 Maven 主项目聚合 backend/frontend/gateway-demo | ✅ 已实施 |
| [mybatis-plus迁移方案.md](proposals/mybatis-plus迁移方案.md) | 数据访问层 JPA → MyBatis-Plus + XML | ✅ 已实施 |
| [package结构优化方案.md](proposals/package结构优化方案.md) | Java 包按职责分层重构 | ✅ 已实施 |

## 业务模块文档（modules/）

| 文档 | 内容 | 状态 |
|---|---|---|
| [用户管理模块-需求文档.md](modules/用户管理模块-需求文档.md) | 用户管理需求（增用户/屏蔽/禁用删除/admin 保护） | ✅ 已实施 |
| [用户管理模块-设计文档.md](modules/用户管理模块-设计文档.md) | 用户管理设计（API 契约/SQL/前端/测试） | ✅ 已实施 |

## 其他

- 领域词汇表：[CONTEXT.md](../CONTEXT.md)
- 架构决策：[adr/](adr/)
- 使用手册：[使用手册.md](使用手册.md)
