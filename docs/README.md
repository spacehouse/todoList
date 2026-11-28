# docs 目录索引

> `docs/` 按状态分类存放，目录口径与根目录 `TODO.md` 的分区（进行中 / 待同步 / 计划中 / 已完成 / 已废弃）对应，每份文档标注其对应的 TODO 条目。
> 文档正文内的「状态：」行以文档自身为准；本索引记录整理时点（2026-11-28）的核对结论，含状态行滞后项。
> 各版本分支 docs 目录基本同构，个别分支独有文档已用「（仅 xx 分支）」标注。

## 目录结构

```text
docs/
├── README.md        ← 本索引
├── completed/       ← 已完成（对应 TODO「已完成」区）
├── in-progress/     ← 进行中（对应 TODO「进行中」区）
├── planned/         ← 计划中（对应 TODO「计划中」区）
├── guides/          ← 指南与流程（长期有效，不对应单一状态）
├── deprecated/      ← 已废弃（对应 TODO「已废弃」区）
└── examples/        ← 文档配套示例
```

## completed/（已完成）

功能已实现并随 1.4.3 发布 / 完成全分支同步的设计文档：

| 文档 | 内容 | 对应 TODO 条目 | 核对备注 |
| --- | --- | --- | --- |
| `feat-task-import-format.md` | 任务批量导入格式 JSON v1（experimental），`/todo import` 统一入口 | 任务批量导入格式（JSON）v1 | 状态与 TODO 一致；experimental 转正条件见文档 §6（2027-05 前无外部使用信号则重新评估）；配套示例在 `examples/task-import-example.json` |
| `feat-trigger-completion.md` | 游戏内事件触发式完成任务（5 类触发器，Phase A~Y1 全部完成） | 游戏内事件触发式完成任务；幽灵任务源头级联完成（Phase Y1） | 文档头部状态行仍写「验证分支开发中」（2026-09-13），功能已随 1.4.3 发布，状态行滞后 |
| `feat-item-title-markup.md` | 任务标题内联 `[item:...]` 渲染物品图标与名称 | 支持任务名称中包含游戏内物品 | 状态行同样滞后（2026-09-11 写「验证分支开发中」） |
| `feat-material-task-generation.md` | 材料反推生成收集任务树（experimental，后续开发已冻结） | 材料反推 | 状态行「🧊 已冻结后续开发」与 TODO 一致；后续路线（JEI 附属 / 导入格式 / AI 生成）结论在 §0.4 |
| `h2-tcp-server-removal.md` | 移除 H2 TCP 服务端与外部访问账号体系 | 移除 H2 TCP 服务端与外部访问账号体系 | 状态行「待 CF 上传验收」已随 1.4.3 发布（2026-11-08 CF/Modrinth 发布成功）完成；§3 保留物中「应急 NBT 写入」一条已被 `nbt-storage-removal.md` 裁定退役 |
| `nbt-storage-removal.md` | 清理 NBT 文件存储残余，收敛为 H2 单链路 | 清理 NBT 文件存储残余 | 状态行「待分支同步」已过时，八分支同步已于 2026-11-21 完成 |
| `1.20.x-adaptation-plan.md`（仅 1.20.x 分支） | 1.20.2~1.20.6 多版本适配计划与共线收尾记录 | TODO「已完成」区 1.20.x 多版本同步相关条目 | 共线收尾 2026-09-05 完成；残留待办仅一条：FabricNetworkingCompat payload 真实联机冒烟 |

留档类：

- `smoke-checklist-2026-10-25.md`：材料反推 E11-E15 + 幽灵任务 Y1 的一次性实机冒烟清单，2026-10-26 全部通过后留档；正文 checkbox 未勾属格式遗留，实际结论见文末「结果记录」。

## in-progress/（进行中）

TODO「进行中」区当前仅此一项：

- `verify-automation-plan.md`：L1 verify-all 已实跑（2026-10-26）；L2 已以 selftest 12 场景两档落地（原 GameTest 路线未走）；L3 实机自动冒烟链路定型 v3（2026-11-22，版本 json 直启 + mc-focus + PostMessage 驱动 + F2 判定 + 数据双通道），2026-11-28 补验 26.2 / 26.3 后九分支实例级实机冒烟全闭环。剩余 CI 挂接 / real-server 环境见文档 §5。

## planned/（计划中）

TODO「计划中」区的条目目前均未立项独立设计文档，进入设计前必须先过需求准入三问。与本区相关的文档：

- `requirement-review-template.md`：「计划中」条目的准入评审模板（需求准入三问 + experimental 转正规则 + 反例存档），长期有效的流程配套。评审通过后的设计文档落位说明见模板内「使用约定」。
- `feat-material-task-generation.md` §0.4（现位于 completed/）：计划中「附属 / 外部集成」三条路线（JEI 附属、任务导入格式、AI 生成）的结论出处。
- `feat-trigger-credit-allocation.md` §9（现位于 deprecated/）：计划中「借鉴成熟 RPG 任务系统」方向的 Stage / Optional 边界参考。

## guides/（指南与流程）

- `storage-backend-h2-guide.md`：面向玩家的 H2 存储使用 / 迁移 / 备份恢复指南（已按 H2 单后端现状改写）。
- `h2-external-client-guide.md`：外部工具经备份副本 + H2 只读模式排查数据的操作指南（已按 TCP 移除后现状改写）。

## deprecated/（已废弃）

- `feat-trigger-credit-allocation.md`：同物品任务额度配额（Phase Z），2026-09-28 实机验证证伪，实现已回退。§0 为废弃结论与教训（结论先行），§1~§9 为设计过程存档，**不要按其实现**；§9 另被计划中 RPG 方向引用为边界参考。

## 维护约定

- 新增设计文档时：先按状态放入对应目录（评审中放 planned/，开发中放 in-progress/，完成放 completed/），再在本索引对应区补一行，并确认 TODO 对应条目已链接新路径。
- 文档状态流转（计划中 → 进行中 → 已完成 / 已废弃）时，先 `git mv` 移动到对应目录，再更新文档头部「状态：」行与本索引分区。
- 引用文档路径时一律写完整分类路径（如 `docs/completed/feat-*.md`），不要写裸 `docs/feat-*.md`。
- 本索引与 TODO.md 分区口径一一对应；TODO 中无对应条目的横切文档（指南 / 模板 / 留档）单列说明。
