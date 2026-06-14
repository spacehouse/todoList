# [CLOSED] neoforge-h2-driver

## 现象
- 在 `NeoForge 1.21.1-21.1.220` 中切换到 `H2` 模式后，日志报错 `H2 driver is not available on the runtime classpath`。
- 后续项目、任务、玩家状态读取全部失败。
- 执行 `h2 status` 时还出现系统聊天编码异常并触发断线。

## 已知证据
- 日志首个关键异常来自 `H2StorageBootstrap.prepareEmbeddedBeforeTcp(...)`。
- 根因栈包含：
  - `java.sql.SQLException: H2 driver is not available on the runtime classpath`
  - `java.lang.ClassNotFoundException: org.h2.Driver`
- 出错平台限定在 `NeoForge`，需要与 `Fabric/Forge` 的依赖打包链路做差异比对。

## 可证伪假设
1. `NeoForge` 发布产物没有把 `H2` 依赖带入运行时类路径。
2. `H2` 依赖只进入了开发或测试环境，没有进入 `NeoForge` 的最终发布配置。
3. `NeoForge` 的模块类加载对依赖可见性有额外要求，导致依赖存在但 `Class.forName("org.h2.Driver")` 不可见。
4. `h2 status` 的聊天断线是独立次生问题，和驱动缺失分属两条链路。

## 分析结论
- 假设 1 成立：`NeoForge` 产物未包含 `H2` 驱动。
- 假设 2 成立：`H2` 仅在 `common` 测试类路径与 `fabric/forge` 打包链路中存在，`neoforge` 未对齐。
- 假设 3 不成立：不是“依赖存在但类加载不可见”，而是最终 jar 中确实缺少 `org/h2/Driver.class`。
- 假设 4 成立：`h2 status` 聊天断线是独立次生问题，原因是将 `storageStatus.getReason()` 枚举对象直接作为翻译参数发送。

## 关键证据
- 日志明确显示 `ClassNotFoundException: org.h2.Driver`。
- 修复前产物检查：
  - `todolist-fabric-1.21.1-1.3.0.jar -> org/h2/Driver.class = 1`
  - `todolist-forge-1.21.1-1.3.0.jar -> org/h2/Driver.class = 1`
  - `todolist-neoforge-1.21.1-1.3.0.jar -> org/h2/Driver.class = 0`
- 修复后产物检查：
  - `todolist-neoforge-1.21.1-1.3.0.jar -> org/h2/Driver.class = 1`

## 修复
- 在 `neoforge/build.gradle.kts` 的 `jar` 任务中补齐 `libs/h2-2.2.220.jar` 解包。
- 在根 `build.gradle.kts` 的 `h2JarContentCheck` 中纳入 `neoforge`，作为后续发布前校验。
- 在 `CommandBootstrap` 中将 `storageStatus.getReason()` 转为 `name()` 字符串，避免 NeoForge 下 `system_chat` 编码失败。

## 验证
- 已通过 `.\build-local-21.bat --offline build`。
- 最新 jar 已复制到 `E:\MC\modtest\versions\1.21.1-NeoForge_21.1.220\mods`。
- 已在 `NeoForge 1.21.1-21.1.220` 上确认修复：切换到 `H2` 模式可正常使用，`/todo h2 status` 不再导致断线。
