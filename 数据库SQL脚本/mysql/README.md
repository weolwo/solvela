### 数据库脚本

默认数据库为 Mysql，若为其他数据库，请关注：[SmartAdmin其他数据库](https://smartadmin.vip/views/other/china-db/)

---

## 🔴 新环境部署：两个文件，按顺序

```
mysql> SOURCE 数据库SQL脚本/mysql/schema-baseline.sql;   -- ① 建结构（75 张表）
mysql> SOURCE 数据库SQL脚本/mysql/data-baseline.sql;     -- ② 灌种子数据（菜单/字典/权限等）
```

跑完就能登录并正常使用。之后按需执行造数脚本（都可重复执行，见 `*造数*.sql`）。

已验证：空库上 `schema-baseline` 150 条语句 + `data-baseline` 39 条语句零失败，
关键种子数据（菜单 270 / 角色菜单 135 / 员工 12 / 文件分类含四个内置 code /
定时任务 9 / 任务事件 9）全部到位，且**业务表全部为 0 行**（基线不带任何测试数据）。

| 文件 | 内容 | 不含 |
|---|---|---|
| `schema-baseline.sql` | 75 张表的结构 | 任何数据 |
| `data-baseline.sql` | 18 张配置表、505 行种子数据 | 会员/活动/任务记录/流水/日志等业务数据 |

> ⚠️ `data-baseline.sql` 里的 `t_employee` 含 Argon2 密码哈希与手机号
> （上游 `smart_admin_v3.sql` 本来也带，不是新增暴露面）。
> 要发到公开仓库或交付外部前，先把非必要账号删掉、只留一个 admin。

### 这个文件是怎么来的、为什么必须有它

`schema-baseline.sql` 是**从开发库用 `SHOW CREATE TABLE` 逐表导出**的，
所以它就是库里真实的样子，不是人工维护的近似版本。
已验证：在空库上执行 150 条语句零失败，75 张表的**列与索引和源库逐一完全一致**。

在它出现之前（2026-08-22 之前），「新环境要执行哪些 SQL」这个问题**没有答案**：

| 建表语句在哪 | 张数 |
|---|---|
| `mysql/smart_admin_v3.sql`（上游 SmartAdmin 基线，**已于 2026-08-22 删除**） | 44 |
| `activity.sql` / `lottery.sql` / `mysql/task.sql` | 21 |
| `member.sql` / `mall.sql` | 11 |
| **只在 `sql-update-log/` 的某个版本文件里** | **3** |
| **任何文件里都没有** | **3** |

最后两行是致命的：`t_activity_display` / `t_file_category` / `t_file_relation` 得翻 49 个版本文件才找得到；
而 `t_task_event` / `t_task_record_flow` / `t_lottery_number_pool` **在整个仓库里根本没有建表语句** ——
照当时 README 说的「第一次部署只执行 `smart_admin_v3.sql`」，会缺掉整个营销域、会员域，外加这三张表。
（那个文件与配套的增量回放脚本已一并删除，docker-compose 的 initdb 也改成只挂两个基线。）

这也是交接文档铁律 22 的根因：**表清单靠 grep 文件永远是不全的，必须查 `information_schema`。**

---

## 🔴 维护约定：改表结构时，两个地方都要动

| 改哪 | 服务谁 |
|---|---|
| `schema-baseline.sql` | **新环境** —— 让它建出来就是最新的 |
| `sql-update-log/vX.Y.0.sql` | **已有环境** —— 让它能从旧版本升上来 |

改完重新跑导出工具覆盖基线，用 `git diff` 核对是否与预期一致：

```
cd 数据库SQL脚本/tools
java -cp <mysql-connector.jar> DumpSchema.java        # 结构基线
java -cp <mysql-connector.jar> DumpSeedData.java      # 种子数据基线（改了菜单/字典/权限才需要）
java -cp <mysql-connector.jar> VerifyFreshInstall.java # 空库验证：两个文件能否得到可用系统
```

> **只改迁移不改基线**，结果是新环境和老环境结构不一样，**而且没有任何人会发现** ——
> 直到某天新环境上线报「Unknown column」。上面那 3 张「任何文件里都没有」的表，
> 就是这么来的。

### 分域 DDL 文件的定位（activity / lottery / task / member / mall）

它们**不是权威定义**，保留只为那些解释「为什么这么设计」的注释 —— 基线是机器导出的，
只有结构没有理由。**计划：等各模块开发完工后，把设计注释搬进交接文档，
然后删掉分域文件、只留基线。**

在那之前同一张表的定义存在两处，会静默漂移。所以有 `CheckModuleDrift`：

```
cd 数据库SQL脚本/tools
java -cp <mysql-connector.jar> CheckModuleDrift.java     # 报告分域文件与基线的差异
java -cp <mysql-connector.jar> SyncFromBaseline.java lottery.sql t_lottery_issue   # 整块同步某张表
```

> 2026-08-22 首次跑这个检查，**11 张表在漂**。其中 `t_task_record` 缺 `version`（v3.44.0 加的）、
> `t_task_template` 缺 `status`、`t_lottery_record` 缺 `dispatch_status`/`prize_code`
> —— 这些都是**很久以前**就漂的，一直没人发现。
> **靠纪律维护两份定义是不成立的**，这正是「最终要合并成一个文件」的理由。
> 现已全部同步，检查通过。
>
> ⚠️ 手工补列会打地鼠（补完 nullable 又冒出 default、再冒出索引顺序）。
> 用 `SyncFromBaseline` 整块替换 CREATE TABLE，注释原样保留。

---

## `sql-update-log/` 只剩 3 个文件了

**2026-08-22：v3.15.0 ~ v3.69.0 共 46 个已全部删除。**

删除的前提是上面那两个基线文件已经验证过 —— 在此之前不能删，因为那 46 个文件里有 17 个含
`INSERT`，是新环境必需的种子数据（光 `t_menu` 的增量插入就横跨 v3.18 / v3.41 / v3.42 /
v3.46 / v3.47 / v3.48 / v3.52 / v3.60 八个文件，靠人按版本号顺序拼才能得到完整菜单，
漏一个就少一块功能入口且不报错）。这些数据现已收敛进 `data-baseline.sql`。

需要翻旧账时用 `git log -- 数据库SQL脚本/mysql/sql-update-log/` —— 文件内容都在 git 历史里，
关键决策与踩坑记录也已沉淀进 `docs/营销中台-会话交接文档.md`。

剩下的 3 个是本轮改造的，其中一个**尚未执行**：

| 版本 | 内容 | 状态 |
|---|---|---|
| `v3.70.0` | 默认租户 `'0'` → `'taozi'`，25 张表 | ✅ 已执行 |
| `v3.71.0` | 关联键 `member_name` → `member_id`，10 张表，回填 9216 行 | ✅ 已执行，**run-once 不可重跑** |
| `v3.72.0` | `member_name` 收口（单据类降级快照 / 状态类删列） | 🔴 **未执行** —— 必须等 Java 侧全切到 `memberId` 并观察一个版本 |

> 保留 v3.70/71 不是因为还要执行，而是因为它们是 v3.72 的前置上下文 ——
> 谁要执行 v3.72，得先读懂前两个做了什么。

---

## 其他文件

- `t_menu.sql`：菜单数据补丁。
- `*造数*.sql` / `*联调*.sql`：各模块的联调造数，可重复执行，尾部通常带清场语句。用前先读注释。
- `诊断-审计时间异常排查.sql`：时区问题的排查脚本，见铁律 10。
