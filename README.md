# Solvela

会员积分营销中台 —— 一套后端 + 两个前端，覆盖**会员、资产账本、营销玩法、积分商城、风控**五个域。

玩法侧有活动、抽奖、任务、彩票四类；交易侧是积分商城（商品/SKU/兑换/收货/履约）；
两侧共用一套奖品配置与资产账本，由脚本引擎做编排、由风控做预算与防刷。

| | |
|---|---|
| 后端 | JDK 25 · Spring Boot 4.1 · MyBatis-Plus · MySQL 8.4 · Redis 8 · RabbitMQ · MinIO(S3) |
| 管理端 | Vue 3 · Ant Design Vue 4 · Vite · Tailwind v4 |
| C 端 | Vue 3 · TypeScript · Pinia · vue-router · Vite（自建 UI 组件，无组件库） |
| 部署 | Docker Compose + Cloudflare 隧道（**零公网端口**） |
| 许可 | MIT |

---

## 目录

- [进程拓扑](#进程拓扑)
- [仓库结构](#仓库结构)
- [后端模块地图](#后端模块地图)
- [前端](#前端)
- [本地启动](#本地启动)
- [部署](#部署)
- [测试与质量门](#测试与质量门)
- [设计文档索引](#设计文档索引)
- [几条红线](#几条红线)

---

## 进程拓扑

后端打成**三个进程**，不是一个单体，也不是一堆微服务：

```
                    ┌──────────────────────────────────────────┐
  管理端浏览器 ─────►│ admin        :1024  管理后台全部接口      │
                    │                     员工登录 + 菜单权限   │
                    └──────────────────────────────────────────┘

                    ┌──────────────────────────────────────────┐
  C 端浏览器/App ───►│ app-gateway  :1025  C 端网关             │
                    │                     会员令牌签发/解析     │
                    │                     ⚠ 没有数据源、没有    │
                    │                       JDBC 驱动          │
                    └────────────────┬─────────────────────────┘
                                     │ HTTP（member-api / marketing-api 两个契约）
                                     ▼
                    ┌──────────────────────────────────────────┐
                    │ app-biz      :1026  C 端业务             │
                    │                     会员 · 营销 · 资产    │
                    │                     只提供 /internal 接口 │
                    └──────────────────────────────────────────┘

           三个进程 ──► MySQL / Redis / MinIO（同一套存储）
```

**为什么 admin 和 C 端要分开**（写在 `AppApplication` 类注释里，这里摘要）：

1. **故障隔离** —— 后台跑一个大导出把进程拖垮时，用户侧不该跟着白屏；
2. **鉴权是两套** —— 员工登录（`t_employee` + 菜单权限）与会员登录（`t_member`，无权限模型）没有一行共用逻辑；
3. **容量特征相反** —— 管理端低并发重查询，C 端高并发轻查询，扩缩容策略不同。

**为什么网关和业务要分开** —— 网关 2026-09-01 摘掉了全部 base 模块，
classpath 上**连 MySQL 驱动都没有**。这不是约定，是 `AppBoundaryTest` 断言守着的物理事实：
从「约定网关不查库」变成「物理上不具备」。

**将来要拆的是资产域**。契约（`solvela-member-api` / `solvela-marketing-api`）已经按
**将来的服务边界**划好了，今天由 `app-biz` 一个进程实现两套契约。
真要拆时是加一个进程壳 + 换一个 bean + 改 compose 里一个 URL，业务代码一行不动。

---

## 仓库结构

```
solvela/
├── solvela-api/          后端，22 个 maven 模块
├── solvela-admin-web/    管理端前端
├── solvela-app-web/      C 端前端
├── 数据库SQL脚本/         建库基线 + 造数脚本
│   └── mysql/
│       ├── schema-baseline.sql   表结构基线
│       ├── data-baseline.sql     种子数据（菜单/字典/权限）
│       └── README.md             🔴 部署前先读这个
├── deploy/               nginx / cloudflared / web 镜像
├── docs/                 各模块设计文档与交互原型
├── docker-compose.yml    一键部署
└── .env.example          全部密钥与配置项，含每一把钥匙的含义
```

---

## 后端模块地图

`solvela-api/pom.xml` 里的 **`<modules>` 顺序即依赖方向：上面的不认识下面的**。
写反了 Maven 直接报循环依赖 —— 这条边界是编译器在守，不靠自觉。

### 契约层（谁也不依赖）

| 模块 | 职责 |
|---|---|
| `solvela-contract` | 全仓共享的枚举与错误码。反应堆里的第一个模块 |
| `solvela-model` | 实体与持久化模型 |
| `solvela-member-api` | 对外契约：会员、资产、提案、优惠配置。**只有接口与 record** |
| `solvela-marketing-api` | 对外契约：活动、抽奖、任务、彩票、奖品、商城。同上 |
| `solvela-auth` | 令牌的签发、解析、吊销。**只用 Redis，不碰数据库**（网关要它，而网关不能有 JDBC） |

> 两个 `*-api` 的粒度对齐的是**将来的服务**，不是今天的域模块 ——
> 会员/资产/提案/优惠配置将来同属一个服务，所以共用一个 api 模块。

### 技术底座（2026-08-30 拆成七块）

| 模块 | 职责 |
|---|---|
| `solvela-base-core` | 工具、JSON、校验、常量、链路 id、配置装载。**不碰 DB/Redis/对象存储** |
| `solvela-base-redis` | 缓存管理器、RedisService、Redisson、分布式锁、发号器 |
| `solvela-base-data` | MyBatis-Plus、数据源、p6spy、Dao 基类、自动填充、系统配置表 |
| `solvela-base-file` | 对象存储、文件模块、SonicExcel 读写 |
| `solvela-base-mail` | 邮件模板渲染与发送 |
| `solvela-base-mq` | RabbitMQ 连接、交换机/队列声明、JSON 编解码 |
| `solvela-base-web` | 各端共用的 HTTP 基础件：链路 id 过滤器、服务间调用的错误出口 |

> 🔴 **别再造一个「什么都有」的聚合模块。** 旧的 `solvela-base` 门面已于 2026-08-31 删除 ——
> 它是把 JDBC 驱动带上网关、把 S3 SDK 和 Excel 引擎带进 C 端进程的最短路径，
> 而且写的时候完全无感。每个模块现在列出真正用到的那几块。

### 业务域

| 模块 | 职责 | 主要子包 |
|---|---|---|
| `solvela-member` | 会员域 | `auth` `register` `device` `email` `id`(发号) `loginlog` `operationlimit` |
| `solvela-scriptengine` | 脚本引擎（QLExpress），玩法编排 | `core` `loader` `runtime` `handler` `spi` |
| `solvela-risk` | 风控：预算池、防刷、提案 | `engine` `promotionconfig` `promotiongroup` `proposal` `spi` |
| `solvela-prize` | 奖品配置与发奖流水 | `prizeconfig` `prizelog` `runtime` |
| `solvela-ledger` | 资产账本：钱包、发放、优惠券、实物履约、对账 | `wallet` `grant` `coupon` `logistic` `transaction` `stat` |
| `solvela-marketing` | 玩法中台 | `activity` `draw`(抽奖) `task`(任务) `lottery`(彩票) `consumer`(发奖消费) `dispatch` `stat` |
| `solvela-mall` | 积分商城 | `commodity` `sku` `category` `order` `address` `favorite` `exchangelimit` |

`prize` 横跨活动侧与资产侧，所以单独成模块 —— 硬塞进任何一端都会造出新的环。

`solvela-mall` 于 2026-09-05 从 `solvela-marketing` 拆出：
那四个玩法共享活动/奖池/编排脚本，而商城是**交易**，邻居是钱包与履约。
**它不许依赖 `solvela-marketing`**，由 `PlayBoundaryTest` 守着。

### 端（HTTP 层）

| 模块 | 进程 | 端口 | 说明 |
|---|---|---|---|
| `solvela-admin` | admin | 1024 | 管理端全部接口。`solvela-web` 已于 2026-08-31 并入 |
| `solvela-app` | app-gateway | 1025 | C 端网关。**只有启动类、HTTP 薄壳与错误出口** |
| `solvela-app-biz` | app-biz | 1026 | C 端业务。只提供 `/internal` 接口，由网关转发 |

端模块刻意排在业务域之后：**域模块不认识端，只有端认识域。**

---

## 前端

### `solvela-admin-web` —— 管理端

Vue 3 + Ant Design Vue 4.2.6 + Vite + Tailwind v4，另有 CodeMirror（脚本编辑）、
ECharts（图表）、wangEditor（富文本）。

```bash
cd solvela-admin-web && npm install && npm run dev
```

dev server 在 `http://localhost:8081`。

> 🔴 **dev server 刻意没有 Vite proxy，不要「顺手补上」。**
> 前端通过 axios 的 `baseURL` 直连后端（`VITE_APP_API_URL`，dev 即 `http://127.0.0.1:1024`），
> 跨域由后端 CORS 放行。2026-08-01 有人给它加过 `'/'` 全匹配代理，
> 结果首页 HTML、`/@vite/client`、每个源码模块请求全被转发到 1024，页面直接白屏。
> 详见 `vite.config.js` 里那段注释。

### `solvela-app-web` —— C 端

Vue 3 + TypeScript + Pinia + vue-router，**自建 UI 组件**（`src/ui/`：Button/Card/Cell/Field/Sheet/TabBar…），
不引组件库。Node ≥ 24。

```bash
cd solvela-app-web && npm ci && npm run dev
```

dev server 在 `http://localhost:5273`，`/api` 代理到 `127.0.0.1:1025`。

分层约定：

```
src/
├── ui/           基础组件，自动注册，页面里不用 import。命名不带前缀
├── components/   业务组件
├── views/        页面
├── api/          按后端域切（auth/mall/task/promo/records/assets/activity/address/device/session）
├── stores/       Pinia（auth / theme）
├── composables/  useAsync / useCodeSender / useFavorites
└── testing/      测试夹具
```

> **`ui/` 不 import `api/`，`api/` 不 import `.vue`。**

可用脚本：`dev` `build` `typecheck` `lint` `format` `test` `ci`。

---

## 本地启动

### 0. 环境

MySQL 8.4 · Redis · MinIO · JDK 25 · Maven · Node ≥24（C 端，见 `.nvmrc`）/ Node ≥22.12（管理端）

### 1. 建库

```
mysql> SOURCE 数据库SQL脚本/mysql/schema-baseline.sql;
mysql> SOURCE 数据库SQL脚本/mysql/data-baseline.sql;
```

① 建表结构，② 灌种子数据（菜单/字典/权限/员工）。跑完就能登录。
之后按需执行 `*造数*.sql`（都可重复执行）。

> ⚠️ **先读 [`数据库SQL脚本/mysql/README.md`](数据库SQL脚本/mysql/README.md)。**
> 那里记着基线曾被手工改过、文件头与 README 的表数一度互不相同。
> 改表结构后请重新跑 `数据库SQL脚本/tools/DumpSchema.java`，**别手改基线文件**。
> 换环境前建议先跑一次 `数据库SQL脚本/tools/VerifyFreshInstall.java`。

### 2. 起后端

```bash
cd solvela-api && mvn clean package
```

三个进程分别起（IDE 里直接跑启动类也行）：

| 启动类 | 端口 |
|---|---|
| `solvela.admin.AdminApplication` | 1024 |
| `solvela.app.AppApplication` | 1025 |
| `solvela.biz.BizApplication` | 1026 |

配置按 profile 分目录放在各端的 `src/main/resources/{dev,test,pre,prod}/`。
Maven profile 可选 `dev`（默认）/ `test` / `pre` / `prod`。

### 3. 起前端

见上一节。管理端 8081 → 1024，C 端 5273 → 1025。

---

## 部署

```bash
cp .env.example .env
```

把每一处 `change-me` 都改掉，然后：

```bash
docker compose up -d
```

**完整步骤看 [`deploy/README.md`](deploy/README.md)** —— 含 Cloudflare 隧道建立、DNS 记录、
管理端 Access 策略。

两件最要紧的事：

- **没有任何服务对公网发布端口。** mysql/redis/minio 从一开始就没有 `ports`；
  三个后端进程和 web 也一个都不发布。唯一入口是 cloudflared，而隧道是**出站**连接 ——
  路由器不用开任何入站端口，公网也扫不到这台机器。
- **密钥全部必填，缺一个就启动失败。** 这是刻意的：给了默认值的话，忘配的人会用一个
  全世界都知道的密钥把用户地址加密进库、给设备签名 —— 功能完全正常，
  而整套设计的前提直接归零，且没有任何迹象。宁可起不来。
  其中三把**上线后不能改**，`.env.example` 里每把都标了属于哪一类。

---

## 测试与质量门

后端 153 个测试文件。其中一类值得单独说 —— **架构守卫测试**，它们把架构约束变成会失败的断言：

| 测试 | 守的是什么 |
|---|---|
| `AppBoundaryTest` | 网关进程不许出现数据源 / Mapper / MySQL 驱动 |
| `NoWebDependencyTest` | `base-core` 不许依赖 web |
| `LedgerBoundaryTest` | marketing ↮ ledger 的缝 |
| `MallBoundaryTest` / `PlayBoundaryTest` | mall 与玩法互不依赖 |
| `MallLedgerBoundaryTest` | mall ↮ ledger 的缝 |
| `PrizeDispatchBypassTest` | 不许绕过 `PrizeEventPublisher` 直接调发奖处理器（会让派发跑进事务里） |

改动如果撞上这些边界，构建会失败并在失败信息里说明原因 —— **先读那段话，再决定是改代码还是改边界。**

前端：

```bash
cd solvela-app-web && npm run ci
```

即 `format:check` + `lint` + `typecheck` + `test` + `build`。
GitHub Actions 有 `app-web CI`，只在 `solvela-app-web/**` 有改动时触发。

> **管理端目前没有 lint 配置，也没有 CI。**

---

## 设计文档索引

`docs/` 下是各模块的设计文档与可直接打开的交互原型（`.html`）。

**架构与底座**
- [后端升级白皮书-SpringBoot4.md](docs/后端升级白皮书-SpringBoot4.md)
- [C端网关-API契约方案.md](docs/C端网关-API契约方案.md)
- [文件模块-架构设计文档.md](docs/文件模块-架构设计文档.md)
- [SonicExcel-架构设计文档.md](docs/SonicExcel-架构设计文档.md) · [泡菜工厂图鉴](docs/SonicExcel-泡菜工厂图鉴.md)
- [前端优化白皮书.md](docs/前端优化白皮书.md)

**玩法与营销**
- [任务引擎核心运转与排雷白皮书.md](docs/任务引擎核心运转与排雷白皮书.md) · [任务开发指南](docs/任务开发指南文档.md) · [任务中台-操作说明书](docs/任务中台-操作说明书.md) · [任务中台-改进技术方案](docs/任务中台-改进技术方案.md)
- [彩票中台-实现技术方案.md](docs/彩票中台-实现技术方案.md)
- [活动创建向导-实现技术方案.md](docs/活动创建向导-实现技术方案.md)
- [奖池抽奖模块原型-变更说明.md](docs/奖池抽奖模块原型-变更说明.md)
- [营销中台-数据统计方案.md](docs/营销中台-数据统计方案.md) · [会话交接文档](docs/营销中台-会话交接文档.md)

**会员与触达**
- [通知与公告-实现技术方案.md](docs/通知与公告-实现技术方案.md) · 消息中心（尚未实现）

**脚本引擎**
- [脚本引擎核心架构与扩展白皮书.md](docs/脚本引擎核心架构与扩展白皮书.md)
- [脚本引擎-版本化与挂载方案.md](docs/脚本引擎-版本化与挂载方案.md)

**运维与治理**
- [定时任务模块-重构技术方案.md](docs/定时任务模块-重构技术方案.md) · [实测验证报告](docs/定时任务模块-实测验证报告.md)
- [枚举改造-阶段3工作清单.md](docs/枚举改造-阶段3工作清单.md) · [存量数据对账报告](docs/枚举改造-存量数据对账报告.md)
- [首页图表-口径确认清单.md](docs/首页图表-口径确认清单.md)
- [压测-抽奖并发验证.js](docs/压测-抽奖并发验证.js)

**交互原型**（浏览器直接打开）
- [活动创建向导](docs/活动创建向导原型-一体化向导.html) · [奖池抽奖](docs/奖池抽奖模块原型交互设计.html) · [任务与抽奖后台](docs/任务与抽奖模块-后台交互原型.html) · [FPE 彩票](docs/彩票中台原型-FPE彩票.html) · [活动大屏](docs/活动大屏原型.html) · [商城](docs/mall.html)

---

## 几条红线

这些约束在代码注释里都有更长的版本，动到相关位置前请先读原注释。

1. **`<modules>` 顺序即依赖方向。** 想让 A 依赖 B 而 B 在 A 下面时，先问是不是分层错了，别调顺序。
2. **不要造聚合式的 base 模块。** 见上文。
3. **网关不许有 JDBC。** 由 `AppBoundaryTest` 守着。
4. **`solvela-mall` 不许依赖 `solvela-marketing`。** 理由写在 `solvela-mall/pom.xml`。
5. **发奖必须走 `PrizeEventPublisher`。** 绕过它会让派发跑进事务里。
6. **别手改 `schema-baseline.sql`。** 用 `数据库SQL脚本/tools/DumpSchema.java` 重新导出。
7. **admin-web 的 dev server 不要加全匹配代理。**
8. **三把密钥上线后不能改**（PII 加密、PII HMAC、会员号）。改了等于旧数据解不开、老会员登不进来、会员号可能撞号。

---

## License

MIT。上游基于 [1024 创新实验室 SmartAdmin](https://smartadmin.vip)，见 [LICENSE](LICENSE)。
