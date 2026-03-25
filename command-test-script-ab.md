# TodoList 命令系统双人联机执行脚本

## 使用说明
- 本脚本适用于两名测试玩家协作执行，分别记为玩家 A、玩家 B。
- 建议在专用服务端或局域网联机环境下执行。
- 文中所有命令默认都在聊天栏以 `/` 开头执行。
- 每一步执行后，先确认“预期结果”再进入下一步。

## 预设信息
- 玩家 A：
- 玩家 B：
- 玩家 B UUID：
- 服务端地址：
- 测试日期：
- 测试版本：

## 阶段一：基础确认
### 1. 玩家 A
- 执行：`/todo help`
- 预期：显示帮助信息，包含 `task`、`project`、`hud`、`join` 相关命令。

### 2. 玩家 B
- 执行：`/todo help`
- 预期：显示帮助信息，和玩家 A 一致。

## 阶段二：创建团队项目
### 3. 玩家 A
- 执行：`/todo project create team Team Alpha`
- 预期：团队项目创建成功。

### 4. 玩家 A
- 执行：`/todo project list all`
- 预期：列表中出现 `Team Alpha`。
- 记录：把 `Team Alpha` 的项目 ID 记为 `T1`。

### 5. 玩家 A
- 执行：`/todo project member-create T1 on`
- 预期：开启成员创建任务成功。

## 阶段三：加入审批流程
### 6. 玩家 B
- 执行：`/todo join project T1`
- 预期：玩家 B 收到“申请已发送”提示。

### 7. 玩家 A
- 观察聊天消息
- 预期：收到玩家 B 的加入申请消息，包含同意/拒绝操作提示。

### 8. 玩家 A
- 执行：`/todo join accept T1 <B_UUID>`
- 预期：玩家 A 收到审批成功提示。

### 9. 玩家 B
- 观察聊天消息
- 预期：收到已加入团队项目的提示。

### 10. 玩家 B
- 执行：`/todo task listp T1`
- 预期：可以正常访问该团队项目任务列表，即使当前为空也不报权限错误。

## 阶段四：成员角色变更
### 11. 玩家 A
- 执行：`/todo project member role T1 <B_UUID> lead`
- 预期：玩家 B 被提升为 `lead`。

### 12. 玩家 A
- 执行：`/todo project member role T1 <B_UUID> member`
- 预期：玩家 B 被降回 `member`。

## 阶段五：团队任务新增与查看
### 13. 玩家 A
- 执行：`/todo task addp T1 "Team task one" "Prepare sync" sync,meeting`
- 预期：团队任务创建成功。

### 14. 玩家 B
- 执行：`/todo task listp T1`
- 预期：能看到 `Team task one`。
- 记录：把这条任务 ID 记为 `TASK1`。

## 阶段六：团队任务领取与完成
### 15. 玩家 B
- 执行：`/todo task claimp T1 TASK1`
- 预期：领取成功，任务指派给玩家 B。

### 16. 玩家 A
- 执行：`/todo task listp T1`
- 预期：能看到 `Team task one` 已被指派，数据同步正常。

### 17. 玩家 B
- 执行：`/todo task donep T1 TASK1`
- 预期：任务完成成功。

### 18. 玩家 A
- 执行：`/todo task listp T1 completed`
- 预期：能看到 `Team task one` 已完成。

## 阶段七：团队任务指派与放弃
### 19. 玩家 A
- 执行：`/todo task addp T1 "Team task two" "Follow up" followup`
- 预期：第二条团队任务创建成功。

### 20. 玩家 A
- 执行：`/todo task listp T1`
- 预期：能看到 `Team task two`。
- 记录：把这条任务 ID 记为 `TASK2`。

### 21. 玩家 A
- 执行：`/todo task assignp T1 TASK2 <B_UUID>`
- 预期：指派成功。

### 22. 玩家 B
- 执行：`/todo task listp T1`
- 预期：能看到 `Team task two` 已指派给自己。

### 23. 玩家 B
- 执行：`/todo task abandonp T1 TASK2`
- 预期：放弃成功，任务指派人清空。

### 24. 玩家 A
- 执行：`/todo task listp T1`
- 预期：`Team task two` 仍存在，但不再指派给玩家 B。

## 阶段八：团队任务删除
### 25. 玩家 A
- 执行：`/todo task removep T1 TASK2`
- 预期：任务删除成功。

### 26. 玩家 B
- 执行：`/todo task listp T1`
- 预期：看不到 `Team task two`。

## 阶段九：成员移除
### 27. 玩家 A
- 执行：`/todo project member remove T1 <B_UUID>`
- 预期：玩家 B 被移出项目。

### 28. 玩家 B
- 执行：`/todo task listp T1`
- 预期：无法继续正常访问该团队项目，或看不到该项目相关数据。

## 阶段十：加入申请拒绝流程
### 29. 玩家 A
- 执行：`/todo project create team Team Beta`
- 预期：第二个团队项目创建成功。

### 30. 玩家 A
- 执行：`/todo project list all`
- 预期：出现 `Team Beta`。
- 记录：把 `Team Beta` 的项目 ID 记为 `T2`。

### 31. 玩家 B
- 执行：`/todo join project T2`
- 预期：申请发送成功。

### 32. 玩家 A
- 执行：`/todo join deny T2 <B_UUID>`
- 预期：拒绝成功。

### 33. 玩家 B
- 观察聊天消息
- 预期：收到被拒绝提示。

### 34. 玩家 B
- 执行：`/todo task listp T2`
- 预期：无法访问该项目，或看不到该项目数据。

## 阶段十一：异常场景
### 35. 玩家 A
- 执行：`/todo project member role T1 <B_UUID> manager`
- 预期：非法角色，命令失败。

### 36. 玩家 A
- 执行：`/todo task assignp T1 TASK1 ghost-user`
- 预期：非法目标，命令失败。

### 37. 玩家 A
- 执行：`/todo project remove confirm`
- 预期：如果当前没有待确认请求，应提示确认已过期或没有待确认操作。

### 38. 玩家 A
- 执行：`/todo task clean confirm`
- 预期：如果当前没有待确认请求，应提示确认已过期或没有待确认操作。

## 执行结果记录
| 编号 | 是否通过 | 备注 |
|---|---|---|
| 1 |  |  |
| 2 |  |  |
| 3 |  |  |
| 4 |  |  |
| 5 |  |  |
| 6 |  |  |
| 7 |  |  |
| 8 |  |  |
| 9 |  |  |
| 10 |  |  |
| 11 |  |  |
| 12 |  |  |
| 13 |  |  |
| 14 |  |  |
| 15 |  |  |
| 16 |  |  |
| 17 |  |  |
| 18 |  |  |
| 19 |  |  |
| 20 |  |  |
| 21 |  |  |
| 22 |  |  |
| 23 |  |  |
| 24 |  |  |
| 25 |  |  |
| 26 |  |  |
| 27 |  |  |
| 28 |  |  |
| 29 |  |  |
| 30 |  |  |
| 31 |  |  |
| 32 |  |  |
| 33 |  |  |
| 34 |  |  |
| 35 |  |  |
| 36 |  |  |
| 37 |  |  |
| 38 |  |  |
