# 实机验证自动化方案（L1/L2 已落地，L3 实机冒烟链路定型 v3）

> 状态：**L1 已实现并实跑**（`verify-all.bat`，2026-10-26）；**L2 已以 selftest 形态落地**（12 场景两档 + 无头判定，2026-11）；**L3 实机自动冒烟链路已定型 v3**（2026-11-22，五实例实测全 PASS）
> 对应 TODO.md：进行中（剩余项见 §5 演进方向；各分支最新冒烟状态以 TODO.md「已完成」区分支级记录为准）
> 更新日期：2026-11-28

## 0. 背景与目标

早期实机验证是完全手动的循环：构建 → 手动复制 jar 到各测试版本 → 手动启动 → 手动建任务 →
人眼判断结果 → 手动记录。这套循环是本项目 token 与时间成本的主要来源（材料反推 E3-E10 的
六轮实机反馈即为此付出）。

目标：把「数据与启动」部分全自动化，人工只保留「视觉判断」。**2026-11-22 起该目标已基本达成**：
实机侧为「脚本直启实例 + PostMessage 驱动 + F2 截图 + 数据双通道」，人工只确认截图结论。

**三层分工（2026-10-25 方向评审确定，2026-11-28 按落地实态修订）**：

| 层 | 内容 | 执行方式 | 状态 |
|---|---|---|---|
| L1 启动冒烟 | mod 能否在目标环境正常加载、存储初始化无异常、无启动崩溃 | 全自动、无人值守、全环境顺序执行 | ✅ 已实现 |
| L2 数据断言 | 触发器进度推进、任务完成判定、H2 存取语义正确 | `/todo selftest` 12 场景两档（mod 侧场景引擎）；无头档随 verify-all 判定 | ✅ 已落地（selftest 形态；原 GameTest 路线未走，见 §2） |
| L3 实机冒烟 | GUI 布局、交互手感、渲染效果 | 脚本直启真实实例 + 自动进世界 + 键鼠驱动 + F2 截图；判定 = 人/模型看图 + 数据双通道 | ✅ 链路定型 v3（见 §3） |

L3 的过渡形态 `docs/completed/smoke-checklist-2026-10-25.md` 已完成使命留档
（2026-10-26 全部通过）；逐特性的手工清单现由 test-panel 手测场景注册表承接（见 §4）。

## 1. L1：verify-all.bat（已实现）

位置：仓库根 `verify-all.bat`（按 .gitignore 约定属**本地文件**，与 build-local-17.bat 同类，
不进 git）。**注意：当前检出根若不存在该文件（换机 / 新克隆），面板的 selftest 回归按钮会
报「请先放置」**——需从历史检出或备份恢复一份。

### 1.1 用法

```text
verify-all.bat                  顺序跑全部检测到的环境
verify-all.bat fabric           只跑名字含 "fabric" 的环境（同理 forge / real 等）
verify-all.bat --list           列出检测到的环境
verify-all.bat --timeout 600    覆盖默认 420 秒超时
verify-all.bat --client <名>    客户端准备（见 1.4，已被 L3 直启链路取代）
```

### 1.2 判定协议

每个环境：后台启动 → 轮询日志（5 秒一次）→ 判定 → 按窗口标题杀进程树 → 下一个。

- **PASS**：日志出现 `Done (`（服务端完成启动）且无崩溃标记；selftest 自动档开启时
  进一步要求 `SELFTEST SUMMARY fail=0`（见 §2.2）；
- **FAIL**：出现 `FAILURE: Build failed` / `Crash report saved` 标记；或进程先于 Done 退出；或超时；
- 每环境日志留存于 `verify-reports\run-<时间戳>\logs\<环境名>.log`，
  汇总 HTML 报告为同目录 `index.html`（自包含、含失败环境日志尾 40 行，浏览器直接打开，
  按时间戳留档可翻历史趋势）；
- 退出码：全 PASS 为 0，有 FAIL 为 1（可被后续 CI 复用）。

实现注意（踩过的坑）：`timeout` / `tasklist` / `taskkill` 必须用
`%SystemRoot%\System32\...` 绝对路径——从 Git Bash 调用时 PATH 里的 coreutils 同名命令会
劫持裸命令名，导致轮询空转、计数器瞬间耗尽而误杀正常启动中的服务端。

### 1.3 环境适配器

| 环境名 | 来源 | 说明 |
|---|---|---|
| `dev-fabric` | `gradlew --no-daemon :fabric:runServer --offline` | 开发环境服务端，免搭建免下载；首次运行自动写 `fabric\run\eula.txt`（一次性世界，可随时删） |
| ~~`dev-forge`~~ | （**已禁用**，见 1.5 已知问题） | forge dev 运行的 FML 类路径缺 common，启动即崩；forge 的启动冒烟走 real-server 路线 |
| `real-<目录名>` | `E:\MC\modtest\servers\<目录>\verify-server.cmd` | **真实打包环境**：用户自备服务端实例，目录内提供 `verify-server.cmd`（前台、headless 启动服务端），目录与路径不得含空格 |

dev 路线验证「代码能跑」，real 路线验证「打包产物能装」（mixins、依赖打包、加载器真实版本差异）。
real-server 环境搭建涉及网络下载 server jar，按项目规则需用户自备或明确同意下载，**尚未开始**。

### 1.4 客户端准备（已由 L3 直启链路取代）

原设计「复制 jar 进实例 mods + 提示用 PCL 启动」仅自动化了 jar 部署（该能力现由 test-panel
「手测」页实例部署承接，见 §4）；**实例启动本身已由 L3 的 `launch-client.py` 版本 json 直启
彻底解决**（PCL 无法可靠命令行化的问题不再构成阻塞），见 §3.1。

### 1.5 实跑结论（2026-10-26）

- **dev-fabric：PASS**。`Done (20.780s)`，日志含 20 条 todolist 初始化记录，
  `Storage backend: H2 (forced)` 正常，报告留存于当轮 `verify-reports\`；
- 调试过程中踩过两个坑（已修，见 1.2 坑注）：PATH 劫持与 timeout.exe 的 stdin 限制，
  最终用 `%SystemRoot%\System32\ping.exe` 做轮询延时，任何调用环境下都稳定；
- **dev-forge：已知问题，已禁用**。根因：本项目 common 以「构建期打进平台 jar」方式集成
  （`tasks.jar { from(commonMainOutput) }`），fabric 的 dev 运行因 Knot 类加载器回退父类路径
  而碰巧可用，forge 的 dev 运行走 modlauncher 独立类加载层，FML 类路径上没有 common 的
  classes，启动即 `NoClassDefFoundError: com/todolist/platform/DataPathProvider`。
  尝试过 `loom.mods` 跨项目源集注册（architectury-loom 1.7.435），无效，已回退该尝试。
  **这不是 mod 代码缺陷**——打包后的 forge jar 在真实游戏一直正常。后续若要修：把 common
  改造为各平台 `modImplementation` 可消费的形态（参照 Architectury 官方模板的 Split Source
  Sets / common 预发布路线），属独立小任务；**当前 forge 的启动冒烟由 real-server 环境承担**
  （用打包产物，本来就是对症的验证层级）。

## 2. L2：数据断言（已落地，selftest 形态）

### 2.1 与原 GameTest 规划的关系

原规划走 Fabric/Forge GameTest 框架（假玩家模拟事件）。实际落地选择了 **mod 侧自建场景引擎**
（`/todo selftest` 命令，`common/src/main/java/com/todolist/bootstrap/SelfTestSupport.java`）：
场景直接跑在真实服务端逻辑上，无需 GameTest 框架的注册差异层，且同一套场景可同时用于
无头判定（L1 链路）与实机玩家档（L3 链路），跨加载器零平台适配。GameTest 路线未走、不再规划。

### 2.2 形态与判定

- **12 场景两档**：控制台档 4（无需玩家，`SelfTestSupport` 自建隔离 UUID 存储空间）+
  玩家档 8（需要真实玩家上下文）；覆盖触发器推进、任务完成判定、团队指派、H2 存取等核心语义；
- **无头判定**：服务端以自动模式启动（环境变量 `TODOLIST_SELFTEST_AUTO=1` 或
  `-Dtodolist.selftest.auto=1`）→ 启动完成后自动执行控制台档 → 日志输出
  `SELFTEST SUMMARY pass=N fail=M mode=console` → verify-all 以 `fail=0` 参与 PASS 判据；
- **实机玩家档**：L3 链路中以 `/todo selftest run all`（mode=player）执行，F2 截图判定
  `SELFTEST SUMMARY pass=12 fail=0`；
- **面板对接**：test-panel「自动化」页 selftest 回归按钮 → `verify-all.bat dev-fabric` →
  `panel/logparse.py` 解析 `[CMD]/[GUI]/[MIXIN] × [GROUP|CASE] × [RUN|PASS|FAIL]` 标签为用例表。

### 2.3 增量策略（持续）

每修一个实机反馈问题，就补一条 selftest 场景固化——E3-E15 那 13 轮实机修订里的每一轮，
理论上都应已是一条场景；新需求的验收标准里能写成断言的，直接进 selftest 场景清单。

## 3. L3：实机自动冒烟链路（定型 v3，2026-11-22）

### 3.1 架构与脚本位置

链路脚本为**仓库外本地资产**（含真实机器路径与实例布局，不进 git），集中存放于
`E:\AI\Todolist-versions\dev\smoke-reports\<日期主题>\scripts\`，现役主目录
`20261121-gui-verify\scripts\`：

| 脚本 | 职责 |
|---|---|
| `launch-client.py` | **版本 json 直启**：解析 PCL 式实例 json，拼 classpath（同 artifact 去重防 ASM 重复）、natives、assets，离线 legacy 登录，JDK 按 17/21/25 参数化；用法 `python -X utf8 launch-client.py <实例目录> <玩家名> [java_home] [世界名]`（第 4 参即 Quick Play 直进世界） |
| `mc-focus.ps1` | **真前台**：找 java 进程的 Minecraft 窗口，ShowWindow(9) + ALT keybd_event 绕前台锁 + SetForegroundWindow |
| `ime.ps1` | 输入法切换（SHIFT 切微软拼音中英 / Win+Space 切语言）——**进游戏先切英文再驱动**，中文 IME 会吞按键 |
| `postkeys.ps1` | PostMessage 键盘（WM_KEYDOWN/UP 扫码：ESC/T/ENTER/F2/F3/K/SLASH）与聊天文本（WM_CHAR 逐字）；`focus` 模式伪造 WM_SETFOCUS；`release` 模式 ClipCursor(NULL) 解光标圈禁；**脚本必须纯 ASCII**（PS5.1 把 UTF-8 无 BOM 中文注释按 GBK 解读，乱码字节会吞掉后续代码行） |
| `postclick.ps1` / `mouse.ps1` / `keys.ps1` | 鼠标点击（坐标从当次截图定位）/ 激活+单击 / SendKeys |
| `window.ps1` | 窗口激活/最大化/移动/挪桌面（最大化后 GUI 走大屏响应式布局，值得各验一次） |
| `move_to_test.py` | pyvda 把窗口挪到「测试」虚拟桌面 |
| `snap.ps1` | 屏幕级抓窗口位图（GetWindowRect 裁剪），F2 的备用通道 |

**为何不用 `gradlew runClient`**：loom 需联网下载 MC 客户端资产，本机无 loom assets 缓存，
`--offline` 被拒；服务端 runServer 不需要资产可正常跑（L1 即基于此）。客户端实机的正路就是
解析 PCL 版本 json 直接 java 启动真实实例——classpath 的 `${classpath}` 占位符**必须替换**
（官方 json 靠启动器替换，漏了就 ClassNotFoundException），实例 natives 由 PCL 预先提取好。

### 3.2 前置一次性配置（每实例）

1. `server.properties`：`pause-when-empty-seconds=0`（禁失焦自动暂停——单机 Quick Play 后
   集成服无人即弹暂停菜单会卡死驱动序列）；
2. 存档 `level.dat`：`allowCommands` 字节级检查（gzip 解压后按 TAG_Byte 布局定位，0 则改 1）；
   旧世界无 OP 权限会让 `/todo` 命令 402。26.1.2 新世界作弊默认开，无需修改；
3. mods 刷新：分支构建产物拷入实例 mods，**旧版本 jar 先移入 `mods/_disabled`**
   （同 modid 双 jar 冲突，如 1.4.2 与 1.4.3 共存）；
4. 玩家身份：`launch-client.py` 已改为优先从实例 `usercache.json` 按玩家名取历史 UUID——
   实机看「我的」视图数据玩家名传 `EC_XY`（真实账号），数据实况判定用 `/todo h2 health`。

### 3.3 标准流程 v3 序列（全分支统一）

```text
① 分支构建产物 → 刷实例 mods（旧 jar 移 _disabled）
② launch-client.py Quick Play 直进世界（玩家名 EC_XY + 世界名按实例）→ 等 Synced 日志
③ ime.ps1 切英文 → mc-focus.ps1 真前台（每次驱动前必须执行——用户侧任何消息都会
   把系统焦点切走，失焦时 Screen 事件全不分发，这是 26 线曾"驱动全失效"的真正根因）
④ window.ps1 maximize（大屏布局 + 真焦点，1.20.2+ Screen 门控自然解锁）
⑤ 自动暂停菜单出现（Quick Play 加载后必然）→ 点击"回到游戏"（坐标必须从当次截图定位，
   窗口尺寸/位置每次会话不同）
⑥ SLASH 键直开聊天栏（自动带 / 前缀）→ WM_CHAR 输入 "todo selftest run all"（不带斜杠）
   → ENTER → 等 15s
⑦ SLASH 键开聊天栏看历史（输出 10s 淡出但历史保留）→ F2 落盘 → 读 PNG 判 SELFTEST SUMMARY
⑧ 收尾：ESC 关聊天栏 → postkeys release 解 ClipCursor（防光标圈禁遗留卡住用户光标）
```

**聊天命令输入协议（2026-11-22 定型）**：命令一律 **SLASH 键直开聊天栏 + 输入不带斜杠的
"todo xxx"**；不要按 T 开栏再输 "/todo"——T 键按下时其字符事件会落进已打开的聊天输入框
拼成 "t/todo" 被当普通聊天发出、命令不执行。轮与轮之间保证聊天栏关闭。

### 3.4 判定通道（截图 + 数据双轨）

| 通道 | 内容 | 适用 |
|---|---|---|
| F2 游戏内截图 | PostMessage F2 → 实例 `screenshots/*.png`（游戏原生渲染帧，含 GUI/HUD/聊天回显，零遮挡、后台可用） | 主通道；snap.ps1 屏幕截图备用 |
| `todolist.mv.db` mtime | 命令是否执行的硬信号（拒绝类命令不写库） | 拒绝件判定、命令执行确认 |
| H2 JDBC 直查 | 退出游戏后 `libs/h2-2.2.220.jar` 直查终验（URL 必须带 `;DATABASE_TO_UPPER=FALSE`；JDK17 单文件运行先 `javac -encoding UTF-8` 再 java） | 拒绝件零落库断言、桶计数闭合等数据级权威结论 |

**数据级验证优先于截图**：视觉判定工具可用性随模型变动（曾出现 Read 图片只回链接、
analyze_image 报 400 的情况），截图只作视觉结论；数据正确性以 mv.db mtime + JDBC 终验为准。

### 3.5 版本差异参数表（流程一致，仅参数不同）

| 版本线 | JDK | 世界名（实例） | 特有注意 |
|---|---|---|---|
| 1.20.1 | 17 | 新的世界 | allowCommands 需修（旧世界） |
| 1.20.2-1.20.6 | 17/21 | 各实例 | 无（流程直用） |
| 1.21.1-1.21.11 | 21 | 新的世界 | 1.21.9-11 覆盖源集有测试侧副本，check 才编译 |
| 26.1/26.2/26.3 | 25 | New World（26.1.2）/新的世界 | 26.3 SDL 输入世代 WM_CHAR 首跑直接生效（2026-11-28 实测，keybd_event 逐键为备用回退）；26.x 各分支自带 `build-local-26x.bat`（自动选 Java 25 + 产物自动部署进实例，2026-11-28 实测可用） |

### 3.6 版本演进记录（TODO.md 提交可考）

- **v1（2026-11-21，`22c9b4d`）**：1.20.4 冒烟半成（selftest 因旧世界无 OP 失败 → 字节修
  allowCommands；1.20.2+ 焦点门控未破），当时计划走「多人链路 runServer + Quick Play
  Multiplayer + CreateDesktop」待立项；
- **v2（2026-11-22，`49a7600`）**：改单人 Quick Play + PostMessage 跨桌面驱动 + F2 判定，
  三项修复（allowCommands / pause 禁用 / SETFOCUS 门控解锁）落地，三分支 selftest 12/12 PASS
  （26 线当时因 Screen 激活判定误判升级而延后）；
- **v3（2026-11-22，`46265db`）**：补 mc-focus 真前台（根因实锤：失焦才是驱动失效主因，
  26 线与 1.21 系无门控差异），全分支统一适用；定型时五实例实测全 PASS
  （1.20.1 / 1.20.4 / 1.21.1 / 1.21.4 / 26.1.2），1.21.9-11 定向抽查 PASS；
- **补验（2026-11-28）**：26.2 / 26.3 按 v3 流程补验 selftest 12/12 PASS（26.3 SDL 输入世代
  WM_CHAR 首跑直接生效，无需逐键回退）——至此九分支实例级实机冒烟全闭环。补验同时发现并修复
  26.2/26.3 测试基建适配缺口：MC 26.2 起 `Minecraft.screen` 字段迁至 `Gui.screen`（主代码守卫
  已走 `minecraft.gui.screen()`，测试辅助 `GuiTestSupport.initScreen` 仍写旧字段导致离线
  guiSystemTest 失败），修复为经 `FakeMinecraftClient.setScreen` 同步（各分支独立提交）。

### 3.7 关键坑速查

1. 中文输入法拦截模拟按键（连 ESC 都被吞）——先切英文再驱动；
2. 世界列表必须先点击世界条目选中再点"进入选中的世界"，直接点进入无效（不用 Quick Play 时）；
3. 窗口失焦则 Screen 事件（ESC/T/打字/点击）全不分发，KeyMapping 轮询路径（F2）无门控持续生效；
4. MC 进世界后 GLFW CURSOR_DISABLED 会 ClipCursor 系统级圈禁光标（失焦/退出后仍生效）——
   三重防护：收尾先 ESC 开暂停菜单让 MC 主动解 grab → postkeys release 兜底 → 虚拟桌面隔离；
5. 单机命令回显不进 latest.log，判定用 F2 截图；客户端日志看实例 `logs/latest.log`
   （stdout 重定向抓不到 log4j 的 mod 行），关键行 `Received N tasks from server` / `Client: Synced N projects`；
6. Git Bash 调 PowerShell 传 `/todo` 类斜杠参数必须 `MSYS_NO_PATHCONV=1`（否则被转成 Git 安装路径）；
7. 视觉判定工具可用性随视觉模型变动，数据级验证优先（见 §3.4）。

## 4. test-panel 本地工作台（仓库外）

`E:\AI\Todolist-versions\test-panel`（标准库 HTTP 服务，`127.0.0.1:8765` 仅本机），
2026-11-01 落地、11-14 改版，定位是**验证工作台**而非实时监控面板。与 2026-10-25
「不手搓本地 web 监控面板」评审结论的关系：该结论反对的是"为单轮验证做实时监控"；
test-panel 实际沉淀的是**验证基建**（构建/测试/回归/手测/实例部署的统一入口与留档），
且完全在仓库外、零主仓依赖，不构成主仓维护成本。远期正路仍是 CI（见 §5）。

- 页签：总览 / 自动化 / 手测 / 设置（旧 9 页签已收编）；
- 自动化页：构建、测试、triplet 回归、release-precheck、selftest 回归（dev-fabric，
  经 verify-all + logparse 出用例表）；
- 手测页：实例部署（矩阵实例分组 + dist 最新 jar 匹配 + 部署全部并打开 PCL + PCL 主页生成）
  + 手测场景注册表（manual/visual 主表，状态按仓库+分支存档）；
- 数据留档：`data\runs\<时间戳-类型>\`（meta + output）；
- 细节见其自带 `README.md`。

## 5. 演进方向

1. **CI**：L1（含 selftest 无头档）挂 GitHub Actions（push 触发，headless 服务端可跑），
   免费获得网页端运行界面、历史趋势、失败通知——本地工作台不替代 CI，两者互补；
2. **L2 增量**：每修一个实机反馈问题补一条 selftest 场景（§2.3，持续执行）；
3. **real-server 环境**：按加载器搭建真实打包服务端实例（需网络，按项目规则征询后进行），
   补齐 forge 打包产物的自动启动冒烟；
4. **26.3 SDL 输入驱动**：~~26.3 为 SDL 输入世代，若 WM_CHAR 驱动不通，按版本差异参数表
   改 keybd_event 逐键~~（已闭环：2026-11-28 实测 WM_CHAR 首跑直接生效，keybd_event 逐键降级为备用回退）。

## 6. 验收标准（本方案自身的）

- [x] L1：一条命令无人值守跑完所有检测环境，输出 PASS/FAIL 汇总与 HTML 报告
- [x] 版本过滤：调试单环境失败时不必全量重跑
- [x] 报告留档：verify-reports 按时间戳可翻历史
- [x] L2：数据断言落地——selftest 12 场景两档 + verify-all 无头判定 + 面板用例表
      （原 GameTest 路线未走，见 §2.1）
- [x] L3：实机自动冒烟链路定型 v3——直启 + 驱动 + F2 + 数据双通道；定型时五实例实测全 PASS，
      2026-11-28 补验 26.2 / 26.3 后九分支实例级实机冒烟全闭环（§3.6）
- [ ] CI：verify-all + selftest 无头档挂 GitHub Actions
- [ ] real-server 环境搭建（forge 打包产物自动冒烟）
- [x] 26.3 SDL 驱动验证（2026-11-28 实测 WM_CHAR 首跑直接生效，keybd_event 逐键降级为备用回退）
