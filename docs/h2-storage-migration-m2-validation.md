# H2 存储迁移 M2 验证记录

## 当前完成范围

- 新增 `config/todolist-h2.json`，TCP 默认关闭，缺失配置会生成安全默认值。
- 配置损坏时保留为 `todolist-h2.json.corrupted`，并重建安全默认配置。
- 新增 H2 TCP Server 生命周期管理，支持启用后启动、停止、重启和状态快照。
- TCP 首次启用且数据库文件不存在时，先用嵌入式连接预创建 schema 和旧数据迁移，再启动 TCP，避免 H2 TCP 远程创建库限制。
- TCP 启动成功后，内部 DAO 连接 URL 切换为 `jdbc:h2:tcp://...`，TCP 未启用或失败时保持嵌入式路径。
- schema 初始化会创建 `admin`、`readonly`、`readwrite` 三类账号，并授予基础权限。
- 新增 `/todo h2 status`、`/todo h2 restart-tcp`、`/todo h2 reset-password <role>` 管理命令入口，状态输出不包含密码或完整 JDBC URL。
- `reset-password` 会同步更新 H2 数据库用户密码和配置文件，避免配置与数据库凭据分叉。

## 已验证项

- 缺失 H2 TCP 配置时自动生成默认配置。
- 损坏 H2 TCP 配置会被保留并重建。
- Windows 临时目录、空格路径和中文路径下，TCP URL 可连接同一数据库。
- TCP 启用成功后，内部连接 URL 使用 TCP 模式。
- `readonly` 可执行 `SELECT`，拒绝 DML。
- `readwrite` 可执行 DML，拒绝 DDL。
- 默认端口被占用时会自动递增到下一个可用端口。
- 端口尝试耗尽时保持嵌入式回退状态，不清空数据。
- 远程访问开启但配置弱密码时，会禁用 TCP 并输出 WARN。
- `/todo h2 status` 和 `/todo h2 reset-password <role>` 已纳入命令集成测试。

## 待继续项

- M3 阶段继续实现备份、维护锁和 `reload-db`，并覆盖 TCP 模式组合。
- M4 阶段继续推进 GUI/HUD SQL 查询优化。

## 验证命令

```powershell
$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat --offline :common:compileJava :common:compileTestJava :common:h2TcpAccessTest
$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat --offline :common:commandSystemTest :common:h2CommandIntegrationTest
```
