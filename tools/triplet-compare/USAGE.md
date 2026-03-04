# COMMAND_RESULT_TRIPLET 自动比对脚手架使用说明

## 1. 目标

用于解析日志中的 `COMMAND_RESULT_TRIPLET` 行，并自动对比以下三元组字段差异：

- `code`
- `messageKey`
- `sideEffects`

该脚手架仅做日志解析与离线比对，不修改任何业务逻辑。

## 2. 文件清单

- `tools/triplet-compare/compare-command-result-triplet.ps1`：比对脚本
- `tools/triplet-compare/baseline-sample.json`：基线样本（可复制后按项目实际维护）
- `tools/triplet-compare/log-sample.txt`：示例日志（可用于快速验证一键入口）
- `tools/triplet-compare/BASELINE-UPDATE-FLOW.md`：基线更新流程（允许/禁止场景、审阅清单、提交模板、回退策略）

## 2.1 文档入口

- 基线更新流程：`tools/triplet-compare/BASELINE-UPDATE-FLOW.md`
- CI 同步规则：`tools/triplet-compare/CI-SYNC-RULES.md`
- 分支保护落地手册：`tools/triplet-compare/BRANCH-PROTECTION-RUNBOOK.md`
- 仓库管理员 5 分钟启用清单：`tools/triplet-compare/REPO-ADMIN-5MIN-ENABLE-CHECKLIST.md`
- 截图命名规范与归档目录约定：`tools/triplet-compare/SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`
- PR 模板（基线变更勾选项）：`.github/pull_request_template.md`
- PR 阻断工作流：`.github/workflows/triplet-baseline-pr-check.yml`

## 3. 日志格式要求

脚本会匹配包含如下片段的行（顺序需一致）：

```text
COMMAND_RESULT_TRIPLET code=<int> messageKey=<key> sideEffects=[A,B,...]
```

示例：

```text
[Server thread/INFO] COMMAND_RESULT_TRIPLET code=1 messageKey=command.todolist.task.done.success sideEffects=[PERSIST_DATA,REFRESH_HUD]
```

## 4. 基线格式

支持两种 JSON 结构：

1) 直接数组：

```json
[
  { "code": 1, "messageKey": "command.todolist.result.none", "sideEffects": ["NONE"] }
]
```

2) 对象包裹（推荐）：

```json
{
  "triplets": [
    { "code": 1, "messageKey": "command.todolist.result.none", "sideEffects": ["NONE"] }
  ]
}
```

## 5. 执行方式

### 5.1 Windows 一键回归入口（推荐）

在项目根目录执行（仅需传入日志路径）：

```powershell
.\tools\triplet-compare\run-triplet-regression.bat .\run.log
```

快速验证示例：

```powershell
.\tools\triplet-compare\run-triplet-regression.bat .\tools\triplet-compare\log-sample.txt
```

可选参数（按顺序）：

1. `logPath`（必填）：日志路径
2. `baselinePath`（可选）：基线 JSON 路径，默认 `tools/triplet-compare/baseline-sample.json`
3. `reportPath`（可选）：报告输出路径，默认 `build/reports/triplet-diff.json`
4. `ignoreOrder`（可选）：`true/false`，`true` 时按“签名+次数”忽略顺序比对

完整示例：

```powershell
.\tools\triplet-compare\run-triplet-regression.bat `
  .\run.log `
  .\tools\triplet-compare\baseline-sample.json `
  .\build\reports\triplet-diff.json `
  true
```

### 5.2 直接调用 PowerShell 脚本

在项目根目录执行：

```powershell
.\tools\triplet-compare\compare-command-result-triplet.ps1 `
  -LogPath .\run.log `
  -BaselinePath .\tools\triplet-compare\baseline-sample.json
```

输出 JSON 差异报告：

```powershell
.\tools\triplet-compare\compare-command-result-triplet.ps1 `
  -LogPath .\run.log `
  -BaselinePath .\tools\triplet-compare\baseline-sample.json `
  -ReportPath .\build\reports\triplet-diff.json
```

忽略顺序（按“签名+次数”比对）：

```powershell
.\tools\triplet-compare\compare-command-result-triplet.ps1 `
  -LogPath .\run.log `
  -BaselinePath .\tools\triplet-compare\baseline-sample.json `
  -IgnoreOrder
```

## 6. 退出码约定

- `0`：PASS，无差异
- `1`：FAIL，存在差异
- `2`：执行失败（文件不存在、JSON 格式错误等）

## 7. 建议落地流程

1. 先收集一轮稳定日志，提取目标命令路径的三元组作为初始基线。
2. 后续每次改动后重复采集日志并运行脚本。
3. 当脚本返回 `1` 时，查看控制台表格或 `-ReportPath` 报告定位差异。

## 8. 失败排查说明

1. 返回码 `2` 且提示 `Log file not found`
   - 检查日志路径是否正确，建议使用绝对路径或在项目根目录执行命令。
2. 返回码 `2` 且提示 `Baseline file not found`
   - 检查基线文件是否存在；一键入口默认使用 `tools/triplet-compare/baseline-sample.json`。
3. 返回码 `2` 且提示 `Invalid baseline JSON`
   - 检查 JSON 是否为“数组”或 `{ "triplets": [...] }` 结构，确认字段名为 `code/messageKey/sideEffects`。
4. 返回码 `1`（存在差异）
   - 先查看控制台差异表，再打开报告 JSON，重点核对 `position/field/baseline/actual`。
5. 提示 PowerShell 执行策略限制
   - 优先使用 `run-triplet-regression.bat`（已内置 `-ExecutionPolicy Bypass`），避免本地策略阻断。
6. 报告文件未生成
   - 检查 `reportPath` 父目录写权限；脚本会自动创建目录，但无权限时会失败并返回 `2`。

## 9. GitHub Actions 最小模板

- 可直接复用模板：`.github/workflows/release-precheck-minimal.yml`。
- 该模板在 Windows Runner 上直接调用：
  - `release-precheck.bat`
  - `tools/triplet-compare/log-sample.txt`（示例日志，可替换为实际日志产物）
  - `tools/triplet-compare/baseline-sample.json`
  - `build/reports/triplet-diff.json`
- 模板已内置 artifact 上传：`build/reports/triplet-diff.json`（artifact 名称 `triplet-diff`）。

## 10. 基线 PR 勾选项自动校验（第二十批）

- 仓库新增工作流：`.github/workflows/triplet-baseline-pr-check.yml`。
- 当 PR 修改 `tools/triplet-compare/*baseline*.json` 时，工作流会自动检查 PR 正文是否已勾选“triplet 基线更新”清单全部必选项。
- 若未全部勾选，工作流会失败并阻断合并；若未修改 baseline 文件，则自动跳过该校验。

## 11. 分支保护落地（第二十一批）

- 仓库新增分支保护落地手册：`tools/triplet-compare/BRANCH-PROTECTION-RUNBOOK.md`。
- `main` 分支要求必须通过以下状态检查后方可合并：
  - `release-precheck-minimal`
  - `triplet-baseline-pr-check`
- 手册包含：
  - GitHub Rulesets/Branch protection 两种配置路径；
  - 必选检查项配置步骤；
  - 常见误区与规避建议（检查名不一致、无历史检查无法选择、skip 状态判定等）。

## 12. 仓库管理员 5 分钟启用清单（第二十二批）

- 仓库新增启用清单：`tools/triplet-compare/REPO-ADMIN-5MIN-ENABLE-CHECKLIST.md`。
- 清单内容覆盖：
  - 5 分钟步骤核对（含截图位说明）；
  - 验收清单（可直接勾选）；
  - 失败排查（required checks、检查名、artifact、参数一致性等）。

## 13. 截图命名规范与归档目录约定（第二十三批）

- 仓库新增截图治理文档：`tools/triplet-compare/SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`。
- 文档统一定义：
  - 文件命名模板（日期/仓库/分支/阶段/截图位/结果/操作者）；
  - 归档目录层级（`artifacts/governance/screenshots/YYYY/YYYYMMDD/{branch}/{stage}`）；
  - 最小留痕集（`shot-01` 至 `shot-06`）；
  - 审计清单（命名、路径、截图完整性、`triplet-diff` 与 `release-precheck` 结果可读性）。

## 14. 截图留痕目录初始化与当日归档一键生成（第二十四批）

- 仓库新增 Windows 入口脚本：`tools/triplet-compare/init-screenshot-archive.bat`。
- 仓库新增 PowerShell 实现：`tools/triplet-compare/init-screenshot-archive.ps1`。
- 在项目根目录一键生成“当日 + 当前分支 + 四阶段”归档目录：

```powershell
.\tools\triplet-compare\init-screenshot-archive.bat
```

- 指定分支（日期默认当天）：

```powershell
.\tools\triplet-compare\init-screenshot-archive.bat main
```

- 指定分支与日期：

```powershell
.\tools\triplet-compare\init-screenshot-archive.bat release-2026Q1 20260304
```

- 脚本会自动创建目录：`artifacts/governance/screenshots/YYYY/YYYYMMDD/{branch}/ruleset|pr-checks|artifact|release-precheck`。
