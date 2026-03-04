# Roadmap

本文仅包含未来规划中的两个方向：

- Phase 4：游戏化功能
- 命令系统

## Stable/Progressive/Deferred 支持矩阵

| 矩阵层级 | 版本范围 | 加载器组合 | 策略说明 |
|---|---|---|---|
| Stable | `1.20.1` | Fabric + Forge | 当前首要交付基线，优先保证功能闭环与回归稳定 |
| Stable | `1.21.1` | Fabric + NeoForge | 下一主线基线，作为 1.21 系列扩展起点 |
| Progressive | `1.20.x` | Fabric + Forge | 按子版本分批纳入，逐批执行差异评估与回归 |
| Progressive | `1.21.x` | Fabric + NeoForge | 按子版本分批纳入，并执行阈值评分决定是否拆簇 |
| Deferred | `1.21.x` | Forge | 同期暂缓，避免与 NeoForge 双轨重复投入 |

## 本轮按阶段执行记录（2026-03-03）

- 阶段 1（`1.20.1` / Fabric + Forge）：已完成执行边界与验收口径对齐，当前以“最小可用闭环”作为后续实施基线。
- 阶段 2（`1.20.x` / Fabric + Forge）：已固化“候选筛选 → 差异预扫 → 最小适配 → 回归验证 → 矩阵入库 → 合并发布”流程。
- 阶段 3（`1.21.1` / Fabric + NeoForge）：已确认迁移边界与退出条件，明确不并行维护 `1.21.x` Forge 轨道。
- 阶段 4（`1.21.x` / Fabric + NeoForge）：已落地阈值触发与差异簇拆分规则，作为子版本扩展时的默认治理机制。

## 命令系统

- 提供 `/todo` 命令入口，覆盖个人项目与团队项目的常用操作
- 代表性能力点：
  - 快速添加：`/todo add <标题> [描述] [标签]`
  - 查询列表：`/todo list`（支持筛选/搜索参数）
  - 完成/删除：`/todo complete <ID>`、`/todo delete <ID>`、`/todo clear`
  - 团队协作相关操作在服务端进行权限校验，并与现有同步流程一致
- CommandBootstrap 推进记录：
  - 第十一批（2026-03-04）：已定义并锁定命令权限语义映射 `VIEW / EDIT / PROJECT_ADMIN / HUD_CONTROL / ADMIN` 到命令入口；新增统一权限校验辅助方法与 `PERMISSION_DENIED` 拒绝返回；完成 `task / project / hud / join` 相关命令接入。
  - 第二十批（2026-03-04）：已新增 PR 阻断工作流 `triplet-baseline-pr-check.yml`；当 PR 修改 `tools/triplet-compare/*baseline*.json` 时，强制校验 PR 正文“triplet 基线更新”清单勾选状态，不满足即失败阻断；并同步更新 triplet 文档入口与流程规则。
  - 第二十一批（2026-03-04）：已新增分支保护落地手册 `tools/triplet-compare/BRANCH-PROTECTION-RUNBOOK.md`；定义 `main` 分支必须通过状态检查 `release-precheck-minimal` 与 `triplet-baseline-pr-check`；补充 GitHub Rulesets/Branch protection 配置步骤与常见误区，并同步更新 `USAGE.md`、`CI-SYNC-RULES.md`。
  - 第二十三批（2026-03-04）：已新增截图命名规范与归档目录约定文档 `tools/triplet-compare/SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`；统一命名模板、归档目录层级、最小留痕集与审计清单；并同步更新 `USAGE.md`、`CI-SYNC-RULES.md`、`REPO-ADMIN-5MIN-ENABLE-CHECKLIST.md`。
  - 第二十四批（2026-03-04）：已新增截图留痕目录初始化脚本 `tools/triplet-compare/init-screenshot-archive.bat`（调用 `init-screenshot-archive.ps1`）；支持一键生成“当日 + 当前分支 + 四阶段”归档目录，并支持传入分支与日期参数；同步更新 `USAGE.md`、`SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`、`REPO-ADMIN-5MIN-ENABLE-CHECKLIST.md`。

## Phase 4：游戏化功能

- 任务书物品：以物品形式承载任务列表，手持/右键打开 GUI，并可用于分享/交付任务
- 任务奖励系统：完成任务获得经验、物品或计分板点数；支持可配置的奖励规则
- 成就与里程碑：按任务完成数量、连续完成天数、特定类型目标解锁徽章/称号
- 公共任务板：与告示牌/特定方块集成，展示团队任务、公告与进度
- 通知与反馈：在聊天栏或屏幕提示中展示领取/指派/完成等关键事件
- 自动任务与进度追踪：基于游戏事件生成任务，自动判定完成条件并展示实时进度
- 数据与统计面板：查看完成趋势、常见任务类型与个人/团队贡献概览
