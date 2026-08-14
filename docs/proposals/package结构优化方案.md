# 包结构优化方案：按职责分层重构 Java 包

> **状态：✅ 已实施并合入 main**（commit "refactor: 包结构按职责分层重构"）。
> 依据 `/java-architect` 架构审查：`com.gatewaydashboard.route` 等域包类数过多且职责混杂
> （controller / service / mapper / entity / dto / assembler / validator / locator / event 混放同一包）。
> 目标：域边界不变（域间依赖已是 DAG），**域内按职责分层**，包名即职责、可读性与可维护性提升。
> 基准：main `dfab124`。

## 0. 已确认决策

| 决策点 | 结论 |
|---|---|
| D1 组织风格 | **域内分层**（`route.controller` / `route.service` / `route.mapper`…，保留域边界） |
| D2 小域处理 | **统一分层**（refresh/gateway 等小域同样分层） |
| D3 支撑类归属 | **域根保留**（Assembler / Locator / Event / Filter / Listener / Scheduler / 枚举等留域根） |
| D4 测试 | **同步移动**（测试镜像 main 结构） |

**类归属规则**：`*Controller`→`controller`；`*Service`/`*Validator`→`service`；`*Mapper`→`mapper`；实体→`entity`；`*Dtos`→`dto`；其余（Filter/Listener/Scheduler/Cache/Locator/Assembler/Event/枚举/授权管理器）→域根。

---

## 1. 现状盘点（main 源码）

| 包 | 类数 | 职责混放 |
|---|---|---|
| route | **13** | Controller×2、Service×2、Mapper×2、Entity×2、Dto、Assembler、Validator、Locator、Event 九种角色平铺 |
| auth | 8 | Controller / Service / JwtService / Filter / Cache / Entity / Mapper / Dtos 平铺 |
| permission | 6 | Controller / Service / 授权管理器 / Entity / Mapper / Dtos 平铺 |
| audit | 6 | Controller / Service / 枚举 / Entity / Mapper / Dtos 平铺 |
| gateway | 5 | Controller / Service×2 / Listener / Dtos 平铺 |
| refresh | 3 | Scheduler / RefreshService / Listener 平铺 |
| common | 8 | 通用设施（ApiResponse / 异常 / 工具 / 错误写出）——**保持平铺合理** |
| config | 6 | Spring 配置 + 属性绑定 + 初始化 + 限流——**保持平铺合理** |

**问题**：域包内 `Controller/Service/Mapper` 等角色混放，无法一眼区分分层；新成员需自行判断归属。

## 2. 目标结构（按职责分层，域边界不变）

```
com.gatewaydashboard
├── common/                  # 不变（跨域通用设施）
├── config/                  # 不变（Spring 配置、属性绑定、初始化、限流）
├── auth/
│   ├── AuthController.java          （域根放控制器？见 D1 决策）
│   ├── AuthService.java / JwtService.java / JwtAuthenticationFilter.java / UserAuthStateCache.java
│   ├── dto/AuthDtos.java
│   ├── entity/User.java
│   └── mapper/UserMapper.java
├── route/
│   ├── controller/RouteController.java, RouteMetaController.java
│   ├── service/RouteService.java, RouteRefreshService.java, RouteValidator.java
│   ├── mapper/RouteConfigMapper.java, ConfigRevisionMapper.java
│   ├── entity/RouteConfig.java, ConfigRevision.java
│   ├── dto/RouteDto.java
│   └── （支撑类归属见 D3：RouteAssembler / DbRouteDefinitionLocator / RouteChangedEvent）
├── audit/
│   ├── AuditController.java / AuditService.java / AuditAction.java
│   ├── dto/AuditDtos.java
│   ├── entity/AuditLog.java
│   └── mapper/AuditLogMapper.java
├── permission/
│   ├── PermissionRuleController.java / PermissionRuleService.java / DynamicPermissionAuthorizationManager.java
│   ├── dto/PermissionRuleDtos.java
│   ├── entity/PermissionRule.java
│   └── mapper/PermissionRuleMapper.java
├── gateway/
│   ├── GatewayStatusController.java / GatewayStatusService.java / ExternalGatewayStatusService.java / RefreshTimestampListener.java
│   └── dto/GatewayStatusDtos.java
└── refresh/
    ├── EmbeddedGatewaySyncScheduler.java / ExternalGatewayRefreshService.java / RouteChangeSyncListener.java
```

**分层规则**（每个域包内）：
| 子包 | 职责 |
|---|---|
| `controller` | Web 层：`*Controller`（响应式入口，薄层） |
| `service` | 业务逻辑：`*Service`、`*Validator` 等业务服务 |
| `mapper` | 数据访问：`*Mapper` 接口（SQL 在 resources/mapper/*.xml） |
| `entity` | 持久化实体：`@TableName` 标注的 POJO |
| `dto` | 请求/响应 record（`*Dtos` 容器或独立 record） |

## 3. 迁移影响面（关键）

| 影响点 | 说明 |
|---|---|
| **mapper XML namespace** | `mapper/*.xml` 的 `<mapper namespace="com.gatewaydashboard.route.RouteConfigMapper">` 需同步改为新路径（`...route.mapper.RouteConfigMapper`）——**漏改 = 运行期 BindingException** |
| import 修正 | 拆分后同域内跨包引用需补 import（如 RouteService → RouteConfig/RouteDto/RouteAssembler/ConfigRevisionMapper）；以 javac 编译错误驱动逐个修正 |
| 测试镜像 | `src/test/java/com/gatewaydashboard/route/*` 等测试类同步移动 + 更新 import |
| 组件扫描 | 默认扫描 `com.gatewaydashboard` 全部子包，无需改（无 @MapperScan/@ComponentScan 限定） |
| 行为 | 纯包重构，无逻辑改动；以现有 38 测 + gateway-demo 9 测全绿为回归保障 |

## 4. 实施步骤（方案通过后）

1. `git mv` 移动 main 源码文件至新子包（保留历史）+ 修改 `package` 声明
2. 同步修改 `resources/mapper/*.xml` 的 namespace
3. javac 编译 → 按报错补 import（可脚本化：同域类自动补 `import com.gatewaydashboard.<域>.<子包>.<类>`）
4. `git mv` 测试镜像 + 更新 import
5. `mvn -pl backend verify` 全绿（38 测 + JaCoCo 门槛）；`mvn -pl gateway-demo test`（9 测）
6. 更新 README 目录结构 / CONTEXT（如涉及）/ 提交

## 5. 风险与对策

| # | 风险 | 对策 |
|---|---|---|
| R1 | XML namespace 漏改 → 运行期 BindingException | 实施后全量测试覆盖（所有 Mapper 均被测试触达）；grep 校验 namespace 与类路径一致 |
| R2 | 移动文件后 import 遗漏/错误 | 编译错误驱动 + 全量测试；`grep -r "com.gatewaydashboard.route\."` 校验无残留旧包引用 |
| R3 | 小域分层后出现"每层 1 个类"的碎包 | D2 决策：小域（≤5 类）是否强制分层 |
| R4 | 包移动影响审计/监控可读性 | 纯包名变化，类名不变，日志/监控不受影响 |

## 6. 验收标准

- `mvn -pl backend verify` 全绿（38 测 + JaCoCo 门槛）；`mvn -pl gateway-demo test` 全绿（9 测）
- `grep -r "com.gatewaydashboard\.route\.\(RouteConfig\|RouteService\|RouteConfigMapper\|...\)"` 无残留旧路径（XML namespace 与源码一致）
- 每个域包下仅出现 `controller/service/mapper/entity/dto` 及少量域专属支撑类
- Java 文件全部经 `git mv` 移动（历史可追溯）

---

## 决策点（需确认）

- **D1 组织风格**：**域内分层**（`route.controller`、`route.service`、`route.mapper`…，推荐：保留现有域边界，贴合 DDD/域驱动，与项目已做的事件解耦一致）；还是**经典顶层分层**（顶层 `controller`/`service`/`mapper`/`entity`/`dto` 包，域被打散——教科书风格，但破坏现有域聚合）？
- **D2 小域处理**：**统一分层**（refresh/gateway 等 ≤5 类小域也分层，风格一致）；还是小域保持平铺（仅对 route/auth/permission/audit 等 ≥6 类的域分层，避免"每层 1 类"碎包）？
- **D3 支撑类归属**：`RouteAssembler`（转换器）、`DbRouteDefinitionLocator`（网关加载器）、`RouteChangedEvent`（领域事件）放在——**域根包保留**（推荐：它们是域级支撑，不属于 controller/service/mapper/entity/dto 任何一层）；还是并入 service / 独立 `support` 包？
- **D4 测试是否同步移动**：**同步移动**（测试镜像 main 结构，推荐）；还是测试保持原包（改动最小，但结构与 main 不一致）？
