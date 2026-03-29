# Todo List Mod for Minecraft

一个简单而实用的 Minecraft 待办事项模组，支持单人、本地局域网和多人服务器协作。

A simple and practical todo list mod for Minecraft, built for single-player, LAN, and multiplayer collaboration.

## Features / 功能

### v1.1.4 Capability Overview / 1.1.4 版本能力概览
- In-game GUI task management: create, edit, delete, complete, search, filter, and organize tasks by priority and tags / 游戏内 GUI 任务管理：支持创建、编辑、删除、完成、搜索、筛选，以及按优先级和标签组织任务
- Personal and team projects: switch between personal and team workspaces with project-based task organization / 个人与团队项目：基于项目维度管理任务，可在个人项目和团队项目之间切换
- HUD display and sync: configurable HUD list with improved layout, view sync, and visibility synchronization / HUD 显示与同步：可配置 HUD 列表，支持布局优化、视图同步与显示状态同步
- LAN and multiplayer collaboration: better team project availability, team data sync, and personal task recovery in LAN sessions / 局域网与多人协作：优化团队项目可用性、团队数据同步，以及 LAN 场景下的个人任务恢复
- Assignment and permissions: support claim, abandon, assign others, and role-based team permissions / 指派与权限：支持领取、放弃、指派他人，以及基于角色的团队权限控制
- Local singleplayer isolation: personal task storage is isolated more cleanly in local singleplayer environments / 本地单人隔离：本地单人环境下的个人任务存储隔离更加清晰稳定

More details / 更多说明：
- [FEATURES.md](FEATURES.md) / [FEATURES_EN.md](FEATURES_EN.md)
- [ROADMAP.md](ROADMAP.md) / [ROADMAP_EN.md](ROADMAP_EN.md)
- [CHANGELOG.md](CHANGELOG.md) / [CHANGELOG_EN.md](CHANGELOG_EN.md)

## Screenshots / 截图

### Singleplayer / 单人游戏

![singleplayer-1](https://qiniuyun.emptycity.top/typora-img/image-20260308182353464.png)
![singleplayer-2](https://qiniuyun.emptycity.top/typora-img/image-20260308182431099.png)

### Multiplayer / 多人游戏

#### Op View / 管理员视角

![multiplayer-op-1](https://qiniuyun.emptycity.top/typora-img/image-20260308182538945.png)
![multiplayer-op-2](https://qiniuyun.emptycity.top/typora-img/image-20260308182634208.png)
![multiplayer-op-3](https://qiniuyun.emptycity.top/typora-img/image-20260308182736610.png)
![multiplayer-op-4](https://qiniuyun.emptycity.top/typora-img/image-20260308182916689.png)

#### Player View / 玩家视角

![multiplayer-player-1](https://qiniuyun.emptycity.top/typora-img/image-20260308182825917.png)
![multiplayer-player-2](https://qiniuyun.emptycity.top/typora-img/image-20260308182952758.png)
![multiplayer-player-3](https://qiniuyun.emptycity.top/typora-img/image-20260308183029983.png)
![multiplayer-player-4](https://qiniuyun.emptycity.top/typora-img/image-20260308183053439.png)

## Installation / 安装

### Requirements / 要求
- Minecraft 1.20.1
- Fabric Loader 0.15.11+
- Fabric API 0.86.1+1.20.1

### Steps / 步骤
1. Download the latest mod JAR / 下载最新的模组 JAR 文件
2. Put it into your `mods` folder / 将文件放入 `mods` 文件夹
3. Launch Minecraft / 启动 Minecraft
4. Press `K` in game to open the todo list / 在游戏中按 `K` 打开待办列表

## Usage / 使用说明

### Key Bindings / 按键绑定
- `K`: Open Todo List / 打开待办列表
- `H`: Toggle HUD / 展开或收起 HUD

### Projects / 项目
- Tasks belong to the currently selected project in the left sidebar / 任务归属于左侧栏当前选中的项目
- Switch between personal and team projects from the sidebar header / 可在侧边栏顶部切换个人项目和团队项目
- Starred projects are sorted first and can be used as HUD sources / 星标项目会优先排序，也可作为 HUD 项目来源
- Add, edit, and delete projects from the sidebar actions / 可通过侧边栏按钮新增、编辑和删除项目

### Team Collaboration / 团队协作
- Team views include Unassigned, All Assigned, and Assigned to Me / 团队视图包含待分配、全部已分配和分配给我
- Team changes take effect after clicking Save, and the client will re-sync from server after Save or Cancel / 团队改动需要点击保存后生效，保存或取消后客户端会重新从服务端同步
- The Assign Others dialog now lists project members instead of online players, and offline members can still be assigned / “指派他人”弹窗现在展示项目成员而不是在线玩家，离线成员也可被正常指派
- Team permissions are role-based: managers and leads can fully manage team tasks, while members focus on claiming and completing their own tasks / 团队权限基于角色控制：管理者和负责人可完整管理团队任务，成员主要负责领取和完成自己的任务

### HUD Config / HUD 配置
- Configure width, height, visible item count, default expanded state, opacity, and project source / 可配置宽度、高度、显示条数、默认展开状态、透明度与项目来源
- The HUD list follows current view filters and visibility state more consistently / HUD 列表会更一致地跟随当前视图筛选和显示状态
- In true singleplayer, team HUD views stay hidden and personal view remains the default / 在真正的单人环境中，团队 HUD 视图保持隐藏，默认视图固定为个人视图

## Permission System / 权限系统

- `OP`: server operators / 服务器管理员
- `PROJECT_MANAGER`: project owner or manager / 项目经理或创建者
- `LEAD`: elevated team lead / 团队负责人
- `MEMBER`: regular project member / 普通项目成员

The server checks team operations based on role, current view, task assignment, and project membership. Effective team operations are recorded in a unified `[TEAM_OP]` log format.

服务端会基于角色、当前视图、任务归属和项目成员身份校验团队操作，所有生效的团队操作都会以统一的 `[TEAM_OP]` 格式记录到日志中。

## Development / 开发

### Build from Source / 从源码构建

Requires Java 17+ / 需要 Java 17+

```bash
git clone https://github.com/spacehouse/todoList.git
cd todoList
./gradlew.bat --offline build
```

### Release / 发布

- Releases are published by pushing a Git tag that starts with `v`, for example `v1.1.4` / 发布通过推送以 `v` 开头的 Git 标签触发，例如 `v1.1.4`
- The release workflow uses the changelog as the main release note source / 发布工作流会以变更日志作为主要发布说明来源

## Roadmap / 路线图

- [ROADMAP.md](ROADMAP.md) / [ROADMAP_EN.md](ROADMAP_EN.md)

## Contributing / 参与贡献

Contributions are welcome. Feel free to open an issue or submit a pull request.

欢迎贡献内容，提交 Issue 或 Pull Request 都很欢迎。

## License / 许可证

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE)。

## Contact / 联系方式

- GitHub Issues: [Report bugs](https://github.com/spacehouse/todoList/issues)
- Discord: [Join our server](https://discord.gg/uYHw9MHNe)
