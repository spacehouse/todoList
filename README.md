# Todo List Mod for Minecraft

一个简单而强大的 Minecraft 待办事项模组，支持单人和多人模式。

A simple and powerful todo list mod for Minecraft, supporting both single-player and multiplayer.

## 🌟 Features / 功能

### ✅ Implemented / 已实现 (v1.0.0+)
- ✅ **Task Management** - Add, edit, delete, and complete tasks / 任务管理（增删改查）
- ✅ **Task Filtering & Search** - Filter by status/priority and search by text/tags / 任务筛选与搜索（状态、优先级、标题/描述/标签）
- ✅ **Auto Save** - Tasks are automatically saved on close / 关闭时自动保存
- ✅ **Manual Save** - Save button to manually save tasks / 手动保存按钮
- ✅ **Scrollable List** - Mouse wheel and draggable scrollbar / 可滚动列表（滚轮+拖动）
- 🎨 **Priority Levels** - Low, Medium, High with color coding / 优先级（低中高，颜色区分）
- 🏷️ **Tags** - Basic tag support with colored labels in GUI & HUD / 基础标签支持（GUI与HUD显示彩色标签）
- 🎮 **In-Game GUI** - Easy-to-use interface / 游戏内GUI界面
- 💾 **Persistent Storage** - Tasks saved to local file / 本地文件持久化
- ⌨️ **Keyboard Shortcuts** - K to open, H to toggle HUD, Enter to add, Esc to close / 键盘快捷键（K 打开，H 切换HUD，Enter添加，Esc关闭）
- 🌍 **Multi-Language** - English and Chinese / 多语言支持
- 📊 **HUD Display** - On-screen todo list with expand/collapse and current-view header; in single-player HUD always shows personal tasks / HUD待办列表显示（右上角，可展开/收起，显示当前视图和数量；单人模式下HUD始终显示个人任务）
- 👥 **Team Tasks** - Personal & team views (unassigned/assigned/assigned-to-me) with multiplayer sync, permissions and role-based buttons / 个人与团队任务视图（待分配/已分配/分配给我，多人同步与权限控制，按钮按角色区分）
- ⚙️ **HUD Config GUI** - In-game HUD width/height/limits/default view, show-when-empty, draggable preview; HUD default view is locked to personal in single-player / HUD配置界面（宽度、高度、条数、默认视图、空列表是否显示、预览拖动位置；单人模式下HUD默认视图固定为个人）

### 🔄 Recent Improvements / 最近改进 (2026-02)
- HUD default list view is forced to **Personal** in true single-player worlds, and team views are hidden there / 真单人世界中HUD默认视图锁定为个人视图并隐藏团队视图
- HUD team views now strictly follow GUI semantics: **Unassigned** (no assignee), **All Assigned**, **Assigned to Me** / HUD团队视图与GUI语义对齐：待分配（无指派人）、已分配（所有已指派）、分配给我
- Team task GUI now always re-syncs from server after **Save**, **Cancel** or closing with **Esc**, discarding unsaved local edits / 团队任务界面在点击保存、取消或按Esc关闭后都会从服务器重新同步任务数据，丢弃未保存的本地修改
- Priority buttons can be used even with no task selected to set the default priority for new tasks / 即使未选中任务也可以使用优先级按钮，为新任务预设优先级
- In team views, the **Assign Others** button is only visible for admins; regular players only see Claim/Abandon actions / 团队视图中“指派他人”按钮仅对管理员可见，普通玩家只显示领取/放弃操作

### 🚧 Planned / 计划中
- 🏷️ **Advanced Tagging & Categories** - Rich tag filters and categories / 高级标签与分类
- 💬 **Commands** - `/todo` command system / 命令系统
- 🌐 **Advanced Multiplayer Support** - More powerful team workflows and permissions / 更完善的多人与团队权限系统
- 📅 **Due Dates** - Set task deadlines / 截止日期
- 🎯 **Subtasks** - Break down tasks into smaller parts / 子任务

## 📸 Screenshots / 截图

### Singleplayer / 单人游戏

![image-20260218171343365](https://qiniuyun.emptycity.top/typora-img/image-20260218171343365.png)

![image-20260218171437868](https://qiniuyun.emptycity.top/typora-img/image-20260218171437868.png)

![image-20260218171517007](https://qiniuyun.emptycity.top/typora-img/image-20260218171517007.png)

### Multiplayer / 多人游戏

#### Op View / 管理员视角

![image-20260218172028941](https://qiniuyun.emptycity.top/typora-img/image-20260218172028941.png)

![image-20260218172248351](https://qiniuyun.emptycity.top/typora-img/image-20260218172248351.png)

![image-20260218172311546](C:/Users/%E7%A9%BA%E5%9F%8E%E9%87%8C/AppData/Roaming/Typora/typora-user-images/image-20260218172311546.png)

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

### Creating Tasks / 创建任务
1. Open the todo list with **K** key / 按**K**键打开待办列表
2. Enter task title and description / 输入任务标题和描述
3. Click **Add** button / 点击**添加**按钮

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
- Regular players can only claim/abandon and complete their own team tasks; admins can fully manage team tasks. The “Assign Others” button is only visible for admins / 普通玩家只能领取/放弃并完成自己的团队任务，管理员可以完整管理团队任务，“指派他人”按钮仅对管理员可见

### HUD Config / HUD 配置
- Open the todo GUI with **K**, then click the top-right **HUD Config** button to open the config screen / 按 **K** 打开待办界面，点击右上角的 **HUD配置** 按钮进入配置界面
- Configure HUD width, max height, todo/done limits (0–30), default expanded state, whether to show when empty, and default list view / 可配置HUD宽度、最大高度、待办/已办显示条数（0–30）、默认展开、无任务时是否显示以及默认列表视图
- In true single-player worlds, the HUD default list view is locked to **Personal** and cannot be changed; HUD team views are hidden / 在真正的单人世界中，HUD默认列表视图固定为**个人**且不可修改，同时HUD中不显示团队视图
- Drag the HUD preview rectangle to set a custom position; changes apply after clicking **Save & Apply** / 在配置界面中拖动HUD预览矩形设置自定义位置，点击**保存并应用**后生效
- If you use Mod Menu, you can also open this config screen from the mod’s entry / 如果安装了 Mod Menu，也可以从 Mod Menu 中打开该配置界面


## 🛡️ Permission System / 权限系统

### Permission Center / 权限中心
The mod implements a unified server-side Permission Center that evaluates every team-related operation based on the player’s role, current view scope, task completion state and assignment status.
模组在服务端实现了统一的权限中心，会根据玩家的角色、当前视图范围、任务完成状态以及指派关系，对所有与团队任务相关的操作进行判定。

### Roles / 角色
- **ADMIN**: Server operators (OP level 2+) / 管理员（OP等级2+）
- **MEMBER**: Regular players / 普通玩家

### View Scopes / 视图范围
- **PERSONAL**: Personal tasks / 个人任务
- **TEAM_UNASSIGNED**: Team tasks - Unassigned / 团队任务 - 待分配
- **TEAM_ALL**: Team tasks - All assigned / 团队任务 - 已分配（所有）
- **TEAM_ASSIGNED**: Team tasks - Assigned to me / 团队任务 - 分配给我

### Permission Matrix / 权限矩阵

#### 1. PERSONAL (个人视图)
| Operation / 操作 | ADMIN | MEMBER | Note / 说明 |
|------------------|-------|--------|-------------|
| ADD_TASK         | ✔     | ✔      | Personal tasks / 个人任务 |
| EDIT_TASK        | ✔     | ✔      | If not completed / 未完成即可 |
| DELETE_TASK      | ✔     | ✔      | If editable or completed / 可编辑或已完成 |
| TOGGLE_COMPLETE  | ✔     | ✔      | Any personal task / 任意个人任务 |
| CLAIM/ABANDON    | ✖     | ✖      | N/A / 不适用 |

#### 2. TEAM_UNASSIGNED (团队-待分配)
| Operation / 操作 | ADMIN | MEMBER | Note / 说明 |
|------------------|-------|--------|-------------|
| ADD_TASK         | ✔     | ✖      | Only Admin can add team tasks / 仅管理员可加团队任务 |
| EDIT/DELETE      | ✔     | ✖      | Only Admin can manage / 仅管理员管理 |
| TOGGLE_COMPLETE  | ✔     | ✖      | Member cannot complete unassigned / 成员不可完成未分配任务 |
| CLAIM_TASK       | ✔     | ✔      | Member can claim unassigned / 成员可认领 |
| ASSIGN_OTHERS    | ✔     | ✖      | Only Admin can assign / 仅管理员指派 |

#### 3. TEAM_ALL (团队-所有已分配)
| Operation / 操作 | ADMIN | MEMBER | Note / 说明 |
|------------------|-------|--------|-------------|
| VIEW             | ✔     | ✔      | Read-only for members / 成员只读 |
| EDIT/DELETE      | ✔     | ✖      | Only Admin can manage / 仅管理员管理 |
| CLAIM_TASK       | ✔     | ✖      | Member cannot steal tasks here / 成员不可在此抢任务 |
| ASSIGN_OTHERS    | ✔     | ✖      | Only Admin can reassign / 仅管理员改派 |

#### 4. TEAM_ASSIGNED (团队-分配给我)
| Operation / 操作 | ADMIN | MEMBER | Note / 说明 |
|------------------|-------|--------|-------------|
| EDIT_TASK        | ✔     | ✖      | Member cannot edit content / 成员不可改内容 |
| TOGGLE_COMPLETE  | ✔     | ✔      | Member can complete own tasks / 成员可完成自己任务 |
| ABANDON_TASK     | ✔     | ✔      | Member can abandon own tasks / 成员可放弃自己任务 |
| ASSIGN_OTHERS    | ✔     | ✖      | Only Admin can reassign / 仅管理员改派 |

## 📜 Operation Logs / 操作日志
- All effective team task operations (toggle complete, claim, abandon, assign others, save with changes) are validated by the Permission Center on the server and then recorded in the server console logs in a unified `[TEAM_OP]` format that includes player name, operation type, task ID, title and change details. / 所有有效的团队任务操作（完成状态切换、领取、放弃、指派他人、保存产生的实际变更）都会在服务器端先经过权限中心校验，然后以统一的 `[TEAM_OP]` 格式记录到服务器日志中，包含玩家名、操作类型、任务ID、标题以及变更详情。

## 🛠️ Development / 开发

### Build from Source / 从源码构建

```bash
# Clone the repository / 克隆仓库
git clone https://github.com/spacehouse/todoList.git
cd todoList

# Build the mod / 构建模组
./gradlew build

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
│   │   ├── ClientTaskPackets.java    # Client-side network helpers / 客户端网络辅助
│   │   └── ModMenuIntegration.java   # Mod Menu config integration / Mod Menu配置集成
│   ├── config/
│   │   └── ModConfig.java            # Configuration handling / 配置处理
│   ├── gui/
│   │   ├── TodoScreen.java           # Main GUI / 主界面
│   │   ├── TaskListWidget.java       # Task list widget / 任务列表组件
│   │   ├── ScrollBar.java            # Scroll bar widget / 滚动条组件
│   │   └── ConfigScreen.java         # HUD config GUI / HUD配置界面
│   ├── network/
│   │   └── TaskPackets.java          # Network packets / 网络包
│   ├── permission/
│   │   └── PermissionCenter.java     # Permission center / 权限中心
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

### Phase 1: MVP ✅ (Current / 当前)
- Basic task CRUD / 基础任务CRUD
- Simple GUI / 简单GUI
- Local storage / 本地存储

### Phase 2: Enhanced Features / 增强功能
- HUD display / HUD显示
- Task filtering / 任务筛选
- Priority system / 优先级系统
- Command support / 命令支持

### Phase 3: Multiplayer / 多人支持
- Server-client sync / 服务器-客户端同步
- Player data storage / 玩家数据存储
- Network packets / 网络包通信

### Phase 4: Advanced Features / 高级功能
- Task book item / 任务书物品
- Task rewards / 任务奖励
- Sign board integration / 告示牌集成
- Subtasks / 子任务

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
