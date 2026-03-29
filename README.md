# Todo List Mod for Minecraft

一个简单而强大的 Minecraft 待办事项模组，支持单人和多人模式。

A simple and powerful todo list mod for Minecraft, supporting both single-player and multiplayer.

## 🌟 Features / 功能

### v1.2.2 Capability Overview / 1.2.2 版本能力概览
- ✅ Minecraft 1.21.1 multi-loader support (Fabric / Forge / NeoForge), client & server / Minecraft 1.21.1 多加载器支持（Fabric / Forge / NeoForge），覆盖客户端与服务端
- ✅ In-game GUI task management: CRUD, priority, tags, filter & search / 游戏内GUI任务管理：增删改查、优先级、标签、筛选与搜索
- ✅ HUD todo list: expand/collapse, view header, configurable size/position/opacity / HUD待办列表：展开/收起、视图标题、可配置尺寸/位置/透明度
- ✅ Multiplayer & team collaboration: team tasks, view semantics, server-side permission checks and audit logs / 多人团队协作：团队任务、视图语义、服务端权限校验与操作审计日志
- ✅ Projects: personal/team projects, project sidebar, basic project management / 项目：个人/团队项目、项目侧边栏与基础管理
- ✅ Stability upgrades in LAN/team sync, personal task restore, and assign dialog UX / 局域网/团队同步、个人任务恢复与指派弹窗体验稳定性增强
- ✅ i18n: Chinese & English / 多语言：中文与英文

More details / 更多说明：
- [FEATURES.md](FEATURES.md) / [FEATURES_EN.md](FEATURES_EN.md)
- [ROADMAP.md](ROADMAP.md) / [ROADMAP_EN.md](ROADMAP_EN.md)
- [CHANGELOG.md](CHANGELOG.md) / [CHANGELOG_EN.md](CHANGELOG_EN.md)

## 📸 Screenshots / 截图

### Singleplayer / 单人游戏

![image-20260308182353464](https://qiniuyun.emptycity.top/typora-img/image-20260308182353464.png)

![image-20260308182431099](https://qiniuyun.emptycity.top/typora-img/image-20260308182431099.png)

### Multiplayer / 多人游戏

#### Op View / 管理员视角

![image-20260308182538945](https://qiniuyun.emptycity.top/typora-img/image-20260308182538945.png)

![image-20260308182634208](https://qiniuyun.emptycity.top/typora-img/image-20260308182634208.png)

![image-20260308182736610](https://qiniuyun.emptycity.top/typora-img/image-20260308182736610.png)

![image-20260308182916689](https://qiniuyun.emptycity.top/typora-img/image-20260308182916689.png)

#### Player View / 普通玩家视角

![image-20260308182825917](https://qiniuyun.emptycity.top/typora-img/image-20260308182825917.png)

![image-20260308182952758](https://qiniuyun.emptycity.top/typora-img/image-20260308182952758.png)

![image-20260308183029983](https://qiniuyun.emptycity.top/typora-img/image-20260308183029983.png)

![image-20260308183053439](https://qiniuyun.emptycity.top/typora-img/image-20260308183053439.png)

## 🚀 Installation / 安装

### Requirements / 要求
- Minecraft 1.21.1
- Java 21+
- **Fabric**: Fabric Loader `0.18.1+` & Fabric API `0.116.9+1.21.1`
- **Forge**: Forge `52.1.10+`
- **NeoForge**: NeoForge `21.1.219+`

### Steps / 步骤

1. Download the latest mod JAR file / 下载最新的模组JAR文件
2. Place it in your `mods` folder / 将文件放入`mods`文件夹
3. Launch Minecraft / 启动Minecraft
4. Press **K** key in-game to open the todo list / 在游戏中按**K**键打开待办列表

## 🎮 Usage / 使用方法

### Key Bindings / 按键绑定
- **K** - Open Todo List / 打开待办列表
- **H** - Toggle HUD (expand/collapse) / 展开或收起HUD
- **J** - Toggle HUD visibility / 显示或隐藏HUD

### Projects / 项目
- The todo GUI is project-based: tasks you create belong to the currently selected project in the left sidebar. / 待办界面以“项目”为维度：你创建的任务会归属到左侧侧边栏当前选中的项目。
- Use the top button in the sidebar to switch between **Personal Projects** and **Team Projects** (team projects require server support). / 侧边栏顶部按钮可在**个人项目/团队项目**之间切换（团队项目需要服务端支持）。
- Click a project to switch context; starred projects are sorted to the top. / 点击项目切换当前项目；星标项目会自动置顶排序。
- Click ★/☆ to star/unstar a project (used by HUD “Starred projects” source). / 点击 ★/☆ 可星标/取消星标项目（用于 HUD 的“星标项目”来源）。
- Use Add/Edit/Delete buttons in the sidebar to manage projects (delete requires confirmation). / 使用侧边栏的新增/编辑/删除管理项目（删除需要二次确认）。

### Creating Tasks / 创建任务
1. Open the todo list with **K** key / 按**K**键打开待办列表
2. Select a project in the left sidebar / 在左侧项目列表中选择一个项目
3. Enter task title and description / 输入任务标题和描述
4. Click **Add** button (or press Enter when the title field is focused) / 点击**添加**按钮（或在标题输入框聚焦时按 Enter）

### Managing Tasks / 管理任务
- Click on a task to select it / 点击任务选中
- **Complete** - Mark as completed by clicking the checkbox / 点击复选框标记完成
- **Right-Click Menu** - Right-click a task to open the context menu for more actions: / **右键菜单** - 右键点击任务打开上下文菜单以执行更多操作：
  - **Edit** - Modify the task / 编辑任务
  - **Delete** - Remove the task / 删除任务
  - **Priority** - Change task priority (High/Medium/Low) / 修改优先级（高/中/低）
- Use filter buttons to show specific tasks / 使用筛选按钮查看特定任务
- Use the search box to filter by title/description/tags / 使用搜索框按标题、描述、标签过滤任务
- Use priority buttons (High/Medium/Low) to quickly filter / 使用高/中/低优先级按钮快速筛选

### Team Tasks / 团队任务
- Use view buttons at the top of the GUI to switch between Personal and team views: **Unassigned**, **All Assigned**, **Assigned to Me** / 使用界面顶部视图按钮在个人视图和团队视图之间切换：**待分配**、**已分配**、**分配给我**
- In team views, changes are applied only after clicking **Save**; the server will validate permissions and handle conflicts / 在团队视图中修改后需要点击**保存**才会提交到服务器，并进行权限和冲突校验
- After **Save**, **Cancel**, or closing with **Esc** in team views, the client always re-syncs team tasks from the server so that local unsaved edits are discarded and the list matches server state / 在团队视图中点击**保存**、**取消**或按 **Esc** 关闭界面后，客户端都会从服务器重新同步团队任务，本地未保存修改会被丢弃，列表始终与服务器一致
- Project members with role **MEMBER** can only claim/abandon and complete their own team tasks; **PROJECT_MANAGER/LEAD/OP** can fully manage team tasks. The “Assign Others” button is shown only when you have permission. / 角色为 **成员** 的项目成员只能领取/放弃并完成自己的团队任务；**项目经理/负责人/OP** 可完整管理团队任务。“指派他人”按钮仅在拥有权限时显示
- In team projects, adding new tasks is only enabled in **Unassigned** view; other team views disable the Add button and Enter-to-add. / 在团队项目中，仅 **待分配** 视图允许新增任务；其他团队视图会禁用添加按钮与 Enter 添加操作。

### HUD Config / HUD 配置
- Open the todo GUI with **K**, then click the top-right **Config** button to open the config screen / 按 **K** 打开待办界面，点击右上角的 **配置** 按钮进入配置界面
- Configure HUD width, max height, todo/done limits (0–30), default expanded state, whether to show when empty, default list view, list project source and opacity / 可配置HUD宽度、最大高度、待办/已办显示条数（0–30）、默认展开、无任务时是否显示、默认列表视图、列表项目来源与透明度
- **Real-time View Sync** - The HUD task list automatically syncs with the current view filter in the main GUI (e.g., Personal/Team views, Priority filters) / **实时视图同步** - HUD 任务列表会根据主界面的当前视图过滤项（如个人/团队视图、优先级筛选）实时同步显示内容
- In true single-player worlds, the HUD default list view is locked to **Personal** and cannot be changed; HUD team views are hidden / 在真正的单人世界中，HUD默认列表视图固定为**个人**且不可修改，同时HUD中不显示团队视图
- Drag the HUD preview rectangle to set a custom position; changes apply after clicking **Save & Apply** / 在配置界面中拖动HUD预览矩形设置自定义位置，点击**保存并应用**后生效
- If you use Mod Menu, you can also open this config screen from the mod’s entry / 如果安装了 Mod Menu，也可以从 Mod Menu 中打开该配置界面


## 🛡️ Permission System / 权限系统

### Permission Center / 权限中心
The mod implements a unified server-side Permission Center that evaluates operations in team projects based on role, view scope, task state/assignment, and whether the player is a member of the project. OP always bypasses checks.
模组在服务端实现了统一的权限中心，会根据团队项目中的角色、当前视图范围、任务完成状态/指派关系以及玩家是否为项目成员，对相关操作进行判定。OP 永远拥有所有权限。

### Roles / 角色
- **OP**: Server operators (OP) / 服务器管理员（OP）
- **PROJECT_MANAGER**: Project owner/manager, full project control / 项目经理（创建人，项目权限最大）
- **LEAD**: Project lead, elevated permissions / 负责人（次级权限）
- **MEMBER**: Regular project member / 成员（最小权限）

### View Scopes / 视图范围
- **PERSONAL**: Personal tasks / 个人任务
- **TEAM_UNASSIGNED**: Team tasks - Unassigned / 团队任务 - 待分配
- **TEAM_ALL**: Team tasks - All assigned / 团队任务 - 已分配（所有）
- **TEAM_ASSIGNED**: Team tasks - Assigned to me / 团队任务 - 分配给我

### Key Rules / 核心规则
- Team operations require being a member of the project (non-members are read-only). / 团队相关操作要求你是该项目成员（非成员默认只读）。
- In team projects, **PROJECT_MANAGER/LEAD** can add/edit/delete/assign tasks, and can toggle completion in all team views. / 团队项目中 **项目经理/负责人** 可新增/编辑/删除/指派任务，并可在所有团队视图中切换完成状态。
- **MEMBER** can claim tasks in **Unassigned** view, and can abandon/complete tasks only when they are assigned to themselves (typically in **Assigned to Me**). / **成员** 仅可在 **待分配** 视图领取任务；仅能对“分配给自己”的任务执行放弃/完成（通常在 **分配给我** 视图）。
- Project editing/deletion is restricted to **PROJECT_MANAGER** (OP always allowed). / 项目编辑/删除仅 **项目经理** 可用（OP 例外）。
- Member management: **PROJECT_MANAGER** can manage members and roles; **LEAD** can manage members except the project manager and themselves (OP always allowed). / 成员管理：**项目经理** 可管理成员与角色；**负责人** 可管理成员（但不能操作项目经理和自己）（OP 例外）。

#### 1. PERSONAL (个人视图)
- All players can add/edit/delete/toggle completion for personal tasks. / 所有玩家都可对个人任务进行新增、编辑、删除与完成状态切换。

#### 2. TEAM_UNASSIGNED (团队-待分配)
- PROJECT_MANAGER / LEAD: add/edit/delete tasks, assign others, toggle completion / 项目经理/负责人：可新增/编辑/删除、指派他人、切换完成
- MEMBER: claim unassigned tasks / 成员：可领取未分配任务

#### 3. TEAM_ALL (团队-所有已分配)
- PROJECT_MANAGER / LEAD: edit/delete/reassign/toggle completion / 项目经理/负责人：可编辑/删除/改派/切换完成
- MEMBER: view only / 成员：只读查看

#### 4. TEAM_ASSIGNED (团队-分配给我)
- PROJECT_MANAGER / LEAD: edit/delete/reassign/toggle completion / 项目经理/负责人：可编辑/删除/改派/切换完成
- MEMBER: toggle completion and abandon for own assigned tasks / 成员：可对“分配给自己”的任务完成/放弃

## 📜 Operation Logs / 操作日志
- All effective team task operations (toggle complete, claim, abandon, assign others, save with changes) are validated by the Permission Center on the server and then recorded in the server console logs in a unified `[TEAM_OP]` format that includes player name, operation type, task ID, title and change details. / 所有有效的团队任务操作（完成状态切换、领取、放弃、指派他人、保存产生的实际变更）都会在服务器端先经过权限中心校验，然后以统一的 `[TEAM_OP]` 格式记录到服务器日志中，包含玩家名、操作类型、任务ID、标题以及变更详情。

## 🛠️ Development / 开发

### Build from Source / 从源码构建

Requires Java 21+ / 需要 Java 21+（仓库提供 `build-with-java21.bat` 便于在 Windows 上构建）

```bash
# Clone the repository / 克隆仓库
git clone https://github.com/spacehouse/todoList.git
cd todoList

# Build the mod / 构建模组
./build-with-java21.bat clean build

# The JAR will be in build/libs/ / JAR文件位于build/libs/目录
```

### Release / 发布

- Releases are published by pushing a Git tag that starts with `v`, for example `v1.2.2`. / 发布通过推送以 `v` 开头的 Git 标签触发，例如 `v1.2.2`
- The release workflow extracts notes from `CHANGELOG.md` based on the tag version. / 发布工作流会根据标签版本从 `CHANGELOG.md` 提取发布说明

### Project Structure / 项目结构

```text
todoList/
├── common/          # Shared game logic, GUI, data models, permissions / 通用逻辑、界面、数据与权限
├── fabric/          # Fabric bootstrap and networking adapter / Fabric 启动与网络适配
├── forge/           # Forge bootstrap and networking adapter / Forge 启动与网络适配
├── neoforge/        # NeoForge bootstrap and networking adapter / NeoForge 启动与网络适配
├── build.gradle     # Multi-loader build entry / 多加载器构建入口
└── gradle.properties
```

## 📝 Roadmap / 开发路线

See / 详见：
- [ROADMAP.md](ROADMAP.md) / [ROADMAP_EN.md](ROADMAP_EN.md)

## 🤝 Contributing / 贡献

Contributions are welcome! Please feel free to submit a Pull Request.
欢迎贡献！请随时提交Pull Request。

1. Fork the repository / Fork仓库
2. Create your feature branch / 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. Commit your changes / 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch / 推送到分支 (`git push origin feature/AmazingFeature`)
5. Open a Pull Request / 打开Pull Request

## 📄 License / 许可证

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 🙏 Credits / 致谢

- Fabric team for the excellent modding tools / Fabric团队出色的开发工具
- Minecraft community for inspiration / Minecraft社区的灵感

## 📧 Contact / 联系方式

- GitHub Issues: [Report bugs](https://github.com/spacehouse/todoList/issues)
- Discord: [Join our server](https://discord.gg/uYHw9MHNe)

---

Made with ❤️ by spacehouse
