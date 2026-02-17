# Todo List Mod for Minecraft

一个简单而强大的 Minecraft 待办事项模组，支持单人和多人模式。

A simple and powerful todo list mod for Minecraft, supporting both single-player and multiplayer.

## 🌟 Features / 功能

### ✅ Implemented / 已实现 (v1.0.0+)
- ✅ **Task Management** - Add, edit, delete, and complete tasks / 任务管理（增删改查）
- ✅ **Task Filtering** - View all, active, or completed tasks / 任务筛选（全部/进行中/已完成）
- ✅ **Auto Save** - Tasks are automatically saved on close / 关闭时自动保存
- ✅ **Manual Save** - Save button to manually save tasks / 手动保存按钮
- ✅ **Scrollable List** - Mouse wheel and draggable scrollbar / 可滚动列表（滚轮+拖动）
- 🎨 **Priority Levels** - Low, Medium, High with color coding / 优先级（低中高，颜色区分）
- 🏷️ **Tags** - Basic tag support with colored labels in GUI & HUD / 基础标签支持（GUI与HUD显示彩色标签）
- 🎮 **In-Game GUI** - Easy-to-use interface / 游戏内GUI界面
- 💾 **Persistent Storage** - Tasks saved to local file / 本地文件持久化
- ⌨️ **Keyboard Shortcuts** - K to open, Enter to add, Esc to close / 键盘快捷键
- 🌍 **Multi-Language** - English and Chinese / 多语言支持
- 📊 **HUD Display** - On-screen todo list with expand/collapse / HUD待办列表显示（按 H 展开/收起）
- 👥 **Team Tasks** - Personal & team views with basic multiplayer sync / 个人与团队任务视图（基础多人同步）
- ⚙️ **HUD Config GUI** - In-game HUD width/height/limit/default view settings / HUD配置界面（宽度、高度、条数、默认视图等）

### 🚧 Planned / 计划中
- 🏷️ **Advanced Tagging & Categories** - Rich tag filters and categories / 高级标签与分类
- 💬 **Commands** - `/todo` command system / 命令系统
- 🌐 **Advanced Multiplayer Support** - More powerful team workflows and permissions / 更完善的多人与团队权限系统
- 📅 **Due Dates** - Set task deadlines / 截止日期
- 🎯 **Subtasks** - Break down tasks into smaller parts / 子任务

## 📸 Screenshots / 截图

![image-20260217175152509](https://qiniuyun.emptycity.top/typora-img/image-20260217175152509.png)

![image-20260217175159300](https://qiniuyun.emptycity.top/typora-img/image-20260217175159300.png)

![image-20260217175204764](https://qiniuyun.emptycity.top/typora-img/image-20260217175204764.png)

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

## 🛠️ Development / 开发

### Build from Source / 从源码构建

```bash
# Clone the repository / 克隆仓库
git clone https://github.com/todolist-mod/todolist-fabric.git
cd todolist-fabric

# Build the mod / 构建模组
./gradlew build

# The JAR will be in build/libs/ / JAR文件位于build/libs/目录
```

### Project Structure / 项目结构

```
todolist-fabric/
├── src/main/java/com/todolist/
│   ├── TodoListMod.java          # Main mod class / 主类
│   ├── client/
│   │   └── TodoClient.java       # Client initialization / 客户端初始化
│   ├── config/
│   │   └── ModConfig.java        # Configuration handling / 配置处理
│   ├── gui/
│   │   ├── TodoScreen.java       # Main GUI / 主界面
│   │   └── TaskListWidget.java   # Task list widget / 任务列表组件
│   ├── network/
│   │   └── TaskPackets.java      # Network packets / 网络包
│   └── task/
│       ├── Task.java             # Task entity / 任务实体
│       ├── TaskManager.java      # Task manager / 任务管理器
│       └── TaskStorage.java      # Data persistence / 数据持久化
└── src/main/resources/
    ├── assets/todolist/
    │   └── lang/                 # Language files / 语言文件
    └── fabric.mod.json           # Mod metadata / 模组元数据
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
