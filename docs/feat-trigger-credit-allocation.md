# 同物品任务的额度配额（Phase Z 设计稿）

> 状态：**已定方案，尚未实现**
> 决策记录（2026-09-15）：
> 1. 方案 1 全量额度配额，**分账顺序按任务列表顺序（从上往下）**，拖拽排序即调整**分账顺序**。
>    注意这不是"严格优先级"：收集类采用**条件优先级**（整份满足优先）——
>    排在前面的任务无法整份满足时，额度会让给后面能整份满足的任务（§3.3、§3.6）；
> 2. 累加型（合成 / 破坏 / 击杀）采用**事件批次分账**（严格语义，不引入持久化字段）；
> 3. 已完成任务被改 `targetCount` / 目标 / **新增触发器**时**视为显式重置**（先取消完成、清零进度、再按新目标重新判定）；
>    Phase Z 在编辑器保存路径实际落地该规则（含 GUI 一次确认提示），见 §4.1；
>    **删除触发器是例外**：不重置 `completed`，只释放旧组额度（§4.1）；
> 4. （2026-09-22 修订）累加型**不做任何历史重算**：新建 / 删除 / 改目标 / 改 `targetCount`
>    都不清算已分账的历史事件，只影响后续批次；
> 5. （2026-09-22 修订）手动完成累加型时 `progress` 补至 `targetCount`；
>    batch 边界 = 单次 advancement 调用所携带的 `amount`。
> 6. （2026-09-22 修订）**取消完成时收集类与累加型都显式把 `progress` 置 0**：
>    收集类不能只依赖"后续组级重算覆盖"——负责人离线时不重算，否则取消操作会被加载规范化吞掉（§4.2）。
>
> 关联：`docs/feat-trigger-completion.md`（触发器语义底座）、`docs/feat-material-task-generation.md`（材料反推生成的收集/合成任务链）

## 1. 问题

同一个项目里，如果多条任务（含子任务）追踪**同一种物品 / 同一个目标**，同一批事件或同一份物品会把它们**同时**判为完成：

| 触发类型 | 现有判定 | 现象 |
|---|---|---|
| `ITEM_COLLECT` 收集 | 进度 = 当前背包持有量（绝对量），每条任务各自与同一份背包比较 | 拿到 16 个铁锭 → 所有目标 ≤16 的收集任务一起达标 |
| `CRAFT_ITEM` 合成 | 一次合成事件累加给**所有**同物品未完成任务 | 合成 4 个木板 → 两条"需要 4 个"的任务一起完成 |
| `BREAK_BLOCK` 破坏 / `KILL_ENTITY` 击杀 | 同上（累加型） | 破坏 4 个方块 → 两条"破坏 4 个"的任务一起完成 |

根因：引擎里任务之间没有任何额度协调，见 `TaskTriggerService.advanceMatchingTasksByUuid`
与 `recalculateItemCollectByUuid`，两者都是**逐任务独立判定**。物品既没有被真正消耗，
任务之间也不记账，因此同一份物品可以"重复满足"任意多条任务。

这在材料反推场景下很常见：一个项目里几个不同的合成目标，各自都需要木板，
就会生成多条"收集 橡木木板 ×N"，全部被同一批木板同时完成。

## 2. 不变量与硬约束

### 2.1 不变量（实现与测试的判定标准）

**I1 已完成额度不可回退**

```
completed(task, before) = true  ⇒  completed(task, after) = true
```

仅以下**显式用户操作**例外，且两类触发器的"释放"含义不同（详见 §3.5、§4）：

| 显式操作 | 收集类（状态账） | 累加型（事件账） |
|---|---|---|
| 取消完成 / 删除任务 | 任务回到未完成；释放当前持有额度，后续任务可能提前完成 | 任务回到未完成且 `progress = 0`；但**已分账的历史事件不返还**，只能参与后续批次 |
| 修改已完成任务的 `targetCount` / 目标 | 走 §4 显式重置 | 走 §4 显式重置 |
| 领取人变更（既有规则） | 进度清零、按新组重算 | 进度清零，**旧事件不迁移** |

措辞澄清：取消完成**本身就是**回到未完成（`completed = false`，"不会回退"指的是 I1 主句的
`completed ⇒ 保持 completed` 不被事件本身打破）。所谓"不返还"针对的是
**已分账的历史事件 / 已占用的额度不退还给别人**，而不是"状态上仍算已完成"。

**I2 同一次分配中不重复吃额度**

同组未完成任务按列表顺序分配后：`Σ(本轮新增占用) ≤ 本轮可用资源`，
同一份资源只能被一个任务吃掉。两类触发器的"本轮可用资源"定义不同：

```
ITEM_COLLECT：availableResource = max(0, held − used)
累加型：      availableResource = 当前批次的 amount
```

**I3 判定顺序只由持久化任务顺序决定**

```
sortByPriority / 折叠展开 / 搜索过滤 / HUD 与 GUI 差异
```
以上任一变化都**不得**改变额度判定结果；只要持久化任务顺序不变，结果必须相同。

**I4 实现级不变量（代码 review 检查点，与 I1~I3 并列）**

`progress` 只存在于 `TaskTrigger` 上，无触发器任务不参与额度体系，
所以第 2、3 条必须显式带 `hasTrigger` 前置，否则对无触发器任务无意义：

```
0 ≤ pool                                                   // allocator 内部，见 §3.3 的 max(0, ...)
hasTrigger  ⇒  0 ≤ trigger.progress ≤ trigger.targetCount   // TaskTrigger.clampProgress 既有保证
hasTrigger  ⇒  completed == (trigger.progress == trigger.targetCount)     // 双向
```

第 3 条是**双向**的，展开就是两个方向：

```
hasTrigger && completed                        ⇒  progress == targetCount
hasTrigger && progress == targetCount          ⇒  completed
```

**为什么必须双向**：单向形式允许存在 `completed = false` 且 `progress == targetCount` 的稳定态，
而它没有业务含义——累加型尤其危险：下一批事件进来时 `need = targetCount − progress = 0`，
`take = 0`，任务只能靠"恰好在这一轮被判定完成"来自愈，属于不该被持久化的状态。
该状态的来源是**取消完成**：如果取消只做一个"翻转状态位"的动作（不动 `progress`）就会产生它，
因此**取消完成被收口成一个原子入口 `revertCompletion()`**（`completed = false` + `progress = 0`，§5.3），
再靠 §5.4 第 6 条的提交时机约束 + 第 7 条的双向加载规范化兜住历史 / 异常数据。

- 第 1 条由 §3.3 的夹紧提供，防止"持有量回落后 `pool` 为负"导致进度倒扣；
- 第 2 条是**既有保证**：`TaskTrigger` 的 `type / target / targetCount` 为 final，构造器无条件
  `clampProgress`，而全部加载路径（`Task.fromNbt` → `H2TaskStore` / `H2TaskQueryService` /
  `H2LegacyMigrationReader`）都经过它，因此**不可能**出现 `progress > targetCount`；
- 第 3 条的两个方向**都不是既有保证，必须新增**：`clampProgress` 只管上下界，管不了这条等价式。
  现有数据里确实存在反例——`TaskManager.toggleTaskCompletion` 只翻转 `completed`、不动
  `trigger.progress`，所以"手动勾选一条 `0/16` 的收集任务"或"手动完成一条 `2/4` 的合成任务"
  都会留下 `completed = true` 而 `progress < targetCount` 的脏状态；且由于已完成任务会被
  `removeIndex` 摘出索引，任何重算路径都不会再覆盖它，脏状态会一直留存。

**I4 第 3 条由三个入口共同保证，缺一不可：**

| 入口 | 动作 |
|---|---|
| 自动完成（`TaskTriggerService`） | 收集类第一遍显式写 `progress = targetCount`（§3.3）；累加型 `take = min(need, pool)` 天然写满（§3.4）。两类的"写满"与"完成"都在同一处发生，天然满足双向 |
| 手动 / 批量 / 命令完成 | **所有把 `completed` 置为 `true` 的入口**都必须同时把 `trigger.progress` 补到 `targetCount`（§4.2）；不能依赖"后续重算刷新" |
| 加载规范化（`Task.fromNbt`） | 反序列化后按**双向**修正：`completed && progress < targetCount` → 写满 `progress`；`!completed && progress == targetCount` → 置 `completed = true`（§5.4 第 7 条） |

**落点必须在 `Task.fromNbt`**：`completed` 与 `trigger` 都在该层解析、彼此可见；
`TaskTrigger.fromNbt` 只能看到 trigger 子树，看不到 `completed`，放那里做不了这个判断。
补满 `progress` 对台账**无副作用**——收集类 `used` 用 `targetCount` 而非 `progress`，
累加型没有 `used`。

### 2.2 硬约束

1. **零新增持久化**：不新增表/字段，重启、重进世界、多人同步后结果一致；
2. **单任务额度行为不变**：某目标只有一条任务时，额度分配结果与现在完全一致（`pool` 全给它）。
   注意这是**额度维度**的约束；§4.1 / §4.2 记录的"触发器编辑清零 `progress`"
   与"手动完成补满 `progress`"是独立的语义修正，单条任务同样适用；
3. **排序来源必须是持久化的人工顺序**：拖拽可以改变"谁先被满足"（有意设计），
   但视图开关不能影响判定；
4. **同一 bucket 内串行**：额度计算与完成提交必须在同一执行上下文内完成
   （或基于不可变快照计算、统一提交），避免两个事件线程各自以 `used = 0` 起算导致重复占额；
5. **可离线测试**：判定逻辑不依赖 MC 运行时对象。

## 3. 方案

### 3.1 核心思路

把「同一分组」下的任务看作对同一份资源的**多次申领**。

分组键 = `(所属桶/项目, 触发器类型, 目标身份, 负责人 UUID)`

- **目标身份**必须复用触发器自身的规范化口径（`TaskTrigger.normalizeTarget` 的结果），
  即"额度分组认为相同的目标"与"触发器索引认为相同的目标"必须完全一致；
  allocator 不自定义相等性（`BREAK_BLOCK` 用方块身份、`KILL_ENTITY` 用实体身份）；
- 个人任务：负责人即玩家自己；团队任务按 `assigneeUuid` 分组，未指派任务不参与（既有规则）；
- 不同项目 / 不同作用域天然隔离。

两类触发器的"账"性质不同，因此分成两条路径：

| 类别 | 账的性质 | 依据 |
|---|---|---|
| `ITEM_COLLECT` | **状态账**：进度 = 当前持有量的切片，可随时重算 | `used = Σ(同组已完成任务的 targetCount)` |
| 累加型（合成/破坏/击杀） | **事件账**：进度 = 本任务实际分到的历史事件量，只能增量推进 | 不需要 `used`，每批事件当场分账 |

`quota participation` 是三态的（§5.1）：`ITEM_COLLECT → STATE`、
`CRAFT / BREAK / KILL → EVENT_BATCH`、**`ADVANCEMENT → NONE`**。

**`ADVANCEMENT` 的已知残留（有意排除，不是 bug）**：`ADVANCEMENT` 本阶段不纳入额度配额，
因此它**仍沿用现在的逐任务独立判定**——多条 `ADVANCEMENT` 任务追踪同一个进度时，
同一次获得进度依然会把它们一起判完成。这是阶段性取舍：
`ADVANCEMENT` 的 `targetCount` 恒为 1、本质是一次性事件，量化"额度"没有意义。
后续若要处理，需单独立项，且必须按 §5.1 的三态归入 `EVENT_BATCH` 或新引入模式。

**`used` 没有新增持久化**：它由"同组已完成任务"直接推导。

### 3.2 分配顺序：任务列表顺序（从上往下）

顺序键是**路径上的 Segment 序列**（支持任意深度），比较方式为逐段字典序：

```
OrderKey(task) = [Seg0, Seg1, Seg2, ...]      // 根 → 父 → 子 → 孙
  Seg0（顶层）  = (sort_order, created_at, id)
  Seg1..SegN（第 N 层子任务）= (subtask_sort_order, id)

比较规则（必须写死，避免 tie 时返回 0 让顺序依赖原集合迭代序）：
  逐段比较，段内按字段顺序依次比较；
  Seg0 的 sort_order 相同时比 created_at，再相同比 id；
  子层 segment 的 subtask_sort_order 相同时比 id；
  前缀相同而路径更短者在前：
    [1] < [1,1] < [1,1,1] < [1,1,2] < [1,2] < [2]
```

每层的 tie-break 必须落在比较键里（不能只把 `id` 写在文字说明中）：
否则 `sort_order` 相同的两条任务 OrderKey 相等 → comparator 返回 0 → 排序结果取决于集合迭代序，
重启后顺序可能变化，直接破坏 I3 与"跨重启一致"。

**Segment 口径必须与现有实现一致，不要自定义：**

| 层 | Segment | 现有权威实现 |
|---|---|---|
| 顶层 | `(sort_order, created_at, id)` | `H2TaskStore` 的 `ORDER BY sort_order, created_at, id` |
| 子层 | `(subtask_sort_order, id)` | `TaskManager#getSiblingSubtasksInOrder` 的比较器 |

**不存在 NULL / 缺失分支，不要为此加兜底逻辑**：OrderKey 用到的列在 schema 里全部 `NOT NULL`，
读取走 `ResultSet#getLong` → Java 基本类型 `long`，因此 Segment 字段恒有值，
既不依赖 H2 的 NULL 排序规则，也不需要"无 `sort_order` 则用 `created_at`"这种分支
（`created_at` 是**相同 `sort_order` 时的第二级比较**，不是缺失时的回退）。

| 列 | schema 定义 | 在 OrderKey 中的角色 |
|---|---|---|
| `sort_order` | `BIGINT NOT NULL` | 顶层第一级 |
| `created_at` | `BIGINT NOT NULL` | 顶层第二级 |
| `id` | `VARCHAR(64) NOT NULL`（主键组成部分） | 顶层第三级 / 子层第二级 |
| `subtask_sort_order` | `BIGINT NOT NULL DEFAULT 0` | 子层第一级 |

四列都非空、`id` 又是主键的一部分，**OrderKey 才是真正的全序**（不会出现两条任务比较结果相等）。

`id` 的格式也写死：由 `Task` 构造器 `UUID.randomUUID().toString()` 生成，
即**小写十六进制 UUID 字符串**（`8-4-4-4-12`，仅 `0-9a-f` 与 `-`），
因此 `String.compareTo` 与任何合理 collation 的排序结果一致，不存在大小写 / locale 歧义。

**判定顺序只由内存 OrderKey 决定，不需要与 H2 的 collation 保持一致**：
`groupKey` / 顺序视图都在内存里构建（§5.2 明确"子层必须显式排序"），
DB 的 `ORDER BY` 只是"数据来源"，不是"判定依据"。真正的约束是——

> **全过程只能有一个比较器**：§5.2 的顺序视图、allocator 的输入顺序、测试断言都必须用同一个
> OrderKey 实现；一旦某条路径改用 DB 返回顺序，就会出现两套口径。

若担心"某条路径偷偷用了 DB 顺序"，用测试 54（H2 加载结果 vs 内存 OrderKey 排序结果一致）兜底。

**子层顺序必须显式排序，且两条数据路径的 tie-break 不一致，务必以内存比较器为准：**

| 路径 | 排序键 | 说明 |
|---|---|---|
| `H2TaskStore#loadBucket`（任务桶加载） | `ORDER BY sort_order, created_at, id` | **不含 `subtask_sort_order`**（该列只在 SELECT 列表里），子任务顺序在此路径下不成立 |
| `H2TaskQueryService`（子任务查询） | `ORDER BY subtask_sort_order, created_at, id` | 有子任务顺序，但 tie-break 是 `created_at`，与内存比较器不同 |
| `TaskManager#getSiblingSubtasksInOrder` | `(subtask_sort_order, id)` | **判定顺序以这条为准**（GUI 列表同源） |

即：`subtask_sort_order` 相同时，内存口径比 `id`、DB 口径比 `created_at`。
`reorderSubtasks` 会写入互不相同的序号，正常情况下不会撞上这个差异；
但历史 / 异常数据可能出现相同值，**OrderKey 必须明确采用 `(subtask_sort_order, id)`**，
不能依赖 DB 返回顺序（`loadBucket` 路径下更是完全没有子任务顺序）。

因此构建"列表顺序视图"（§5.2）时，必须**按父任务分组后显式按子层 Segment 排序**，
不能假设 load 出来就是对的。

这等价于"深度优先展开"（父任务 → 它的子任务 → 下一个顶层任务），与列表展示一致，
而且天然覆盖 父→子→孙 多于两层的情况。顶层顺序由 `TaskManager.reorderTasks` 写回 `sort_order`，
子层顺序由 `reorderSubtasks` 写回 `subtask_sort_order`，两者都持久化，跨重启一致。

**排序来源只能用持久化字段**，不要用视图派生顺序：

| 不要用 | 原因 |
|---|---|
| 优先级排序开关（`sortByPriority`） | 切一个显示设置就换一套判定结果（违反 I3） |
| 折叠 / 展开状态 | 折叠只是隐藏子任务，不改变相对顺序 |
| 搜索过滤 / 分页 / HUD 与 GUI 的差异 | 视图不同不应产生不同完成结果 |

### 3.3 收集类（ITEM_COLLECT）：状态账，两遍分配

```
输入 = 同组【未完成】任务（已按 §3.2 OrderKey 排好序）+ 该组 used + 该组 held
pool = max(0, held − used)  // held 沿用现有 countHeldItems 口径（含容器展开）
used = Σ(同组【已完成】任务的 targetCount)   // 由调用方在组内算出并传入，见 §5.4 数据来源约束

第一遍（决定谁完成）——只有能"整份满足"的任务才占用额度：
    for taskId in orderedIncompleteTaskIds:
        if (pool >= targetCount[taskId]) {
            resultProgress[taskId] = targetCount[taskId]  // 必须写满：完成态存储值 = 展示值（I4 第 3 条）
            completedIds.add(taskId)
            pool -= targetCount[taskId]
        }

第二遍（决定展示）——把剩余额度按同一顺序**排他**地填给仍未完成的任务：
    for taskId in orderedIncompleteTaskIds:              // "未完成" = 第一遍之后的完成集合之外
        if (taskId in completedIds) continue
        take = min(targetCount[taskId], pool)
        resultProgress[taskId] = take
        pool -= take

返回 AllocationResult(resultProgress, completedIds)      // 纯计算结果，不触碰任何 Task 对象
```

**伪代码是"状态输入 → 状态输出"的纯计算**：allocator 全程只读写 `targetCount[...]` /
`progress[...]` / `resultProgress` / `completedIds` 这些值，**不接收也不修改 `Task` 实例**
（§5.1 的契约）。回写由 service 层负责：拿到 `AllocationResult` 后统一执行
"写 `trigger.progress` → `completeNormalized()` → `removeIndex` → `markDirty` → 通知"。
这样 allocator 才能真正做到无 MC 依赖、可离线单测。

`used` 之所以由调用方传入而不是 allocator 自己收集：已完成任务会被 `removeIndex` 摘出索引，
它的 `targetCount` 只能从桶内全量任务列表里累加（§5.4 第 1、2 条），
而 allocator 只拿到"未完成候选"，看不到已完成任务。

**两个前置约束（都容易实现错）：**

- **候选必须已经属于同一 quota group**：allocator 不接受混杂列表，也不自己判断目标相等性
  （与 §3.1"不自定义相等性"一致）。调用方传 `StateQuotaSnapshot` / `EventBatchQuotaSnapshot`（§5.1），
  allocator 只负责在同一组内按顺序分账；
- **第二遍的"未完成"必须是第一遍执行完成之后的状态**，不能复用第一遍之前的快照。
  典型错误写法是先把未完成任务收集成 `incomplete`，再跑第一遍、再 `secondPass(incomplete)`——
  那样第一遍刚完成的任务仍在列表里，会被第二遍再写一次 `progress`。

三个要点：

- **`pool` 必须夹到 0 以上**：`used` 只由"已完成任务的 `targetCount`"决定，不会因玩家丢弃物品而缩小，
  所以 `held < used` 是常态（例：A 已完成 ×32、当前持有 16 → `pool = max(0, 16 − 32) = 0`），
  夹紧后 B 显示 `0/32` 而不是 `-16/32`；
- **整份满足才占用**：`32` 那条在持有 16 时不会被"部分占用"，额度留给后面能整份满足的 `16`；
- **展示排他**：第二遍同一份额度只填给一条任务，避免"多条任务都显示 16/32 却谁也完不成"。

**"列表顺序"是分账顺序，不是严格优先级**：列表顺序决定"谁先被尝试"，
但第一遍的"整份满足才占用"会让**排在后面、却能一次满足的任务先完成**
（`A ×32` 在前、`B ×16` 在后、`held = 16` → B 先完成，A 显示 `0/32`）。
这是有意设计（否则 `×32` 会长期"占着"额度导致 `×16` 永远做不了），但**不要把它描述成"严格按优先级分配"**。

注意与材料配方预览的区别：配方预览（`MaterialListScreen`）用的是**严格顺序**占用
（上面的行先吃掉数量，下面的行只能用剩余量）；Phase Z 是**任务级额度**，用条件优先级。
两者场景不同，**不需要也不应该强行统一**。

第一遍必须**同时**写 `resultProgress[taskId] = targetCount[taskId]` 并把 id 放进 `completedIds`：
若第一遍只登记完成而不写进度，回写链路（不再像现有实现那样先 `setProgress(held)`）
就会留下"已完成却 `0/16`"的状态，直接违反 I4 第 3 条。

### 3.4 累加型（合成 / 破坏 / 击杀）：事件账，批次分账

```
每次同组事件到达（一批，量 = amount）：
    pool = amount
    for taskId in orderedIncompleteTaskIds:
        need = targetCount[taskId] − progress[taskId]
        take = min(need, pool)
        resultProgress[taskId] = progress[taskId] + take
        pool -= take
        if (resultProgress[taskId] == targetCount[taskId]) { completedIds.add(taskId); }
    剩余 pool 丢弃（没有别的任务要领）
    返回 AllocationResult(resultProgress, completedIds)   // 同样只输出值，不改 Task
```

与收集类一致，这段也是**纯计算**：`progress[taskId]` 取自输入的快照值，
结果写进独立的 `resultProgress`，allocator 不持有也不修改 `Task`。

为什么这样是对的：

- **每批事件只被分账一次** → 不再共享（I2）；
- **不引用任何历史量** → 不存在"后创建的任务被扣掉它没见过的历史额度"；
- **单条任务时 pool 全给它**，是唯一选择 → 与现有语义完全一致（硬约束 2）；
- `progress` 就是"本任务实际分到的量"，**存储值 = 展示值**，不会出现"显示 4/4 却未完成"；
- `need = targetCount − progress`、`take = min(need, pool)`，达标瞬间 `progress` 必然正好等于
  `targetCount`，因此**天然满足 I4 第 3 条**，无需像收集类那样额外补写。

反例对照（A 先完成、B 后创建，B 之后收到 4 个事件）：

| 算法 | 结果 |
|---|---|
| `raw − used`（早期草稿的 1a） | `4 − 4 = 0` → B 不完成，还要再收 4 个（**多扣历史额度**） |
| 批次分账（本方案） | pool=4 全给 B → **B 正常完成** |

**批次边界（写死，不留解释空间）**：

> Batch = **单次进入 `advanceMatchingTasksByUuid` 的调用**所携带的 `amount`。

即"一次合成 8"是一个 `pool = 8` 的批次，**不是**拆成 8 次 `amount = 1`；
批次内候选集合与顺序取快照（沿用现有"索引副本 + 顺序快照"写法），批内不变。

需要说明的是：本算法是**无记忆的顺序贪心 + 完成即出候选**，
**在"任务快照与顺序均不变、批次之间没有 quota-affecting mutation"的前提下**，
单个大批次与拆成多个连续小批次结果相同（`A=3, B=3` 总量 6 时，一次 `pool=6` 与拆成 6 次一致）。
反之，若批次之间发生新建 / 删除 / 重排 / 改目标等 mutation，两种切分当然会分叉——
但那时它们本来就不是"同一批"，属于 §5.4 第 3 条的 mutation 语义。
把边界写死的目的是**消除实现歧义**，同时划清下面这条责任：

> `advanceMatchingTasksByUuid` **不得**因同一个 MC 事件被重复回调而再次消费同一批事件。
> 事件去重（at-least-once → 实际只消费一次）属于现有触发事件链的责任，
> **不属于额度分配器**。

因此 §6 测试 17 的准确表述是"**纯计算确定性**"：
同一任务状态 + 同一批次输入 → allocator 输出一致；
**不是**"整个累加型链路支持 exactly-once"。

### 3.5 两类的不对称（必须明确，避免后续误判为 bug）

| | 收集类（状态账） | 累加型（事件账） |
|---|---|---|
| 能否随时重算 | ✅ 纯函数：改顺序 / 改目标 / 改指派后立即重算 | ❌ 事件已经"花掉"，无法回溯 |
| 拖拽的影响 | 立即改变"谁先完成"，可能让某条提前完成 | **只影响后续批次**，已分配的不返还 |
| 取消完成 / 删除任务 | 释放额度，后续任务可能提前完成 | **不返还已分账事件**，后续任务不追溯补领 |
| `targetCount` / 目标 / 指派变更 | 立即重算 | `progress` 归零，旧账不迁移 |
| 进度含义 | 当前可分配到的持有量 | 本任务实际分到的历史事件量 |

这是语义必然（事件已发生），也正好符合 I1。

### 3.6 行为示例

两条 `收集 铁锭 ×32`（列表在前）、`收集 铁锭 ×16`（列表在后）：

| 背包持有 | 铁锭 ×32 | 铁锭 ×16 | 说明 |
|---|---|---|---|
| 0 | 0/32 | 0/16 | 都没占用 |
| 16 | 16/32 未完成 | **16/16 完成** | 32 不能整份满足，额度让给后面的 16 |
| 16（上一行之后） | 0/32 未完成 | 已完成 | 第二遍展示：剩余额度为 0 |
| 48 | 32/32 完成 | 已完成 | 合计 48，不再共享 |

两条 `合成 木板 ×4`（同批创建）：

| 累计合成 | 任务 A | 任务 B | 说明 |
|---|---|---|---|
| 4（一批） | **4/4 完成** | 0/4 | pool 被 A 吃满 |
| 再 4（一批） | 已完成 | **4/4 完成** | 合计 8 才全部完成 |
| 一次 8（一批） | **4/4 完成** | **4/4 完成** | pool=8 → A 拿 4 → B 拿 4 |

A 完成之后再创建 B，B 之后合成 4：**B 正常完成**（不承担 A 的历史额度）。

## 4. 边界与一致性命中检查

| 场景 | 期望行为 | 依据 |
|---|---|---|
| 已完成后玩家丢弃物品，持有量下降 | 已完成保持完成；未完成任务的展示进度可下降 | I1 |
| 取消勾选 / 删除某条已完成任务（收集类） | 释放额度；后面的任务可能"提前完成" | I1 例外（只会更早完成） |
| 取消勾选 / 删除某条已完成任务（累加型） | **不释放历史事件**，后续任务不追溯补领；只影响后续批次 | §3.5、§4.2 |
| **拖拽调整顺序** | 收集类立即重算、可能让某条提前完成；累加型只影响后续批次 | §3.5 |
| 修改已完成任务的 `targetCount` / 目标（触发器编辑） | **显式重置**：`completed = false` + `progress = 0` → 写入新触发器 → 该组重算（GUI 给一次确认提示） | 决策 3、§4.1 |
| 修改未完成任务的 `targetCount`（收集类） | 该组重算；已完成的保持完成 | I1 |
| 修改未完成任务的 `targetCount`（累加型） | `progress` 随触发器重建归零，按新目标从头累计 | §4.1 |
| 修改触发器目标 identity（两类） | 收集类该组重算；**累加型 `progress = 0`**，视为新目标、旧目标事件不迁移 | §4.1 |
| 取消完成（累加型） | **`progress` 归零**，从"重新开始"参与后续批次 | 见 §5.4 |
| 取消完成（收集类） | 按当前持有量重算，若仍达标会再次自动完成（绝对量语义，保持现状） | 既有语义 |
| 团队任务改派 / 取消领取 | 按新负责人的组重新分配；旧组额度释放、进度清零 | 既有规则 |
| 未指派的团队任务 | 不参与分组与判定 | 既有规则 |
| 某目标只有一条任务 | 收集类 `used=0`、`pool=held`；累加型 pool 全归它 → **额度行为**与现在完全一致 | 硬约束 2 |
| 手动勾选完成（收集类） | 直接计入"已完成"→ 自动占用额度 | 与"触发器优先 + 可手动勾选"一致 |
| 手动勾选完成（累加型） | 计入"已完成"，且 `progress` **补至 `targetCount`** | §4.2 |
| 父任务带触发器（触发器优先） | 父任务不因聚合完成；额度分配只看它自身完成态 | 不侵入既有规则 |
| 优先级排序开关 / 折叠 / 搜索 | 判定结果不变 | I3 |
| 同一批事件同时满足多条 | 按顺序逐个吃 pool，不会重复分配 | I2 |

### 4.1 触发器编辑：`progress` 清零 + 已完成任务显式重置（新增 / 删除 / 修改，Phase Z 一并落地）

修改前存在两个事实：

1. GUI 保存触发器时**无条件重建**对象（`TriggerEditScreen#save` → `new TaskTrigger(type, target, targetCount)`），
   而 `TaskTrigger` 的 `type / target / targetCount` 是 final 字段、构造器把 `progress` 置 0
   → **改类型、改目标或只改 `targetCount` 都会让 `progress` 归零**；
2. 保存链路 `TodoScreen#applyTriggerFromEditor` 只做 `task.setTrigger(...)` + 标记未保存，
   **不动 `completed`**。

**触发类型是可编辑的**：`TriggerEditScreen` 的类型按钮在 `TaskTrigger.Type.values()`（全部 5 种）
之间轮换，因此 `ITEM_COLLECT ↔ CRAFT_ITEM` 是真实可操作的路径，**类型变更同样属于跨 group**。

两者叠加会产生"已完成但 `0/8`"这类违反 I4 第 3 条的状态。
**Phase Z 一并修正**，把决策 3 真正落地（`completed ⇒ progress == targetCount` 必须在所有路径成立）。

规则要按**上位**的粒度描述，而不是只枚举"改目标 / 改数量 / 改类型"：

> **新增 / 修改触发器、且变更后任务仍有触发器时**，若任务已完成，则显式重置
> （`completed = false` + `progress = 0`）；
> **删除触发器**是例外：**不重置 `completed`**（触发器只是判定手段，删掉不改变"任务确实做过"），
> 只释放旧组额度并移除后续进度约束。
> 是"跨组"还是"无组"由 `beforeGroup` / `afterGroup` 决定。

| 编辑动作 | 任务原状态 | Phase Z 行为 |
|---|---|---|
| **新增**触发器（原无 → 有） | 未完成 | `beforeGroup = null` → 只处理 `afterGroup`（`ITEM_COLLECT` 按当前 `held` 判定） |
| **新增**触发器（原无 → 有） | 已完成 | **显式重置**：`completed = false` + `progress = 0` → 处理 `afterGroup`。否则会留下 `completed` + `progress = 0`，直接违反 I4 |
| **删除**触发器（原有 → 无） | 任意 | `afterGroup = null` → 只处理 `beforeGroup`（释放额度）；**`completed` 保持、不重置**。触发器被移除后 `progress` 随 `TaskTrigger` 一起消失，不存在"无触发器却带旧进度"的残留 |
| 改目标 identity | 未完成 | 旧组 + 新组各处理；累加型 `progress = 0`，视为新目标，旧目标事件不迁移 |
| 改目标 identity | 已完成 | **显式重置**：`completed = false` + `progress = 0` → 写入新触发器 → 旧组 + 新组各处理 |
| 改触发类型（含跨类） | 未完成 | **跨组**：旧组释放额度 → 处理；新组按新类型语义参与。累加型 `progress = 0` |
| 改触发类型（含跨类） | 已完成 | **显式重置** + **跨组**：`completed = false` + `progress = 0` → 旧组处理 → 新组按新类型判定 |
| 只改 `targetCount` | 未完成 | 该组处理（不跨组）；累加型 `progress = 0`（触发器重建），按新目标从头累计 |
| 只改 `targetCount` | 已完成 | **显式重置**：`completed = false` + `progress = 0`，再按新目标参与分配 |

"新增触发器到一条已完成任务"是最容易漏的一条：现有链路
`TodoScreen#applyTriggerFromEditor` 在 `trigger == null` 时只是 `task.setTrigger(null)`，
新增时也没有把 `completed` 置回未完成，**两条路径都必须走上面的上位规则**。

注意**触发器重建本身会把 `progress` 置 0**（`new TaskTrigger(...)` 构造器的既有行为），
与 Phase Z 是否额外置零无关；Phase Z 要负责的是"**完成态**"的重置，以及受影响组的处理。

**编辑后两个 group 的处理按类型分开（不要一律"重算"）：**

```
ITEM_COLLECT（状态账）：旧组与新组都执行完整状态重算（按当前 held）
CRAFT/BREAK/KILL（事件账）：
    只建立新任务状态（progress = 0），
    绝不重新扫描 / 回放历史事件；
    仅未来批次继续推进
```

实现落点：`TodoScreen#applyTriggerFromEditor` 在 `setTrigger` 之前判断
"任务已完成且触发器参数发生变化"，命中则先取消完成；写入后按 §5.4 第 8 条
对 `beforeGroup` 与 `afterGroup` 各触发一次处理。
**GUI 需给一次确认提示**（决策 3），因为该操作会把已完成任务退回未完成。

### 4.2 手动完成 / 取消对两类触发器的语义

| 操作 | 收集类（状态账） | 累加型（事件账） |
|---|---|---|
| 手动勾选完成 | 计入"已完成"，并**立即把 `progress` 写成 `targetCount`** | 计入"已完成"，并把 `progress` **补至 `targetCount`** |
| 取消完成 | **显式置 `progress = 0`**；随后组级重算按当前 `held` 覆盖成正确展示值，若仍达标会再次自动完成 | **显式置 `progress = 0`**，过去已分账事件不返还 |

**两类取消完成都必须显式把 `progress` 置 0**，收集类不能只依赖"后续重算覆盖"：

不重算的路径是真实存在的——**主机可以在成员离线时勾选团队任务**
（`TaskManager#toggleTaskCompletion` 不做 assignee 在线校验），
而 §5.4 第 11 条明确"离线负责人不重算"。若收集类取消完成时不动 `progress`，就会留下

```
completed = false && progress == targetCount
```

——既违反 I4 双向形式，又会被 `Task.fromNbt` 的加载规范化**改回 `completed = true`**，
等于"**用户的取消操作被悄悄吞掉**"。显式置 0 后该状态合法（`0 < targetCount`），
在线时随即被重算覆盖为正确展示值，离线时保守显示 `0/N`，下次 `held` 快照到达即修正。

**收集类不能写成"进度随后续重算刷新"**：已完成任务会被 `removeIndex` 摘出索引，
而候选来源正是索引，所以重算**根本不会覆盖已完成任务**。反过来必须立刻写满：
`held = 0` 时手动完成一条 `0/16` 的收集任务，若只置 `completed = true`，
重算时该任务不参与、`used = 16` 直接把它算成已完成额度，最终留下 `completed` 却 `0/16`
的状态，违反 I4 第 3 条。

**覆盖范围是"所有把 `completed` 置为 `true` 的入口"**，不只是 `TaskTriggerService.completeTask`：

| 入口 | 说明 |
|---|---|
| `TaskManager#toggleTaskCompletion` | GUI 单条勾选 / 父任务批量勾选（当前只 `setCompleted`，需补 `progress`） |
| `CommandBootstrap` 的完成类命令 | `/todo task done` 等（当前只 `setCompleted(true)`，需补 `progress`） |
| `TaskTriggerService#completeTask` | 自动完成链路（走 §3.3 / §3.4 的写满逻辑） |

累加型"补满"的理由与收集类相同（存储值 = 展示值），补满后又与"取消完成归零"正好对称；
已完成任务已不在候选集合内，因此不会残留脏进度。

**手动完成的产品语义（写死，避免以后被当成"凭空多出的事件"）**：

> 手动完成 = **直接授予该任务所需的剩余完成额度**（`progress` 补至 `targetCount`），
> 它**不是**一笔事件：不进入事件账、不产生可供其它任务使用的事件额度、也不追溯 / 回放历史事件。

也就是说 `2/4` 手动完成后，那"多出来的 2"只存在于这条任务的完成态里，
不会出现在同组其它任务的额度池中，也不会被记入任何历史事件总量。

## 5. 实现要点

### 5.1 新增纯逻辑组件

`com.todolist.trigger.TriggerCreditAllocator`（无 MC 依赖，可离线单测）。
两类的分配形状相同（`take = min(need, pool)` 的顺序贪心），只是 `pool` 与 `need` 的来源不同：

```
收集类：pool = held − used，need = targetCount（整份满足才占用），结果含展示进度
累加型：pool = 本批事件量，need = targetCount − progress（允许部分累加），结果只含完成集合 + 新的 progress
```

**接口契约（Z1 冻结，之后不再改动语义）：**

按模式**拆成两个输入类型**——让非法组合**无法被表达**：

```
输入 StateQuotaSnapshot {                    // 仅收集类（quotaMode = STATE）
    groupKey,                                // (bucket/项目, 触发类型, 目标身份, 负责人)
    List<TaskId> orderedIncompleteTaskIds,   // 同组【未完成】任务，已按 §3.2 OrderKey 排好
    Map<TaskId, Integer> targetCountByTaskId,
    int used,                                // 同组已完成任务的 targetCount 之和，由调用方算出
    int held                                 // 当前持有量（含容器展开）
}

输入 EventBatchQuotaSnapshot {               // 仅累加型（quotaMode = EVENT_BATCH）
    groupKey,
    List<TaskId> orderedIncompleteTaskIds,
    Map<TaskId, Integer> targetCountByTaskId,
    Map<TaskId, Integer> progressByTaskId,   // need = targetCount − progress
    int amount                               // 本批事件量（Batch 边界见 §3.4）
}

输出 AllocationResult {                      // 两类共用
    Map<TaskId, Integer> progressByTaskId,   // 覆盖 orderedIncompleteTaskIds 的【全部】任务
    Set<TaskId> completedTaskIds
}
```

**为什么拆成两个类型**：用单个 `QuotaGroupSnapshot` + `mode` 字段时，
"`mode` 与 `groupKey.type` 必须自洽、STATE 不能传 `amount`"只能**靠约定**防错；
拆成两个 record 后，STATE 里**根本没有** `amount` 字段、EVENT_BATCH 里**根本没有** `used` / `held`，
**靠类型防错**，也就直接消灭了整类接线错误。
入口可以是两个方法（`allocateState(...)` / `allocateEventBatch(...)`）或一个重载，由 Z1 决定。

**`progressByTaskId` 是"全量结果"而不是"变更结果"**：输出必须覆盖输入 `orderedIncompleteTaskIds`
中的每一条任务（含本轮未变化、`take = 0` 的任务），值是它的最终 `progress`。
这样调用方不需要 `getOrDefault(旧值)` 之类的补丁逻辑，也不会出现"漏回写导致展示停在旧值"。
已完成任务不在候选输入中，因此**无需**出现在 `progressByTaskId` 里。

**但"全量结果"只是 allocator 的契约，不等于"无条件写库 / 无条件推送"**（两件事要分开）：

| 层次 | 约定 |
|---|---|
| allocator | 输出**全量** `progressByTaskId`（纯计算，不做 diff） |
| service 回写 | **按 diff 处理**：`newProgress == oldProgress` 的任务不写 `progress`、不 `markDirty`、不推送 |
| 完成态 | 只对 `completed: false → true` 的任务执行 `completeNormalized()` / `removeIndex` / 完成通知 |

否则每次背包变化都会把整组任务重新 `UPDATE` 一遍并推送，产生无意义的写放大与 HUD 抖动
（`A 16/32 / B 0/16 / C 0/8` 重算后若结果不变，应完全不产生写入与推送）。

**`groupKey(task)` 的返回值必须先定死**（三态 `quotaMode` 只管"有组之后怎么处理"，管不了"有没有组"）：

```
groupKey(task):
    if (!task.hasTrigger())                  return null      // 无触发器 = 无组
    if (task 是团队任务 && assignee 为空串/null) return null      // 未指派 = 无组（null 与 "" 等价）
    return (bucket/项目, trigger.type, normalizeTarget(trigger.target), owner(task))
```

**`owner(task)` 的来源必须唯一，禁止依赖 ambient context**：

| 任务类型 | `owner` 取值 | 来源（唯一） |
|---|---|---|
| 个人任务 | **`CachedBucket.playerUuid`**（= DB 的 `tasks.owner_uuid`），绝不为 `null` / 空串 | **持久化的桶所有者**，不是"当前操作的玩家"或"事件玩家" |
| 团队任务 | `normalizeAssignee(task.assigneeUuid)`，`null` 与 `""` 等价=未指派 | 任务的指派字段（持久化） |

**禁止**用"当前玩家 / 事件玩家 / GUI 视角玩家"等 ambient 信息推导 `owner`：

- 团队场景下"事件玩家"（触发事件的 assignee）与"桶所有者"（团队）本来就是不同的人，
  用错会把两个不同的组错误合并（团队任务被按事件玩家拆组，或反之）；
- 个人任务用 ambient 玩家推导，在跨玩家推送 / 服务端代算路径上会直接把任务归到错误的人。

不同玩家的个人任务天然属于**不同 bucket**（个人桶按 owner 隔离），因此即使同物品、同类型也不会互相占额；
把 `owner` 显式写进 `groupKey` 是为了防止实现时漏掉"个人任务也要带身份"这一层。

| 情况 | `groupKey` | `quotaMode` |
|---|---|---|
| 无触发器 | `null` | —（不进流程） |
| 团队未指派 | `null` | —（不进流程） |
| `ITEM_COLLECT` | 非空 | `STATE` |
| `CRAFT` / `BREAK` / `KILL` | 非空 | `EVENT_BATCH` |
| `ADVANCEMENT` | 非空 | `NONE` |

注意 `!hasTrigger → null` 这条**天然覆盖了"删除触发器"这个 mutation**：
`有 Trigger → 无 Trigger` 就是 `beforeGroup = 旧组, afterGroup = null`（§5.4 第 8 条流程已支持）。

**Snapshot 合法性必须由调用方保证，allocator 不纠正**（它"不分组、不判断"，
一旦传入非法组合会"非常正确地算出错误结果"）：

```
StateQuotaSnapshot:        used ≥ 0 且 held ≥ 0
EventBatchQuotaSnapshot:   amount ≥ 0
两者共同：orderedIncompleteTaskIds 中每条任务 0 ≤ progress < targetCount   // 未完成 ⇒ 严格小于（I4 双向）
          targetCount ≥ 1
```

**模式是三态的，不要写成二元**（`ADVANCEMENT` 属于 `NONE`，既不进 allocator，也不能落进事件账分支）：

```
ITEM_COLLECT          → STATE        （状态账：可由 held 快照反复重算）
CRAFT / BREAK / KILL  → EVENT_BATCH  （事件账：只能按批次向前消费）
ADVANCEMENT           → NONE         （不参与 Phase Z 额度配额，保持既有语义）
```

- **新增触发类型时必须显式声明其 quota participation：`STATE` / `EVENT_BATCH` / `NONE` 三选一**，
  并同步确认该模式下 `used / held / amount` 的语义；
- `NONE` 不构造任何 snapshot；
- 不允许出现"既不属于状态账也不算事件账"的隐式默认分支——
  **`else` 一律不许当作 `EVENT_BATCH` 的同义词**（§5.4 第 8 条同样按三态判断）。

allocator **不做排序、不做分组、不做相等性判断**，只做"同组内按已给顺序分账"，
保证"顺序来源"与"分组来源"各只有一处定义。

**后置条件（postcondition，allocator 必须保证）：**

```
结果中的 progress 全部落在 [0, targetCount]
completedTaskIds 中的任务，其 progress 必为 targetCount
收集类：Σ(本轮新增占用) ≤ max(0, held − used)                  // I2
        "本轮新增占用" 严格定义为【第一遍完成任务的 targetCount】之和
累加型：Σ(本轮 take) = min(amount, Σ候选剩余需求)               // I2
        停止条件 = pool 用尽 或 所有候选均达标（需求耗尽）；
        两者都不是时不得提前停止；仍有剩余 pool 则直接丢弃（没有别的任务要领）
同一任务不会同时出现在"两次分配"里重复吃额度                    // I2
```

**"占用"与"展示"必须严格区分**（写死，否则 `used` 会被重复计算）：

| 阶段 | 写入 | 是否算"占用" | 说明 |
|---|---|---|---|
| 收集类第一遍（整份满足 → 完成） | `progress = targetCount` + 进 `completedIds` | ✅ **算**，计入 `used` | 这是真正的额度消费 |
| 收集类第二遍（填给未完成任务） | `progress = take` | ❌ **不算** | 只是展示切片；**不计入 `used`、不跨轮累积、不影响下一轮 `pool`** |

下一轮的 `pool` 只由 `held − Σ(已完成任务 targetCount)` 决定，
而第二遍写入的进度属于**未完成任务**，不参与该求和 → 天然不会累积。

注：累加型的停止条件是"**需求耗尽或 pool 用尽**"，不是"amount 用尽即停"——
例如 A、B 各需 2、`amount = 8` 时实际 `Σ take = 4`，剩余 4 丢弃，此时 `amount` 并未用尽。

**前置条件（precondition，由调用方保证；allocator 内部可加断言，不做兜底逻辑）：**

```
targetCount ≥ 1        // TaskTrigger 构造器与 fromNbt 均 Math.max(1, ...)，已有保证
progress ∈ [0, targetCount]
held ≥ 0
used ≥ 0
amount ≥ 0             // 累加型批次量。现网入口全部 ≥ 1：
                       //   Fabric 合成 Math.max(1, product.getCount())
                       //   Forge 合成非空 ItemStack.getCount()（恒 ≥ 1）
                       //   BREAK / KILL / ADVANCEMENT 硬编码 1
                       // 写成前置条件是为防未来新接入方传 0 或负数（负数会让 pool 直接变负）
候选必须已属于同一 quota group（调用方分组，allocator 不校验、不纠正）
```

**驱动单位必须是 quota group，不是单个任务。** 重算入口按组命名
（如 `recalculateCollectQuotaGroup(groupKey)`），不要实现成 `recalculateTask(task)`——
额度是组级状态，"只重算被改的那条任务"算不出同组其他任务的额度变化（§5.4 第 3、8 条）。

### 5.2 排序来源

在桶内提供统一的"列表顺序"视图供引擎取候选任务：

- 按 §3.2 的 OrderKey Segment 路径序生成（顶层 `(sort_order, created_at, id)`、子层 `(subtask_sort_order, id)`）；
- **子层必须显式排序**：`H2TaskStore` 的 `ORDER BY sort_order, created_at, id` 不含 `subtask_sort_order`，
  load 出来的顺序对子任务不成立；可按父任务分组后复用 `TaskManager#getSiblingSubtasksInOrder` 的同款比较器；
- 与折叠/展开、优先级排序、搜索过滤**无关**；
- 建议实现成 `bucket.tasks` 的一次排序（或缓存），供收集类与累加型共用。

### 5.3 接入点

| 位置 | 现状 | 改法 |
|---|---|---|
| `recalculateItemCollectByUuid` | 逐任务 `setProgress(held)` + `isSatisfied()` | 取 `held` + 由桶内全量列表算 `used` → 按 §3.3 两遍分配 → 回写进度 + 完成集合 |
| `advanceMatchingTasksByUuid` | 循环内 `addProgress(amount)` 后立即判完成 | 改为 §3.4 批次分账：候选（未完成、同组）按列表顺序吃 `pool`，再统一提交完成；覆盖 `CRAFT_ITEM` / `BREAK_BLOCK` / `KILL_ENTITY` |
| `completeTask` / `markDirty` / `removeIndex` / 通知推送 | 既有链路 | 完成动作仍走原链路 |
| `Task#setCompleted` | 被多处直接调用，各自不管 `trigger.progress` | **收口为两个"天然稳定"的迁移入口** `completeNormalized()` / `revertCompletion()`（见下）；其余入口改调它们 |
| `TaskManager#toggleTaskCompletion` | 只 `setCompleted(...)`；**父任务批量勾选会循环改多个子任务** | 改调收口方法（§4.2）；批量路径按 §5.4 第 10 条聚合成一次重算：先改完全部状态 → `distinct` 汇总受影响组 → 每组只处理一次 |
| `CommandBootstrap` 完成类命令 | 只 `task.setCompleted(true)` | 改调收口方法（§4.2） |
| `Task#fromNbt` | 只做 `clampProgress` | 末尾补加载规范化：`completed && hasTrigger() && progress < targetCount` → `progress = targetCount`（§5.4 第 7 条） |
| `TodoScreen#applyTriggerFromEditor` | 只 `setTrigger` + 标记未保存 | 已完成任务被改参数时先 `revertCompletion()` + 写入新触发器（决策 3、§4.1，含 GUI 确认提示），随后按 §5.4 第 8 条对受影响组去重后逐个处理 |

**完成态迁移收敛为两个入口，且两个入口都必须输出"天然稳定"的状态**
（不要暴露一个只翻转状态位、把非法组合留给调用方收拾的 setter）：

```
completeNormalized()    // 收口所有 completed=false → true
    if (hasTrigger()):
        trigger.progress = trigger.targetCount     // I4 第 3 条
    completed = true
    // 结果：completed == (progress == targetCount) 成立，稳定

revertCompletion()      // 收口所有 completed=true → false，且一步到位
    if (hasTrigger()):
        trigger.progress = 0                       // 两类都置 0（§4.2）
    completed = false
    // 结果：progress = 0 < targetCount，completed = false，I4 双向成立，稳定
```

**为什么取消完成不能只做一个"翻转状态位"的方法**：那样调用方必须先调 `markIncomplete()`
再自己补 `progress = 0`，中间会短暂存在 `!completed && progress == targetCount` ——
既违反 I4 双向，又会被加载规范化改回 `completed = true`（等于取消被吞掉，§4.2）。
把它做成**一个原子入口**后，任何调用方拿到手的就是稳定状态，非法中间态**无法被暴露**。

`revertCompletion()` 之后，收集类仍会被随后的组级重算按当前 `held` 覆盖成正确展示值；
离线（不重算）时保守显示 `0/N`，下次 `held` 快照到达即修正（§5.4 第 11 条）。

**不靠 `package-private` 限制可见性**：这两个入口的调用方分布在
`com.todolist.task`（`TaskManager`）、`com.todolist.trigger`（`TaskTriggerService`）、
`com.todolist.gui`（`TodoScreen`）、`com.todolist.bootstrap`（`CommandBootstrap` 的完成类命令）、
`com.todolist.client`（HUD 同步应用）五个包，收成包内可见会直接改不动；
因此这类约束靠**命名 + 文档**表达，而不是访问修饰符。

两个入口内部**都按类型无关的方式处理**（收集类与累加型一律 `progress = 0` 或 `= targetCount`），
类型相关的差异留给随后的组级重算，`Task` 本身不需要知道自己是哪种触发类型。

**收口点必须放在 `Task` 层，不能放在 `TaskTriggerService.completeTask`**：
`TaskManager` 位于 `com.todolist.task`、不依赖 `com.todolist.trigger`，
而 `TaskTriggerService` 已经依赖 `com.todolist.task`；
若让前者调后者，会形成 **`task` 层 → `trigger` 层的反向依赖，破坏当前
`task → trigger` 的单向分层方向**。在 `Task` 上提供收口方法既满足
"减少可直接 `setCompleted(true)` 的地方"，又不引入反向依赖。

注意：候选列表副本遍历（防 `ConcurrentModificationException`）必须保留；
`removeIndex` 在判定完成后统一执行。

### 5.4 数据来源与重算约束（实现红线）

1. **`used` 只能从桶内完整任务集合计算**（`CachedBucket.tasks`），
   **不能**从触发器活动索引取——完成任务会被 `removeIndex` 摘出索引，
   `collectItemTargets` 遍历的正是索引，从那里永远算不出 `used`；
2. **候选来源与台账来源分离**：索引负责"哪些任务可能受本次事件影响"，
   桶内全量列表负责"同组有哪些已完成任务、占了多少额度"；
3. **额度相关变更（quota-affecting mutation）**：收集类必须触发受影响分组**立即重算**，
   否则"拖拽即优先级"只在下一次事件才生效，玩家会看到旧状态；
   **累加型不做任何历史重算**——它的 `pool` 只来自当前这一批事件（§3.4），
   不存在可以重新扫描出来的历史总量，因此新建 / 删除 / 改目标都不会重分配历史事件：

   | 变更 | 收集类（状态账） | 累加型（事件账） |
   |---|---|---|
   | 排序变化（`reorderTasks`） | 立即重算 | **仅影响后续批次**，不重算 |
   | 新建任务 | 立即重算（新任务可能抢走额度） | 初始化 `progress = 0`，**不补历史事件** |
   | 删除任务 | 立即重算（释放额度） | 删除自身状态，**不回收已分账事件** |
   | **事务内**自动完成（重算/分账过程中） | **不另起重算**，属当前事务（§5.4 第 9 条） | 同左 |
   | **外部**完成（手动 / 批量 / 命令） | 立即重算（`used` 台账变了，同组展示要跟着变） | **不触发组级重算**（累加型没有 `used` 台账；只更新该任务自身状态，后续批次按新状态跳过它） |
   | 取消完成 | **显式置 `progress = 0`**，随后按当前 `held` 重新分配 | `progress = 0`，**已分账事件不返还** |
   | `targetCount` 变更（未完成） | 立即重算（**不跨组** → 只处理一次） | `progress = 0`（触发器重建，§4.1），按新目标累计 |
   | `targetCount` / 目标变更（已完成） | **显式重置**：`completed = false` + `progress = 0` → 受影响组去重后逐个处理（§4.1、§5.4 第 8 条） | 同左 |
   | 触发类型变更（未完成） | **跨组**：受影响组去重后逐个处理 | `progress = 0`，视为新目标 |
   | 触发类型变更（已完成） | **显式重置 + 跨组**：`completed = false` + `progress = 0` → 受影响组逐个处理 | 同左 |
   | 目标 identity 变更（未完成） | **跨组**：受影响组去重后逐个处理 | `progress = 0`，视为新目标 |
   | 指派变更（跨组） | **跨组**：受影响组去重后逐个处理；**同组内改派**（组键不变）则只处理一次 | `progress = 0`，旧事件不迁移 |

   注意"累加型立即重算"这种说法**不成立**，不要在实现里写"重新分账历史事件"的逻辑。

4. **`progress` 语义**：累加型 = "本任务实际分到的历史事件量"（存储值 = 展示值，无需另存原始量）；
   收集类 = "当前可分配到的持有量切片"。二者都直接写回 `trigger.progress`，**不存在 raw/display 双份状态**。
   **代码注释统一口径**：`trigger.progress` 是 **allocation progress（配额分配结果）**，
   不是"任务完成进度"；含义随类型不同（`ITEM_COLLECT` = 当前可分配到的持有量切片，
   累加型 = 实际分到的历史事件量），避免后来者按字面理解成普通进度条。
   数值类型用 `int` 即可，不引入 `long`：`targetCount` 已被 `TaskTrigger` 限制为正 int 且受
   UI 输入约束，`Σ targetCount` 溢出 int 需要同组需求总量超过 21 亿，与项目实际规模相差多个数量级，
   属"不可能发生"的防御，不纳入本轮；
5. **取消完成（两类都显式把 `progress` 归零）**：
   累加型若不清零，它会带着 `progress ≥ targetCount` 回到候选，下一条事件会立刻把它再次判完成，
   取消操作形同无效；收集类若只依赖"后续重算覆盖"，在负责人离线（不重算）时会留下
   `!completed && progress == targetCount`，重载后被加载规范化改回 `completed = true`，
   等于**取消操作被吞掉**（§4.2 有详细推导）。两者都与今天的行为不同，需要一并改并补回归（测试 12、48）；
6. **串行假设与提交时机**：同一 bucket 的额度计算在同一执行上下文完成，或"不可变快照计算 + 统一提交"。
   并且**必须先完成整轮重算、拿到稳定状态，再落库与推送**——
   不得把"取消完成后 `progress` 尚未被重算覆盖"这类中间态写进 H2 或推给 HUD
   （否则会短暂出现 `completed = false` 但 `progress == targetCount`，见 I4 双向形式）。

   **GUI 的本地保存路径同样受本条约束**：现有 GUI 在"局域网主机 / 单人"下会**直接把任务写入本地 H2**
   （`TodoScreen#persistCurrentViewTasksInBackground` 一类链路，不经过服务端保存包），
   这条路径必须**同样调用组处理入口**（而不是只改任务字段就落库），否则：

   - 额度不会被重算（GUI 看到的还是旧展示）；
   - 其他玩家 / 事件链路看到的额度台账与 GUI 写下的状态不一致。

   本条只要求"**quota mutation 与事件链路共用同一组处理入口、同一执行上下文**"，
   **不**要求把 GUI 改成"只能发请求"——把整个编辑链路改成服务端权威是远超 Phase Z 范围的重构，
   且与项目既有"客户端编辑 + 服务端防线（如 `resetTriggerProgressOnAssigneeChange`）"的模型冲突。
   因此 §5.4 第 9、10 条的事务边界同样适用于 GUI 触发的 mutation。
7. **加载规范化（老存档兜底，双向）**：`Task.fromNbt` 结束时按 I4 双向形式修正：
   - `completed && hasTrigger() && progress < targetCount` → `progress = targetCount`；
   - `!completed && hasTrigger() && progress == targetCount` → `completed = true`。

   两条都必须放在 `Task` 层而非 `TaskTrigger.fromNbt`——后者看不到 `completed`。
   该规范化对台账无副作用（`used` 用 `targetCount`，累加型无 `used`），
   但能修复历史脏状态（如手动完成留下的 `completed` + `0/16`，或取消完成瞬态被落库留下的
   `!completed` + `16/16`）；
8. **任何使任务跨 quota group 的 mutation，都按"受影响组去重后逐个处理"的统一流程**，
   不要按场景逐一枚举（漏一个就出 bug）：

   ```
   beforeGroup = groupKey(变更前的 桶/项目 + 类型 + 目标身份 + 负责人)   // nullable
   afterGroup  = groupKey(变更后的 桶/项目 + 类型 + 目标身份 + 负责人)   // nullable

   affectedGroups = distinct(非空(beforeGroup), 非空(afterGroup))   // 去重：同名只处理一次
                                                                    // 过滤：null 表示"无组"，不参与

   for (group in affectedGroups):
       switch (quotaMode(group.type)):     // 三态：STATE / EVENT_BATCH / NONE（§5.1）
           case STATE:        // ITEM_COLLECT：按当前 held 做完整状态重算
               recalculateCollectQuotaGroup(group)
           case EVENT_BATCH:  // CRAFT / BREAK / KILL：Phase Z 不做历史重算 / 历史回放
               // 注意：这里【不】负责"把 progress 置 0"！
               // progress 何时归零由【各 mutation 自己的 transition】负责，清单如下：
               //   触发器新增 / 重建（改目标 / 改类型 / 改 targetCount）→ 置 0
               //   改派 / 领取 / 取消领取（负责人变更）                     → 置 0
               //   取消完成                                              → 置 0
               //   删除触发器                                            → 不涉及（progress 随 trigger 消失）
               //   单纯排序变化（拖拽）                                    → 不修改现有 progress
           case NONE:         // ADVANCEMENT：不参与 Phase Z 额度处理
               // 保持既有 trigger 语义：既不重算、也不置零、也不进 allocator
   ```

   **三个易错点，必须写死：**

   - **必须按三态判断，不能写 `if (ITEM_COLLECT) ... else ...`**：
     `else` 会把 **`ADVANCEMENT` 误当成事件账**，这既是语义错误，也会诱导后续实现把
     `ADVANCEMENT` 接进 allocator（§5.1 已明确它属于 `NONE`）；
   - **每个组按"它自己的类型"判断**，不能对整个 mutation 做一次单分支判断——
     跨类变更（收集型 ↔ 累加型）时 `beforeGroup.type ≠ afterGroup.type`，
     旧收集组要重算、新累加组不能重算（或反之），必须各自独立判断；
   - **`beforeGroup == afterGroup` 时只能处理一次**：`targetCount` **不在** group key 里，
     所以"改数量"这种修改 `beforeGroup` 与 `afterGroup` 相同；
     若机械执行"两个组各处理一次"，就会对同组重算两次，
     而重算带有自动完成、`removeIndex`、通知推送等副作用，重复执行不可依赖其幂等性。
     统一用 `distinct(...)` 消除；
   - **"无组"不是一种组**：团队任务未指派（`assigneeUuid` 为 `null` / 空串，按既有
     `normalizeAssignee` 口径二者等价）时**不参与分组与判定**，此时 `groupKey` 为 `null`。
     绝不能写成 `(未指派, type, target, null)` 这样的伪 group——那会把**所有未指派任务
     错误地算成同一个额度组**，也会在 `group.type` 上直接 NPE。

   **"无组 ↔ 有组"两个方向的具体处理**（对应"取消领取"与"领取"）：

   | 方向 | 处理 |
   |---|---|
   | 有组 → 无组（取消领取） | 任务退出额度体系：既有 `resetTriggerProgressIfAssigneeChanged` 已清零 `progress` 并置 `completed = false`；再对 `beforeGroup` 做一次组级重算释放额度（`afterGroup` 为 `null`，跳过） |
   | 无组 → 有组（领取给玩家） | 任务进入新组（`progress = 0`、`completed = false`，既有规则）；对 `afterGroup` 做一次组级重算（`beforeGroup` 为 `null`，跳过） |
   | 有组 → 其他有组（改派） | `beforeGroup` 与 `afterGroup` 都非空，两者都处理（去重后） |

   当前可能导致跨组的修改来源（**将来新增分组键维度时也必须走同一流程**）：

   | 来源 | 是否可达 | 说明 |
   |---|---|---|
   | 触发类型变更 | ✅ 可达 | `TriggerEditScreen` 的类型按钮在全部 5 种类型间轮换，含收集型 ↔ 累加型互转 |
   | 目标 identity 变更 | ✅ 可达 | 同一编辑器内改目标 |
   | 负责人变更（改派 / 领取 / 取消领取） | ✅ 可达 | 既有 `resetTriggerProgressIfAssigneeChanged` 已处理进度清零，但**未**重算相关组；取消领取时一侧为"无组" |
   | `targetCount` 变更 | ✅ 可达但**不跨组** | 不在 group key 内 → `beforeGroup == afterGroup` → 只处理一次 |
   | **新增触发器**（无 → 有） | ✅ 可达 | `beforeGroup = null`（`!hasTrigger → null`，§5.1）→ 只处理 `afterGroup`；已完成任务须先走 §4.1 的显式重置 |
   | **删除触发器**（有 → 无） | ✅ 可达 | GUI 右键菜单「清除触发器」→ `applyTriggerFromEditor(task, null)` → `task.setTrigger(null)`；`afterGroup = null` → 只处理 `beforeGroup`（释放额度） |
   | 跨项目 / 跨桶移动 | ❌ 不可达 | 项目内无该操作（`setProjectId` 只用于创建与子任务继承），不列该路径 |

   跨类变更（收集型 ↔ 累加型）时两边语义不同，务必分开处理：
   旧收集组需按当前 `held` 重算以释放额度；新累加组只置 `progress = 0`，不得扫描 / 回放历史事件。

9. **重算事务边界：allocator 内部产生的"自动完成"不得反向再触发一次额度重算**。
   组级重算的调用链必须是单向的：

   ```
   外部触发（用户拖拽 / 编辑触发器 / 手动完成 / 事件批次提交）
       ↓
   recalculateCollectQuotaGroup(group)
       ↓
   TriggerCreditAllocator
       ↓
   完成集合回写：completeNormalized() / removeIndex / markDirty / 通知
       ↓
   本次事务结束 —— 不再 dispatch 新的 quota mutation
   ```

   理由：一轮重算内部已经完成"谁完成 + 谁展示"的完整分配，事务结束时同组状态已自洽
   （第一遍已把完成任务的额度从 `pool` 中扣除，与 `used` 台账一致），**不需要也不必再算一次**。
   若允许自动完成再派发重算，会形成 `recalculate → complete → recalculate` 的重入；
   即使因"第二次看到 A 已完成"而收敛，也属于不该依赖的行为，且会放大通知 / 落库副作用。

   **区分"外部完成"与"事务内完成"**（§5.4 第 3 条表中"完成"一行的准确含义）：

   | 完成来源 | 是否另起一次组重算 |
   |---|---|
   | 重算事务内的自动完成（收集类第一遍、累加型批次分账） | ❌ 不另起，属当前事务 |
   | 手动勾选 / 批量勾选 / 命令完成 | ✅ 需要（外部操作改变了 `used` 台账，同组展示要跟着变） |

10. **批量操作以"一次用户操作 / 一次服务事务"为原子单位，只在最后统一重算一次**。
    典型批量入口：`TaskManager#toggleTaskCompletion` 的**父任务批量勾选**（一次改多个子任务）、
    命令批量完成。禁止实现成"改一条 → 重算一次"的循环：

    ```
    ❌ 错误：
        for (task in 批量目标) {
            completeNormalized()
            recalculateCollectQuotaGroup(groupOf(task))   // 循环内重算 → 重复重算 / 重复通知
        }

    ✅ 正确：
        for (task in 批量目标) { completeNormalized() }   // 先完成全部状态修改
        affectedGroups = distinct(所有被改动任务涉及的组)          // 再汇总受影响组
        for (group in affectedGroups) { processGroup(group) }     // 每组只处理一次
        // 事务结束：统一落库 / 推送
    ```

    理由：循环内重算会带来三类问题——
    ① 同组被重复重算，重复触发自动完成 / `removeIndex` / 通知；
    ② 中间态被 `markDirty` 或推送出去（违反第 6 条的提交时机约束）；
    ③ 前一轮重算可能让后续目标已自动完成，而批量循环仍按旧清单继续处理，行为难以推理。

    本条的实质与第 9 条同源：**重算只在"外部事务提交点"发生一次**，
    区别只是第 9 条约束"事务内不反向派发"、本条约束"一个事务内不重复派发"。

11. **加载 / 重启后，收集类必须在"首次获得有效 `held` 快照"时完成一次组级重算**。
    第 7 条的加载规范化只解决 I4（`completed ↔ progress == targetCount`），
    **不替代额度重算**：旧存档里未完成任务的 `progress` 是 Phase Z 之前的旧语义写的
    （例如 `A 已完成 ×32`、`B 未完成 8/16`，而现在只有 16 个铁锭），
    若不重算，`B` 会一直显示 `8/16`，直到玩家背包再次变化才被纠正。

    ```
    加载阶段：
        Task.fromNbt（含第 7 条 I4 双向规范化）
            ↓
        构建 bucket 与列表顺序视图（§5.2）
            ↓
        收集类组：在该组负责人「已获得有效 held 快照」时执行一次 recalculateCollectQuotaGroup
            ↓
        稳定状态后才向 HUD / GUI 同步、才落库
    ```

    **不新增持久化、不引入加载期全量扫描**，直接复用现有 `handleInventoryChanged` 链路：
    该入口本来就要求"玩家在线 + 能取到背包快照"，因此天然满足下面两条边界——

    | 情况 | 行为 |
    |---|---|
    | 负责人在线、已能取到 `held` | 首次快照到达时立即重算该玩家的收集类组 |
    | 负责人离线（团队任务改派后负责人未上线） | **不重算、不伪造 `held`**；等其上线、首次获得 `held` 快照时再重算 |

    **"首次"必须去重，且重算前不得推送旧语义**：

    - 以 `(玩家 UUID, groupKey)` 为键记录"本会话是否已做过首次重算"，**每个组只做一次**；
    - 之后的 `handleInventoryChanged` 走正常重算路径，不再触发"首次"分支（不额外扫全桶）；
    - 首次重算**完成之前**，不得向 HUD / GUI 推送该组的旧语义 `progress`
      （否则玩家会先看到过期值再跳变，与第 6 条的提交时机约束矛盾）；
    - 该标记只存在于内存，**不新增持久化**：重启后重新走一次"首次重算"即可，
      结果是幂等的（同一 `held` 重算得到同一状态）。

    **仅有"不推送"还不够，必须有一个显式的 hydration 状态**：
    GUI / HUD 读的是任务对象本身（尤其局域网主机模式下读的是同一份内存），
    "不推送"拦不住它们直接渲染旧 `progress`。因此引入**非持久化**的组级状态：

    ```
    quotaHydration: READY | NOT_READY        // 每个 quota group 一个，仅内存

    NOT_READY：该组的收集类进度尚未按当前 held 重算过
        → GUI / HUD **不显示**旧 progress（不显示进度数字，或按 §5.5 的占位文案处理）
        → 也不得据此判定"未达标/已达标"
    READY：已完成首次重算，正常展示
    ```

    - 初始为 `NOT_READY`；首次重算完成后转 `READY`；
    - 累加型（`EVENT_BATCH`）不参与 hydration（它没有"需要重算的历史状态"），恒为 `READY`；
    - 触发条件同样受"负责人离线不重算"约束：离线时该组保持 `NOT_READY`，
      直到该负责人上线并拿到 `held`；
    - 具体渲染方式（隐藏数字 vs 占位符）与 §5.5 的文案一并定稿。

    累加型在任何情况下都**不**因加载而重算、不回放历史事件（§3.4）。

### 5.5 显示与文案（待定 UI 决策）

收集类任务的右侧进度会从"持有量/需求"变成"**可分配额度/需求**"（§3.6 例子里 `铁锭 ×32` 会显示 `0/32`）。
判定正确，但玩家可能疑惑"我明明有 16 个"。

两个候选处理：

- **A**：进度旁加后缀（如 `0/32（同物品已占用 16）`）或 tooltip；
- **B**：不改文案，靠文档说明"同物品任务按列表顺序分配额度，可拖拽调整优先级"。

实现时二选一，倾向前者。**该决策需在 Z2 前定稿**（不要拖到 Z4），因为文案会影响
HUD 与 GUI 的布局与本地化键定义。

若采用 A，建议 tooltip 文案覆盖三件事：

> 同物品任务按列表顺序分配额度；已完成任务会占用额度；拖拽可调整分账顺序。

另外要覆盖一个高频困惑场景：**手动完成一条收集任务后 `used` 变大**，
可能让同组其它任务从 `8/16` 掉到 `0/16` —— 这属于正确行为（§3.3 的 `used` 台账），
但需要靠 tooltip / 文档说明，否则玩家会以为进度被"吞了"。

## 6. 测试计划

离线（扩展 `TaskTriggerServiceTestMain`，纯逻辑入口现成）：

1. 两条同物品收集：持有只够列表靠后那条 → 只完成它，前一条显示 0/N；
2. 持有够全部 → 两条都完成；
3. 持有回落到不足 → 已完成不回退；
4. **拖拽对调顺序后立即重算**：已完成保持完成、未完成任务的完成结果按新顺序变化，且**无需新的 MC 事件**；
5. 删除 / 取消完成一条（收集类）→ 额度释放，另一条可完成；
6. 累加型同批创建：一批只够一条 → 只完成一条；再来一批 → 第二条完成；一次大批量（8）→ 两条都完成；
7. **累加型错位创建**：A 创建→4 事件→完成；再创建 B → 断言 B 初始为 `0/4`；再 4 事件 → **B 必须完成**（防历史额度误扣）；
8. **累加型部分重叠创建**：A 创建→2 事件；B 创建→2 事件；再 2 事件 → 验证分配符合 §3.4 定义；
9. **已完成任务被 `removeIndex` 后仍计入 `used`**（防实现层最常见 bug）；
10. 已完成任务的**排序改变** → 绝不回退；
11. 已完成任务修改 `targetCount` → 走显式重置（取消完成后按新目标判定）；
12. 取消完成后累加型 `progress` 归零，不会被下一条事件立刻重新完成；
13. 破坏 / 击杀类同目标两条：事件量只够一条 → 只完成一条；
14. 多层父子任务（父→子→孙）的 OrderKey 路径序；
15. 单条任务行为与既有用例一致（全部旧用例保持绿）；
16. 团队：不同负责人各算各的；未指派不参与；
17. **纯计算确定性**：同一任务状态 + 同一批次输入 → allocator 输出一致。
    注意这里**不**承诺累加型链路 exactly-once——同一批事件被重复消费是触发链去重责任，不是 allocator 职责；
18. **视图无关性**：开启优先级排序 / 折叠父任务 / 搜索过滤后重算，结果与关闭时一致；
19. **删除已完成累加型任务**：A 完成 `4/4`、B `0/4`，删除 A → **B 仍为 `0/4`**（防误实现"释放历史事件"）；
20. **累加型改目标 identity**：`木板 2/4` → 改为 `石砖 ×4` → `progress = 0`；
21. **累加型只改 `targetCount`（未完成）**：`木板 2/4` → 改为 `木板 ×8` → `progress = 0`（现状为触发器整体重建，防回归）；
22. **手动完成累加型**：`2/4` → 手动勾选 → `completed = true` 且 `progress = 4/4`；下一批同目标事件不再分给它；
23. **收集类 `used > held`（I4 第 1 条）**：已完成 A `32/32`、当前 `held = 16` → 所有未完成任务 `progress ≥ 0`，**不得出现负数**（`pool` 夹到 0）；
24. **收集类本轮新完成（I4 第 3 条）**：未完成 `0/16` + `held = 16` → `completed = true` **且** `progress = 16/16`；
25. **累加型删除"部分进度"任务**：A `2/4`、B `0/4`，删除 A → **B 仍为 `0/4`**（防误实现"退还已消费的部分进度"）；
26. **超界 `progress` 输入规范化**：构造 `progress = 8 / targetCount = 4` 的任务 → `fromNbt` 后为 `4/4`（既有 `clampProgress` 保证，防回归，不需要新增兼容代码）；
27. **已完成任务编辑触发器（决策 3 落地）**：已完成 + 改 `targetCount`（或改目标）→ `completed = false`、`progress = 0`、该组重算，且 `used` 台账随之下调；
28. **加载态不满足 I4 第 3 条（老数据兜底）**：构造 `completed = true` / `progress = 2` / `targetCount = 4` → `Task.fromNbt` 后为 `completed = true` / `progress = 4/4`；收集类同样构造 `completed = true` / `progress = 0` / `targetCount = 16` → 规范化为 `16/16`；
29. **手动完成收集类（I4 第 3 条）**：`held = 0`、任务 `0/16` → 手动勾选完成 → `completed = true` **且** `progress = 16/16`，且该任务的额度过账（`used` 计入 16）；
30. **收集类跨组改目标（旧组 + 新组）**：固定 `铁锭 held = 16`、`金锭 held = 16`；铁锭组 A `16/16`（已完成）、B `0/16`；把 A 的目标改成金锭 ×16 → ①铁锭组 B 立即 `16/16` 完成（旧组释放）；②金锭组 A 立即 `16/16` 完成（新组参与 + 已完成被重置）；
31. **跨类变更：收集型 → 累加型**：A = `ITEM_COLLECT 铁锭 ×16` 且已完成 → 改成 `CRAFT_ITEM 铁锭 ×4` → `completed = false`、`progress = 0`；旧收集组释放额度（同组后续任务可能完成）；**不回放历史合成事件**（此后不发生合成事件则 A 一直 `0/4`）；
32. **跨类变更：累加型 → 收集型**：A = `CRAFT_ITEM 铁锭 ×4` 且 `progress = 2/4` → 改成 `ITEM_COLLECT 铁锭 ×16` → `progress = 0`，再按当前 `held` 重新参与收集组判定；
33. **同组内修改 `targetCount`（`beforeGroup == afterGroup`）**：`ITEM_COLLECT 铁锭 ×16` → `×32` → 该组**只被处理一次**（用 `distinct(beforeGroup, afterGroup)` 保证），不出现重复自动完成 / 重复通知；
34. **两批事件不共享 `pool`（把测试 6 的断言显式化）**：A、B 同组各 `0/4`；提交 `amount=4` 后 A `4/4`、B `0/4`，**再**提交 `amount=4` → B `4/4`；断言第二批不会被第一批"预支"或丢失。
    MC 事件天然在服务端主线程串行（`TaskTriggerService` 类注释已声明事件入口统一调度到主线程），
    因此本项是"两批连续事件"而非多线程竞态测试；真正的并发保护由 §5.4 第 6 条的串行假设保证；
35. **"无组"不是一种组**：团队任务取消领取（有组 → 无组）→ `afterGroup` 为 `null` 被跳过、`beforeGroup` 正常重算、不抛 NPE；再领取给另一玩家（无组 → 有组）→ 只处理 `afterGroup`；断言**未指派任务之间不会互相占用额度**；
36. **批量操作只重算一次**：一次批量完成同组 A/B/C → 该组只执行**一次** `recompute`（用调用计数断言），不出现重复自动完成 / 重复通知；
37. **反向完成态规范化**：构造 `hasTrigger && !completed && progress == targetCount` 的持久化状态 → `Task.fromNbt` 后 `completed = true`（I4 第 3 条反向）；
38. **`ADVANCEMENT` 属于 `NONE`，不得落进事件账分支**：①`ITEM_COLLECT 铁锭 ×16`（已完成）改成 `ADVANCEMENT` → 旧收集组额度正确释放，`ADVANCEMENT` 侧 Phase Z **既不重算也不额外置零**、不进 allocator（触发器重建导致的 `progress = 0` 属 `TaskTrigger` 构造器的既有行为）；②`ADVANCEMENT` 改成 `ITEM_COLLECT 铁锭 ×16`（`held = 16`）→ 新收集组按 `held` 正常判定；
39. **`AllocationResult.progressByTaskId` 覆盖全部候选**：A `4/4`、B `0/4`、C `0/4` 场景下，断言输出 map 的 key 集合 **恰好等于** 输入的 `orderedIncompleteTaskIds`（含本轮 `take = 0` 的任务）；
40. **allocator 纯函数性**：执行 `allocate(snapshot)` 前后，`snapshot` 内部结构（候选顺序、`targetCount`、`progress`、`used` / `held` / `amount`）**完全不变**，且不持有 / 不修改任何 `Task` 实例；
41. **自动完成不重入重算**：A、B 同组 `ITEM_COLLECT`、`held` 足够让两者在同一轮全部完成 → 调用一次 `recalculateCollectQuotaGroup(group)` → 断言 `recompute` 调用次数为 **1**、A/B 各完成一次、每个任务只产生一次完成通知；
42. **新建收集任务立即进入当前额度**：A 已完成 `16/16`、B `0/16`、`held = 16`，此时新建 C `收集铁锭 ×16` → 最终分配严格按新的列表顺序生效（**不等下一次捡铁**），且断言不产生额外写入；
43. **OrderKey 子层 tie-break 用 `id` 而非 `created_at`**：同父下 `child A(subtask_sort_order=1, id=10)`、`child B(subtask_sort_order=1, id=20)`，且 **A 的 `created_at` 晚于 B** → 必须仍是 `A < B`（锁住 §3.2 最容易回归的细节）；
44. **加载后首次 collect 重算（Phase Z 与旧存档的交界面）**：构造旧数据 `A 已完成 32/32`、`B 未完成 8/16`、当前 `held = 16` → 加载 + 首次 `held` 快照后断言 `A` 仍 `32/32` 已完成、`B` 变为 `0/16` 未完成，且**不发生任何累加型历史回放**；
45. **触发器新增 / 删除的跨组处理**：①已完成且**原本无触发器**的任务新增 `ITEM_COLLECT 铁锭 ×16` → `completed = false`、`progress = 0`、`afterGroup` 按 `held` 判定；②已完成任务删除触发器 → 旧收集组释放额度、`completed` **保持**（不被重置）；
46. **第一遍完成的任务不被第二遍覆盖**：`A ×16`、`held = 16` → 第一遍使 A `16/16` 完成并进 `completedIds`；断言第二遍**不把 A 重新写回**（防实现复用第一遍之前的候选快照）；
47. **第二遍展示排他**：A `×32`、B `×32`、`held = 16`、`used = 0` → 断言 `A = 16/32`、`B = 0/32`（同一份额度只填给列表靠前的一条，不重复展示）；
48. **离线负责人取消完成收集类**：团队收集任务已完成、负责人离线（无 `held` 快照）时被主机取消完成 → 断言落库状态为 `completed = false` + `progress = 0`（**不得**出现 `!completed && progress == targetCount`），且重载后 `Task.fromNbt` **不会**把它改回 `completed = true`；
49. **OrderKey 顶层 tie-break**：`sort_order` 相同 → 比 `created_at`；两者都相同 → 比 `id`；逐级断言顺序；
50. **个人任务与团队任务的 groupKey 隔离**：同一玩家个人的 `收集铁锭 ×16` 与团队（已指派给自己）的同物品任务**不共享额度**；个人任务的 `owner` 恒为玩家 UUID、不为 `null`；
51. **跨项目 / 跨桶隔离**：同物品、同负责人、同类型，但属于不同项目 → 不共享额度；
52. **`ADVANCEMENT` 残留回归**：多条 `ADVANCEMENT` 任务追踪同一进度 → 仍**一起完成**（确认未被误接入 allocator，属已知残留而非 bug）；
53. **新增 / 删除触发器时累加型 `progress` 路径**：①新建 `CRAFT_ITEM` 触发器 → `progress = 0`（构造器既有行为），Phase Z 不额外置零、不回放历史；②删除累加型触发器 → 任务退出额度体系，后续同目标合成事件不再分账给它；
54. **H2 加载顺序 vs 内存 OrderKey 排序一致**：构造子层 `subtask_sort_order` 相同、而 `created_at` 与 `id` 顺序**相反**的数据，分别经 H2 查询与内存 OrderKey 排序 → 断言判定采用的列表顺序恒为内存 OrderKey 的结果（`(subtask_sort_order, id)`），不随 DB collation 变化；
55. **删除"未完成且已有部分展示进度"的收集任务**：A `16/16`（已完成）、B `8/16`（未完成、展示中），删除 B → 断言 A 的额度不受影响、重算后 A 仍 `16/16` 完成；反向再删除 A → B 获得释放额度并按新 `pool` 重新展示（防"删除未完成任务时误改他人额度"）；
56. **hydration 未就绪前不显示旧进度**：构造旧存档数据使某收集组初始为 `NOT_READY` → 在首次 `held` 快照到达前，断言 GUI / HUD **不渲染**该组旧 `progress`（也不据此判定达标）；快照到达并重算后转 `READY`，展示变为重算结果。

实机清单（追加到 `feat-trigger-completion.md` §8）：

1. 项目里建两条同物品收集任务，捡够较小需求量 → 只有一条完成；补齐差额 → 第二条完成；拖拽对调后重复一次；
2. 另建两条同物品合成任务，分两批各合成一半，确认不是同时完成；
3. 累加型删除 / 取消完成：合成任务 A 完成后删除 A → 后面的同物品任务**不应被追溯补完成**；
4. 手动完成累加型：`2/4` 时勾选完成 → 进度应显示 `4/4`，后续同目标合成不再使它变化；
5. 触发器编辑清零：`木板 2/4` → 改成 `木板 ×8` 或改成 `石砖` → 进度应显示 `0/8` / `0/4`；
6. 已完成任务编辑触发器（决策 3）：把一条**已完成**的任务改成更大的数量 → 应弹出确认提示；确认后该任务退回未完成、进度显示 `0/N`，同组后续任务可能因 `used` 下调而提前完成；
7. 手动完成收集类：任务 `0/16` 时手动勾选 → 进度应显示 `16/16`（不是 `0/16` 却打勾）；
8. 跨组改目标：让已完成任务 A 与未完成任务 B 同组（同物品同数量），把 A 的目标改成另一种物品 → B 应立即获得释放的额度（可能直接完成），A 在新物品组重新判定；
9. 跨类改类型：把一条已完成的「收集 铁锭 ×16」改成「合成 铁锭 ×4」→ 应退回未完成且显示 `0/4`；原收集组的同物品任务应立即受益；此后不再合成则不推进。

## 7. 风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| 进度语义变化不易理解 | 同物品任务的进度不再是"背包持有量" | §5.5 文案 + 文档说明 |
| 拖拽会改变完成结果 | 玩家可能没意识到"拖拽 = 优先级" | 文档/提示说明；有意设计 |
| 累加型拖拽只影响后续 | 与收集类不对称，容易被当成 bug | §3.5 明确写入 |
| 排序来源被视图污染 | 误用视图顺序会让显示开关影响判定 | §3.2 + 测试 18 |
| `used` 来源写错 | 从触发器索引取 → 永远算不出 `used` | §5.4 第 1、2 条 + 测试 9 |
| 并发重复占额 | 两个事件线程都以 `used=0` 起算 | §5.4 第 6 条（串行/快照提交） |
| 取消完成行为改变 | 累加型取消后 `progress` 由"保持"改为"归零"（今天会在下一条事件被立刻重新完成） | §5.4 第 5 条 + 测试 12 |
| 手动完成累加型补满进度 | `progress` 由"保持不变"改为"补至 `targetCount`"，与本任务实际事件量不再相等 | §4.2 + 测试 22；已在规格中显式声明 |
| 编辑触发器连带清空 `progress` | 改目标或改 `targetCount` 都会因触发器整体重建而归零，玩家可能困惑 | §4.1 + 测试 20、21 |
| 已完成任务被编辑后退回未完成 | 决策 3 落地后，改数量 / 改目标会把已完成任务重置为未完成并清零进度 | 属显式设计：§4.1 + GUI 确认提示 + 测试 27 |
| 收集类 `pool` 未夹紧 | `used > held` 时算出负 `pool`，第二遍可能倒扣进度 | §3.3 的 `max(0, ...)` + I4 第 1 条 + 测试 23 |
| 收集类完成未写满进度 | 第一遍只登记完成不写 `progress` → 留下"已完成 `0/16`" | §3.3 第一遍显式写 `targetCount` + I4 第 3 条 + 测试 24 |
| 手动完成留下脏进度 | GUI / 批量 / 命令完成都只置 `completed`，不动 `progress`，且已完成任务不再被重算覆盖 | 统一入口补写 + `Task.fromNbt` 加载兜底（§4.2、§5.4 第 7 条）+ 测试 28、29 |
| 跨组变更漏算旧组 | 只重算新组 → 旧组额度不释放，后续任务卡在不够的状态 | §5.4 第 8 条 + 测试 30 |
| 跨组来源只枚举了两条 | 漏掉**触发类型变更**（编辑器可在 5 种类型间轮换，含跨类） | §4.1 + §5.4 第 8 条按 `beforeGroup`/`afterGroup` 统一处理 + 测试 31、32 |
| OrderKey tie 时返回 0 | `sort_order` 相同且比较键不含 `id` → 顺序依赖集合迭代序，重启后可能变 | §3.2 要求每层 tie-break 落在比较键里 |
| 子层顺序依赖 DB 返回序 | H2 的 `ORDER BY` 不含 `subtask_sort_order`，load 出来并非子任务正确顺序 | §3.2 明确必须显式按子层 Segment 排序 |
| 完成态收口点放错包 | 若收口到 `TaskTriggerService.completeTask` → `task` 包与 `trigger` 包循环依赖 | §5.3 收口到 `Task` 层 |
| 自动完成反向触发重算 | `recalculate → complete → recalculate` 重入，放大通知 / 落库副作用 | §5.4 第 9 条（重算事务边界，单向下发）+ 区分事务内 / 外部完成 |
| "无组"被当成一种组 | 未指派任务被写成 `(未指派, type, target, null)` → 所有未指派任务错误地共享一份额度，或在 `group.type` 上 NPE | §5.4 第 8 条：`groupKey` 可空，`distinct(非空(...))` 过滤 + 测试 35 |
| 批量操作循环内重算 | 父任务批量勾选时"改一条算一次" → 同组重复重算、重复通知、中间态外泄 | §5.4 第 10 条（以事务为单位聚合重算）+ 测试 36 |
| 取消完成的中间态被落库 | 若取消完成拆成"先翻转状态位、再补 `progress`"，中途落库/推送会写出 `!completed` + `16/16` | §5.3 收口为原子入口 `revertCompletion()` + §5.4 第 6 条提交时机 + 第 7 条双向规范化 + 测试 37 |
| 同组 mutation 被处理两次 | `targetCount` 不在 group key 内，机械执行"两个组各处理一次"会对同组重算两遍 | §5.4 第 8 条用 `distinct(beforeGroup, afterGroup)` + 测试 33 |
| 跨类变更写成单分支判断 | 写成 `if (类型为 ITEM_COLLECT)` 时无法同时处理 `beforeGroup` 收集、`afterGroup` 累加 | §5.4 第 8 条要求按"每个组自身的类型"判断 + 测试 31、32 |
| `ADVANCEMENT` 落进事件账分支 | 用 `if (ITEM_COLLECT) ... else ...` 会把 `ADVANCEMENT` 当成 `EVENT_BATCH`，语义错误并诱导后续把它接进 allocator | §5.1 三态（`NONE`）+ §5.4 第 8 条 `switch(quotaMode)` + 测试 38 |
| allocator 直接修改 `Task` | §3.3 / §3.4 伪代码若写成 `task.progress = ...`，会被实现成"allocator 持有 Task"，破坏无 MC 依赖与可离线单测 | §3.3 / §3.4 统一为值输入 → 值输出；回写只在 service 层 + 测试 40 |
| `progressByTaskId` 语义含糊 | 若只输出"变化的任务"，调用方需写 `getOrDefault(旧值)`，容易漏回写导致展示停在旧值 | §5.1 写死为"覆盖全部候选" + 测试 39 |
| 全量结果被当成"无条件写库" | 每次背包变化都把整组任务 `UPDATE` 一遍并推送，产生写放大与 HUD 抖动 | §5.1 区分"全量结果（契约）"与"按 diff 回写（service 策略）" |
| 触发器新增/删除漏处理 | 已完成任务新增触发器 → `completed = true` + `progress = 0` 违反 I4；删除触发器 → 旧收集组额度不释放 | §4.1 上位规则 + §5.1 `!hasTrigger → null` + §5.4 第 8 条来源表 + 测试 45 |
| 加载后收集类展示停留在旧语义 | 旧存档 `progress` 由 Phase Z 之前写入，不重算会一直显示过期值 | §5.4 第 11 条（首次 `held` 快照时重算；离线不重算）+ 测试 44 |
| "列表顺序"被误描述为严格优先级 | 文档写"优先级"但算法是"整份满足优先"，容易被当成实现错了 | §3.3 / 决策记录统一为"分账顺序 + 条件优先级" |
| `Snapshot` 传入非法组合 | `mode` 与 `groupKey.type` 不自洽时 allocator 会"正确地算出错误结果" | §5.1 Snapshot 合法性约束（调用方保证，allocator 不纠正） |
| `EVENT_BATCH` 分支被误当成"一律清 0" | 拖拽排序也会被清进度 | §5.4 第 8 条 `case EVENT_BATCH` 注释明确"归零由具体 mutation 语义决定" |
| 收集类取消完成只靠"后续重算" | 负责人离线时无人重算 → 留下 `!completed && progress == targetCount`，重载后被规范化改回 `completed = true`，**取消操作被吞掉** | §4.2 两类都**显式置 `progress = 0`** + 测试 48 |
| 个人任务 `owner` 归一化错误 | 写成 `null` 会把同一玩家（甚至不同玩家）的个人任务错误合并，或直接 NPE | §5.1 `owner(task)` 表：个人任务恒为玩家 UUID + 测试 50 |
| 判定顺序被 DB collation 污染 | 某条路径改用 DB 返回顺序 → 与内存 OrderKey 两套口径 | §3.2 `id` 为小写十六进制 UUID + "全过程只能有一个比较器"；测试 54 兜底 |
| 手动完成收集类后同组进度"掉到 0" | `used` 变大属正确行为，但玩家会以为进度被吞 | §5.5 tooltip / 文档说明 |
| "不推送"拦不住 GUI 直接渲染旧进度 | GUI / HUD 读的是任务对象本身，`NOT_READY` 期间会显示过期值 | §5.4 第 11 条 `quotaHydration` 状态 + 测试 56 |
| GUI 本地保存绕过组处理入口 | 局域网主机下 GUI 直接写本地 H2，若只改字段不重算 → 展示与台账不一致 | §5.4 第 6 条（GUI 路径同样走组处理入口） |
| `owner` 依赖 ambient 玩家 | 用"事件玩家/当前玩家"推导 `owner` → 团队任务被按事件玩家拆组、个人任务归错人 | §5.1 `owner(task)` 来源唯一化（`CachedBucket.playerUuid` / `assigneeUuid`） |
| 子层 tie-break 口径不一致 | DB 子任务查询用 `(subtask_sort_order, created_at, id)`，内存比较器用 `(subtask_sort_order, id)` | §3.2 明确以内存比较器为准（`(subtask_sort_order, id)`），不依赖 DB 顺序 |
| 重算粒度写成 task 而非 group | 只重算被改的任务 → 同组其他任务的额度变化被漏掉 | §5.1 组级入口命名 + §5.4 第 3、8 条 |
| 老存档首次升级的行为跳变 | 已完成的同物品任务会立即占用额度，未完成任务进度可能下降 | 只影响未完成任务的展示，不回退已完成；实机验证一次 |

## 8. 分步计划

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase Z1 | `TriggerCreditAllocator` 纯逻辑（**值输入 → 值输出，不持有 / 不修改 `Task`**；`progressByTaskId` 覆盖全部候选）+ **冻结接口契约（拆分为 `StateQuotaSnapshot` / `EventBatchQuotaSnapshot` 两个输入类型 + `AllocationResult` 输出 + 前后置条件 + 三态 `quota participation`；`owner(task)` 来源唯一化）** + OrderKey Segment 路径序（含每层 tie-break，子层显式排序，不依赖 DB 顺序） + "`used` 来自桶内全量列表"的取数入口 + 离线单测（不接线） | ⬜ 未开始 |
| Phase Z2 | 接入收集类（组级重算入口 `recalculateCollectQuotaGroup`：`pool = max(0, held − used)` 两遍分配，含"完成即写满 `progress`"）+ 额度相关变更立即重算（**`groupKey` 可空、`distinct(非空(beforeGroup), 非空(afterGroup))` 去重后按 `switch(quotaMode)` 三态处理**，覆盖**类型变更（含 `ADVANCEMENT` 的 `NONE` 分支）/ 目标变更 / 指派（改派 / 领取 / 取消领取）/ 触发器新增与删除**；**重算事务内不反向派发、批量操作只在提交点聚合派发一次**；**重算稳定后才落库推送、且按 diff 回写**；**GUI 本地保存路径同样走组处理入口**）+ **触发器编辑重置（上位规则：新增 / 修改且变更后仍有触发器 → 已完成任务显式重置；删除触发器只释放额度、不重置 `completed`，含 GUI 确认提示；累加型部分待 Z3 接线后完整生效）** + **取消完成收口为原子入口 `revertCompletion()`（两类都置 `progress = 0`）** + **加载后首次获得有效 `held` 快照时补一次收集类重算（内存去重、每组仅一次；离线负责人不重算；`quotaHydration` 未就绪前 GUI / HUD 不显示旧进度）** + **`Task` 层 `completeNormalized()` / `revertCompletion()` + `Task.fromNbt` 双向加载规范化** + **§5.5 进度文案定稿** + 离线回归 + 实机验证 | ⬜ 未开始 |
| Phase Z3 | 接入累加型（`advanceMatchingTasksByUuid` 批次分账，覆盖 `CRAFT_ITEM` / `BREAK_BLOCK` / `KILL_ENTITY`；含取消完成 `progress` 归零、手动完成 `progress` 补至 `targetCount`，且**不实现任何历史重算**）+ 离线回归 + 实机验证 | ⬜ 未开始 |
| Phase Z4 | （可选）同物品占用提示文案 + 文档同步 | ⬜ 未开始 |

## 9. 与未来 RPG 化的边界（本阶段不改动）

参考成熟 RPG 任务系统（Quest / Objective / Trigger 分层、Stage、依赖、Optional、Track / Focus、Journal）
做的方向评估结论见 `TODO.md` 的「计划中」。**本阶段只钉住三条边界**，目的是让将来那些功能是"新增维度"
而不是"改语义"——尤其 §3.2 的 OrderKey 直接建立在父子层级上，父子语义一变就会牵动判定顺序。

1. **父子关系 = 展示层级（`parentTaskId` + 子层排序），暂时不代表执行依赖。**
   现在父任务完成是"全部后代完成的聚合"（触发器优先的父任务除外），没有任何 gating。
   未来的**任务阶段 Stage**、"前置任务 / 依赖图"属于**新增维度**，不是改写父子语义。
2. **Trigger 是 objective 的判定器，不是 quest 本身。**
   `ITEM_COLLECT 铁锭 ×16` 描述的是"某一个 objective 怎么判定完成"，不是一个任务；
   文档里的"任务 / 子任务 / 触发器"三层结构保持不变，**不做类名重命名**
   （`Task` / `SubTask` / `TaskTrigger` 已落在 H2 列、NBT 键、网络包与测试里，改名收益低、风险高）。
3. **额度只统计"同一 quota group 内的未完成任务"（§3.1 / §5.1）。**
   将来的 Stage / Optional Objective 若想改变这个统计范围（例如"未解锁的 objective 不占额度"
   或"可选目标不占额度"），**必须另立设计文档**，不允许在本阶段顺手扩大/缩小候选集合——
   那会同时改动 `used` 台账、`pool` 语义与已验证的 I1~I4。

**明确不做的方向**：对话树、多结局剧情、大规模 World State / NPC AI Quest、完整剧情编辑器——
会把项目从"Minecraft 任务管理"变成"RPG Quest Framework"，复杂度不属同一量级。
