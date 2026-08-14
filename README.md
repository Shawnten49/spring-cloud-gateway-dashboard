# Gateway Dashboard

Spring Cloud Gateway 管理后台：登录后可查看、修改、保存网关路由配置，改动无需重启网关即可生效（保存即生效，操作全程留审计日志）。

## 功能

- 登录认证：用户表 + JWT（12 小时），`ADMIN`（可读写）/ `VIEWER`（只读）两种角色，预置 `admin` / `viewer` 账号
- 用户管理：ADMIN 可新增用户、屏蔽/启用用户（屏蔽即吊销其 token）；**不允许删除用户**；`admin` 为特殊用户不可屏蔽（需求与设计见 docs/modules/）
- Token 吊销：改密、屏蔽账号后该用户已签发 token **立即失效**（S-04 机制，无需等过期）
- 登录限流：按客户端 IP 令牌桶（默认 10 次/分钟），超限返回 429，防止暴力破解
- 权限配置：接口访问权限规则存于数据库，方法 + 路径 + 允许角色 + 优先级，修改后即时生效（新增模块无需改代码）
- 路由管理：路由（URI、order、predicates、filters、metadata、enabled）的增删改查，可停用/启用，保存前做服务端强校验（工厂名 + 参数绑定）
- 动态生效：数据库为路由配置唯一真源，保存/删除/停用后自动触发热刷新（事件驱动），无需重启；内嵌网关另有 **5 秒轮询兜底**（他实例/直连库的变更也会自动生效，全局修订号水印驱动）
- 网关状态：只读展示健康状态、最近刷新时间、当前生效路由（Predicates/Filters 完整 JSON，悬浮框展示），用于验证"保存即生效"
- 操作审计：每次 创建/修改/删除/停用/启用 记录操作者、时间、变更前后内容、IP
- 高级 JSON 模式：predicates/filters 支持结构化编辑，也支持整路由 JSON 编辑
- 统一错误响应：未登录 401 / 无权限 403 / 未知接口 404 / 参数错误 400 / 冲突 409 / 限流 429 均返回统一 JSON 信封

## 架构

管理后台与网关运行在同一个 Spring Boot 进程内（单实例进程，支持管理外部网关实例）：路由配置存储在 MySQL（MyBatis-Plus 访问，**全部 SQL 在 `backend/src/main/resources/mapper/*.xml`**），通过自定义 `RouteDefinitionLocator` 接入 Spring Cloud Gateway；每次写操作落库后发布 `RouteChangedEvent`，由刷新监听器统一完成本地生效路由刷新（含预热）→ 修订号标记 → 外部网关推送；全局修订号（`config_revision` 水印）驱动内嵌网关 5 秒轮询兜底，任何来源的变更（他实例/直连库）都会自动生效。设计决策见 [docs/adr](docs/adr/)，领域词汇表见 [CONTEXT.md](CONTEXT.md)，文档导航见 [docs/README.md](docs/README.md)。

> 详细使用说明（动态路由配置原理、页面操作、Predicate/Filter 示例、API 脚本化）见 [docs/使用手册.md](docs/使用手册.md)。

### 对接外部网关（管理独立部署的 Gateway）

仪表盘默认管理**自己进程内嵌的网关**。如果要管理独立部署的网关（如 `http://localhost:8088`），需要：

1. 在外部网关工程中集成"数据库路由源 + 刷新接口"（本仓库自带已集成的演示工程 [gateway-demo](gateway-demo/)）：
   - 加 `spring-boot-starter-jdbc` + `mysql-connector-j` 依赖，数据源指向同一个 `gateway_dashboard` 库
   - 把 `DbRouteDefinitionLocator` 等 6 个类放入网关工程（读取 `route_config` 表、轮询**全局修订号水印**、内部刷新/查看接口）
   - 从 YAML 中移除业务路由，改由数据库管理
2. 在仪表盘 `application.yml` 配置外部网关，保存路由后自动推送刷新：

```yaml
gateway-dashboard:
  external-gateways:
    - base-url: http://localhost:8088
      token: gd-internal-token-dev   # 与网关工程的 internal-token 一致
```

> 网关侧内部接口（`/internal/routes/**`）为 **fail-closed**：`internal-token` 未配置时拒绝启动，token 缺失/错误一律 401（恒定时间比较）。生产请用强随机 token（如 `openssl rand -hex 32`）并通过环境变量注入。

推送失败不影响保存结果：网关侧另有 5 秒修订号轮询兜底，路由最多延迟一个轮询周期生效。

配置后，仪表盘"网关状态"页会展示每个外部网关实例：在线状态、最近推送结果与时间、该实例的生效路由列表。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x（Northfields）、Spring Cloud Gateway 4.3.x（WebFlux）、Spring Security + JWT、MyBatis-Plus 3.5.x（**全部 SQL 写在 `backend/src/main/resources/mapper/*.xml`**，见 docs/proposals/mybatis-plus迁移方案.md）、Flyway、MySQL 8（测试用 H2 MySQL 兼容模式）
- 前端：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + axios

## 快速开始

### 1. 准备 MySQL（macOS Homebrew）

```bash
brew install mysql@8.4
brew services start mysql@8.4
mysql -u root
```

在 `mysql>` 会话中执行：

```sql
CREATE DATABASE gateway_dashboard DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'gateway'@'localhost' IDENTIFIED BY 'gateway123';
GRANT ALL PRIVILEGES ON gateway_dashboard.* TO 'gateway'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run            # 默认 dev profile（MySQL）
```

首次启动会自动建表（Flyway）、创建预置账号和 2 条 httpbin 示例路由。

> 本机还没有 MySQL 时，可用 H2 文件库临时跑通全流程：`mvn spring-boot:run -Dspring-boot.run.profiles=local`。**运行/交付环境请使用 MySQL（dev profile）。**

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev                    # http://localhost:5173，/api 自动代理到 8080
```

### 4.（可选）启动演示网关 gateway-demo

仓库自带一个独立部署形态的演示网关（数据库路由源 + 刷新接口，端口 8088），用于演示仪表盘管理**外部网关**：

```bash
cd gateway-demo
mvn spring-boot:run            # http://localhost:8088，需 Nacos（127.0.0.1:8848）
```

### 预置账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | ADMIN（可读写） |
| viewer | viewer123 | VIEWER（只读） |

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 8080 | 后端端口 |
| `gateway-dashboard.jwt.secret` | 见 application.yml | JWT 密钥；**非 dev/local/test profile 下使用默认值会拒绝启动**，生产必须用 `JWT_SECRET` 环境变量注入（至少 32 字节） |
| `gateway-dashboard.jwt.expire-hours` | 12 | Token 有效期；改密/屏蔽账号后旧 token 立即吊销（S-04） |
| `gateway-dashboard.seed.admin-password` / `.viewer-password` | admin123 / viewer123 | 首次启动预置账号口令；生产用 `GD_ADMIN_PASSWORD` / `GD_VIEWER_PASSWORD` 覆盖，账号不存在且口令为空时拒绝启动 |
| `gateway-dashboard.security.login-rate-limit.capacity` / `.refill-per-minute` | 10 / 10 | 登录限流（Bucket4j 令牌桶，按客户端 IP）：容量 + 每分钟补充 |
| `gateway-dashboard.route-sync.enabled` | true | 内嵌网关轮询兜底开关（F5：他实例/直连库变更 ≤ 轮询周期生效） |
| `gateway-dashboard.route-sync.poll-interval-ms` | 5000 | 内嵌网关轮询间隔（也是 gateway-demo 外部网关轮询间隔） |
| `gateway-dashboard.cors.allowed-origins` | localhost:5173 | 允许的跨域来源，逗号分隔 |
| `gateway-dashboard.external-gateways` | localhost:8088 | 外部网关实例；内部 token 建议生产用 `GD_INTERNAL_TOKEN` 覆盖 |
| `spring.datasource.*`（dev） | localhost:3306 / gateway / gateway123 | MySQL 连接 |
| `spring.datasource.*`（local） | H2 文件库 | 仅本地临时开发 |

**生产部署（prod profile）**：`SPRING_PROFILES_ACTIVE=prod` 时，`JWT_SECRET`、`GD_ADMIN_PASSWORD`、`GD_VIEWER_PASSWORD` 均无默认值，缺失或使用开发默认值将拒绝启动（详见 `application-prod.yml`）。

## API 摘要

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | /api/auth/login | 登录 | 公开 |
| GET | /api/auth/me | 当前用户 | 登录 |
| PUT | /api/auth/password | 修改自己的密码 | 登录 |
| GET | /api/routes | 路由列表（可按 keyword 搜索） | 登录 |
| GET | /api/routes/{routeId} | 路由详情 | 登录 |
| POST | /api/routes | 创建路由（保存即生效） | ADMIN |
| PUT | /api/routes/{routeId} | 更新路由 | ADMIN |
| DELETE | /api/routes/{routeId} | 删除路由 | ADMIN |
| POST | /api/routes/{routeId}/enabled | 启用/停用（请求体必须显式给出 `{"enabled": true/false}`，缺失返回 400，避免静默停用） | ADMIN |
| POST | /api/routes/validate | 只校验不保存 | 登录 |
| GET | /api/meta/factories?type=predicate\|filter | 支持的工厂名列表 | 登录 |
| GET | /api/gateway/status | 网关健康、生效路由、最近刷新时间、外部网关实例状态（在线/最近推送/生效路由） | 登录 |
| GET | /api/audit-logs?page=&size= | 操作审计分页 | 登录 |
| GET | /api/permission-rules | 权限规则列表 | ADMIN |
| POST | /api/permission-rules | 新增权限规则（即时生效） | ADMIN |
| PUT | /api/permission-rules/{id} | 修改权限规则 | ADMIN |
| DELETE | /api/permission-rules/{id} | 删除权限规则（内置规则不可删除） | ADMIN |
| GET | /api/users?keyword= | 用户列表（不含密码哈希） | ADMIN |
| POST | /api/users | 新增用户（用户名唯一、密码 ≥8 位、角色 ADMIN/VIEWER） | ADMIN |
| PUT | /api/users/{id}/enabled | 屏蔽/启用用户（屏蔽即吊销其 token；**admin 不可屏蔽**；无删除接口） | ADMIN |

统一响应体：`{ "code": 200, "message": "ok", "data": ... }`。

## 测试

```bash
mvn verify                    # 根目录全量：backend 43 测 + gateway-demo 9 测 + frontend 构建 + 打包（后端 JaCoCo 覆盖率门槛）
cd backend && mvn test        # 仅后端：43 个用例（路由校验/权限保护/审计/吊销/限流/404/用户管理/外部网关等集成测试，H2）
cd gateway-demo && mvn test   # 仅网关：9 个用例（内部接口 token fail-closed + 轮询兜底同步，Mockito 单测）
cd frontend && npm test       # 仅前端：21 个用例（store/Login/路由列表/网关状态/权限规则/用户管理等组件测试）
cd frontend && npm run test:coverage   # 前端覆盖率报告 + 门槛
```

## Maven 多模块构建（根主项目）

根目录 `pom.xml` 为 Maven 主项目（聚合 backend / frontend / gateway-demo，版本统一管理，见 [docs/proposals/maven多模块方案.md](docs/proposals/maven多模块方案.md)）：

```bash
mvn package          # 根目录一条命令构建全部：后端 jar + 网关 jar + 前端 dist
mvn verify           # 全量测试 + 打包（含后端 JaCoCo 覆盖率门槛）
mvn -pl backend test # 仅后端（-am 连带父 pom）
cd backend && mvn test   # 子目录独立构建仍可用（父 pom 经 relativePath 解析）
```

## Docker（交付参考，未在本机验证）

`docker/` 提供 MySQL 8.4 + 后端 + 前端（Nginx）的编排文件与 Dockerfile：

```bash
cd docker
docker compose up -d --build    # 前端 http://localhost:8088，后端 http://localhost:8080
```

## 目录结构

```text
.
├── pom.xml           # Maven 主项目（聚合 backend/frontend/gateway-demo，版本统一管理）
├── backend/          # Spring Boot 后端（Gateway + 管理 API，MyBatis-Plus + XML SQL）
├── gateway-demo/     # 独立部署形态的演示网关（数据库路由源，端口 8088）
├── frontend/         # Vue 3 前端（含 Maven 包装模块，mvn 可触发 npm 构建）
├── docker/           # Docker 交付物（未验证）
├── docs/             # 文档中心（导航见 docs/README.md：adr/ 架构决策、review/ 评审报告、
│                     #   proposals/ 方案、modules/ 模块需求设计、使用手册.md）
└── CONTEXT.md        # 领域词汇表
```

## 已知边界

- 单实例进程；多实例刷新传播已具备**数据库修订号轮询兜底**（内嵌网关 + 外部网关均 ≤5s 生效），低延迟广播（Redis pub/sub）仍为可选演进
- 无草稿/发布流程，保存即生效；误操作靠审计日志追溯
- 只管理路由（RouteDefinition），不管理全局过滤器等完整网关配置
- 用户管理仅支持新增 / 屏蔽 / 启用；修改角色、重置他人密码不在本期范围
