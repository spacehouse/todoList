# H2 存储使用说明

> 2026-11-21 起，模组存储链路已收敛为 **H2 单后端**：旧版 NBT 文件存储模式整体移除，
> `storageBackend` 配置只支持 `h2`（旧配置中的 `nbt` 值会在加载时回退为 `h2`）。
> 原「NBT ↔ H2 切换指南」与「H2 TCP 外部访问」章节随移除失效，
> 设计记录见 `docs/completed/nbt-storage-removal.md` 与 `docs/completed/h2-tcp-server-removal.md`。

## 这份文档解决什么问题

如果你已经在使用本模组的 H2 存储，但还不清楚：

- 数据存在哪个文件、哪个目录
- 首次从旧版 NBT 升级时会不会自动迁移旧数据
- 遇到备份、恢复、配置损坏、存储不可用时该看哪里

这份文档就是给这些场景准备的。

---

## 一、先看结论

- 存储后端只有 `h2` 一种：`storageBackend` 配置项保留在 `config/todolist.json` 中作为配置结构与注释锚点，但不再支持切换。
- 嵌入式连接是唯一连接模式（无 TCP、无监听端口）。
- 旧版 NBT 数据（`.dat` 文件）在首次需要时自动迁移进 H2，迁移完成前不会丢弃。

---

## 二、相关文件和目录

### 1. 主配置文件

路径：

```text
config/todolist.json
```

与存储相关的配置项：

- `storageBackend`：存储后端，只支持 `h2`（唯一支持的存储模式）
- `h2BackupOnStart`：启动初始化 H2 后是否自动创建一次备份（默认开启）
- `h2BackupRetentionCount`：自动备份保留上限

### 2. H2 存储配置文件

路径：

```text
config/todolist-h2.json
```

当前唯一有效字段是 `databasePathOverride`（数据库路径覆盖）；历史上 TCP 相关
字段（`tcpEnabled`、`port`、`accounts` 等）已被移除，读取时静默忽略。

### 3. 数据目录

默认数据都在：

```text
todo/<namespace>/
```

其中 `<namespace>` 是当前存储域。常见理解方式：

- 单人本地世界通常是 `local`
- 多人/联机环境会派生出对应的命名空间目录

数据库主文件基础路径是：

```text
todo/<namespace>/todolist
```

实际 H2 文件通常会表现为：

```text
todo/<namespace>/todolist.mv.db
```

旧版 NBT 数据文件（`moddata.dat`、`projects.dat`、`players/*.dat` 等）仍会保留在同一套
`todo/<namespace>/` 目录结构下，迁移完成后不会自动删除。

---

## 三、旧版 NBT 数据迁移

老版本存档升级到当前版本时，首次需要访问存储（进入世界同步任务、执行管理命令等）会触发：

1. 创建或打开 `todo/<namespace>/todolist.mv.db`
2. 初始化 H2 schema
3. 检查旧 NBT 数据是否已迁移（`storage_meta` 表的迁移标记）
4. 未迁移时，把旧 `.dat` 数据（任务、项目、玩家状态）以单事务导入 H2
5. 迁移完成后，后续读写都走 H2

关键点：

- 旧 `.dat` 文件不会自动删除
- 迁移完成后，不会在每次启动时重复导入旧 `.dat`
- 迁移失败时 H2 会进入不可用状态（见 `/todo h2 status` 的最近失败信息），修复后重试

确认方式：游戏内执行 `/todo h2 status`，重点看：

- `H2 存储后端：h2`
- `H2 可用性：available`
- `H2 当前连接模式：embedded`

---

## 四、常用 H2 运维命令

### 1. 查看状态

```text
/todo h2 status
```

适合确认：

- H2 当前是否可用
- 最近一次失败原因

### 2. 在线备份

```text
/todo h2 backup
/todo h2 backup <name>
```

适合在改配置、做升级、做外部维护前先留一个备份。

备份默认开启自动保留（配置项 `h2BackupRetentionCount`，默认 `10`）：
**启动自动备份**（`todolist-h2-*.zip`）与**恢复前安全备份**（`pre-restore-*.zip`）超过上限时自动删除最早的，
手动命名备份与 schema 升级备份不会被自动清理。

### 3. 查看备份

```text
/todo h2 backups
```

按时间倒序列出 `todo/<命名空间>/backups/` 下的全部备份，并给出可直接引用的序号：

```text
H2 备份共 3 份（自动备份保留上限 10 份，超出后自动删除最早的）：
  [1] 2026-09-15 00:28:08  4.3 MB  todolist-h2-20260915-002808.zip
  [2] 2026-09-12 15:00:54  4.3 MB  schema-upgrade-v2-to-v3.zip
  [3] 2026-09-11 21:10:03  4.2 MB  todolist-h2-20260911-211003.zip
用 /todo h2 restore <序号> 恢复到指定备份，/todo h2 restore latest 恢复最近一份；恢复会在下次启动时生效。
```

### 4. 恢复备份

```text
/todo h2 restore latest
/todo h2 restore <序号>
/todo h2 restore <备份文件名>
```

恢复分两步，**不会在数据库使用中替换文件**：

1. 执行命令时只做**校验与暂存**：解压备份里的 `todolist.mv.db`，打开确认能读到任务表，
   然后放到 `backups/restore-pending/`；备份不可读时会直接拒绝，不会改动现有数据；
2. **下次启动、数据库被打开之前**自动落地：先把当前库打包成 `pre-restore-*.zip` 安全备份，
   再用暂存的库覆盖，随后照常启动（如备份是旧 schema，会照现有流程自动升级并再备份一次）。

所以命令提示「暂存成功」后，**保存并完全退出游戏再重新打开**即可完成恢复。

### 5. 重载数据库上下文

```text
/todo h2 reload-db
/todo reload-db
```

适合在数据库维护后刷新当前内存上下文。

### 6. 健康检查

```text
/todo h2 health
```

会输出：

- 当前 schema 版本
- 任务数量
- 健康检查是否通过

---

## 五、注意事项

### 1. 第一次升级时不要手动删旧 `.dat`

原因很简单：

- 首次迁移要读旧数据
- 出现异常时，旧数据仍然是最重要的回退依据

### 2. 看到 `.corrupted` 文件不要慌

这通常说明：

- 配置文件损坏
- 程序已经帮你把坏文件保留下来
- 并重新生成了一份新的安全默认配置

这不是"自动删配置"，而是"先保留原件，再恢复可启动状态"。

### 3. 局域网开放、退出世界、再回未发布单人，会不会把 H2 搞坏？

当前版本已经专门修复了这条生命周期链路里的旧连接复用问题。现在在：

```text
未发布单人 -> 对局域网开放 -> 退出 -> 再进入未发布单人
```

这条路径上，程序会主动失效旧上下文里的可复用连接，避免把已经关闭的旧 session 继续拿来写入。

如果你仍遇到"存储当前不可用"，优先查看日志和 `/todo h2 status` 的最近失败信息。

### 4. 进世界后一直提示「存储当前不可用」，日志里刷 `File corrupted while reading record` / `File corrupted in chunk ...`

这是**数据库文件本身损坏**，不是配置或权限问题，程序无法在运行中自行修好。典型日志：

```text
org.h2.jdbc.JdbcSQLNonTransientConnectionException: File corrupted while reading record:
  ".../todo/local/todolist.mv.db". Possible solution: use the recovery tool [90030-220]
```

处理顺序：

1. **先别删库**。`/todo h2 backups` 看有没有可用备份（启动备份、schema 升级备份、手动备份都会列出）；
2. 有备份就 `/todo h2 restore latest`（或指定序号），然后**退出游戏重新打开**完成恢复；
3. 没有备份时，只能保留损坏库、让程序重建空库（会丢历史任务）：
   退出游戏 → 把 `todo/<命名空间>/todolist.mv.db` 改名为 `todolist.mv.db.corrupt-<日期>` → 重新启动。

关于「为什么会损坏」：H2 只在**后续读到坏页时**才发现损坏，日志里不会有"是哪次操作写坏的"，
所以只能从环境排查。已知的高风险来源：

- 游戏/主机在写库过程中被强杀、断电或休眠（MVStore 写入未落盘）；
- **用外部工具（DBeaver/DataGrip 等）直接打开 `todolist.mv.db`**，尤其是驱动版本与库的 write format 不匹配，
  或以可写模式打开同一个库文件；
- 同时有第二个进程（另一个游戏实例、脚本）打开同一个 `todo/` 目录。

外部工具排查数据请走**备份副本路线**：`/todo h2 backup` 生成备份后，
用 DBeaver 等工具以 H2 嵌入式只读方式打开**副本**，不要直接开正在使用的库文件。
连接模板见 [h2-external-client-guide.md](h2-external-client-guide.md)。

### 5. 维护与升级的推荐流程

1. 退出游戏或停服
2. 备份整个 `todo/` 目录和 `config/todolist.json`
3. 启动一次，让程序自动建库和迁移
4. 进入游戏后执行 `/todo h2 status` 确认 `H2 可用性` 为 `available`
5. 准备做结构调整或运维时，先执行一次 `/todo h2 backup`

---

## 六、给不同用户的简版建议

### 玩家

如果你只是自己玩：

- 直接用默认配置即可（H2 嵌入式 + 启动自动备份）

### 局域网房主 / 服务器服主

推荐：

- 保持默认 `storageBackend=h2`
- 改配置前先 `/todo h2 backup`
- 用 `/todo h2 status`、`/todo h2 health`、`/todo h2 backups` 做日常维护
