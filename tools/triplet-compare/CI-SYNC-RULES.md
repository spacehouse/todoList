# CI 同步规则（发布前统一检查）

## 目标

- 统一本地与 CI 的发布前阻断口径：必须按 **build -> triplet 回归** 顺序执行。
- 任一步失败即终止流水线并返回非 0，禁止进入发布阶段。

## 文档入口

- triplet 使用说明：`tools/triplet-compare/USAGE.md`
- triplet 基线更新流程：`tools/triplet-compare/BASELINE-UPDATE-FLOW.md`
- 分支保护落地手册：`tools/triplet-compare/BRANCH-PROTECTION-RUNBOOK.md`
- 仓库管理员 5 分钟启用清单：`tools/triplet-compare/REPO-ADMIN-5MIN-ENABLE-CHECKLIST.md`
- 截图命名规范与归档目录约定：`tools/triplet-compare/SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`
- PR 模板（基线变更勾选项）：`.github/pull_request_template.md`
- PR 阻断工作流：`.github/workflows/triplet-baseline-pr-check.yml`

## 统一执行入口

- Windows 环境统一调用仓库根目录脚本：`release-precheck.bat`。
- 禁止在 CI 中分散拼接“构建 + triplet 回归”命令，避免参数漂移与行为不一致。

## 必选参数与默认参数

- 必选参数：`logPath`（包含 `COMMAND_RESULT_TRIPLET` 的日志文件路径）。
- 默认参数：
  - `baselinePath`: `tools/triplet-compare/baseline-sample.json`
  - `reportPath`: `build/reports/triplet-diff.json`
  - `ignoreOrder`: `false`
  - `gradleTask`: `build`

## 退出码约定

- `0`：build 与 triplet 回归全部通过。
- 非 `0`：任一步失败（包括参数错误、脚本缺失、构建失败、triplet 差异失败）。

## CI 编排要求

- 先产出日志，再执行 `release-precheck.bat` 并传入 `logPath`。
- 将 `build/reports/triplet-diff.json` 作为 CI artifact 保留，便于回归审计与差异追踪。
- 每次调整命令语义（`code/messageKey/sideEffects`）时，必须同步更新 triplet 基线并在 PR 描述说明原因。
- 当 PR 涉及 baseline 变更时，必须勾选 `.github/pull_request_template.md` 中“triplet 基线更新”清单并补齐提交要求。
- 当 PR 涉及 baseline 变更时，`triplet-baseline-pr-check.yml` 必须为 PASS；未勾选清单会直接失败阻断。
- CI 与本地必须使用同一基线文件路径，避免“本地通过、CI 失败”的口径分裂。
- 与门禁配置相关的截图留痕必须遵循统一命名与归档规范（见截图治理文档），确保审计可追溯。

## Windows CI 调用示例

```bat
release-precheck.bat ".\tools\triplet-compare\log-sample.txt" ".\tools\triplet-compare\baseline-sample.json" ".\build\reports\triplet-diff.json" false build
```

## GitHub Actions 最小模板（可直接复用）

- 仓库已提供最小模板：`.github/workflows/release-precheck-minimal.yml`。
- 模板动作：
  - 使用 `windows-latest` + `JDK 17`；
  - 调用 `release-precheck.bat`（固定执行顺序：build -> triplet 回归）；
  - 上传 `build/reports/triplet-diff.json` 作为 artifact（名称：`triplet-diff`）。
- 复用建议：复制该文件后仅替换触发条件与日志来源（`logPath`），其余参数保持默认口径。

## 基线 PR 清单阻断工作流（第二十批）

- 仓库已提供：`.github/workflows/triplet-baseline-pr-check.yml`。
- 触发条件：`pull_request` 的 `opened/edited/synchronize/reopened/ready_for_review`。
- 阻断规则：仅当 PR 变更 `tools/triplet-compare/*baseline*.json` 时，强制检查 PR 正文中的“triplet 基线更新”清单是否全部勾选。
- 失败结果：任一必选项未勾选即返回失败状态，阻断合并。

## 分支保护必选检查（第二十一批）

- 分支保护落地手册：`tools/triplet-compare/BRANCH-PROTECTION-RUNBOOK.md`。
- `main` 分支必须将以下检查配置为 required：
  - `release-precheck-minimal`
  - `triplet-baseline-pr-check`
- 建议优先采用 GitHub `Rulesets` 管理分支策略；若使用经典 `Branch protection rules`，同样需显式勾选上述检查。
- 配置后需通过 PR 验证：
  - 任一检查失败即阻断合并；
  - 两项检查通过后才允许进入后续发布流程。

## 仓库管理员 5 分钟启用清单（第二十二批）

- 仓库新增管理员启用清单：`tools/triplet-compare/REPO-ADMIN-5MIN-ENABLE-CHECKLIST.md`。
- 清单提供“步骤核对 + 截图位 + 验收 + 失败排查”一体化落地路径，用于快速完成分支保护与状态检查启用。

## 截图命名规范与归档目录约定（第二十三批）

- 仓库新增截图治理文档：`tools/triplet-compare/SCREENSHOT-NAMING-ARCHIVE-CONVENTION.md`。
- 约束内容包含：
  - 命名模板：`{日期}_{仓库}_{分支}_{阶段}_{截图位}_{结果}_{操作者}.png`；
  - 归档目录：`artifacts/governance/screenshots/YYYY/YYYYMMDD/{branch}/{stage}`；
  - 最小留痕集：`shot-01` 到 `shot-06`；
  - 审计清单：命名/路径/检查状态/artifact/release-precheck 结果可读性核对。
