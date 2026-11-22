# 任务批量导入格式（JSON）v1

> 状态：**experimental**（2026-11-22 立项，在验证分支 `verify/1.20.1-feat-task-import` 实施）
> 关联：`docs/feat-material-task-generation.md` §0.4（外部承接路线第 2 条）、
> `docs/feat-trigger-completion.md`（触发器语义底座）、`docs/feat-item-title-markup.md`（标题物品 markup）

## 0. 需求准入三问（评审记录，2026-11-22）

### 1. 用户故事

- 作为待办清单玩家，我想把外部工具生成的任务清单（AI 生成、JEI/EMI 附属产出、手工编写的
  JSON 文件）一键导入游戏待办，以便不逐条手敲任务与触发器。
- 场景描述：玩家从 AI 对话拿到一份「下界探险准备清单.json」（含物品收集触发器），
  放入 `config/todolist/imports/`，游戏内执行 `/todo import 下界探险准备清单.json`，
  待办中出现完整父子任务树，收集对应物品时任务自动推进。
- 需求来源：
  - [x] 自己的设想（承接已冻结的材料反推点 2：2026-10-25 冻结评审明确
    「清单导入需求由任务导入格式承接」，是所有外部生成来源的统一入口——基础设施性质，
    无它则 JEI 附属 / AI 生成都没有落库出口）
  - [ ] 社区反馈（无）

### 2. 验证方案

- 验收标准：
  - [x] 合法文档（完整字段 / 仅必填字段）导入成功，任务落库且父子、触发器、归属正确
  - [x] 文档级错误（文件缺失 / JSON 语法 / 版本不支持 / 空列表 / 超上限）整批拒绝并输出对应反馈
  - [x] 字段级错误聚合上报（前 5 条 + 总数），且**不部分落库**（原子性）
  - [x] 离线断言进 `:common:check`（`taskImporterTest` + `commandSystemTest` 两个新组）
- 实机验证路径：1.20.1 实例 `config/todolist/imports/` 放示例 JSON → `/todo import` →
  任务树出现、父子正确、`ITEM_COLLECT` 触发器随拾取推进（随全分支冒烟流程执行）。
- 离线可覆盖部分：解析、校验、转换、命令落库、原子性全部离线覆盖（见 §5）。

### 3. 投入上限

- 生产代码预估：~450 行（DTO + 解析器 + 命令 + lang），实际以提交为准
- 时间预算：1 个工作日内完成离线验证；实机冒烟随统一流程
- 触发冻结的条件：实机首轮导入链路不通且 2 小时内无法定位；或预估超限 50%

### 4. 状态流转

- 当前状态：experimental（对外发布但明示实验性；只修缺陷不扩能力）
- 转正条件：拿到社区反馈（有玩家/附属作者实际使用导入格式）后转正；
  长期无反馈的到期处理（2027-05 前无任何外部使用信号则重新评估冻结）
- 归档：完成后回填 TODO.md 条目位置与本文档链接

## 1. 定位与范围

**v1 只做导入、只做个人任务、只做命令入口**（2026-11-22 用户确认）：

| 维度 | v1 决策 | 延后项 |
|---|---|---|
| 入口 | `/todo import <file>`（服务端命令，EDIT 权限） | GUI 导入按钮 |
| 作用域 | 个人任务（落当前激活项目，与 `/todo task add` 归属规则一致） | 团队作用域（涉及领取/指派语义） |
| 方向 | 仅导入 | `/todo export` 导出（格式预留对称性） |

导入格式是**所有外部任务生成来源的统一入口**（FTB Quests chapters 同款模式）：
JEI/EMI 附属、AI 生成、清单导入都是该格式的生产者，主模组只消费。

## 2. 格式规范 v1

```json
{
  "formatVersion": 1,
  "tasks": [
    {
      "id": "gear",
      "title": "准备下界装备",
      "description": "出发前备齐",
      "priority": "HIGH",
      "tags": ["探险"],
      "dueDate": 1735689600000,
      "parentId": "gear",
      "trigger": {
        "type": "ITEM_COLLECT",
        "target": "minecraft:iron_ingot",
        "targetCount": 10
      }
    }
  ]
}
```

### 字段说明

| 字段 | 必填 | 类型 | 规则 |
|---|---|---|---|
| `formatVersion` | 是 | int | 仅支持 `1`；未来破坏性变更升版本号并保持向后读取 |
| `tasks` | 是 | array | 非空，≤500 条（`MAX_TASKS`）；元素不可为 null |
| `tasks[].id` | 否 | string | 文档内局部别名，供 `parentId` 引用；重复即报错 |
| `tasks[].title` | 是 | string | 非空白，≤512 字符（H2 列宽）；支持 `[item:路径]` / `[item:命名空间:路径]` 标题 markup（如 `[item:minecraft:iron_ingot]`，省略命名空间自动补 `minecraft:`；语法不合法时按普通文本原样显示，见 `TitleMarkupParser`） |
| `tasks[].description` | 否 | string | ≤16384 字符（H2 列宽），缺省空串 |
| `tasks[].priority` | 否 | string | `LOW` / `MEDIUM` / `HIGH`（大小写不敏感），缺省 `MEDIUM` |
| `tasks[].tags` | 否 | string[] | 单个 ≤128 字符（H2 列宽）；导入时去空白、去空串、去重，保持首次出现顺序 |
| `tasks[].dueDate` | 否 | long | epoch 毫秒，≥0 |
| `tasks[].parentId` | 否 | string | 引用同文档别名，**允许前向引用**；未知别名报错；**自引用与环（a→b→a）报错** |
| `tasks[].trigger` | 否 | object | 见下；`progress` **不可导入**（恒 0，进度只能由游戏内事件推进） |
| `trigger.type` | 是* | string | 严格匹配 `KILL_ENTITY` / `BREAK_BLOCK` / `CRAFT_ITEM` / `ITEM_COLLECT` / `ADVANCEMENT`（大小写不敏感）；非法值报错，**不静默回退** |
| `trigger.target` | 是* | string | 目标资源 ID（如 `minecraft:iron_ingot`），≤256 字符（H2 列宽） |
| `trigger.targetCount` | 是* | int | ≥1 |

\* 提供 `trigger` 对象时三项必填。

### 全局限制

| 限制 | 值 | 原因 |
|---|---|---|
| 文件大小 | ≤5 MB（`MAX_FILE_BYTES`） | 读取前预检，防巨型文件造成内存压力 |
| 批次文本总量 | ≤256 KB（`MAX_BATCH_TEXT_BYTES`，标题+描述+标签 UTF-8 字节） | 个人任务同步为单包全量，防超网络帧上限 |
| 长度上限 | 见字段表 | 与 H2 列宽一致，超限值落库会触发 SQLException |

### 校验语义

- **严格 + 原子**：全量校验收集所有错误，任何一条错误都会让整批导入失败，不部分落库。
  与运行期 `TaskTrigger.Type.parse` 的容错回退语义**刻意不同**——导入文件来自外部工具，
  错误必须显式暴露给生产者修复。
- **错误明细格式**：`#任务序号 字段: 原因`（1 基序号，面向 JSON 生产者排错的技术格式），
  上报前 5 条 + 剩余条数（`TaskImporter.MAX_ERRORS_REPORTED`）。
- **未知字段忽略**：Gson 默认行为，为格式前向兼容保留空间。

### 转换规则

- 父子关系：`id` / `parentId` 别名在导入时映射为 Task 真实 ID；同一父任务下子任务按文档
  出现顺序分配 `subtaskSortOrder`（0 基递增，与 `MaterialTaskGenerator` 一致）。
- 归属：全部任务 `scope=PERSONAL`；`creatorUuid` = 执行导入的玩家；`projectId` =
  当前激活项目（`resolveProjectIdForCommandTaskAdd`，与 `/todo task add` 一致）。

## 3. 命令行为

```
/todo import <file>              # 不带项目参数：落当前激活项目（与 /todo task add 一致）
/todo import <file> <project>    # 可选项目参数（ID 或名称精确匹配，greedyString 支持中文/空格名）
```

- 权限：`EDIT`（与 `/todo task add` 一致）。
- 文件查找顺序：游戏目录下相对/绝对路径 → `config/todolist/imports/<file>`。
- 项目参数（2026-11-22 新增）：先按项目 ID 精确查找，再按名称在全部项目中精确匹配（取首个命中）；
  项目不存在报 `project_not_found`；**团队项目报 `project_not_personal`**（v1 仅支持个人项目，
  团队任务的待领取/指派语义留待后续版本）。
- 落库链路：`loadPersonalTasksForCommand` → 追加 → `savePersonalTasksForCommand` →
  `syncTasksToPlayer`（与命令侧任务写入完全同一条链路，H2 持久化 + 客户端同步一次完成）。
- 反馈（lang key 前缀 `command.todolist.import.`）：`success`（含父/子任务计数）、
  `file_not_found` / `invalid_format` / `validation_failed` / `empty` / `too_many` /
  `too_large` / `project_not_found` / `project_not_personal` / `failed`。
- 健壮性：解析入口捕获 `StackOverflowError`（深嵌套 JSON 在无嵌套限制的 Gson 版本上不会击穿命令线程）；
  命令层对 `TaskImporter.parse` 另有 `RuntimeException` 兜底（未预见的解析异常按 `failed` 反馈）。

## 4. 代码结构

| 文件 | 职责 |
|---|---|
| `com/todolist/importer/TaskImportModels.java` | Gson DTO（文档 / 任务项 / 触发器项），零校验 |
| `com/todolist/importer/TaskImporter.java` | 解析 → 全量校验（错误聚合）→ 两遍转换；`Result(tasks, errors, documentErrorCode)` |
| `CommandBootstrap.executeTaskImport` | 命令入口：路径解析、上下文构造、落库与同步、分类反馈 |
| `docs/examples/task-import-example.json` | 完整示例（兼实机冒烟素材） |

## 5. 验证

- 离线（进 `:common:check`）：
  - `taskImporterTest`（`TaskImporterTestMain`，38 用例）：完整字段映射、默认值、
    别名父子挂接 + 排序、前向引用、标签规范化、文件读取（UTF-8 中文）、
    全部拒绝分支（未知/重复别名、版本、空、超限、空白标题、非法优先级、
    非法触发器三字段、多错误聚合截断、非法 JSON、缺失文件）；
    **边界加固用例（2026-11-22 第二批）**：null 任务条目、自引用/两节点/三节点
    parentId 环、无环链不误伤、标题 512 边界（超/恰）、超长描述/标签/触发目标、
    UTF-8 BOM 剥离、10 万层深嵌套 JSON 不崩溃、字段类型错误（tasks 对象化 /
    tags 字符串 / dueDate 非数字）、顶层数组、恰好 500 条边界、错误消息值截断、
    批次文本总量预算、超限文件（5 MB）预检
  - `commandSystemTest` 新增 `CommandBootstrapImportIntegrationTestMain`（7 用例）：
    imports 目录与游戏目录两路导入成功落库、文件缺失 / 非法 JSON / 空列表反馈、
    校验失败不落库（原子性）、null 条目文件命令链路不崩溃不落库
- 构建：`.\build-local-17.bat build --offline`（验证分支基于 1.20.1）——BUILD SUCCESSFUL，26 测试组全 PASS
- 实机冒烟（2026-11-22，1.20.1-Fabric 0.19.5 实例，EC_XY 单人世界）：**PASS**（两轮）
  - 第一轮（数据链路）：`/todo import task-import-example.json`（imports 目录）：6 条任务全部落库
    （LOCAL_PERSONAL 16→22），父子挂接与 sortOrder 正确、优先级 HIGH/MEDIUM/LOW 按文件、
    标签渲染（HUD 青色 `[补给]`/`[探险]` 前缀）、触发器六条全挂、creator/project/scope 归属正确
    （H2 JDBC 数据级终验，报告 `smoke-reports/20261122-task-import/result.txt`）；
    `/give EC_XY minecraft:iron_ingot 20` → 「准备下界装备」progress 20/20 自动完成，
    级联完成两个未完成子任务（Phase Y1 语义：completed=true 但 progress=0）
  - **第一轮视觉复查发现示例文件 markup 语法错误**：示例标题写成 `[minecraft:obsidian]`，
    合法语法是 `[item:路径]` / `[item:命名空间:路径]`（非法标记按普通文本原样显示是
    `TitleMarkupParser` 的设计行为，对 GUI 手输与导入一视同仁）；已修正示例与本文档
  - 第二轮（markup 渲染复验）：导入含 `[item:minecraft:diamond]` 与 `[item:emerald]`
    （省略命名空间）两种形态的标题，HUD 均**正确渲染为物品图标**；
    `/give` 钻石后父任务触发器达标级联完成子任务（划线态）。截图
    `smoke-reports/20261122-task-import/crop-markup-verify.png`，
    原图存实例 `screenshots/2026-11-22_18.12.11.png`
  - 第三轮（边界加固实机，18:31 构建 jar）：7 个边界文件逐个导入，全部符合预期且**游戏全程存活**——
    null 条目 / parentId 环 / 超长标题（513>512）/ 错误类型 各报 `validation_failed` 或
    `invalid_format`（错误明细精确定位到字段与原因）；批量超限与 5MB 超限文件报 `too_large`；
    BOM 文件正常导入 2 条且 `/give` 绿宝石后触发器 2/2 自动完成。H2 终验
    （`smoke-reports/20261122-task-import/edge-result.txt`）：6 类拒绝件零落库、
    无超 512 字符标题、桶计数 27=25+2 精确闭合
  - 第四轮（指定项目实机，19:02 构建 jar）：`/todo import edge-project.json ttt` 成功落
    `ttt` 项目（H2 联查 `project=ttt | scope=PERSONAL`）；`/todo import ... 不存在的项目`
    报 `找不到项目：不存在的项目。`。命令集成测试新增 4 用例（ID/名称导入、
    项目缺失、团队拒绝）全过

## 6. v2 展望（不承诺）

- `/todo export` 导出（格式对称，round-trip 验证）
- 团队作用域导入（需处理待领取/指派语义）
- GUI 导入按钮（文件选择受 MC 限制，大概率仍是路径输入）
- JEI/EMI 附属模组作为该格式的首个三方生产者（独立仓库，主模组零依赖不变）
