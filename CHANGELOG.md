# 更新日志

## [1.0.0] - 2026-02-10

### ✅ 新增功能

#### 核心功能
- **任务管理系统**
  - 创建任务（标题 + 描述）
  - 编辑现有任务
  - 删除任务
  - 标记完成/未完成
  - 任务选择和高亮显示

#### 用户界面
- **主GUI界面**
  - 按K键打开待办列表
  - 任务列表显示
  - 输入框（标题和描述）
  - 操作按钮（添加、编辑、删除、完成、保存）
  - 筛选按钮（全部、进行中、已完成）

#### 数据持久化
- **存储系统**
  - NBT格式保存到本地文件
  - 自动保存（关闭GUI时）
  - 手动保存按钮
  - 启动时加载已保存任务

#### 交互功能
- **鼠标操作**
  - 鼠标滚轮滚动任务列表
  - 拖动滚动条
  - 点击选择任务

- **键盘快捷键**
  - `K` - 打开/关闭待办列表
  - `Enter` - 添加任务
  - `Esc` - 关闭界面

#### 多语言支持
- 中文界面
- 英文界面

### 🐛 Bug修复

#### UI问题
- ✅ 修复标题与筛选按钮重叠
- ✅ 修复完成按钮与任务列表重叠
- ✅ 添加保存按钮

#### 功能问题
- ✅ 实现鼠标滚轮滚动
- ✅ 实现滚动条拖动
- ✅ 实现保存列表功能（手动+自动）

#### 其他修复
- ✅ 修复客户端入口点路径错误
- ✅ 添加缺失的导入语句

### 📁 文件变更

#### 新增文件
- `FEATURES_COMPLETED.md` - 已完成功能清单
- `FEATURES_TODO.md` - 待开发功能清单
- `CHANGELOG.md` - 本文件
- `Bugs.md` - Bug修复记录（已更新）

#### 修改文件
- `src/main/java/com/todolist/gui/TodoScreen.java`
  - 修复UI布局
  - 添加保存功能
  - 添加鼠标拖动支持
  - 添加自动保存和加载

- `src/main/java/com/todolist/gui/TaskListWidget.java`
  - 实现鼠标滚轮滚动
  - 实现滚动条拖动
  - 添加拖动状态管理

- `src/main/resources/fabric.mod.json`
  - 修复客户端入口点路径

### 🔧 技术细节

#### UI布局调整
```java
// 标题位置: y+10 → y-5
int titleY = (height - guiHeight) / 2 - 5;

// 列表widget
// 位置: y+40 → y+35
// 高度: guiHeight-100 → guiHeight-110
taskListWidget = new TaskListWidget(..., x + 10, y + 35, guiWidth - 20, guiHeight - 110);

// 完成按钮位置: y+guiHeight-80 → y+guiHeight-85
// 保存按钮位置: y+guiHeight-85
```

#### 滚动功能实现
```java
// 鼠标滚轮
public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)

// 滚动条拖动
public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)
public boolean mouseReleased(double mouseX, double mouseY, int button)
```

#### 保存功能实现
```java
// 自动保存（关闭时）
@Override
public void close() {
    TodoListMod.getTaskStorage().saveTasks(taskManager.getAllTasks());
}

// 手动保存
private void onSaveTasks() {
    TodoListMod.getTaskStorage().saveTasks(taskManager.getAllTasks());
}

// 加载任务（初始化时）
List<Task> loadedTasks = TodoListMod.getTaskStorage().loadTasks();
```

### 📊 代码统计
- 新增代码行数: ~200
- 修改代码行数: ~150
- 修复Bug数量: 8

### 🎯 下一步计划

查看 `FEATURES_TODO.md` 了解计划中的功能。

---

## 测试检查清单

### UI测试
- [ ] 标题显示正确，不与按钮重叠
- [ ] 完成按钮不与列表重叠
- [ ] 保存按钮正常显示
- [ ] 所有按钮对齐正确

### 功能测试
- [ ] 可以添加新任务
- [ ] 可以编辑任务
- [ ] 可以删除任务
- [ ] 可以标记完成/未完成
- [ ] 可以筛选任务（全部/进行中/已完成）
- [ ] 鼠标滚轮可以滚动
- [ ] 滚动条可以拖动
- [ ] 点击任务可以选中
- [ ] 保存按钮可以保存任务
- [ ] 关闭GUI时自动保存
- [ ] 重新打开时任务已加载

### 兼容性测试
- [ ] 单人模式正常工作
- [ ] 任务数据正确保存
- [ ] 多次开关GUI数据不丢失

---

*最后更新: 2026-02-10*
*构建版本: 1.0.0*
*Minecraft版本: 1.20.1*
*Mod加载器: Fabric 0.14.21+*
