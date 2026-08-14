# Gateway Dashboard

Spring Cloud Gateway 管理后台：登录后可查看、修改、保存网关路由配置，改动无需重启网关即可生效（保存即生效，操作全程留审计日志）。

## 功能

- 登录认证：用户表 + JWT（12 小时），`ADMIN`（可读写）/ `VIEWER`（只读）两种角色，预置 `admin` / `viewer` 账号
- 权限配置：接口访问权限规则存于数据库，方法 + 路径 + 允许角色 + 优先级，修改后即时生效（新增模块无需改代码）
- 路由管理：路由（URI、order、predicates、filters、metadata、enabled）的增删改查，可停用/启用，保存前做服务端强校验（工厂名 + 参数绑定）
- 动态生效：数据库为路由配置唯一真源，保存/删除/停用后自动触发 `RefreshRoutesEvent` 热刷新，无需重启
- 网关状态：只读展示健康状态、最近刷新时间、当前生效路由，用于验证"保存即生效"
- 操作审计：每次 创建/修改/删除/停用/启用 记录操作者、时间、变更前后内容、IP
- 高级 JSON 模式：predicates/filters 支持结构化编辑，也支持整路由 JSON 编辑

## 架构

管理后台与网关运行在同一个 Spring Boot 进程内（单实例 MVP），路由配置存储在 MySQL，通过自定义 `RouteDefinitionLocator` 接入 Spring Cloud Gateway；每次写操作落库后发布刷新事件热生效。设计决策见 [docs/adr](docs/adr/)，领域词汇表见 [CONTEXT.md](CONTEXT.md)。

> 详细使用说明（动态路由配置原理、页面操作、Predicate/Filter 示例、API 脚本化）见 [docs/使用手册.md](docs/使用手册.md)。

### 对接外部网关（管理独立部署的 Gateway）

仪表盘默认管理**自己进程内嵌的网关**。如果要管理独立部署的网关（如 `http://localhost:8088`），需要：

1. 在外部网关工程中集成"数据库路由源 + 刷新接口"（本仓库自带已集成的演示工程 [gateway-demo](gateway-demo/)）：
   - 加 `spring-boot-starter-jdbc` + `mysql-connector-j` 依赖，数据源指向同一个 `gateway_dashboard` 库
   - 把 `DbRouteDefinitionLocator` 等 6 个类放入网关工程（读取 `route_config` 表、轮询版本号、内部刷新/查看接口）
   - 从 YAML 中移除业务路由，改由数据库管理
2. 在仪表盘 `application.yml` 配置外部网关，保存路由后自动推送刷新：

```yaml
gateway-dashboard:
  external-gateways:
    - base-url: http://localhost:8088
      token: gd-internal-token-dev   # 与网关工程的 internal-token 一致
```

> 网关侧内部接口（`/internal/routes/**`）为 **fail-closed**：`internal-token` 未配置时拒绝启动，token 缺失/错误一律 401（恒定时间比较）。生产请用强随机 token（如 `openssl rand -hex 32`）并通过环境变量注入。

推送失败不影响保存结果：网关侧另有 5 秒版本轮询兜底，路由最多延迟一个轮询周期生效。

配置后，仪表盘"网关状态"页会展示每个外部网关实例：在线状态、最近推送结果与时间、该实例的生效路由列表。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x（Northfields）、Spring Cloud Gateway 4.3.x（WebFlux）、Spring Security + JWT、Spring Data JPA、Flyway、MySQL 8（测试用 H2 MySQL 兼容模式）
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
| `gateway-dashboard.jwt.expire-hours` | 12 | Token 有效期 |
| `gateway-dashboard.seed.admin-password` / `.viewer-password` | admin123 / viewer123 | 首次启动预置账号口令；生产用 `GD_ADMIN_PASSWORD` / `GD_VIEWER_PASSWORD` 覆盖，账号不存在且口令为空时拒绝启动 |
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

统一响应体：`{ "code": 200, "message": "ok", "data": ... }`。

## 测试

```bash
cd backend && mvn test          # 22 个用例：路由校验 + JWT 默认值治理 + 权限保护回归 + 审计全动作 + 登录/CRUD/停用/外部网关集成（H2）
cd gateway-demo && mvn test     # 9 个用例：内部接口 token fail-closed + 轮询兜底同步（Mockito 单测，无需 MySQL/Nacos）
cd frontend && npm test         # 路由 JSON 工具 Vitest
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
├── backend/          # Spring Boot 后端（Gateway + 管理 API）
├── gateway-demo/     # 独立部署形态的演示网关（数据库路由源，端口 8088）
├── frontend/         # Vue 3 前端
├── docker/           # Docker 交付物（未验证）
├── docs/adr/         # 架构决策记录
└── CONTEXT.md        # 领域词汇表
```

## 已知边界（v1）

- 单实例网关；多实例刷新传播（Redis pub/sub 等）留待后续版本
- 无草稿/发布流程，保存即生效；误操作靠审计日志追溯
- 只管理路由（RouteDefinition），不管理全局过滤器等完整网关配置
