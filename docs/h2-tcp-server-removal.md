# 设计：移除 H2 TCP 服务端，外部排查改走备份文件路线

> 状态：**已评审通过，待实现**（对应 TODO.md 计划中条目）
> 类型：外部约束强制变更（CurseForge 审查要求）
> 目标分支：`verify/1.20.1-feat-trigger-and-item-title`
> 更新日期：2026-10-28

## 0. 背景与外部约束

CurseForge 对 1.4.1 的人工审查拒绝原文（两次拒绝，同一原因）：

> The mod includes functionality to expose its database over TCP with admin credentials.
> therefore this poses a security risk and requires a fix before upload.

措辞是 "requires a fix"——不是质疑待解释，而是要求改掉再传。其顾虑技术上说得通：
带凭据的 H2 TCP 端点即使仅绑回环，SQL 层面仍具备文件读写能力（`FILE_READ` / `CREATE ALIAS`
一类），等于机器上多一个凭据保护的可执行 SQL 入口。纯申诉翻转概率低，不再作为主路径。

## 1. 现状事实（2026-10-28 代码核查结论，移除安全性的依据）

- `tcpEnabled` 默认 `false`（`H2TcpConfig.java:27`），用户手改 `config/todolist-h2.json` 才生效；
- TCP 服务器固定只绑 `127.0.0.1` 回环（`H2TcpServerManager.LOOPBACK_BIND_ADDRESS`），
  历史 `bindAddress` / `allowRemote` 字段已移除并在读取时忽略（`H2TcpConfig.java:251`）；
- 全仓无自动激活路径（无 `openToLan` / `publishWorld` 钩子，无代码内 `tcpEnabled = true`）；
  `ensureStarted` 对未启用配置直接 `stop()` 并返回 disabled；
- **TCP 不是联机运行的内部依赖**：联机同步走模组网络包；`H2ConnectionProvider` 仅在
  TCP 激活时改走 TCP URL，默认路径是嵌入式 H2——移除后所有游戏内功能不受影响。

## 2. 需求准入三问

1. **用户故事**：CurseForge 渠道用户需要正常获得版本更新（外部硬约束：审查要求移除
   带凭据的 TCP 数据库暴露）；维护者/进阶用户仍能用 DBeaver / DataGrip 排查数据，
   改走"备份副本"路线。
2. **验证方案**：`:common:check` 全绿（TCP 用例移除后）；`verify-all.bat dev-fabric`
   启动冒烟通过；遗留 `todolist-h2.json` 含 `tcpEnabled: true` 的老配置被静默忽略不报错；
   实机轻量验证 H2 保存 / 备份 / 恢复 / 健康检查命令仍正常；**最终验收 = 移除后的
   jar 上传 CurseForge 通过审查**。
3. **投入上限**：2 个工作日（设计 + 删除 + 回归 + 文档），超限冻结复盘。

状态流转：计划 → 实现 → 待同步（不设 experimental，删除类变更直接正式）。

## 3. 删除范围

| 项 | 处理 | 位置（代表） |
|---|---|---|
| TCP 服务器启动 / 停止 / 端口回退 | 删除 | `H2TcpServerManager`（server 相关部分与状态机） |
| `tcpEnabled` / 端口 / 自增等 TCP 配置字段与凭据体系（账号权限、密码重置） | 删除；遗留字段沿用"忽略未知键"既有模式（同 `bindAddress` 先例） | `H2TcpConfig` |
| `H2ConnectionProvider` 的 TCP 分支 | 收敛为嵌入式单路径 | `H2ConnectionProvider:77-101` |
| `H2StorageBootstrap` / `TodoListCommon` 的 `ensureStarted` / `stop` 接线 | 删除 | `H2StorageBootstrap:70,135`、`TodoListCommon:81,175` |
| TCP 相关运维命令（重启 / 状态查询中的 TCP 部分）与诊断输出 | 删除或收敛 | `CommandBootstrap:874-907,1185` |
| H2 TCP 测试用例（`H2TcpAccessTestMain` 及外部会话互锁类用例） | 删除 | `common/src/test` |
| `h2-external-client-guide.md` | 改写为备份文件路线（见 §4） | `docs/` |

**保留物（不得删除）**：嵌入式 H2 本体、H2 备份 / 恢复 / 健康检查 / 维护锁 / schema
升级等维护能力（无监听、CF 无风险）、NBT→H2 老档迁移、H2 不可用时的应急 NBT 写入、
Task NBT 序列化格式。

## 4. 替代工作流（外部排查）

1. 运行维护命令生成 H2 备份（zip 内含 `todolist.mv.db`），或直接复制数据目录中的
   `todolist.mv.db`（游戏关闭时）；
2. 解出 `.mv.db` 副本，DBeaver / DataGrip 以 **H2 嵌入式文件** 方式打开副本排查
   （副本无游戏文件锁，可行）；
3. 跨机器场景用 scp / 共享目录传备份文件，替代原 SSH 隧道方案。

损失的能力仅"对运行中游戏的实时查询"（原外部会话与游戏写入互锁、端口回退、凭据等
复杂度随之消失，为净减负）。

## 5. 连带动作

- `docs/h2-external-client-guide.md` 按第 4 节改写；
- **AGENTS.md 第 15 / 16 条同步修订**：两条规定围绕"已发布 / TCP 激活路径"与
  `isTcpActive()` 展开，TCP 移除后改为只覆盖嵌入式路径的口径（或合并为一条）；
- CHANGELOG 记录行为变更：`tcpEnabled` 不再生效（老配置静默忽略）；
- 版本号：随下一次发版（≥1.4.2）携带。

## 6. 验收标准

- [ ] `:common:check` 全绿，无 TCP 遗留引用
- [ ] 遗留 `tcpEnabled: true` 配置被忽略，游戏正常启动
- [ ] 备份 / 恢复 / 健康检查命令实机可用，指南新路线可照做走通
- [ ] `verify-all.bat dev-fabric` PASS
- [ ] 移除后 jar 上传 CurseForge 通过审查（最终验收）

## 7. 后续（不在本需求内）

- 若未来确需"运行中实时查库"，评估 CF 之外渠道的独立工具形态，不在主模组内重开监听。
