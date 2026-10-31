# H2 数据排查指南（备份副本路线）

> **重要变更（2026-10-28）**：模组已移除 H2 TCP 服务端与外部访问账号体系（CurseForge 审查要求，
> 背景见 `docs/h2-tcp-server-removal.md`）。`config/todolist-h2.json` 中的 `tcpEnabled`、`port`、
> `accounts` 等字段已全部废弃，读取时被静默忽略；`/todo h2 restart-tcp` 与 `/todo h2 reset-password`
> 命令已删除。外部工具排查数据请按本指南的**备份副本**路线操作。

## 这份文档适合谁

如果你已经把底层存储切到了 `H2`（1.4.2 起为默认且唯一后端），并且想：

- 用 `DBeaver` / `DataGrip` 查看数据
- 用命令行快速执行 SQL
- 把数据带到另一台机器排查

这份文档可以直接照着做。核心思路只有一句话：**拿数据库文件的副本，用 H2 嵌入式模式打开**。

---

## 一、先拿到数据库文件副本

### 路线 A：游戏 / 服务器已关闭（最简单）

直接复制数据目录里的数据库文件：

```text
<游戏目录>/todo/<namespace>/todolist.mv.db
```

例如 Windows 单人世界：

```text
E:/Minecraft/.minecraft/todo/local/todolist.mv.db
```

把它复制到任意工作目录（如 `D:/todo-inspect/todolist.mv.db`），后续对**副本**操作。

### 路线 B：游戏 / 服务器正在运行（用备份命令）

运行中的数据库文件被游戏进程加锁，不能直接复制打开。用维护命令生成在线备份：

```text
/todo h2 backup inspect-001
```

备份 zip（内含 `todolist.mv.db`）生成在：

```text
<游戏目录>/todo/<namespace>/backups/
```

用 `/todo h2 backups` 可以列出全部备份与序号。把 zip 里的 `todolist.mv.db` 解到工作目录即可。

---

## 二、用 DBeaver / DataGrip 打开副本

副本没有游戏进程的文件锁，可以用 H2 嵌入式（file）模式直接打开。

推荐配置（**只读打开**，最安全）：

```text
Driver: H2
JDBC URL: jdbc:h2:file:D:/todo-inspect/todolist;DATABASE_TO_UPPER=FALSE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r
User: sa
Password: （留空）
```

要点：

1. URL 里的路径**不带** `.mv.db` 后缀（`.../todolist` 而不是 `.../todolist.mv.db`）；
2. `IFEXISTS=TRUE` 防止路径写错时误建空库；
3. `ACCESS_MODE_DATA=r` 以只读模式打开，杜绝误写；
4. 用户名固定 `sa`、密码留空（副本是本地文件，无账号体系）。

### 推荐用途

- 查 `tasks` / `projects` / `player_project_state` / `storage_meta`
- 导出 CSV
- 验证 NBT→H2 迁移结果

### 不推荐

- 在副本上做写操作——排查副本的改动**不会**回到游戏数据；要回写请走 `/todo h2 restore` 备份恢复流程。

---

## 三、命令行查库

仓库自带 H2 jar（`libs/h2-2.2.220.jar`），可以直接用 H2 Shell：

```bash
java -cp libs/h2-2.2.220.jar org.h2.tools.Shell -url "jdbc:h2:file:D:/todo-inspect/todolist;DATABASE_TO_UPPER=FALSE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r" -user sa -password ""
```

进入后可以先试：

```sql
SELECT COUNT(*) FROM tasks;
SELECT COUNT(*) FROM projects;
SELECT * FROM storage_meta;
```

注意 H2 版本要匹配（本模组内嵌 2.2.220），用过旧的 H2 工具版本打开新库文件可能报版本不支持。

---

## 四、跨机器排查

不需要任何隧道：把备份 zip（或 `todolist.mv.db` 副本）用 `scp` / 共享目录传到另一台机器，按第二、三节打开即可。

---

## 五、最容易出错的 5 个点

### 1. 直接打开游戏正在使用的原文件

会撞上 H2 文件锁（报 `Database may be already in use`）。永远操作**副本**；游戏运行中就用路线 B 的备份命令。

### 2. 把 `.mv.db` 写进 JDBC URL

正确写法是不带后缀的基础路径（见第二节要点 1）。

### 3. `databasePathOverride` 改过路径

如果你在 `config/todolist-h2.json` 里设置了 `databasePathOverride`，数据库文件在那个覆盖路径下，而不是默认的 `todo/<namespace>/` 目录。

### 4. 在副本上改数据想同步回游戏

不会生效。数据回滚请用游戏内的 `/todo h2 backup` + `/todo h2 restore <序号>` 维护流程。

### 5. H2 工具版本过旧

用 `libs/h2-2.2.220.jar` 同版本（或更新）的驱动打开。

---

## 六、推荐流程汇总

```text
1. /todo h2 status                 # 确认 H2 可用
2. /todo h2 backup inspect-001     # 运行中：生成备份；已关服：直接复制文件
3. 解出 / 复制 todolist.mv.db 到工作目录
4. DBeaver / Shell 只读打开副本查询
```
