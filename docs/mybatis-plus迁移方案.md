# MyBatis-Plus 迁移方案：数据访问层 JPA → MyBatis-Plus + XML

> **状态：✅ 已实施并合入 main**（commit "feat: 数据访问层迁移 MyBatis-Plus（全部 SQL 入 XML）"）。
> 本方案为迁移设计与决策记录；实施中额外解决两个问题（见 §11 实施记录）。
> 目标：backend 模块数据访问层从 Spring Data JPA/Hibernate 迁移到 **MyBatis-Plus（MP）**，
> 所有 SQL（含按主键 CRUD）写入 `src/main/resources/mapper/*.xml` 便于人工维护；行为与现有实现完全等价。
> 基准：main `50f595d`。

## 0. 已确认决策

| 决策点 | 结论 |
|---|---|
| D1 SQL 边界 | **全部 SQL 都写 XML**（含按主键 selectById/insert/updateById/deleteById）；不依赖 BaseMapper 内置方法 |
| D2 范围 | **仅 backend** 迁移；gateway-demo 保留 JdbcTemplate |
| D3 版本 | **mybatis-plus-spring-boot3-starter 3.5.12**（实施时验证 Boot 3.5.16 兼容） |
| D4 分页方言 | **默认 DbType.MYSQL**，H2 测试实测兼容；不兼容再按 profile 双配置 |

D1 影响：乐观锁不再依赖 MP 插件，改为 **XML 手写 `WHERE version = #{version}` + `version = version + 1`**，
受影响行数 0 → 409；实体 `@Version` 注解不再需要（version 为普通字段，语义由 XML 保证）。

---

## 1. 目标与范围

| 项 | 说明 |
|---|---|
| 迁移范围 | **backend 模块**全量（5 实体 + 5 Repository + Service 数据访问调用点） |
| gateway-demo | **建议保留 JdbcTemplate**（独立演示工程，仅 2 条内联 SQL；迁移收益低）——见决策点 D2 |
| 不动 | 控制器/服务业务逻辑/事务 afterCommit 模式/前端/Flyway 迁移脚本/V1-V3 DDL |
| 行为等价 | 乐观锁 409、并发创建 409、审计、权限、吊销、限流、404、状态页（含 Predicates/Filters JSON）、分页——全部保持 |
| 交付物 | 5 个实体改造、5 个 Mapper 接口、5 个 XML、MP 配置与插件、Service 适配、测试适配 |

## 2. 现状盘点（数据访问点）

| 实体/表 | Repository 方法 | 用途 |
|---|---|---|
| route_config | findByRouteId / existsByRouteId / findAllByOrderByOrderNoAscIdAsc / findAllByEnabledTrueOrderByOrderNoAscIdAsc / search(LIKE) / save / saveAndFlush / delete / count | 路由 CRUD、列表/搜索、网关加载、健康检查 |
| sys_user | findByUsername / existsByUsername / findAll / save | 登录、改密、认证缓存全量加载 |
| audit_log | save / findAllByOrderByCreatedAtDesc(Pageable) | 审计写入与分页 |
| permission_rule | findAllByOrderByPriorityAscIdAsc / findAll / findById / save / saveAndFlush / delete | 权限规则缓存/守卫/CRUD |
| config_revision | findById(1) / bumpRevision(@Modifying UPDATE) | F13 修订号水印（读写） |

JPA 特有配置：`spring.jpa.open-in-view`、`spring.jpa.hibernate.ddl-auto`（application.yml / application-prod.yml，迁移后移除）。

## 3. 技术选型

| 项 | 选择 |
|---|---|
| 依赖 | 移除 `spring-boot-starter-data-jpa`；新增 `com.baomidou:mybatis-plus-spring-boot3-starter`（**3.5.12**，或实施时最新稳定版；需联网下载，本地仓库暂无，已验证下载通道可用） |
| 保留 | mysql-connector-j、H2（测试，MODE=MySQL）、flyway-core、flyway-mysql |
| XML 位置 | `src/main/resources/mapper/*.xml`，`mybatis-plus.mapper-locations: classpath*:mapper/*.xml` |
| 下划线映射 | `map-underscore-to-camel-case: true`（显式 `@TableField("xxx")` 双保险） |
| 枚举 | `default-enum-type-handler: EnumTypeHandler`（`AuditAction` 按 name 存库，与现 VARCHAR 值一致，**无需数据迁移**） |

## 4. 实体与 Mapper 映射

### 4.1 实体改造（5 个）

| 现 JPA 注解 | 迁移后 |
|---|---|
| `@Entity` / `@Table(name=...)` | `@TableName("route_config")` |
| `@Id @GeneratedValue(IDENTITY)` | `@TableId(type = IdType.AUTO)` |
| `@Column(name=..., nullable, length)` | `@TableField("column")`（长度/nullable 由 DDL 管，实体可不重复） |
| `@Version long version` | 保留 `@Version`（MP 同名注解，需乐观锁插件） |
| `@CreationTimestamp/@UpdateTimestamp` | 移除；`MetaObjectHandler` 插入/更新填充 `created_at/updated_at` |
| 枚举字段（AuditLog.action） | 全局 EnumTypeHandler（存 name） |
| boolean enabled | 常规 Boolean 字段（MySQL BOOLEAN=TINYINT(1)） |

### 4.2 Mapper 接口（5 个）

- `route/RouteConfigMapper`、`auth/UserMapper`、`audit/AuditLogMapper`、`permission/PermissionRuleMapper`、`route/ConfigRevisionMapper`
- **D1 约定**：**全部数据操作都走 XML 显式 SQL**；接口不继承 BaseMapper（或继承但所有方法均 XML 实现，不调用内置方法），Mapper 文件逐一映射 insert/selectById/updateById/deleteById 与条件查询。

### 4.3 XML 清单（全部 SQL，含按主键 CRUD）

| XML | SQL |
|---|---|
| RouteConfigMapper.xml | **insert（useGeneratedKeys 回主键）**；**selectById**；**updateById（`SET version=version+1, ... WHERE id=#{id} AND version=#{version}`，乐观锁手写）**；**deleteById**；按 routeId 查询；exists（SELECT COUNT(1)）；按 order_no,id 排序列表；enabled=true 排序列表；keyword LIKE 搜索（lower(concat('%',#{keyword},'%'))）；count |
| UserMapper.xml | **insert（useGeneratedKeys）**；**selectById**；**updateById**；按 username 查询；exists（SELECT COUNT(1)）；全量列表（认证缓存加载） |
| AuditLogMapper.xml | **insert（useGeneratedKeys）**；**selectById**；分页：按 created_at DESC（IPage<AuditLog> 参数） |
| PermissionRuleMapper.xml | **insert（useGeneratedKeys）**；**selectById**；**updateById**；**deleteById**；按 priority,id 排序全量（缓存 reload）；全量（守卫模拟用 findAll 等价） |
| ConfigRevisionMapper.xml | **selectById(1)**；`UPDATE config_revision SET revision = revision + 1 WHERE id = 1`（保留 @Transactional） |

> 时间戳：仍用 `MetaObjectHandler` 填充 created_at/updated_at（Java 侧参数值，实体同步回填，响应一致）；SQL 中只出现列名，不算"SQL 内联"。

## 5. 行为等价性对照（关键）

| 现状（JPA） | 迁移后（MP + XML） | 注意点 |
|---|---|---|
| `@Version` 乐观锁 → 捕获异常 409 | XML `updateById` 手写 `AND version=#{version}`，受影响行数 0 → `BusinessException.conflict` | 语义等价，测试断言不变；`version=version+1` 同语句完成 |
| 并发创建唯一约束 → `DataIntegrityViolationException` → 409 | MP 抛 `DuplicateKeyException`（是其子类） | **现有 catch 不变** |
| `saveAndFlush`（RouteService.update / setEnabled） | XML `updateById`（无需 flush，事务提交即落库） | 返回值判 0 → 409 |
| `save`（create） | XML `insert`（useGeneratedKeys 回主键） | — |
| `repository.count()` | XML `selectCount`（`SELECT COUNT(1) FROM route_config`） | — |
| `PageRequest` 分页 | XML 分页查询带 `IPage` 参数 + `PaginationInnerInterceptor` | 审计分页结果转换 PageResult |
| `@CreationTimestamp/@UpdateTimestamp` | `MetaObjectHandler`（insert/update fill） | insert 后实体 updatedAt 有值（响应可用） |
| `@Modifying` bulk update | XML `<update>` | 保留 @Transactional |
| `saveAll/deleteAll`（种子/测试） | 循环 insert / deleteById（MP 无单表批量 insert 内置） | 数据量小，循环可接受 |
| `findById(1).orElseThrow()` | `selectById(1)` null 判读 | 测试/服务适配 |

## 6. 配置变更

- `application.yml` / `application-prod.yml`：删除 `spring.jpa` 块；新增：
  ```yaml
  mybatis-plus:
    mapper-locations: classpath*:mapper/*.xml
    configuration:
      map-underscore-to-camel-case: true
      default-enum-type-handler: org.apache.ibatis.type.EnumTypeHandler
  ```
- 新增配置类 `config/MybatisPlusConfig`：
  - `MybatisPlusInterceptor`：`PaginationInnerInterceptor`（分页）+ `OptimisticLockerInnerInterceptor`（乐观锁）
  - `MetaObjectHandler` 实现（insertFill：created_at/updated_at；updateFill：updated_at）
- 分页方言：默认 `DbType.MYSQL`；H2（test profile，MODE=MySQL）下 `LIMIT ? OFFSET ?` 语法兼容，**实测验证**；不兼容则按 profile 注入 DbType（见风险 R1）

## 7. Service/调用点适配清单

| 文件 | 改动 |
|---|---|
| RouteService | exists/save→insert/saveAndFlush→updateById(判0→409)/delete→deleteById；`bumpRevision` 调用不变 |
| AuditService | 分页改 `Page<AuditLog> = auditLogMapper.selectPage(new Page<>(page-1,size), null)`（或 XML 分页）；PageResult 转换不变 |
| AuthService / UserAuthStateCache | findByUsername → mapper XML 查询（或 selectOne(QueryWrapper)）——按 D1 决策 |
| PermissionRuleService | findAllByOrderBy...→XML；findById → selectById（null 判读→404）；saveAndFlush→updateById |
| SeedDataInitializer / 测试 | exists/saveAll/deleteAll → mapper 等价方法 |
| DbRouteDefinitionLocator | findAllByEnabledTrue... → XML |

## 8. 实施步骤（方案通过后）

1. **依赖与配置**：pom 替换依赖（联网下载 MP）；yml 增删配置
2. **实体改造**：5 实体注解迁移 + AuditAction 枚举适配
3. **Mapper + XML**：5 接口 + 5 XML（全部显式 SQL 入 XML）
4. **插件与填充**：MybatisPlusConfig + MetaObjectHandler
5. **Service 适配**：乐观锁 409、分页、exists、insert/update/delete 方法替换
6. **测试适配与回归**：更新注入 repository 的测试（saveAll/deleteAll/findById(1) 等）；backend 全量回归（现 38 测）+ gateway-demo 9 + frontend 17
7. **验证点实测**：H2 分页/乐观锁方言、@Version 回填、枚举 name 存储
8. **文档**：README 技术栈更新、迁移记录（ADR 或本方案标记已实施）

## 9. 风险与对策

| # | 风险 | 对策 |
|---|---|---|
| R1 | H2（MODE=MySQL）与 MP 分页/乐观锁方言兼容性 | 分页实测（D4 默认 MYSQL）；乐观锁为手写 XML 无插件方言问题 |
| R2 | 枚举（AuditAction）存量数据为 name 字符串 | 全局 EnumTypeHandler 存 name，值不变，无需迁移 |
| R3 | boolean 列 / 时间戳列类型映射偏差 | DDL 不动；实体显式 @TableField；回归测试覆盖 |
| R4 | insert/update 后 `version`/`updatedAt` 回填差异 | `version`：XML 手写自增，update 后 Service 层实体 version+1 同步；`updatedAt`：MetaObjectHandler 填充值实体已回填 |
| R5 | MP 依赖需联网下载 | 已验证下载通道可用；锁定版本 3.5.12 |
| R6 | 测试注入 JpaRepository 类型（saveAll/deleteAll/findById(1).orElseThrow） | 统一改 Mapper 调用并适配断言 |
| R7 | Flyway 与 MP 并存 | 无冲突（Flyway 只管 DDL） |

## 10. 验收标准

- backend 全部现有测试通过（行为等价：乐观锁 409 / 并发 409 / 审计 / 权限 / 吊销 / 限流 / 404 / 状态页 JSON 展示 / revision 水印）
- gateway-demo 9 测、frontend 17 测不受影响（若 D2 选不迁移 gateway-demo）
- 代码审查：**无 Java 内联显式 SQL 残留**（backend；除 BaseMapper 内置方法外），全部在 mapper/*.xml
- 手工验证：路由 CRUD + 状态页悬浮框 JSON + 审计分页 + 权限规则即时生效

## 11. 实施记录（方案外补充）

| # | 实施中发现的坑 | 解决 |
|---|---|---|
| X1 | **分页插件不在 starter 里**：MP 3.5.9+ 将 `PaginationInnerInterceptor` 拆到独立 `mybatis-plus-jsqlparser` 模块 | pom 额外引入 `mybatis-plus-jsqlparser:3.5.12` |
| X2 | **纯 XML 场景 TableInfo 未初始化**：Mapper 不继承 BaseMapper 时，MP 不会自动构建实体的 TableInfo 缓存，导致 `MetaObjectHandler.strictInsertFill` NPE / 时间戳填充失效（insert 报 `created_at NULL`） | `MybatisPlusConfig.initTableInfo()` 启动时对 5 个实体调用 `TableInfoHelper.initTableInfo(...)` 预初始化 |

---

## 决策点（已确认）

- **D1** SQL 边界：BaseMapper 内置单表 CRUD（按主键）不写 XML，仅**带条件/排序/更新的显式 SQL 写 XML**（推荐，可维护性最优）；还是**连主键 CRUD 也全部显式写 XML**（更严格、XML 更多）？
- **D2** 范围：仅 backend 迁移（推荐，gateway-demo 保留 JdbcTemplate）；还是 backend + gateway-demo 全部迁移？
- **D3** 版本：MP 3.5.12（锁定，实施时验证 Boot 3.5.16 兼容）；或由我按最新稳定版自选？
- **D4** 分页方言：默认 MYSQL + 实测 H2（推荐）；或直接按 profile 双配置 DbType？
