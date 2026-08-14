# 交付物与部署评审

Labels: wayfinder:research
Status: closed
Claimed by: wayfinder research 子代理（d72d3302-b53d-47bc-a719-efe6bf9f5a72）

## Question

评审交付物与部署（**本机无 Docker**——docker 编排只能静态审阅，所有相关结论标注「未实机构建验证」）：

1. **Docker 交付物**：`docker/` 下编排文件与 Dockerfile——镜像基础、多阶段构建、健康检查、环境变量注入、前后端联动（前端 Nginx 反代）、**为何缺少 gateway-demo 服务**、有无明显配置错误（README 自述「未在本机验证」）。
2. **启动脚本**：`startup.sh` 的行为、健壮性、假设（MySQL 依赖、端口）。
3. **配置 profile**：`dev`（MySQL 明文密码）/ `local`（H2 文件库）/ `test`（H2 + 不可达外部网关）——profile 用途是否清晰、生产化缺口（密钥、日志、监控）。
4. **版本对齐**：backend Spring Cloud 2025.0.3 vs gateway-demo 2025.0.0 及 starter artifact 差异是否会造成行为漂移；ADR 0004 中「Boot 3.5 OSS 生命周期」的说法是否属实（2026-06 结束社区支持？）。
5. **文档交付准确性**：README「快速开始 / Docker / 配置项」与工程实际是否一致。

产出：交付物判断 + 发现列表（每条标 P0-P3、位置、为什么、建议修法）。已知疑点供核查：docker-compose 未验证、无 gateway-demo 服务、明文 DB 密码、版本不一致。

## Blocking

Blocked by: 无
Blocks: 合成评审报告

## Resolution

**结论：无 P0，但交付物是六维中最不成熟的一维**——全部 Docker 结论标注「未实机构建验证」（本机无 Docker），README「交付参考，未验证」属实。

**P1（2）**：
- P1-1 前后端 Dockerfile 均缺 `.dockerignore`：`COPY . .` 会用宿主 macOS 的 node_modules（~160M）覆盖容器内 Linux 依赖，macOS 宿主构建大概率失败——**交付物当前很可能不可构建**（未实机构建验证）
- P1-2 无 prod profile：JWT 默认密钥明文（application.yml:27）、MySQL 口令明文（application-dev.yml:3-5、docker-compose.yml:8-31、gateway-demo application.yml:8-10）

**P2（5）**：
- P2-1 compose 无 gateway-demo 服务（无其 Dockerfile、依赖的 Nacos 未提供），外部网关联动不在 Docker 交付范围且文档未明示
- P2-2 backend SC 2025.0.3 vs gateway-demo 2025.0.0 同 train 补丁漂移；P2-5 `spring-cloud-starter-gateway`（旧名别名）vs `-server-webflux` 在 2025.0.x 同为 WebFlux 无架构差异，但 2025.1/Oakwood 起旧名含义翻转是迁移陷阱
- P2-3 startup.sh 强杀端口进程、无 MySQL 预检、FRONTEND_PORT 不可覆盖；P3-4 脚本被 .gitignore 忽略未入库、README 不引用
- P2-4 后端容器无 HEALTHCHECK/自愈，actuator 仅 health

**P3（7，详见文件）**：README 仅 P3-6（Dockerfile 实际不在 docker/ 目录，README:145 措辞）与 P3-7（不提 startup.sh）两处措辞不符，其余预置账号/示例路由/端口/profile/配置项均一致。

**事实核查**：ADR 0004「Boot 3.5 OSS 已于 2026-06 结束」**属实**（2026-06-30，商业 extended 至 2028-06-30；来源 endoflife.date、StackOverflow、spring.io 2025.0.0 博客、SC Release Notes、OpenRewrite recipe）。

**完整发现**：`wayfinder/findings/deliverables-deploy-review.md`（195 行，逐条 文件:行号），一次性分支 `research/deliverables-deploy-review`，commit `7648d66`（未并入 main）。
