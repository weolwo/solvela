# C 端网关 API 契约方案（solvela-app → 域服务）

> 目标：`solvela-app` 作为**编排网关**，今天同 JVM 调用、将来拆成 `app-activity` / `app-member` 等独立服务时**调用方代码一行不改**。
> 范围：本轮优先 activity / member / ledger(资产) 三个域 + 抽奖执行，服务于「活动全链路验证」。
> 日期：2026-08-30

---

## 0. 一句话结论

**建 `*-api` 模块，接口用 Spring HTTP Interface（`@HttpExchange`）声明，今天由本地 `@Service` 实现、将来由 `@RestController` 实现 —— 两种形态共用同一个接口，`solvela-app` 注入的类型不变。**

不引入 Dubbo：Dubbo 3.3 的 starter 仍绑在 Boot 3.x 的自动装配机制上，Java 25 与 Boot 4.1 不在其测试矩阵内；且它捆绑的注册中心/配置中心是另一套运维，与「现在两个进程共库共 Redis」的现状不匹配。

---

## 1. 模块拓扑

### 1.0 api 模块的粒度 = 将来的服务粒度，不是今天的 maven 模块粒度

目标服务只有两个（外加网关）：

| 将来的服务 | 装哪些实现模块 | 对应的 api 模块 |
|---|---|---|
| **app-marketing** (1026) | `solvela-activity`、`solvela-marketing`(draw/lottery/mall/task/stat)、`solvela-prize`、`solvela-scriptengine` | `solvela-marketing-api` |
| **app-member** (1027) | `solvela-member`、`solvela-ledger`(资产)、`solvela-risk`、**`solvela-consumer`(发奖派发)**、`solvela-prize`(读配置/写流水) | `solvela-member-api` |
| **solvela-app** (1025) | 网关，无域实现 | 依赖上面两个 |
| **solvela-admin** (1024) | 后台，仍是单体，装全部 | — |

**所以只建两个 api 模块，不按今天的 maven 模块一一对应。** 特别地：

- **活动与商城同属一个服务**，所以只有一个 `solvela-marketing-api`，不按今天的 maven 模块再切细 —— 它们将来在同一个进程里，拆成两个 api 只会让网关同时持有两个指向同一台机器的 client；
- **不建 `solvela-ledger-api`。** 资产属于 app-member 服务，契约进 `solvela-member-api`；
- **一个 api 模块里按实现归属切多个接口**：`solvela-marketing-api` 里有 `ActivityQueryApi`（solvela-activity 实现）和 `DrawApi`（solvela-marketing 实现）。
  不能合成一个大接口 —— 一个 Spring bean 没法只实现半个接口，而这两半在不同的 maven 模块里；
- **实现模块保持今天的域切分不动。** `solvela-marketing` 里的 `DrawExecuteService` 去 implements `solvela-marketing-api` 里的 `DrawApi` —— 跨 maven 模块实现同一个 api 模块的接口是正常的。拆分那天是「把若干实现模块打进一个服务」，api 一行不用改。

```
solvela-contract         ← 新增。纯枚举 + 错误码，零第三方依赖
solvela-marketing-api     ← 活动 / 抽奖 / 任务 / 奖品 / 商城 的对外契约
solvela-member-api       ← 会员 / 资产 / 提案 / 优惠配置 的对外契约
   ↑ implements（可由多个实现模块分别实现）
solvela-activity + solvela-marketing + solvela-prize
solvela-member + solvela-ledger + solvela-risk
   ↑ 只依赖这两个 api
solvela-app
```

### 1.0.1 尚未定的一处

`solvela-consumer`（事件派发 + 各类发奖 handler）依赖 activity / prize / risk / scriptengine，而它的产物是「奖品变成资产」。按数据所有权应归 **app-member**（写的是资产与账本），但它读的是活动侧的配置。本轮不动它，等两个服务的库拆分方案定了再决定 —— 也可能它就该是第三个服务（异步消费者），那是另一个话题。

### 1.1 三条硬约束

| # | 约束 | 为什么 |
|---|---|---|
| 1 | `*-api` **不得依赖 `solvela-model`** | model 里有 `PiiTypeHandler`、`PiiCipher`、MyBatis entity。api 依赖它 = 把持久层塞给每一个调用方，拆进程时还得跟着带 mybatis-plus |
| 2 | 先拆 `solvela-contract`（把 `solvela/enums` + `solvela/code` 从 model 挪出来）✅ 2026-08-30 完成 | 否则 api 的 DTO 想用 `MemberStatusEnum` 就会把整个 model 拉进来。**包名刻意不变**，全仓 import 零改动 |
| 3 | `solvela-app` 的 pom **只有 `*-api`**，没有任何域实现模块 | Maven 成为硬边界。`AppApplication` 的白名单 `@ComponentScan` 与 `AppBoundaryTest` 第三条断言可以退休 —— 后台 service 在 classpath 上根本不存在，比"扫进来再排除"强一个数量级 |

### 1.1.1 `solvela-contract` 的两个依赖，以及为什么它们不算破例

contract 里的 `BaseEnum` 继承 MyBatis-Plus 的 `IEnum`（枚举 ↔ 数据库 value 的自动转换靠它），
`getValue()` 上有 Jackson 的 `@JsonValue`。所以 contract 有且只有两个依赖：

| 依赖 | 为什么可以 |
|---|---|
| `com.baomidou:mybatis-plus-annotation` | 只有接口和注解的包（约 60KB，零传递依赖），**不含任何 ORM 运行时** |
| `com.fasterxml.jackson.core:jackson-annotations` | 纯注解包，不引 databind，不绑定任何序列化实现 |

「api 不得依赖持久层」要防的是把 `SqlSession`、`TypeHandler`、entity 带给调用方，
不是字面上不许出现 mybatis 几个字母。引 `mybatis-plus-core`（含 ORM 运行时）才是破例。

🔴 **contract 被所有人依赖，它多一个依赖全仓每个模块就多一个。** 加之前先问一遍值不值。

### 1.2 脚本引擎不建 api 模块

`solvela-scriptengine` 是**域内实现细节**，网关永远不该直接调它。它已经有正确的形态（`ScriptEngine` 门面 + `EngineContext` 双通道）。

拆微服务时，脚本引擎**跟着用它的域走**：任务与抽奖都在 app-marketing，它就打进 app-marketing 服务，不独立成服务 —— 它是个库，不是个服务。

C 端请求里也绝不能出现脚本变量：`activityCode` / `memberId` 由域服务自己绑进 `EngineContext`，走**内部通道**（`bindInternal`），运营的脚本视野里看不到。这正是 `EngineContext` 拆两条通道的用途。

---

## 2. 契约形态

### 2.1 命名分层（COLA 惯例）

| 角色 | 后缀 | 说明 |
|---|---|---|
| 写入参 | `XxxCmd` | 一次业务动作 |
| 查询入参 | `XxxQry` | 多条件查询才需要；单参数直接写参数 |
| 出参 | `XxxView` / `XxxResult` | 只读 |
| 接口 | `XxxApi` | 方法粒度 = **业务用例**，不是 CRUD |

**入参出参一律用 record**，与全仓其它地方一致。曾经考虑过入参用 `class + @Builder`（record 加
component 会破坏老调用方编译的规范构造器），结论是**现在不值**：api 与实现在同一个反应堆里一起编译、
一起发版，不存在版本偏斜。等真拆成独立发版的服务、出现「客户端还在用上个版本的 api」时再换，
那时换的是新增字段的那几个 record，不是全部。

### 2.2 内外都不要信封

对外为什么没有信封，见 `ApiErrors` 的类注释（状态码给基础设施看、code 给客户端代码看），已定稿。
**对内同样没有** —— 曾经在这里写过一版「内部用 `Result<T>`（success/code/message/data）」，已撤回。

理由：`Result<T>` 就是 `ResponseDTO` 换个名字装在内部。而"跨进程不能抛异常"推不出"要统一信封"，
它只推出**预期内的失败必须由返回值表达**。第 1 步做出来的 `MemberAuthResult` 就是正确形态，
而它恰恰不是信封：

```java
// 现在这样 —— reason 是枚举，switch 漏分支编译不过；lockedSeconds 是结构化的
MemberAuthResult(MemberIdentity identity, AuthFailReason reason, long lockedSeconds)

// 包成信封会退化成 —— lockedSeconds 无处安放，只能塞进 message 让调用方去解析人话
Result<MemberIdentity>(false, "OPERATION_LIMITED", "请 3 分钟后重试", null)
```

字符串 code 也比枚举差一档：`switch` 不再穷尽检查，加一个失败原因不会编译报错。

### 2.3 三条返回值规则

| 情况 | 返回什么 |
|---|---|
| 只读查询 | 直接返回 view，查不到返回 `null` |
| 有多种**预期**结果的写/校验 | 带 reason 枚举的 record，照 `MemberAuthResult` 的形状 |
| **意外**失败（库挂了、代码 bug） | 照常抛异常 → 跨进程后是 5xx → `RestClient` 抛 → 网关兜底成 `INTERNAL` |

🔴 **域里不许再用 `BusinessException` 表达「活动已下线」这类预期内结果** —— 跨进程后它会变成 500，
用户看到「服务开小差了」，监控上多一条假的服务端错误。预期内的分支一律进 reason 枚举。
这条比有没有信封重要得多。

### 2.4 不要用 `ResponseEntity` 当契约类型

`ResponseEntity` 带 status 和 headers，谁返回它谁就在决定 HTTP 语义。放进 api 契约会有三个后果：

1. **域开始决定状态码**，而那是网关唯一的活（`AuthFailReason` → `ApiErrors` → 4xx）。
   两个地方决定同一件事，就是 `AppBoundaryTest` 第二条断言在防的「两个错误出口」；
2. **本地实现被迫构造 HTTP 对象**。让同 JVM 的 `MemberAuthService` 去 `return ResponseEntity.ok(...)`，
   等于宣布「这个服务只能被 HTTP 调用」—— 与「service 收 `HttpServletRequest` 就没法复用」同一个错误；
3. `@HttpExchange` 客户端本来就不需要它：返回类型写业务类型即可，Spring 自己处理状态码。

`ResponseEntity` 用在 controller（`MemberLoginController.logout` 返回 204 就是对的），不用在契约层。

---

## 3. 数据怎么传：业务参数 vs 调用上下文

**规则：「谁的一次业务动作」进 DTO；「这次调用怎么发生的」进传输头。**

| 数据 | 走哪 | 为什么 |
|---|---|---|
| `memberId` | **显式字段，进 Cmd/Qry** | 见 §3.1 |
| `requestId`（幂等键） | **进 Cmd** | 它是业务语义（哪一次抽奖），不是传输细节。`DrawExecuteCommand` 已经这么做了 ✅ |
| `activityCode` / `poolCode` 等 | 进 Cmd/Qry | 业务参数 |
| `clientIp` | **进 Cmd** | 它是**落库的数据**（`t_member_login_log.client_ip`），不是传输元信息。而且拆分后只有网关知道真实客户端 IP，走 header 还要额外解决「下游凭什么信这个头」 |
| `deviceType` / 渠道 | **进 Cmd** | 同上，也是落库的列 |
| `traceId` | **不进签名**，走 MDC | 对所有接口一致，塞进每个 Cmd 是噪音，还会因为「某个调用点忘了填」而静默变空。今天域服务直接读 `Trace.id()`（MDC）；拆分后由服务端 Filter 把 `X-Trace-Id` 放进 MDC —— **同一行代码两种场景都对** |

> ⚠️ 为此 `Trace` 从 `solvela.app.web` 移到了 `solvela.base.trace`：MDC 的 key 只能有一个定义处，
> 而域模块不可能反向依赖网关。`TraceFilter` 仍留在 app —— HTTP 行为归 app，MDC key 归 base。

### 3.1 `memberId` 必须显式传，不要藏进 ThreadLocal

`DrawExecuteCommand.memberId` 的注释写着「C 端接入后改为从登录态获取并删除该字段」—— **这条要推翻**。

三个理由：

1. 域服务不该有"当前登录用户"这个概念。同一个 `DrawApi.execute` 会被 C 端、后台补发、定时任务三种调用方调用，后两者没有登录态；
2. ThreadLocal 跨线程就丢 —— 网关的并发读用虚拟线程扇出，异步派发、`@Async` 事件全部丢；
3. 拆成 RPC 后 ThreadLocal 根本传不过去，届时要把每个方法签名都改一遍，正是本方案要避免的返工。

**身份在网关解析（`CurrentMember.require()`），然后作为普通参数传下去。**

### 3.2 只传 `memberId`，不传整个 `MemberPrincipal`

昵称、头像是展示数据，域服务不需要；传过去还得考虑缓存一致性。下游确实要会员资料时，自己调 `MemberApi.getProfile(memberId)`。

---

## 4. 公共 api 与编排边界

### 4.0 一组公共 api，所有活动共用

玩法差异全部收在**脚本**和各玩法模块里，不体现在接口上。前端对接一次，新增第四种玩法一行不用改。

| 方法 | 用途 | 状态 |
|---|---|---|
| `getActivityRule(code)` | 活动信息 + 时间窗 + 状态 + 展示配置 + 规则正文 | ✅ 已实现 |
| `draw(cmd)` | 抽奖（走脚本编排） | ✅ 已实现（2026-08-30） |
| `claimPrize(cmd)` | 领取奖励 | ⏳ |
| `getPrizeRecord(qry)` | 我的奖励：**可领的** + 历史记录（app 拿它判断要不要弹窗） | ⏳ |
| `getTaskCenter(code, memberId)` | 我的任务中心 | ⏳ |
| `getLotteryIssue(code)` | 当前期号 | ⏳ |
| `getLotteryRecord(qry)` | 我的彩票记录 | ⏳ |

**方法跟着实现一个一个加**：先摆七个签名再让实现抛 `UnsupportedOperationException`，
等于给前端一份会骗人的接口文档。

通用性的代价是入参里有一个开放的 `params`。但**别让脚本自己判空** ——
用 `ScriptScene` 已有的 `required(...)` 声明必填变量，执行前校验，报的是变量名而不是脚本里的 NPE。

🔴 跨玩法的方法（`getPrizeRecord` / `getTaskCenter` / `getLotteryRecord`）必须走 **SPI**：
活动域是 marketing 的上游，不能反向依赖。照 `ActivityRefProvider` 的形状做依赖倒置，
各玩法模块实现并注册（`List` 注入 + 自报 `supportType()`，**不要 `Map<枚举,Provider>`** ——
本项目在那上面踩过三次静默失效的坑）。

### 4.1 抽奖的真实链路：编排在脚本里，不在网关里

```
app  POST /activity/{code}/draw
  └→ ActivityApi.draw(cmd)                  ← 不含 poolCode
       ├─ Java：活动校验（存在/上线/在参与窗内）
       ├─ Java：会员校验（存在/未冻结）
       ├─ Java：幂等（requestId 去重）
       └─ 脚本 ACTIVITY_PLAY
            ├─ 脚本按次数/身份/时段算出 poolCode
            └─ 脚本调用 draw(poolCode) → DrawExecuteService
       ← DrawResultView
```

**`poolCode` 绝不能由客户端传**：那等于让客户端绕过编排自己挑池子，
是运营配置里最不该开放的一个参数。所以 `DrawApi`（收 poolCode 的那个）
**不是 C 端接口**，只给脚本函数适配器、内部工具与联调用。

### 4.2 🔴 三件必须由 Java 做，绝不能下放到脚本

脚本是**运营写的**，漏一句就是线上事故。

| # | 必须在 Java 里 | 下放到脚本会怎样 |
|---|---|---|
| 1 | **准入校验**（活动上线、在参与窗内、会员未冻结） | 脚本漏一句，下线的活动照样能抽 |
| 2 | **幂等**（`requestId` 去重，拦在脚本执行前） | 脚本重跑一次就是重复发奖，多发的那份没人会主动来报 |
| 3 | **副作用顺序**：一次执行只准调用**一次**有副作用的函数，且必须是脚本最后一步 | 见下 |

第 3 条是这个设计唯一的结构性风险，展开说：

现有三个脚本场景（`TASK_RULE` / `POOL_ENTRY` / `ACTIVITY_RULE`）**全是纯谓词**，
返回 Boolean，重跑无害。`ACTIVITY_PLAY` 是第一个**有副作用**的场景 —— 脚本真的会扣库存、发奖、写流水：

- 脚本在 `draw()` 之后再做别的然后抛异常 → DB 事务回滚，但 **Redis 的库存预扣和幂等键不回滚**，库存凭空少一份；
- 脚本里写了两次 `draw()` → 抽两次，而这在脚本里看起来完全正常；
- 脚本超时被中断 → 奖发没发，无从判断。

所以 `ACTIVITY_PLAY` 在 `ScriptScene` 里**显式标注有副作用**，并由引擎做硬约束：
同一次执行里有副作用的函数只准调一次。**这道约束写在 Java 里，不写在脚本规范文档里。**

✅ 2026-08-30 已实现：`@ScriptFunction(sideEffect = true)` → `EngineFunctionMeta` →
`QLExpressFunctionAdapter.guardSideEffect()`，哨兵放在 `EngineContext` 的**内部通道**
（脚本看不见也改不掉），**在调用之前拦**而不是调用之后记账 —— 记账式实现发现问题时奖已经发出去了。

同一批还落地了另一条防线：会员号/活动编码/幂等键走**内部通道**（`ActivityPlayKeys`），
脚本变量里那份只供脚本读。否则一段 `memberId = 10086; return draw_draw('POOL_A');` 就能替别人抽奖。

### 4.3 写操作只调一次，永远

🔴 **绝对不能这样写：**

```
app: MemberApi.deductScore(memberId, 100)   // 扣积分（app-member 服务）
app: ActivityApi.draw(...)                  // 抽奖（app-marketing 服务）
```

拆成微服务后，这两次网络调用中间断电 = 用户扣了分没抽奖，没有任何补偿路径。

`DrawExecuteService` 明说自己**不做资产扣减**，"由上游业务算完再调进来"。
那个"上游业务"是 **app-marketing 内部**的编排（`ActivityDrawFacade` / `ACTIVITY_PLAY` 脚本），
在一个本地事务里完成。**这条从今天单体阶段就要守**。

### 4.4 判据

| 场景 | 谁编排 |
|---|---|
| 多个域的**只读**数据拼页面 | app（并发扇出，虚拟线程 + `CompletableFuture`；单下游 800ms，总预算 3s，非关键项失败降级不整页失败） |
| 一次写 + 若干读 | app（读用于组装响应） |
| **两次及以上的写**，或跨域一致性要求 | 域内编排，app 只调一次 |

---

### 4.5 活动的三个时间窗（2026-08-30 新增 `data_end_time`）

> 后台已可配置：活动编辑表单与创建向导都加了「数据截止时间」（选填，前端做即时校验，
> 真正的约束在 `ActivityConfigService.validateDataEndTime`）。活动列表页暂未加这一列。

```
start_time ──── data_end_time ──── end_time
[  可参与、可领奖  ][  只可领奖、可查看  ][ 已结束 ]
```

此前只有 `end_time` 一个时刻，「参与截止」与「活动结束」没法分开表达。
运营的做法是把 `end_time` 提前，代价是活动页跟着提前下线，**用户领不到已经中的奖**。

- 允许为空，为空即「与 `end_time` 相同」，行为与加列之前完全一致；
- 刻意不给默认值 —— 有默认值的话，「没配」和「配成与结束时间相同」看起来一模一样，将来想区分就区分不了了；
- `ActivityRuleView.joinable()` 按 `dataEndTime` 判，`claimable()` 按 `endTime` 判。
  ⚠️ 这两个方法是给**展示**用的（按钮要不要置灰），真正的准入判定在服务端 —— 客户端算的是客户端的时钟。

---

## 5. 幂等与重试

| 类型 | 规则 |
|---|---|
| 读 | 天然幂等，可重试。超时 800ms，重试 1 次 |
| 写 | **必须带 `requestId`**，服务端保证幂等。带幂等键则允许重试；不带则**读超时绝不重试**（不知道是否已执行） |

`requestId` 由**客户端生成并携带**（前端一次点击一个 id），不是 app 生成 —— app 生成的话，客户端重试会拿到不同的 id，幂等就失效了。app 侧只做校验与透传。

`DrawExecuteService` 已有幂等防重实现，本方案是把它从实现细节**提升为契约要求**。

---

## 6. 版本兼容规则

| 规则 | 说明 |
|---|---|
| 出参 record **只加字段，不改不删** | 加 component 对调用方无损（除非用了解构模式） |
| 入参用 class + `@Builder` | record 加 component 会破坏规范构造器，调用方编译失败 |
| 枚举**只加值，不改序数/不删值** | `solvela-contract` 是所有服务共享的，删一个值就是全链路二进制不兼容 |
| 单体阶段 api 与实现**同版本发布** | 不搞独立版本号，那是拆开之后才需要付的成本 |

---

## 7. 落地顺序（本轮活动链路）

| # | 步骤 | 完成判据 |
|---|---|---|
| **1** ✅ | **登录逻辑下沉到 `solvela.member.auth`**（2026-08-30 完成） | app 里**一个 DAO 都没有**（见 §9）。21 个测试全绿 |
| **2** ✅ | 拆 `solvela-contract`：`solvela/enums` + `solvela/code` 从 model 挪过去，model 反过来依赖它（2026-08-30 完成） | 包名不动，全仓 import 零改动；contract 只依赖两个纯注解包（见 §1.1.1） |
| **3** ✅ | 建 `solvela-marketing-api` / `solvela-member-api`（只两个，理由见 §1.0）（2026-08-30 完成） | api 模块的 pom 里只有 contract + spring-web；没有 solvela-model、没有 mybatis 运行时 |
| **4** 部分 | **只放本轮链路要用的方法** | 已放：认证 3 个、活动详情/规则 2 个、抽奖 1 个。剩余次数与钱包余额**没放** —— 域里今天还没有对应的读方法（`MemberWalletService` 只有 stat/queryPage/扣加退），等真要用时连实现一起加 |
| 5 | 域模块 implements；`MemberPrincipalLoader` 回源改调 api | 认证热路径仍是「Redis 命中 → 直接返回」，miss 才回源 |
| 6 | app 换依赖：删掉 `solvela-member`，`@ComponentScan` 收缩 | app 的 pom 里只有两个 `*-api` |
| 7 | 拆 `solvela-base`，app 摘掉 JDBC（见 §9.4） | `Class.forName("com.mysql.cj.jdbc.Driver")` 在 app 进程里抛 `ClassNotFoundException` |

🔴 **第 1 步和第 5 步之间不要往 app 里加任何新的 DAO。** 现在 app 是干净的，保持住比事后清理便宜得多。

### 7.0 已决：app 暂不接活动实现（2026-08-30）

`ActivityApi.draw` 的实现（活动校验 → `ACTIVITY_PLAY` 脚本 → 抽奖）已经在活动域跑通，
但**网关侧刻意先不接**。

接上它需要 app 依赖 `solvela-activity` + `solvela-marketing` 的**实现**，
并把 `solvela.activity` / `solvela.draw` / `solvela.scriptengine` / `solvela.prize` / `solvela.risk`
加进 `@ComponentScan` —— 那两个模块里混着后台写路径（`wizardCreate`、`DrawWorkbench*`），
进了容器就离公网 controller 只差一个 `@Autowired`（§1.1 的装配面问题）。

**决定：等 HTTP 客户端形态（`@ImportHttpServices`）到位后再接**，不为了早跑一天把装配面撑大，
反正拆分时还要收回来。

代价本来是「没有任何调用方会走到 `ACTIVITY_PLAY` 这条路」（后台的 `/drawPrizeLog/execute`
是直调引擎、绕过脚本的）。**已用验收测试补上**：`ActivityPlayAcceptanceTest`（solvela-admin，10 条，
自建自删造数，不依赖任何造数脚本）覆盖活动校验的四种拒绝、脚本算出的奖池确实被传到了引擎、
挂载点的场景守卫；引擎侧的副作用约束由 `ScriptEngineTest` 的 4 条钉住。

⚠️ 仍未覆盖：**真正中奖的那一段**。中奖要奖池/奖项/映射/库存四套配置，那是
`抽奖模块-联调造数.sql` 的活，引擎门内的行为由 marketing 自己的测试负责。

---

### 7.1 已知的债，按该还的顺序

| # | 债 | 什么时候必须还 |
|---|---|---|
| 1 ✅ | ~~`DrawExecuteService` 用 `BusinessException` 表达预期内结果~~ | **2026-08-30 已还**，见 §7.2 |
| 2 | 剩余次数 / 钱包余额还没有 C 端读方法 | 活动详情页要显示它们时 |
| 5 ✅ | ~~`ACTIVITY_PLAY` 这条链路没有自动化验证~~ | **2026-08-30 已还**：`ActivityPlayAcceptanceTest`（admin，10 条）+ `ScriptEngineTest` 副作用约束 4 条 |
| 3 ✅ 潜伏 | ~~冻结不生效（安全）~~ **2026-08-30 已还**，见 §7.3。剩下的「改昵称后最多 30 分钟看到旧资料」**目前够不着** —— 全仓没有任何修改会员昵称/头像的写路径（`t_member` 只有 status 与运营备注两个写入口），没有东西会变旧 | 等真有改资料的功能时，跟那个功能一起做 |
| 4 半 ✅ | `solvela-base` **已拆成四块**（2026-08-30，见 §9.5）；app 摘掉 JDBC 仍**被决策 B 挡着** —— 同进程托管 member 实现，走 MyBatis DAO | 摘 JDBC：等第 6 步 |

### 7.2 抽奖的失败表达（债 1，已还）

改动的判据来自一个事实：**抽奖只有两个调用方，它们对「被拒」的处置本来就不同**。

| 调用方 | 怎么调 | 被拒时该怎样 |
|---|---|---|
| 脚本引擎 | 在编排里调 `DrawExecuteService`，不经过 api | **中断整段脚本** —— 异常是天然机制 |
| C 端 | 经 `DrawApi` 纯 Java 调用，不走脚本编排 | 给用户一句人话 + 一个 4xx —— 异常会变成 500 |

所以引擎**只陈述事实**：五处 `throw new BusinessException(...)` 改成
`return DrawExecuteDTO.ofReject(DrawRejectReason.XXX)`，**抛不抛由调用方决定**。
如果引擎自己抛，C 端那条路就只能 catch 了再按 message 字符串分类 —— 比不分类更糟。

三种结果从此在类型上分得开，这比"少一个异常"重要得多：

| 结果 | 判据 | 上游该做什么 |
|---|---|---|
| 中奖 | `hit=true` | 发奖 |
| 没中奖 | `hit=false, reject=null` | 机会已消耗，不发奖 |
| **没被受理** | `reject!=null` | **这一次压根没抽**，机会与资产原样退回 |

后两者的区别决定要不要退还——混在一个 message 字符串里时，这个决定只能靠猜。

**安全性**：五个 reject 分支全部在任何 DB 写入之前，所以 `@Transactional` 方法里
「返回」与「抛出」在事务上等价，没有部分提交的风险。

⚠️ 后台联调接口 `/drawPrizeLog/execute` 的行为跟着变了：被拒从错误响应变成 200 + `reject` 字段，
造数与压测脚本判定成功与否要看 `reject`。

⚠️ 还剩一处异常：`memberService.requireMemberName` 在会员不存在时抛。**刻意保留** ——
令牌有效却查不到会员是真正的异常状态，5xx 是对的。

### 7.3 冻结即时生效（债 3 的安全部分，已还）

**发现的缺陷**：`MemberPrincipalLoader` 的类注释写着「冻结时会 `revokeAll` 掉全部令牌」，
而 `revokeAll` 在全仓**只有测试在调**。实际后果是被冻结的会员还能正常用最多 30 分钟
（身份缓存 TTL）——风控封掉的刷子账号还有半小时可以继续刷。

> 教训不是「有人忘了实现」，而是**写下了一个没有测试盯着的安全承诺**。

**为什么不能随手加一行**：冻结发生在 admin 进程，令牌和身份缓存在 app 进程。
两个独立进程，Spring 的本地事件跨不过去，能跨的只有共享的 Redis。

**做法（方案 A）**：把会话存储从网关下沉到会员域 —— `solvela.member.session`：

| 搬下去的 | 留在网关的 |
|---|---|
| `MemberTokenStore` / `MemberRedisTokenStore` / `MemberAccessToken` | `AuthenticationFilter`、`CurrentMember`、`MemberPrincipal` |
| `solvela.member.session.token-ttl` / `max-sessions`（会员的规则） | `solvela.app.auth.header` / `scheme`（端怎么携带令牌） |

`MemberService.updateStatus` 冻结时在**同一个事务内**吊销全部会话：吊销失败则整体回滚，
运营看到报错可以重来；反过来（先提交再吊销）一旦吊销失败就回到了「已冻结但还能用」。

⚠️ 这不违反「令牌不该焊进认证」那条 —— 那条讲的是**签发时机**（`authenticate()` 不发令牌，
各端会话模型可以不同），而这里是**域在冻结时能吊销会话**，两件事。

**类名带 Member 前缀是必须的**：admin 进程同时装配员工会话（`solvela.admin.auth.RedisTokenStore`）
和会员域，不加前缀两个 `RedisTokenStore` 的默认 bean 名撞车、启动即失败 —— 这次就撞上了。

**回归测试**：`FreezeRevokesSessionTest`（3 条，先断言令牌本来有效再断言冻结后失效，
避免空过）。这条链路坏掉时不会有任何报错，接口照常响应，只是封不住人。

---

## 7.4 四进程拆分实录（2026-08-30，进行中）

### 已完成

| | 产物 | 验证 |
|---|---|---|
| marketing 服务 | 端模块 + 启动类 + 四套 yaml + 错误出口 + 两个 HTTP 薄壳 | `InternalEndpointMappingTest` 起真端口发真 HTTP，2/2 |
| member 服务 | 同构，薄壳接 `MemberAuthApi` | 同上，2/2 |
| 消息底座 | `solvela-base-mq`：拓扑常量、交换机/队列/死信声明、JSON 编解码 | 编译通过（还没接线） |
| outbox | `t_prize_dispatch_outbox` + 实体 + dao | DDL 已写，**待执行** |
| 发布方抽象 | `PrizeEventPublisher` + `LocalPrizeEventPublisher`，三个发布点全部改走它 | 全反应堆绿，admin 行为不变 |

### 装配过程逼出来的四件事（都不报错、只在运行期炸）

1. **`@MapperScan` 漏了 `solvela.stat`** —— 手写清单的必然产物；
2. **两个契约撞同一个 URL**：`ActivityApi` 的 `/internal/activity` + `/draw` 与 `DrawApi` 的
   `/internal/activity/draw`。启动期 Ambiguous mapping —— 这个报错顺带<b>证明了
   Spring MVC 确实认接口上的 `@HttpExchange`</b>，整套「契约只定义一次」的做法才站得住；
3. **派发链路反向依赖活动域**：`ProposalSourceResolver` 拿 activityCode 回头查活动表。
   拆开后活动配置在营销、派发在会员，不在一个进程里。改成
   **消息自带上下文**：`t_prize_log.activity_type` + `UserPrizeEvent.activityType`，
   解析器变成纯函数；
4. **`solvela.dispatch` 忘了进扫描清单** —— 同 1。

### 🔴 两个模块横跨两个服务（真要拆库前必须先拆它们）

| 模块 | 一半归营销 | 一半归会员 |
|---|---|---|
| `solvela-risk` | `promotionconfig` / `promotiongroup`（奖品关联它） | `proposal`（跟资产走） |
| `solvela-prize` | `prizeconfig`（配活动要读） | `prizelog`（发奖流水） |

现在靠「精确到子包扫描」和「两个服务都装 prize」绕过去，共库阶段无害。
**共库阶段唯一的硬规则：写路径只能有一个服务拥有**（`prize_log` 只由 member 写）。

### 为什么 MQ 之外还要 outbox

MQ 覆盖不了这个窗口：**事务提交成功 → 进程在发消息之前挂了**。
奖已判定、流水已落库，消息却没发出去，而且没有任何地方记得这件事。
publisher-confirm 也救不了 —— 确认回调的前提是消息真的发出去了。

**MQ 负责投递，outbox 负责不丢**，各管一段。代价是消费方必须幂等
（发奖侧靠 `t_prize_log` 的 `uk_external_biz` 兜底，与 `source_biz_id` 同值）。

---

## 8. 拆分那天要做的事（提前记下，验证今天的设计是否够）

| 步骤 | 改动量 |
|---|---|
| 域模块外面套 `@RestController implements XxxApi` 的薄壳 | 每个 api 一个类，方法体全是 `return delegate.xxx()` |
| app 侧把本地 bean 换成 `@ImportHttpServices` 生成的 HTTP 代理 | 配置改动，**调用方代码 0 行** |
| traceId / clientIp 从 MDC 改成 HTTP header 透传 | 一个 `RestClient` 拦截器 + 一个服务端 Filter |
| **数据库跟着拆** | ⚠️ 见下 |

### 8.1 真正的大头不是 RPC，是数据所有权

拆成多服务后，"两个进程共库共 Redis"必须变成每个服务管自己的库，否则拆出来的是**共享数据库的分布式单体** —— RPC 的复杂度全付了，故障隔离和独立伸缩一个没拿到。

`solvela-prize` 的归属**已定**：跟 activity 走（见 §1.0 的服务映射表）。parent pom 注释里那句「prize 横跨活动侧与资产侧」在服务边界上的解法是：**奖品配置**属于 app-activity，**发出去之后的资产**属于 app-member，分界线在派发那一刻。

---

## 9. app 不查数据库

### 9.1 边界：Redis 算 app 的，DB 不算

`RedisTokenStore` 存的是会话凭证，是网关自己的状态，不是域数据。**app 可以有自己的 Redis，不可以有 DB。** 拆服务那天，app 的 Redis 也是它自己的，不与 app-member 共享。

### 9.2 现在 app 里全部的库触点，正好都在登录链路上

| 触点 | 去向 |
|---|---|
| `MemberAuthDao`（两个查询） | → `solvela.member.auth` |
| `MemberLoginLogDao` | → 随登录日志进 member |
| `MemberOperationLimitService` | → 本来就在 member，app 不再直接调 |
| `PiiHasher` / `PasswordCipher` / `MemberPhoneUtil` | → 手机号摘要与验密在 member 算 |
| `SolvelaIpUtil.getRegion()` | → IP 归属地跟着登录日志走。⚠️ `AppApplication` 的 `Ip2RegionListener` **现在还不能删** —— member 的 bean 仍与网关同进程，xdb 得有人加载；到第 6 步 app 不再装配 member 时才删 |

下沉后 app 自己的代码里**不再有任何 DAO**，对外部包的引用只剩 `solvela.enums` 与 `solvela.member.auth`（域契约，第 3 步会换成 api 模块里的同名类型）。

⚠️ `@ComponentScan` 这时还不能收缩：member 的 bean 仍与网关同进程，`solvela.member` 和 `solvela.crypto`（`PiiHasher` 给会员域算手机号摘要用）都还得扫。收缩发生在第 6 步。

### 9.3 认证热路径：缓存结构不变，只换回源

`MemberPrincipalLoader.load()` 每个请求走一次。库摘掉之后：

```java
@Cacheable(value = CACHE, key = "#memberId")   // app_member_principal#30m，不变
public MemberPrincipal load(Long memberId) {
    return memberApi.getAuthIdentity(memberId)   // ← 唯一改动，原来是 memberAuthDao.selectForAuth
            ...
}
```

30 分钟 TTL 意味着每个活跃会员 30 分钟才回源一次，RPC 量可接受。

**这个改动安全的前提**：冻结即时生效已经不依赖缓存过期 —— 走的是 `revokeAll`，令牌没了压根到不了这里。`MemberPrincipalLoader` 类注释里那句「这一版还多了一层保险」正好把这条路铺好了。

⚠️ **不要为了省这次回源退回 JWT。** `RedisTokenStore` 注释里那段取舍（即时吊销 vs 无状态）在拆服务后**更成立**，别因为「少一次 RPC」重开这个决定。

### 9.4 两笔要还的债

**债一：缓存失效跨进程。** 改昵称/换头像发生在 member 侧，缓存在 app 的 Redis 里。现在共享 Redis，member 直接 `evict` 就行；拆开后需要事件广播。

现在就该做的：把「会员资料变更 → 失效身份缓存」收口成**一个出口**（member 侧发 `MemberProfileChangedEvent`，app 侧一个监听器调 `evict`）。今天是本地 `ApplicationEvent`，将来换 MQ 只改订阅端一处。散在各个改资料的地方直接调 `evict`，拆分那天就是满仓库找调用点。

**债二：`solvela-base` 把库和文件捆在一起。** app 依赖 base 就自动带上 `mybatis-plus-spring-boot4-starter` + `mysql-connector-j` + p6spy。不拆 base，「app 没有库」就只能靠 `exclude = DataSourceAutoConfiguration.class` 打补丁 —— 那正是 `AppApplication` 注释里批判的「扫全世界再减掉」。

### 9.5 base 已拆（2026-08-30）

```
solvela-base-core    util / json / validation / 常量 / trace / 配置装载   ← 谁都能依赖
solvela-base-redis   缓存 / RedisService / Redisson / 分布式锁 / 发号器
solvela-base-data    datasource / mybatis-plus / p6spy / Dao 基线 / 系统配置表
solvela-base-file    对象存储 / 文件模块 / SonicExcel / 邮件 / 模板
solvela-base         ← 只剩一个门面，依赖上面四个，本身没有代码
```

**包名一个字没改**（仍是 `solvela.base.*`），全仓 import 零改动 —— 与拆 `solvela-contract` 同一套做法。

**留门面模块**是为了不动那十几个 pom：老的 `solvela-base` 声明原样有效，需要收窄的模块自己改。
已收窄的两个：

| 模块 | 现在依赖 | 甩掉了什么 |
|---|---|---|
| `solvela-member` | core + redis + data | S3、Excel 引擎、tika、邮件、freemarker、jsoup |
| `solvela-app` | core + redis（+ 经 member 传递的 data） | 同上 |

🔴 新模块请直接依赖具体那一块，不要图省事写 `solvela-base` —— 写了就等于把 JDBC 驱动、
S3 SDK、Excel 引擎一并背上。

**拆的时候踩到的三件事**（都不报错、只在运行期炸）：

1. `mapper/support/FileMapper.xml` 一开始放进了 data，而它的 `FileVO` 在 file ——
   凡是有 data 没 file 的进程（正是网关），启动时 MyBatis 解析 mapper 直接失败。
   **mapper XML 必须和它引用的类在同一个模块**；
2. 三个 JSON 序列化器（`FileKeySerializer` 等）在 `solvela.base.json` 下却引用文件模块，
   跟着搬进了 file。包名同名跨两个模块是允许的，但要知道自己在做什么；
3. 移动文件后**必须 `mvn clean`**：反应堆里依赖解析到的是 `target/classes` 而不是 jar，
   老模块的残留 class 与 mapper 会和新模块的同时在 classpath 上，
   表现是 MyBatis 报「重复的 mapper 片段」——看起来完全像是配置写错了。

**app 摘掉 JDBC 仍未达成**：会员域有 MyBatis Dao，而它的 bean 目前与网关同进程。
等第 6 步删掉 `solvela-member` 依赖，才能给 `AppBoundaryTest` 加上那条
`Class.forName("com.mysql.cj.jdbc.Driver")` 断言。

拆完后 `AppBoundaryTest` 加第四条断言，与「sa-token 不在 classpath 上」同形：

```java
assertThrows(ClassNotFoundException.class, () -> Class.forName("com.mysql.cj.jdbc.Driver"));
```

从「约定 app 不查库」变成「app 连驱动都没有」—— 与 job 模块那次收敛是同一个形状：**从"靠配置约束"变成"物理上不具备"**。
