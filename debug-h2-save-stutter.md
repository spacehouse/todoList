# [CLOSED] h2-save-stutter

## 背景
- 现象：H2 模式下，待办列表新增或修改后点击保存，界面仍有明显卡顿。
- 目标：确认卡顿主要发生在主线程保存前快照、主线程保存后 UI 刷新，还是 H2/网络链路回主线程后的某一步。

## 当前假设
1. `persistDirtyTasksInBackground(...)` 在切后台前仍会在主线程做大对象复制，任务量上来后导致明显停顿。
2. 保存完成后 `finishBackgroundTaskPersistence(...)` 回到主线程执行 `sendReplaceAllTasks` / `sendMergeTeamTasks` / HUD 同步时产生卡顿。
3. 保存后立即触发的 `filterTasks()` / `buildTaskPaneSections()` / 详情有效性检查在大列表下重算过重，造成 UI 停顿。
4. H2 模式下某个本应在后台线程的步骤仍间接阻塞了渲染线程，例如关闭/恢复/同步请求路径。
5. 实际卡顿不在本地 GUI，而在客户端发包后等待服务端团队同步回包的交互阶段。

## 计划
1. 只添加日志埋点，不修改业务逻辑。
2. 记录保存开始、主线程快照耗时、后台保存耗时、主线程收尾耗时、关闭耗时。
3. 在 H2 模式下复现“新增/修改后保存卡顿”，对比各阶段耗时。
4. 根据证据决定最小修复点。

## 证据记录
- 已确认 `A` 不是主因：多次 `manual_save` / `complete` / `uncomplete` 的保存前快照耗时均为 `0-1ms`。
- 已确认 `C` 不是主因：保存完成回主线程收尾耗时均为 `1-2ms`。
- 已确认 `E` 不是主因：团队任务同步回包应用耗时为 `0ms`。
- 已确认 `B` 是主因：后台 H2 保存存在稳定的 `245-509ms` 耗时。
- 典型证据：
  - 团队手动保存：`manual_save-12069254137000`，`teamCount=33`，`B.elapsedMs=245`，同时 `A=0ms`、`C=1ms`
  - 团队手动保存：`manual_save-12073541046500`，`teamCount=34`，`B.elapsedMs=253`，同时 `A=0ms`、`C=1ms`
  - 个人手动保存：`manual_save-11965384728800`，`personalCount=30`，`B.elapsedMs=509`，同时 `A=1ms`、`C=2ms`
  - 个人手动保存：`manual_save-12049791517400`，`personalCount=33`，`B.elapsedMs=498`，同时 `A=0ms`、`C=1ms`
  - 个人手动保存：`manual_save-12054099193300`，`personalCount=34`，`B.elapsedMs=495`，同时 `A=0ms`、`C=1ms`
- 进一步代码审查显示：本地集成服务端下的个人保存路径会同时执行 `savePlayerTasks(...)` 和 `saveTasks(...)`，在 H2 模式下等于顺序保存两个任务桶；这与“个人项目比团队项目更卡”的体感一致。
- 修复后用户反馈：保存卡顿已明显改善，但在“已发布游戏 -> 团队项目保存 -> 重新打开待办 -> 切到个人项目”时仍有明显卡顿；反向“个人 -> 团队”不明显。
- 第二阶段待验证：
  1. 切到个人项目时命中了后台 H2 个人任务读取，读取本身偏慢。
  2. 个人任务读取完成后，回主线程的 `replacePersonalTasks + updateProjectList + filterTasks` 才是卡点。
  3. `switchProject(... -> PERSONAL)` 内部的 `rebuildUI()` 在该路径下明显更重。
  4. 团队保存后导致个人缓存未命中或异步加载尚未完成，因此“先团队后个人”更容易踩到这条路径。
- 第二阶段证据：
  - 已发布游戏场景下，`F` 个人任务后台读取稳定在 `239-259ms`，例如：
    - `personal-load-13269547718700`：`publishedLocal=true`，`taskCount=36`，`F.elapsedMs=257`
    - `personal-load-13276647615400`：`publishedLocal=true`，`taskCount=37`，`F.elapsedMs=239`
    - `personal-load-13283898564600`：`publishedLocal=true`，`taskCount=37`，`F.elapsedMs=259`
  - 同批次里 `G` 回主线程应用始终为 `0ms`，`H` 切项目本身为 `5-21ms`，说明剩余卡顿主因在 H2 个人任务读取，而不在 UI 切换或加载结果应用。
- 第二轮读取优化后再验证：
  - `F` 仍有 `225-296ms`，例如：
    - `personal-load-13609532039300`：`publishedLocal=true`，`taskCount=33`，`F.elapsedMs=225`
    - `personal-load-13623881711300`：`publishedLocal=true`，`taskCount=34`，`F.elapsedMs=253`
    - `personal-load-13634681422000`：`publishedLocal=true`，`taskCount=34`，`F.elapsedMs=296`
  - 真实复现里 `H` 也抬升到 `71-97ms`，且主要发生在切到个人项目时：
    - `switch-project-13625176999300`：切到 `PERSONAL`，`H.elapsedMs=84`
    - `switch-project-13627602654900`：切到 `PERSONAL`，`H.elapsedMs=97`
    - `switch-project-13635416009900`：切到 `PERSONAL`，`H.elapsedMs=71`
  - 当前判断：剩余体感卡顿由“切到个人项目时的 H2 个人任务读取”与“切项目时的整页重建”共同造成，其中 `G` 仍不是主因。
- 第三轮细分埋点后确认：
  - `ClientTaskStorageHelper` 已区分读取路径：
    - 未发布单人：`local-standalone`
    - 已发布个人：`published-player`
  - 已发布个人项目读取中，`H2TaskStore.loadBucket(...)` 的查询前半段很轻：
    - `personal-load-15065714140600`：`preCloseMs=13`，但 `totalLoadMs=226`
    - `personal-load-15071512579900`：`preCloseMs=14`，但 `totalLoadMs=237`
    - `personal-load-15096311812300`：`preCloseMs=12`，但 `totalLoadMs=253`
  - 同批次 `closeGapMs` 达到 `213-241ms`，说明主要耗时不是 SQL 查询或 `Task.fromNbt(...)`，而是“最后一个嵌入式 H2 连接关闭时触发的关库收尾”。
  - 针对该证据，已在 `H2ConnectionProvider` 的常规嵌入式 JDBC URL 上启用 `DB_CLOSE_DELAY=-1`，让游戏内短连接关闭时不立刻关库；同时保留 TCP 首启前的嵌入式预创建连接为原短生命周期，避免影响现有 TCP 大小写兼容测试。
- 第四轮复现后进一步确认：
  - 已发布游戏慢样本里，`switchProject(PERSONAL)` 只有 `51ms`，不是 `0.5s` 主因：
    - `switch-project-19979473988300`：`elapsedMs=51`
  - 同批次真正偏重的是重新打开待办后自动触发的个人任务后台读取：
    - `personal-load-19978411350200`：`publishedLocal=true`，`totalLoadMs=254`，其中 `closeGapMs=242`
  - 为定位为何上一轮连接复用没有生效，补充了 `H2ConnectionProvider.openReusableConnection` 埋点；结果显示：
    - 未发布/嵌入式样本能命中该埋点
    - 已发布游戏慢样本完全没有出现该埋点
  - 结合 `H2ConnectionProvider.openConnection()` 代码确认：当 `H2TcpServerManager.getStatus().isTcpActive()` 为 `true` 时，读取会先走 TCP 分支，直接绕过此前只对嵌入式连接生效的复用逻辑。
  - 结论：`已发布游戏 -> 团队保存 -> 再开待办 -> 点个人` 的剩余卡顿，本质上是“已发布场景下 GUI 后台个人读取走 TCP 短连接，每次读取都重复付出连接关闭/收尾成本”，而不是项目切换 UI 本身。
  - 已针对该根因修复：将 GUI 后台读取线程的连接复用从“仅嵌入式”扩展到“TCP 与嵌入式统一复用”，仍只限 `TodoList GUI Storage Loader` 线程，不改变业务语义。

## 结论与规则
- 根因结论：
  - 第一阶段卡顿主因是 H2 后台保存；个人保存在本地集成服务端下会写两个桶，因此比团队保存更重。
  - 剩余卡顿主因不是 `switchProject(PERSONAL)` 主线程切换，而是“重新打开待办后自动触发的个人任务后台读取”。
  - 该读取在未发布/嵌入式与已发布/TCP 激活两条路径上的瓶颈并不完全相同；如果只优化嵌入式路径，已发布游戏仍会留下明显卡顿。
- 本次沉淀规则：
  1. H2 GUI 性能问题必须分开采样 `local-standalone`、`published-player/TCP active` 两条路径，不能混在一起判断。
  2. 凡是改 `H2ConnectionProvider.openConnection()`、连接复用、生命周期、缓存或 `DB_CLOSE_DELAY`，都必须检查 `TCP active` 分支是否绕过优化。
  3. 涉及“切回个人项目卡顿”的问题，验证链路必须至少覆盖：`团队项目修改并保存 -> 关闭待办 -> 再打开待办 -> 点击个人`。
  4. 判断体感卡顿时，先用埋点把 `switchProject`、后台个人读取、主线程应用三段拆开；不要凭主观感觉直接改 UI 切换代码。
  5. 调试结束后，把结论同步到项目规则，避免后续只在单人/嵌入式环境复现后误判“问题已修复”。
