# C 端网关 API 契约方案（solvela-app → 域服务）

> 目标：`solvela-app` 作为**编排网关**，今天同 JVM 调用、将来把域拆成独立服务时**调用方代码一行不改**。
> 范围：activity / member / ledger(资产) 三个域 + 抽奖执行。
> 日期：2026-08-30 初版，2026-08-31 按进程拓扑变更修订。

---

## 修订说明（2026-08-31）

**本文的核心结论已经被兑现，而且正是它让这次进程收缩只花了一次改名的功夫** ——
`*-api` + `@HttpExchange`、本地实现与 HTTP 实现共用同一接口、调用方注入的类型不变。
撤掉一个进程时，`MemberAuthApi` / `MemberProposalApi` 从 HTTP 代理换回本地 bean，
业务代码一行没动。

**变的是服务划分**。初版按「app-activity / app-member 两个业务服务」设计，
2026-08-30 照此拆出了四个进程，2026-08-31 又把 member 收了回来 ——
原因不是设计错了，是**那一刀切在了不产生隔离的地方**（见 §7.4）。

现在是三个进程：

| 进程 | 端口 | 装什么 |
|---|---|---|
| `solvela-admin` | 1024 | 全域后台单体，独占 `@EnableScheduling` |
| `solvela-app` | 1025 | 网关。**classpath 上没有 mysql 驱动**，由 `AppBoundaryTest` 断言守着 |
| `solvela-app-biz` | 1026 | C 端唯一业务进程：会员 + 营销 + 资产 |

**将来真要拆，拆的是资产域（ledger），不是会员** —— 目标是资产独立成服务，
会员 + 资产用一个独立的后台控制台。所以本文凡是提到「app-activity / app-member」的划分，
都按这条重读；那条缝的守法见 §7.4 与 §8。

---

## 0. 一句话结论

**建 `*-api` 模块，接口用 Spring HTTP Interface（`@HttpExchange`）声明，今天由本地 `@Service` 实现、将来由 `@RestController` 实现 —— 两种形态共用同一个接口，`solvela-app` 注入的类型不变。**

不引入 Dubbo：Dubbo 3.3 的 starter 仍绑在 Boot 3.x 的自动装配机制上，Java 25 与 Boot 4.1 不在其测试矩阵内；且它捆绑的注册中心/配置中心是另一套运维，与「现在两个进程共库共 Redis」的现状不匹配。

---

## 1. 模块拓扑

### 1.0 api 模块的粒度 = 将来的服务粒度，不是今天的 maven 模块粒度

**这条原则不变，变的是「将来的服务」是哪几个。**

今天的进程与实现模块：

| 进程 | 装哪些实现模块 | 对应的 api 模块 |
|---|---|---|
| **solvela-app-biz** (1026) | `solvela-marketing`（含并入的 activity/consumer）、`solvela-prize`、`solvela-scriptengine`、`solvela-member`、`solvela-ledger`、`solvela-risk` | 两个都实现 |
| **solvela-app** (1025) | 网关，无域实现 | 依赖两个 api |
| **solvela-admin** (1024) | 后台，单体，装全部 | — |

将来拆出去的那一个：

| 将来的服务 | 装哪些 | 契约 |
|---|---|---|
| **资产服务** | `solvela-ledger`、`solvela-risk` 的 `proposal` + `engine` 半边、`solvela-prize` 的 `prizelog` 半边 | `solvela-member-api` 里的 `MemberProposalApi` |

**仍然只建两个 api 模块**，理由与初版一致：

- **一个 api 模块里按实现归属切多个接口**：`solvela-marketing-api` 里有 `ActivityApi`（活动域实现）
  和 `DrawApi`（抽奖实现）。不能合成一个大接口 —— 一个 Spring bean 没法只实现半个接口；
- **不建 `solvela-ledger-api`。** 资产的对外契约就是 `MemberProposalApi`，它已经在
  `solvela-member-api` 里，拆资产那天不用新建模块；
- **实现模块的域切分与 api 无关。** 2026-08-31 把 `solvela-activity` 与 `solvela-consumer`
  并进了 `solvela-marketing`（三者进程足迹完全相同），`solvela-marketing-api` 一行没改 ——
  这正说明 api 粒度对齐的是服务，不是 maven 模块。

```
solvela-contract         ← 纯枚举 + 错误码，零第三方运行时依赖
solvela-marketing-api    ← 活动 / 抽奖 / 任务 / 奖品 / 商城 的对外契约
solvela-member-api       ← 会员 / 资产 / 提案 的对外契约
   ↑ implements（可由多个实现模块分别实现）
solvela-marketing + solvela-prize + solvela-scriptengine
solvela-member + solvela-ledger + solvela-risk
   ↑ 只依赖这两个 api
solvela-app
```

### 1.0.1 曾经未定的一处：consumer 归哪个服务（已定）

初版写着「`solvela-consumer` 按数据所有权应归 app-member（写的是资产与账本），
但它读的是活动侧的配置，本轮不动它」。

**已定：跟营销走。** 判据不是「谁在链路终点」，是**账本归属 —— 谁的表谁写**：
consumer 写的是 `t_prize_log`，那是营销侧的账。资产入账由它经
`MemberProposalApi` 交给资产侧，自己一个字都不写别人的表。

2026-08-31 它整个并进了 `solvela-marketing`（进程足迹完全相同，合并不改变任何 classpath）。
代价是编译器不再拦着玩法侧直接调它的发奖处理器，改由 `PrizeDispatchBypassTest` 守。

### 1.1 三条硬约束

| # | 约束 | 为什么 |
|---|---|---|
| 1 | `*-api` **不得依赖 `solvela-model`** | model 里有 `PiiTypeHandler`、`PiiCipher`、MyBatis entity。api 依赖它 = 把持久层塞给每一个调用方，拆进程时还得跟着带 mybatis-plus |
| 2 | 先拆 `solvela-contract`（把 `solvela/enums` + `solvela/code` 从 model 挪出来）✅ 2026-08-30 完成 | 否则 api 的 DTO 想用 `MemberStatusEnum` 就会把整个 model 拉进来。**包名刻意不变**，全仓 import 零改动 |
| 3 | `solvela-app` 的 pom **只有 `*-api`**，没有任何域实现模块 ✅ 2026-08-31 达成 | Maven 成为硬边界。`dependency:tree` 复核：网关 114 个依赖里 0 个 mysql、0 个 `base-data`、0 个 `base-file`、0 个域实现模块。`AppBoundaryTest` 的四条断言全绿，其中「mysql 驱动必须 `ClassNotFound`」是这条约束的物理证明 |

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

拆微服务时，脚本引擎**跟着用它的域走**：任务与抽奖都在 `solvela-app-biz`，它就打进那个服务，不独立成服务 —— 它是个库，不是个服务。
（2026-08-31 复核：`solvela-scriptengine` 的依赖方只有 marketing 与 admin，资产侧一处都不用它。所以拆资产域时它不跟着走。）

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
| `draw(cmd)` | 抽奖（走脚本编排） | ✅ 已实现，**网关的 C 端接口已接通**（2026-08-30） |
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
app: MemberApi.deductScore(memberId, 100)   // 扣积分（将来的资产服务）
app: ActivityApi.draw(...)                  // 抽奖（业务服务）
```

拆成微服务后，这两次网络调用中间断电 = 用户扣了分没抽奖，没有任何补偿路径。

`DrawExecuteService` 明说自己**不做资产扣减**，「由上游业务算完再调进来」。
那个「上游业务」是**业务服务内部**的编排（`ActivityDrawFacade` / `ACTIVITY_PLAY` 脚本），
在一个本地事务里完成。

🔴 **这条从今天就要守，而且今天更容易破** —— 资产域现在与营销域同进程，
网关连着两次写调用是「能跑通」的。守法见 §8.2 的 `LedgerBoundaryTest`。

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

## 7. 落地顺序（本轮活动链路，七步已全部完成）

| # | 步骤 | 完成判据 |
|---|---|---|
| **1** ✅ | 登录逻辑下沉到 `solvela.member.auth`（2026-08-30） | app 里**一个 DAO 都没有**（见 §9） |
| **2** ✅ | 拆 `solvela-contract`：`solvela/enums` + `solvela/code` 从 model 挪过去（2026-08-30） | 包名不动，全仓 import 零改动；contract 只依赖两个纯注解包（见 §1.1.1） |
| **3** ✅ | 建 `solvela-marketing-api` / `solvela-member-api`（2026-08-30） | api 的 pom 里只有 contract + spring-web；没有 model、没有 mybatis 运行时 |
| **4** 部分 | 只放本轮链路要用的方法 | 已放：认证 3 个、活动详情/规则 2 个、抽奖 1 个、建提案 1 个。剩余次数与钱包余额**没放** —— 域里今天还没有对应的读方法，等真要用时连实现一起加 |
| **5** ✅ | 域模块 implements；`MemberPrincipalLoader` 回源改调 api（2026-08-30） | 认证热路径仍是「Redis 命中 → 直接返回」，miss 才回源 |
| **6** ✅ | app 换依赖：删掉 `solvela-member`，`@ComponentScan` 收缩（2026-08-30） | app 的 pom 里只有两个 `*-api` + 三块 base + session |
| **7** ✅ | 拆 `solvela-base`，app 摘掉 JDBC（2026-08-30 拆，2026-08-31 删门面） | `AppBoundaryTest.没有数据库驱动()` 断言 `Class.forName("com.mysql.cj.jdbc.Driver")` 抛 `ClassNotFoundException`，已通过 |

🔴 **不要往 app 里加任何 DAO。** 现在它连驱动都没有，这是「物理上不具备」而不是「约定不要那么做」——
前者坏掉时构建就红了，后者要等到 code review 有人注意到。

### 7.0 「app 暂不接活动实现」这个决定，以及它是怎么被兑现的

2026-08-30 曾决定网关先不接 `ActivityApi.draw`：接上它当时需要 app 依赖
`solvela-activity` + `solvela-marketing` 的**实现**，把一堆包加进 `@ComponentScan` ——
那两个模块里混着后台写路径（`wizardCreate`、`DrawWorkbench*`），
进了容器就离公网 controller 只差一个 `@Autowired`。

**决定是「等 HTTP 客户端形态到位后再接」，而不是「凑合先接上」。** 结果见 §7.7：
接通的时候走的是 `DownstreamClientConfig` 生成的 HTTP 代理，网关的 classpath 上
一个域实现模块都没有。**如果当时凑合接了，第 6、7 步就得反过来做一遍。**

> 这是本方案里最值钱的一次「先不做」。

### 7.1 已知的债，按该还的顺序

| # | 债 | 状态 |
|---|---|---|
| 1 | `DrawExecuteService` 用 `BusinessException` 表达预期内结果 | ✅ 2026-08-30 已还，见 §7.2 |
| 2 | 剩余次数 / 钱包余额还没有 C 端读方法 | ⏳ 活动详情页要显示它们时再做 |
| 3 | 冻结不生效（安全） | ✅ 2026-08-30 已还，见 §7.3。剩下的「改昵称后最多 30 分钟看到旧资料」**目前够不着** —— 全仓没有任何修改会员昵称/头像的写路径，没有东西会变旧 |
| 4 | `solvela-base` 把库和文件捆在一起，app 摘不掉 JDBC | ✅ 2026-08-31 全部还清：base 拆四块（2026-08-30）、空门面删除、网关 classpath 上没有驱动、断言已加 |
| 5 | `ACTIVITY_PLAY` 链路没有自动化验证 | ✅ 2026-08-30 已还：`ActivityPlayAcceptanceTest`（admin，10 条）+ `ScriptEngineTest` 副作用约束 4 条 |
| 6 | 跨服务链路 id 断掉（新服务没有 TraceFilter） | ✅ 已还：`TraceFilter` 在 `solvela-base-web`，三个进程都依赖它并扫 `solvela.base` |
| 7 | **发奖投递没有不丢保证** | ❌ **未还，且比初版记的更严重** —— 见 §7.4「outbox 的真实状态」 |
| 8 | `ProposalCmdMapper` 是过渡形态 | ⏳ 它的前提（consumer 搬到营销服务）2026-08-31 已成立，见 §7.5 |

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

## 7.4 四进程拆分与回收实录（2026-08-30 拆，2026-08-31 收）

拆了一次又收回来一半，两次都留了记录 —— **收回来不是推翻设计，是发现那一刀切错了地方**。

### 拆的时候做出来的东西（大部分留下了）

| | 产物 | 现在 |
|---|---|---|
| 端模块形态 | 启动类 + 四套 yaml + 错误出口 + HTTP 薄壳，两个服务同构 | ✅ 留下，`solvela-app-biz` 用的就是这套 |
| 真起进程的验证 | `InternalEndpointMappingTest` 起真端口发真 HTTP | ✅ 留下，拆成两个（活动/抽奖、会员认证） |
| 契约与两侧实现 | `MemberProposalApi` / `MemberAuthApi` + 本地实现 + HTTP 薄壳 | ✅ 留下，这是最值钱的部分 |
| 消息底座 | `solvela-base-mq` | ⚠️ 留下，但只剩编解码 —— 发奖专用拓扑已删 |
| outbox | `t_prize_dispatch_outbox` + 实体 + dao | ❌ **从来没接线**，2026-08-31 连表带类一并删除 |
| 发布方抽象 | `PrizeEventPublisher` + `LocalPrizeEventPublisher` | ✅ 留下 |

### 装配过程逼出来的四件事（都不报错、只在运行期炸）

1. **`@MapperScan` 漏了 `solvela.stat`** —— 手写清单的必然产物；
2. **两个契约撞同一个 URL**：`ActivityApi` 的 `/internal/activity` + `/draw` 与 `DrawApi` 的
   `/internal/activity/draw`。启动期 Ambiguous mapping —— 这个报错顺带**证明了
   Spring MVC 确实认接口上的 `@HttpExchange`**，整套「契约只定义一次」的做法才站得住；
3. **派发链路反向依赖活动域**：`ProposalSourceResolver` 拿 activityCode 回头查活动表。
   改成**消息自带上下文**：`t_prize_log.activity_type` + `UserPrizeEvent.activityType`，
   解析器变成纯函数。**这个改动即使收回进程也是对的**，留着；
4. **`solvela.dispatch` 忘了进扫描清单** —— 同 1。

### 🔴 为什么把 member 收了回来（2026-08-31）

摊开算了一次账，那一刀**买到的是零，付出的是三样**：

| 付出 | 事实 |
|---|---|
| 热路径上一次同步 HTTP | 抽奖 → 建提案跨进程，读超时 3s；超时会让发奖流水停在「待提交」等重投 |
| 一条只为回写存在的 MQ 链路 | RabbitMQ 全仓**只用在这一件事上**：一个 publisher、一个 listener |
| 两处不得不开的扫描妥协 | 整个 `solvela.member` 和半个 `solvela.risk` 本来就已经在营销进程里了 |
| **买到的隔离** | **零** —— 四个进程连的是同一个库，yaml 里自己写着「第一步只拆进程，不拆数据」 |

而 `solvela-app-member` 本身只有 6 个 java 文件，不装任何独有的域。

**收回来不用扔掉任何契约**：`MemberProposalApi` 在合并后的进程里解析成 `ProposalApiService`
（本地 bean）而不是 HTTP 代理，`MemberAuthApi` 同理，调用方代码一行没动。
**这正是 §0 那个结论的反向验证** —— 它不只保证「拆的时候不用改」，也保证「合的时候不用改」。

### 🔴 两个模块横跨两个域（拆资产域前必须先拆它们）

| 模块 | 归营销的一半 | 归资产的一半 |
|---|---|---|
| `solvela-risk` | `promotionconfig`(8) / `promotiongroup`(7)，奖品关联它 | `proposal`(9) + `engine`(8)，跟资产走 |
| `solvela-prize` | `prizeconfig`，配活动要读 | `prizelog`，发奖流水 |

`solvela-risk` 已查实**这一刀切得很干净**：`engine` 只被 `proposal` 引用，
`promotionconfig` / `promotiongroup` 一处都不引用 `proposal`，两侧零交叉。

四进程时代靠「精确到子包扫描」绕过去；现在同进程，那个止血撤掉了，
**改由 `LedgerBoundaryTest` 守**（见 §8）。

⚠️ `solvela-prize` 的分界在**派发那一刻**：奖品配置属于营销，发出去之后的资产属于资产侧。
共库阶段唯一的硬规则：**写路径只能有一个服务拥有**。

### outbox 的真实状态：**没有，而且比初版记的更严重**

初版这里论证了「MQ 之外还要 outbox」，论证本身是对的：

> MQ 覆盖不了这个窗口：**事务提交成功 → 进程在发消息之前挂了**。
> 奖已判定、流水已落库，消息却没发出去，而且没有任何地方记得这件事。
> publisher-confirm 也救不了 —— 确认回调的前提是消息真的发出去了。

**但它从来没有落地。** 2026-08-31 核查发现 `PrizeDispatchOutbox` 实体与
`PrizeDispatchOutboxDao` 除互相引用外**零使用**，`t_prize_dispatch_outbox` 是一张
没人写也没人读的表；而 `PrizeEventPublisher` 的注释却把它描述成已有能力，
`LocalPrizeEventPublisher` 的注释甚至在教人给营销服务配 `dispatch.mode=mq`
（真配了会因为没有第二个实现而启动失败）。三者已一并清理。

> 教训：**写下了一个没有测试盯着的可靠性承诺**，与 §7.3 的「冻结不生效」同一个形状。
> 差别是那次有类注释可以打脸，这次连打脸的地方都没有 —— 因为死代码看起来就像已经做完了。

**那个窗口今天是敞着的**（进程内 `AFTER_COMMIT` 同样覆盖不了「提交后、派发前进程挂掉」）。
风险比跨进程时小（少一个网络跃点），但没有消失。真要覆盖就补一个 `PrizeEventPublisher`
的 outbox 实现 —— 业务代码一行不用改，这正是留着那个接口的意义。

### 7.5 发奖：同步提案 + 结果回写（2026-08-30 跨服务设计，2026-08-31 收进同一进程）

```
marketing ──同步 HTTP──▶ member    受理 / 拒绝 + 原因（当场返回）
     ↓ 落 t_prize_log.proposal_status + proposal_id + fail_reason
member ────异步消息────▶ marketing  资产真正入账完成 / 失败
     ↓ 落 t_prize_log.status（终态）
```

**为什么不是全异步**：「被风控拒了」这个当场就知道的结论，全异步要等一个来回才回来，
而这期间发奖流水停在「待提交」——分不清是没提交过去还是被拒了。
失败原因要能直接展示给 C 端用户、要让开发不必翻两个服务的日志对时间戳。

**为什么不是全同步**：审批与入账是慢的（可能人工审批几小时），同步等不了。

**状态拆两列**（`proposal_status` / `status`）的理由见 `PrizeProposalStatusEnum` 类注释：
压在一个字段上时，「已受理但还在审批」与「已入账」长得一模一样。

#### 已完成，并且合并进程后原样有效

- 契约 `MemberProposalApi` + `CreateProposalCmd` + `ProposalResult`；
- 资产侧 `ProposalApiService`：把 `BusinessException` 翻成 `failReason`。
  **翻译放在这一层而不是改 `addProposal` 本身** —— 后台审批、人工补发也在调它，
  那些路径上抛异常是对的（调用方是人）。同一段逻辑，对内抛异常、对外给返回值；
- 四个发奖 handler 改调 api；`DispatchOutcome` 带上 `proposalId`；
- `PrizeDispatchHandler` 落 `proposal_status`：被拒 → REJECTED + 原因；受理 → ACCEPTED + 提案 id。
  **受理时刻意不写 `status=1`** —— 那会造成「记录显示成功、用户其实没收到」。

#### 2026-08-31 的变化：异步回写那一段没有了

两侧进了同一个进程，所以：

| 初版的未完成项 | 现在 |
|---|---|
| consumer 从 member 挪到 marketing | ✅ 已挪，并进一步并入 `solvela-marketing` |
| ledger 停写 `t_prize_log`，改成发消息 | ✅ 不写别人的表这条守住了 —— 它调 `PrizeDispatchResultPublisher`，今天的实现是进程内更新 |
| marketing 订阅回写消息落终态 | ❎ **不需要了**，同进程直接落 |
| `t_mq_message_log` + 消费幂等 + 7 天清理 | ⚠️ 代码与表都在，但**目前没有任何使用者** —— 唯一的消费者随发奖 listener 一起删了。留着是给下一摊（活动事件订阅）用的 |
| 重投任务：扫 `proposal_status = PENDING` | ⏳ 仍然要做。`PrizeDispatchReconcileJob` 已在，覆盖面待核 |

**上表那三个 ❎/✅ 在拆资产域那天会重新变成待办** —— 所以 `PrizeDispatchResultPublisher`
这个接口刻意留着（今天只有一个实现），到时候加一个 MQ 实现、在端模块换装配即可。

#### 一个可以收尾的过渡形态

`ProposalCmdMapper` 的类注释写着：

> ⚠️ 这是过渡形态。`ProposalRecordAddCommand` 属于会员侧的风控域，发奖侧本不该认识它。
> **等 consumer 整体搬到营销服务之后**，四个 handler 应当直接拼 `CreateProposalCmd`，本类随之删除。

**那个前提 2026-08-31 已经成立。** 改完之后可以把 `LedgerBoundaryTest` 里
`ALLOWED_PROPOSAL_SUBPACKAGE = "domain"` 那条放行去掉，禁掉整个 `risk.proposal` ——
缝守得更严。⚠️ 这是改四个 handler 拼装奖品命令的业务代码，字段映射错了就是发错奖，
该单独一次改动、单独过一遍。

#### 已定但未实施的下一摊（活动事件订阅）

**MQ 底座就是为这个留着的** —— 发奖那条链路没了，但 `solvela-base-mq` 的 JSON 编解码
（⚠️ 必须是 Jackson 3 的 `JacksonJsonMessageConverter`，名字带 2 的那个绑的是 Jackson 2）
与 `MqMessageLog` 的消费幂等表都在，补拓扑与发布点即可。

- `t_mq_message_log` 的隔离列叫 **`consumer_key`** 而不是 `activity_code`：
  这张表要装多种消息，唯一键 `(message_id, consumer_key)`，后台重试按 `consumer_key` 过滤 ——
  重跑 A 活动不会碰到 B。**这一列本来就是为多消费者设计的**，正好接住登录事件这类 fan-out；
- **每种事件一个队列 + 订阅关系在库里**，不是每活动一个队列：活动是运营随时建的，
  队列与绑定会爆炸且下线后残留。订阅关系复用现成的 `t_script_ref` + `ScriptRefPoint`；
- 取订阅者时必须 **join 活动表过滤**（`t_script_ref` 不知道活动上没上线、数据结没结束）。
  **活动没启用或数据已结束的，消息不记录**；
- 🔴 **每个队列都必须绑死信交换机**。没有死信配置时 RabbitMQ 对被拒消息的默认行为是
  **直接丢弃**；消费端同时要配 `default-requeue-rejected: false`，
  否则一条必然失败的消息会被反复重入队把队列打爆。写法见 `MqConfig` 的类注释；
- ⚠️ 这会让时间窗判据出现第三处（展示、准入、事件过滤）。**到第三处必须收口**成一个
  `joinable(activity, now)`，SQL 只做粗筛。缓存只能存活动对象，不能存布尔值 ——
  数据截止是时间到了自然失效，存布尔值会让活动结束后仍触发到 TTL 过期。

⚠️ `docker-compose.yml` 里**没有 RabbitMQ 服务**，yaml 指向 127.0.0.1:5672 的外部实例。
这一摊开工前要先补上。

---

### 7.6 端到端联调（2026-08-30，两条缝已验证）

真起两个进程、发真 HTTP、发真消息，验的是<b>拆分新增的两条缝</b>
（抽奖引擎本身由营销服务自己的测试覆盖，不重复验）。

| 缝 | 验证方式 | 结果 |
|---|---|---|
| marketing →(同步 HTTP)→ member | POST `/internal/member/proposal`，用不存在的优惠配置 | `200` + `{"accepted":false,"failReason":"资产配置异常"}` ✅ |
| member →(异步消息)→ marketing | 向 `solvela.prize` 发 `prize.dispatch.result` | `routed:true`，监听器消费并落 `t_mq_message_log`（status=成功）✅ |
| 重复投递 | 同一条消息再发一次 | 行数仍为 1、`retry_count=0`、日志「已成功处理过」✅ |

#### 🔴 抓到一个真缺陷：`MemberProposalApi` 的 HTTP 薄壳漏了

`ProposalApiService` 实现了接口，但它是 `@Service` ——
**Spring MVC 只给 `@Controller`/`@RestController` 建映射**。没有薄壳时端点根本不存在。

而这件事**所有进程内测试都发现不了**：会员服务的上下文照常启动，
营销服务的上下文也照常启动（它那边只是个 HTTP 代理，不校验对端存在），
一直要到第一次真实发奖才炸。

> 教训：**每加一个 api 接口，就要问一句「服务端的壳建了没有」**。
> 契约实现类和 HTTP 薄壳是两件事，而它们分别在两个模块里。

#### 顺带发现：新服务没有 TraceFilter（已还）

当时会员服务的错误响应里 `traceId` 是 `null`：网关侧的客户端拦截器**已经在发**
`traceId` 请求头（见 `DownstreamClientConfig`），但两个新服务**没有 Filter 把它读进 MDC**，
跨服务的链路 id 是断的。

**已还**：`TraceFilter` 收进 `solvela-base-web`，三个进程都依赖它并扫 `solvela.base`。
它与 `DownstreamClientConfig` 的请求头拦截器是**一件事的两半**，少任何一半链路都断 ——
抄三份的话三处的 sanitize 规则迟早不一致，断得还更隐蔽（两边都有 id，只是不一样）。

---

### 7.7 C 端活动接口接通（2026-08-30）

```
GET  /activity/{code}        匿名可访问 —— 活动页是分享入口，要求先登录等于把分享链路掐断
POST /activity/{code}/draw   需登录；会员号取自登录态，客户端传的一律不认
```

真起网关 + 营销两个进程验过：

| 用例 | 结果 |
|---|---|
| 匿名看不存在的活动 | `404` `{"code":"NOT_FOUND","message":"活动不存在或已结束"}` |
| 未登录抽奖 | `401` `LOGIN_REQUIRED`（鉴权在任何下游调用之前） |
| 伪造令牌抽奖 | `401`，**没有产生下游调用** —— 令牌在网关的 Redis 里就解析失败了 |

#### 网关在这条链路上只做三件事

1. **身份**：`CurrentMember.require()` 取会员号，客户端传的不认；
2. **翻译**：`DrawRejectReason` → 状态码 + <b>给用户看的话</b>。用 switch 表达式，
   营销侧新增一个 reason 时网关<b>编译不过</b> —— 两边分开发版，编译期能拦的不该留到运行期；
3. **组装**：`prizeItemId`（奖池内部主键）与 `source`（概率/白名单/兜底）<b>不下发</b>。
   后者尤其：让用户知道自己是「白名单命中」的，等于告诉他这个活动内定了。

#### 奖池那四种拒绝对用户说同一句话

`POOL_NOT_FOUND` / `POOL_CLOSED` / `POOL_NO_PRIZE` / `POOL_BROKEN` 对用户是同一件事：
**现在抽不了**。把区别告诉他既没用，又暴露了配置结构 —— 真正的原因在营销服务的日志里。

而 `NO_PLAY_SCRIPT`（活动没挂编排脚本）在网关<b>打 error 日志</b>：
对用户含糊其辞是对的，但没有这行日志，这个活动会安静地一个奖都发不出去。

---

## 8. 拆资产域那天要做的事（以及今天怎么把那条缝守住）

目标形态：**资产独立成服务，会员 + 资产用一个独立的后台控制台**。
所以 `marketing ↔ ledger` 就是那条缝。

### 8.1 那天的改动量

| 步骤 | 改动量 |
|---|---|
| 拆 `solvela-risk`：`proposal` + `engine` 归资产，`promotionconfig` + `promotiongroup` 留营销 | 两侧零交叉，已查实 |
| 拆 `solvela-prize`：`prizelog` 归资产，`prizeconfig` 留营销 | 分界在派发那一刻 |
| 资产侧套 `@RestController implements MemberProposalApi` 的薄壳 | `MemberProposalInternalController` 就是它，删掉的那个照抄回来即可 |
| 营销侧把本地 bean 换成 HTTP 代理 | `MemberServiceClientConfig` 同上。**调用方代码 0 行** |
| 入账结果回写改回发消息 | 加一个 `PrizeDispatchResultPublisher` 的 MQ 实现，端模块换装配 |
| **数据库跟着拆** | ⚠️ 见 §8.3 |

前两行是真活，后三行是把 2026-08-31 删掉的东西照抄回来 —— **git 里都有**。

### 8.2 🔴 今天靠什么守住这条缝

四进程时代靠 `@ComponentScan` 精确到子包；合并之后那个止血撤掉了。现在靠两条断言，
都在 `solvela-marketing` 里，都用注入探针验证过**确实会失败**：

| 断言 | 守什么 |
|---|---|
| `LedgerBoundaryTest` | pom 里不许有 `solvela-ledger`；class 不许引用 `solvela/ledger/` 与 `solvela/risk/proposal/` 的非 `domain` 子包。**要动资产只能经 `MemberProposalApi`** |
| `PrizeDispatchBypassTest` | 玩法侧不许直接引用 `solvela.consumer.handler`。绕过 `PrizeEventPublisher` 会让派发跑进事务里，业务回滚撤不掉已发的奖 |

两条都**扫 class 文件常量池，不是扫 import** —— 全限定名直接写在代码里是没有 import 行的。

> 这两条断言就是「将来还能不能便宜地拆出去」的全部保障。它们红了别去改它们，去改代码。

### 8.3 真正的大头不是 RPC，是数据所有权

拆成多服务后，「三个进程共库共 Redis」必须变成每个服务管自己的库，
否则拆出来的是**共享数据库的分布式单体** —— RPC 的复杂度全付了，
故障隔离和独立伸缩一个没拿到。

**2026-08-31 那次把 member 收回来，根因就在这里**：进程拆了、库没拆，
所以那一刀买到的隔离是零。**下次动手前先问：库拆不拆？** 不拆就不值得拆进程。

⚠️ 后台控制台的成本容易被低估：`solvela-admin` 里 `module/ledger`(15) + `module/member`(13)
只有 28 个文件，而 `module/system`(280 —— 菜单/角色/员工/字典/权限/代码生成器)
是两个控制台都要的。**那 280 个怎么办**（复制 / 抽成模块 / 共用一套账号走 SSO）
才是那一刀的实际工作量，别按 28 个文件的量级排期。

---

## 9. app 不查数据库

### 9.1 边界：Redis 算 app 的，DB 不算

`RedisTokenStore` 存的是会话凭证，是网关自己的状态，不是域数据。**app 可以有自己的 Redis，不可以有 DB。** 拆服务那天，app 的 Redis 也是它自己的，不与业务服务共享。

### 9.2 当初 app 里全部的库触点，正好都在登录链路上（已全部下沉）

| 触点 | 去向 |
|---|---|
| `MemberAuthDao`（两个查询） | → `solvela.member.auth` |
| `MemberLoginLogDao` | → 随登录日志进 member |
| `MemberOperationLimitService` | → 本来就在 member，app 不再直接调 |
| `PiiHasher` / `PasswordCipher` / `MemberPhoneUtil` | → 手机号摘要与验密在 member 算 |
| `SolvelaIpUtil.getRegion()` | → IP 归属地跟着登录日志走。`AppApplication` 的 `Ip2RegionListener` ✅ 已删（`AppApplication:71` 留了一行注释说明为什么不再注册），`Ip2RegionListener` 现在挂在 `BizApplication` 上 |

**已全部完成**：app 自己的代码里没有任何 DAO，`@ComponentScan` 已收缩，
pom 里只有两个 `*-api` + `base-core`/`base-redis`/`base-web` + `member-session`。
`solvela.member.auth` 那条引用换成了 `solvela-member-api` 里的同名契约类型。

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

### 9.4 两笔要还的债（债二已还）

**债一：缓存失效跨进程。** 改昵称/换头像发生在 member 侧，缓存在 app 的 Redis 里。现在共享 Redis，member 直接 `evict` 就行；拆开后需要事件广播。

现在就该做的：把「会员资料变更 → 失效身份缓存」收口成**一个出口**（member 侧发 `MemberProfileChangedEvent`，app 侧一个监听器调 `evict`）。今天是本地 `ApplicationEvent`，将来换 MQ 只改订阅端一处。散在各个改资料的地方直接调 `evict`，拆分那天就是满仓库找调用点。

**债二：`solvela-base` 把库和文件捆在一起。** ✅ **已还**，见 §9.5。

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

### 9.6 门面已删，app 摘掉 JDBC 已达成（2026-08-31）

`solvela-base` 那个 0 行代码的门面**已经删除**。留它是为了不动十几个 pom，
但它同时是「一行 pom 就把 JDBC 驱动 + S3 SDK + Excel 引擎一起拖进来」的最短路径 ——
而那正是本节两条边界最容易被破的方式。

现在 9 个模块各自列出真用到的 base 子模块（按 148 个 base 类逐个归属统计）：

| 模块 | 依赖 | 甩掉了 |
|---|---|---|
| `solvela-scriptengine` | core + data | file、redis |
| `solvela-prize` | core + data | file、redis |
| `solvela-risk` | core + redis + data | file |
| `solvela-marketing` / `solvela-ledger` | core + data + file | redis |
| `solvela-admin` | 四块都真用到 | — |

拆门面顺带暴露了一条白嫖：`solvela-scriptengine` 的 `EngineExecutionMonitorAspect` 用
`@Aspect/@Around`，而 aspectj 是经 `solvela-base → base-redis` 白嫖来的
（base-redis 需要它是为了 `RedisLockAspect`，跟脚本引擎没有半点关系）。
**这正是门面在掩盖的那类东西**，现在显式声明了。

🔴 **别再造一个「什么都有」的聚合模块。** 判据：一个 Maven 模块只有在
**能把某个 jar 挡在某个 classpath 之外**时才值这个价；挡不住任何东西的，
用包 + 一条会失败的断言表达更便宜。全仓 20 个模块里，base-core/redis/data/file/mq/web、
contract、两个 `*-api`、member-session 都在挡东西，一个都不能合。

**`AppBoundaryTest` 的四条断言（全部通过）**：

```java
// 1
assertThrows(ClassNotFoundException.class, () -> Class.forName("com.mysql.cj.jdbc.Driver"));
// 2
assertThrows(ClassNotFoundException.class, () -> Class.forName("cn.dev33.satoken.stp.StpUtil"));
// 3  本进程只有一个 solvela 自己的 @RestControllerAdvice
// 4  共享模块的 controller 一个都没被装配
```

`dependency:tree` 复核：网关 114 个依赖里 0 个 mysql、0 个 `base-data`、0 个 `base-file`、
0 个域实现模块。

从「约定 app 不查库」变成「app 连驱动都没有」—— 与 job 模块那次收敛是同一个形状：
**从「靠配置约束」变成「物理上不具备」**。
