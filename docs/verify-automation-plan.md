# 实机验证自动化方案（L1 已落地，L2/L3 规划）

> 状态：**L1 已实现并实跑验证**（`verify-all.bat`，2026-10-26）；L2 / L3 为规划
> 对应 TODO.md：进行中（材料反推轻量冒烟）→ 本方案 L3 承接视觉验收分工
> 更新日期：2026-10-26

## 0. 背景与目标

当前实机验证是完全手动的循环：构建 → 手动复制 jar 到各测试版本 → 手动启动 → 手动建任务 →
人眼判断结果 → 手动记录。这套循环是本项目 token 与时间成本的主要来源（材料反推 E3-E10 的
六轮实机反馈即为此付出）。

目标：把「数据与启动」部分全自动化，人工只保留「视觉判断」。

**三层分工（2026-10-25 方向评审确定）**：

| 层 | 内容 | 执行方式 | 状态 |
|---|---|---|---|
| L1 启动冒烟 | mod 能否在目标环境正常加载、存储初始化无异常、无启动崩溃 | 全自动、无人值守、全环境顺序执行 | ✅ 已实现 |
| L2 数据断言 | 触发器进度推进、任务完成判定、H2 存取语义正确 | GameTest 假玩家模拟事件，自动断言，增量补用例 | 🕓 规划 |
| L3 视觉验收 | GUI 布局、交互手感、渲染效果 | 人工；自动化仅负责「摆场景 + 截图落盘」，判断留给人 | 🕓 规划（当前用冒烟清单过渡） |

L3 的当前形态是 `docs/smoke-checklist-2026-10-25.md`（一次进游戏、一个版本验完一个特性，
GUI 代码在 common 三加载器共享，不逐版本重复）。

## 1. L1：verify-all.bat（已实现）

位置：仓库根 `verify-all.bat`（按 .gitignore 约定属**本地文件**，与 build-local-17.bat 同类，
不进 git）。默认面向 1.20.1 分支线（Java 17）。

### 1.1 用法

```text
verify-all.bat                  顺序跑全部检测到的环境
verify-all.bat fabric           只跑名字含 "fabric" 的环境（同理 forge / real 等）
verify-all.bat --list           列出检测到的环境
verify-all.bat --timeout 600    覆盖默认 420 秒超时
verify-all.bat --client <名>    客户端环境准备（见 1.4）
```

### 1.2 判定协议

每个环境：后台启动 → 轮询日志（5 秒一次）→ 判定 → 按窗口标题杀进程树 → 下一个。

- **PASS**：日志出现 `Done (`（服务端完成启动）且无崩溃标记；
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
real 环境首次搭建需要下载各加载器 server jar（涉及网络，按项目规则需用户自备或明确同意下载）；
搭建参考 `E:\MC\server\forge-server-1.19.2` 的既有结构。

### 1.4 客户端模式（--client，尽力而为项）

`verify-all.bat --client 1.20.1-Forge`：从 `dist\` 取该加载器最新 jar 复制进匹配的
`E:\MC\modtest\versions\<版本>\mods\`，然后提示用 PCL 启动对应版本并指向冒烟清单。
不自动拉起客户端——PCL 的实例选择无法可靠命令行化，强行自动化易碎；
该模式的价值是把「确认 mods 里是最新 jar」这步自动化。

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
  **这不是 mod 代码缺陷**——打包后的 forge jar 在真实游戏一直正常（E3-E10 六轮实机反馈佐证）。
  后续若要修：把 common 改造为各平台 `modImplementation` 可消费的形态（参照 Architectury
  官方模板的 Split Source Sets / common 预发布路线），属独立小任务；**当前 forge 的启动
  冒烟由 real-server 环境承担**（用打包产物，本来就是对症的验证层级）；
- real-server 环境搭建（需网络下载 server jar，按项目规则征询用户后进行）尚未开始。

## 2. L2：数据断言（规划，GameTest）

**现状利好**：fabric 的依赖里已经带着 `fabric-gametest-api-v1`（runServer 日志确认），
Forge 侧 1.20.1 亦有 GameTest 框架，无需新依赖即可起步。

路线：

1. 在 common 定义测试用例骨架（建任务 → 模拟事件 → 断言进度/完成态），
   平台侧用各自 GameTest 入口注册；跑法：服务端启动参数加 gametest 批量执行，
   `verify-all.bat` 的 PASS 判据从「Done」升级为「Done + gametest 全绿」；
2. 优先把**实机验证成本最高**的语义搬进来：触发器五种类型的推进与达标、
   团队指派变更清零（Phase U）、父任务级联完成（Y1）、H2 保存/重载往返；
3. 增量策略：每修一个实机反馈问题，就补一条 gametest 用例固化——
   E3-E15 那 13 轮实机修订里的每一轮，理论上都应已是一条用例；
4. 依赖同一「需求准入三问」：新需求的验收标准里能写成断言的，直接进 L2。

## 3. L3：视觉验收（规划）

- dev-only 截图命令（如 `/todo dev screenshot <场景>`）：以种子数据打开指定 GUI
  （任务列表 / 材料配方预览 / HUD），落盘 PNG 到固定目录；
- 人工只做「看图集」：一次特性一个版本一次（见冒烟清单定位），不逐版本重复；
- 明确**不做**自动图像 diff：MC 动态渲染（动画、光照、字体抗锯齿）误报率高，
  维护成本大于收益。

## 4. 演进：网页端监控的正路是 CI，不是本地 web 服务

L2 成熟后，把 verify-all 挂上 **GitHub Actions**（push 触发，headless 服务端可跑），
即可免费获得：网页端运行界面、历史趋势、失败通知。**不手搓本地 web 监控面板**——
单人本地项目、单轮验证分钟级，实时面板属于典型自造需求（2026-10-25 评审结论）。

## 5. 验收标准（本方案自身的）

- [x] L1：一条命令无人值守跑完所有检测环境，输出 PASS/FAIL 汇总与 HTML 报告
- [x] 版本过滤：调试单环境失败时不必全量重跑
- [x] 报告留档：verify-reports 按时间戳可翻历史
- [ ] L2：首个 gametest 用例进 check 链路（后续里程碑）
- [ ] L3：截图命令落地（后续里程碑）
