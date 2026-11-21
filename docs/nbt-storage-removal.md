# 设计：清理 NBT 文件存储残余，收敛为 H2 单链路

> 状态：**已实现，待分支同步**（2026-11-21 完成实现与回归，见 §7）
> 类型：技术债清理（refactor，`ModConfig.normalize()` 强制 H2 之后遗留的死分支移除）
> 目标分支：`refactor/1.20.1-nbt-storage-removal`（基于 `1.20.1`）
> 更新日期：2026-11-21

## 0. 背景与边界冲突裁定

TODO 计划区原条目（2026-10-25 需求评审）的范围是「三个存储类的非 H2 分支 + ModConfig 的
NBT 配置注释与 lang key」，并写明**明确不含**「H2 不可用时的应急 NBT 写入
（`shouldKeepNbtWritableWhenH2Unavailable` 固化）」。

实现前核查发现该边界存在事实冲突：

- 生产代码里 H2 不可用时**直接抛 `StorageUnavailableException` 并向玩家报错**，
  从不存在"H2 挂了自动降级写 NBT"的路径；
- 该测试的真实内容是：手动 `setStorageBackend(NBT)` 切到 NBT 后端 → 写 NBT 文件 →
  读回成功，验证"NBT 后端不受 H2 可用性状态影响"——这是双模式并存时代的验收语义；
- 保留该测试就必须保留 `StorageBackend.NBT` 枚举值 + TaskStorage 的 NBT 读写文件路径，
  与"清理非 H2 分支"字面直接冲突。

**维护者裁定（2026-11-21）**：退役该用例，NBT 文件链路全删。数据安全语义由既有机制承接：
H2 不可用时显式报错不静默丢数据（`shouldBlockH2WritesWhenUnavailable` 固化）+
`/todo h2 backup/restore` 恢复链路。保留 NBT 写通道反而可能造成
「写进 NBT 但 H2 才是权威」的双写分裂。

## 1. 现状事实（移除安全性的依据）

- `ModConfig.normalize()` 已把 `storageBackend` 强制为 `H2`（配置层强制，`nbt` 值加载即回退）；
- `StorageBackendFactory.isNbtSelected()` 全仓生产代码零调用；
- 三个存储类（TaskStorage / ProjectStorage / ProjectPlayerStateStorage）内的
  `isH2Selected()` if-else 中，非 H2 分支在生产中永不执行；
- 命令侧 5 处 `requires_h2` 拦截与 `ensureStorageAvailableForCommand` 的非 H2 放行分支同理；
- GUI / HUD 的 `isH2Selected()` else 分支（同步直读、文件时间戳轮询）同理。

## 2. 需求准入三问

1. **用户故事**：维护者只需读懂一条存储链路，消除"配置强制 H2 后永不执行"的死分支。
2. **验证方案**：`:common:check` 全绿且保留用例（迁移链 / Task NBT 序列化 / H2 可用性 /
   H2 集成）仍通过；实机冒烟由 verify-all 承接。
3. **投入上限**：1 个工作日，超限冻结。

## 3. 删除范围

| 项 | 处理 | 位置 |
|---|---|---|
| TaskStorage 的 12 处 NBT 分支 + 纯 NBT 文件 IO（saveTasksToFile / loadTasksFromFile / serializeTaskRoot / readLastSavedSafe / maybeLogLoadSummary / 目录预热）| 删除，方法体直接委托 `H2TaskStore` | `task/TaskStorage.java`（536 → ~200 行） |
| ProjectStorage 的 5 处 NBT 分支 + 文件 IO | 删除，直接委托 `H2ProjectStore`；`hasPersonalProjectsFile` 保留（H2 分支活跃） | `project/ProjectStorage.java` |
| ProjectPlayerStateStorage 的 3 处 NBT 分支 + 文件 IO + NBT key 常量 | 删除，直接委托 `H2ProjectPlayerStateStore`；`ProjectPlayerState` 值对象保留 | `project/ProjectPlayerStateStorage.java` |
| `StorageBackend.NBT` 枚举值 / `setStorageBackend` / nbt 配置注释 / lang nbt key | 删除；`storageBackend` 字段与 getter 保留作旧配置兼容（"nbt" 走非法值容错回退 H2） | `config/ModConfig.java` |
| `StorageBackendFactory` 整类 | 删除（`isNbtSelected` 零调用；`isH2Selected` 调用点全部简化） | `storage/StorageBackendFactory.java` |
| 命令 5 处 `requires_h2` 拦截 + 非 H2 放行分支 + status 后端名动态读取 | 删除；status 后端名硬编码 `h2` | `bootstrap/CommandBootstrap.java` |
| GUI init 同步直读 else 分支、H2 查询 `!isH2Selected` 守卫、HUD 文件时间戳轮询 else 路径 | 删除 | `gui/TodoScreen.java`、`client/TodoHudRenderer.java` |
| `closeStorageContext` 的后端判断、`H2MaintenanceGuard` 非 H2 空转、`ClientTaskStorageHelper` 双写分支 | 简化 | `TodoListCommon.java`、`storage/H2MaintenanceGuard.java`、`client/ClientTaskStorageHelper.java` |
| lang 的 `config.todolist.storage_backend.comment.nbt` 与 5 个 `command.todolist.h2.*.requires_h2` | 删除（en_us / zh_cn 同步） | `assets/todolist/lang/` |
| NBT 后端依赖型测试（详见 §5） | 退役或改造 | `common/src/test` |
| `docs/storage-backend-h2-guide.md` 的 NBT 切换 / TCP 章节 | 重写为 H2 单链路使用说明 | `docs/` |

## 4. 保留物（生命线，不得删除）

- **NBT→H2 老档迁移链**：`H2LegacyMigrationReader` / `H2LegacyMigrationPreflight` /
  `H2LegacyMigrator` / `Legacy*` DTO（首次 H2 访问惰性触发，`storage_meta` 迁移标记，
  starred 写入用 `MERGE ... KEY` 天然去重）；
- **Task/Project NBT 序列化格式**：`Task.toNbt/fromNbt`、`TaskTrigger.toNbt/fromNbt`、
  `Project.toNbt/fromNbt`——网络包与 H2 行映射（`H2TaskStore` ResultSet → CompoundTag →
  `Task.fromNbt`）共享的内存契约，与磁盘文件 IO 零耦合；
- **H2 可用性机制**：`H2StorageAvailability` / `StorageUnavailableException` /
  `StorageFailureNotifier` / `SafePersistenceHelper`（配置写盘 + 迁移读取器只读共用）/
  `DataPathProvider`；
- **H2 维护能力**：备份 / 恢复 / 健康检查 / 维护锁 / schema 升级；
- fabric/forge 的 `performMigration`（默认项目改名 + 孤儿任务清理，与 NBT→H2 迁移无关）。

## 5. 测试侧处置

| 测试 | 处置 | 说明 |
|---|---|---|
| `H2StorageAvailabilityTestMain.shouldKeepNbtWritableWhenH2Unavailable` | **退役** | §0 裁定；其余 6 用例保留 |
| `PersistenceSafetyTestMain` 的 7 个 NBT 文件备份恢复用例 | **退役** | `.dat` 备份恢复机制随 NBT 文件链路消失；保留 2 个配置文件用例；数据安全兜底语义由 H2 可用性 + 备份/恢复承接 |
| `TaskCompatibilityTestMain.shouldFlattenLegacySubtasksWhenLoadingTaskStorage` | **退役** | "TaskStorage 读旧文件展平"依赖 NBT 读路径；展平语义由迁移链测试（`shouldFlattenSingleLevelSubtasksDuringMigration`）承接；其余 3 个纯内存兼容用例保留 |
| `H2LegacyMigrationTestMain` / `H2StorageBackendIntegrationTestMain` 的造旧数据夹具 | **改造** | 原经 NBT 后端写 `.dat`；改为新增测试专用 `LegacyNbtFixtureWriter` 直写旧格式文件（字段键与迁移读取器约定一致），迁移链生产代码零改动 |
| `GuiTestSupport.resetState` 默认 NBT 档 | **改造为 H2** | 全部 GUI 测试换 H2 嵌入式链路重验；`resetStateKeepDefaultBackend` 合并删除 |
| 命令测试 10 处 `.dat` 文件存在性断言 | **改造** | 改为 H2 等价语义（`hasPlayerTasks` / `hasPlayerState` / `hasPersonalProjectsFile` / 桶非空） |

## 6. GUI 测试 H2 化的三项基建修复

默认档切换后暴露的测试基建问题（仅测试代码改动）：

1. **用例命名空间隔离**：H2 同 URL 连接共享同一 MVStore 实例，已删除数据库文件的旧句柄
   仍可读出旧数据（Windows `FILE_SHARE_DELETE` 语义），`invalidateReusableConnections`
   只能失效当前线程的 ThreadLocal 缓存、无法跨线程物理关闭——resetState 改为**按用例分配
   独立存储 namespace**（新 URL = 新库，天然隔离，迟到加载任务亦被 namespace 守卫拦下）。
2. **异步加载等待**：TodoScreen 的 H2 个人任务加载是 CompletableFuture 异步（GUI_STORAGE_LOADER
   单线程池），initScreen 统一等待 `personalTasksLoading` 翻回 false 后再继续断言。
3. **屏幕打开语义对齐**：真实 MC 是 `setScreen` 先设置 `minecraft.screen` 再调 init；
   测试 initScreen 同步为先设置 screen 字段再 init，保证 init 期间提交的异步加载回调在
   "用户未关屏"守卫下正确应用，并排空 loader / 保存执行器队列后再重置静态状态。

## 7. 回归记录（2026-11-21）

- `:common:check --offline` 全绿（23 个 JavaExec 测试任务 + GUI 19 组 + 命令 6 组）；
- 顺带修复：`H2ProjectPlayerStateStore.replaceStarredProjects` 写入侧增加
  trim + 保序去重规范化（与 `player_hud_starred_projects` 唯一索引对齐；重复 starred
  旧脏数据在 NBT 模式可写、H2 唯一索引拒绝，写入规范化让 join sanitize 用例在 H2 下
  语义不变）；迁移链 starred 写入本就走 `MERGE ... KEY`，无冲突；
- 乱码抽查（鍒/闂/锟/�）：lang 与全部改动文件干净；
- verify-all 实机冒烟：见分支同步前的执行记录（若未执行则如实标注）。
