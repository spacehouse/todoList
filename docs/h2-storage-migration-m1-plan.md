# H2 存储迁移 M1 详细计划：嵌入式 H2 存储基础

## 关联总计划

- 总计划：[h2-storage-migration-plan.md](h2-storage-migration-plan.md)
- 本阶段实现 schema v1、DAO、只读旧数据迁移、Storage 门面兼容和不可用降级。

## 通用执行门禁

- 每个阶段开始前先确认上一个阶段的验收项已通过；未通过时不得继续叠加下一阶段功能。
- 每个阶段只实现本阶段明确列出的能力；发现需要跨阶段能力时，先更新总计划和本阶段文档并说明原因，再实施。
- 每个阶段都必须保持 `storageBackend=nbt` 可用，且 NBT 模式不得加载、初始化或写入 H2。
- 每个阶段都必须保持现有命令、GUI、HUD、网络同步行为不因未开启 H2 而变化。
- 每个阶段的 Java 新增类和新增方法都必须按项目规则补充简短说明注释。
- 每个阶段完成后执行对应阶段测试；涉及代码实现阶段还要执行 `.\build-with-java21.bat --offline build`。
- 发现中文乱码、旧 `.dat` 被意外修改、H2-only 数据被旧 NBT 覆盖、或 H2 失败后内存出现未保存脏状态时，立即停止本阶段并先修复问题。

## 阶段内审查循环

- 实施前按“执行前检查清单”和“禁止事项”审查。
- 每完成一个子阶段，按“子阶段验收门禁”“验收标准”和“最小测试矩阵”复审。
- 发现计划缺口时，先改进总计划和本阶段文档，再继续实施。
- 重复审查和改进，直到没有新的计划改进建议。

## 目标

- 在不改变外部调用面的前提下，让 `TaskStorage`、`ProjectStorage`、`ProjectPlayerStateStorage` 能按配置委托 NBT 或 H2 后端。
- 完成 schema v1、DAO、只读旧数据迁移、异常反馈和内存一致性保护。

## 阶段输入

- M1-0 已验证的 H2 版本、离线依赖配置和最终 jar 打包方式。
- 当前 NBT 存储行为、旧 `.dat` 文件布局和 `SafePersistenceHelper` 恢复语义。
- 总计划中的 schema v1、迁移与 fallback 边界。
- 当前命令、GUI、HUD、网络同步写入口清单。

## 执行前检查清单

- 确认 M1-0 的离线构建、最终 jar 检查和 H2 smoke test 已通过。
- 确认旧 `.dat` 文件布局、`.bak` / `.corrupt` 恢复语义和项目只读 reader 约束已理解。
- 确认所有保存入口已经列出，包含 GUI、命令、网络同步、后台保存和退出保存。
- 确认本阶段不实现 TCP、备份、reload-db 或 SQL 查询优化。

## 阶段产出

- `storageBackend=nbt|h2` 配置和规范化逻辑。
- NBT/H2 后端接口、集中工厂和 Storage 门面委托实现。
- H2 连接提供器、schema 初始化器、DAO 和字段校验。
- 只读 NBT migration reader、迁移预检、单事务导入和迁移元数据。
- 存储不可用状态、异常原因枚举和用户可见反馈。
- 覆盖 NBT 不变、H2 迁移、fallback、内存一致性和 namespace 生命周期的测试。
- 当前验收记录文档：[h2-storage-migration-m1-validation.md](h2-storage-migration-m1-validation.md)

## 实施步骤

1. 在 `ModConfig` 中新增 `storageBackend=nbt|h2`，完成读取、规范化、非法值回退和安全保存。
2. 新增后端接口和集中工厂，例如 `StorageBackendFactory`，确保 `TodoListCommon.init()` 在配置加载后再创建 Storage 门面。
3. 将现有 NBT 逻辑迁入或包装为 `Nbt*StorageBackend`，先保持 NBT 行为完全一致。
4. 新增 `H2ConnectionProvider`，按当前 namespace 派生数据库路径，并提供关闭/重建能力。
5. 新增 schema 初始化器，创建 schema v1 表、索引和 `storage_meta`，保证重复执行幂等。
6. 新增 DAO：任务、标签、项目、成员、玩家项目状态、星标项目、桶元数据、存储元数据。
7. 新增字段长度和枚举校验层，所有 DAO 写入前统一校验并抛出可定位原因的异常。
8. 新增只读 NBT migration reader，读取任务、项目、玩家项目状态，复用 `.bak` / `.corrupt` 恢复语义但不回写旧文件。
9. 新增迁移预检，覆盖字段长度、枚举、重复 ID、成员 UUID、非空 `subtasks`。
10. 实现单事务迁移；业务数据、`dat_migration_completed=true`、摘要和完成时间必须同事务提交。
11. 改造 Storage 门面保存方法：列表保存按桶事务 upsert 当前集合并删除缺失记录；单条 upsert/delete 预留给 M4。
12. 改造调用入口：所有写入口先检查维护锁和存储可写许可，再修改内存；保存失败必须回滚内存或恢复快照。
13. 实现 H2 不可用状态和 `StorageUnavailableException` 原因枚举，GUI、命令、网络同步都要有明确提示。
14. 实现 namespace 切换、客户端退出、服务器停止时的 H2 资源关闭和状态重建。
15. 保留紧急回退：`storageBackend=nbt` 时强制使用旧 NBT，不读写 H2。

## 建议拆分顺序

- M1-A：配置、后端接口、工厂、NBT 后端包裹，验证 NBT 行为不变。
- M1-B：H2 连接、schema、DAO 基础和字段校验，先用纯 DAO 测试覆盖 round-trip。
- M1-C：只读迁移 reader、预检、事务迁移和迁移元数据。
- M1-D：Storage 门面接入 H2 后端，完成加载/保存/删除桶边界。
- M1-E：不可用状态、内存一致性、GUI/命令/网络提示和 namespace 生命周期。

## 子阶段验收门禁

- M1-A 完成后，`storageBackend=nbt` 下所有现有测试必须通过，且确认 H2 类未被加载。
- M1-B 完成后，纯 DAO round-trip 覆盖任务、标签、项目、成员、玩家项目状态、星标项目和 meta 表。
- M1-C 完成后，旧 `.dat` 迁移成功、失败、无旧数据、`.bak` 恢复、非空子任务阻断都必须有测试。
- M1-D 完成后，公开 Storage 门面的加载、保存、删除边界与 NBT 行为等价，且 H2 事务失败会回滚。
- M1-E 完成后，所有写入口在不可用、字段超限、维护锁占用时都不会留下内存脏状态。

## 禁止事项

- 不实现 TCP Server、外部账号、backup 命令、`reload-db` 或 SQL 化 GUI/HUD。
- 不在 `storageBackend=nbt` 时加载 H2 类或创建 H2 文件。
- 不自动删除、改名或清理旧 `.dat`、`.bak`、`.corrupt`。
- 不复用会回写的 `ProjectStorage.loadProjectsFromFile` 做迁移读取。
- 不静默丢弃非空子任务；发现非空 `subtasks` 必须阻断迁移，除非本阶段同步实现子任务表。
- 不在 H2 已迁移完成或存在 H2-only 数据后自动展示旧 `.dat` fallback。
- 不改变 M2/M3/M4 才负责的 TCP、备份、reload 或 SQL 查询优化行为。

## 停止条件

- 无法证明 `storageBackend=nbt` 不加载 H2。
- 迁移 reader 会回写或修改旧 `.dat` / `.bak` / `.corrupt`。
- 任一写入口无法做到“保存成功后再发布内存状态”或保存失败后恢复快照。
- 旧数据存在非空子任务，但没有阻断策略或子任务持久化方案。
- H2 已迁移完成后仍会自动展示过期旧 `.dat`。

## 验收标准

- `storageBackend=nbt` 下现有命令、GUI、HUD、网络同步行为与迁移前一致。
- `storageBackend=h2` 首次启动能从旧 `.dat` 单事务迁移任务、项目、玩家项目状态和星标项目。
- 迁移失败不写 `dat_migration_completed=true`，下次启动重新完整迁移。
- 无旧数据时写入完成标记和 `no legacy data found` 摘要。
- H2 保存失败、字段超限、维护锁占用时不会留下未持久化内存脏状态。
- 已迁移完成后切回 NBT 再切回 H2，不自动同步 NBT 期间新增数据。
- namespace 切换不会串库，不残留 H2 文件锁。
- `.\build-with-java21.bat --offline build`、`:common:commandSystemTest`、`:common:guiSystemTest` 通过。

## 可进入下一阶段条件

- M1 所有后端选择、schema、迁移、fallback、内存一致性和回归测试通过。
- H2 嵌入式模式在 Fabric/Forge 双端均能完成启动、读写、退出和再次启动。

## 最小测试矩阵

- 后端选择：`nbt`、`h2`、非法值、缺失配置。
- NBT 保护：`nbt` 模式不加载 H2、不创建 `.mv.db`、旧测试全部通过。
- schema：空库初始化、重复初始化、索引重复创建、meta 写入读取。
- DAO round-trip：任务主体、标签顺序、项目成员、玩家状态、星标桶推断、中文字段。
- 迁移：本地个人任务、玩家个人任务、团队任务、个人项目、团队项目、玩家项目状态。
- 迁移失败：字段超限、非法枚举、重复 ID、非空 `subtasks`、`.dat` 损坏且无可用备份。
- fallback：未完成迁移可只读旧数据，已完成迁移或 H2-only 数据时不得展示旧数据。
- 内存一致性：字段超限、H2 文件锁、事务失败、维护锁占用、保存中异常。
- 生命周期：namespace 切换、客户端退出、服务器停止、再次启动。

## 回滚方式

- 用户级紧急回退：将 `storageBackend` 改回 `nbt`，强制使用旧 NBT，不读写 H2。
- 实现级回滚：撤回 H2 后端接入，但保留旧 `.dat`，不得删除或覆盖 H2 数据文件。
- 数据级恢复：只有用户显式选择时，才允许从旧 `.dat` 恢复；不得自动合并 H2 与 NBT 分叉数据。

## 文档同步要求

- schema v1 表结构、字段长度、索引或 bucket 取值有变化时，同步更新总计划的 Schema v1。
- fallback、迁移标记、子任务策略或回退语义有变化时，同步更新总计划和 M3 文档。
- 新增或删除写入口时，同步更新本阶段测试矩阵和 M3 维护锁覆盖范围。
