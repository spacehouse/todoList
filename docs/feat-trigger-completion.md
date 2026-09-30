# 实现方案：游戏内事件触发式完成任务

> 状态：验证分支 `verify/1.20.1-feat-trigger-and-item-title` 开发中
> 对应 TODO.md 计划项：【feat】游戏内事件触发式完成任务
> 更新日期：2026-09-13

## 1. 背景与目标

任务支持配置"游戏内事件触发条件"，当玩家在游戏中的行为达成条件（如击杀指定实体、持有指定物品达到数量）后，服务端自动推进进度并在达标时完成任务，无需手动打勾。

目标：

- 服务端权威判定（防作弊、支持多人）
- 进度持久化（重启不丢）
- 与现有 GUI / HUD / 网络同步链路复用，不另起炉灶
- 为后续附属 mod（投影材料列表、机械动力材料清单）提供"导入即追踪"的底座

## 2. 总体方案（如何实现）

### 2.1 数据模型

新增 `com.todolist.task.TaskTrigger`：

```text
TaskTrigger {
    type:        KILL_ENTITY | BREAK_BLOCK | CRAFT_ITEM | ITEM_COLLECT | ADVANCEMENT
    target:      资源 ID（如 minecraft:iron_ingot）
    targetCount: 目标数量（最小 1）
    progress:    当前进度（可变，随任务持久化）
}
```

挂载在 `Task.trigger`（可空字段）。序列化走 `Task.toNbt()/fromNbt()`，因此：

- 网络传输（`TaskPackets.writeTask/readTask` 走 NBT）自动携带，无需改包格式；
- 团队任务三态合并判断 `tasksEquivalent`（按 NBT 比较）自动感知进度变化；
- H2 行映射（`readTask` 构建 NBT → `Task.fromNbt`）自动还原。

### 2.2 持久化（H2-only，硬约束）

> 本项目已强制 H2 存储后端，NBT 文件存储模式已废弃。本特性只覆盖 H2 链路，不为文件存储做适配。

- `tasks` 表新增 4 列：`trigger_type VARCHAR(32)`、`trigger_target VARCHAR(256)`、`trigger_count INT NOT NULL DEFAULT 1`、`trigger_progress INT NOT NULL DEFAULT 0`
- schema 版本 v2 → v3，`H2SchemaUpgrader` 增加 `TaskTriggerColumnsUpgradeStep(2, 3)`（幂等 `ADD COLUMN IF NOT EXISTS`，升级前自动备份走现有链路）
- `H2TaskStore` 的 SELECT / INSERT / bindTask / readTask 四处同步加列
- 多语言 schema 注释键补 `h2.schema.comment.column.tasks.trigger_*`

### 2.3 服务端引擎 `TaskTriggerService`

新增 `com.todolist.trigger.TaskTriggerService`（common 包，零平台 import）：

- **主线程调度**：所有事件入口 `server.execute(...)` 调度到服务端主线程，与 GUI replace 包、命令保存天然串行，消除替换式保存的竞态；
- **桶级内存缓存**：`个人桶(LOCAL 或 玩家UUID) / TEAM 桶` → 任务列表 + `type+target → 任务列表` 倒排索引，事件 O(1) 命中不扫全量；
- **防抖落库（Phase S 起为增量）**：事件只改内存进度，3 秒防抖统一 flush；flush 只 `UPDATE` 桶内被推进过的任务行（脏任务集合），结构上不会覆盖其他路径保存的任务；服务器停止 `flushAll` 兜底；
- **缓存失效钩子（Phase S 起收口到存储出口）**：`H2TaskStore` 的四个全量保存出口（`saveLocalTasks`/`savePlayerTasks`/`saveLocalAndPlayerTasks`/`saveTeamTasks`）统一调用 `invalidateAllPersonal()`/`invalidateTeam()`，任何保存路径（含单人模式客户端直写本地 H2）都会失效引擎缓存，不再依赖调用点自觉接钩子；
- **完成联动与进度推送（Phase T 起为轻量增量）**：达标 → `task.setCompleted(true)` → 聊天通知玩家；进度推送按 250ms 短节流只发**变化任务**的 ID + 进度 + 完成态（`todolist:trigger_progress` 包），客户端就地更新内存任务列表并刷新 GUI/HUD，不再发送全量快照、也不再触发客户端全量落库。

### 2.4 判定语义（边界规则）

| 规则 | 说明 |
|---|---|
| 个人任务 | 仅任务归属玩家本人的事件推进 |
| 团队任务 | **必须已指派**：仅 assignee == 事件玩家可推进；未指派（待领取）任务不参与任何事件触发；**领取人变更时进度清零**（取消领取、改派他人后由新领取者从头开始）；其他成员行为不计入 |
| ITEM_COLLECT | **绝对持有量语义**：progress = 当前库存持有数（主背包+快捷栏+副手，并展开容器类物品的内容，只展开一层），达到 targetCount 即完成。天然防"丢出再捡回"刷进度，且合成/漏斗/奖励箱获得的物品同样有效 |
| KILL/BREAK/CRAFT/ADVANCEMENT | 累加语义：事件发生一次加一次（CRAFT 按合成产物数量加） |
| 已完成任务 | 不再参与事件匹配（防止取消勾选后又被事件自动勾回需重新计入） |
| 子任务触发器 | 达标只置子任务 completed；父任务完成态由现有展示层聚合逻辑处理，引擎不越权 |
| 父任务（已有直属子任务） | **允许设置触发器**（Phase X 放宽）：父任务挂触发器时完成态**触发器优先**，`syncParentCompletionStates` 跳过这类父任务，不再被子任务聚合覆盖；未挂触发器的父任务仍维持「子任务全部完成即完成」的聚合行为 |
| 挂触发器的父任务手动勾选 | **触发器优先下仍可手动勾选**：勾选/取消直接切换该任务自身完成态（`toggleTaskCompletion` 对带触发器的任务单独处理），不会被聚合逻辑改回；**级联覆盖全部后代（含带触发器的后代）**。例外：「我的」视图的批量子任务切换（`toggleDirectSubtasksForPlayer`）仍只作用于**当前玩家的、无触发器的直属子任务**——该路径下父任务完成态由子任务聚合决定，不会产生幽灵任务 |
| 完成父任务时的级联（Phase Y1） | **父任务完成 = 整条分支做完**：手动勾选（`TaskManager#toggleTaskCompletion`）与引擎达标（`TaskTriggerService#completeTask` → `CachedBucket#completeDescendants`）两条路径都级联完成**全部未完成后代**（含带触发器的后代）。级联只切换完成态、**不改写后代触发器进度**（进度是真实数据，取消勾选后能如实回落）；引擎侧同时把级联完成的任务移出倒排索引并置脏，走增量落库。原因：若跳过后代中的带触发器任务，会留下「父任务已完成、后代未完成」的**幽灵任务**——未完成列表只列顶层任务、已完成区只列已完成子任务，两侧都看不见，玩家既看不到也处理不了 |
| 材料任务生成 | 材料配方预览生成的任务**一律挂 `ITEM_COLLECT`**（持有量语义、拿到即达标）：最终目标是「拿到这个材料」，配方制作 / 购买 / 捡拾 / 交换都算达成，配方只作为**标题提示**——标题渲染为「动作词 `[产物]` ×数量」（如「熔炼 `[铁锭]` ×27」），无配方时为「收集 `[物品]` ×数量」。**标题不内联功能方块**（Phase E14）：HUD 行宽有限，方块图标 + 方块名会挤掉材料名；功能方块由材料配方预览的横向配方树承担展示。**整棵材料树里同种物品只生成一条任务**，挂在首次出现的位置、数量取全树汇总需求，避免多条任务共享同一份进度（Phase E9 → Phase E11 推广到全树） |
| 右侧进度展示 | **触发器进度优先**：挂触发器的任务（含父任务）右侧展示 `目标图标 + 进度`，不再被子任务聚合进度顶掉；未挂触发器的父任务仍显示子任务进度 `1/3`。GUI 任务列表与 HUD 一致（Phase E6） |
| HUD 多层层级 | 多层子任务不再只靠缩进，按依赖树绘制**树形连线**（祖先层贯穿竖线 + `├ / └` 分支线），与材料配方预览的树形展示一致（Phase E6） |

### 2.5 平台事件接入

common 引擎提供公开静态入口，平台桥接类订阅各自事件总线后调用（common 不 import 平台类）：

| 事件 | Forge 1.20.1 | Fabric 1.20.1 |
|---|---|---|
| 击杀实体 | `LivingDeathEvent`（判定 killer 为 ServerPlayer） | `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` |
| 破坏方块 | `BlockEvent.BreakEvent` | `PlayerBlockBreakEvents.AFTER` |
| 合成物品 | `PlayerEvent.ItemCraftedEvent` | `ResultSlotMixin` 注入 `ResultSlot#onTake`（背包 2x2 合成栏与工作台共用该槽位） |
| 物品收集 | 库存快照对比（每秒 tick 扫描，仅存在 ITEM_COLLECT 触发任务时） | 同左（`ServerTickEvents.END_SERVER_TICK`） |
| 获得进度 | `AdvancementEvent` | `PlayerAdvancementsMixin` 注入 `PlayerAdvancements#award`（Fabric API 无公开回调） |

### 2.6 用户入口（v1 命令）

```text
/todo task trigger set <任务ID> <type> <target> [count]   设置触发器
/todo task trigger clear <任务ID>                          清除触发器
/todo task trigger info <任务ID>                           查看触发器与进度
```

进度展示：GUI 任务详情与 HUD 在标题行后追加 `进度 x/y`；目标物品图标复用 feat 2 的标题标记渲染链路。

补充（Phase H/I）：除命令外，GUI 任务列表右键菜单提供「设置/编辑触发器」与「清除触发器」，点击进入 `TriggerEditScreen`；目标输入框右侧按钮会**按当前触发类型**打开对应选择器（物品 / 方块 / 实体 / 进度），避免手写资源 ID；切换类型时自动清空旧目标。命令的 `target` 参数已改用 `ResourceLocationArgument.id()`，`minecraft:iron_ingot` 这类带命名空间目标可直接解析，不再报「参数后应有空格分隔，但发现了紧邻数据」。

补充（Phase J/K）：
- **触发器优先建任务**：快速新增行新增「触发」按钮，先设触发器 → 保存后自动按目标生成任务标题（如「收集 铁锭 ×32」）并直接进入行内重命名，免去「先写标题再加触发器」的重复操作。
- **进度推送与落库解耦**：进度变化按 250ms 短节流用内存快照推送，HUD 不再等待 3 秒落库。
- **自动完成提示**：改为画面顶部卡片式浮动提示（简短状态文案 + 独立一行的「图标 + 名称」标题），不再使用聊天文本，避免长前缀挤占导致展示不全。

## 3. 分步实现计划

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase A | 数据层：TaskTrigger + Task NBT + H2 v3 升级 + 读写映射 + lang 注释 | ✅ 完成 |
| Phase B | 引擎：TaskTriggerService（缓存/索引/防抖/完成联动/失效钩子接线） | ✅ 完成 |
| Phase C | 平台事件桥：Forge 全量 5 类；Fabric 4 类 + CRAFT 用 Mixin（见 Phase M） | ✅ 完成 |
| Phase D | 命令入口 `/todo task trigger` + 通知消息 lang 键 | ✅ 完成 |
| Phase E | 进度展示（GUI 详情区 + HUD 追加段，复用 feat 2 渲染） | ✅ 完成 |
| Phase F | 离线测试（TestMain）+ `build-local-17.bat --offline` + 游戏内手工验证 | ✅ 完成（手工验证待用户执行，见 §8） |
| Phase G | 命令 target 参数改用 `ResourceLocationArgument`（修复命名空间报错） | ✅ 完成 |
| Phase H | GUI 右键菜单触发器编辑器 `TriggerEditScreen`（原 v2 提前实施） | ✅ 完成 |
| Phase I | 物品选择器 `ItemSelectorScreen`（ID + 本地化名双匹配，中文可搜） | ✅ 完成 |
| Phase J | 目标选择器泛化为物品/方块/实体/进度，编辑器按类型接入 | ✅ 完成 |
| Phase K | 触发器优先建任务（快速新增行「触发」入口 + 行内重命名） | ✅ 完成 |
| Phase L | 修复 ITEM_COLLECT 扫描死锁 + 触发器创建后立即评估持有量 | ✅ 完成 |
| Phase M | Fabric `ResultSlotMixin` 接入合成事件（移除 CRAFT 近似局限） | ✅ 完成 |
| Phase N | 浮动提示卡片式改造 + HUD 进度图标覆盖方块/合成/实体 | ✅ 完成 |
| Phase O | Fabric 进度事件接入 `PlayerAdvancementsMixin`；进度触发器数量锁定为 1；触发器建任务标题图标化 + 进度名解析 | ✅ 完成 |
| Phase P | 修复 `advanceMatchingTasks` 遍历倒排索引引发的 `ConcurrentModificationException`（达标瞬间中断事件回调） | ✅ 完成 |
| Phase Q | 修复 Mixin 可见性违规（去重入口抽到 `AdvancementAwardGate`），恢复进档 | ✅ 完成 |
| Phase R | 合成事件覆盖 Shift+左键取走（配方反查回退）；触发器编辑器显示目标本地化名称 | ✅ 完成 |
| Phase S | 引擎落库增量化（只 UPDATE 推进过的行）+ 缓存失效收口到存储保存出口，修复「新任务不触发/被旧缓存覆盖消失/触发卡顿」 | ✅ 完成 |
| Phase T | 进度推送轻量化：250ms 推送由「全量任务快照（发网络包 + 客户端全量落库）」改为「只发进度变化的任务 ID + 进度 + 完成态」，客户端就地更新不落库，消除每次事件触发的卡顿 | ✅ 完成 |
| Phase U | 团队任务语义收口：未指派（待领取）任务不再被事件推进，必须「领取/指派」后才参与触发，回归「创建 → 领取/指派 → 完成」流程 | ✅ 完成 |
| Phase V | 子触发任务与展示优化：快速新增「触发」按钮跟随选中任务（选中父任务时建子任务）；禁止为父任务设置触发器并在新增子任务时自动清除；任务列表右侧展示触发器进度与目标图标 | ✅ 完成 |
| Phase W | 进度选择器数据源收口：新增服务端权威进度目录包（`todolist:advancement_catalog`），「选择进度」改用服务端全量目录并每次打开重建；Forge 侧改为只监听 `AdvancementEarnEvent`，修复同一条进度弹两次完成提示 | ✅ 完成 |
| Phase X | **放宽父任务触发器限制**：父任务允许挂触发器，完成态改为「触发器优先」（`syncParentCompletionStates` 跳过有触发器的父任务）；移除右键菜单/编辑器/命令/引擎索引四道守卫与「新增子任务时清除父任务触发器」逻辑 | ✅ 完成 |
| Phase Y | **多层依赖与 HUD 完成态收口**（实机验证第 2 轮）：① 任务域支持任意层级——`syncParentCompletionStates` 改为自底向上多轮收敛、`deleteTask` 级联删除全部后代、放开「子任务不能再挂子任务」的三处守卫；② GUI 任务列表与 HUD 均按真实深度递归渲染并逐层缩进（不再只有 2 层）；③ HUD 完成态改为按**整棵子树**判定并统一走「触发器优先」，与 GUI 一致（子任务全完成后父任务不再被 HUD 误归入已完成）；④ HUD 中父任务被删除后其后代不再残留、第 3 层任务不再完成后消失 | ✅ 完成 |
| Phase Y1 | **幽灵任务修复**：父任务完成时**级联完成全部未完成后代（含带触发器的后代）**——手动勾选路径去掉 `toggleTaskCompletion` 里"跳过带触发器后代"的分支，引擎达标路径新增 `CachedBucket#completeDescendants`（并移出倒排索引 + 置脏增量落库）；级联只切换完成态、不改写触发器进度。修复"父任务已完成、后代未完成且在已完成/未完成列表中都看不见" | ✅ 完成（离线回归：`TaskManagerSubtaskTestMain.shouldCascadeToggleToTriggeredDescendants`、`TaskTriggerServiceTestMain.shouldCompleteDescendantsWhenParentCompletes`） |

## 4. 边界（本期不做什么）

- 不做 datapack JSON 谓词（biome/工具/附魔限定等），留 v2；
- ~~不做 GUI 触发器可视化编辑器（含物品选择器），留 v2~~ 已提前实施（Phase H/I），见 §2.6 补充；仍不做 datapack 谓词式的复杂条件编辑器；
- 不做团队任务的"多人合计计数"（所有人进度加在一起），v1 按 assignee 各自独立；团队任务必须**已指派**才可被事件推进，未指派（待领取）任务不参与任何触发事件（见 §2.4 与难点 17）；
- 不处理离线玩家（事件来源必须在线，离线 assignee 的团队任务不会推进）；
- 不做触发器达成的历史记录 / 撤销 / 回滚；
- 投影、机械动力附属 mod 不在本期（等本底座验证通过）；
- 不为已废弃的 NBT 文件存储模式做任何适配（H2-only 硬约束）。
- 收集类（ITEM_COLLECT）的容器内容只支持**原版标准容器 NBT 格式**（`BlockEntityTag.Items`，如潜影盒及走同一套格式的模组容器），且只展开一层；用自有 NBT 结构或平台能力（如 Forge `IItemHandler`）暴露库存的模组背包（如旅行者背包）不计入，需按模组单独适配，不在本期范围。
- 击杀类的进度图标按「实体 ID + `_spawn_egg`」约定降级解析，模组实体若不符合该命名约定则不显示图标（不影响触发判定）。

## 5. 难点与对策

1. **替换式保存的竞态**：GUI 保存是"全量替换桶"，引擎若持有旧缓存直接写回会覆盖 GUI 修改。→ 对策：引擎与 GUI 保存全部在服务端主线程串行执行 + 三处失效钩子，缓存失效后下次事件重新 load。
2. **高频事件写库压力**：材料类任务每秒可触发几十次库存变化。→ 对策：内存累加 + 3 秒防抖 + 停服 flush 兜底；事件路径零 SQL。
3. **`PICKUP_ITEM` 语义陷阱**：拾取事件 ≠ 获得物品（合成产出、漏斗、奖励箱不走该事件）。→ 对策：ITEM_COLLECT 用绝对持有量 + 库存快照对比，不订阅拾取事件。
4. **库存快照成本**：每次对比需遍历背包。→ 对策：仅当存在未完成的 ITEM_COLLECT 触发任务时才启用扫描；无触发任务零开销。
5. **Fabric 无合成事件**：Fabric API 未暴露公开合成回调。→ 对策（Phase M）：新增 `ResultSlotMixin` 注入 `ResultSlot#onTake`，背包 2x2 合成栏与工作台共用该槽位，一次注入即覆盖两种合成方式；已通过 `refmap`（`todolist.mixins.refmap.json`）解决生产环境重映射，jar 内已确认包含 Mixin 类与 refmap。
6. **进度同步时机**：每次事件都全量 sync 网络包太重。→ 对策（Phase J 调整）：落库仍为 3 秒防抖，推送拆成独立的 250ms 短节流且只发内存快照、零 SQL；完成瞬间立即落库并推送。
7. **团队任务并发推进冲突**：多成员同时做同一任务。→ 对策：assignee 限定 + 主线程串行天然解决。
8. **ITEM_COLLECT 扫描死锁**：桶只在事件发生时懒加载，而 `hasItemCollectWork` 原本只读缓存（`peekBucket`），导致纯捡取（无任何游戏事件）时桶永不存在、库存扫描永不启用——只有当玩家恰好挖了方块/杀了怪（触发其他类型事件）把桶加载起来后，收集类任务才开始生效。→ 对策（Phase L）：`hasItemCollectWork` 改走 `getOrLoadBucket`，扫描首次调用即完成加载；同时新增 `evaluateItemCollectAfterTriggerChange`，在命令/ GUI 保存触发器后立刻评估一次持有量，使「玩家已持有足量物品」的新任务能马上完成。
9. **达标瞬间的 `ConcurrentModificationException`（Phase P，根因级缺陷）**：倒排索引存的就是桶内任务的**实时列表**，`advanceMatchingTasks` 直接 `for-each` 该列表；一旦某任务达标，`completeTask → removeIndex → group.remove(task)` 就在遍历中途改列表，下一次 `next()` 立即抛 CME。异常从 `server.execute` 回调里抛出后被 `MinecraftServer` 捕获记录，**达标之后的落库、通知、推送、团队桶处理全部被跳过**；而索引条目已被移除，后续同类事件再也命中不到该任务。玩家侧看到的就是「任务永远不完成、进度也不变」。→ 对策：候选列表改为 `new ArrayList<>(bucket.find(...))` 遍历副本。判定依据来自玩家提供的运行日志（`latest.log` 中 3 处 `Error executing task on Server ... ConcurrentModificationException` 均指向 `TaskTriggerService.advanceMatchingTasks`）。
10. **Fabric 无进度事件**：Fabric API 未暴露公开的进度达成回调。→ 对策（Phase O）：新增 `PlayerAdvancementsMixin` 注入 `PlayerAdvancements#award`，在 `RETURN` 处用 `getOrStartProgress(...).isDone()` 判定该进度是否真正完成，完成才上报；用「玩家 UUID + 进度 ID」集合去重，避免多条件进度在校验过程中重复上报，玩家断线时清理去重记录。
11. **Mixin 可见性硬约束（曾导致进档即崩，Phase Q）**：Mixin 要求被注入类里**不能出现非 private 的静态方法**（`MixinApplicatorStandard.checkMethodVisibility`）。初版把去重集合的清理入口 `public static clearReportedFor(UUID)` 直接写在 `PlayerAdvancementsMixin` 里，运行期在 `ServerPlayer` 构造中加载 `PlayerAdvancements` 时抛 `InvalidMixinException: contains non-private static method`，进而 `Couldn't place player in world`，客户端表现为「进入世界提示无效的玩家数据、存档列表异常」。**该错误编译期与单元测试都发现不了，只有真正进档才暴露**。→ 对策（Phase Q）：去重状态与清理入口抽到普通类 `AdvancementAwardGate`（`com.todolist.fabric`），Mixin 内只保留 private 字段 + private 注入方法；两个 Mixin 类已用 `javap -p` 复核仅含 private 成员。
12. **Shift+左键取走合成产物时参数堆为 `minecraft:air`（Phase R）**：1.20.1 中 Shift 取走走 `InventoryMenu#quickMoveStack`，产物在调用 `onTake` 前就被 `moveItemStackTo` 搬进背包，传入堆**连物品类型都丢失**（实机日志证实 `item=minecraft:air count=0 removeCount=0`），`@Shadow removeCount` 也为 0，无法从参数与字段获得任何信息。→ 对策（Phase R）：`ResultSlot#onTake` 的方法体在注入点（HEAD）之后才消耗合成网格材料，因此 HEAD 处 `craftSlots` 仍完整 —— 用原版同一套 `RecipeManager#getRecipeFor(RecipeType.CRAFTING, craftSlots, level)` 反查产物；鼠标取走仍优先用传入堆（精确），仅参数为空堆时走配方反退，数量取配方产物数（原木 → 木板 ×4）。两种取走方式均已实机验证推进正常且不重复计数。
13. **触发器编辑器的目标可读性（Phase R）**：目标输入框按设计保存的是资源 ID（技术值），zh_cn 下只有 tag 对用户不友好。→ 对策（Phase R）：面板加高至 202，在目标输入框下方渲染 `↳ <本地化名称>`（复用 `TriggerTargetSupport.resolveTargetDisplayName`，覆盖物品/方块/实体/进度四类目标），右对齐、浅绿色；解析失败或与资源 ID 相同时不渲染，避免重复信息。
14. **引擎旧缓存覆盖新任务 + 失效钩子遗漏（Phase S，曾表现为「新任务不触发 / 新任务直接消失 / 每次触发卡顿」）**：三个症状同根。单人模式下 GUI 保存走 `ClientTaskStorageHelper` **客户端直写本地 H2**（不发网络包），服务端引擎桶缓存无从得知；此前失效钩子只接在 `TaskPackets.handleReplaceTasks` 与命令 trigger set/clear 三个点上，直写路径与其余命令路径全部遗漏 → 引擎桶永远是旧列表（新任务不进索引、不触发），且事件推进触发 3 秒防抖落库时用**旧缓存列表全量替换** tasks 表 → 其他路径刚保存的新任务被抹掉；250ms 节流推送也把旧快照发给客户端 → GUI 上新任务「消失」。判定依据：Forge 实机日志中引擎桶全程只加载一次（invalidate 从未生效），DB 与客户端任务数不一致。「每次触发卡顿」则是全量替换式落库（上千行 DELETE+INSERT）在主线程的开销。→ 对策（Phase S，三管齐下）：
    - **落库增量化**：`H2TaskStore.updateTriggerStates` 按任务 ID 批量 UPDATE `trigger_*` 与 `completed` 列（天然覆盖本地/玩家双桶副本），`CachedBucket` 维护脏任务集合，引擎 flush 只写推进过的行——结构上不可能再覆盖新任务，且把上千行替换写降为个位数行 UPDATE，消除卡顿；
    - **失效收口**：`H2TaskStore` 的四个全量保存出口（`saveLocalTasks`/`savePlayerTasks`/`saveLocalAndPlayerTasks`/`saveTeamTasks`）统一调用 `TaskTriggerService.invalidateAllPersonal()`/`invalidateTeam()`——任何路径的全量保存必然失效引擎缓存，不再依赖每个调用点自觉接钩子；
    - **失败恢复**：增量落库失败时恢复脏集合等待重试，进度不丢。
15. **每次事件触发仍卡顿：高频全量快照推送（Phase T）**：Phase S 已把落库降为个位数行 UPDATE，但玩家实测「收集物品、获得成就、击杀实体、破坏方块」触发时仍卡一下。根因在**推送链路**：250ms 节流到点时，`maybePushThrottled` 调 `sendPersonalTasksSnapshot` / `broadcastTeamTasksSnapshot` 发送**全量任务快照**（实测 ~1100 个任务、约 34KB），客户端收到后又走 `ClientTaskStorageHelper.savePersonalTasks` / `saveTeamTasks` **全量写本地 H2 双桶**（整桶 DELETE+INSERT）。也就是说：一次事件 → 一次全量网络包 + 一次客户端全量落库，且这套开销与「实际变化了几个任务」无关。→ 对策（Phase T，三条链路同时轻量化）：
    - **服务端只发变化**：新增轻量包 `todolist:trigger_progress`（`TaskPackets.TRIGGER_PROGRESS_ID`），载荷仅为 `(团队标记, [(任务 ID, 进度, 完成态)])`，数据来源是引擎桶的脏任务集合 `peekDirtyTasks()`；
    - **推送是旁路，不消费脏集合**：`peekDirtyTasks()` 只读、不清空 `dirtyTaskIds`，保证随后 3 秒防抖的增量落库仍能拿到全部变化任务（`takeDirtyTasks()` 才消费；否则推送会把落库的任务吃掉、进度丢失）；
    - **客户端就地更新、零落库**：`TodoScreen.applyTriggerProgress` 按任务 ID 直接改内存中 `cachedPersonalTasks`/`cachedTeamTasks` 的 `trigger.progress` 与 `completed`，命中后刷新 HUD/打开中的 GUI（并置父任务完成态为脏以触发重新聚合）；单人模式下本地 H2 由 GUI 保存与引擎增量落库各自负责，轻量推送不再触发任何全量写；
    - **两条推送路径统一**：除 250ms 短节流外，3 秒防抖落库后的 `flushBucket(pushSync=true)` 原先也走 `syncTasksToPlayer`/`broadcastTeamTasks` 全量快照（服务端多读一次库 + 客户端全量写本地 H2），现已一并改为发送同一批脏任务的轻量增量包；`completeTask` 也改为 `flushBucket(..., true)`（立即增量落库 + 同一批变化的轻量推送 + 完成提示包），全链路不再有全量快照。
16. **完成任务的那一刻仍卡顿（Phase T 收尾）**：进度更新已经不卡后，玩家实测「达标完成的瞬间」仍会顿一下。根因是达标路径单独保留了全量同步：`completeTask` 调 `flushBucket(..., false)` 落库后，仍调 `TaskPackets.syncTasksToPlayer` / `broadcastTeamTasks` 发送全量任务快照（服务端 `loadPersonalTasks` 读库 + 全量序列化，客户端整桶重写本地 H2），且该开销与任务总量成正比。→ 对策：`completeTask` 改调 `flushBucket(server, bucket.key, true)`，由 `flushBucket` 统一完成「增量 UPDATE + 同一批脏任务的轻量进度推送」，再补一个轻量的完成提示包（`TRIGGER_COMPLETED_ID`，只带标题）；至此 250ms 节流、3 秒防抖落库、达标完成三条路径的推送全部为增量。
17. **未指派（待领取）团队任务被事件推进（Phase U）**：初版 `canPlayerAdvanceByUuid` 把「未指派」视为「谁都可以推进」（`assignee == null || assignee.isEmpty() || assignee.equals(playerUuid)`）。这直接架空了团队任务的「创建 → 领取/指派 → 完成」流程——待领取的任务会被任何人顺手做的一个动作推进共享进度，待领取状态失去意义。→ 对策：团队任务必须**已指派**，即 `assignee != null && !assignee.isEmpty() && assignee == 事件玩家` 才可推进；未指派任务不参与累加推进、不参与 ITEM_COLLECT 重算，也不再让 `hasItemCollectWork` 为其开启库存扫描（避免无意义扫描）。有权限的玩家仍可在 GUI/命令中**手动完成**任务，该路径不经过引擎判定，不受本约束影响。
18. **领取人变更后进度被下一位领取者继承（Phase U）**：配合上一条，团队任务取消领取（回到待领取）或改派给他人时，原领取者已累计的进度会留给下一个人，等于"别人替你做完一半"。→ 对策：`TaskTriggerService.resetTriggerProgressOnAssigneeChange(previous, incoming)` 按任务 ID 比对领取人，凡「从无到有」「从有到无」「A → B」都清零 `trigger.progress` 并撤销 `completed`，个人任务与未变更任务不受影响。落点上必须覆盖两条保存路径，且重置要发生在引擎进度合并**之后**（否则会被合并结果覆盖）：
    - **服务端保存入口**（防线）：`TaskPackets.handleTeamReplaceTasks` 按库里已存状态比对；命令路径 `CommandBootstrap.saveProjectTasksForCommand` 的团队分支也是「先 `mergeTeamTriggerStateInto` 保留引擎进度、再比对领取人清零」，与网络包路径一致（此前该分支缺合并，命令保存会覆盖尚未落库的进度）；
    - **GUI 变更点**（`TodoScreen.assignAssigneeWithTriggerReset`）：局域网主机「已发布局域网」模式下的 GUI 保存会**直接写入本地 H2、不经过服务端保存包**（`shouldUsePublishedLocalPlayerStorage`），因此必须在点击领取/取消领取时就地清零，否则该路径会漏掉。
19. **父任务挂触发器与子触发任务入口不便（Phase V）**：两个相关交互问题。① 子任务加触发器要「建子任务 → 输入名称 → 双击 → 点按钮」四步，且空白子任务会被自动保存/丢弃逻辑删掉，实际很难走通；② 已有子任务的父任务也能设触发器，而父任务完成态由子任务聚合，触发器语义冲突。→ 对策：
    - **入口跟随选中任务**：顶部快速新增行的「触发」按钮复用同一套流程，但会读取当前选中项——选中可承载子任务的顶层任务时，按钮文案变为「子触发」并把新建任务挂到该父任务下（自动生成标题 + 直接进行内重命名）；未选中时行为不变（建顶层任务）。按钮语义在点击前可见，避免层级歧义；
    - ~~**父任务禁止设触发器**：右键菜单对已有直属子任务的父任务不再显示「设置/编辑触发器」，`openTriggerEditScreen`/`applyTriggerFromEditor` 与命令 `todo task trigger set` 各加一道守卫，引擎 `rebuildIndex` 也不收录此类任务（历史残留触发器同样不生效）；~~ **已在 Phase X 撤销**：材料反推需要父任务追踪目标物持有量，改为「父任务可挂触发器 + 完成态触发器优先」；
    - ~~**新增子任务时自动清除父任务触发器**：父任务一旦有了子任务，其触发器不再有意义，创建第一个子任务时清除并弹出提示，避免留下「已设置但无效」的困惑状态。~~ **已在 Phase X 撤销**：该逻辑已删除，父任务触发器在新增子任务后继续生效。
20. **点击顶部快速新增「触发」按钮会丢失选中态（Phase V）**：`TodoScreen.mouseClicked` 的自定义命中判定（`TodoScreenHitTestSupport.isClickInEditArea`）只登记了输入框与详情面板按钮，**没有登记快速新增行的两个按钮**，于是点击「触发」被判定为「点了空白处」→ 先执行 `auto_clear_selection` 自动保存并关闭详情区，按钮语义尚未生效选中任务就没了（入口本就依赖选中项，等于直接失效）。→ 对策：把 `quickAddItemButton`/`quickAddTriggerButton` 纳入编辑区命中集合，点击它们不再清空选中；该修复同时消除了「插入物品」按钮的同类问题。
21. **「选择进度」在新存档下恒为空（Phase W）**：两个原因叠加。① 客户端 `ClientAdvancements` **不是全量目录**——服务端按 `AdvancementVisibilityEvaluator` 的可见性规则增量下发（完成态 + 祖先完成度 + `hidden` 标记，可见深度仅 2 层），新存档下客户端只有个位数进度，且已解锁的配方进度还没有展示信息；② `ItemSelectorScreen.CACHE` 是 `static final`，首次打开时若目录为空就把空列表**永久缓存**，之后即便达成进度、重新打开也仍为空。→ 对策：
    - **服务端权威目录**：服务端从 `MinecraftServer#getAdvancements()` 全量导出「带展示信息」的进度（ID + 展示名），随登录后的任务同步一并下发新包 `todolist:advancement_catalog`；客户端缓存在 `AdvancementCatalog`；
    - **不做空结果缓存**：进度类型的选择列表每次打开都重建（物品/方块/实体仍沿用注册表静态缓存，与存档无关）；
    - **缓存与存储域绑定**：`AdvancementCatalog` 记录下发时的 `DataPathProvider.getStorageNamespace()`，切换存档/服务器后旧目录立即失效，并在客户端断开时清空，避免跨存档串数据；
    - **任务标题回退**：`TriggerTargetSupport.resolveAdvancementTitle` 先取客户端已加载进度（客户端语言），未覆盖时回退到服务端目录的展示名，避免标题回退成资源 ID。
22. **Forge 下同一条进度弹出两个完成提示（Phase W）**：`AdvancementEvent` 是基类，Forge 在 `PlayerAdvancements#award` 中会为同一次达成投递**两个**子类事件——先 `AdvancementProgressEvent`（GRANT，条件递增），进度真正完成时再 `AdvancementEarnEvent`。监听基类会把两个事件都收进来，于是同一条进度被推进两次、播放两个完成提示（Fabric 侧走 Mixin 注入，天然只有一次）。→ 对策：`ForgeGameEventBridge.onAdvancement` 的参数类型改为 `AdvancementEvent.AdvancementEarnEvent`，只处理「真正达成」的那一次。

## 6. 备选方案对比

| 方案 | 结论 | 理由 |
|---|---|---|
| A（采用）内存缓存 + 主线程串行 + 防抖落库 | ✅ | 事件路径零 IO，竞态用线程模型消除，改动集中在引擎单类 |
| B 每事件直接读改写 H2 | ❌ | 高频事件 IO 不可接受，H2 写放大 |
| C 独立 `task_triggers` 表 | ❌ | 与"替换式保存"语义冲突（整桶 DELETE+INSERT 会丢触发器行），需要双表事务对齐，复杂度不成比例 |
| D 客户端本地判定进度 | ❌ | 多人模式作弊面大；团队任务无法服务端仲裁；与"服务端权威"目标冲突 |
| E 原版进度(Advancement)判据复用 | ⏸ | 表达力最强但接入重、玩家进度页污染明显，v2 评估 datapack 谓词时一并考虑 |

## 7. 任务跟踪

- [x] 创建验证分支 `verify/1.20.1-feat-trigger-and-item-title`
- [x] `TaskTrigger` 实体（NBT 序列化、进度收敛、合法性校验）
- [x] `Task.trigger` 字段 + toNbt/fromNbt + getter/setter
- [x] `H2SchemaInitializer`：tasks 建表加列、SCHEMA_VERSION=3、注释条目
- [x] `H2SchemaUpgrader`：`TaskTriggerColumnsUpgradeStep(2,3)`
- [x] `H2TaskStore`：SELECT/INSERT/bindTask/readTask 加列
- [x] zh_cn/en_us schema 注释键
- [x] `TaskTriggerService` 引擎（缓存、索引、防抖、完成联动、flushAll）
- [x] `TaskPackets.handleReplaceTasks` / `handleTeamReplaceTasks` 失效钩子（含保存前进度合并）
- [x] Forge 事件桥 `ForgeGameEventBridge`（5 类事件 + 库存扫描 + 退出 flush）
- [x] Fabric 事件桥 `FabricGameEventBridge`（KILL/BREAK/ITEM_COLLECT + 退出 flush；CRAFT 与 ADVANCEMENT 见局限）
- [x] `EventBootstrap.handleServerStopped` 接 flushAll
- [x] `/todo task trigger set|clear|info` 命令
- [x] 通知/帮助 lang 键（zh_cn + en_us）
- [x] HUD 触发进度展示（文本进度 + ITEM_COLLECT 目标图标）
- [x] 离线测试：TaskTrigger NBT 往返（taskTriggerTest）、`:common:check` 全量通过（含 H2 schema v3 幂等与升级）
- [x] 命令 `target` 参数改用 `ResourceLocationArgument.id()`，修复带命名空间目标解析报错
- [x] 命令离线回归：`shouldSetItemCollectTriggerWithNamespacedTargetSuccessfully`（`minecraft:iron_ingot` 完整解析 + 数量 32）
- [x] GUI 右键菜单「设置/编辑触发器」「清除触发器」+ `TriggerEditScreen` 编辑器
- [x] GUI 回归：`shouldClearTriggerFromContextMenu`（commandSystemTest / guiSystemTest 全绿）
- [x] 物品选择器 `ItemSelectorScreen`（ID + 本地化名双匹配；快速新增行与任务行内编辑「+」插入入口）
- [x] 触发进度推送与 3 秒落库解耦：`maybePushThrottled` 按 250ms 用内存快照推送（`sendPersonalTasksSnapshot` / `broadcastTeamTasksSnapshot`），HUD 不再等待落库
- [x] 自动完成提示改为客户端浮动提示条：`TaskPackets.TRIGGER_COMPLETED_ID` + `TodoToastRenderer`（真实物品图标 + 名称，与标题渲染一致），替换原聊天文本
- [x] 物品选择器「取消」按钮移至右侧，不再遮挡左下角总数文本
- [x] 目标选择器泛化为 `Kind`（物品/方块/实体/进度），编辑器按触发类型接入并清空旧目标；回归 `TriggerEditScreenTestMain.shouldMapTriggerTypeToSelectorKind`
- [x] `TriggerTargetSupport`（图标 ID / 目标显示名 / 默认任务标题）；回归 `TriggerTargetSupportTestMain`
- [x] HUD 进度图标覆盖 `BREAK_BLOCK`（方块物品形态）/`CRAFT_ITEM`/`KILL_ENTITY`（刷怪蛋约定降级）
- [x] 浮动提示卡片式改造：简短状态文案 + 标题独立一行（不再被长前缀挤占截断）
- [x] 修复 ITEM_COLLECT 扫描死锁（`hasItemCollectWork` 改走 `getOrLoadBucket`）+ `evaluateItemCollectAfterTriggerChange` 触发后立即评估
- [x] Fabric `ResultSlotMixin` 接入合成事件，`todolist.mixins.refmap.json` 已生成并打入 jar
- [x] 触发器优先建任务：快速新增行「触发」按钮 → 自动生成标题 + 行内重命名
- [x] 触发器建任务标题显示物品图片：`resolveTitleTargetToken` 对物品/合成/破坏方块目标生成 `[item:...]` 标记（复用 feat 2 渲染链路）
- [x] 进度触发器的任务标题解析进度显示名：`resolveAdvancementTitle` 从 `ClientAdvancements` 取 `getDisplay().getTitle()`，不再回退成资源 ID tag
- [x] 进度触发器数量锁定为 1 且不可编辑（`applyTypeDependentWidgetState` + `save()` 强制取 1）
- [x] Fabric 进度事件：`PlayerAdvancementsMixin`（注入 `award` + `isDone` 判定 + 去重 + 断线清理），`todolist.mixins.json` 注册
- [x] 修复 `advanceMatchingTasks` 的 `ConcurrentModificationException`（候选列表遍历副本），恢复达标后的落库/通知/推送链路
- [x] 修复 Mixin 可见性违规：去重入口抽到普通类 `AdvancementAwardGate`，Mixin 类只保留 private 成员（否则进档即 `InvalidMixinException`）
- [x] 合成事件覆盖 Shift+左键：参数堆为空时按 `craftSlots` 配方反查产物（Phase R），鼠标取走与 Shift 取走均实机验证通过
- [x] 触发器编辑器目标输入框下方显示本地化名称（`renderResolvedTargetName`，四类目标通用）
- [x] 移除 CRAFT_ITEM 排查用的临时 debug 日志（`ResultSlotMixin` / `TaskTriggerService`）
- [x] 引擎落库增量化：`H2TaskStore.updateTriggerStates` + `CachedBucket` 脏任务集合，flush 只 UPDATE 推进过的行
- [x] 缓存失效收口：`H2TaskStore` 四个全量保存出口统一失效引擎缓存（含单人模式客户端直写路径）
- [x] 增量落库失败恢复脏集合，进度不丢
- [x] 进度推送轻量化：`todolist:trigger_progress` 只发变化任务（ID + 进度 + 完成态），客户端就地更新内存、不再全量写本地 H2；`peekDirtyTasks` 只读不消费脏集合；250ms 节流、3 秒防抖落库后、达标完成三条路径的推送统一为增量（Phase T）
- [x] 团队任务必须已指派才可被事件推进：未指派（待领取）任务不参与累加推进与 ITEM_COLLECT 重算，也不触发库存扫描；手动完成不受影响（Phase U）
- [x] 团队任务领取人变更时进度清零：取消领取/改派他人后由新领取者从头开始；覆盖服务端保存入口（网络包 + 命令）与 GUI 变更点（已发布局域网的客户端直写路径）（Phase U）
- [x] 命令的团队保存分支补齐 `mergeTeamTriggerStateInto`，与网络包/个人命令路径一致，避免命令保存覆盖引擎尚未落库的进度（Phase U 顺带修复）
- [x] 快速新增「触发」按钮跟随选中任务：选中父任务时创建为其子任务（文案变「子触发」），未选中时仍建顶层任务（Phase V）
- [x] 修复快速新增行按钮点击被误判为空白点击、导致选中态丢失与详情区关闭（`isClickInEditArea` 登记两个按钮）（Phase V）
- [x] 禁止为已有子任务的父任务设置触发器（右键菜单 + 编辑器 + 命令 + 引擎索引四道），新增首个子任务时自动清除父任务触发器并提示（Phase V）
- [x] 任务列表右侧展示触发器进度与目标物品图标（父任务子任务进度优先；与负责人并存时形如 `@名字 12/64`）（Phase V）
- [x] `build-local-17.bat build --offline` 双平台 jar 产出（fabric/forge 1.20.1-1.4.2）并归档 `dist/`
- [x] 放宽父任务触发器限制（Phase X）：父任务允许挂触发器、完成态改为触发器优先（`syncParentCompletionStates` 跳过有触发器的父任务）；移除右键菜单/编辑器/命令/引擎索引四道守卫与「新增子任务时清除父任务触发器」逻辑；`TaskTriggerServiceTestMain.shouldAdvanceParentTaskWithSubtasks` 与 `TaskManagerSubtaskTestMain.shouldKeepTriggeredParentCompletionFromBeingOverwritten` 固化
- [ ] 游戏内手工验证清单（见 §8，待用户在游戏环境执行）
- [ ] **同物品任务额度配额（Phase Z，方案已定、待实现）**：同一项目内多条任务追踪同一种物品 / 同一目标时，不再被同一份物品或同一批事件同时满足；**按任务列表顺序（从上往下）分配额度**，拖拽排序即调整优先级；设计稿见 `feat-trigger-credit-allocation.md`
  - [ ] 验收标准：三条不变量 I1 已完成不回退 / I2 同一次分配不重复吃额度（两类 `availableResource` 定义不同：收集类 `max(0, held − used)`、累加型 `批次 amount`，且累加型停止条件是"需求耗尽或 `pool` 用尽"）/ I3 判定只由持久化顺序决定（`sortByPriority`、折叠、搜索均不得影响结果）；另加实现级不变量 I4：`0 ≤ pool`、`0 ≤ progress ≤ targetCount`（既有 `clampProgress` 保证）、`completed == (progress == targetCount)`（**双向，需新增**，靠"自动完成写满 + 所有完成入口补写 + `Task.fromNbt` 双向加载兜底"三处共同保证）
  - [ ] Z1 `TriggerCreditAllocator` 纯逻辑（**值输入 → 值输出，不持有 / 不修改 `Task`**；`progressByTaskId` 覆盖输入的全部候选 = 全量结果而非变更结果；分组 = 项目 + 类型 + 目标身份 + 负责人，`owner` 来源唯一化：个人任务取 `CachedBucket.playerUuid`、团队任务取 `assigneeUuid`，禁止 ambient context；重算入口按 **quota group** 而非单任务命名，输入拆分为 `StateQuotaSnapshot` / `EventBatchQuotaSnapshot`、输出 `AllocationResult` 的接口契约在本阶段冻结）+ OrderKey **Segment 路径序**（顶层 `(sort_order, created_at, id)`、子层 `(subtask_sort_order, id)`，每层 tie-break 必须落在比较键里；子层需显式排序，H2 的 `ORDER BY` 不含 `subtask_sort_order`）+ "`used` 取自桶内全量任务列表而非触发器索引"的取数入口 + 前置条件（`targetCount ≥ 1`、`progress ∈ [0, targetCount)`、`used / held / amount ≥ 0`、候选已属同一组）+ **三态 `quota participation`（`STATE` / `EVENT_BATCH` / `NONE`，`ADVANCEMENT` 属 `NONE`）** + 离线单测
  - [ ] Z2 接入收集类（组级重算入口 `recalculateCollectQuotaGroup`：`pool = max(0, held − used)` 两遍分配，第一遍写满 `progress`、第二遍用第一遍之后的未完成状态填充展示）+ 额度相关变更立即重算，**`groupKey` 可空**（无触发器 / 未指派 = 无组，不是伪 group），受影响组按 **`distinct(非空(beforeGroup), 非空(afterGroup))`** 去重后按 **`switch(quotaMode)` 三态**处理（覆盖触发类型变更（含 `ADVANCEMENT` 的 `NONE` 分支）、目标 identity 变更、指派改派 / 领取 / 取消领取、**触发器新增与删除**；跨类时旧收集组重算、新累加组只置 `progress = 0`）+ **重算只在外部事务提交点派发一次**（事务内自动完成不反向派发；父任务批量勾选先改完状态再聚合重算；重算稳定后才落库推送，且按 diff 回写；**GUI 本地直接写 H2 的路径同样走组处理入口**）+ **触发器编辑重置按上位规则落地（新增 / 修改且变更后仍有触发器 → 已完成任务显式重置；删除触发器只释放额度、不重置 `completed`，含 GUI 确认提示）** + **取消完成收口为原子入口 `revertCompletion()`（两类都置 `progress = 0`，不暴露中间态）** + **加载后首次获得有效 `held` 快照时补一次收集类重算（按 `(玩家 UUID, groupKey)` 内存去重、每组仅一次；离线负责人不重算、不伪造 `held`；配非持久化 `quotaHydration`，未就绪前 GUI / HUD 不显示旧进度）** + **`Task` 层两个稳定态入口（`completeNormalized()` 补满 / `revertCompletion()` 原子取消；不收口到 `TaskTriggerService` 以免反向依赖）+ `Task.fromNbt` 双向加载规范化** + **§5.5 进度文案在 Z2 前定稿** + 离线回归 + 实机验证
  - [ ] Z3 接入累加型：`advanceMatchingTasksByUuid` 改为**事件批次分账**（`pool = 本批事件量`，batch 边界 = 单次 advancement 调用携带的 `amount`），按列表顺序 `take = min(need, pool)`，覆盖 `CRAFT_ITEM` / `BREAK_BLOCK` / `KILL_ENTITY`；**累加型不做任何历史重算**（新建 / 删除 / 改目标都只影响后续批次，不清算已分账事件）；取消完成时 `progress` 归零、手动完成时 `progress` 补至 `targetCount` + 离线回归 + 实机验证
  - [ ] Z4 （可选）同物品占用提示文案 + 文档同步

### 待定决策（Phase Z）

- 收集类进度语义变化后的提示方式：任务右侧加后缀（如 `0/32（同物品已占用 16）`）还是只做文档说明，实现前需先定文案。
- `ADVANCEMENT`（进度）数量恒为 1、本质是一次性事件，归入 **`quota participation = NONE`**：不纳入额度配额，也不进 allocator，保持现有的逐任务独立判定（已知残留：同一进度被多条任务追踪时仍会一起完成，后续要处理需单独立项）。因此跨组处理的 `switch(quotaMode)` 必须有 `NONE` 分支，**不能用 `else` 把它当事件账**。
- allocator 是**纯函数**：输入拆分为 `StateQuotaSnapshot` / `EventBatchQuotaSnapshot`（值）、输出 `AllocationResult`（值），不持有也不修改 `Task`；拆成两个输入类型是为了让"`mode` 与类型不自洽 / STATE 传了 `amount`"这类接线错误**无法被表达**。§3.3 / §3.4 的算法伪代码一律写成值到值的计算，回写（`trigger.progress`、`completeNormalized()`、`removeIndex`、通知）全部由 service 层负责。`AllocationResult.progressByTaskId` 覆盖输入的全部候选任务（全量结果，非变更结果）。
- `groupKey` 的负责人来源唯一化、**禁止 ambient context**：个人任务取 `CachedBucket.playerUuid`（= `tasks.owner_uuid`），团队任务取 `normalizeAssignee(task.assigneeUuid)`；不得用"当前玩家 / 事件玩家"推导（团队场景下两者本来就不同人）。
- 已完成任务修改 `targetCount` / 目标视为显式重置：Phase Z **实际落地**该规则（保存时先取消完成 + 清零进度，再写入新触发器并重算该组），GUI 需补一次确认提示。
- 取消完成时累加型 `progress` 改为归零、手动完成时累加型 `progress` 补至 `targetCount`（保证"存储值 = 展示值"，与前者对称），均与今天的行为不同，实现时需一并改并补回归。**收集类取消完成也必须显式把 `progress` 置 0**（不能只依赖"后续重算覆盖"）：主机可在成员离线时勾选团队任务，而离线负责人不重算，否则会留下 `!completed && progress == targetCount`，重载后被加载规范化改回 `completed = true`，等于取消操作被吞掉。
- 触发器编辑会连带清空 `progress`（改类型、改目标或只改 `targetCount` 都会因编辑器整体重建触发器而归零）：属既有事实，Phase Z 在此基础上补"已完成任务先取消完成"，使 `completed ⇒ progress == targetCount` 全局成立；若后续要做"改数量保留进度"需另立需求。
- `progress` 与 `completed` 是两个独立持久化字段，历史上会出现"已完成但进度未满"（手动完成只翻转 `completed`）：Phase Z 统一为"所有把 `completed` 置 true 的入口都补写 `progress`，并在 `Task.fromNbt` 做一次性加载规范化"。
- 跨 quota group 变更（触发类型变更、目标 identity 变更、团队任务改派）必须按 `beforeGroup` / `afterGroup` 统一流程分别处理；**触发类型在编辑器中是可改的**（5 种类型轮换，含收集型 ↔ 累加型互转），因此类型变更必须纳入跨组处理。项目内不存在"任务移动到其他项目/桶"的操作，故不列该路径。
- 完成态的收口放在 `Task` 层，不放在 `TaskTriggerService.completeTask`——`TaskManager` 位于 `com.todolist.task` 且不依赖 `com.todolist.trigger`，反向收口会造成 `task → trigger` 的反向依赖、破坏当前单向分层。收敛为两个**都输出稳定状态**的入口：`completeNormalized()`（`completed = true` + `progress` 补满）与 `revertCompletion()`（`completed = false` + `progress = 0`，原子完成，不暴露 `!completed && progress == targetCount` 中间态）。
- 加载后的"首次 `held` 快照重算"配一个**非持久化**的组级 `quotaHydration`（`READY` / `NOT_READY`）：未就绪时 GUI / HUD 不渲染旧 `progress`，也不据此判定达标——只靠"不推送"拦不住 GUI 直接读任务对象。
- 局域网主机 / 单人下 GUI 会**直接写本地 H2**，该路径必须同样调用组处理入口（否则额度不重算、展示与台账不一致）；但**不**把 GUI 改成"只能发请求"，那是远超 Phase Z 的服务端权威重构。
- 配额重算按"受影响组去重"执行：`distinct(beforeGroup, afterGroup)`，`targetCount` 不在分组键内，因此改数量的 `beforeGroup == afterGroup` 必须只处理一次，避免重复自动完成 / 重复通知。
- 重算事务边界单向下发：重算内部的自动完成不再反向派发一次额度重算；只有外部操作（拖拽 / 编辑触发器 / 手动完成 / 事件批次提交）才启动新的组重算。批量操作（父任务批量勾选、命令批量完成）以"一次用户操作"为原子单位：先改完全部状态、再汇总受影响组、每组只重算一次，禁止在循环内逐条重算。
- 未指派团队任务与**无触发器任务**都等于"无 quota group"（`groupKey = null`）：`!hasTrigger → null` 这条同时覆盖了"新增触发器"（无 → 有）与"删除触发器"（有 → 无）两种 mutation，两者都走 §5.4 第 8 条的 `beforeGroup` / `afterGroup` 流程。**已完成任务新增触发器必须显式重置**（`completed = false` + `progress = 0`），否则会留下违反 I4 的状态。
- 加载 / 重启后，收集类要在**首次获得有效 `held` 快照**时补一次组级重算（加载规范化只解决 I4，不替代额度重算）；负责人离线时不重算、不伪造 `held`，等其上线首次快照再算。累加型在任何情况下都**不**因加载而重算、也不回放历史。
- "列表顺序"是**分账顺序 + 条件优先级**（收集类"整份满足优先"），不是严格优先级；材料配方预览用的是严格顺序占用，两者场景不同、不做统一。
- `AllocationResult.progressByTaskId` 是**全量结果**（allocator 契约），但回写要**按 diff**（service 策略）：进度未变化的任务不写库、不推送。
- `groupKey` 的负责人归一化：个人任务的 `owner` 恒为玩家自身 UUID（绝不为 `null`），团队任务用 `normalizeAssignee` 且 `null` / `""` 等价为"无组"；不同玩家的个人任务靠 bucket 天然隔离。
- 加载后的"首次 `held` 快照重算"以 `(玩家 UUID, groupKey)` 为键在内存去重、每组只做一次；重算完成前不得推送旧语义进度；该标记不持久化（重启重做一次即可，结果幂等）。
- §5.5 的进度文案（后缀 / tooltip）需在 **Z2 前定稿**，不要拖到 Z4；同时要覆盖"手动完成收集任务后 `used` 变大、同组其它任务进度下降"这一正确但反直觉的行为。
- 完成态不变量 I4 第 3 条为**双向**：`completed == (progress == targetCount)`。为保证任何调用方拿到的都是稳定状态，取消完成收口为原子入口 `revertCompletion()`（`completed = false` + `progress = 0`），不暴露"先翻转状态位、再补进度"的中间态；`Task.fromNbt` 保留双向规范化作为历史 / 异常数据兜底，并配合"重算稳定后才落库与推送"。
- `used / pool / amount` 内部一律用 `int`（不用 `long`）：`targetCount` 已被限制为正 int，溢出需要同组需求总量超过 21 亿，与项目实际规模相差多个数量级，不做该防御。

### 实施补充说明

- Fabric 平台 `AFTER_KILLED_OTHER_ENTITY` 实际为 3 参签名（无 indirectSource），当前按 directSource 判定，**投射物击杀在 Fabric 侧不计入**（Forge 侧完整）。
- GUI 列表/HUD 的标题物品图标渲染复用 feat 2 的 `ItemTitleRenderer` 分段链路，见 `feat-item-title-markup.md`。
- 推送与落库是两条独立链路：落库仍为 3 秒防抖（控制 H2 写放大），推送为 250ms 短节流且只发内存快照、不做任何 SQL；完成瞬间仍立即落库并推送。
- Fabric 合成事件通过 Mixin 实现，`fabric/build.gradle.kts` 显式声明 `loom.mixin.defaultRefmapName`，`todolist.mixins.json` 声明对应 `refmap`；生产 jar 内已确认包含 `ResultSlotMixin.class`、`PlayerAdvancementsMixin.class` 与两者齐全的 `todolist.mixins.refmap.json`（Loom 会把 `@Shadow private ServerPlayer player` 直接改写为 `field_13391`，因此无需额外的字段 refmap 条目）。
- 两个 Mixin 的注入点均已在 1.20.1 映射层核对：`ResultSlot#onTake(Player, ItemStack)` 由 `ResultSlot` 自身声明（不可注入未声明的继承方法），`PlayerAdvancements#award(Advancement, String)` 返回 `boolean`。`defaultRequire: 1` 下若注入点找不到会直接崩溃，因此运行期"能进游戏且能打开合成界面"即可反证注入生效。

## 8. 游戏内手工验证清单

1. `/todo task add 测试` → `/todo task trigger set <id> item_collect minecraft:iron_ingot 32` → 检查 info 显示；
2. 获取 32 铁锭（创造/合成/捡取混合作业）→ 任务自动完成 + 画面顶部浮动提示（含真实铁锭图标）+ HUD 刷新；
3. 存掉部分铁锭（<32）→ 已完成任务不回退；
4. 设置 kill_entity minecraft:zombie 3 → 击杀 3 只僵尸 → 自动完成；
5. 重启世界 → 进度与完成态保持；
6. 团队任务指派给玩家 A，玩家 B 击杀目标 → 不推进；A 击杀 → 推进；
7. GUI 打开状态下另一路径保存（如命令修改标题）→ 触发器进度不被覆盖；
8. 无任何触发任务时观察性能（快照扫描不激活，仅首次会加载一次任务桶用于判断）；
9. 命令 `todo task trigger set <id> item_collect minecraft:iron_ingot 32` 不再报「参数后应有空格分隔」并能成功设置；
10. 任务列表右键 →「设置触发器」→ 类型轮换 → 右侧按钮随类型变为「选择物品/方块/实体/进度」，且能搜到对应条目；
11. 任务列表右键 →「清除触发器」→ 触发进度条目消失，先前进度不再推进；
12. 收集类任务进行中：HUD 进度数字应在获得物品后**立即**变化（约 250ms 内），不再有约 3 秒延迟；
13. 自动完成后画面顶部提示卡片：第一行简短状态、第二行「图标 + 本地化名称」，长标题不再展示不全；最多同屏 3 条、4 秒后消失；
14. **收集类（重点回归）**：新开世界先捡 32 铁锭 → 再创建 item_collect 32 的触发器 → 任务应**立即完成**（无需再捡/挖任何东西）；
15. 丢弃全部铁锭再捡回 32 个 → 任务推进并完成（验证拾取与容器取放都算收集）；
16. 破坏方块触发器（BREAK_BLOCK）→ HUD 进度数字前显示该方块物品图标；
17. 合成触发器（CRAFT_ITEM）→ 分别用**背包 2x2 合成栏**与**工作台**合成目标物品，两种方式都应推进进度并自动完成；
18. 击杀实体触发器（KILL_ENTITY）→ HUD 进度数字前显示对应刷怪蛋图标（原版实体）；
19. 快速新增行点「触发」→ 选类型/目标/数量 → 保存后自动创建任务、标题形如「收集 铁锭 ×32」且处于行内重命名态；回车确认后列表显示物品图标。
20. **（Phase P 重点回归）** 任一触发器（合成/破坏/击杀/进度）达标瞬间：任务立即变为已完成、画面顶部出现浮动提示、HUD 进度同步刷新；`latest.log` 中**不再出现** `ConcurrentModificationException`。
21. 进度触发器：目标数量输入框应不可编辑且恒为 `1`；保存后 info 显示 count=1。
22. 进度触发器建任务：任务标题显示进度的**本地化名称**（如「成就：石器时代」），不是 `minecraft:story/minecraft` 这类资源 ID。
23. 进度触发器：创建后达成对应进度（如「石器时代」）→ 任务自动完成 + 浮动提示；同一条进度只上报一次，不重复触发。
24. 由触发器创建的任务（收集/合成/破坏方块类）：任务标题行显示对应物品图标，不是 `[item:...]` 字面文本。
25. **（Phase R 重点回归）** 合成触发器：背包 2x2 与工作台，鼠标左键取走与 **Shift+左键**取走均应推进；原木 → 木板 ×4 用 Shift 取走按 4 计入；同一产物不重复计数。
26. 触发器编辑器：选择/输入目标后，输入框下方右侧显示 `↳ <本地化名称>`（zh_cn 如「橡木木板」「石器时代」）；清除目标后提示消失。
27. **（Phase S 重点回归）** 先触发任一事件任务（如挖方块）→ 保持世界不退出 → 新建一个事件任务 → 触发对应事件：新任务应正常推进并完成、**不消失**；整个过程中事件触发不再有明显卡顿；GUI 中新任务在旧任务推进/完成后依然在列表中。
28. **（Phase T 重点回归）** 收集物品 / 获得成就 / 击杀实体 / 破坏方块四类任务，每次事件触发时**不再卡顿**；HUD 与 GUI 中的进度数字仍在约 250ms 内刷新；多人（团队）任务由负责人触发时，其他在线成员看到的进度也同步更新；触发大量任务（上百个）时帧率不出现尖刺。
29. **（Phase T 收尾回归）** 任务**达标完成的那一刻**不再卡顿：完成瞬间应同时出现「完成浮动提示」+ 列表中该任务立即移入已完成区；连续完成多个任务（如一次破坏让多个任务同时达标）也不出现卡顿或进度丢失。
30. **（Phase U 重点回归，联机双端）** 团队项目里新建带触发器的任务但**不领取/不指派**：任何玩家做对应动作（击杀/破坏/合成/收集/达成进度）都不推进进度、不自动完成、也不弹完成提示；由任一玩家**领取**（或指派给某玩家）后，该玩家再做对应动作即可正常推进与完成；其他玩家做同样动作仍不推进。有权限的玩家手动点击完成不受影响。
31. **（Phase U 进度清零回归）** 团队任务进行到一半（进度 > 0）时执行**取消领取**：进度应立即回到 0、完成态撤销，任务回到待领取；随后由另一名玩家**领取或被指派**，进度从 0 重新开始。分别验证三条路径：远程联机（服务端保存包）、局域网主机 GUI（已发布局域网的客户端直写）、`/todo task claim|abandon|assign` 命令。
32. **（Phase V 重点回归）** 选中一个顶层任务后点击顶部「触发」按钮：按钮文案应为「子触发」，点击时**不应**丢失选中态或关闭右侧详情区；完成触发器编辑后应自动在该父任务下生成子任务（标题已按触发器生成并处于行内重命名态）。
33. **（Phase X 重点回归）** 对**已有子任务的父任务**右键：菜单中应出现「设置/编辑触发器」；`/todo task trigger set` 应能成功设置（不再被拒绝）；给带触发器的父任务新增子任务时，父任务触发器**应保留**（不再自动清除）。父任务触发达标时父任务立即完成，且不会被未完成的子任务改回未完成；未挂触发器的父任务仍按"子任务全部完成才完成"聚合。
34. **（Phase V 重点回归）** 任务列表中，挂有触发器的任务右侧显示「目标图标 + 进度」（如铁锭图标 + `12/64`）；同时有负责人时显示 `@名字 12/64`；父任务仍显示子任务进度 `1/3`；无触发器的任务维持原有负责人展示。
35. **（Phase W 重点回归，Fabric + Forge 各测一遍）** 新建一个**全新存档**，不达成任何进度时直接打开触发器编辑器并把类型切到「获得进度」→ 点「选择进度」：列表应能列出全部带展示信息的进度（不再为空）；达成第一个进度后重新打开，列表内容不变（不是靠玩家已解锁进度撑起来的）；再回到主菜单进入另一个存档，列表内容应刷新为新存档的目录，不残留上一个存档的数据。
36. **（Phase W 重点回归，仅 Forge）** 新建一个「获得进度」触发任务并选中一个**单条件**进度（例如「石器时代」`minecraft:story/mine_stone`）：达成时只弹出**一个**完成提示，任务只完成一次；再用一个**多条件**进度（例如「探索的时光」）验证：条件逐个达成过程中不误触发，真正达成时同样只弹一个提示。
37. **（Phase Y 重点回归）** 构造三层依赖任务（顶层父任务 → 子任务 → 孙任务）：① GUI 任务列表应展示到第 3 层并逐层缩进；② HUD 同样展示到第 3 层、层级缩进正确，孙任务完成时**不消失**、仍留在原父任务组内显示删除线；③ 把孙任务与子任务全部勾选完成后，父任务在 GUI 与 HUD 中应**同时**变为已完成（不再出现 GUI 未完成而 HUD 已完成）；④ 删除父任务后，其全部后代子任务应从 GUI 与 HUD 中一并消失；⑤ 选中子任务后点击「新增子任务」/「触发」按钮，应能在子任务下再挂一层（不再被顶层任务守卫拒绝）。
38. **（Phase Y 材料多层回归，联机与单人各测一遍）** 打开「材料配方预览」：默认应只展示目标物品配方所需的**直接材料**；选中某个中间产物点「继续展开」后展开下一层，再次点击可折叠回去；预览层级用树形连线（`├─` / `└─`）展示而非仅缩进；再次反推时回到依赖链上的同一材料（如 铁块 ↔ 铁锭）不应重复展示。点「生成」后：任务列表按预览展开的层级生成多层依赖任务，中间产物为「合成」任务（挂 `CRAFT_ITEM`）、最终材料为「收集」任务（挂 `ITEM_COLLECT`），右侧展示触发器进度；取消勾选的最终材料不生成任务。
39. **（Phase E4 / E7 材料触发器回归，联机与单人各测一遍）** 用「材料配方预览」生成一组多层任务（如 合成铁块 ×3 → 收集铁锭 ×27）：① 「合成」任务挂 `CRAFT_ITEM`（累计合成量语义）、「收集」任务挂 `ITEM_COLLECT`（持有量语义），右侧均显示目标图标 + 进度；② **背包里预先已有 3 个铁块**时生成：「合成铁块」任务**不应**被判为已完成（旧物品不计入累计合成量），只能靠真的合成出 3 个铁块或手动勾选完成；③ 把所有材料子任务手动勾选完成后，上层的「合成」任务若尚未真的合成出目标物品，应仍是未完成，不会被自动完成；④ 真的合成出目标数量后，「合成」任务由触发器自动完成；⑤ 手动勾选「合成」任务本身可立即完成、再次勾选可取消，且不会被回退；⑥ 多层链路下中间层与顶层行为一致。
40. **（Phase E5 多候选材料回归，联机与单人各测一遍）** 找一个输入使用物品标签的配方（如「火把 ← 任意木板」「台阶 ← 任意木板」）：① 预览中该材料行名称后应显示「可选 N 种」；② 默认应选中**背包里已有**的那种木板（背包清空后回退为列表首项）；③ 选中该行后点「选择材料」，列表应只列出该配方的候选材料，点选后预览与生成的任务都应换成新选的材料；④ 切换到另一个候选后重新解析，数量与层级不应错乱。
41. **（Phase E6 触发器优先回归，联机与单人各测一遍）** ① 先用「材料配方预览」生成一组多层任务（如 合成铁块 ×3 → 收集铁锭 ×27），任务右侧应显示**触发器进度 + 目标物品图标**（如 `铁块 0/3`），而不是子任务进度 `1/2`；GUI 任务列表与 HUD 表现一致；② 未挂触发器的父任务仍显示子任务进度 `1/2`（回归不破）。
42. **（Phase E7 / E8 背包持有量标注回归）** 打开「材料配方预览」：① 背包里已经攒够某材料（如已有 30 个铁锭、配方需要 27）时，该行数量文本应**标绿并显示「已够」**；② 数量不够时数量保持蓝色，并显示灰色「缺 N」（N = 还差多少个，例：需要 27、已有 12 → 显示「缺 15」）；③ 目标物品自身也参与比较；④ 背包里的**潜影盒内**物品不计入（把材料装进潜影盒后重开预览，标绿应回退为未达标）；⑤ 调整目标数量或切换配方重新解析后，标绿与「缺 N」应随之更新。
43. **（Phase E8 同种材料跨层级累计回归）** 找一个同一材料出现在两个层级的配方组合（如 目标物 ← 木板 ×8 + 部件，部件 ← 木板 ×8，对「部件」点「继续展开」）：① 背包放 **16** 个木板 → 两行的木板数量都标绿（合计刚够）；② 背包只放 **8** 个木板 → **上一层的木板标绿，下一层的木板不标绿并显示「缺 8」**（靠下的行不能重复使用已被上面占用的数量）；③ 背包放 12 个 → 上面标绿、下面显示「缺 4」；④ 把目标数量翻倍后重新解析，分配结果应整体放大且结论一致。
44. **（Phase E7 HUD 树形连线回归）** 构造三层依赖任务：HUD 中每条子任务行左侧应能看到「祖先竖线 + 当前层 `├ / └` 横向分支线」；非末位子任务的竖线贯穿整行、末位子任务竖线只画到行中线；横向分支线从竖线延伸到标题文本前，宽度足够、清晰可见（不再出现"只有竖线、没有树形结构"）。
45. **（Phase E9 材料任务合并回归）** 用「材料配方预览」生成一个同种材料出现在两个层级的方案（如 目标物 ← 木板 ×8 + 部件，部件 ← 木板 ×8，对「部件」点「继续展开」）：① 任务列表中**只应有一条**「收集 橡木木板」任务，数量为**汇总后的 16**（不是两条各 8）；② 该任务挂在首次出现的位置（目标物任务下）；③ 背包只放 8 个木板 → 该任务**仍未完成**（8/16），必须凑满 16 才自动完成；④ 中间产物任务（合成 部件）自身仍按 `CRAFT_ITEM` 正常完成，不因少了这条子任务而出问题。
46. **（Phase E11 材料任务全树去重回归）** 构造一个**中间产物跨分支重复**的方案（如 目标物 ← 部件A + 部件B，部件A ← 共享件，部件B ← 共享件，均点「继续展开」）：① 任务列表中「合成 共享件」**只应有一条**，数量取**两分支汇总**；② 它挂在**首次出现**的位置（部件A 下）；③ 只在重复分支里出现的更深层材料（如 共享件 的原料）**不应丢**，应挂到最近的已生成祖先任务下；④ 只凑够一条分支所需数量时该任务**仍未完成**（不再出现"凑一半就两条都完成"）。
47. **（Phase Y1 幽灵任务回归）** 用「材料配方预览」生成 合成铁块 ×3 → 收集铁锭 ×27 的多层任务：① 直接**手动勾选**顶层「合成 铁块」→ 中间层与叶子层应**一并变为已完成**，未完成区不再残留看不见的子任务；② 反向：取消勾选顶层 → 整条分支回到未完成，且子任务的触发器进度**是原来的真实值**（未被改写为 0 或目标值）；③ 另一条路径：实际合成出 3 个铁块让顶层**由触发器达标** → 后代同样被级联完成，不会留下"父已完成、子未完成"且两侧列表都看不见的任务；④ 勾选/取消后重进 GUI 或重启，完成态与进度保持一致（增量落库已覆盖被级联的任务）。
48. **（Phase E12 配方提示与「全部收集」回归）** 用「材料配方预览」生成 铁块 ×3（默认只展开一层）：① 任务标题应形如「合成 `[铁块]` ×3」「熔炼 `[铁锭]` ×27」——动作词按配方类型给出（功能方块改由预览界面展示，见第 50 条）；② 所有任务（含目标物与中间产物）的类型都应是**收集**（持有量语义）：背包里已有 3 个铁块时，「合成 铁块 ×3」应直接判达标；③ 把某节点「继续展开」到切石 / 锻造 / 高炉 / 烟熏 / 营火类配方，标题动作词应随之变化，且这些任务依然能靠持有量达标（不再出现"进度永远推不动"）；④ 无配方的最终材料仍显示「收集 `[物品]` ×N」。
49. **（Phase E13 预览界面横向配方树回归）** 打开「材料配方预览」选一个有多层配方的目标物（如 铁块）：① 界面应为**从左到右的横向配方树**，物品与其下级配方之间有一个**功能方块节点**（铁块 → 工作台 → 铁锭 → 熔炉 → 粗铁），折叠节点也带功能方块提示；② **点功能方块图标**能切换配方（如铁锭在「熔炉熔炼 / 工作台合成铁粒」之间切换，方块图标与下级材料随之变化）；③ **点物品图标**在有多候选材料（如木板类）时弹出「可选材料」列表，选中后树与数量立即更新；④ **点节点其余区域**能展开 / 收起下级配方，生成任务的范围随之变化（收起目标节点后只生成一条目标任务）；⑤ **滚轮纵向、Shift + 滚轮横向**、在空白处**拖拽**都能滚动，树宽 / 树高超出面板时不会越界；⑥ 面板比旧版明显更大，底部只有数量输入与「取消 / 生成任务」；⑦ 原有的「仅目标 / 目标 + 材料」按钮、勾选框与「切换配方 / 到此为止 / 选择材料」三个按钮都不再出现。
50. **（Phase E14 实机反馈回归）** ① **标题更短**：生成的标题应是「熔炼 `[铁锭]` ×27」「合成 `[铁块]` ×3」「收集 `[粗铁]` ×27」这类形式，**不再带功能方块**；HUD 里长材料名也不至于挤掉数量；② **功能方块与材料一眼可分**：功能方块节点应是「有底 + 有框」的方块格，物品节点只有图标与文字；该物品有多条配方时方块格边框更亮、右上角有小标记；③ **点图标不再无响应**：没有可选材料时点物品图标等同于点节点，直接展开 / 收起；④ **循环配方不再卡死**：找一个互相可逆的配方（如 铁块 ← 铁锭 且 铁锭 ← 铁块），展开 / 切换后若节点因循环无法展开，**功能方块节点仍应保留**，点它可以换回不循环的配方；⑤ 悬浮在这种节点上应提示「循环依赖，无法继续展开」；对没有已知配方的物品提示「未收录该配方类型」（药水、模组结构性方块即属此类）；⑥ 点物品图标弹出的「可选材料」列表**不会被树上的物品图标盖住**。
51. **（Phase E15 实机反馈回归二）** ① **浮层压在树上**：点有多个候选材料的物品图标弹出「可选材料」列表后，列表盖住的树**图标**应看不见（文字与连线被面板正常盖住即可），而**被点的那颗图标本身仍应可见**（列表在它右侧，不应连带消失）；鼠标移到任意节点上出现的**悬浮提示同样不能被树上的图标压住**；② 选一个**名称较长**的目标物（如「橡木木板」「粗制安山岩」），目标节点名称应完整显示、**不贴左边界被裁**；③ 把树横向拖到最左时首列名称仍完整。

## 9. 问题回归记录（实机验证轮次）

> 本节汇总实机验证中暴露的问题、根因、修复位置与固化手段，供后续回归追溯。
> 「固化」列为空表示该问题依赖运行期 MC 环境（真实玩家 / 世界 / 语言包），无法离线复现，靠 §8 手工清单覆盖。

| # | 现象（用户反馈） | 根因 | 修复 | 固化 |
|---|---|---|---|---|
| R1 | 由触发器创建的任务不显示物品图片 | 默认标题用纯文本显示名，未走 feat 2 的 `[item:...]` 标记链路 | `TriggerTargetSupport.resolveTitleTargetToken`（Phase O） | `TriggerTargetSupportTestMain.shouldEmbedItemMarkupInDefaultTitle` |
| R2 | 获得进度触发器建任务，标题显示进度的 tag 而非名称 | ADVANCEMENT 显示名分支缺失，回退成资源 ID | `TriggerTargetSupport.resolveAdvancementTitle`（Phase O） | `TriggerTargetSupportTestMain.shouldFallbackAdvancementDisplayNameToRawId`（回退分支；在线解析靠 §8-22） |
| R3 | 获得进度触发器的目标数量可修改 | 编辑器未按类型约束数量 | `applyTypeDependentWidgetState` + `resolveTargetCount`（Phase O） | `TriggerEditScreenTestMain.shouldForceAdvancementTargetCountToOne` |
| R4 | 达成目标进度后任务不完成 | ① Fabric 无进度事件 ② 达标瞬间 CME 中断事件回调（见下 R5） | `PlayerAdvancementsMixin`（Phase O）+ 副本遍历（Phase P） | R5/R6 行；在线链路靠 §8-20/23 |
| R5 | 合成触发器不生效，进度也无变化 | `advanceMatchingTasks` 直接遍历倒排索引实时列表，达标时 `removeIndex` 中途修改列表抛 `ConcurrentModificationException`，落库/通知/推送全部被跳过 | 候选列表遍历副本 `new ArrayList<>(bucket.find(...))`（Phase P） | `TaskTriggerServiceTestMain.shouldAdvanceBreakBlockForTasksSharingTarget`（同键多任务、一个达标时其余仍推进）；完整事件链靠 §8-20 |
| R6 | 进入世界提示「无效的玩家数据」，存档列表异常 | `PlayerAdvancementsMixin` 含非 private 静态方法，运行期 `InvalidMixinException` 导致 `PlayerAdvancements` 类加载失败（编译期不可见） | 去重状态抽到 `AdvancementAwardGate`，Mixin 只留 private 成员（Phase Q） | `fabric:mixinVisibilityTest`（`MixinVisibilityTestMain`，已挂 `:fabric:check`） |
| R7 | 合成 Shift+左键取走不推进 | 1.20.1 中 Shift 走 `quickMoveStack`，产物在 `onTake` 前已被搬走，传入堆为 `minecraft:air`（类型与数量均丢失） | 参数堆为空时按 `craftSlots` 配方反查产物（Phase R） | 无法离线复现（需真实合成交互），靠 §8-25 |
| R8 | 触发器编辑器只展示目标 tag，zh_cn 不友好 | 目标输入框按设计保存资源 ID，无可读名展示 | 面板加高 + `renderResolvedTargetName` 显示 `↳ <本地化名称>`（Phase R） | 解析逻辑由 R2 用例间接覆盖；渲染靠 §8-26 |
| R9 | 完成旧任务后新加的事件任务无法触发 | 单人模式 GUI 保存为客户端直写本地 H2（不发网络包），失效钩子又只接在 REPLACE 包与命令三个点上，引擎桶缓存永不失效，新任务进不了倒排索引 | 失效收口到 `H2TaskStore` 四个全量保存出口（Phase S） | 存储行为靠 §8-27 在线验证；失效收口为存储出口统一逻辑 |
| R10 | 增加旧任务进度时，新增的任务直接消失 | 引擎 3 秒防抖落库用旧缓存列表**全量替换** tasks 表，抹掉其他路径刚保存的新任务；250ms 推送旧快照覆盖客户端列表 | 引擎落库增量化 `updateTriggerStates`（只 UPDATE 推进过的行，Phase S） | `H2StorageBackendIntegrationTestMain.shouldUpdateTriggerStatesWithoutTouchingOtherRows` |
| R11 | 每次事件触发都卡顿一下 | 达标与防抖落库均为全量替换式保存（上千行 DELETE+INSERT）在服务端主线程执行 | 增量 UPDATE 消除写放大（Phase S） | `TaskTriggerServiceTestMain.shouldTrackDirtyTasksForIncrementalFlush`（脏集合仅含被推进任务）；性能靠 §8-27 感知验证 |
| R12 | Phase S 后新任务已能触发，但收集/成就/击杀/破坏触发时仍卡顿 | 推送链路仍是全量：250ms 节流推送全量任务快照（~1100 任务 / ~34KB 网络包），3 秒防抖落库后也再发一次全量快照；客户端每次收到都全量写本地 H2 双桶，开销与实际变化任务数无关 | 轻量包 `todolist:trigger_progress` 只发变化任务 + 客户端就地更新内存不落库；`peekDirtyTasks` 只读不消费脏集合；`flushBucket` 的推送同样改为增量（Phase T） | `TaskTriggerServiceTestMain.shouldPeekDirtyTasksWithoutConsumingForLightPush`（推送不消费脏集合，落库不丢进度）；性能靠 §8-28 感知验证 |
| R13 | 进度更新不卡了，但「完成任务的那一刻」仍卡顿 | 达标路径 `completeTask` 单独保留了全量同步（`syncTasksToPlayer` / `broadcastTeamTasks`）：服务端读库 + 全量序列化，客户端整桶重写本地 H2 | `completeTask` 改调 `flushBucket(..., true)`，由 `flushBucket` 统一做增量落库 + 同一批脏任务的轻量推送；完成提示保持轻量包（Phase T 收尾） | 同 R12（脏集合/推送语义用例）；性能靠 §8-29 感知验证 |
| R14 | 团队任务未指派（待领取）时也会被事件推进，待领取状态失去意义 | `canPlayerAdvanceByUuid` 把「未指派」当成「谁都可以推进」（`assignee == null \|\| isEmpty()` 分支） | 团队任务必须已指派给事件玩家才可推进；未指派任务不参与累加推进/收集重算/库存扫描（Phase U） | `TaskTriggerServiceTestMain.shouldIgnoreUnassignedTeamTaskForAllPlayers`（累加型 + 收集型）；联机链路靠 §8-30 |
| R15 | 团队任务取消领取/改派后，进度被下一位领取者继承（等于别人替你做一半） | 领取人变更时没有任何进度归属处理，共享进度照旧保留 | `resetTriggerProgressOnAssigneeChange` / `resetTriggerProgressIfAssigneeChanged` 在领取人变更时清零进度并撤销完成态；服务端保存入口（网络包 + 命令）与 GUI 变更点（已发布局域网的客户端直写）各设一道（Phase U） | `TaskTriggerServiceTestMain.shouldResetTriggerProgressWhenTeamAssigneeChanges`（取消领取/改派/未变更/新任务/个人任务五种情形）；三条保存路径靠 §8-31 |
| R16 | 给子任务加触发器要先建子任务、输入名称、双击、再点按钮，且空白子任务会被丢弃，实际很难走通 | 快速新增「触发」按钮只建顶层任务，子任务侧没有任何触发器入口 | 按钮跟随选中任务：选中顶层任务时建为其子任务（文案「子触发」），未选中时行为不变（Phase V） | `TaskListWidgetTestMain`/GUI 用例覆盖展示层；交互链路靠 §8-32 |
| R17 | 点击顶部「触发」按钮时选中任务失焦、右侧详情区关闭，入口实际不可用 | `isClickInEditArea` 未登记快速新增行按钮，点击被判为空白点击 → 触发 `auto_clear_selection` 并关闭详情 | 把 `quickAddItemButton`/`quickAddTriggerButton` 纳入编辑区命中集合（Phase V） | 无直接离线用例（依赖鼠标事件链），靠 §8-32 实机验证 |
| R18 | 已有子任务的父任务也能设置触发器 | 编辑器/命令/引擎都没有「父任务不可挂触发器」的约束 | 右键菜单、`openTriggerEditScreen`/`applyTriggerFromEditor`、命令 `trigger set`、引擎 `rebuildIndex` 四道守卫；新增首个子任务时自动清除并提示（Phase V） | `TaskTriggerServiceTestMain.shouldIgnoreParentTaskWithSubtasks`；GUI/命令守卫靠 §8-33 |
| R19 | GUI 列表分不清哪些任务挂了触发器，也看不到触发进度 | 列表右侧只渲染子任务进度或负责人，完全没有触发器信息 | 右侧追加触发器进度与目标物品图标，与负责人并存时形如 `@名字 12/64`（Phase V） | `TaskListWidgetTestMain.shouldShowTriggerProgressAndTargetIconInTrailingMeta` |
| R20 | 新存档下「选择进度」列表一直是空的，达成第一个进度后重开仍为空 | ① `ClientAdvancements` 只含服务端按可见性下发的少量进度；② `ItemSelectorScreen.CACHE` 把首次打开时的空结果永久缓存 | 新增服务端权威进度目录包 `todolist:advancement_catalog`，`AdvancementCatalog` 客户端缓存并与存储域绑定；进度类型每次打开重建，不再缓存空结果（Phase W） | `AdvancementCatalogTestMain`（同域读缓存、切换域失效、编解码往返）；在线列表靠 §8-35 |
| R21 | Forge 下同一条进度弹两个完成提示 | `AdvancementEvent` 为基类，`award` 会先投递 `AdvancementProgressEvent`（GRANT）再投递 `AdvancementEarnEvent`，监听基类把两次都收了 | `ForgeGameEventBridge.onAdvancement` 改为只监听 `AdvancementEvent.AdvancementEarnEvent`（Phase W） | 无法离线复现（需真实 Forge 事件）；靠 §8-36 |
| R22 | 任务列表与 HUD 只展示 2 层，第 3 层依赖任务在列表里看不到（HUD 里还被压到第 2 层显示） | 层级模型本身是扁平单指父指针（可表达任意深度），但展示链路有硬编码 2 层：GUI 只补一层直属子任务、缩进固定 0/14，HUD 只展开一层；同时 `canAddSubtaskToParent` / `shouldShowAddSubtaskButton` / `resolveTriggerCreationParentTaskId` 三处 `isTopLevelTask()` 守卫禁止子任务再挂子任务 | GUI `buildSectionTasksWithDescendants` 改 BFS 收集全部后代 + `TaskListWidget.appendTaskRows` 递归渲染 + `DisplayRow.depth` 逐层缩进；HUD `appendHudGroup` 按真实深度递归并逐层缩进；三处守卫去掉（Phase Y） | `TaskListWidgetTestMain` 多层渲染用例；`TodoHudRendererTestMain.shouldKeepThirdLevelTaskInsideItsParentGroup` |
| R23 | HUD 中第 3 层任务完成后直接消失；删除父任务后 HUD 里第 3 层任务仍残留 | ① 第 3 层任务在 HUD 里是孤儿，被平铺追加到全局 pending/done，完成后进 done 区又受 `hudDoneLimit`（默认 5）裁剪而"消失"；② `TaskManager.deleteTask` 只删自身，后代成为孤儿继续被 HUD 渲染 | HUD 改为按 `knownTaskIds` 建 children 索引、父不在范围内按根处理，同组子任务留在原组显示删除线；`TaskManager.deleteTask` 改为级联删除全部后代（BFS + visited），`collectDescendantIds` 供复用（Phase Y） | `TaskManagerSubtaskTestMain.shouldCascadeDeleteDescendants`；`TodoHudRendererTestMain.shouldKeepThirdLevelTaskInsideItsParentGroup` |
| R24 | 子任务全部完成后，父任务在 GUI 任务列表仍是未完成，但在 HUD 却归到了已完成 | HUD 有独立于 `TaskManager` 的 `isHudGroupCompleted`，按"直属子任务全部完成"判 done，既缺 `hasTrigger()` 保护也没有子树递归；GUI 走 `TaskManager.syncParentCompletionStates` 聚合（跳过有触发器的父任务、且需全部完成） | HUD 完成态统一改为 `isSubtreeCompleted`（触发器优先 + 整棵子树递归），结果写入 `hudCompletedByTaskId`，`isHudGroupCompleted` 只读该表，与 GUI 收敛到同一规则（Phase Y） | `TodoHudRendererTestMain.shouldKeepTriggeredParentInPendingWhenTriggerNotMet`；`TaskManagerSubtaskTestMain.shouldAggregateThreeLevelCompletionBottomUp` |
| R25 | 材料配方预览生成的「合成」父任务（含中间层）在自己没有触发器时，子任务一做完就被自动完成 —— 材料齐了不等于目标物品真的做出来了；手动给父任务补触发器后行为才正常 | `MaterialTaskGenerator` 的合成任务用 `createDependencyTask` 生成，**不挂触发器**，完成态完全落到 `syncParentCompletionStates` 的子任务聚合上（阶段 3c 的设计前提与"父任务带触发器"语义不一致）；同时带触发器且有子任务的任务在手动勾选时走级联分支，父任务自身完成态不会被切换，等于"手动勾选"这条路径也失效 | 合成任务改为 `createCraftTask`，挂 `CRAFT_ITEM 物品 ×数量`；收集任务保持 `ITEM_COLLECT`。`toggleTaskCompletion` 对带触发器的任务单独处理：手动勾选直接切换自身完成态，无触发器后代仍级联；`TodoScreen` 的「我的」视图批量子任务切换跳过带触发器子任务，且带触发器任务不再走批量分支（Phase E4） | `MaterialTaskGeneratorTestMain.shouldGenerateParentWithMaterialSubtasks`/`shouldGenerateDependencyChainMatchingExpandedLevels`；`TaskManagerSubtaskTestMain.shouldKeepTriggeredParentIncompleteWhenAllChildrenComplete`/`shouldManuallyToggleTriggeredParentWithChildren` |
| R26 | ①父任务明明已经拥有目标物品（触发器应已达标），却仍在等子任务；②父任务右侧只显示子任务进度（`1/2`），看不到触发器进度；③HUD 里第 2/3 层任务仅靠缩进，层级关系不直观 | ①`CRAFT_ITEM` 只累加"本次会话新合成"的数量，玩家在此之前已有的物品不计数，触发器永远不达标，父任务看起来一直在等子任务；②GUI `buildTaskTrailingMetaText` 与 HUD `buildRowVisual` 均是**先**取子任务进度，只有没有子任务时才回退到触发器进度；③HUD 只用 `HUD_SUBTASK_EXTRA_INDENT` 固定缩进 + `"- "` 前缀，没有任何连线 | ①合成任务触发器改用 `ITEM_COLLECT`（持有量语义），复用 Phase L「设置触发器后立即评估持有量」链路，已有物品即达标；②GUI 与 HUD 的进度取值顺序对调为**触发器优先**（挂触发器的父任务不再显示子任务进度，`resolveTrailingTriggerIconId` 同步放行）；③HUD 新增 `hudTreeLineByTaskId`（末位标记 + 祖先层是否还有后续兄弟）并在行内用 `GuiGraphics.fill` 画树形连线（祖先竖线 + `├ / └` 分支线），层级宽度 `HUD_TREE_LEVEL_WIDTH`（Phase E6） | `MaterialTaskGeneratorTestMain.shouldGenerateParentWithMaterialSubtasks`（断言 ITEM_COLLECT）；`TaskListWidgetTestMain.shouldShowTriggerProgressAndTargetIconInTrailingMeta`（带触发器父任务展示触发器进度与图标）；`TodoHudRendererTestMain.shouldKeepTriggeredParentInPendingWhenTriggerNotMet`（右侧优先展示触发器进度）/`shouldDifferentiateSubtaskRowsInHud`/`shouldKeepThirdLevelTaskInsideItsParentGroup`（树形连线签名） |

| R27 | ①子任务里的「合成」步骤被玩家背包里已有的物品直接判达标，展开配方材料却不需要真的合成（Phase E6 改 `ITEM_COLLECT` 引入的回归）；②材料配方预览看不出"背包里的材料够不够"，需要人工数；③HUD 多层层级只有竖线、看不到 `├ / └` 横向分支线 | ①`ITEM_COLLECT` 是持有量语义，任务创建前已有的物品也计入，与"展开配方材料说明这一步必须自己合成"的意图冲突；②预览界面只渲染材料清单，没有任何"已有/需求"比较；③`HUD_TREE_LEVEL_WIDTH = 6` 时横向分支线的终点 `textStartX - 2` 只剩约 4px，视觉上几乎不可见 | ①撤销 E6 的做法，合成任务（目标物与中间产物）触发器**改回 `CRAFT_ITEM`**（累计合成量语义），收集任务保持 `ITEM_COLLECT`（Phase E7）；②`MaterialListScreen` 新增 `collectHeldItemCounts()`（只统计背包槽位，**不展开潜影盒**）+ `countHeldItems()` / `isRequirementSatisfied()`，行内数量达标标绿（`0xFF55FF55`）、未达标追加灰色「(已有 N)」，同一份快照继续驱动"多候选材料背包优先"（Phase E7）；③`HUD_TREE_LEVEL_WIDTH` 6→10、新增 `HUD_TREE_LINE_OFFSET = 3`，横向分支线终点改为 `textStartX - 1`（Phase E7） | `MaterialTaskGeneratorTestMain.shouldGenerateParentWithMaterialSubtasks`/`shouldGenerateDependencyChainMatchingExpandedLevels`（断言改回 `CRAFT_ITEM`）；`MaterialPreviewStateTestMain.shouldGenerateTasksWithSelection`、`MaterialListScreenTestMain.shouldGenerateTasksAndCloseScreen`（同步断言）；`MaterialListScreenTestMain.shouldMarkCountSufficientWhenHeldItemsReachRequirement`（新增，覆盖达标标绿/未达标不标绿/刚好达线）；HUD 连线靠 §8-43 实机验证 |

| R28 | ①材料配方预览只用颜色区分"数量已达标/未达标"，新手不理解两种颜色含义；②同一材料出现在多个层级时（第 1 层与第 2 层各需 8 个木板），每行都各自与背包总量比较，出现"每行都标绿、实际总量不够"的误导；③不够的项显示的是"已有多少"，玩家还得自己减出"还差多少" | ①上一轮（Phase E7）只在未达标时追加了一个裸数字「(12)」，达标时完全是"裸绿数字"，没有可读文字；②`MaterialListScreen` 逐行独立调用 `heldCountFor(itemId)` 取背包总量，没有任何跨行/跨层级的额度分配；③标注语义停在"现状"而不是"待办" | ①`buildHeldSuffix` 改为输出可读文字：达标 → 绿色「已够」（`gui.todolist.material_preview.held_enough`），不够 → 灰色「缺 N」（`...held_missing`，`N = 需求 − 该行可用持有量`），颜色降为辅助线索（Phase E8）；②新增 `allocateHeldCounts` / `consumeHeld`，在 `refreshPlan` 后按**显示顺序自上而下**为每行分配背包持有量（高层级先占 `min(已有, 需求)`，低层级只用剩余量），行渲染与目标行统一走 `allocatedHeldCount` / `targetHeldCount`；③行宽不足时只隐藏持有量标注，数量本身始终可见 | `MaterialListScreenTestMain.shouldMarkCountSufficientWhenHeldItemsReachRequirement`（「已够」/「缺 N」文字断言）；`MaterialListScreenTestMain.shouldAllocateHeldItemsAcrossRepeatedMaterials`（16 够 / 12 只够上层且下层「缺 4」/ 8 只够上层且下层「缺 8」）；实机靠 §8-42 / §8-43 |

| R29 | 同种材料出现在多个层级/分支时，生成的任务列表里多条同种材料任务**共享同一份数量**：背包只有一半数量，几条任务就全部被判为完成 | `MaterialResolver` 只在 `leafTotals` 里跨分支求和，树里仍是多个独立节点；`MaterialTaskGenerator.appendSubtreeTasks` 是"一个节点一条任务"，于是生成多条「收集 同种材料 ×单层需求」，而 `ITEM_COLLECT` 是绝对持有量语义 → 每条各自拿同一份背包数量比较（Phase E9 前） | 新增 `collectFinalMaterialTotals()` 预汇总每个最终材料的总需求；`appendSubtreeTasks` 对最终材料（叶子 + 退化为最终材料的中间产物）按 itemId 去重，**只在首次出现处生成一条收集任务**、数量取汇总需求（Phase E9） | `MaterialTaskGeneratorTestMain.shouldMergeRepeatedFinalMaterialIntoOneTask`（三个层级各需 1 个原木 → 只生成一条 ×3 的收集任务、挂首次出现位置）；实机靠 §8-45 |

### 新增测试任务索引

- `:common:guiSystemTest` → `TriggerTargetSupportTestMain`（含 R1/R2 固化用例）
- `:common:guiSystemTest` → `TriggerEditScreenTestMain`（含 R3 固化用例）
- `:common:taskTriggerServiceTest` → `TaskTriggerServiceTestMain`（五种事件类型的引擎判定语义 + 增量落库脏集合，已挂 `:common:check`）
- `:common:h2StorageBackendIntegrationTest` → `H2StorageBackendIntegrationTestMain`（含 R10 增量更新不覆盖新任务用例）
- `:common:advancementCatalogTest` → `AdvancementCatalogTestMain`（含 R20 进度目录缓存/失效/编解码用例，已挂 `:common:check`）
- `:fabric:mixinVisibilityTest` → `MixinVisibilityTestMain`（R6 防回归守卫，已挂 `:fabric:check`，`build` 会自动执行）

### 五种事件类型的引擎测试场景（`TaskTriggerServiceTestMain`）

引擎的判定语义原先与 `ServerPlayer` 运行时耦合、无法离线测试；现已拆出纯逻辑入口
`advanceMatchingTasksByUuid` / `recalculateItemCollectByUuid`（按玩家 UUID + 持有量快照推进，返回达标列表，
由外层负责落库/通知/推送，行为等价），离线覆盖：

| 用例 | 事件类型 | 覆盖语义 |
|---|---|---|
| `shouldCompleteKillEntityByAccumulatedKills` | KILL_ENTITY | 累加推进 → 达标完成 → 移出索引 → 置脏 |
| `shouldAdvanceBreakBlockForTasksSharingTarget` | BREAK_BLOCK | 同键多任务共享推进；一个达标时其余仍推进（CME 防回归） |
| `shouldAdvanceCraftItemByCraftedBatchAmount` | CRAFT_ITEM | 按本次合成批量数累加（一次 ×4 直接计入） |
| `shouldTrackCollectProgressByAbsoluteHeldCount` | ITEM_COLLECT | 绝对持有量语义：进度=持有数、clamp 上限、完成后不回退 |
| `shouldCompleteAdvancementOnFirstAward` | ADVANCEMENT | 单次达标；重复上报不再匹配 |
| `shouldIgnoreUnassignedTeamTaskForAllPlayers` | 团队边界（Phase U） | 未指派团队任务：累加型与收集型都不推进、不置脏；必须领取/指派后才参与触发 |
| `shouldResetTriggerProgressWhenTeamAssigneeChanges` | 领取人变更（Phase U） | 取消领取/改派清零进度与完成态；未变更、新任务、个人任务均不受影响 |
| `shouldAdvanceParentTaskWithSubtasks` | 父任务边界（Phase X） | 父任务与叶子任务都进倒排索引、都被事件推进；父任务可被触发器完成 |
| `shouldIgnoreNonAssigneeEventsForTeamTask` | 团队边界 | 非负责人事件不计入，负责人正常推进 |
| `shouldSkipCompletedTasksInIndex` | 完成态边界 | 已完成任务不进索引、不被事件匹配 |
