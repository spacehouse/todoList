# H2 外部工具连接示例

## 这份文档适合谁

如果你已经把底层存储切到了 `H2`，并且还想：

- 用 `DBeaver` 查看数据
- 用 `DataGrip` 做只读排查或导出
- 用命令行快速执行 SQL
- 从另一台机器连到当前游戏或服务器的 H2

这份文档可以直接照着填。

如果你还没看过底层模式切换说明，建议先看：

- [storage-backend-h2-guide.md](storage-backend-h2-guide.md)

---

## 一、连接前检查

外部工具要连上当前模组的 H2，至少要满足下面 4 条：

1. `config/todolist.json` 里已经设置：

```json
{
  "storageBackend": "h2"
}
```

2. `config/todolist-h2.json` 里已经开启：

```json
{
  "tcpEnabled": true
}
```

3. 游戏或服务器已经重启过，或者你已经执行过：

```text
/todo h2 restart-tcp
```

4. ` /todo h2 status ` 显示当前连接模式是 `tcp`

如果状态是：

- `embedded`：说明根本没开 TCP
- `embedded_fallback`：说明你想开 TCP，但 TCP 启动失败，外部工具此时不能连

---

## 二、先拿到 4 个连接信息

真正开始连之前，你只需要确认这 4 个信息：

- 主机地址
- 端口
- 数据库路径
- 用户名和密码

### 1. 主机地址和端口

进游戏或服务器控制台执行：

```text
/todo h2 status
```

重点看：

- `H2 当前连接模式：tcp`
- `H2 TCP 地址：127.0.0.1:<port>（local）`

常见例子：

```text
H2 TCP 地址：127.0.0.1:9092（local）
```

或者端口冲突后变成：

```text
H2 TCP 地址：127.0.0.1:9094（local）
```

TCP 服务器固定只接受本机回环连接；远程机器要访问请先建 SSH 隧道（见 3.2）。

所以不要假定永远是 `9092`，尤其你启用了自动递增端口时。

### 2. 数据库路径

当前实现里，外部 TCP JDBC URL 使用的是：

```text
jdbc:h2:tcp://<host>:<port>/<absolute-db-path>;DATABASE_TO_UPPER=FALSE
```

这里的 `<absolute-db-path>` 指的是数据库基础路径，不带 `.mv.db` 后缀。

默认情况下，它通常是：

```text
<游戏目录>/todo/<namespace>/todolist
```

Windows 例子：

```text
E:/Minecraft/.minecraft/todo/local/todolist
```

注意两点：

1. 要写数据库基础路径，不要写成 `todolist.mv.db`
2. 建议统一写正斜杠 `/`，这和当前代码生成 JDBC URL 的方式一致

如果你在 `config/todolist-h2.json` 里设置了：

```json
{
  "databasePathOverride": "D:/MyTodoData/prod/todolist"
}
```

那外部客户端也必须改为这个覆盖路径。

### 3. 用户名

默认用户名是：

- 管理账号：`todo_admin`
- 只读账号：`todo_readonly`
- 读写账号：`todo_readwrite`

建议：

- 日常查询只用 `todo_readonly`
- 只有明确要做外部写入时才用 `todo_readwrite`
- 不建议把 `todo_admin` 当成日常查询账号

### 4. 密码

密码在：

```text
config/todolist-h2.json
```

对应字段是账号区里的密码字段。

如果你执行了：

```text
/todo h2 reset-password readonly
```

或者：

```text
/todo h2 reset-password readwrite
```

新的密码不会直接打印到聊天栏里，要回到 `config/todolist-h2.json` 里查看。

---

## 三、最常用的 JDBC URL 模板

## 3.1 本机连接模板

适合：

- 游戏和 SQL 工具在同一台机器上

模板：

```text
jdbc:h2:tcp://127.0.0.1:<实际端口>/<游戏目录>/todo/<namespace>/todolist;DATABASE_TO_UPPER=FALSE
```

例子：

```text
jdbc:h2:tcp://127.0.0.1:9092/E:/Minecraft/.minecraft/todo/local/todolist;DATABASE_TO_UPPER=FALSE
```

## 3.2 远程机器连接模板（先建 SSH 隧道）

TCP 服务器不开放远程访问，历史配置中的 `bindAddress`、`allowRemote` 字段已被移除并忽略。

从另一台机器查库时，先建 SSH 隧道把服务器端口转发到本机：

```text
ssh -L 9092:127.0.0.1:9092 user@服务器IP
```

然后按本机模板连接 `127.0.0.1:9092` 即可。注意两点：

- 数据库工具连接的地址是 `127.0.0.1`（隧道落点），不是服务器 IP
- URL 里的数据库路径必须是“服务器那台机器上的路径”，不是你电脑上的路径

---

## 四、DBeaver 连接示例

## 4.1 最稳的做法

推荐直接使用完整 JDBC URL，不要只填 Host/Port 让工具自己拼。

推荐参数：

- Driver: `H2`
- URL: 用完整 JDBC URL
- User name: `todo_readonly`
- Password: 从 `config/todolist-h2.json` 里复制

### 填写示例

```text
Driver: H2
JDBC URL: jdbc:h2:tcp://127.0.0.1:9092/E:/Minecraft/.minecraft/todo/local/todolist;DATABASE_TO_UPPER=FALSE
User: todo_readonly
Password: <从 todolist-h2.json 读取>
```

### 推荐用途

- 查 `tasks`
- 查 `projects`
- 查 `player_project_state`
- 导出 CSV
- 验证迁移结果

### 不推荐

- 直接拿 `todo_admin` 在 DBeaver 里长期使用
- 不确认 SQL 的情况下直接做写操作

---

## 五、DataGrip 连接示例

## 5.1 建议配置

在 DataGrip 新建 `Data Source` 时：

- 类型选择 `H2`
- 连接方式优先用 `URL only`
- 粘贴完整 JDBC URL

示例：

```text
URL: jdbc:h2:tcp://127.0.0.1:9092/E:/Minecraft/.minecraft/todo/local/todolist;DATABASE_TO_UPPER=FALSE
User: todo_readonly
Password: <从 todolist-h2.json 读取>
```

### 如果你要做只读排查

建议：

- 账号只用 `todo_readonly`
- 只做 `SELECT`
- 不改 schema

### 如果你要做维护写入

只有在你非常清楚自己要改什么时，才考虑：

```text
User: todo_readwrite
```

并且建议先做一次：

```text
/todo h2 backup
```

再动手。

---

## 六、命令行连接示例

仓库已经带了 H2 jar：

```text
libs/h2-2.2.220.jar
```

所以你可以直接用 H2 自带的 Shell 工具。

## 6.1 只读连接示例

在项目根目录执行：

```bash
java -cp libs/h2-2.2.220.jar org.h2.tools.Shell -url "jdbc:h2:tcp://127.0.0.1:9092/E:/Minecraft/.minecraft/todo/local/todolist;DATABASE_TO_UPPER=FALSE" -user todo_readonly -password "<你的密码>"
```

进入后可以先试：

```sql
SELECT COUNT(*) FROM tasks;
SELECT COUNT(*) FROM projects;
SELECT * FROM storage_meta;
```

## 6.2 读写连接示例

只有在明确需要时再使用：

```bash
java -cp libs/h2-2.2.220.jar org.h2.tools.Shell -url "jdbc:h2:tcp://127.0.0.1:9092/E:/Minecraft/.minecraft/todo/local/todolist;DATABASE_TO_UPPER=FALSE" -user todo_readwrite -password "<你的密码>"
```

---

## 七、推荐的实际使用姿势

## 7.1 我只是想看看数据

直接用：

- `DBeaver` 或 `DataGrip`
- `todo_readonly`
- `tcpEnabled=true`

这是最稳、最安全、最不容易踩坑的组合。

## 7.2 我想从另一台机器查库

步骤：

1. 在本机建 SSH 隧道：`ssh -L 9092:127.0.0.1:9092 user@服务器IP`
2. 数据库工具连接 `127.0.0.1:9092`
3. JDBC URL 里的数据库路径仍然写服务器端路径

数据库不开放任何远程直连，这是有意设计。

## 7.3 我想做导出或排查

推荐顺序：

1. `/todo h2 status`
2. `/todo h2 backup`
3. 用 `todo_readonly` 连接
4. 做查询或导出

---

## 八、最容易出错的 8 个点

### 1. 把 `.mv.db` 写进 JDBC URL

错误示例：

```text
jdbc:h2:tcp://127.0.0.1:9092/E:/Minecraft/.minecraft/todo/local/todolist.mv.db
```

正确写法：

```text
jdbc:h2:tcp://127.0.0.1:9092/E:/Minecraft/.minecraft/todo/local/todolist;DATABASE_TO_UPPER=FALSE
```

### 2. 明明是远程连接，却用了本机路径

错误理解：

- 以为 JDBC URL 里的路径应该写客户端电脑路径

正确理解：

- 路径必须写数据库所在机器上的绝对路径

### 3. 端口写死成 9092

如果你启用了：

- `autoIncrementPort=true`

那真实端口可能已经不是 `9092` 了。要先看：

```text
/todo h2 status
```

### 4. 想让其他电脑直连数据库

TCP 服务器只绑定 `127.0.0.1`，其他机器无论怎么配置都连不上，这是有意设计。远程查库请走 SSH 隧道（见 3.2）。

### 5. 弱密码处理

账号密码由程序自动生成高强度随机值。如果手动改弱了密码，程序会在下次加载时自动重新生成强密码。

### 6. 忘了重启游戏或服务器

改了配置但没重启，外部工具往往会连旧状态。

### 7. 想查数据却用了 `todo_admin`

不推荐。

查数据优先用：

```text
todo_readonly
```

### 8. 改了密码却没同步客户端

执行：

```text
/todo h2 reset-password readonly
```

之后，你的 DBeaver、DataGrip、脚本都要更新密码，否则只会一直报认证失败。

---

## 九、遇到报错时怎么判断

### 1. `Connection refused`

优先排查：

- H2 TCP 没启动
- 端口不对
- 目标主机不对
- 防火墙拦截

先执行：

```text
/todo h2 status
```

### 2. 认证失败

优先排查：

- 用户名写错
- 密码没同步
- 你连的是旧端口或旧环境

### 3. 找不到数据库

优先排查：

- 数据库基础路径写错
- 错把 `.mv.db` 后缀带进 URL
- `databasePathOverride` 已改，但外部客户端还在用旧路径

### 4. 当前状态是 `embedded_fallback`

这说明：

- 当前模组本体可能还能继续工作
- 但外部 TCP 连接暂时不可用

你应该先解决 TCP 启动失败的问题，再连工具。

---

## 十、给你的推荐模板

如果你只是想稳定地“看数据不改数据”，推荐直接用这套：

### 本机 DBeaver / DataGrip

```text
URL: jdbc:h2:tcp://127.0.0.1:<实际端口>/<游戏目录>/todo/local/todolist;DATABASE_TO_UPPER=FALSE
User: todo_readonly
Password: <从 config/todolist-h2.json 读取>
```

### 命令行

```bash
java -cp libs/h2-2.2.220.jar org.h2.tools.Shell -url "jdbc:h2:tcp://127.0.0.1:<实际端口>/<游戏目录>/todo/local/todolist;DATABASE_TO_UPPER=FALSE" -user todo_readonly -password "<你的密码>"
```

这套最安全，也最适合日常排查。
