# 仓库管理员 5 分钟启用清单（release-precheck / triplet-baseline）

## 1. 适用范围

- 适用于仓库管理员首次启用或巡检以下合并门禁：
  - `release-precheck-minimal`
  - `triplet-baseline-pr-check`
- 目标是在 5 分钟内完成“配置确认 + PR 验收 + 留痕核对”。
- 留痕命名与归档必须遵循：`tools/triplet-compare/SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`。

## 2. 启用前确认（30 秒）

- 确认仓库已存在工作流：
  - `.github/workflows/release-precheck-minimal.yml`
  - `.github/workflows/triplet-baseline-pr-check.yml`
- 确认你具备仓库 `Admin` 或可编辑 Rulesets/Branch protection 的权限。
- 确认仓库近期已有至少一次 PR 检查记录（避免 required checks 下拉列表为空）。

## 3. 5 分钟步骤核对（含截图位）

### 步骤 1：打开分支规则入口（约 30 秒）

- 路径：`Settings -> Rules -> Rulesets`（推荐）；
- 若仓库仍使用旧模式，则走：`Settings -> Branches -> Branch protection rules`。
- 截图位说明：`[截图位-01：Rulesets 或 Branch protection 入口页]`。

### 步骤 2：绑定 main 分支与 PR 规则（约 60 秒）

- 目标分支选择 `main`（或你的默认发布分支）；
- 启用 `Require a pull request before merging`；
- 启用 `Require status checks to pass`。
- 截图位说明：`[截图位-02：分支目标与 PR 必需项勾选状态]`。

### 步骤 3：添加必需状态检查（约 60 秒）

- 在 `Required status checks` 中添加：
  - `release-precheck-minimal`
  - `triplet-baseline-pr-check`
- 若检查项不可选，先运行一次 PR 或手动触发工作流再返回此页面。
- 截图位说明：`[截图位-03：required checks 已包含两项检查]`。

### 步骤 4：提交保存并触发验证 PR（约 90 秒）

- 保存规则；
- 新建或更新一个测试 PR，观察 Checks 面板：
  - `release-precheck-minimal` 应执行；
  - 若未改 baseline，`triplet-baseline-pr-check` 可为 `skipped/success`；
  - 若改了 baseline 且 PR 未勾选清单，应被阻断失败。
- 截图位说明：`[截图位-04：PR Checks 面板状态]`。

### 步骤 5：核对差异报告留痕（约 60 秒）

- 在 CI 产物中确认存在 `triplet-diff` artifact；
- 下载后应包含 `build/reports/triplet-diff.json`。
- 截图位说明：`[截图位-05：Artifacts 列表与 triplet-diff 文件]`。

### 步骤 6：按规范命名并归档截图（约 30 秒）

- 按模板命名截图文件：`{日期}_{仓库}_{分支}_{阶段}_{截图位}_{结果}_{操作者}.png`；
- Windows 可先执行：`.\tools\triplet-compare\init-screenshot-archive.bat` 一键创建当日归档目录；
- 归档到目录：`artifacts/governance/screenshots/YYYY/YYYYMMDD/{branch}/{stage}`；
- 至少确保 `shot-01` 到 `shot-06` 齐全。
- 截图位说明：`[截图位-06：release-precheck PASS/FAIL 结果页]`。

## 4. 验收清单（完成即勾选）

- [ ] `main` 分支已启用 PR 合并门禁。
- [ ] required checks 已包含 `release-precheck-minimal` 与 `triplet-baseline-pr-check`。
- [ ] 测试 PR 中检查状态符合预期（失败可阻断、通过可放行）。
- [ ] baseline 变更场景可触发 PR 清单校验阻断。
- [ ] `triplet-diff` artifact 可下载且内容完整。
- [ ] 截图文件命名与归档路径符合统一规范，最小留痕集完整。

## 5. 失败排查（快速定位）

1. **找不到 required checks 选项**
   - 先触发一次对应工作流（PR 或 `workflow_dispatch`），再刷新设置页。
2. **检查名选错（UI 显示为 workflow/job 组合）**
   - 以 PR Checks 面板中的真实显示名为准，避免仅按 yml 文件名选择。
3. **baseline 未改也被阻断**
   - 检查 `triplet-baseline-pr-check.yml` 的路径匹配是否仅针对 `*baseline*.json`。
4. **本地通过、CI 失败**
   - 确认 CI 与本地都走 `release-precheck.bat` 且参数一致（`logPath/baselinePath/reportPath/ignoreOrder/gradleTask`）。
5. **没有 triplet-diff 产物**
   - 检查 `release-precheck-minimal.yml` 是否保留 artifact 上传步骤，路径是否为 `build/reports/triplet-diff.json`。
6. **截图难以审计或无法检索**
   - 检查是否按 `SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md` 执行命名模板与目录归档，重点核对 `shot-01`~`shot-06` 是否齐全。

## 6. 关联文档

- 使用说明：`tools/triplet-compare/USAGE.md`
- CI 同步规则：`tools/triplet-compare/CI-SYNC-RULES.md`
- 分支保护落地手册：`tools/triplet-compare/BRANCH-PROTECTION-RUNBOOK.md`
- 截图命名规范与归档目录约定：`tools/triplet-compare/SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`
