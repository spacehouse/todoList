# 分支保护落地手册（release-precheck / triplet-baseline）

## 1. 目标

为 `main` 分支建立可执行、可审计的合并门禁，确保以下两项状态检查通过后才允许合并：

- `release-precheck-minimal`
- `triplet-baseline-pr-check`

上述检查分别用于：

- 发布前统一检查（`build -> triplet 回归`）
- baseline 变更时 PR 清单勾选阻断

## 2. 前置条件

- 仓库已存在工作流：
  - `.github/workflows/release-precheck-minimal.yml`
  - `.github/workflows/triplet-baseline-pr-check.yml`
- 至少有一次 PR 或手动触发，能在仓库中看到对应检查记录（避免“找不到可选检查项”）。
- 具备仓库管理员权限（`Admin`）或可编辑 Rulesets/Branch protection 的权限。

## 3. GitHub 仓库配置步骤（推荐：Rulesets）

1. 进入仓库 `Settings -> Rules -> Rulesets`。
2. 点击 `New ruleset`，选择 `Branch` 类型。
3. `Target branches` 选择 `main`（或你的默认发布分支）。
4. 在规则中启用：
   - `Require a pull request before merging`
   - `Require status checks to pass`
5. 在 `Required status checks` 中添加：
   - `release-precheck-minimal`
   - `triplet-baseline-pr-check`
6. 如仓库要求线性历史/审批数，可按组织策略额外启用：
   - `Require approvals`
   - `Require conversation resolution before merging`
7. 保存并启用 Ruleset。

## 4. 兼容配置步骤（经典 Branch protection）

若仓库仍使用传统分支保护：

1. 进入 `Settings -> Branches`。
2. 在 `Branch protection rules` 为 `main` 新建或编辑规则。
3. 勾选 `Require a pull request before merging`。
4. 勾选 `Require status checks to pass before merging`。
5. 在状态检查列表中选择：
   - `release-precheck-minimal`
   - `triplet-baseline-pr-check`
6. 保存规则并在 PR 中验证阻断效果。

## 5. 验收标准

- 任一必需检查失败时，PR 显示不可合并（Merge 按钮被阻断）。
- 两项检查均通过时，满足其他策略条件后可合并。
- baseline 未变更时，`triplet-baseline-pr-check` 可表现为 `skipped` 或 `success`（以仓库策略为准）；若被标记为 required，需确认该状态在规则中被视为通过。

## 6. 常见误区与规避

1. **误区：只勾选 workflow 文件存在即可**
   - 规避：必须在分支保护里显式添加 required status checks，否则不会阻断。

2. **误区：状态检查名称与 UI 展示不一致**
   - 规避：以 PR 实际显示的检查名称为准；若仓库展示为 `workflow / job` 组合，需选择对应条目，避免仅按文件名填写。

3. **误区：先配置 required checks，但仓库尚无检查历史**
   - 规避：先运行一次工作流（`workflow_dispatch` 或提交 PR），再回到设置页选择检查项。

4. **误区：把 baseline 校验写成“总是失败”**
   - 规避：`triplet-baseline-pr-check` 应在“未修改 baseline 文件”时跳过/通过，仅在命中 baseline 变更时强校验清单。

5. **误区：本地与 CI 参数不一致导致重复争议**
   - 规避：统一通过 `release-precheck.bat` 入口，保持 `logPath/baselinePath/reportPath/ignoreOrder/gradleTask` 口径一致。

6. **误区：只看构建成功，不看 triplet 差异报告**
   - 规避：将 `build/reports/triplet-diff.json` 作为 artifact 保留并纳入审阅流程。

## 7. 推荐巡检清单（每周或每次流程变更后）

- `release-precheck-minimal.yml` 是否仍使用 JDK 17 与统一脚本入口。
- `triplet-baseline-pr-check.yml` 的触发事件是否覆盖 `opened/edited/synchronize/reopened/ready_for_review`。
- PR 模板中的“triplet 基线更新”勾选项是否与校验脚本同步。
- 文档入口是否保持一致：`USAGE.md`、`CI-SYNC-RULES.md`、`BASELINE-UPDATE-FLOW.md`。
