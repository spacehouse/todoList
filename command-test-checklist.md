# TodoList 命令系统测试清单

## 基本信息
- 测试日期：
- 测试版本：
- 测试环境：
- JDK：
- 是否单机：
- 是否联机：
- 测试人：

## 使用说明
1. 先执行自动化离线自检，再进行手工测试。
2. 建议按“单机 -> 联机双人 -> 权限回归 -> 重启持久化”的顺序执行。
3. 执行 `project list` 或 `task list` 后，把实际生成的项目 ID、任务 ID 记录到备注中，后续步骤直接复用。
4. 文中用到的占位符含义：
   `P1`：个人项目 ID
   `T1`：团队项目 ID
   `T2`：第二个团队项目 ID
   `<任务ID>`：对应任务 ID
   `<B_UUID>`：玩家 B 的 UUID

## 自动化验证
| 编号 | 步骤 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| A-01 | 运行离线命令自检 | `.\build-with-java17.bat --offline :common:commandSystemTest` | 构建成功，`commandSystemTest` 通过 |  |  |  |

## 单机功能回归
| 编号 | 步骤 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| S-01 | 查看帮助 | `/todo help` | 显示帮助信息 |  |  |  |
| S-02 | 创建个人项目 | `/todo project create personal My Personal Project` | 创建成功 |  |  |  |
| S-03 | 查看项目列表 | `/todo project list all` | 能看到新项目并记录项目 ID 为 `P1` |  |  |  |
| S-04 | 选择项目 | `/todo project select P1` | 当前项目切换成功 |  |  |  |
| S-05 | 新增个人任务 1 | `/todo task add "Write docs" "Prepare release notes" docs,release` | 创建成功 |  |  |  |
| S-06 | 新增个人任务 2 | `/todo task add "Fix typo" "README cleanup" docs` | 创建成功 |  |  |  |
| S-07 | 查看全部个人任务 | `/todo task list` | 显示刚创建的任务 |  |  |  |
| S-08 | 按状态/优先级/文本筛选 | `/todo task list incomplete medium Write` | 只显示匹配任务 |  |  |  |
| S-09 | 按项目查看任务 | `/todo task listp P1` | 只显示项目 `P1` 下任务 |  |  |  |
| S-10 | 星标项目 | `/todo project star P1` | 星标成功 |  |  |  |
| S-11 | 查看星标项目 | `/todo project list star` | 能看到 `P1` |  |  |  |
| S-12 | 关闭 HUD | `/todo hud set off` | HUD 关闭 |  |  |  |
| S-13 | 开启 HUD | `/todo hud set on` | HUD 开启 |  |  |  |
| S-14 | 完成任务 | `/todo task done <任务ID>` | 任务标记完成 |  |  |  |
| S-15 | 查看已完成任务 | `/todo task list completed` | 能看到已完成任务 |  |  |  |
| S-16 | 请求清理已完成任务 | `/todo task clean personal current completed` | 只提示确认，不立即删除 |  |  |  |
| S-17 | 确认清理 | `/todo task clean confirm` | 删除命中任务，未命中任务保留 |  |  |  |
| S-18 | 重命名项目 | `/todo project rename P1 My Personal Project Renamed` | 重命名成功 |  |  |  |
| S-19 | 请求删除项目 | `/todo project remove P1` | 提示确认 |  |  |  |
| S-20 | 确认删除项目 | `/todo project remove confirm` | 项目删除，关联任务清理 |  |  |  |

## 联机双人功能回归
| 编号 | 角色 | 步骤 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|---|
| M-01 | A | 创建团队项目 | `/todo project create team Team Alpha` | 创建成功 |  |  |  |
| M-02 | A | 查看项目列表并记录 ID | `/todo project list all` | 能看到团队项目并记录项目 ID 为 `T1` |  |  |  |
| M-03 | A | 开启成员创建任务 | `/todo project member-create T1 on` | 设置成功 |  |  |  |
| M-04 | B | 申请加入团队项目 | `/todo join project T1` | B 看到申请已发送，A 收到审批消息 |  |  |  |
| M-05 | A | 同意加入申请 | `/todo join accept T1 <B_UUID>` | A 审批成功，B 加入项目 |  |  |  |
| M-06 | A | 提升 B 为 lead | `/todo project member role T1 <B_UUID> lead` | 角色修改成功 |  |  |  |
| M-07 | A | 降回 B 为 member | `/todo project member role T1 <B_UUID> member` | 角色修改成功 |  |  |  |
| M-08 | A | 新增团队任务 | `/todo task addp T1 "Team task one" "Prepare sync" sync,meeting` | 创建成功 |  |  |  |
| M-09 | B | 查看团队任务 | `/todo task listp T1` | 能看到团队任务 |  |  |  |
| M-10 | B | 领取团队任务 | `/todo task claimp T1 <任务ID>` | 领取成功 |  |  |  |
| M-11 | B | 完成团队任务 | `/todo task donep T1 <任务ID>` | 完成成功 |  |  |  |
| M-12 | A | 查看已完成团队任务 | `/todo task listp T1 completed` | 能看到已完成任务 |  |  |  |
| M-13 | A | 新增第二个团队任务 | `/todo task addp T1 "Team task two" "Follow up" followup` | 创建成功 |  |  |  |
| M-14 | A | 指派团队任务给 B | `/todo task assignp T1 <任务ID> <B_UUID>` | 指派成功 |  |  |  |
| M-15 | B | 放弃团队任务 | `/todo task abandonp T1 <任务ID>` | 放弃成功，指派清空 |  |  |  |
| M-16 | A | 删除团队任务 | `/todo task removep T1 <任务ID>` | 删除成功 |  |  |  |
| M-17 | A | 移除 B 成员 | `/todo project member remove T1 <B_UUID>` | 移除成功 |  |  |  |

## 加入申请拒绝流程
| 编号 | 角色 | 步骤 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|---|
| J-01 | A | 创建第二个团队项目 | `/todo project create team Team Beta` | 创建成功 |  |  |  |
| J-02 | B | 发起加入申请 | `/todo join project T2` | A 收到申请消息 |  |  |  |
| J-03 | A | 拒绝加入申请 | `/todo join deny T2 <B_UUID>` | B 收到拒绝消息，未加入项目 |  |  |  |
| J-04 | B | 验证不可访问 | `/todo task listp T2` | 无法访问或看不到项目 |  |  |  |

## 权限回归
| 编号 | 配置 | 角色 | 步骤 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|---|---|
| P-01 | `VIEW_ONLY` | 普通玩家 | 新增任务 | `/todo task add "Denied task" "x" test` | 被拒绝 |  |  |  |
| P-02 | `VIEW_ONLY` | 普通玩家 | 重命名项目 | `/todo project rename P1 X` | 被拒绝 |  |  |  |
| P-03 | `VIEW_ONLY` | 普通玩家 | 确认清理 | `/todo task clean confirm` | 被拒绝 |  |  |  |
| P-04 | `VIEW_ONLY` | 普通玩家 | 查看任务 | `/todo task list` | 可查看 |  |  |  |
| P-05 | `OP_ONLY` | 非 OP | 新增任务 | `/todo task add "Denied task" "x" test` | 被拒绝 |  |  |  |
| P-06 | `OP_ONLY` | OP | 新增任务 | `/todo task add "Allowed task" "x" test` | 成功 |  |  |  |

## 异常参数与错误处理
| 编号 | 步骤 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| E-01 | 非法 HUD 参数 | `/todo hud set maybe` | 报错，不改状态 |  |  |  |
| E-02 | 非法成员角色 | `/todo project member role T1 <UUID> manager` | 报错 |  |  |  |
| E-03 | 非法指派对象 | `/todo task assignp T1 <任务ID> ghost-user` | 报错 |  |  |  |
| E-04 | 无待确认项目删除 | `/todo project remove confirm` | 报确认过期或无待确认请求 |  |  |  |
| E-05 | 无待确认任务清理 | `/todo task clean confirm` | 报确认过期或无待确认请求 |  |  |  |
| E-06 | 不存在团队任务 | `/todo task claimp T1 missing-task` | 报 not_found |  |  |  |

## 重启持久化回归
| 编号 | 步骤 | 命令 | 预期 | 实际 | 是否通过 | 备注 |
|---|---|---|---|---|---|---|
| R-01 | 重启前准备数据 | 多条任务/项目操作 | 数据已写入 |  |  |  |
| R-02 | 重启后查看项目 | `/todo project list all` | 项目仍存在 |  |  |  |
| R-03 | 重启后查看星标项目 | `/todo project list star` | 星标项目仍存在 |  |  |  |
| R-04 | 重启后查看个人任务 | `/todo task list` | 个人任务状态保留 |  |  |  |
| R-05 | 重启后查看团队任务 | `/todo task listp T1` | 团队任务状态保留 |  |  |  |

## 高风险专项检查
- 删除项目后，确认不会误删其他项目的任务。
- `task clean confirm` 只删除当前筛选命中的任务。
- 团队任务的 `claimp/abandonp/assignp/donep/removep` 必须符合角色权限。
- `join accept/deny` 后，申请人和审批人都能收到正确提示。
- 单机未开放局域网时，团队项目相关命令应受限。

## 问题汇总
| 编号 | 现象 | 复现步骤 | 实际结果 | 预期结果 | 严重程度 | 备注 |
|---|---|---|---|---|---|---|
| BUG-01 |  |  |  |  |  |  |
