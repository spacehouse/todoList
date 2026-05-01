# H2 存储迁移 M1-0 验收记录

## H2 离线依赖

- 版本：H2 `2.2.220`
- 离线放置路径：`libs/h2-2.2.220.jar`
- 本地来源：`.gradle-user/wrapper/dists/gradle-8.8-bin/dl7vupf4psengwqhwktix4v1/gradle-8.8/lib/h2-2.2.220.jar`
- 官方坐标参考：`com.h2database:h2:2.2.220`
- 官方 Maven Central 路径参考：`https://repo1.maven.org/maven2/com/h2database/h2/2.2.220/h2-2.2.220.jar`
- SHA-256：`978AB863018D3F965E38880571C36293EA8B10A8086194159C4D5D20B50F0A57`
- 许可证：H2 官方发布采用 MPL 2.0 / EPL 1.0 双许可证；后续发布前如替换版本，必须重新记录 SHA-256 和许可证来源。

## 构建接入

- `common` 仅将 H2 jar 加入 `testImplementation`，用于 M1-0 诊断测试。
- Fabric 和 Forge release jar 通过 `zipTree(libs/h2-2.2.220.jar)` 合入 H2 类。
- 根构建新增 `h2JarContentCheck`，检查 Fabric/Forge release jar 内 `org/h2/Driver.class` 均存在且数量为 `1`。

## 诊断范围

- `common:h2DiagnosticTest` 加载 `org.h2.Driver`。
- 在系统临时目录创建 H2 文件库，不使用正式 `todo/` 数据目录。
- 执行 `SELECT`、`CREATE INDEX IF NOT EXISTS`、`SCRIPT TO`、`RUNSCRIPT FROM`、`BACKUP TO`。
- 通过反射验证 `org.h2.tools.Server.createTcpServer(String...)` 可访问，为 M2 TCP 方案保留依据。

## 验证结果

- 当前工作区未提供 `build-with-java21.bat`，且直接执行 `.\build-with-java17.bat --offline build` 时受已有 `JAVA_HOME` 影响未命中 JDK 17。
- 已通过验证命令：`$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat --offline build`
- 离线构建结果：通过。
- H2 jar 内容检查报告：`build/reports/h2-driver-content-check.txt`
- H2 jar 内容检查结果：
  - `fabric=todolist-fabric-1.20.1-1.3.0.jar, org/h2/Driver.class=1`
  - `forge=todolist-forge-1.20.1-1.3.0.jar, org/h2/Driver.class=1`
- 临时数据库诊断输出：构建日志中的 `H2 M1-0 diagnostic passed`
- 正式路径保护：仓库工作区内未发现 `.mv.db` 文件。
