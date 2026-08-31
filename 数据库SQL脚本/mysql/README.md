### 数据库脚本

默认数据库为 Mysql，若为其他数据库，请关注：[Solvela其他数据库](https://smartadmin.vip/views/other/china-db/)

---

## 🔴 新环境部署：两个文件，按顺序

```
mysql> SOURCE 数据库SQL脚本/mysql/schema-baseline.sql;   -- ① 建结构（64 张表）
mysql> SOURCE 数据库SQL脚本/mysql/data-baseline.sql;     -- ② 灌种子数据（菜单/字典/权限等）
```

跑完就能登录并正常使用。之后按需执行造数脚本（都可重复执行，见 `*造数*.sql`）。

当前文件实测（2026-08-31 逐个数出来的）：`schema-baseline` 129 条语句
（64 DROP + 64 CREATE + 1 SET NAMES），`data-baseline` 36 条语句、17 条 INSERT
覆盖 16 张配置表共 576 行；其中菜单 329 / 角色菜单 139 / 员工 12 /
文件分类 7（含 COMMON、NOTICE、HELP_DOC、FEEDBACK 四个内置 code）/
定时任务 9 / 任务事件 9，**业务表全部为 0 行**（基线不带任何测试数据）。

> ⚠️ 上面这组数是**从文件里数出来的**，不是空库执行验证的结果。
> 最近一次「空库执行零失败」的验证对应的是 75 张表那一版，此后基线被改过，
> 换环境前请自己再跑一次 `tools/VerifyFreshInstall.java`。

| 文件 | 内容 | 不含 |
|---|---|---|
| `schema-baseline.sql` | 64 张表的结构 | 任何数据 |
| `data-baseline.sql` | 16 张配置表、576 行种子数据 | 会员/活动/任务记录/流水/日志等业务数据 |

> ⚠️ `data-baseline.sql` 里的 `t_employee` 含 Argon2 密码哈希与手机号
> （上游 `smart_admin_v3.sql` 本来也带，不是新增暴露面）。
> 要发到公开仓库或交付外部前，先把非必要账号删掉、只留一个 admin。

### 这个文件是怎么来的、为什么必须有它

`schema-baseline.sql` 是**从开发库用 `SHOW CREATE TABLE` 逐表导出**的，
所以它就是库里真实的样子，不是人工维护的近似版本。

> 🔴 2026-08-31 核对发现：这句话已经不完全成立了。文件自 2026-08-23 导出后**被手工改过**
> （表从 84 张变成 64 张），而计数一处都没跟着改，README 这里写着 75、文件头写着 84、
> 实际是 64 —— 三个数互不相同。少掉的 23 张已逐个确认是随功能下掉的（全仓零代码引用），
> 不是丢了，但**这正是「手工改基线」的代价**：没人能一眼看出它还准不准。
>
> 改表结构之后请重新跑 `tools/DumpSchema.java`，别手改这个文件。

在它出现之前（2026-08-22 之前），「新环境要执行哪些 SQL」这个问题**没有答案**：

| 建表语句在哪                                                              | 张数  |
|---------------------------------------------------------------------------|-------|
| `mysql/smart_admin_v3.sql`（上游 Solvela 基线，**已于 2026-08-22 删除**） | 44    |
| `activity.sql` / `lottery.sql` / `mysql/task.sql`                         | 21    |
| `member.sql` / `mall.sql`                                                 | 11    |
| **只在 `sql-update-log/` 的某个版本文件里**（该目录已删除）               | **3** |
| **任何文件里都没有**                                                      | **3** |

最后两行是致命的：`t_activity_display` / `t_file_category` / `t_file_relation` 得翻 49 个版本文件才找得到；
而 `t_task_event` / `t_task_record_flow` / `t_lottery_number_pool` **在整个仓库里根本没有建表语句** ——
照当时 README 说的「第一次部署只执行 `smart_admin_v3.sql`」，会缺掉整个营销域、会员域，外加这三张表。
（那个文件与配套的增量回放脚本已一并删除，docker-compose 的 initdb 也改成只挂两个基线。）

这也是交接文档铁律 22 的根因：**表清单靠 grep 文件永远是不全的，必须查 `information_schema`。**

---

## 🔴 维护约定：改表结构之后，<b>必须重新导出基线</b>

2026-08-22 起 `sql-update-log/` 已整个删除，**基线是唯一的真相**（为什么删见文末）。
于是流程只剩一条：

> **直接改库 → 重新导出基线 → `git diff` 核对**

也就是说，改表结构的「产物」不是一个迁移文件，而是<b>基线文件的那一段 diff</b>。
⚠️ 只改库不导出，等于这次改动只存在于你这一台机器上 —— 别人建的新环境不会有它，
而且没有任何人会发现，直到某天报 `Unknown column`。

改完重新跑导出工具覆盖基线，用 `git diff` 核对是否与预期一致：

```
cd 数据库SQL脚本/tools
java -cp <mysql-connector.jar> DumpSchema.java        # 结构基线
java -cp <mysql-connector.jar> DumpSeedData.java      # 种子数据基线（改了菜单/字典/权限才需要）
java -cp <mysql-connector.jar> VerifyFreshInstall.java # 空库验证：两个文件能否得到可用系统
```

> 上面那 3 张「任何文件里都没有」的表，就是「改了库但没有任何文件跟着改」留下的。
> 现在唯一要跟着改的东西就是基线，跑一次导出工具就完事 —— 没有借口漏。

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

## `sql-update-log/` 已整个删除

**2026-08-22：这个目录不复存在。** 先是 v3.15.0 ~ v3.69.0 共 46 个被删（前提是两个基线
已经验证过 —— 那 46 个里有 17 个含 `INSERT`，光 `t_menu` 的增量插入就横跨八个文件，
靠人按版本号顺序拼才能得到完整菜单，漏一个就少一块功能入口且不报错；这些已收敛进
`data-baseline.sql`）。随后本轮改造的 5 个也在<b>全部执行完毕</b>后一并删除：

| 版本 | 内容 | 归宿 |
|---|---|---|
| `v3.70.0` | 默认租户 `'0'` → `'taozi'` | 已执行，随后被 v3.73.0 整体推翻 |
| `v3.71.0` | 关联键 `member_name` → `member_id`，10 张表回填 9216 行 | 已执行 |
| `v3.71.1` | 状态类两张表的 `member_name` 放开 NOT NULL | 已执行 |
| `v3.72.0` | `member_name` 收口：8 张单据表降级为展示快照、2 张状态表删列 | 已执行 |
| `v3.73.0` | 删除租户维度：27 张表的 `tenant_id` 全部 DROP | 已执行 |

**删的前提只有一条：库里的状态已经和基线完全一致**，没有任何「待执行」的东西。
本轮删除前逐条确认过 —— 尤其 `v3.72.0` 一度是唯一未执行的那个，
当时库里已经是半迁移态（`t_member_wallet` 880 行里 21 行、`t_task_record` 1802 行里 111 行的
`member_name` 已是 NULL），先跑完它才删的目录。
🔴 **以后再删迁移文件，也要先回答这个问题：还有没有没跑的？**

### 那「已有环境怎么升级」怎么办

本项目<b>只有一套库</b>，而且它永远是最新的 —— 改表就是直接改它，然后导出基线。
真出现第二套需要从旧版本升上来的环境时，再按那一次的实际差异写一个升级脚本即可；
为一个不存在的环境长期维护一整条迁移链，正是这次要消灭的成本
（同一个理由也适用于 `tenant_id`，见 `docs/营销中台-会话交接文档.md` §13.4）。

需要翻旧账时用 `git log -- 数据库SQL脚本/mysql/sql-update-log/` —— 文件内容都在 git 历史里，
关键决策与踩坑记录也已沉淀进 `docs/营销中台-会话交接文档.md`。

---

## 其他文件

- `t_menu.sql`：菜单数据补丁。
- `*造数*.sql` / `*联调*.sql`：各模块的联调造数，可重复执行，尾部通常带清场语句。用前先读注释。
- `诊断-审计时间异常排查.sql`：时区问题的排查脚本，见铁律 10。
