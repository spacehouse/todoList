# H2 关系型存储迁移计划

## Summary

将当前 NBT `.dat` 整文件读写迁移为 H2 关系型存储，先解决数据增长后“每次写入/修改都重写整个文件”的性能和可靠性问题，再逐步开放外部 SQL 访问和 GUI/HUD 按需查询。

迁移按里程碑推进：

- **M1-0：依赖与打包 spike**，验证 H2 jar 在 common、Fabric、Forge 最终产物中可运行加载。
- **M1：嵌入式 H2 存储基础**，实现 schema v1、DAO、旧 `.dat` 只读迁移、Storage 门面兼容和不可用降级。
- **M2：TCP 外部访问**，增加 H2 TCP Server、三级账号和运维状态命令。
- **M3：维护能力**，增加备份、维护锁、`reload-db`、schema 升级前备份。
- **M4：查询优化**，将 GUI/HUD 的高频全量扫描逐步改为 SQL 查询。

## 阶段执行详细计划

- 通用门禁和分阶段执行清单已拆分为独立文档，后续实现时以对应阶段文档为执行入口。
- [M1-0 详细计划：依赖、打包与运行时验证](h2-storage-migration-m1-0-plan.md)
- [M1 详细计划：嵌入式 H2 存储基础](h2-storage-migration-m1-plan.md)
- [M2 详细计划：TCP 外部访问](h2-storage-migration-m2-plan.md)
- [M3 详细计划：备份、维护锁与 reload-db](h2-storage-migration-m3-plan.md)
- [M4 详细计划：SQL 查询优化](h2-storage-migration-m4-plan.md)

各阶段文档必须和本总计划保持一致；如果实现中发现需要跨阶段调整，先更新本总计划和对应阶段文档，再继续实施。

## 阶段内审查循环

- 每个阶段实施前，先按对应阶段文档的“执行前检查清单”和“禁止事项”审查一次。
- 每完成一个子任务或子阶段，先对照“验收标准”和“最小测试矩阵”审查，再继续下一个子任务。
- 发现计划缺口时，先改进总计划和对应阶段文档，再继续实现。
- 重复“审查 -> 改进 -> 审查 -> 改进”，直到没有新的计划改进建议，再将该阶段标记为可实施或可进入下一阶段。
- 若审查发现已实现内容偏离阶段边界，优先回到计划边界，不能用后续阶段功能掩盖当前阶段问题。

## 当前仓库约束

- Gradle 脚本为 Kotlin DSL：根 `build.gradle.kts`、`common/build.gradle.kts`、`fabric/build.gradle.kts`、`forge/build.gradle.kts`；依赖和打包 spike 必须以这些脚本为准，不按 Groovy `build.gradle` 设计。
- 当前 Fabric/Forge jar 通过 `tasks.jar { from(commonMainOutput) }` 合并 common 输出；新增 H2 依赖时必须额外验证第三方 jar 是否进入最终可运行产物，不能只验证 common 编译通过。
- 旧任务文件位置：
  - 本地个人任务：`todo/<namespace>/moddata.dat`
  - 玩家个人任务：`todo/<namespace>/players/<uuid>.dat`
  - 团队任务：`todo/<namespace>/team_tasks.dat`
- 旧项目文件位置：
  - 个人项目：`todo/<namespace>/projects/projects.dat`
  - 团队项目：`todo/<namespace>/projects/team_projects.dat`
  - 玩家项目状态：`todo/<namespace>/projects/players/<uuid>.dat`
- 当前 `ProjectStorage.loadProjectsFromFile` 在发现缺失/非法项目 ID 或 scope 时会回写规范化文件；H2 迁移读取旧项目数据时必须使用新增只读 reader，避免迁移过程意外修改旧 `.dat`。
- 当前 `Task` 类包含 `subtasks` NBT 读写能力，即使 GUI/命令尚未完整使用，也不能在迁移中静默丢弃旧文件里已经存在的非空子任务数据。
- 当前 `Task` 没有 `updatedAt` 字段，H2 `updated_at` 是存储层元数据；迁移旧数据时使用桶级 `lastSaved`，缺失时回退 `createdAt`，后续写入时由 DAO 统一刷新。

## M1-0：依赖、打包与运行时验证

- 固定 H2 版本，将 `h2-<version>.jar` 放入项目 `libs/` 或等价离线可用依赖路径，并记录来源与许可证：MPL 2.0 / EPL 1.0。
- 选型优先使用支持 Java 17 且仍维护的 H2 2.x 版本；记录下载来源、SHA-256、许可证文本路径和离线缓存路径。
- 明确最终打包策略：Fabric/Forge 运行产物必须包含 H2，且只包含一份可加载的 `org/h2/Driver.class`；若采用 `libs/` 本地 jar，需要分别在 `common` 编译、`fabric` 运行打包、`forge` 运行打包中声明。
- spike 必须包含一个 jar 内容检查任务或脚本，检查 Fabric/Forge release jar 中 H2 类是否存在、重复数量是否为 1，并将结果写入测试输出。
- 不改变现有 Storage 行为，不创建正式 `todolist.mv.db`，不写 `storage_meta`。
- 测试库只能位于测试 namespace 或临时目录，测试结束仅清理该测试目录下的 `.mv.db` / `.trace.db`。
- 验证 common test classpath、Fabric remap 后最终产物、Forge remap 后最终产物运行时均可加载 `org.h2.Driver`。
- `Class.forName("org.h2.Driver")` 仅放在测试/诊断路径，不进入正式 mod 初始化流程。
- 确认目标 H2 版本是否支持 `CREATE INDEX IF NOT EXISTS` 等幂等 DDL；若不支持，改用 `INFORMATION_SCHEMA` 查询后创建。
- 确认 H2 的 `RUNSCRIPT`、`SCRIPT`、`BACKUP TO`、TCP Server API 在目标版本的权限和语法，避免 M2/M3 设计依赖已变更 API。
- 不破坏现有 Fabric/Forge 将 common 输出合并进最终 jar 的逻辑，检查 H2 未漏打、未重复打包。

## M1：嵌入式 H2 存储

- 在现有配置中新增 `storageBackend=nbt|h2`，后端选择必须早于 Storage 门面实例化；非法值回退 `nbt`，记录 WARN，并通过安全配置写入机制规范回 `nbt`。
- `storageBackend=nbt` 时继续使用现有 NBT 和 `SafePersistenceHelper`，不得触发 H2 类加载或连接初始化。
- 现有 `TaskStorage`、`ProjectStorage`、`ProjectPlayerStateStorage` 保持公开门面，内部委托 `Nbt*StorageBackend` 或 `H2*StorageBackend`。
- `ProjectPlayerStateStorage` 存在直接 `new` 的路径，构造时也必须延迟选择后端，不能绕过配置或无条件初始化 H2。
- 新增 `H2ConnectionProvider`，M1 使用短连接和 try-with-resources，不引入连接池。
- H2 JDBC URL 使用：`jdbc:h2:file:<path>;AUTO_SERVER=FALSE;DATABASE_TO_UPPER=FALSE;TRACE_LEVEL_FILE=0`。
- `<path>` 从 `Path.toAbsolutePath()` 派生，必须支持 Windows 盘符、反斜杠、空格、中文路径。
- 默认数据库路径为 `todo/<namespace>/todolist.mv.db`，M1 沿用现有 `DataPathProvider.storageNamespace`。
- H2 文件路径只由 `DataPathProvider.getTodoDataDir()` 派生；不得把 namespace 再写入表字段，避免同一个库里混入多个 namespace 的数据。
- 建议新增 `StorageBackendFactory` 或等价集中工厂，由 `TodoListCommon.init()` 在 `ModConfig.load()` 后创建后端；所有 Storage 门面和 direct new 场景都通过该工厂延迟解析当前后端。
- 客户端退出、服务器停止、namespace 切换时关闭 H2 资源，避免 `.mv.db` 锁残留。
- namespace 切换时必须先关闭旧 namespace 的 H2 连接，再切换 `DataPathProvider.storageNamespace` 并重新创建/刷新后端；若关闭失败，记录 WARN 并阻止对新 namespace 写入，直到状态恢复明确。
- H2 初始化和旧数据自动迁移必须发生在业务加载任务/项目之前。
- 每个 `TaskManager` / `ProjectManager` 启动只从一个后端加载一次；H2 失败后只读 fallback 到旧 `.dat` 时不得重复加载。
- 所有列表级保存方法在单事务内完成：按 ID upsert 当前集合，并删除同一桶边界内缺失记录；事务失败则全部回滚。
- DAO 层同时提供单条 upsert/delete，为后续增量写入预留。
- 自动迁移只读取旧 `.dat`，不得触发旧项目文件规范化回写；项目迁移必须新增只读 NBT reader，不能复用会回写的 `ProjectStorage.loadProjectsFromFile`。
- 自动迁移读取旧 `.dat` 时仍通过 `SafePersistenceHelper.readWithRecovery` 复用 `.bak` / `.corrupt` 恢复语义，但恢复结果仅进入 H2 事务，不回写旧文件。
- 迁移前执行数据预检：字段长度、枚举值、重复 ID、项目成员 UUID、任务子任务数量；预检失败不得写入任何 H2 业务表。
- 若发现任意旧任务存在非空 `subtasks`，M1 默认中止 H2 迁移并提示“旧数据包含暂不支持迁移的子任务”；除非同一版本同时补充 `task_subtasks` schema 和 DAO round-trip 测试，不允许静默丢弃。
- 自动导入整体单事务，任一桶失败则回滚，不写迁移完成标记。
- H2 不可用时，只有在 H2 尚未完成迁移、H2 中不存在迁移后数据，或用户显式选择旧 `.dat` 恢复路径时，才可使用旧 `.dat` 只读 fallback 载入内存；该 fallback 不得修改 `dat_migration_completed`，不得把 fallback 数据写回 H2，除非之后 H2 恢复并进入正式迁移流程。
- 如果 H2 已完成迁移或已检测到 H2-only 新数据，H2 不可用时默认进入存储不可用状态，不自动展示旧 `.dat`，避免玩家误以为数据丢失或被回滚。
- H2 不可用时，所有保存通过 `StorageUnavailableException extends IOException` 明确失败；GUI、命令、网络同步入口都必须向用户反馈“当前存储不可用，修改无法保存”。
- `StorageUnavailableException` 应携带原因枚举，例如 `H2_INIT_FAILED`、`FILE_LOCKED`、`FIELD_TOO_LONG`、`MAINTENANCE_LOCKED`，便于 GUI/命令给出准确提示。
- 所有写入口必须先获取维护锁和存储可写许可，再修改内存对象；不得先改内存再尝试保存。
- 如果保存过程中发生异常，调用方必须回滚本次内存变更，或立即通过数据库/旧内存快照恢复，避免玩家看到未持久化的脏状态。
- 批量操作应在内存快照、数据库事务和 UI 刷新之间形成明确边界：事务提交成功后才发布新的内存状态和 GUI/HUD 刷新事件。
- `Task` 从 H2 加载时，如果 schema v1 未实现子任务表，子任务集合保持默认空状态，不得返回 `null`。
- 只有在迁移预检确认旧数据没有非空子任务时，H2 加载任务才允许使用默认空子任务；否则必须阻断迁移或实现子任务持久化。

## Schema v1

- `tasks` 保存任务主体字段：
  - `bucket_type VARCHAR(32) NOT NULL`
  - `owner_uuid VARCHAR(64) NOT NULL`
  - `id VARCHAR(64) NOT NULL`
  - `scope VARCHAR(16) NOT NULL`
  - `project_id VARCHAR(64) NULL`
  - `title VARCHAR(512) NOT NULL`
  - `description VARCHAR(16384) NULL`
  - `completed BOOLEAN NOT NULL`
  - `priority VARCHAR(16) NOT NULL`
  - `created_at BIGINT NOT NULL`
  - `due_date BIGINT NULL`
  - `creator_uuid VARCHAR(64) NULL`
  - `assignee_uuid VARCHAR(64) NULL`
  - `assignee_name VARCHAR(256) NULL`
  - `sort_order BIGINT NOT NULL`
  - `updated_at BIGINT NOT NULL`
  - 主键：`(bucket_type, owner_uuid, id)`；team 的 `owner_uuid` 固定为 `'TEAM'`。
  - `bucket_type` 取值：`LOCAL_PERSONAL`、`PLAYER_PERSONAL`、`TEAM`；本地个人任务的 `owner_uuid` 固定为 `'LOCAL'`。
  - `sort_order` 来自旧 NBT 列表顺序；列表级保存时按当前列表顺序重写该桶内 `sort_order`。
  - `updated_at` 是 DAO 元数据，不回填到 `Task` 对象；迁移时优先使用旧任务桶 `lastSaved`，缺失时使用任务 `createdAt`。
- `task_tags` 保存标签：
  - `bucket_type VARCHAR(32) NOT NULL`
  - `owner_uuid VARCHAR(64) NOT NULL`
  - `task_id VARCHAR(64) NOT NULL`
  - `tag VARCHAR(128) NOT NULL`
  - `sort_order BIGINT NOT NULL`
  - 唯一约束：`(bucket_type, owner_uuid, task_id, tag)`。
  - 加载顺序：`ORDER BY sort_order, tag`，保持当前 `LinkedHashSet` 顺序。
- `projects` 保存项目主体字段：
  - `bucket_type VARCHAR(32) NOT NULL`
  - `id VARCHAR(64) NOT NULL`
  - `name VARCHAR(512) NOT NULL`
  - `color INT NOT NULL`
  - `scope VARCHAR(16) NOT NULL`
  - `owner_uuid VARCHAR(64) NULL`
  - `created_at BIGINT NOT NULL`
  - `allow_member_create BOOLEAN NOT NULL`
  - `allow_all_players_claim_complete BOOLEAN NOT NULL`
  - `sort_order BIGINT NOT NULL`
  - 主键：`(bucket_type, id)`。
  - `bucket_type` 取值：`PERSONAL_PROJECTS`、`TEAM_PROJECTS`。
  - `sort_order` 来自旧 NBT 项目列表顺序。
- `project_members` 保存项目成员与角色：
  - `bucket_type VARCHAR(32) NOT NULL`
  - `project_id VARCHAR(64) NOT NULL`
  - `player_uuid VARCHAR(64) NOT NULL`
  - `role VARCHAR(32) NOT NULL`
  - `member_name VARCHAR(256) NULL`
  - 唯一约束：`(bucket_type, project_id, player_uuid)`。
  - M1 不使用外键，避免旧数据迁移时因脏引用阻断整体迁移。
- `player_project_state` 保存现有玩家项目状态：
  - `player_uuid VARCHAR(64) NOT NULL`
  - `active_project_id VARCHAR(64) NULL`
  - `hud_visible BOOLEAN NOT NULL`
  - `last_saved BIGINT NOT NULL`
  - 主键：`player_uuid`。
  - M1 仅保存现有 `activeProjectId`、`hudVisible` 语义，不扩展新状态模型。
  - `last_saved` 来自旧玩家项目状态 NBT 的 `lastSaved`，缺失时使用迁移时间。
- `player_hud_starred_projects` 保存星标项目：
  - `player_uuid VARCHAR(64) NOT NULL`
  - `project_id VARCHAR(64) NOT NULL`
  - `project_bucket_type VARCHAR(32) NOT NULL`
  - `sort_order BIGINT NOT NULL`
  - 唯一约束：`(player_uuid, project_bucket_type, project_id)`。
  - 加载顺序：`ORDER BY sort_order, project_bucket_type, project_id`。
- `storage_bucket_meta` 保存任务桶 `last_saved`：
  - `bucket_type VARCHAR(32) NOT NULL`
  - `owner_uuid VARCHAR(64) NOT NULL`
  - `last_saved BIGINT NOT NULL`
  - 主键：`(bucket_type, owner_uuid)`。
- `storage_meta` 保存迁移和 schema 元数据：
  - `key VARCHAR(128) NOT NULL`
  - `value VARCHAR(4096) NOT NULL`
  - `updated_at BIGINT NOT NULL`
  - 主键：`key`。
  - 至少记录 `schema_version`、`dat_migration_completed`、`dat_migration_completed_at`、`dat_migration_summary`、`migration_source_format='nbt-v1'`。
- 必要索引：
  - `tasks(bucket_type, owner_uuid, sort_order, created_at, id)`，用于桶内稳定加载。
  - `tasks(project_id, completed)`，用于项目计数和完成状态过滤。
  - `tasks(scope, completed, priority)`，用于 GUI/HUD 常用过滤。
  - `task_tags(bucket_type, owner_uuid, task_id, sort_order)`，用于任务标签加载。
  - `projects(bucket_type, sort_order, created_at, id)`，用于项目列表稳定加载。
  - `project_members(bucket_type, project_id, player_uuid)`，用于项目权限查询。
  - `player_hud_starred_projects(player_uuid, sort_order)`，用于 HUD 星标加载。
- M1 不设计、不迁移、不持久化子任务表；未来真正实现子任务时再通过 schema v2 增加。
- M1 不持久化子任务表的前提是迁移预检确认旧数据无非空子任务；一旦发现旧数据存在子任务，必须改为阻断迁移或把子任务表提前纳入 schema v1。
- 关键边界字段禁止 NULL；可选业务字段缺失统一存 NULL，加载时还原当前对象语义。
- 描述和 meta value 使用足够大的 `VARCHAR`，暂不使用 CLOB。
- DAO 写入前统一校验字段长度；标题、描述、标签、成员名等用户输入字段超限时抛出明确保存异常并反馈用户。
- `storage_meta` 摘要超长时由系统截断或压缩到允许长度内，不得让 meta 摘要过长导致本来成功的迁移失败。
- `updated_at`、`last_saved`、`storage_meta.updated_at` 均由 Java `System.currentTimeMillis()` 生成。
- `bucket_type` 是防串桶和防误删边界；`scope` 只作为业务字段和查询字段。
- 加载任务/项目统一 `ORDER BY sort_order, created_at, id`。
- 保存标签时整组替换该任务在当前桶内的标签。
- 旧 HUD 星标项目迁移时，根据个人项目桶、团队项目桶和当前 `activeProjectId` 推断 `project_bucket_type`；无法判断时默认个人项目桶并记录日志。
- 旧 `ProjectPlayerStateStorage` 只保存星标项目 ID，未保存项目桶；推断时先匹配当前命名空间内个人/团队项目 ID，若两边都有同 ID，优先使用 `activeProjectId` 所在桶，否则记录歧义并默认个人项目桶。

## 旧数据迁移与回退

- 仅 `storageBackend=h2` 时自动导入旧 `.dat`。
- H2 未标记 `dat_migration_completed=true` 时尝试导入；已完成则不再自动导入。
- 迁移状态必须分阶段记录：`dat_migration_started_at` 可在事务外记录诊断信息，但 `dat_migration_completed=true` 只能和业务数据同事务提交；启动后未完成的状态下次必须重新完整迁移。
- 没有旧 `.dat` 时也写入迁移完成标记和摘要 `no legacy data found`，避免重复扫描。
- 旧 `.dat`、`.bak`、`.corrupt` 全部保留，不自动删除。
- 紧急回退方式为将 `storageBackend` 改回 `nbt`；此时强制使用旧 NBT，不读写 H2。
- 文档必须明确：切回 NBT 是紧急回退，不是双写模式；NBT 模式期间新增数据不会自动同步回 H2；再切回 H2 时，如 H2 已迁移完成，不重新导入 NBT 期间新增数据。
- 如用户手动删除或移走 H2 db 后重启，才会重新进入旧 `.dat` 导入路径；文档需提醒这会丢失迁移后 H2-only 新数据。
- 如果 H2 已迁移完成且 `storageBackend=h2`，启动时不得自动读取旧 `.dat` 覆盖或合并 H2 数据；只有显式回退到 NBT 或用户执行未来的手动导入命令才允许读取旧文件。

## M2：TCP 外部访问

- 新增 `config/todolist-h2.json`，保存 TCP 开关、绑定地址、端口、自动递增端口、远程访问开关、数据库路径覆盖、账号凭据。
- 默认监听 `127.0.0.1:9092`；端口冲突时按配置自动递增，最终端口通过日志和 `/todolist h2 status` 显示。
- TCP 启用且启动成功时，模组自身 DAO 也统一使用 TCP URL 连接，例如 `jdbc:h2:tcp://127.0.0.1:<actualPort>/<dbPath>`，避免内部嵌入式连接和外部 TCP 连接混用。
- TCP URL 的 `<dbPath>` 必须使用绝对路径并经过 H2 TCP file path 规则验证；Windows 盘符、反斜杠、空格、中文路径必须纳入测试。
- 只有 TCP 未启用、TCP 启动失败或明确回退时，模组内部才使用嵌入式 file URL。
- TCP 启动失败时回退嵌入式模式，模组功能不中断；嵌入式模式下外部客户端不可用。
- 如果嵌入式也因文件锁失败，标记存储不可用，不清空内存，不崩溃，写入操作明确失败。
- 提供 `admin`、`readonly`、`readwrite` 三级账号：
  - `admin` 仅模组内部使用，拥有 DDL/DML 权限。
  - `readonly` 仅授予 SELECT。
  - `readwrite` 授予 SELECT、INSERT、UPDATE、DELETE，不授予 CREATE、ALTER、DROP。
- TCP 账号初始化必须可重复执行；重复启动时应能自愈 `readonly` / `readwrite` 权限，避免手动改坏权限后长期不可用。
- 远程访问必须显式开启；密码不写日志。
- 远程访问开启时必须要求非默认强密码，并在状态命令中只显示角色和连接地址，不显示密码或完整 JDBC URL。
- 配置损坏时保留原文件为 `.corrupted`，生成安全默认配置和新随机密码，并以 WARN 提示服主查看配置文件。
- 命令：
  - `/todolist h2 status`：显示后端、可用性、TCP/嵌入式状态、实际端口、最近保存失败信息。
  - `/todolist h2 restart-tcp`：重新尝试 TCP 绑定，端口仍冲突时继续自动递增。
  - `/todolist h2 reset-password <role>`：仅控制台或 OP 可执行，重置后提示外部客户端需更新连接。
- `/todolist h2 status` 必须区分 H2 正常读写、H2 不可用但以内存加旧 `.dat` 只读 fallback 运行、H2 不可用且旧 `.dat` 也不可读的默认空状态。

## M3：备份、维护锁与 reload-db

- 新增全局维护锁，覆盖 schema 升级、backup、`reload-db`。
- 维护锁必须阻止数据库写入和内存对象修改入口，避免 `reload-db` 期间玩家修改被覆盖。
- 后台定时保存、退出保存、网络同步触发保存都必须走同一个维护锁检查，不能绕过 backup/reload/schema upgrade 的写入暂停状态。
- 进入维护状态时关闭或刷新 Todo 相关 GUI；若玩家保存已打开编辑界面，必须得到明确“数据库维护中，保存被拒绝”的提示。
- `/todolist h2 backup [name]` 仅控制台或 OP 可执行。
- backup 执行期间启用维护锁或暂停自动保存，避免 H2 `BACKUP TO` 阻塞时产生一致性问题。
- backup 命令反馈中提示“备份期间数据库写入暂停，预计耗时 X 秒”；实际完成后输出备份路径。
- `backupOnStart` 默认 false；schema version 小于当前代码版本时，升级前自动备份。
- schema 升级失败时不得更新 `schema_version`，不得用半升级库继续写入；本次启动应进入存储不可用状态或旧 `.dat` 只读 fallback。
- 如果 `dat_migration_completed=true` 或检测到 H2 已包含迁移后数据，schema 升级失败时默认进入存储不可用状态，不自动 fallback 到旧 `.dat`，避免展示过期数据造成“数据丢失”的误判。
- 只有首次迁移前、H2 中不存在迁移后数据，或用户显式选择恢复旧 `.dat` 时，schema 升级失败才允许进入旧 `.dat` 只读 fallback。
- `/todolist reload-db` 在后台线程读取数据库并重建缓存，主线程只切状态、提示和完成后刷新 GUI/HUD。
- `reload-db` 必须只从当前配置后端读取；`storageBackend=h2` 时不得顺手重读旧 `.dat`，`storageBackend=nbt` 时不得触发 H2 初始化。
- `reload-db` 完成后释放维护锁；失败时保留旧内存状态并提示错误。
- 健康检查包括 `SELECT 1`、读取 `storage_meta`、`SELECT COUNT(*) FROM tasks`。

## M4：SQL 查询优化

- 项目侧栏计数改为 SQL `GROUP BY project_id`。
- HUD 按范围、视图、星标项目、显示数量进行 SQL 查询，避免全量扫描。
- GUI 搜索、优先级、完成状态、负责人、项目过滤逐步改为 DAO 查询。
- 大列表引入分页或 limit。
- 第一阶段 DAO 方法签名要为 M4 预留，保证后续可直接返回新鲜 `List<Task>` / `List<Project>`，不依赖全量内存缓存。

## Test Plan

- **M1-0 依赖测试**：离线构建通过；common、Fabric、Forge 最终产物运行时均可加载 H2；测试库只创建在临时目录；H2 未漏打、未重复打包。
- **打包内容测试**：检查 Fabric/Forge release jar 内 `org/h2/Driver.class` 存在且只有一份；验证 common-only 编译通过不等同于最终 mod 可运行。
- **后端选择测试**：`storageBackend=nbt` 不加载 H2；非法值回退 NBT；`ProjectPlayerStateStorage` direct new 不绕过配置。
- **schema 测试**：初始化幂等；索引幂等；字段长度超限返回明确错误；`storage_meta` 摘要超长时迁移仍可成功；中文标题、描述、标签、成员名 round-trip。
- **连接模式测试**：TCP 启用且启动成功时，模组 DAO 使用 TCP URL；TCP 未启用或失败回退时才使用嵌入式 file URL。
- **迁移测试**：旧 `.dat` 自动导入单事务；通过 `.bak` 恢复的旧文件可导入但不回写旧文件；无旧数据也标记完成；失败不写完成标记；迁移完成后不重复导入。
- **子任务安全测试**：旧 `.dat` 中存在非空 `subtasks` 时，M1 必须阻断迁移并给出明确提示；若实现子任务表，则必须验证子任务 round-trip 不丢字段和顺序。
- **fallback 测试**：H2 未完成迁移且无 H2-only 数据时，H2 不可用可旧 `.dat` 只读载入；H2 已完成迁移或已有 H2-only 数据时不得自动展示旧 `.dat`；fallback 不改变迁移状态；保存抛出带原因枚举的 `StorageUnavailableException` 并被 GUI/命令正确提示。
- **内存一致性测试**：存储不可用、字段超限、维护锁占用时，写入口不得留下未持久化的内存脏状态。
- **数据语义测试**：跨桶同 ID 不覆盖；标签顺序稳定；星标项目桶推断正确；项目成员角色严格解析；无子任务表时加载出的子任务集合保持默认空状态。
- **时间字段测试**：迁移旧任务时 `updated_at` 使用桶级 `lastSaved` 或 `createdAt` fallback；玩家项目状态 `last_saved` 缺失时使用迁移时间。
- **切换测试**：h2 -> nbt -> h2 不自动同步 NBT 期间新增数据，行为与文档一致。
- **TCP 测试**：端口冲突自动递增；TCP 失败回退嵌入式；嵌入式文件锁失败进入存储不可用；权限账号无法越权 DDL；重复启动可修正 readonly/readwrite 权限。
- **维护测试**：backup/reload/schema upgrade 持有维护锁；定时保存、退出保存、网络同步保存不能绕过维护锁；维护期间 GUI 保存被拒绝；`reload-db` 失败保留旧内存状态。
- **schema 升级失败测试**：升级失败不更新 `schema_version`，不允许半升级库继续写入。
- **过期 fallback 测试**：H2 已完成迁移并产生新数据后，schema 升级失败不得自动展示旧 `.dat` 数据。
- **回归测试**：执行 `:common:commandSystemTest`、`:common:guiSystemTest`，以及项目离线构建命令。

## Assumptions

- 当前子任务 UI/命令未作为正式功能开放，但 `Task` NBT 已支持 `subtasks`；H2 schema v1 不包含子任务持久化的前提是迁移预检确认旧数据中没有非空子任务。
- M1 不引入连接池，不做 GUI/HUD SQL 化，只替换持久化后端并保持现有调用模型。
- 正式发布默认 `storageBackend` 是否设为 `h2` 在发布前最终确认；开发和测试阶段默认按 H2 验证。
- 构建命令遵循项目规则，使用当前分支已验证的 `.\build-with-java21.bat --offline build` 离线构建链路。
