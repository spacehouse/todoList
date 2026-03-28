# TodoList 命令系统测试清单

## 使用方式
- 日常验收时，通常只需要关注下面的“手动测试重点”。
- 实际执行时，优先直接填写对应平台的实测文档：
  - Fabric：[command-test-checklist-fabric.md](E:/AI/Todolist-versions/1.21.1/todoList/.trae/test/command-test-checklist-fabric.md)
  - Forge：[command-test-checklist-forge.md](E:/AI/Todolist-versions/1.21.1/todoList/.trae/test/command-test-checklist-forge.md)
- 命令解析、权限、分页、持久化等大部分逻辑已经有自动化覆盖，放在文末做摘要说明，不再展开成长表。
- 发现问题时，直接记录到文末“问题汇总”。

## 手动测试重点
这一节是默认入口。只要不是在补自动化，一般直接从这里开始看。

### 1. HUD 与界面观感
| 编号 | 场景 | 操作 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| H-01 | HUD 显示 | 进入单机世界 | HUD 正常显示 |  |  |  |
| H-02 | HUD 显示 | 切换 `current` 项目 | HUD 内容同步变化 |  |  |  |
| H-03 | HUD 显示 | 设置或取消星标项目 | HUD 与项目来源显示正确 |  |  |  |
| H-04 | HUD 显示 | 查看空态、翻页后状态 | 无残留、无错乱、文本可读 |  |  |  |
| H-05 | HUD 开关 | 执行 `/todo hud set off` 后再执行 `/todo hud set on` | HUD 开关行为正确 |  |  |  |

### 2. 聊天按钮体验
| 编号 | 场景 | 操作 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| C-01 | 分页按钮 | 点击 `task more` / `task prev` | 正常翻页并返回上一页 |  |  |  |
| C-02 | 清理确认 | 点击 `task clean confirm` | 只清理目标任务，且只触发一次 |  |  |  |
| C-03 | 项目删除确认 | 点击 `project remove confirm` | 只删除目标项目，且只触发一次 |  |  |  |
| C-04 | 加入审批按钮 | 点击 `join accept` / `join deny` | 审批结果正确触发 |  |  |  |
| C-05 | 交互文案 | 检查按钮文本与 hover | 文案清楚，不歧义 |  |  |  |

### 3. 双端联机消息时序
| 编号 | 场景 | 操作 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| M-01 | 加入申请 | B 执行 `join project` | A/B 两边提示都正常 |  |  |  |
| M-02 | 申请审批 | A 执行 `join accept` / `join deny` | 双方消息时序正确、可读 |  |  |  |
| M-03 | 团队任务操作 | 执行 `assignp / claimp / donep / abandonp / removep` | 双端消息清晰，无重复无乱序 |  |  |  |

### 4. 真实重启后的客户端观感
| 编号 | 场景 | 操作 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| R-01 | 真实重启 | 完整退出并重进世界 | HUD、当前项目、星标项目、聊天提示观感正常 |  |  |  |

## 自动化覆盖摘要
这一节只保留摘要，方便确认哪些内容已经不用再手工回归。

### 已自动化覆盖
- 命令基础流程：`help`、项目创建/选择/重命名/删除、个人任务增删改查
- 分页与会话：`task more`、`task prev`、无会话/第一页/最后一页、跨查询切换、团队项目间切换
- 项目状态：`project star/unstar`、`project list current/star` 空态与幂等分支
- 成员管理：`project member add/remove/role`、`member-create`、自删/自改角色边界
- 加入流程：`join project/accept/deny`、拒绝后访问限制、加入后分页
- 权限控制：`VIEW_ONLY`、`OP_ONLY`、管理员访问控制
- 团队任务：`addp/listp/assignp/claimp/donep/abandonp/removep` 及权限矩阵
- 环境组合：单机、局域网、专用服下团队项目相关限制与状态净化
- 持久化：项目、任务、星标、当前项目、`member-create`、成员角色变化后的重载一致性

### 自动化自检命令
| 编号 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|
| A-01 | `.\build-with-java17.bat --offline :common:commandSystemTest` | `commandSystemTest` 通过 |  |  |  |

## 问题汇总
| 编号 | 现象 | 复现步骤 | 实际结果 | 预期结果 | 严重程度 | 备注 |
|---|---|---|---|---|---|---|
| BUG-01 |  |  |  |  |  |  |
