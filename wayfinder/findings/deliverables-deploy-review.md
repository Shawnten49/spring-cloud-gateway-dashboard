# Findings — 交付物与部署评审（ticket 007）

> 评审方式：**静态审阅**，未启动服务、未跑测试、未执行任何 docker 命令（本机无 Docker）。
> 所有 Docker 相关结论均为「**未实机构建验证**」的静态推断。
> 评审日期：2026-08-15。基准 commit：`bf1b7d7`（main）。
> 来源标注格式：`文件路径:行号`。

## 总体判断

交付物整体结构合理：多阶段 Dockerfile、compose 依赖/健康检查、Nginx 反代、H2 测试隔离、
README 快速开始与工程实际**核对基本一致**（预置账号、示例路由、端口、profile 默认值均吻合）。
但存在 **1 个很可能直接导致 docker compose 构建失败的问题（P1，缺 .dockerignore + `COPY . .` 覆盖 node_modules）**、
**无 prod profile / 密钥明文（P1 生产化缺口）**，以及一批 P2 健壮性与对齐问题。
`gateway-demo` 未进 compose 属「文档化省略」，原因明确（无 Dockerfile + 依赖 Nacos，compose 未提供）。
ADR 0004 关于「Boot 3.5 OSS 已于 2026-06 结束」的陈述**属实**（见事实核查节）。
未发现 P0（无数据丢失/安全被直接攻破/整体不可用的灾难级问题；但 P1 两项若交付即上线会立刻显现）。

---

## 1. Docker 交付物（全部结论「未实机构建验证」）

### P1-1 — 前端 Dockerfile 缺 `.dockerignore`，`COPY . .` 会用宿主 node_modules 覆盖容器内 Linux 依赖，macOS 宿主上构建大概率失败

- **位置**：`frontend/Dockerfile:3-6`；`frontend/.dockerignore` **不存在**（`backend/.dockerignore` 同样不存在）
- **为什么**：`frontend/Dockerfile:3-4` 先 `COPY package.json package-lock.json ./` + `npm ci`（在 node:24-alpine 内装好 **Linux** 二进制，含 esbuild/vite 平台包），随后 `Dockerfile:5` `COPY . .` 把宿主目录整体拷入——由于没有 `.dockerignore`，宿主 `frontend/node_modules`（160M，darwin 平台二进制，见 `du -sh frontend/node_modules`）**直接覆盖**容器内刚装好的 Linux 依赖；`Dockerfile:6` `npm run build` 在 Linux 容器里执行 darwin 平台的 esbuild → 极可能报 "esbuild was built for a different platform" 之类错误。此外构建上下文还会携带 `dist/`、`.git` 等无关内容。
- **建议修法**：
  1. 新增 `frontend/.dockerignore`（`node_modules`、`dist`、`.git`、`coverage` 等）；
  2. 更稳的分层写法：`COPY package*.json ./` → `RUN npm ci` → `COPY src ./src`（及其他必要文件）→ `RUN npm run build`，避免整目录覆盖；
  3. 同时新增 `backend/.dockerignore`（`target/`、`data/`、`logs/`），backend 上下文当前含 `target/`（约 624K）与 `data/`（H2 文件 52K），虽不拷入镜像但拖慢构建、破坏缓存。

### P1-2 — 生产化缺口：无 prod profile，密钥/口令以明文入库与硬编码进 compose

- **位置**：`backend/src/main/resources/application.yml:27`（JWT 默认密钥明文，仅靠注释提醒覆盖）、`backend/src/main/resources/application-dev.yml:3-5`（MySQL 明文口令）、`docker/docker-compose.yml:8-11,28-31`（root123 / gateway123 / JWT secret 硬编码在编排文件里）、`gateway-demo/src/main/resources/application.yml:8-10`（同样明文）
- **为什么**：仓库唯一 profile 体系是 dev（MySQL 明文）/ local（H2）/ test（H2 内存），**没有 prod profile**。若按 README「交付参考」直接部署，等于默认密钥 + 弱口令 + 无日志文件 + 仅 health 端点（见 P2-4），生产不可用也不安全。
- **建议修法**：
  1. 新增 `application-prod.yml`：所有敏感项一律 `${ENV_VAR}` 注入且**不设默认值**（启动即失败而不是带默认密钥跑）；
  2. compose 改用 `.env` 文件注入，并从编排文件移除字面口令；
  3. 补充日志：`logback-spring.xml` 文件滚动（当前无任何日志文件配置）；
  4. 补充监控：见 P2-4 的 actuator/prometheus 建议。

### P2-1 — docker-compose 无 `gateway-demo` 服务，且未提供其依赖的 Nacos

- **位置**：`docker/docker-compose.yml`（全文仅 mysql/backend/frontend 三个服务）；`gateway-demo/Dockerfile` **不存在**（仓库仅 `backend/Dockerfile`、`frontend/Dockerfile` 两个）；`gateway-demo/src/main/resources/application.yml:15`（`spring.cloud.nacos.discovery.server-addr: 127.0.0.1:8848`）
- **为什么**：① gateway-demo 没有 Dockerfile，compose 无从构建；② 它依赖 Nacos 注册中心，compose 既无 nacos 服务、也无环境变量可关掉 discovery，硬塞进 compose 会起不来；③ README:149 已明确 Docker 只含「MySQL 8.4 + 后端 + 前端」。后果：compose 跑起来的仪表盘，其「网关状态」页的外部网关实例（`application.yml:32` 默认 `http://localhost:8088`）会显示**离线**，保存路由后的推送会失败——设计上「推送失败不影响保存结果」（`application.yml` 注释 + README:38），功能不受损，但首次使用者会困惑。
- **建议修法**：交付范围内二选一——(a) 补 `gateway-demo/Dockerfile` + compose 可选 nacos 服务（或让 gateway-demo 支持 `SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false` 之类开关），把它纳入 compose（注意端口：本地 demo 与 compose 前端都占 8088，需错开）；(b) 维持现状但在 README Docker 一节明确写「外部网关联动不在 Docker 交付范围，仪表盘外部网关状态页显示离线属预期」。

### P2-4 — 后端/前端容器无健康检查与自愈策略；actuator 仅暴露 health

- **位置**：`backend/Dockerfile:8-12`（无 `HEALTHCHECK` 指令、无非 root 用户）、`docker/docker-compose.yml:22-36`（backend 无 healthcheck）、`docker/docker-compose.yml:44-45`（frontend `depends_on: backend` 无 condition，backend 未就绪时 nginx 先起、首个请求 502）、`backend/src/main/resources/application.yml:18-22`（actuator `include: health`，无 liveness/readiness probes、无 metrics）
- **为什么**：编排层无法感知后端是否就绪/存活，宕机不会自动拉起；生产监控（指标、探针）完全没有。
- **建议修法**：Dockerfile 加 `HEALTHCHECK`（如 `CMD-SHELL` 轮询 `/actuator/health`，temurin JRE 无 curl/wget，可用 java 单行或换带 curl 的镜像/装 busybox）；compose 给 backend 加 `healthcheck` + `restart: unless-stopped`、frontend `depends_on` 加 `condition`；actuator 增加 `prometheus` 端点与 `management.endpoint.health.probes.enabled`。

### P3-1 — backend Dockerfile 硬编码 jar 名、镜像 tag 未锁定

- **位置**：`backend/Dockerfile:10`（`COPY --from=build /app/target/gateway-dashboard-backend-0.1.0-SNAPSHOT.jar app.jar`）、`backend/Dockerfile:1,8` 与 `frontend/Dockerfile:1,8`（浮动 tag：`maven:3.9-eclipse-temurin-21`、`eclipse-temurin:21-jre`、`node:24-alpine`、`nginx:1.27-alpine`）
- **为什么**：pom 版本一旦从 `0.1.0-SNAPSHOT` 改动，Dockerfile 未同步即构建失败；浮动 tag 使构建结果不可复现（供应链风险）。
- **建议修法**：jar 复制改通配 `target/*.jar` 或 ARG 传版本；基础镜像锁 digest 或至少锁 minor。

### P3-2 — compose 细节：密钥直接写在编排文件、frontend 无启动条件

- **位置**：`docker/docker-compose.yml:8-11`（`MYSQL_ROOT_PASSWORD: root123`）、`docker/docker-compose.yml:31`（`JWT_SECRET` 字面值）、`docker/docker-compose.yml:44-45`
- **为什么**：编排文件进 git，等于口令/密钥入库的又一份副本；frontend 先于 backend 就绪启动无意义。
- **建议修法**：`.env` + `${VAR}` 注入；frontend `depends_on` 加 `condition: service_healthy`。

### P3-3 — local profile 的 H2 开了 AUTO_SERVER

- **位置**：`backend/src/main/resources/application-local.yml:5`（`AUTO_SERVER=TRUE`）
- **为什么**：允许 H2 通过 TCP 被远程连接（默认无密码），本机 demo 可接受，交付给他人时是意外暴露面。
- **建议修法**：本地单机使用去掉 `AUTO_SERVER=TRUE` 即可。

---

## 2. startup.sh（本地脚本，gitignored）

> 注意：`startup.sh` 位于仓库根目录但被 `.gitignore:27` 忽略、**未纳入 git**（`git ls-files` 无此文件），属作者本地脚本；README 也未提及它。以下为静态审阅。

### P2-3 — 健壮性：强杀端口上的一切进程、无 MySQL 预检、端口可覆盖性不一致

- **位置**：`startup.sh:51-77`（`kill_port` 按端口 `lsof` 后 `kill`/`kill -9`，不校验进程归属）、`startup.sh:106-127`（`restart` 无 MySQL 预检，后端在 MySQL 未启动时空转 90 秒后才打 WARNING：`startup.sh:115` `wait_ready ... 90`）、`startup.sh:39-40`（`BACKEND_PORT` 可覆盖而 `FRONTEND_PORT=5173` 固定）
- **为什么**：共享机器上 `restart` 会误杀占用 18080/5173 的无关进程；MySQL 未就绪时体验差（90s 静默等待）；端口覆盖能力不一致是使用陷阱。
- **建议修法**：`kill_port` 先 `ps` 校验进程命令行含 backend/frontend 特征再杀；`restart` 前检查 `3306` 端口或先 `wait_ready` MySQL；`FRONTEND_PORT` 也支持 `${FRONTEND_PORT:-5173}` 覆盖。

### P3-4 — 脚本整体未随仓库交付，README 不引用，「一键启动」只存在于作者机器

- **位置**：`.gitignore:27`、`README.md`（全文无 startup.sh）、`startup.sh:4`（自述「Local-only file (gitignored)」）
- **为什么**：作为交付评审对象，该脚本是「最顺手的启动方式」，但新克隆者拿不到它，README 快速开始只有手动 mvn/npm 流程——交付一致性缺口。
- **建议修法**：二选一——把脚本纳入仓库（删 `.gitignore:27` 条目，README 快速开始引用 `bash startup.sh restart`），或维持现状并在 README 明确「一键脚本仅作者本地使用」。

### P3-5 — 其余静态观察（低危）

- `startup.sh:97` `status` 检查 3306/8848/8080/18080/5173/8088 六个端口——`8080` 是「标准后端端口」而脚本默认 `18080`，两套端口口径并存（`startup.sh:26` 注释已解释），建议脚本默认跟随 README 的 8080 或统一文档口径。
- `startup.sh:150-155` `gateway` 命令直接 `mvn spring-boot:run`，Nacos 不可达时的行为未验证（SCA 默认 fail-fast 与否不确定），注释已提示「需 Nacos 127.0.0.1:8848」。
- `startup.sh:30` 用 `cd "$(dirname "$0")"` 定位 SCRIPT_DIR，脚本被 `bash path/to/startup.sh` 调用时行为正确（BASH_SOURCE 更稳，低危）。

---

## 3. 配置 profile

### P2-2（并入 1 节 P1-2 的口径）— profile 用途基本清晰，但缺 prod、默认即明文

- **位置**：`backend/src/main/resources/application.yml:5`（`spring.profiles.default: dev`）、`application-dev.yml:1-6`（MySQL）、`application-local.yml:1-8`（H2 文件，注释明确「仅本地」）、`application-test.yml:1-12`（H2 内存 + 外部网关指向不可达 `localhost:19999`，保证测试可重复——**设计良好**，测试经 `@ActiveProfiles("test")` 生效：`backend/src/test/java/.../*Test.java:15,18,14,20`）
- **为什么**：dev/local/test 三者边界清晰、注释到位；缺口在**生产**：无 prod profile（见 P1-2），且 `spring.profiles.default: dev` 使 `mvn spring-boot:run` 不带参数即连 MySQL，无 MySQL 时启动失败（README:71-76 已说明，属可接受的默认选择）。
- **建议修法**：新增 prod profile（见 P1-2 建议）；可选：把默认 profile 改为无外部依赖的 local，显式 `--spring.profiles.active=dev` 才连 MySQL，降低新手上手门槛（会与 README 现状冲突，需同步改文档）。

### P3-3 — test profile 的不可达网关端口是「有意为之」，但注释可更显式

- **位置**：`backend/src/main/resources/application-test.yml:8-12`
- **为什么**：指向 `http://localhost:19999` 保证测试不依赖外部网关，逻辑正确；若该端口恰被占用会偶发「看似外部原因」的失败。
- **建议修法**：保留现状即可；如想更稳可注入随机高位端口。

---

## 4. 版本对齐与 ADR 0004 事实核查

### P2-5 — backend SC 2025.0.3 vs gateway-demo SC 2025.0.0：同 train 补丁差异，漂移风险低但非零

- **位置**：`backend/pom.xml:22`（`spring-cloud.version=2025.0.3`）、`gateway-demo/pom.xml:23`（`2025.0.0`）、`gateway-demo/pom.xml:30`（`spring-cloud-alibaba-dependencies 2025.0.0.0`）
- **为什么**：2025.0.3 是 2025.0.x 的补丁版（同 train 无 breaking change），但含 2025.0.0 之后的 Gateway/其他组件 bugfix；backend 与 gateway-demo 共享同一 `route_config` 表与刷新协议，若某修复改变行为（如路由刷新时序），两端版本不一致可能产生细微行为漂移。Boot 层两侧都是 3.5.16（`backend/pom.xml:10`、`gateway-demo/pom.xml:7`），对齐良好。
- **建议修法**：gateway-demo 升到 `2025.0.3` 并同步 SCA 版本（2025.0.0.0 对应 SC 2025.0.0；若 SCA 有对应补丁版一并升级），在 pom 注释或 ADR 记录「两端必须同 train」。

### P2-6 — starter artifact 差异：`spring-cloud-starter-gateway`（旧名）vs `spring-cloud-starter-gateway-server-webflux`（新名），2025.0.x 中均为 WebFlux，无架构漂移；但旧名是迁移陷阱

- **位置**：`backend/pom.xml:41`（`spring-cloud-starter-gateway-server-webflux`）、`gateway-demo/pom.xml:39`（`spring-cloud-starter-gateway`）
- **为什么**：Spring Cloud 2025.0（Northfields）把 Gateway 模块改名，引入 `-server-webflux` / `-server-webmvc` 两个新 starter，旧 `spring-cloud-starter-gateway` 保留为 **WebFlux 别名**（来源：Spring Cloud 2025.0 Release Notes wiki、OpenRewrite「Migrate to New Spring Cloud Gateway Modules and Starters」recipe——二者在 2025.0.x 解析到同一 WebFlux 实现，**无行为差异**）。但按 ADR 0004 所述 2025.1（Oakwood）起默认方向转向 WebMVC，届时 `spring-cloud-starter-gateway` 的含义可能翻转——**盲目升级 gateway-demo 会踩 WebFlux→WebMVC 的坑**。
- **建议修法**：gateway-demo 改用新名 `spring-cloud-starter-gateway-server-webflux`，并在 ADR 0004 补一句「2025.1/Oakwood 起旧 starter 默认变体变化，升级前先改名」；评审确认 ADR 0004:3 对两变体（WebFlux/WebMVC）的表述与事实一致。

### 事实核查 — ADR 0004「Boot 3.5 的 OSS 支持期已于 2026-06 结束」**属实**

- **位置**：`docs/adr/0004-spring-boot-3-5-spring-cloud-2025-0-webflux.md:5`
- **核查结论**：Spring Boot 3.5.x 的 **OSS（社区开源）支持于 2026-06-30 结束**，与 ADR 陈述一致；商业 Extended Support 延续至 2028-06-30。来源：
  - [endoflife.date/spring-boot](https://endoflife.date/spring-boot)（3.5：OSS 2026-06-30 / extended 2028-06-30）
  - [StackOverflow「OSS support for Spring Boot 3.5.x ended on 2026-06-30」](https://stackoverflow.com/questions/79981421/oss-support-for-spring-boot-3-5-x-ended-on-2026-06-30-message-doesnt-go-away)（官方横幅文案佐证）
  - 辅助：[Spring Boot 3 EOL to 4 升级手册](https://loiane.com/2026/04/spring-boot-3-eol-to-4-upgrade-playbook-jackson-3/)、[danvega.dev 3.x EOL 综述](https://www.danvega.dev/blog/spring-boot-end-of-life)
- **附带结论**：项目当前钉在 Boot 3.5.16（`backend/pom.xml:10`）——OSS 补丁已停止（2026-06-30 后无免费安全补丁）。对学习/演示项目 ADR 已接受该风险；若交付物要长期运行，应把「迁移 Boot 4 / SC 2025.1（Oakwood）」列入计划（ADR 0004:5 已表态待生态成熟再评估，方向正确）。

### 附带核查 — 版本线事实（用于对齐判断）

- Spring Cloud 2025.0.0（Northfields）2025-05-29 发布，配套 Boot 3.5.x：[spring.io 发布博客](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable)、[2025.0 Release Notes wiki](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes)
- Spring Cloud 2025.0.3 已发布（2026 年，Northfields 补丁线）：[devbytes 报道](https://devbytes.co.in/news/spring-cloud-202503-has-been-released)
- 因此 backend 的 2025.0.3 与 ADR 0004「2025.0.x（Northfields）」一致；gateway-demo 的 2025.0.0 属同 train 旧补丁。

---

## 5. 文档交付准确性（README 与工程实际对照）

核对结论：**README 快速开始 / Docker / 配置项与工程实际整体一致**，以下为核对明细与少量偏差。

### 核对一致（逐项）

- 预置账号 `admin/admin123 (ADMIN)`、`viewer/viewer123 (VIEWER)`：`README.md:97-100` ↔ `backend/.../config/SeedDataInitializer.java:38-47`（admin/viewer 落库）✅
- 「首次启动自动建表（Flyway）、创建预置账号和 2 条 httpbin 示例路由」：`README.md:74` ↔ `backend/src/main/resources/db/migration/V1__init.sql`（存在）+ `SeedDataInitializer.java:50-58`（httpbin-get、httpbin-anything 两条）✅
- 后端默认 8080 / 前端 5173 / `/api` 代理到 8080：`README.md:83,106` ↔ `backend/src/main/resources/application.yml:15-16`、`frontend/vite.config.ts:14-18`（默认 `http://localhost:8080`，`VITE_PROXY_TARGET` 可覆盖）✅
- 默认 dev profile（MySQL）：`README.md:71` ↔ `application.yml:5`（`spring.profiles.default: dev`）✅
- 配置项表（`README.md:102-110`）的 JWT 默认/有效期、dev 数据源、local H2：与 `application.yml:27-28`、`application-dev.yml:3-5`、`application-local.yml:5` 一致 ✅
- Docker 端口（前端 8088、后端 8080）：`README.md:149` ↔ `docker/docker-compose.yml:33,43` ✅
- gateway-demo 端口 8088、需 Nacos 127.0.0.1:8848：`README.md:92` ↔ `gateway-demo/src/main/resources/application.yml:2,15` ✅
- 测试命令 `cd backend && mvn test`（H2）：`README.md:139` ↔ 测试类 `@ActiveProfiles("test")` ✅；`cd frontend && npm test` ↔ `frontend/package.json:10`（vitest run）✅
- 「把 `DbRouteDefinitionLocator` 等 6 个类放入网关工程」（`README.md:27`）↔ `gateway-demo/src/main/java/com/example/gatewaydemo/route/` 恰有 6 个类（`DbRouteDefinitionLocator`、`RouteConfigRow`、`RouteRefreshController`、`RouteRefreshPublisher`、`RouteSyncProperties`、`RouteSyncScheduler`）✅

### 偏差 / 缺口

### P3-6 — README Docker 措辞「docker/ 提供 … Dockerfile」与目录实际不符

- **位置**：`README.md:145`
- **为什么**：`docker/` 目录内**只有** `docker-compose.yml`（`docker/` 目录清单），Dockerfile 实际在 `backend/Dockerfile`、`frontend/Dockerfile`；「提供编排文件与 Dockerfile」的措辞会误导读者去 `docker/` 找 Dockerfile。
- **建议修法**：改为「`docker/docker-compose.yml` 编排，Dockerfile 位于 backend/ 与 frontend/ 各模块目录」。

### P3-7 — README 不提及 startup.sh / 一键启动（见 P3-4）

- **位置**：`README.md:47-100`（快速开始全部为手动流程）
- **建议修法**：见 P3-4（纳入仓库并引用，或明确「仅本地」）。

---

## 发现清单（速览）

| 级别 | 编号 | 一句话 | 主位置 |
|---|---|---|---|
| P1 | P1-1 | 前端 Docker 构建：缺 .dockerignore，`COPY . .` 覆盖 Linux node_modules，macOS 宿主构建大概率失败（未实机构建验证） | frontend/Dockerfile:3-6 |
| P1 | P1-2 | 生产化缺口：无 prod profile，JWT/DB 密钥口令明文入库并硬编码进 compose | application.yml:27、application-dev.yml:3-5、docker-compose.yml:8-31 |
| P2 | P2-1 | compose 缺 gateway-demo（无其 Dockerfile + 依赖 Nacos 未提供），外部网关联动不在 Docker 交付范围且未明示 | docker/docker-compose.yml、gateway-demo/.../application.yml:15 |
| P2 | P2-2 | 版本漂移：gateway-demo SC 2025.0.0 vs backend 2025.0.3（同 train，低风险但非零） | gateway-demo/pom.xml:23、backend/pom.xml:22 |
| P2 | P2-3 | startup.sh 强杀端口进程、无 MySQL 预检、FRONTEND_PORT 不可覆盖 | startup.sh:51-77,106-127,39-40 |
| P2 | P2-4 | 后端容器无 HEALTHCHECK / 编排无自愈与探针、actuator 仅 health | backend/Dockerfile:8-12、docker-compose.yml:22-36、application.yml:18-22 |
| P2 | P2-5 | starter 旧名 `spring-cloud-starter-gateway`（gateway-demo）是 2025.1 迁移陷阱，建议改名 + ADR 补注 | gateway-demo/pom.xml:39、docs/adr/0004:3 |
| P3 | P3-1~P3-7 | 硬编码 jar 名/浮动 tag；compose 密钥字面值；H2 AUTO_SERVER；脚本未入库；README Docker 措辞等 | 见各条 |
| 事实 | — | ADR 0004「Boot 3.5 OSS 已于 2026-06 结束」**属实**（2026-06-30，extended 至 2028-06-30） | docs/adr/0004-...md:5 |

---

## 交付物总体结论

- **可交付性**：后端 Dockerfile（多阶段、依赖预取、跳过测试）与 compose 主干结构成立，**但必须先修 P1-1 再做实机构建验证**；所有 Docker 结论均为静态推断，需在有机器的环境跑一次 `docker compose up -d --build` 复核。
- **一致性**：README ↔ 工程实际核对高度一致（唯一实质偏差是 P3-6 的 Dockerfile 位置措辞）。
- **生产化**：缺 prod profile、密钥管理、日志与监控（P1-2/P2-4）——若「交付」指可运行 demo，则 P1-2 可降级为 P2；若指可上线，则 P1-2 是阻塞项。
