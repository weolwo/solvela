# Solvela

会员积分营销中台：一套后端（`solvela-api/`，Maven 多模块，打成三个进程）加两个前端（`solvela-admin-web/` 管理端、`solvela-app-web/` C 端）。

本文只放**项目专属**的入口、命令与红线。通用工程准则在全局宪法里，不在这里重复；细节都在既有文档与代码注释里，本文只负责指路——**与它们不一致时以代码为准，并订正本文**。

## 先读什么

| 要做的事 | 读 |
|---|---|
| 了解项目做到哪了 | `docs/Solvela-项目现状总览.md`（现状以它为准） |
| 动任何代码之前 | `docs/营销中台-会话交接文档.md` 的「动手前必须知道的七条」与 §0 工程铁律 |
| 动模块边界、依赖 | 根 `README.md`「后端模块地图」「几条红线」，以及 `solvela-api/pom.xml` 里 `<modules>` 的注释 |
| 动某个业务域 | `docs/业务/<域>/` 下的实现技术方案 |
| 动 C 端前端 | `solvela-app-web/README.md`（后端契约三条铁律、已定型的取舍） |
| 动数据库脚本 | `数据库SQL脚本/mysql/README.md` |
| 动部署 | `deploy/README.md`、`docker-compose.yml` 与 `.env.example` 的文件头注释 |
| 写新文档 | `docs/README.md` 的「放置规则」「写作约定」 |

读旧文档时留意三处已经过时的说法：

- 交接文档的「现状速览」与「§5 待办」停在 2026-08-01，已由现状总览取代。
- 交接文档里的 `solvela-base/common/util`、`common-api` 是拆分前的旧名。工具类现在在 `solvela-base-core` 的 `solvela.base.util` 包。
- 交接文档里的 `D:/dev_tools/...` 是当时 Windows 开发机的路径，不是项目约定。铁律 19 所说的 pom 资源过滤问题已在 pom 里修掉。

## 命令

后端，在 `solvela-api/` 下（JDK 25）：

```bash
mvn -q compile -pl <模块> -am -DskipTests
mvn test -pl <模块> -am -Dtest=<测试类> -Dsurefire.failIfNoSpecifiedTests=false
mvn -o test -pl solvela-app,solvela-app-biz,solvela-admin -am    # 全量
```

架构守卫测试（`*BoundaryTest`、`PrizeDispatchBypassTest`、`EnumMigrationRatchetTest`）扫的是 `target/classes`，必须在 compile 之后跑。

C 端，在 `solvela-app-web/` 下（Node 版本见 `.nvmrc`）：

```bash
npm ci && npm run ci    # format:check + lint + typecheck + test + build
```

管理端，在 `solvela-admin-web/` 下：

```bash
npm ci && npm run ci    # test + build
```

管理端是纯 JS，没有 lint 和类型检查，这是刻意的（理由在 `.github/workflows/admin-web-ci.yml`）。不要顺手补。

## 结构约定

- **`<modules>` 的顺序即依赖方向**，上面的不认识下面的。想让 A 依赖排在它下面的 B 时，先问是不是分层错了，不要调顺序。
- **三个进程**：`solvela-admin`（1024，管理端，**独占全部定时任务**）、`solvela-app`（1025，C 端网关，**classpath 上没有 JDBC**）、`solvela-app-biz`（1026，只提供 `/internal` 接口）。
- **端模块只有启动类、HTTP 薄壳与错误出口**，业务逻辑放域模块。
- **域内的包**：`<域>/<子域>/{dao,domain,manager,service}`，`domain` 下是 `dto` / `query` / `command`。
- **`Form` 与 `VO` 只存在于端模块**（`solvela-admin` 的 `module/<域>/domain/{form,vo}`），在 Controller 里与域的 `Query` / `DTO` 互转。VO 不进数据层。
- **跨域调用走契约**（`solvela-member-api` / `solvela-marketing-api`，只有接口与 record）。跨域聚合放网关，不在 app-biz 里跨域打结。
- **不许出现的依赖**：`solvela-mall` 与 `solvela-external` 不依赖 `solvela-marketing`；`solvela-notification` 不依赖任何业务域；不造聚合式的 base 模块。
- **发奖必须走 `PrizeEventPublisher`**，不直接调发奖处理器。
- **管理端接口的权限**用 `@RequiresPermission("<资源>:<动作>")`，权限串与 `t_menu` 的功能点一一对应。管理员账号有全量权限，权限串写错在开发期测不出来。

## 动之前先停下来确认

这些改了不可逆，或者会造成资损：

- **三把上线后不能改的密钥**（PII 加密、PII HMAC、会员号），见 `.env.example`。
- **彩票的四条不可逆约束**：发号游标不回滚、主密钥发过号后不可轮换、`issue_no` 不可改、`number_length` / `total_count` 发号后冻结。见交接文档 §4。
- **业务编码**（活动、奖品、奖池、任务模板）创建后不可改。
- **`schema-baseline.sql` 与 `data-baseline.sql` 不手改**，用 `数据库SQL脚本/tools/` 下的 `DumpSchema.java`、`DumpSeedData.java` 重新导出，换环境前跑 `VerifyFreshInstall.java`。
- **只观察不拦截的开关**（设备令牌、二次验证、CSP）切到拦截，有各自的切换判据，见现状总览 §4。

## 本项目里会静默出错的地方

后端：

- **状态 / 类型列全是枚举**（实现 `BaseEnum`，字段名必须叫 `value`，不手写 `getValue()`）；0/1 标记是 `Boolean`。加回 `Integer` 状态常量，`EnumMigrationRatchetTest` 会红。
- **`SolvelaBeanUtil.copy` 对类型不匹配的属性静默跳过。** 改一个列的类型，要把 Entity、DTO、Command、Query、Form、VO、DAO 形参与调用点一次改完。
- **`create_time` / `update_time` 由 DDL 产生**，代码里不填，实体上不加 `@TableField(fill = ...)`。
- **MySQL 服务端跑在 UTC**，应用靠连接串锁会话时区。手工跑带 `NOW()` 的脚本前先 `SELECT NOW(), @@session.time_zone;`。
- **guava 与 hutool 已移除，不要引回。** 写工具方法前先翻 `solvela.base.util`；那里的类多数配了语义固化测试，改实现前先看测试。
- **JSON 是 Jackson 3**（包名 `tools.jackson.*`）。主代码里没有一处 Jackson 2 的 `databind`，新代码不要写回 `com.fasterxml.jackson.databind`。
- **新增配置块要四个 profile 一起加**（`src/main/resources/{dev,test,pre,prod}/`），只加 dev 会让其它环境启动失败。

文件模块：

- 下载路径是 `/support/file/download/{id}`，别漏掉 `/support`。
- 文件一律公开，没有「私有文件」这个概念。
- 管理端显示图片走 `components/support/file-thumb`，不直接 `<img :src>`。
- 业务保存时必须调 `fileAssetService.confirm(...)` 登记引用，否则会被孤儿清理当垃圾删掉。
- 素材分类按 `categoryCode` 引用，不用自增 ID。

前端：

- **两个前端不共享任何约定**，不要为了「一致」把一边的做法搬到另一边。
- **C 端**：ID 经 `toId()` 当字符串；金额走 `utils/money`；时间走 `utils/datetime`；响应没有信封，按 HTTP 状态码判成败；`ui/` 不 import `api/`，`api/` 不 import `.vue`。
- **C 端 dev 必须走同源代理**，不要改成直连 1025——线上没有 CORS。
- **管理端 dev server 不加全匹配代理**，它靠 `baseURL` 直连 1024。

## 提交与注释

- **提交信息用中文，一句话写清改了什么、为什么**，写后果而不是写动作。风格看 `git log`。一个提交一件事，跨端改动不混进单端提交。
- **注释用中文，写「为什么」和「不这样会怎样」**。真正的红线用 🔴 标出，需要留意的用 ⚠️。引用别处时写类名或文档的完整路径。
- **改了行为，同步订正引用它的文档与注释。** 现状类的数字（表数、测试数、模块数）尽量由工具输出，不手抄。
