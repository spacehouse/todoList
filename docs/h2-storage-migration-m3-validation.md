# H2 存储迁移 M3 验证记录

## 本阶段完成项

- 新增 H2 维护锁与非 DAO 层 `H2MaintenanceGuard`，覆盖 DAO 写入、命令写入前检查、GUI 保存触发、网络项目/任务写入和项目保存防抖。
- 新增 `/todo h2 backup [name]`，通过 H2 `BACKUP TO` 生成 zip 备份。
- 新增 `/todo h2 reload-db` 与 `/todo reload-db`，用于重新初始化 H2 存储上下文并刷新内存项目列表。
- 新增 `/todo h2 health`，检查 `SELECT 1`、`storage_meta.schema_version` 和任务数量。
- 新增 `h2BackupOnStart` 配置，默认 `false`；启用后 H2 初始化完成会生成启动备份。
- 新增 schema upgrade 框架：读取版本、升级前备份、事务升级、失败时标记 `SCHEMA_UPGRADE_FAILED`，且不推进 `schema_version`。

## 验证命令

```powershell
$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --offline --no-daemon :common:h2MaintenanceBackupTest :common:h2CommandIntegrationTest
.\gradlew.bat --offline --no-daemon build
```

## 验证结果

- `:common:h2MaintenanceBackupTest :common:h2CommandIntegrationTest` 通过。
- `build` 通过，包含 M1/M2 回归、GUI/命令自测、H2 TCP 组合测试和 H2 驱动打包检查。
- `H2MaintenanceBackupTestMain` 覆盖：
  - 正常备份 zip 生成与内容校验。
  - `h2BackupOnStart` 启动备份。
  - 健康检查读取 schema 与任务数量。
  - 旧 schema 升级前备份并升级到当前版本。
  - schema 升级失败标记不可用且不推进版本。
  - 维护期间写入拒绝和并发维护拒绝。
- `CommandBootstrapH2IntegrationTestMain` 覆盖：
  - `h2 status`。
  - `h2 backup`。
  - `h2 reload-db`。
  - 根级 `reload-db`。
  - `h2 health`。
  - `h2 reset-password`。

## 收尾检查

- H2 运维命令失败路径统一保留旧状态并输出失败提示。
- 维护锁在 H2 后端生效；NBT 后端不触发 H2 初始化或写入阻塞。
- schema 升级失败会进入 H2 不可用状态，不回退展示旧 `.dat`。
