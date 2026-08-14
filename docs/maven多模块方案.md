# Maven 多模块方案：根主项目管理 backend / frontend / gateway-demo

> **状态：✅ 决策已确认，实施中**（commit 见完成后记录）。
> 目标：在仓库根目录建立 Maven 主项目（聚合 + 父 pom），统一管理 **backend、frontend、gateway-demo** 三个子项目：
> 根目录一条 `mvn package` 可构建全部；版本统一收敛；子项目独立构建仍可用。
> 基准：main `f4abe07`。

## 0. 已确认决策

| 决策点 | 结论 |
|---|---|
| D1 主项目命名 | **gateway-dashboard**（artifactId/name） |
| D2 frontend 包装 | **exec-maven-plugin 直调本机 npm** |
| D3 根 pom 策略 | **继承 spring-boot-starter-parent 3.5.16**（Spring Boot 版本全局统一；子模块 parent 均指向根 pom） |
| D4 前端构建绑定 | **绑定 package 阶段**（根 `mvn package` 一条命令构建全部） |

---

## 1. 现状盘点

| 子项目 | 构建体系 | 现状 |
|---|---|---|
| backend | Maven（Spring Boot 3.5.16） | parent=spring-boot-starter-parent；dependencyManagement 引入 spring-cloud BOM；**⚠️ 工作区 artifactId 被误改为 `b`（未提交，需修复为 `gateway-dashboard-backend`）** |
| gateway-demo | Maven（Spring Boot 3.5.16） | parent=spring-boot-starter-parent；自带 spring-cloud 2025.0.0 + nacos 2025.0.0.0 两个 BOM |
| frontend | npm（Vue 3 + Vite） | 无 Maven 构建入口；scripts: dev/build/test/test:coverage |

**痛点**：三个项目三种入口（`cd backend && mvn`、`cd gateway-demo && mvn`、`cd frontend && npm`）；Spring Boot 版本/MP/jjwt/bucket4j 等版本分散在各子 pom；无根级统一构建与版本治理。

## 2. 目标结构

```
spring-cloud-gateway-dashboard/
├── pom.xml                 # 主项目（packaging=pom，聚合 + 父 pom）
│   ├── modules: backend / frontend / gateway-demo
│   ├── dependencyManagement: Spring Boot / Spring Cloud BOM + 常用依赖版本
│   └── build: maven-compiler（release 21）、统一属性
├── backend/
│   └── pom.xml             # parent=根 pom；保留 spring-boot-maven-plugin / jacoco
├── gateway-demo/
│   └── pom.xml             # parent=根 pom；保留 nacos BOM
├── frontend/
│   ├── pom.xml             # 新增：Maven 包装模块（exec 调 npm）
│   ├── package.json        # 不变
│   └── ...
```

## 3. 主项目 pom.xml 设计（根）

| 项 | 设计 |
|---|---|
| groupId / artifactId / packaging | `com.gatewaydashboard` / `gateway-dashboard` / `pom` |
| 模块 | `<modules>backend、frontend、gateway-demo</modules>` |
| parent 策略（D3） | **继承 `spring-boot-starter-parent:3.5.16`**（获取编译/编码/插件版本默认配置）；子模块 parent 指向根 pom |
| 版本收敛 | `<properties>` 集中：spring-cloud 2025.0.3、mybatis-plus 3.5.12、jjwt 0.12.6、bucket4j 8.10.1、jacoco 0.8.13、java.version 21；dependencyManagement import spring-cloud-dependencies BOM + 管理 jjwt/bucket4j/MP/jsqlparser 版本 |
| 编译 | 继承 starter-parent 后 `java.version=21` 属性即生效（maven.compiler.source/target） |

## 4. 子项目改造

### 4.1 backend/pom.xml

- `<parent>`：`spring-boot-starter-parent` → **根 pom**（relativePath `../pom.xml`）
- 删除自身 `<dependencyManagement>`（spring-cloud BOM 移至根）
- 修复 artifactId：`b` → **`gateway-dashboard-backend`**
- 保留：spring-boot-maven-plugin（repackage）、jacoco（report + check 绑定 verify）
- 依赖 version 清理：由根 BOM/属性管理（MP/jjwt/bucket4j 的 `<version>` 移除，由根 properties 或 dependencyManagement 提供）

### 4.2 gateway-demo/pom.xml

- `<parent>`：`spring-boot-starter-parent` → 根 pom
- `dependencyManagement`：删除 spring-cloud BOM（根已 import 2025.0.3）；**保留 nacos BOM**（仅 gateway-demo 使用）
- 版本同步：spring-cloud 2025.0.0 → 根统一 2025.0.3（消除两端漂移，评审 P2-2 遗留）
- 保留 spring-boot-maven-plugin（repackage）

### 4.3 frontend/pom.xml（新增，Maven 包装模块）

- packaging=`pom`（纯构建编排，无 Java 代码）
- 用 **exec-maven-plugin**（见 D2）包装 npm：
  | 阶段 | 动作 |
  |---|---|
  | generate-resources | `npm ci --no-audit --no-fund`（按 lockfile 安装） |
  | compile | `npm run build`（vue-tsc 类型检查 + vite 构建，产物进 frontend/dist） |
  | test | `npm test`（vitest）——**注意**：与根 `mvn package` 的 skipTests 联动可选 |
- 说明：exec 直调本机 node/npm（本仓库开发环境即 macOS 本机）；CI/跨平台如需自带 node 可换 frontend-maven-plugin（见 D2）

## 5. 构建命令变化

| 场景 | 命令 | 说明 |
|---|---|---|
| 全量构建 | `mvn package`（根） | 后端 jar + 网关 jar + 前端 dist |
| 全量测试 | `mvn test`（根） | 含前端 vitest（若绑定 test 阶段） |
| 仅后端 | `mvn -pl backend -am verify` | `-am` 连带父 pom |
| 子目录独立构建 | `cd backend && mvn verify` | parent 通过 relativePath 解析，无需先 install 根 |
| 既有命令不变 | startup.sh / README | `cd backend && mvn spring-boot:run` 等仍可用 |
| 前端开发 | `cd frontend && npm run dev` | 不受影响（Maven 仅多一条构建通道） |

## 6. 风险与对策

| # | 风险 | 对策 |
|---|---|---|
| R1 | 不继承 spring-boot-starter-parent 后，其默认配置（encoding/resource filtering/插件版本）丢失 | 根 pom 显式补：`project.build.sourceEncoding=UTF-8`、compiler `<release>21</release>`、插件版本由 spring-boot-dependencies BOM 管理；实施后以现有 38 测全绿验证 |
| R2 | frontend 模块 `mvn clean`/`package` 误删/重复构建 | 绑定仅 generate-resources（npm ci）+ compile（build）；clean 不映射 npm（避免删 node_modules）；CI 无 node 时可 `-pl !frontend` 排除或 profile 隔离 |
| R3 | gateway-demo 升 2025.0.3 与 nacos BOM 兼容性 | 2025.0.0→2025.0.3 同 train 小版本；以 gateway-demo 9 测验证（不启动 Nacos 的单测不受影响） |
| R4 | 子模块独立构建时 parent 解析 | relativePath `../pom.xml` 默认即可；不依赖本地 install |
| R5 | 工作区已存在的 artifactId=`b` 误改 | 本方案一并修复为 `gateway-dashboard-backend`，并核对打包产物名（backend-0.1.0-SNAPSHOT.jar） |

## 7. 验收标准

- 根目录 `mvn package` 成功：backend jar（gateway-dashboard-backend-0.1.0-SNAPSHOT.jar）+ gateway-demo jar + frontend dist 产出
- `mvn -pl backend verify` 全绿（38 测 + JaCoCo 门槛）
- `mvn -pl gateway-demo test` 全绿（9 测）
- frontend 模块触发 `npm ci + npm run build` 成功（或按 D4 阶段绑定）
- `cd backend && mvn test`（子目录独立）仍可用
- 版本单一事实源：Spring Boot/MP/jjwt/bucket4j 等在根 pom 出现一次

---

## 决策点（需确认）

- **D1 主项目命名**：artifactId/name 用 `gateway-dashboard`（与仓库名一致，推荐）；还是按你消息中的字面 `gateway-dashboard-dashboard`？
- **D2 frontend 的 Maven 包装**：exec-maven-plugin 直调本机 npm（推荐，轻量、无额外下载）；还是 frontend-maven-plugin（自动下载固定版本 node，CI/跨平台更稳、但重且首次要下 node 包）？
- **D3 根 pom 的 parent 策略**：不继承 spring-boot-starter-parent、BOM import 管理版本（推荐，多模块标准做法，根 pom 可控）；还是根 pom 直接继承 spring-boot-starter-parent（少配一些默认项，但 Spring Boot 版本与根 pom 强绑定）？
- **D4 前端构建的 Maven 阶段绑定**：绑定到 `package` 阶段（根 `mvn package` 一条命令构建全部，推荐）；还是独立 profile（如 `-Pbuild-frontend` 才构建前端，默认跳过）？
