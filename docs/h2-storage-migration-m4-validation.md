# H2 存储迁移 M4 验证记录

## 本轮完成项

- 盘点 M4 首个高频全量扫描入口：项目侧栏任务计数。
- 新增 `H2TaskQueryService`，通过参数化 SQL 聚合项目任务数量，GUI 层不直接拼接 SQL。
- 侧栏计数在 `storageBackend=h2` 时优先使用 H2 `GROUP BY project_id` 查询；NBT 后端继续使用原 `TaskManager` 内存路径。
- H2 查询失败时仅侧栏计数回退到当前内存快照，不改变任务保存、reload 或维护锁语义。
- 补充 `H2TaskQueryServiceTestMain`，对照 `TaskManager#getTasksByProject` 验证个人与团队项目计数一致。
- 新增 HUD SQL 查询：按桶、项目来源、指派状态、完成状态和 limit 返回可绘制任务行，同时返回匹配总数用于隐藏计数和折叠摘要。
- HUD 在 H2 后端优先走 SQL 查询；查询失败时回退原内存路径，NBT 后端保持原逻辑。
- 新增 GUI 任务 ID SQL 查询：按桶、项目、完成状态、优先级、指派状态和搜索关键字过滤，搜索覆盖标题、描述和标签。
- GUI 在 H2 后端优先用 SQL 查询任务 ID，再映射回当前 `TaskManager` 内存对象，避免编辑和选择逻辑操作脱离当前快照的对象。
- SQL 搜索对 `%`、`_` 和 `\` 做转义，保持用户输入按普通文本匹配，避免 LIKE 通配符改变原搜索语义。
- GUI 查询服务新增 limit/offset 分页读取和独立 count 能力；已完成任务分组折叠时，H2 后端只统计总数，不加载全部已完成任务。
- 已完成任务分组展开时，或当前选中任务是已完成任务时，仍加载完整已完成任务列表，避免改变滚动、选中项和编辑后刷新语义。
- 补充 GUI 回归测试，验证 H2 后端下已完成分组收起时只展示标题和 SQL 总数，展开后再显示具体已完成任务。
- 评估未完成任务分组后，M4 暂不引入更深层虚拟滚动；未完成区涉及拖拽重排、选中定位和编辑后刷新，继续保留完整加载以保持现有交互语义。

## 已验证命令

```powershell
$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\build-with-java17.bat --offline --no-daemon :common:h2TaskQueryServiceTest
```

结果：通过。

```powershell
$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\build-with-java17.bat --offline --no-daemon :common:guiSystemTest
```

结果：通过，包含 `TodoScreenTestMain.shouldUseH2CompletedCountWhenCompletedSectionCollapsed`。

```powershell
$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\build-with-java17.bat --offline --no-daemon :common:h2TaskQueryServiceTest :common:guiSystemTest
```

结果：通过。

```powershell
$env:JAVA_HOME='D:\JAVA\JDK\jdk-17.0.4'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\build-with-java17.bat --offline --no-daemon build
```

结果：通过。

```powershell
rg -n "<乱码特征集合>" common docs fabric forge build.gradle.kts settings.gradle.kts
```

结果：无命中。

```powershell
Get-ChildItem -Recurse -Filter *.mv.db | Select-Object -ExpandProperty FullName
```

结果：无输出，未发现遗留 H2 数据库文件。

```powershell
Get-Content -Encoding UTF8 build/reports/h2-driver-content-check.txt
```

结果：Fabric 与 Forge 产物中 `org/h2/Driver.class` 均为 1 份。

## 阶段结论

- M4 已完成项目侧栏、HUD、GUI 筛选/搜索和已完成折叠分组的 SQL 查询优化。
- 未完成分组不在 M4 继续分页或虚拟滚动；后续若要优化，需要先设计拖拽重排、选中任务定位、编辑后刷新和删除后定位的虚拟列表语义。
