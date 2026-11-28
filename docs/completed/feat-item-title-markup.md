# 实现方案：任务名称中包含游戏内物品（物品图片与物品名称）

> 状态：验证分支 `verify/1.20.1-feat-trigger-and-item-title` 开发中
> 对应 TODO.md 计划项：【feat】支持任务名称中包含游戏内物品（物品图片以及物品名称）
> 更新日期：2026-09-11

## 1. 背景与目标

任务标题支持内联引用游戏内物品，渲染时展示物品图标 + 本地化名称，让"收集 铁锭 ×64"这类材料任务（尤其配合事件触发式完成、后续投影/机械动力材料清单导入）获得直观的视觉表达。

目标：

- 标题原文保持纯文本存储，**实体、H2、网络包零结构改动**（H2-only 硬约束下不动 schema）
- 图标渲染覆盖 GUI 列表 / 任务详情 / HUD 三个展示面
- 聊天与命令反馈可读（本地化名 + 悬浮 tooltip）
- 解析器零平台依赖，可全量离线测试，跨分支同步零成本

## 2. 总体方案（如何实现）

### 2.1 标记语法

```text
标题原文：收集 [item:minecraft:iron_ingot] ×64
GUI/详情：收集 [🧱铁锭] ×64          （图标 + 当前语言名称）
聊天：    收集 铁锭 ×64              （本地化名，悬浮显示物品 tooltip）
控制台：  收集 minecraft:iron_ingot ×64 （无渲染环境降级，绝不崩）
物品缺失：收集 minecraft:iron_ingot ×64 （对应模组被卸载时的降级）
```

- 标记格式：`[item:<命名空间:路径>]`，解析失败（未闭合/未知格式）按普通文本原样显示；
- 不支持嵌套与转义，v1 明确为不支持（见边界）。

### 2.2 数据流

```text
Task.title (String 原文，存储/网络原样透传)
    │
    ├─ TitleMarkupParser.parse(title)  → List<TitleSegment>   [common，纯静态]
    │       TitleSegment = TEXT(原文) | ITEM(资源ID)
    │
    ├─ 客户端渲染层（GUI 列表/详情/HUD）：按段排版，ITEM 段画图标 + 名称文本
    ├─ 聊天层：ITEM 段转本地化 Component + hover 物品 tooltip
    └─ 控制台/日志层：ITEM 段降级为原资源 ID 文本
```

关键决策：

- **名称解析以函数注入**：`TitleMarkupParser` 位于 common 且零 MC 注册表依赖，物品名称查询（`BuiltInRegistries.ITEM` + translatable）由客户端/服务端调用方注入 `Function<String, Component>`，保持架构纪律（common 不 import 注册表实现）；
- **可见长度校验**：标题长度上限按"解析后可见字符数"计算，标记本身不占用玩家字数预算（命令与 GUI 校验点同步调整）；
- **搜索匹配**：客户端过滤时把标题解析为"本地化名 + 原文"联合文本参与匹配（搜"铁锭"能命中 `[item:minecraft:iron_ingot]`）。

### 2.3 渲染分层实现

| 展示面 | 实现点 | 说明 |
|---|---|---|
| GUI 任务列表行 | `TaskListWidget` 行渲染 | 行内图标按 8~9px 内联绘制（GUI 像素密度下行高有限），段落排版替换原单串 drawString |
| GUI 任务详情区 | `TodoScreen` 详情标题 | 图标 16px + 名称，排版规则同上 |
| HUD | `TodoHudRenderer` rowVisual | 标题段模型加入图标 token；**省略号截断逻辑感知图标宽度**，不把图标截半；触发器进度段（feat 1）旁画目标物品图标复用同一链路 |
| 聊天/命令反馈 | `CommandBootstrap` 消息构造 | ITEM 段 → `Component.translatable` + `.withStyle(hover SHOW_ITEM)` |
| 控制台/日志 | 通用降级函数 | 直接拼原 ID 文本 |

### 2.4 与 feat 1（事件触发）的联动

- 触发器 ITEM_COLLECT 的 HUD 进度段直接渲染目标物品图标（同一分段渲染工具）；
- v2 双向编辑联动（触发器选物品自动插入标题标记 / 标题标记右键创建触发器）留待物品选择器完成后接入。

## 3. 分步实现计划

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase A | `TitleMarkupParser` + `TitleSegment`（common，纯逻辑）+ 离线测试 | ✅ 完成 |
| Phase B | 客户端分段渲染工具（GUI 行排版 + 图标绘制封装，兼容层处理跨版本差异） | ✅ 完成 |
| Phase C | 接入三个展示面：TaskListWidget / TodoScreen 详情 / TodoHudRenderer | 🔶 列表与 HUD 完成，详情区待做 |
| Phase D | 聊天 hover + 控制台降级 + 可见长度校验 + 搜索匹配 | 🔶 输入提示文案完成，其余待做 |
| Phase E | 离线测试 + 构建 + 游戏内手工验证 | ✅ 完成（手工验证待用户执行，见 §8） |
| Phase F | 物品搜索选择器 `ItemSelectorScreen` + 快速新增行「+」与任务行内编辑「+」插入入口 | ✅ 完成 |
| Phase G | 任务行双击内联编辑标题（详情区标题框恢复全宽，取消原「+」按钮） | ✅ 完成 |

## 4. 边界（本期不做什么）

- ~~不做物品选择器 Screen 与输入自动补全（v2，与 feat 1 触发器编辑器合并实现）~~ 物品选择器已提前实施（Phase F）；输入框内的实时自动补全仍不做；
- 不做嵌套标记、转义符（`]` 出现在标记内直接判定为非法标记并按原文显示）；
- 不改标题存储结构（不存 JSON/结构化分段，原文即真相）；
- 不做富文本（颜色/加粗等），仅物品引用一种内联元素；
- 聊天降级不做图标（MC 聊天不支持图片，只用 hover tooltip 补偿）；
- 不为已废弃的 NBT 文件存储模式做适配（H2-only 硬约束，本特性本就不触碰存储）。

## 5. 难点与对策

1. **行排版与截断**：图标是有宽度的 inline token，现有省略号截断按纯文本宽度计算，可能把图标截半或错位。→ 对策：分段排版器按"段"测量，截断时保证 token 完整性（图标后至少跟 1 字符才允许截断），复用现有 ellipsis 宽度预留逻辑。
2. **HUD 行高受限**：HUD 是紧凑列表，16px 图标会撑破行高。→ 对策：HUD 行内用 8~9px 缩放绘制（`renderItem` 支持 GUI 缩放上下文），或仅详情/列表用 16px、HUD 用小图；以实测不破版为准。
3. **跨版本渲染 API 差异**：1.20.1 与 1.21.x 的 `GuiGraphics`/矩阵栈细节不同。→ 对策：图标绘制封装进单一渲染工具类，解析器与分段模型留 common，分支同步时渲染层各自微调。
4. **性能**：列表每行每帧解析标题。→ 对策：解析结果按 `(title, locale)` 缓存在渲染工具内（LRU 或随 widget 生命周期），列表规模（百级）下无压力。
5. **本地化名获取**：common 不能依赖注册表。→ 对策：名称解析函数注入（见 2.2），客户端注入注册表实现，测试注入固定映射。
6. **物品缺失**：标记对应模组被卸载。→ 对策：降级显示原 ID 文本，不隐藏任务、不抛异常、不自动改写标题原文。

## 6. 备选方案对比

| 方案 | 结论 | 理由 |
|---|---|---|
| A（采用）标题内联标记 + 渲染时解析 | ✅ | 存储零改动、旧数据天然兼容（无标记即纯文本）、解析器可全量离线测试 |
| B Task 增加 `List<TitleSegment>` 结构化字段 | ❌ | 侵入实体/H2 schema/网络包/命令序列化全链路，旧数据迁移成本高，收益仅是"语法防呆" |
| C 标题直接存 MC Component 序列化串 | ❌ | 与现有 `String title` 全链路类型冲突，命令/GUI/搜索全要改，且存储体积膨胀 |
| D 只做文本名称不做图标 | ❌ | 达不成"物品图片"的核心诉求，后续重做成本高 |
| E 用 §/& 颜色码风格自定义转义 | ❌ | 与 MC 文本代码冲突，且表达不了"资源 ID + 图标"结构 |

## 7. 任务跟踪

- [x] `TitleSegment` 模型 + `TitleMarkupParser`（解析、非法标记降级、可见文本长度计算）
- [x] 解析器离线测试（`titleMarkupParserTest`，9 用例：合法/非法/嵌套/未闭合/超长/空标记/降级）
- [x] 客户端分段渲染工具 `ItemTitleRenderer`（段测量、截断保护、图标绘制支持、解析缓存、名称降级）
- [x] `TaskListWidget` 列表行接入（图标 + 名称内联绘制）
- [x] `TodoHudRenderer` 接入（HUD 行 9px 图标 + feat 1 触发进度目标图标复用）
- [ ] `TodoScreen` 任务详情区接入（16px 图标排版）
- [ ] 聊天 hover（SHOW_ITEM）+ 命令反馈降级
- [ ] 标题可见长度校验调整（命令 + GUI 输入）
- [ ] 客户端搜索匹配接入（本地化名 + 原文）
- [x] zh_cn/en_us 输入提示文案（快速新增 placeholder 追加「可用 + 插入物品」；详情标题 placeholder 提示双击任务行快速编辑；行内编辑新增回车/Esc 提示）
- [x] 物品搜索选择器 `ItemSelectorScreen`（ID + 本地化名双匹配，中文可搜；`includeTagWrap` 控制返回裸 ID 或 `[item:...]` 标记）
- [x] 选择器泛化为 `Kind`（物品/方块/实体/进度）供 feat 1 触发器目标复用；默认构造仍等价于物品选择器（见 `feat-trigger-completion.md` Phase J）
- [x] 详情区标题框左侧「+」插入按钮（已移除）→ 改为双击任务行内联编辑 + 行内「+」按钮（`TodoScreenWidgetBuildSupport` 恢复标题框全宽）
- [x] `TaskListWidget.getTaskRowBounds` 暴露任务行区域，供行内编辑框定位；新增回归 `shouldReturnTaskRowBoundsForInlineEditing`
- [x] `build-local-17.bat build --offline` 构建通过（随 feat 1 一并产出）并归档 `dist/`
- [ ] 游戏内手工验证清单（见 §8，待用户在游戏环境执行）

### 实施补充说明

- 1.20.1 的 `GuiGraphics` 变换需经 `pose()` 访问（`pushPose/translate/scale`），与 1.21.x 直接调用的写法不同，已封装在各自的 `drawMiniItemIcon` 中，跨分支同步时仅需微调该封装。
- `Item.getName` 在 1.20.1 需传入 `ItemStack`（`item.getName(new ItemStack(item))`），1.20.5+ 才有无参重载。

## 8. 游戏内手工验证清单

1. 快速新增输入 `收集 [item:minecraft:iron_ingot] ×64` → 列表行显示图标 + "铁锭"；
2. 详情区显示 16px 图标 + 名称；
3. HUD 未完成区该任务行内联小图标，展开/收起正常，无版式破溃；
4. 标题超长时省略号截断不出现半个图标；
5. 卸载式场景：`[item:nonexistent:foo]` → 显示原文 ID，无崩溃；
6. 聊天反馈（命令 list 输出）显示"铁锭"且悬浮出物品 tooltip；
7. 搜索"铁锭"能命中该任务；
8. 中英文语言切换后名称跟随（重新进入 GUI）；
9. 旧存档无标记任务渲染与之前完全一致（回归）；
10. 双击任务行 → 行内出现标题编辑框，输入后回车保存、Esc 取消；点击行外区域等同保存；
11. 行内编辑时点行尾「+」→ 选择器输入中文「铁锭」可命中 → 选中后光标处插入 `[item:minecraft:iron_ingot]`，回车保存后列表行立即显示图标；
12. 快速新增行「+」按钮同链路（选择器 `includeTagWrap=true`），插入后回车创建任务标题含标记；
13. 详情区标题输入框恢复全宽，不再有「+」按钮挤压宽度。
