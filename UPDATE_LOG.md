# 更新日志 - 版本 1.0.0

## 🎉 新功能：完整GUI配置系统

### ✨ 主要更新

#### 1. 实时配置界面
- **新增**: 游戏内配置界面（点击⚙按钮访问）
- **功能**: 实时调整所有GUI参数，无需重启游戏
- **验证**: 内置输入验证，防止无效配置
- **重置**: 一键恢复默认配置

#### 2. 可配置参数（20+选项）

##### 界面尺寸
- GUI宽度 (300-800px)
- GUI高度 (200-600px)
- 任务列表高度 (50-400px)
- 任务项高度 (20-50px)

##### 间距设置
- 边距 (0-50px)
- 元素间距 (0-20px)

##### 颜色主题
- 背景颜色
- 选中颜色
- 悬停颜色
- 边框颜色

#### 3. 增强的UI渲染
- 所有组件现在使用配置参数
- 颜色完全可自定义
- 响应式布局适应不同屏幕尺寸

---

## 🔧 修复问题

### Bug修复
1. ✅ 修复所有硬编码尺寸值
2. ✅ 修复TaskListWidget中的TASK_HEIGHT引用
3. ✅ 修复滚动条拖动计算
4. ✅ 修复鼠标滚轮滚动
5. ✅ 修复任务点击检测

### 代码改进
1. ✅ 重构TodoScreen.init()使用rebuildUI()
2. ✅ 添加ConfigLabel类用于配置界面标签渲染
3. ✅ 优化配置保存机制（自动保存）
4. ✅ 改进配置界面布局（更大的面板：450x380）

---

## 📝 配置文件

配置文件位置：`config/todolist.json`

### 新增配置项
```json
{
  "gui": {
    "guiWidth": 400,
    "guiHeight": 280,
    "taskListHeight": 140,
    "taskItemHeight": 25,
    "padding": 10,
    "elementSpacing": 5,
    "backgroundColor": 4278190080,
    "selectedBackgroundColor": 4286611584,
    "hoveredBackgroundColor": 4286515715,
    "borderColor": 4287533439
  }
}
```

---

## 🎮 使用方法

### 基本操作
1. 按 `K` 打开待办列表
2. 点击 `⚙` 按钮打开配置
3. 调整参数
4. 点击 `保存并应用` 查看效果

### 快速修复常见问题

#### 问题：列表显示太少任务
```
列表高度: 180
任务项高度: 22
```

#### 问题：界面太拥挤
```
GUI高度: 350
间距: 8
```

#### 问题：文字太小
```
任务项高度: 32
```

---

## 🔨 技术细节

### 构建信息
- **Gradle版本**: 8.1.1
- **Java版本**: 17
- **Fabric Loader**: 0.14.21
- **Fabric API**: 0.86.1+1.20.1
- **Minecraft版本**: 1.20.1

### 新增文件
- `src/main/java/com/todolist/gui/ConfigScreen.java` - 配置界面
- `配置系统使用指南.md` - 完整配置文档

### 修改文件
- `src/main/java/com/todolist/config/ModConfig.java` - 扩展配置选项
- `src/main/java/com/todolist/gui/TodoScreen.java` - 添加配置按钮和rebuildUI()
- `src/main/java/com/todolist/gui/TaskListWidget.java` - 使用配置参数

---

## 🐛 已知问题

暂无

---

## 📋 下一步计划

### 短期（Phase 1.1）
- [ ] 添加预设主题选择器
- [ ] 实现配置导入/导出功能
- [ ] 添加颜色预览窗口

### 中期（Phase 2）
- [ ] HUD显示功能
- [ ] 游戏内命令系统
- [ ] 任务提醒通知

### 长期（Phase 3-4）
- [ ] 多人游戏网络同步
- [ ] 任务书物品
- [ ] 任务奖励系统

---

## 💬 反馈

如果您遇到任何问题或有建议，请：
- 📝 提交 [GitHub Issue](https://github.com/todolist-mod/todolist-fabric/issues)
- 💬 加入 [Discord](https://discord.gg/todolist)

---

**版本**: 1.0.0
**发布日期**: 2026-02-10
**兼容性**: Minecraft 1.20.1 + Fabric
