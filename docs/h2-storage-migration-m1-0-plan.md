# H2 存储迁移 M1-0 详细计划：依赖、打包与运行时验证

## 关联总计划

- 总计划：[h2-storage-migration-plan.md](h2-storage-migration-plan.md)
- 本阶段只验证 H2 依赖、离线可用性、最终 jar 打包和诊断加载能力。

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
- 每完成一个实施步骤，按“验收标准”和“最小测试矩阵”复审。
- 发现计划缺口时，先改进总计划和本阶段文档，再继续实施。
- 重复审查和改进，直到没有新的计划改进建议。

## 目标

- 只验证 H2 依赖、离线可用性、最终 jar 打包和诊断加载能力。
- 不引入正式存储后端，不创建正式数据库，不改变现有存储路径和运行行为。

## 阶段输入

- 当前 Kotlin DSL 构建脚本：根 `build.gradle.kts`、`common/build.gradle.kts`、`fabric/build.gradle.kts`、`forge/build.gradle.kts`。
- 当前 Fabric/Forge 将 common 输出合并进最终 jar 的打包方式。
- 已选定或待选定的 H2 2.x 版本、离线 jar 和许可证文本。
- 当前离线构建链路：`.\build-with-java21.bat --offline build`。

## 执行前检查清单

- 确认本阶段只做依赖和打包 spike，不开始实现 H2 正式存储。
- 确认 H2 jar 来源、许可证和 SHA-256 记录位置。
- 确认测试数据库目录是临时目录或测试 namespace。
- 确认当前工作区没有需要保护的用户改动被本阶段构建脚本修改覆盖。

## 阶段产出

- 可离线使用的 H2 jar、来源记录、SHA-256 和许可证记录。
- Kotlin DSL 中仅用于编译/运行诊断的 H2 依赖配置。
- H2 driver 加载诊断测试或诊断入口。
- Fabric/Forge release jar 内容检查任务或脚本。
- M1-0 验收记录：构建结果、jar 内容检查结果、临时数据库 smoke test 结果。
- 当前验收记录文档：[h2-storage-migration-m1-0-validation.md](h2-storage-migration-m1-0-validation.md)

## 实施步骤

1. 选择 H2 版本，记录版本号、下载来源、SHA-256、许可证和离线放置路径。
2. 在 Kotlin DSL 构建脚本中加入本地 H2 依赖，确保 `common` 能编译诊断代码。
3. 分别处理 Fabric 和 Forge 最终 jar 打包，确认 H2 类进入 release jar 且不重复。
4. 新增仅测试/诊断使用的 H2 driver 加载检查，不放入正式 mod 初始化流程。
5. 新增 jar 内容检查任务或脚本，统计 Fabric/Forge jar 内 `org/h2/Driver.class` 数量。
6. 新增临时目录数据库 smoke test，只在测试 namespace 或临时目录创建 `.mv.db`。
7. 验证目标 H2 版本的幂等 DDL、备份、脚本和 TCP API 是否满足后续阶段设计。

## 禁止事项

- 不新增 `storageBackend` 配置。
- 不新增正式 DAO、schema 初始化或迁移逻辑。
- 不在正式启动路径调用 `Class.forName("org.h2.Driver")`。
- 不创建 `todo/<namespace>/todolist.mv.db`。
- 不修改现有 NBT Storage 行为或配置文件格式。

## 停止条件

- H2 依赖无法在离线构建中解析，且没有合规的本地 jar 放置方案。
- Fabric 或 Forge release jar 无法稳定包含 H2，或出现重复 H2 类且无法解释来源。
- 诊断测试需要进入正式 mod 初始化路径才能加载 H2。
- smoke test 会触碰真实 `todo/` 数据目录。

## 验收标准

- `.\build-with-java21.bat --offline build` 通过。
- common 诊断测试能加载 `org.h2.Driver`。
- Fabric/Forge release jar 内均存在且仅存在一份 `org/h2/Driver.class`。
- 临时 H2 smoke test 结束后只清理测试目录，不影响真实 `todo/` 数据。
- 文档记录 H2 版本、许可证、SHA-256 和离线路径。

## 可进入下一阶段条件

- H2 离线构建、最终打包和临时数据库 smoke test 全部通过。
- 确认 H2 2.x 目标版本支持后续阶段需要的 API 或已有替代方案。

## 最小测试矩阵

- common classpath：诊断测试能 `Class.forName("org.h2.Driver")`。
- Fabric release jar：存在且仅存在一份 `org/h2/Driver.class`。
- Forge release jar：存在且仅存在一份 `org/h2/Driver.class`。
- 临时数据库：在临时目录创建、连接、执行 `SELECT 1`、关闭、清理。
- 正式路径保护：测试期间不创建 `todo/<namespace>/todolist.mv.db`。

## 回滚方式

- 移除本阶段新增 H2 依赖配置、诊断测试和 jar 内容检查任务。
- 删除临时测试目录中的 `.mv.db` / `.trace.db`。
- 不需要迁移用户数据，因为本阶段不得写入正式存储。

## 文档同步要求

- 若 H2 版本、离线路径、许可证或最终打包方式发生变化，同步更新总计划的 M1-0 小节。
- 若发现 H2 目标版本不支持后续阶段依赖的 API，同步更新 M2/M3 阶段文档中的替代方案。
