# wayfinder 本地 Markdown tracker 约定

本目录是 spring-cloud-gateway-dashboard 上「全面评审报告（六维 + 动手验证）」effort 的 wayfinder 地图（本地 Markdown 形态，未接入 GitHub issues）。

## 结构

```
wayfinder/
├── map.md               # 地图（唯一权威索引：Destination / Notes / Decisions so far / Not yet specified / Out of scope）
├── tickets/             # 子 ticket，每张一个文件：NNN-<slug>.md
└── findings/            # research 子代理的完整发现（在一次性 research/<slug> 分支上提交，不并入 main）
```

## 文件头约定

- `Labels: wayfinder:map` / `wayfinder:research` / `wayfinder:task`（Markdown 模拟标签）
- `Status: open` → `claimed`（须写 `Claimed by: <agent>`）→ `closed`
- `Blocked by: <ticket 名称>` / `Blocks: <ticket 名称>`——正文阻塞约定（Markdown 无原生依赖；frontier 渲染依赖它）

## 术语

- **frontier** = `Status: open` 且未认领且所有 `Blocked by` 已关闭的 ticket——可领取的工作前沿。
- **认领** = 在 ticket 上写 `Claimed by:` 并置 `Status: claimed`（先认领后干活，避免并行会话重复）。
- **决议** = 关闭时在 ticket 追加 `## Resolution` 一节（答案 + findings 指针），`Status` 置 `closed`，并在 `map.md` 的 Decisions so far 追加一行。
- 地图的 Not yet specified 是 fog——写不满的待定问题；Out of scope 是已排除项，永不毕业。

## research findings 流程

1. 从 `main` 切一次性分支：`git checkout -b research/<slug>`。
2. 完整发现写入 `wayfinder/findings/<slug>.md`（单一文件、逐条标注来源）。
3. 只显式 `git add wayfinder/findings/<slug>.md` 后提交（**不要** `git add -A`，工作区可能有他人未提交的改动）。
4. ticket 的 Resolution 记录结论摘要 + 分支名/commit。
