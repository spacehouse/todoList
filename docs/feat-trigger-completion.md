# 实现方案：游戏内事件触发式完成任务

> 状态：验证分支 `verify/1.20.1-feat-trigger-and-item-title` 开发中
> 对应 TODO.md 计划项：【feat】游戏内事件触发式完成任务
> 更新日期：2026-09-11

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
| ITEM_COLLECT | **绝对持有量语义**：progress = 当前库存持有数（主背包+快捷栏+副手），达到 targetCount 即完成。天然防"丢出再捡回"刷进度，且合成/漏斗/奖励箱获得的物品同样有效 |
| KILL/BREAK/CRAFT/ADVANCEMENT | 累加语义：事件发生一次加一次（CRAFT 按合成产物数量加） |
| 已完成任务 | 不再参与事件匹配（防止取消勾选后又被事件自动勾回需重新计入） |
| 子任务触发器 | 达标只置子任务 completed；父任务完成态由现有展示层聚合逻辑处理，引擎不越权 |

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

## 4. 边界（本期不做什么）

- 不做 datapack JSON 谓词（biome/工具/附魔限定等），留 v2；
- ~~不做 GUI 触发器可视化编辑器（含物品选择器），留 v2~~ 已提前实施（Phase H/I），见 §2.6 补充；仍不做 datapack 谓词式的复杂条件编辑器；
- 不做团队任务的"多人合计计数"（所有人进度加在一起），v1 按 assignee 各自独立；团队任务必须**已指派**才可被事件推进，未指派（待领取）任务不参与任何触发事件（见 §2.4 与难点 17）；
- 不处理离线玩家（事件来源必须在线，离线 assignee 的团队任务不会推进）；
- 不做触发器达成的历史记录 / 撤销 / 回滚；
- 投影、机械动力附属 mod 不在本期（等本底座验证通过）；
- 不为已废弃的 NBT 文件存储模式做任何适配（H2-only 硬约束）。
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
- [x] `build-local-17.bat build --offline` 双平台 jar 产出（fabric/forge 1.20.1-1.4.2）并归档 `dist/`
- [ ] 游戏内手工验证清单（见 §8，待用户在游戏环境执行）

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

### 新增测试任务索引

- `:common:guiSystemTest` → `TriggerTargetSupportTestMain`（含 R1/R2 固化用例）
- `:common:guiSystemTest` → `TriggerEditScreenTestMain`（含 R3 固化用例）
- `:common:taskTriggerServiceTest` → `TaskTriggerServiceTestMain`（五种事件类型的引擎判定语义 + 增量落库脏集合，已挂 `:common:check`）
- `:common:h2StorageBackendIntegrationTest` → `H2StorageBackendIntegrationTestMain`（含 R10 增量更新不覆盖新任务用例）
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
| `shouldIgnoreNonAssigneeEventsForTeamTask` | 团队边界 | 非负责人事件不计入，负责人正常推进 |
| `shouldSkipCompletedTasksInIndex` | 完成态边界 | 已完成任务不进索引、不被事件匹配 |
