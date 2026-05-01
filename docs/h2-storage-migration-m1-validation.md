# H2 存储迁移 M1 当前验收记录

## 当前完成范围

- M1-A：新增 `storageBackend=nbt|h2` 配置、规范化逻辑和集中后端选择工厂。
- M1-A：Fabric/Forge 初始化顺序调整为先加载配置，再初始化通用存储组件；`TodoListCommon.init()` 也会兜底读取配置。
- M1-B：新增 H2 短连接提供器，数据库基础路径由 `DataPathProvider.getTodoDataDir()` 派生。
- M1-B：新增 schema v1 初始化器，创建任务、标签、项目、成员、玩家项目状态、HUD 星标、桶元数据和 `storage_meta` 表。
- M1-B：schema 初始化可重复执行，已兼容 H2 2.2.220 中 `"key"` 和 `"value"` 关键字需要加引号的语法差异。
- M1-C：新增只读旧 NBT migration reader、字段预检、单事务迁移器和迁移完成元数据。
- M1-D：新增 H2 任务、项目、玩家项目状态存储服务，并在 `TaskStorage`、`ProjectStorage`、`ProjectPlayerStateStorage` 中按 `storageBackend=h2` 分流。
- M1-D：H2 后端首次访问会初始化 schema；未完成旧 NBT 迁移时自动执行一次只读迁移，之后公开 Storage 门面直接读写 H2。
- M1-E：新增 H2 存储可用状态表和 `StorageUnavailableException`，H2 初始化、迁移、读写、查询失败后会按当前数据库路径记录不可用原因。
- M1-E：H2 被标记不可用后会阻断后续 H2 读写；切回 NBT 时不受 H2 不可用状态影响。
- M1-E：GUI、任务网络同步、项目玩家状态网络入口和部分任务命令失败会区分普通保存失败与存储不可用；项目防抖保存失败时会恢复 dirty 标记，避免内存改动被误判为已落盘。
- M1-E：新增 H2 启动缓存和不可用状态的 namespace 级重建入口，状态按当前 H2 数据库路径隔离。
- M1-E：服务端停止生命周期会在平台通用事件中清理当前 H2 存储上下文状态，避免失败状态串到下一次启动。
- M1-E：任务 done/remove/claim/abandon/assign/add-to-project 等命令入口遇到存储不可用时会返回统一命令提示。
- M1-E：Fabric 客户端断开远程连接、Forge 客户端登出和 namespace 切换前会关闭当前 H2 存储上下文，避免远程库状态滞留到本地库。
- M1-E：项目 create/rename/select/star/member/remove 等命令在修改内存状态前会检查当前 H2 可用性；已不可用时直接返回统一存储不可用提示。

## 当前测试覆盖

- `storageBackendSelectionTest`：
  - 缺失配置默认 `nbt`。
  - 显式 `h2` 可被读取并由工厂返回。
  - 非法值回退 `nbt` 并写回配置。
- `h2SchemaInitializerTest`：
  - 临时 game dir 下创建 H2 文件库。
  - schema v1 初始化可重复执行。
  - 全部 M1-B 表存在。
  - `storage_meta` 写入 `schema_version=1`。
- `h2LegacyMigrationTest`：
  - 迁移读取备份时不会恢复或覆盖主文件。
  - 非空 `subtasks` 会阻断迁移。
  - 旧 NBT 任务、标签、项目、成员、玩家项目状态和 HUD 星标可单事务导入 H2。
  - 迁移完成后写入 `dat_migration_completed=true` 和 `migration_source_format=nbt-v1`。
- `h2StorageBackendIntegrationTest`：
  - `TaskStorage` 在 H2 后端下会首次迁移旧本地任务，并可保存/读取本地、玩家和团队任务桶。
  - H2 任务桶会写入 `lastSaved`，玩家任务桶保存后 `hasPlayerTasks` 返回 true。
  - `ProjectStorage` 在 H2 后端下可首次迁移旧个人项目，并可保存/读取个人与团队项目。
  - `ProjectPlayerStateStorage` 在 H2 后端下可首次迁移旧玩家状态，并可保存/读取当前项目、HUD 星标和 HUD 可见性。
- `h2StorageAvailabilityTest`：
  - H2 数据库被标记不可用后，H2 门面写入会抛出 `StorageUnavailableException`。
  - H2 不可用状态不会影响 `storageBackend=nbt` 的 NBT 写入和读取。
  - GUI 和上层调用可从异常链中识别 `StorageUnavailableException`。
  - 不同 namespace 的 H2 可用状态互相隔离，指定数据库状态可重建。
  - 存储不可用异常会映射为 GUI/命令专用提示 key。
  - 服务端停止生命周期会清理当前 H2 不可用状态。

## 验证结果

- 验证命令：`$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat --offline build`
- 构建结果：通过。
- 已执行：命令系统测试、GUI 系统测试、H2 M1-0 诊断、M1-A 后端选择测试、M1-B schema 初始化测试、M1-C 旧 NBT 迁移测试、M1-D H2 Storage 门面集成测试、M1-E H2 存储可用状态测试、Fabric/Forge H2 Driver 内容检查。
- 正式路径保护：H2 相关测试只使用临时 game dir；旧 `.dat` 迁移读取使用只读路径，不会恢复、覆盖或删除旧主文件。

## 尚未完成

- M1-E 后续增强：为项目命令“改内存前可用性门禁”补充更细的命令级集成断言。
- M1-E 后续增强：平台客户端退出钩子的端到端生命周期测试。
