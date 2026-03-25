# TodoList 命令系统发版前最小冒烟清单

## 目标
- 用最少步骤快速确认命令系统核心能力可用。
- 建议每次准备发布、合并大改动、调整权限逻辑后执行一遍。

## 环境建议
- 先在本地执行一次自动化离线自检。
- 再至少做一轮单机冒烟。
- 如果这次改动涉及团队命令、权限或同步，再补一轮双人联机冒烟。

## 一、自动化冒烟
| 编号 | 步骤 | 命令 | 通过标准 |
|---|---|---|---|
| R-01 | 运行离线命令自检 | `.\build-with-java17.bat --offline :common:commandSystemTest` | 构建成功，测试通过 |

## 二、单机最小冒烟
| 编号 | 步骤 | 命令 | 通过标准 |
|---|---|---|---|
| R-02 | 查看帮助 | `/todo help` | 帮助正常显示 |
| R-03 | 创建个人项目 | `/todo project create personal Smoke Personal` | 创建成功 |
| R-04 | 查看并记录个人项目 ID | `/todo project list all` | 能看到新项目 |
| R-05 | 选择个人项目 | `/todo project select P1` | 切换成功 |
| R-06 | 新增个人任务 | `/todo task add "Smoke task" "Smoke desc" smoke,test` | 创建成功 |
| R-07 | 查看个人任务 | `/todo task list` | 能看到刚创建的任务 |
| R-08 | 完成个人任务 | `/todo task done <任务ID>` | 完成成功 |
| R-09 | 查看已完成任务 | `/todo task list completed` | 能看到已完成任务 |
| R-10 | HUD 开关 | `/todo hud set off` 然后 `/todo hud set on` | HUD 状态可切换 |
| R-11 | 星标项目 | `/todo project star P1` | 星标成功 |
| R-12 | 查看星标项目 | `/todo project list star` | 能看到 `P1` |

## 三、双人联机最小冒烟
| 编号 | 角色 | 步骤 | 命令 | 通过标准 |
|---|---|---|---|---|
| R-13 | A | 创建团队项目 | `/todo project create team Smoke Team` | 创建成功 |
| R-14 | A | 查看并记录团队项目 ID | `/todo project list all` | 能看到团队项目 `T1` |
| R-15 | B | 申请加入团队项目 | `/todo join project T1` | 申请发送成功 |
| R-16 | A | 同意加入 | `/todo join accept T1 <B_UUID>` | B 成功加入 |
| R-17 | A | 新增团队任务 | `/todo task addp T1 "Smoke team task" "sync" smoke` | 创建成功 |
| R-18 | B | 领取团队任务 | `/todo task claimp T1 <任务ID>` | 领取成功 |
| R-19 | B | 完成团队任务 | `/todo task donep T1 <任务ID>` | 完成成功 |
| R-20 | A | 查看团队已完成任务 | `/todo task listp T1 completed` | 能看到任务已完成 |

## 四、权限最小冒烟
| 编号 | 配置 | 角色 | 命令 | 通过标准 |
|---|---|---|---|---|
| R-21 | `VIEW_ONLY` | 普通玩家 | `/todo task add "Denied" "x" t` | 被拒绝 |
| R-22 | `VIEW_ONLY` | 普通玩家 | `/todo task list` | 仍可查看 |
| R-23 | `OP_ONLY` | 非 OP | `/todo project rename P1 X` | 被拒绝 |
| R-24 | `OP_ONLY` | OP | `/todo project rename P1 Y` | 可以成功 |

## 五、异常参数冒烟
| 编号 | 步骤 | 命令 | 通过标准 |
|---|---|---|---|
| R-25 | 非法 HUD 参数 | `/todo hud set maybe` | 返回错误，不改状态 |
| R-26 | 非法成员角色 | `/todo project member role T1 <UUID> manager` | 返回错误 |
| R-27 | 非法指派对象 | `/todo task assignp T1 <任务ID> ghost-user` | 返回错误 |

## 六、持久化冒烟
| 编号 | 步骤 | 操作 | 通过标准 |
|---|---|---|---|
| R-28 | 重启前保留数据 | 保留至少一个个人项目、一个团队项目、一条完成任务 | 数据存在 |
| R-29 | 重启后查看个人项目 | `/todo project list all` | 项目仍存在 |
| R-30 | 重启后查看任务 | `/todo task list` 和 `/todo task listp T1` | 任务状态仍正确 |

## 发版结论
- 自动化离线自检：通过 / 不通过
- 单机最小冒烟：通过 / 不通过
- 联机最小冒烟：通过 / 不通过
- 权限最小冒烟：通过 / 不通过
- 持久化最小冒烟：通过 / 不通过

## 备注
- 如果 `R-13` 到 `R-20` 没做，则本次发布默认不覆盖团队联机能力。
- 如果本次改动涉及 `join`、`member`、`assignp`、`claimp`、`donep`、`permission` 等逻辑，建议不要跳过联机冒烟。
