# 实现方案（草稿）：反推前置材料并生成收集任务

> 状态：**方案草稿，暂不实现**（当前分支优先处理其他小改动）
> 对应 TODO.md 计划项：【feat】支持根据收集目标反推前置材料并自动生成收集任务 / 支持粘贴导入材料清单生成收集任务 / 支持模组自定义配方类型的材料反推
> 目标分支：`verify/1.20.1-feat-trigger-and-item-title`（设计同时面向 1.20.1 / 1.21.1，实际 API 名按落地分支核对）
> 更新日期：2026-09-13

## 1. 背景与目标

### 1.1 背景

触发器底座已完成（见 `docs/feat-trigger-completion.md`），`TaskTrigger` 已支持 `ITEM_COLLECT`（绝对持有量语义）与 `CRAFT_ITEM`（累加语义），玩家可以获得「材料收集到量后自动完成」的能力。

当前缺口：**玩家仍需手工把目标拆成一条条收集任务**。本方案要解决的正是"从目标或清单自动展开出收集任务"。

### 1.2 目标

给定「一个目标物品 + 数量」或「一份外部材料清单」，自动展开成一组可追踪（自动判定完成）的收集任务。

### 1.3 两条数据源路线（本方案的核心判断）

| 路线 | 说明 | 覆盖对象 |
|---|---|---|
| 反推路线 | 从配方依赖树反推前置材料 | 原版可合成物品（点 1）、结构标准的模组机器配方（点 3） |
| 导入路线 | 直接拿外部现成清单 | 任意模组（点 2），包括反推算不准的场景 |

**为什么必须两条腿**：模组配方语义没有边界（流体、概率产出、催化剂、加工时长、序列组装），通用反推不可能全覆盖，任何"万能解析"最终都会变成补不完的适配清单。导入路线不依赖配方语义，是覆盖复杂模组的逃生舱。

### 1.4 可复用的现有底座（本方案不改动它们）

| 现有资产 | 复用方式 | 位置 |
|---|---|---|
| `TaskTrigger`（含 `ITEM_COLLECT` / `CRAFT_ITEM`） | 生成的任务直接挂触发器，天然自动追踪 | `common/src/main/java/com/todolist/task/TaskTrigger.java` |
| `Task` 子任务模型 | `parentTaskId` / `subtasks` / `subtaskSortOrder` / `addSubtask` 已具备，材料树→任务树直接映射 | `common/src/main/java/com/todolist/task/Task.java` |
| 物品检索与中英双匹配 | 清单导入的名称解析直接复用 | `common/src/main/java/com/todolist/gui/ItemSelectorScreen.java` |
| 配方反查先例 | 已有 `RecipeManager#getRecipeFor(RecipeType, ...)` 的成功用法 | `fabric/src/main/java/com/todolist/mixin/ResultSlotMixin.java` |
| 离线自测基建 | 新增 `JavaExec` self-test 挂到 `:common:check` | `common/build.gradle.kts` |

**关键结论：本方案不需要改 H2 schema、不需要改网络包、不需要改平台模块。** `common` 已可直接使用原版类（`BuiltInRegistries` / `ItemStack` / `RecipeManager`）与客户端类，无需平台拆分。

## 2. 本期范围

| 序号 | 范围 | 依赖 |
|---|---|---|
| 点 1 | 原版配方反推（L1）：crafting / smelting / blasting / smoking / campfire / stonecutting / smithing | 无 |
| 点 2 | 文本材料清单导入：粘贴 / 解析 / 生成 | 复用点 1 的任务生成链路 |
| 点 3 | 模组自定义配方类型的通用 resolver（L2） | 插入点 1 的 resolver 注册表 |

依赖关系：点 1 是地基；点 2 复用点 1 的模型与建任务链路（可省约 30%~40% 代码量）；点 3 只需在点 1 的注册表里增加一个 resolver 实现，不新增架构。

## 3. 点 1：原版配方反推（L1）

### 3.1 处理流程

```text
输入：目标物品 + 目标数量
  │
  ├─ RecipeIndex 反查（item → 配方列表，带缓存）
  │
  ├─ MaterialResolver 递归展开
  │     ├─ 数量传播：need = ceil(上层需求 / 单次产出) × 单次消耗
  │     ├─ 循环检测（visited 集合）+ 深度上限
  │     ├─ tag 输入展开策略
  │     └─ 叶子判定（无可解析配方即视为叶子）
  │
  ├─ DAG 合并（同一材料跨分支合并求和）
  │
  └─ MaterialPlan → 生成任务
```

### 3.2 语义规则（必须先拍板，决定后续全部实现）

| 规则项 | 建议默认 | 说明 |
|---|---|---|
| 叶子材料 | 生成 `ITEM_COLLECT` 收集任务 | 不可再合成的材料，持有量语义成立 |
| 中间产物 | **不生成任务**（默认） | `ITEM_COLLECT` 是"当前持有量"，中间产物合成后立即被消耗，持有量永远到不了目标，任务会永久卡住 |
| 中间产物（备选） | 生成 `CRAFT_ITEM` 子任务 | 累加语义，能反映"合成过 N 次"，但语义与"收集"不同，需产品确认 |
| 数量传播 | `ceil(need / outputCount) × inputCount` | 向上取整；多输入按各自消耗量分别计算 |
| 同材料合并 | 合并求和为一条 | 结果是 DAG 而非树，不合并会产出大量重复任务 |
| 循环依赖 | visited 检测 + 深度上限 | 如 铁块 ↔ 铁锭、木板 ↔ 原木 |
| tag 输入 | 取 tag 内**任一**代表物 + 数量（建议允许用户改） | 如 `#minecraft:planks`；需决定是"任一"还是"全部" |
| 多配方选择 | "最省料"为默认，允许用户切换 | 同一物品多配方会导致整棵树不同，必须可干预 |
| 催化剂 / 工具 | 标记排除，不计入数量 | 模具、搅拌棒、桶等可复用物品 |
| 概率产出 | 取"必得最小值"（保守） | 取期望值会算不准，建议先保守 |

### 3.3 新增组件（预估）

| 组件 | 职责 | 预估行数 |
|---|---|---|
| `RecipeIndex` | item → 配方列表 反查索引 + 缓存 | 150~200 |
| `RecipeResolver`（接口）+ `VanillaRecipeResolver` | 统一解析入口，覆盖 7 种原版配方类型 | 200~250 |
| `MaterialResolver` | 递归展开 + 数量传播 + DAG 合并 + 循环/深度保护 | 250~350 |
| `MaterialNode` / `MaterialPlan` | 材料树/清单数据模型 | ~100 |
| `MaterialTaskGenerator` | 材料清单 → 任务（挂触发器、建子任务） | ~150 |
| `MaterialListScreen` | 材料清单预览（树/清单、勾选、改数量、切配方） | 350~450 |

### 3.4 建任务链路

```text
目标任务（父任务，仅作分组）
  ├─ 子任务：收集 铁锭 ×36    ← trigger: ITEM_COLLECT minecraft:iron_ingot 36
  ├─ 子任务：收集 橡木原木 ×12 ← trigger: ITEM_COLLECT minecraft:oak_log 12
  └─ 子任务：收集 煤炭 ×8      ← trigger: ITEM_COLLECT minecraft:coal 8
```

- 复用 `Task.addSubtask` + `setParentTaskId` + `subtaskSortOrder`，**不改任务模型**；
- **注意与现有规则的冲突**：`feat-trigger-completion.md` Phase V 规定「已有子任务的父任务不允许挂触发器」。因此目标任务作为父任务时**自身不能挂 `ITEM_COLLECT`**，只作分组；触发器全部下沉到叶子子任务。若希望目标任务本身也可追踪，需改为与材料任务**平级**生成——这是待拍板项。

### 3.5 预览界面（点 1 的主要工作量）

- 展示材料清单 / 树形结构；
- 支持切换配方、调整数量、勾选要生成哪些条目；
- 与已有任务去重（同材料已有收集任务时，是累加数量还是新建子任务）；
- 预览阶段可反复调整，确认后才落库。

## 4. 点 2：文本材料清单导入

### 4.1 输入来源

- 投影（Litematica）材料清单界面可复制的文本；
- Create 蓝图材料清单文本；
- 玩家手工整理的清单。

### 4.2 解析规则与容错

需支持多种行格式，例如：

```text
铁锭 x64
minecraft:iron_ingot 64
64 iron_ingot
iron_ingot    64
```

- 分隔符：`x` / `×` / `*` / 空格 / Tab / 逗号；
- 数量缺省视为 1；
- 无法解析的行**标记出来提示用户**，不静默丢弃；
- 允许 `#` 开头的注释行。

### 4.3 名称 → 资源 ID 匹配

- 清单里可能是**本地化名**（"铁锭"）而非资源 ID，需注册表扫描 + 中英文双匹配；
- 该能力 `ItemSelectorScreen` 已具备，建议抽出公共的 `ItemNameIndex`（预估 ~100 行重构）；
- 需要处理歧义（同名物品）与未匹配项的提示。

### 4.4 与点 1 的复用关系

解析后直接产出 `MaterialPlan`，**复用点 1 的预览界面与建任务链路**，是三点中最省的一块。

## 5. 点 3：模组自定义配方类型的通用 resolver（L2）

### 5.1 原理

原版 `Recipe` 接口提供通用方法，遍历 `RecipeManager` 的全部配方类型即可拿到「输入 → 输出」：

```text
for (RecipeType<?> type : 全部配方类型) {
    for (RecipeHolder<?> holder : 该类型全部配方) {
        Recipe<?> recipe = holder.value();
        NonNullList<Ingredient> inputs = recipe.getIngredients();   // 通用输入
        ItemStack output = recipe.getResultItem(registryAccess);     // 通用输出
        // 过滤：输入为空 / 输出为空 / 输出等于输入的配方不参与
    }
}
```

> **待核对项 T1**：`getIngredients()` / `getResultItem(RegistryAccess)` 在 1.20.1 与 1.21.1 的映射与可见性需在实现时逐一核对（1.21 起配方 API 有调整）。此为本点最大的不确定来源。

### 5.2 覆盖边界

- **能覆盖**：实现了标准 `Recipe` 接口、且输入输出可被通用方法读出的模组机器配方；
- **覆盖不了**：非容器型配方（读不到输入）、多产物/概率产出、流体输入输出、序列组装类（如 Create 的 sequenced assembly）。这些属于 L4 专用 adapter 范畴，本期不做。

### 5.3 注册表设计（与点 1 共用）

```text
RecipeResolverRegistry
  ├─ VanillaRecipeResolver      （点 1，L1）
  ├─ GenericTypeRecipeResolver  （点 3，L2，通用反射/通用接口）
  └─ JsonRuleRecipeResolver     （L3 数据驱动，后续，本期不做）

RecipeResolver {
    boolean canResolve(RecipeHolder<?> holder)
    ResolvedRecipe resolve(RecipeHolder<?> holder, ItemStack target)
}
ResolvedRecipe { List<IngredientSpec> inputs; ItemStack output; int outputCount; }
```

点 3 只需实现该接口并注册，不改点 1 的算法。

### 5.4 风险

- **离线无法覆盖**：真实模组配方只在运行期存在，必须**装真实模组进游戏验证**；
- 各种模组对 `getIngredients()` 的实现质量参差，可能返回空或包含无用占位，需实测过滤规则；
- 与点 1 的配方选择策略需要统一（同一物品既有原版配方又有模组配方时排序规则）。

## 6. 边界（本期明确不做什么）

- 不做 L4 专用 adapter（Create 序列组装、AE2 压印等按模组手写）；
- 不做 mod API 直连（投影 `SchematicPlacement` / Create 蓝图 API）；
- 不做蓝图 / 原理图文件解析（`.litematic` / `.nbt`）；
- 不处理流体（任务系统目前只有物品维度）；
- 不做团队任务多人合计；
- 不改 H2 schema、不改网络包、不改平台模块；
- 不为已废弃的 NBT 文件存储做适配（H2-only 硬约束）。

## 7. 关键风险与待定决策

| # | 风险 / 决策 | 影响 | 状态 |
|---|---|---|---|
| D1 | 中间产物是否生成任务；若生成，用 `CRAFT_ITEM` 还是 `ITEM_COLLECT` | 决定任务是否正确可完成 | **待拍板** |
| D2 | 目标任务是父任务（不能挂触发器）还是与材料任务平级 | 决定目标任务能否自身追踪 | **待拍板** |
| D3 | 多配方选择策略（最省料 / 最少步骤 / 用户选） | 整棵材料树结果 | **待拍板** |
| D4 | 概率产出取期望值还是必得最小值 | 数量准确性 | **待拍板** |
| D5 | tag 输入按"任一"还是"全部"展开 | 数量准确性 | **待拍板** |
| T1 | `Recipe` 通用方法在 1.20.1 / 1.21.1 的可用性 | 点 3 可行性 | **待核对** |
| R1 | 任务数量爆炸（复杂物品树可达数十~数百节点） | 需要聚合 + 勾选，否则污染任务列表 | 由 3.5 预览界面缓解 |
| R2 | 配方解析性能（大树上重算会卡 GUI） | 需反查索引 + 缓存，项目对卡顿敏感 | 由 `RecipeIndex` 缓解 |
| R3 | 实机验证不可离线覆盖 | token / 返工成本主要来源 | 需实机清单 |

## 8. 分步实现计划（全部未开始）

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase A | 语义拍板（D1~D5）+ API 核对（T1） | ⬜ 未开始 |
| Phase B | 点 1：`RecipeIndex` + `RecipeResolver` 注册表 + `VanillaRecipeResolver` | ⬜ 未开始 |
| Phase C | 点 1：`MaterialResolver`（递归/数量/DAG/循环）+ 离线单测 | ⬜ 未开始 |
| Phase D | 点 1：`MaterialTaskGenerator` + 建任务链路 | ⬜ 未开始 |
| Phase E | 点 1：`MaterialListScreen` 预览界面 | ⬜ 未开始 |
| Phase F | 点 2：`MaterialListTextParser` + `ItemNameIndex` 抽取 + `ImportMaterialScreen` | ⬜ 未开始 |
| Phase G | 点 3：`GenericTypeRecipeResolver` + 过滤规则 | ⬜ 未开始 |
| Phase H | 离线回归挂 `:common:check` + 实机验证清单 | ⬜ 未开始 |

## 9. 工作量与代码量估算（基于本仓库同类特性实测行数）

| | 点 1 | 点 2 | 点 3 | 合计 |
|---|---|---|---|---|
| 生产代码 | 1,200~1,500 | 500~700 | 300~400 | **2,000~2,600** |
| 测试代码 | 300~400 | ~200 | ~250 | **750~850** |
| 新增文件 | 6~7 | 3~4 | 2~3 | **11~14** |
| 改动现有文件 | 1~2 | 1~2 | 0~1 | **2~5** |
| 复杂度 | 中偏高 | 低-中 | 中（验证风险高） | — |

对照基准：本仓库「触发器特性」生产代码约 2,000 行 + 测试约 540 行（见 `feat-trigger-completion.md`，22 个 Phase / 21 条实机回归记录）。本方案规模接近，但**无 schema / 网络包 / 平台模块改动**，故风险低于触发器特性。

token 量级参考（含探索、实现、构建、离线测试修错，不含实机回归）：约 **0.8M~1.5M**；含实机验证与回归修复通常再翻倍以上。实机验证是本特性的主要成本来源。

## 10. 待确认问题（需要用户拍板后才进入实现）

1. D1：中间产物要不要建任务？用哪种触发器？
2. D2：目标任务作父任务（不挂触发器）还是与材料任务平级（可挂触发器）？
3. D3：默认配方选择策略？
4. 生成的任务挂在当前选中任务下，还是新建一个独立任务/项目？
5. 与已有收集任务去重时：累加数量，还是新建独立子任务？
6. 是否需要"生成前预览、确认后落库"，还是直接生成后允许撤销？
7. 点 2 的清单文本来源，是否已有目标模组（投影 / Create）的实际输出样本可参考？
