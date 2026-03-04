# 截图命名规范与归档目录约定（release-precheck / triplet-baseline）

## 1. 目标与适用范围

- 统一仓库在“分支保护启用、PR 检查验证、发布前回归”过程中的截图留痕口径，确保可检索、可审计、可复核。
- 适用于以下场景：
  - 管理员启用/巡检 required checks；
  - PR 验收 `release-precheck-minimal` 与 `triplet-baseline-pr-check`；
  - 发布前执行 `release-precheck.bat` 并留存结果证据。

## 2. 命名模板（强制）

### 2.1 文件名模板

```text
{日期}_{仓库}_{分支}_{阶段}_{截图位}_{结果}_{操作者}.png
```

字段说明：

- `日期`：`YYYYMMDD`，例如 `20260304`
- `仓库`：仓库短名，建议 `todolist`
- `分支`：分支名标准化（`/` 替换为 `-`），例如 `main`、`feature-triplet-rule`
- `阶段`：固定值之一：`ruleset` / `pr-checks` / `artifact` / `release-precheck`
- `截图位`：与清单中的截图位编号一致，例如 `shot-01`
- `结果`：`pass` / `fail` / `blocked`
- `操作者`：执行人标识（工号、GitHub ID 或姓名拼音）

### 2.2 命名示例

```text
20260304_todolist_main_ruleset_shot-01_pass_adminA.png
20260304_todolist_main_pr-checks_shot-04_blocked_adminA.png
20260304_todolist_main_release-precheck_shot-06_pass_adminA.png
```

## 3. 目录层级（强制）

```text
artifacts/
  governance/
    screenshots/
      YYYY/
        YYYYMMDD/
          {branch}/
            ruleset/
            pr-checks/
            artifact/
            release-precheck/
```

约定说明：

- 根目录固定为 `artifacts/governance/screenshots/`；
- 同一天多次操作统一放在同一 `YYYYMMDD` 下，按分支再分目录；
- 不允许将截图散落在临时目录、个人桌面路径或无日期目录中。
- Windows 可使用一键脚本初始化目录：`tools/triplet-compare/init-screenshot-archive.bat`；
- 默认执行会按“当天 + 当前分支”创建 `ruleset/pr-checks/artifact/release-precheck` 四个阶段目录。

## 4. 最小留痕集（Min Evidence Set）

每次启用或巡检必须至少保留以下 6 张截图（可多不可少）：

1. `shot-01`：Rulesets/Branch protection 入口页；
2. `shot-02`：目标分支与 PR 必需项勾选状态；
3. `shot-03`：required checks 已包含两项检查；
4. `shot-04`：PR Checks 面板状态（含 pass/blocked 证据）；
5. `shot-05`：Artifacts 列表与 `triplet-diff` 文件；
6. `shot-06`：本地或 CI 执行 `release-precheck` 的通过结果。

补充要求：

- `shot-04` 在 baseline 变更场景建议同时保留“未勾选被阻断”与“勾选后通过”两张对照图；
- 截图需保留完整浏览器地址栏/页面标题或终端窗口标题，避免无法确认来源页面；
- 图片格式建议 `png`，禁止二次压缩导致关键信息不可读。

## 5. 审计清单（提交/巡检时逐项核对）

- [ ] 文件名符合模板，字段完整且可读。
- [ ] 路径位于 `artifacts/governance/screenshots/YYYY/YYYYMMDD/{branch}/...`。
- [ ] 最小留痕集 `shot-01`~`shot-06` 已齐全。
- [ ] `shot-03` 可清晰识别 required checks 两项名称。
- [ ] `shot-04` 可清晰识别对应 PR 与检查结果状态。
- [ ] `shot-05` 可清晰识别 artifact 名称 `triplet-diff` 与目标文件路径。
- [ ] `shot-06` 可清晰识别 `release-precheck` 执行结果（PASS/FAIL）。
- [ ] 留痕日期与对应 PR/发布窗口时间一致。

## 6. 与现有文档映射关系

- 管理员启用步骤与截图位定义：`tools/triplet-compare/REPO-ADMIN-5MIN-ENABLE-CHECKLIST.md`
- 本地/CI 一致性执行规则：`tools/triplet-compare/CI-SYNC-RULES.md`
- triplet 脚手架入口：`tools/triplet-compare/USAGE.md`
