# triplet 基线更新流程（第二十批）

## 目标

- 统一 `COMMAND_RESULT_TRIPLET` 基线更新口径，避免“真实回归”被误更新掩盖。
- 明确“何时允许更新、何时禁止更新”，并提供可审计的提交与回退流程。

## 文档入口

- triplet 使用说明：`tools/triplet-compare/USAGE.md`
- CI 同步规则：`tools/triplet-compare/CI-SYNC-RULES.md`
- PR 模板（基线变更勾选项）：`.github/pull_request_template.md`
- PR 阻断工作流：`.github/workflows/triplet-baseline-pr-check.yml`

## 允许更新场景（满足其一即可）

1. **预期语义变更已评审通过**
   - 业务需求明确要求调整 `code/messageKey/sideEffects`；
   - 对应 PR/任务单已说明行为变更原因与影响范围。
2. **文案键规范化但语义不变**
   - `messageKey` 发生规范化迁移（如命名统一），且 `code/sideEffects` 语义保持一致；
   - 已确认不是误改执行路径导致的回归。
3. **副作用策略的显式收敛**
   - 例如将原本隐式行为改为显式 `PERSIST_DATA` 或 `REFRESH_HUD`；
   - 已同步更新命令语义说明，且通过回归验证。
4. **版本适配引入的受控差异**
   - 兼容层或平台层改动导致可解释、可追溯的 triplet 差异；
   - 差异仅限目标版本/目标平台，不影响既有稳定基线。

## 禁止更新场景（命中任一即禁止）

1. **未定位根因的差异**
   - 仅因回归失败而直接覆盖基线；
   - 未明确“为何变化、是否符合预期”。
2. **权限/上下文错误语义漂移**
   - `PERMISSION_DENIED`、`not_player_context` 等关键拒绝语义异常变化；
   - 可能掩盖越权或上下文判定缺陷。
3. **副作用缺失或越界**
   - 应有的 `PERSIST_DATA/REFRESH_HUD` 丢失，或新增了未评审副作用；
   - 可能导致数据一致性或 HUD 同步问题。
4. **跨命令批量异常波动**
   - 同一批次出现多命令 triplet 大范围变化，且无统一设计说明；
   - 优先判定为回归风险，不得以“更新基线”收口。

## 审阅清单（提交前逐项勾选）

> 本清单与 `.github/pull_request_template.md` 对齐，仅在涉及 baseline 变更时勾选并补齐。

- [ ] 已给出差异来源说明：涉及命令、字段、预期行为、影响范围。
- [ ] 已确认变更与需求/设计一致，并在 PR 描述附上关联链接。
- [ ] 已执行 `release-precheck.bat`（示例或真实日志）并通过。
- [ ] 已检查 `build/reports/triplet-diff.json`，确认无未解释差异。
- [ ] 已同步更新相关文档（`USAGE.md` / `CI-SYNC-RULES.md` / roadmap 批次记录）。
- [ ] 已准备回退方案（基线回退点、触发条件、执行命令）。
- [ ] 已确认 `.github/workflows/triplet-baseline-pr-check.yml` 通过（PR 基线清单勾选校验）。

## PR 阻断校验（第二十批新增）

- 当 PR 修改 `tools/triplet-compare/*baseline*.json` 文件时，GitHub Actions 会自动触发 `.github/workflows/triplet-baseline-pr-check.yml`。
- 工作流会读取 PR 正文并校验“triplet 基线更新”清单是否全部勾选（`- [x]`）。
- 任一必选项未勾选即失败并阻断合并，避免“更新了 baseline 但未补齐审阅信息”。
- 若 PR 不涉及 baseline 文件变更，该校验自动跳过，不影响普通改动。

## 提交模板（建议直接复用）

```text
[triplet-baseline] 更新基线：<命令域/版本范围>

变更原因：
- <需求或缺陷单链接>

差异摘要：
- code: <变化说明或“无”>
- messageKey: <变化说明或“无”>
- sideEffects: <变化说明或“无”>

影响范围：
- 平台：<Fabric/Forge/NeoForge>
- 版本：<如 1.20.1>
- 命令：<命令列表>

验证记录：
- release-precheck.bat: PASS
- 报告文件：build/reports/triplet-diff.json

回退策略：
- 回退提交：<commit id/预留>
- 回退触发条件：<触发条件>
```

## 回退策略

1. **快速回退（首选）**
   - 直接回退本次基线提交（`git revert <commit>`），恢复上一稳定基线；
   - 重新执行 `release-precheck.bat`，确认恢复后回归通过。
2. **定点回退（仅回退基线文件）**
   - 仅还原 `tools/triplet-compare/baseline-sample.json` 到上一个稳定版本；
   - 保留代码变更用于继续排查，避免一次性回退过多上下文。
3. **触发条件（满足任一执行回退）**
   - 上线前/后发现未评审 triplet 差异；
   - 发现权限拒绝语义或关键副作用与预期不一致；
   - CI 连续失败且定位为基线误更新。
4. **回退后动作**
   - 在 PR 或变更单补充“误更新原因 + 修复计划”；
   - 若确需更新基线，必须重新走本流程并完成审阅清单。
