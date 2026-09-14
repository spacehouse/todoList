# 实现方案（草稿）：反推前置材料并生成收集任务

> 状态：**点 1 已实现**（Phase A~E6 全部完成并通过离线回归；点 2 / 点 3 尚未开始）
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

### 3.2 语义规则（Phase A 已定稿）

| 规则项 | 结论 | 状态 |
|---|---|---|
| 叶子材料 | 生成 `ITEM_COLLECT` 收集任务（不可再合成的材料，持有量语义成立） | ✅ 已定 |
| 中间产物 | **生成「合成」任务并挂 `CRAFT_ITEM` 触发器**（累计合成量语义：必须在任务创建后真的合成出来，背包里已有的旧物品不计入）；原「不建任务」结论已作废 | ✅ 已修订（实机验证（第 4 轮）） |
| 默认展开层级 | **默认只展开根物品的直接材料**（`defaultExpandDepth = 1`，超出层级的节点以 `MaterialStopReason.FOLDED` 终止）；玩家在预览中选中节点点「继续展开」后才展开下一层 | ✅ 已修订（实机验证（第 2 轮）） |
| 数量传播 | `ceil(need / outputCount) × inputCount`；向上取整，多输入按各自消耗量分别计算 | ✅ 已定 |
| 同材料合并 | 合并求和为一条（结果是 DAG 而非树，不合并会产出大量重复任务）。**生成任务时同样只生成一条「收集」任务**，挂在首次出现的位置、数量取汇总需求（Phase E9 修订：否则多条任务各自用持有量语义比较，会互相"共享"同一份背包物品） | ✅ 已修订（实机验证（第 5 轮）） |
| 循环依赖 | visited 检测 + 深度上限（如 铁块 ↔ 铁锭）；展开结果回到当前依赖链上的同一材料时**不展示该子项**，避免重复 | ✅ 已定 |
| 多配方选择 | **默认预选"产出比最高"，同产出比时优先 crafting**（不耗燃料）；玩家可在预览界面覆盖。不静默替玩家决定 | ✅ 已定 |
| 终止条件 | **熔炼/烧制类（smelting / blasting / smoking / campfire）配方的输入即视为最终材料，不再展开**；玩家可在预览中对任一节点改为"继续展开" | ✅ 已定 |
| 熔炼燃料 | **本期不处理**：材料清单只列物品，不提示燃料（燃料不在配方数据里，无法准确折算） | ✅ 已定 |
| 生成模式 | 两种：**A** 只生成目标物品任务（挂 `ITEM_COLLECT`）；**B** 按材料树展开层级生成依赖任务链 | ✅ 已修订（实机验证（第 2 轮）） |
| 父任务触发器 | **合成任务（目标物与中间产物）挂自身的 `CRAFT_ITEM` 触发器**（累计合成量语义）：展开配方材料即代表这一步必须自己合成，材料齐了也要真的做出来，背包里已有的旧物品不会让它直接达标；**收集任务（最终材料）挂 `ITEM_COLLECT`**（持有量语义，已有即达标）。两类任务的完成态都只由「触发器达标」或「手动勾选」决定，触发器判定**优先于子任务聚合**（聚合逻辑跳过有触发器的父任务，见 §3.6） | ✅ 已修订（实机验证（第 4 轮）） |
| 催化剂 / 工具 | 标记排除，不计入数量（模具、桶等可复用物品） | 🕓 待定（点 1 可先不做） |
| 概率产出 | 取"必得最小值"（保守） | 🕓 待定（点 1 无概率配方，点 3 再定） |
| tag / 多候选输入 | **默认优先选择玩家背包里已有的候选材料**，其次取候选列表首项；预览界面提供「选择材料」列表可手动指定用哪一种，节点行同时显示「可选 N 种」 | ✅ 已实现（实机验证（第 3 轮）） |

### 3.3 新增组件（含落地状态）

| 组件 | 职责 | 状态 |
|---|---|---|
| `MaterialRecipeKind` / `MaterialIngredient` / `MaterialRecipe` | 中立模型（物品资源 ID 表示，与 MC 解耦） | ✅ Phase B |
| `MaterialRecipeIndex` | 产物 → 配方 反查索引 | ✅ Phase B |
| `MaterialRecipeSelector` | 多配方默认策略（D3） | ✅ Phase B |
| `MaterialRecipeResolver` + `VanillaMaterialRecipeResolver` + `MaterialRecipeIndexBuilder` | MC 配方 → 中立模型（7 种原版类型） | ✅ Phase B |
| `MaterialResolver` | 递归展开 + 数量传播 + 合并 + 循环/深度/节点保护 + 终止条件 | ✅ Phase C |
| `MaterialStopReason` / `MaterialNode` / `MaterialPlan` / `MaterialResolveOptions` | 展开树、终止原因与结果模型 | ✅ Phase C |
| `MaterialTaskGenerator` + `MaterialTaskMode` / `MaterialTaskContext` / `MaterialTaskTitleProvider` | 材料清单 → 任务（模式 A / 模式 B） | ✅ Phase D |
| `MaterialPreviewState` | 预览状态：目标/数量/模式、逐节点配方覆盖、手动终止与继续展开、勾选过滤 | ✅ Phase E1 |
| `MaterialListScreen` | 预览界面（树、切配方、改数量、勾选、模式选择、生成入口） | ✅ Phase E2 |

离线测试：`materialRecipeTest`（索引 + 选择策略 6 例）、`materialResolverTest`（反推核心 11 例）、`materialTaskGeneratorTest`（任务生成 8 例）、`materialPreviewStateTest`（预览状态 7 例）、`MaterialListScreenTestMain`（预览界面 11 例，已并入 `guiSystemTest`），均已挂 `:common:check`。

### 3.4 建任务链路（两种模式）

**模式 A：只生成目标物品任务**

```text
收集 铁块 ×8    ← trigger: ITEM_COLLECT minecraft:iron_block 8
```

适合"我就想要 64 个铁锭"这类场景，不建子任务，一条普通任务即可。

**模式 B：按展开层级生成依赖任务链**

```text
合成 铁块 ×8                    ← 合成任务，trigger: CRAFT_ITEM minecraft:iron_block 8
  └─ 收集 铁锭 ×72               ← 收集任务，trigger: ITEM_COLLECT minecraft:iron_ingot 72
```

若在预览中对「铁锭」点了「继续展开」，则再往下生成一层：

```text
合成 铁块 ×8                    ← 合成任务，trigger: CRAFT_ITEM minecraft:iron_block 8
  └─ 合成 铁锭 ×72               ← 合成任务，trigger: CRAFT_ITEM minecraft:iron_ingot 72
       └─ 收集 粗铁 ×72          ← 收集任务，trigger: ITEM_COLLECT minecraft:raw_iron 72
```

- 复用 `Task.addSubtask` + `setParentTaskId` + `subtaskSortOrder`，**不改任务模型**（扁平单指父指针，支持任意深度）；
- **目标物与中间产物为「合成」任务（挂 `CRAFT_ITEM`，累计合成量语义）；最终材料为「收集」任务（挂 `ITEM_COLLECT`，持有量语义）**。展开配方材料意味着一整条链路都要从原料开始合出来，所以合成步骤不会被背包里已有的旧物品直接判达标；只有最终材料允许"手上已经有了就算收集到"；
- 每条任务都由自身触发器决定完成态，**触发器判定优先于子任务聚合**，不会因为子任务全部完成而被自动完成；
- 挂触发器的合成任务支持**手动勾选**：勾选/取消直接切换该任务自身的完成态，不会被聚合逻辑改回去；
- 任务层级与预览中已展开的材料层级**一一对应**，未展开的层不会生成任务；
- **同种最终材料跨层级/分支重复出现时只生成一条「收集」任务**：挂在深度优先顺序里**首次出现**的位置，数量取该材料的汇总需求（如第 1 层与第 2 层各需 8 个木板 → 只生成一条「收集 橡木木板 ×16」）。否则多条任务各自用 `ITEM_COLLECT` 的持有量语义比较，背包里只有 8 个就会把两条都判为完成（Phase E9）；
- 被合并掉的层级不会再有该材料的子任务，因此中间产物任务可能没有对应的材料子任务；其完成态本就由自身触发器（`CRAFT_ITEM`）决定，不受影响；
- 玩家在预览中取消勾选的最终材料不生成任务；若某节点展开出的材料全部被取消勾选，该节点退化为最终材料，改为生成「收集」任务；
- 生成的任务全部落在**当前项目**下（已定）；`MaterialTaskGenerator` 只产出纯数据，不写存储、不发网络包，由预览界面确认后走现有保存链路；
- **团队作用域生成时不做自动指派**（已定）：生成后由玩家在任务列表里领取/指派；未领取前触发器不推进（Phase U 规则）。

### 3.5 预览界面（点 1 的主要工作量）

- 展示材料清单 / 树形结构；
- 支持切换配方（每个多配方节点给推荐默认值，可覆盖）、调整数量、勾选要生成哪些条目；
- 选择生成模式（A / B）；
- 与已有任务去重（同材料已有收集任务时，是累加数量还是新建子任务）；
- 预览阶段可反复调整，确认后才落库。

**Phase E2 实际形态**：

- 入口：任务列表底部快速新增行的「材料」按钮（与「触发」按钮同一行，不依赖选中任务）→ 复用 `ItemSelectorScreen` 选目标物品 → 打开 `MaterialListScreen`（标题为「材料配方预览」）；
- **默认只展开直接材料**：打开时只显示根物品配方所需的直接材料；选中某个可继续展开的节点后点「继续展开」才往下展开一层（`MaterialStopReason.FOLDED`）；
- 树渲染：`INDENT_WIDTH = 10`、`CHECKBOX_WIDTH = 12`、`ROW_HEIGHT = 18`，`flatten()` 递归展平后按行绘制**树形连线**（祖先层竖向连线 + `├─` / `└─` 横线）与勾选框 + 图标 + 名称 + 数量 + 右侧标签（配方类型或终止原因），层级关系直观可见；
- **多候选材料（物品标签/多选输入）**：行内名称后追加「可选 N 种」提示；打开界面时把玩家背包内容同步给状态层，默认优先选中**背包里已有的**候选，其次回退候选列表首项；选中该行后点「选择材料」可用只列出该材料全部候选的列表（复用 `ItemSelectorScreen` 的资源 ID 白名单）手动指定替代材料；
- 交互：点击勾选框切换勾选（只对叶子生效）、点击行选中节点、滚轮滚动、Esc 关闭、回车提交数量；
- **背包持有量标注（辅助判断是否需要继续准备）**：打开界面与每次重新解析时统计玩家背包（含快捷栏与副手，**不展开潜影盒等容器内容**）已有物品数量；行内数量与配方需求（含目标物品自身）比较，**达到或超过需求量时数量文本标绿**（`0xFF55FF55`）并追加绿色「已够」；不够时保持默认蓝（`0xFF9CDCFE`）并追加灰色「缺 N」（`N = 需求 − 该行可用持有量`）。标注只给"还能做什么"的信息，玩家不必自己做减法；**不只靠颜色区分**，新手也能直接读懂两种状态；
- **同种材料跨层级累计比较**：同一材料可能同时出现在多个层级（如第 1 层与第 2 层各需 8 个木板），此时按**自上而下、高层级优先占用**的顺序分配背包持有量——靠上的行先占掉 `min(已有, 需求)`，靠下的行只能拿剩余数量比较，避免每行单独看都"已够"、实际总量却不够；
- 同一份背包快照同时驱动「多候选材料背包优先」的默认选择；行宽不足时优先隐藏持有量标注，数量本身始终可见；
- 按钮：`modeButton`（A/B 模式切换）、`recipeButton`（多配方中间节点轮换，文案随节点类型变化）、`stopButton`（到此为止 / 继续展开 / 恢复默认 / 折叠回去）、`candidateButton`（选择替代材料）、`generateButton`；
- 生成结果通过 `TodoScreen.applyGeneratedMaterialTasks()` 走现有 `taskManager.addTask` + `markUnsaved()` + `applySearchFilter()` + 后台自动保存链路，落在**当前项目**下；团队作用域不自动指派。

### 3.6 D6：父任务完成态冲突（放宽 Phase V 的配套改造，必须先解决）

现有两套完成态机制会互相覆盖：

| 机制 | 行为 | 位置 |
|---|---|---|
| 子任务聚合 | 父任务的**直属子任务全部完成**时，父任务被置为 `completed = true`；只要还有未完成子任务，就被置回 `false` | `TaskManager.syncParentCompletionStates()` |
| 触发器 | 达标时 `task.setCompleted(true)` | `TaskTriggerService.completeTask()` |

聚合逻辑是**无条件**按子任务统计结果覆盖父任务完成态的，所以给父任务挂触发器后，只要聚合逻辑触发（`parentCompletionDirty` 置位后任意一次读），触发器设置的完成态就会被抹掉，表现为"父任务自动完成又自己变回未完成"。

**已定语义：触发器优先** —— 父任务挂了触发器时，`syncParentCompletionStates()` 跳过它，完成态完全由触发器决定；没有触发器的父任务维持现有聚合行为不变。

此外还需同步放开四处守卫（均在 Phase V 加的）：右键菜单显示项、`TriggerEditScreen` 编辑器、`/todo task trigger set` 命令、`TaskTriggerService.rebuildIndex` 索引收录，以及"新增首个子任务时清除父任务触发器"的逻辑。

### 3.7 已知取舍（Phase C 实现记录）

- **向上取整误差（偏保守）**：同一中间产物出现在不同分支时，会在各分支分别向上取整。
  例如「1 原木 → 4 木板」，两条分支各需 5 木板时，分别展开为 2 + 2 = 4 原木，而合并后只需 `ceil(10/4) = 3` 原木。
  当前实现已**在同一配方内先合并重复输入**（减少节点数与误差），但**不做跨分支的中间产物聚合**。
  要精确到全局最优需改成「先聚合再展开」的两阶段算法，本期不做；误差方向是偏多，不会导致任务无法完成。
- **节点数上限**：默认 `MaterialResolveOptions.DEFAULT_MAX_NODES = 2000`，超出时 `MaterialPlan.truncated` 置为 true，
  预览界面需提示"结果被截断"（对应风险 R1）。
- **深度上限**：默认 32 层，与路径循环检测（`MaterialStopReason.CYCLE`）共同防止死循环。

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
    for (Recipe<?> recipe : 该类型全部配方) {
        String recipeId = recipe.getId();                            // 1.20.1：配方自带 ID
        List<Ingredient> inputs = recipe.getIngredients();            // 通用输入
        ItemStack output = recipe.getResultItem(registryAccess);      // 通用输出
        // 过滤：输入为空 / 输出为空 / 输出等于输入的配方不参与
    }
}
```

> **T1 已核对（1.20.1，Phase B 编译验证）**：该项目映射下**不存在 `RecipeHolder`**，配方本身就是 `Recipe`，ID 走 `Recipe#getId()`；
> `RecipeManager#getAllRecipesFor(RecipeType)` 返回配方列表，`Recipe#getIngredients()` / `getResultItem(RegistryAccess)` 均可直接调用。
> 1.21.1 分支映射是否一致，需在该分支落地时再核对（1.21 起配方 API 有调整）。

### 5.2 覆盖边界

- **能覆盖**：实现了标准 `Recipe` 接口、且输入输出可被通用方法读出的模组机器配方；
- **覆盖不了**：非容器型配方（读不到输入）、多产物/概率产出、流体输入输出、序列组装类（如 Create 的 sequenced assembly）。这些属于 L4 专用 adapter 范畴，本期不做。

### 5.3 注册表设计（与点 1 共用）

> **实施补充（Phase B 落地后的实际形态）**：中立模型改用**物品资源 ID 字符串**而非 `ItemStack`，
> 这样索引、选择策略与递归展开都能离线构造数据并测试；只有 `VanillaMaterialRecipeResolver`
> 需要 MC 运行时（把 `Recipe` 转成中立模型）。
>
> ```text
> MaterialRecipeResolver {
>     Set<RecipeType<?>> supportedTypes()
>     MaterialRecipe resolve(Recipe<?> recipe, RegistryAccess registryAccess)
> }
> MaterialRecipe { String id; String outputItemId; int outputCount;
>                  List<MaterialIngredient> inputs; MaterialRecipeKind kind }
> MaterialIngredient { List<String> candidates; int count }   // 多候选 = 物品标签/多选输入
> ```
>
> 已落地的组件：`MaterialRecipeKind`、`MaterialIngredient`、`MaterialRecipe`、`MaterialRecipeIndex`、
> `MaterialRecipeSelector`（纯逻辑，离线测试）、`MaterialRecipeResolver`、`VanillaMaterialRecipeResolver`、
> `MaterialRecipeIndexBuilder`（MC 侧）。

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
| D1 | 中间产物是否生成任务 | 决定任务是否正确可完成 | ✅ 已定：不生成，只出叶子材料 |
| D2 | 目标任务能否自身追踪 | 目标任务能否挂触发器 | ✅ 已定：放宽 Phase V，父任务可挂 |
| D3 | 多配方选择策略 | 整棵材料树结果 | ✅ 已定：默认产出比最高（同比较优先 crafting），玩家可覆盖 |
| D4 | 概率产出取期望值还是必得最小值 | 数量准确性 | 🕓 待定（点 3 再定） |
| D5 | tag 输入按"任一"还是"全部"展开 | 数量准确性 | 🕓 待定 |
| D6 | 父任务完成态：聚合逻辑与触发器互相覆盖（见 §3.6） | 放宽 Phase V 的前置项 | ✅ 已定：触发器优先，聚合逻辑跳过有触发器的父任务 |
| D7 | 终止条件 | 决定铁锭会不会被推成铁粒 | ✅ 已定：熔炼类配方的输入即停止，玩家可改 |
| D8 | 熔炼燃料提示 | 数量准确性 | ✅ 已定：本期不处理 |
| T1 | `Recipe` 通用方法在 1.20.1 / 1.21.1 的可用性 | 点 3 可行性 | ✅ 1.20.1 已核对（无 `RecipeHolder`，用 `Recipe#getId()`）；1.21.1 待该分支核对 |
| R1 | 任务数量爆炸（复杂物品树可达数十~数百节点） | 需要聚合 + 勾选，否则污染任务列表 | 由 3.5 预览界面缓解 |
| R2 | 配方解析性能（大树上重算会卡 GUI） | 需反查索引 + 缓存，项目对卡顿敏感 | 由 `RecipeIndex` 缓解 |
| R3 | 实机验证不可离线覆盖 | token / 返工成本主要来源 | 需实机清单 |

## 8. 分步实现计划（全部未开始）

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase A | 语义拍板（D1~D3、D6~D8 已定；D4/D5 与 T1 留点 3） | ✅ 完成 |
| Phase A2 | **放宽 Phase V**：聚合逻辑改为"有触发器的父任务跳过" + 放开四处守卫 + 去掉"新增首个子任务时清除父任务触发器" + 触发器回归测试 | ✅ 完成 |
| Phase B | 点 1：`RecipeIndex` + `RecipeResolver` 注册表 + `VanillaRecipeResolver`（含 `MaterialRecipeSelector` 默认策略） | ✅ 完成 |
| Phase C | 点 1：`MaterialResolver`（递归/数量/合并/循环与深度保护/终止条件）+ 离线单测 | ✅ 完成 |
| Phase D | 点 1：`MaterialTaskGenerator` + 建任务链路（模式 A / B） | ✅ 完成 |
| Phase E1 | 点 1：`MaterialPreviewState`（预览状态层：配方覆盖 / 终止与继续展开 / 勾选过滤）+ 离线单测 | ✅ 完成 |
| Phase E2 | 点 1：`MaterialListScreen` 预览界面 + 任务列表入口接线 + GUI 测试 | ✅ 完成 |
| Phase E3 | **实机验证（第 2 轮）修订**：预览改「材料配方预览」并默认只展开直接材料（`FOLDED` + 「继续展开」）；预览树形连线展示；依赖链上重复子材料不展示；按展开层级生成多层依赖任务（合成任务不挂触发器 + 收集任务挂 `ITEM_COLLECT`） | ✅ 完成，`:common:check` 全量通过 |
| Phase E4 | **实机验证（第 3 轮）修订**：合成任务改为挂 `CRAFT_ITEM` 触发器，不再靠子任务聚合完成（材料齐了也要真的做出来）；`TaskManager` 支持手动勾选带触发器的父任务直接切换自身完成态 | ✅ 完成（Phase E6 曾改为 `ITEM_COLLECT`，Phase E7 已改回 `CRAFT_ITEM`） |
| Phase E5 | **多候选材料选择**：`MaterialResolveOptions` 新增候选覆盖与「背包已有物品」；`MaterialResolver` 按「玩家指定 &gt; 背包已有 &gt; 候选首项」选择替代材料；预览界面新增「选择材料」按钮（复用 `ItemSelectorScreen` 资源 ID 白名单）与「可选 N 种」提示 | ✅ 完成，`:common:check` 全量通过 |
| Phase E6 | **实机验证（第 3 轮）修订**：合成任务触发器由 `CRAFT_ITEM` 改为 `ITEM_COLLECT`（持有量语义），已有物品即达标，修复「明明有父任务物品却还在等子任务」；GUI 任务列表与 HUD 右侧进度改为**触发器进度优先**（挂触发器的父任务不再被子任务进度顶掉）；HUD 多层子任务改为树形连线展示（与材料配方预览一致） | ✅ 完成，`:common:check` 全量通过 |
| Phase E7 | **实机验证（第 4 轮）修订**：合成任务触发器**改回 `CRAFT_ITEM`**（展开配方材料即代表必须自己合成，背包已有旧物品不应直接判达标，撤销 E6 的做法）；`MaterialListScreen` 新增**背包持有量标注**（不含潜影盒，达标标绿 + 未达标追加「(已有 N)」）；HUD 树形连线加宽（`HUD_TREE_LEVEL_WIDTH` 6→10）并补正横向分支线，修复"只有竖线没有树形结构" | ✅ 完成，`:common:check` 全量通过 |
| Phase E8 | **实机验证（第 5 轮）修订**：持有量标注**增加文字提示**（「已够」绿色 / 不够时「缺 N」灰色，只给还需要准备多少），不再只靠颜色区分；新增**同种材料跨层级累计分配**（按显示顺序自上而下、高层级先占用背包数量，低层级只用剩余数量比较），并让数量本身在宽度不足时仍可见 | ✅ 完成，`:common:check` 全量通过 |
| Phase E9 | **实机验证（第 5 轮）修订**：`MaterialTaskGenerator` 新增 `collectFinalMaterialTotals()`，同种最终材料跨层级/分支重复出现时**只生成一条「收集」任务**（挂首次出现位置、数量取汇总需求），修复"多条收集任务共享同一份背包物品、只凑一半就全部完成" | ✅ 完成，`:common:check` 全量通过 |
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

1. 与已有收集任务去重时：累加数量，还是新建独立子任务？
2. D5：tag 输入按"任一"还是"全部"展开？
3. D4：概率产出取期望值还是必得最小值？（点 3 再定）
4. 点 2 的清单文本来源，是否已有目标模组（投影 / Create）的实际输出样本可参考？
