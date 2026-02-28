# Todo List Mod for Minecraft

一个简单而强大的 Minecraft 待办事项模组，支持单人和多人模式。

A simple and powerful todo list mod for Minecraft, supporting both single-player and multiplayer.

## 🌟 Features / 功能

### v1.0.0 Capability Overview / 1.0.0 版本能力概览
- ✅ In-game GUI task management: CRUD, priority, tags, filter & search / 游戏内GUI任务管理：增删改查、优先级、标签、筛选与搜索
- ✅ HUD todo list: expand/collapse, view header, configurable size/position/opacity / HUD待办列表：展开/收起、视图标题、可配置尺寸/位置/透明度
- ✅ Multiplayer & team collaboration: team tasks, view semantics, server-side permission checks and audit logs / 多人团队协作：团队任务、视图语义、服务端权限校验与操作审计日志
- ✅ Projects: personal/team projects, project sidebar, basic project management / 项目：个人/团队项目、项目侧边栏与基础管理
- ✅ i18n: Chinese & English / 多语言：中文与英文

More details / 更多说明：
- [FEATURES.md](FEATURES.md) / [FEATURES_EN.md](FEATURES_EN.md)
- [ROADMAP.md](ROADMAP.md) / [ROADMAP_EN.md](ROADMAP_EN.md)
- [CHANGELOG.md](CHANGELOG.md) / [CHANGELOG_EN.md](CHANGELOG_EN.md)

## 📸 Screenshots / 截图

### Singleplayer / 单人游戏

![image-20260218171343365](https://qiniuyun.emptycity.top/typora-img/image-20260218171343365.png)

![image-20260218171437868](https://qiniuyun.emptycity.top/typora-img/image-20260218171437868.png)

![image-20260218171517007](https://qiniuyun.emptycity.top/typora-img/image-20260218171517007.png)

### Multiplayer / 多人游戏

#### Op View / 管理员视角

![image-20260218172028941](https://qiniuyun.emptycity.top/typora-img/image-20260218172028941.png)

![image-20260218172248351](https://qiniuyun.emptycity.top/typora-img/image-20260218172248351.png)

![image-20260218172311546](https://qiniuyun.emptycity.top/typora-img/image-20260218172311546.png)

#### Player View / 普通玩家视角

![image-20260218172335169](https://qiniuyun.emptycity.top/typora-img/image-20260218172335169.png)

![image-20260218172358885](https://qiniuyun.emptycity.top/typora-img/image-20260218172358885.png)

![image-20260218172423215](https://qiniuyun.emptycity.top/typora-img/image-20260218172423215.png)



## 🚀 Installation / 安装

### Requirements / 要求
- Minecraft 1.20.1
- Fabric Loader 0.14.21+
- Fabric API 0.87.0+

### Steps / 步骤

1. Download the latest mod JAR file / 下载最新的模组JAR文件
2. Place it in your `mods` folder / 将文件放入`mods`文件夹
3. Launch Minecraft / 启动Minecraft
4. Press **K** key in-game to open the todo list / 在游戏中按**K**键打开待办列表

## 🎮 Usage / 使用方法

### Key Bindings / 按键绑定
- **K** - Open Todo List / 打开待办列表
- **H** - Toggle HUD (expand/collapse) / 展开或收起HUD

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
- **Edit** - Modify the task / 编辑任务
- **Delete** - Remove the task / 删除任务
- **Complete** - Mark as completed / 标记完成
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

Requires Java 17+ / 需要 Java 17+（仓库提供 `build-with-java17.bat` 便于在 Windows 上构建）

```bash
# Clone the repository / 克隆仓库
git clone https://github.com/spacehouse/todoList.git
cd todoList

# Build the mod / 构建模组
./build-with-java17.bat build

# The JAR will be in build/libs/ / JAR文件位于build/libs/目录
```

### Project Structure / 项目结构

```
todoList/
├── src/main/java/com/todolist/
│   ├── TodoListMod.java              # Main mod class / 主类
│   ├── client/
│   │   ├── TodoClient.java           # Client initialization & key bindings / 客户端初始化与按键绑定
│   │   ├── TodoHudRenderer.java      # HUD renderer / HUD渲染
│   │   ├── ClientProjectPackets.java # Client-side project networking / 客户端项目网络辅助
│   │   ├── ClientTaskPackets.java    # Client-side network helpers / 客户端网络辅助
│   │   └── ModMenuIntegration.java   # Mod Menu config integration / Mod Menu配置集成
│   ├── config/
│   │   └── ModConfig.java            # Configuration handling / 配置处理
│   ├── gui/
│   │   ├── AddProjectScreen.java     # Create project screen / 新建项目界面
│   │   ├── ProjectSettingsScreen.java# Project settings & members / 项目设置与成员管理
│   │   ├── AddMemberScreen.java      # Add member screen / 新增成员界面
│   │   ├── ConfirmDeleteProjectScreen.java # Project delete confirm / 删除项目确认
│   │   ├── ProjectListWidget.java    # Project sidebar list / 项目侧边栏列表
│   │   ├── TodoScreen.java           # Main GUI / 主界面
│   │   ├── TaskListWidget.java       # Task list widget / 任务列表组件
│   │   ├── ScrollBar.java            # Scroll bar widget / 滚动条组件
│   │   └── ConfigScreen.java         # HUD config GUI / HUD配置界面
│   ├── network/
│   │   ├── TaskPackets.java          # Task networking / 任务网络包
│   │   └── ProjectPackets.java       # Project networking / 项目网络包
│   ├── permission/
│   │   └── PermissionCenter.java     # Permission center / 权限中心
│   ├── project/
│   │   ├── Project.java              # Project model / 项目模型
│   │   ├── ProjectManager.java       # Project manager / 项目管理器
│   │   ├── ProjectStorage.java       # Project persistence / 项目持久化
│   │   └── ProjectSaveDebouncer.java # Batched saves / 合并写入
│   └── task/
│       ├── Task.java                 # Task entity / 任务实体
│       ├── TaskManager.java          # Task manager / 任务管理器
│       └── TaskStorage.java          # Data persistence / 数据持久化
└── src/main/resources/
    ├── assets/todolist/
    │   └── lang/                     # Language files / 语言文件
    └── fabric.mod.json               # Mod metadata / 模组元数据
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

Made with ❤️ by the TodoList Mod Team
