# Gateway Dashboard

Spring Cloud Gateway 管理后台：登录后可查看、修改、保存网关路由配置，改动无需重启网关即可生效（保存即生效，操作全程留审计日志）。

## 功能

- 登录认证：用户表 + JWT（12 小时），`ADMIN`（可读写）/ `VIEWER`（只读）两种角色，预置 `admin` / `viewer` 账号
- 路由管理：路由（URI、order、predicates、filters、metadata、enabled）的增删改查，可停用/启用，保存前做服务端强校验（工厂名 + 参数绑定）
- 动态生效：数据库为路由配置唯一真源，保存/删除/停用后自动触发 `RefreshRoutesEvent` 热刷新，无需重启
- 网关状态：只读展示健康状态、最近刷新时间、当前生效路由，用于验证"保存即生效"
- 操作审计：每次 创建/修改/删除/停用/启用 记录操作者、时间、变更前后内容、IP
- 高级 JSON 模式：predicates/filters 支持结构化编辑，也支持整路由 JSON 编辑

## 架构

管理后台与网关运行在同一个 Spring Boot 进程内（单实例 MVP），路由配置存储在 MySQL，通过自定义 `RouteDefinitionLocator` 接入 Spring Cloud Gateway；每次写操作落库后发布刷新事件热生效。设计决策见 [docs/adr](docs/adr/)，领域词汇表见 [CONTEXT.md](CONTEXT.md)。

> 详细使用说明（动态路由配置原理、页面操作、Predicate/Filter 示例、API 脚本化）见 [docs/使用手册.md](docs/使用手册.md)。

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

### 预置账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | ADMIN（可读写） |
| viewer | viewer123 | VIEWER（只读） |

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 8080 | 后端端口 |
| `gateway-dashboard.jwt.secret` | 见 application.yml | JWT 密钥，生产用 `JWT_SECRET` 环境变量覆盖 |
| `gateway-dashboard.jwt.expire-hours` | 12 | Token 有效期 |
| `spring.datasource.*`（dev） | localhost:3306 / gateway / gateway123 | MySQL 连接 |
| `spring.datasource.*`（local） | H2 文件库 | 仅本地临时开发 |

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
| POST | /api/routes/{routeId}/enabled | 启用/停用 | ADMIN |
| POST | /api/routes/validate | 只校验不保存 | 登录 |
| GET | /api/meta/factories?type=predicate\|filter | 支持的工厂名列表 | 登录 |
| GET | /api/gateway/status | 网关健康、生效路由、最近刷新时间 | 登录 |
| GET | /api/audit-logs?page=&size= | 操作审计分页 | 登录 |

统一响应体：`{ "code": 200, "message": "ok", "data": ... }`。

## 测试

```bash
cd backend && mvn test          # 路由校验单测 + 登录/创建/停用/审计 集成测试（H2）
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
├── frontend/         # Vue 3 前端
├── docker/           # Docker 交付物（未验证）
├── docs/adr/         # 架构决策记录
└── CONTEXT.md        # 领域词汇表
```

## 已知边界（v1）

- 单实例网关；多实例刷新传播（Redis pub/sub 等）留待后续版本
- 无草稿/发布流程，保存即生效；误操作靠审计日志追溯
- 只管理路由（RouteDefinition），不管理全局过滤器等完整网关配置
