# 业务脚本目录

**这里是脚本内容的唯一权威。**`t_script` 表只是它的只读镜像 —— 应用启动时由
`ScriptFileLoader` 把这里的文件同步进去，改数据库不生效、下次启动就被覆盖。

改脚本 = 改文件 + code review + 发版。后台的脚本页只能「查看 + 在线试跑」，不能编辑。

## 为什么是文件不是数据库

|                  | 文件（当前方案）                     | 数据库可编辑               |
|------------------|--------------------------------------|----------------------------|
| 语法错误何时暴露 | **应用启动就失败**，坏脚本进不了生产 | 运营点保存那一刻，或者更晚 |
| 能不能 review    | 能，走 PR                            | 不能                       |
| 能不能回滚       | `git revert`                         | 得自己做版本表             |
| 改了谁知道       | git blame                            | 猜                         |
| 运营能自助改吗   | **不能**，要发版                     | 能                         |

最后一行是代价，其余四行是收益。这个取舍已经确认，不要在没有新信息的情况下推翻。

## 目录规则

```
scripts/{域}/{脚本名}.ql
```

- **文件夹必须等于场景所属域的 namespace**（`ScriptScene.getDomain().getNamespace()`）。 把 `TASK_RULE` 的脚本放进 `draw/`
  会在启动时直接报错 —— 这条规则的存在就是为了让目录树不撒谎。
- **`script_code` 由路径推导**：`scripts/task/streak_sign_7d.ql` → `task/streak_sign_7d`。 不要在文件头里再写一遍
  code，能推导出来的东西不留给人写。
- 文件名用小写下划线，别用中文。

## 文件头格式

每个脚本必须以一段块注释开头，三个标签缺一不可：

```javascript
/**
 * @name  连续签到满 7 天
 * @scene TASK_RULE
 * @desc  连续签到进度达到 7 天即判定达标。
 *        可以写多行，缩进对齐即可。
 */
return currentMetric >= 7;
```

| 标签     | 必填 | 说明                                                                           |
|----------|------|--------------------------------------------------------------------------------|
| `@name`  | 是   | 中文名，后台列表显示用                                                         |
| `@scene` | 是   | 场景枚举名，见 `ScriptScene`。**它决定了这个脚本能拿到哪些变量、必须返回什么** |
| `@desc`  | 是   | 用途说明，支持多行                                                             |

域不用写 —— 由 `@scene` 推导（`ScriptScene.getDomain()`）。

## 能用哪些变量和函数

- **变量**：由 `@scene` 决定。查 `ScriptScene` 枚举，或调 `GET /script/engine/scene/view`。 用了场景没声明的变量，取到的是
  `null`，不会报错 —— 这是 QL 的语义，注意。
- **函数**：所有 `@ScriptFunction` 暴露的方法，一律带域前缀（`tool_` / `member_` / `mall_` …）。 查 `GET /script/engine/view`
  或后台的「脚本方法文档」页。

## 🔴 写脚本时最容易踩的两个坑

**1. 分支没覆盖全 = 静默返回 null**

QLExpress 里最后一个表达式的值就是返回值，所以「漏写 return」通常没事。真正危险的是这个：

```javascript
if (memberId < 0) {
    return true;
}
// memberId >= 0 时走到这里，整段脚本没有返回值 → null
```

场景契约会在执行后拦下它并报错。但你自己写的时候就该避免 —— **每条分支都要有返回值**。

**2. 多行表达式的操作符必须留在上一行末尾**

```javascript
return baseScore +          // ✅ 加号在上一行末尾
       bonusScore;

return baseScore            // ❌ 会被当成两条语句
       + bonusScore;
```

## 业务域函数

除了 `tool_` / `cache_`，各业务域也会把自己的能力挂上来。**查询类函数一律返回 map**（原因见下节：
隔离策略下脚本读不出普通对象的字段，而且不报错，只是拿到 null）。

| 函数 | 说明 |
|---|---|
| `member_info()` | 当前会员资料 map：`memberId/memberName/nickname/gender/status/registerSource/registerTime/registerDays/birthday/birthdayToday/inviteId/invited/level`。**一次执行只查一次库**，写几遍都行 |
| `member_registerDays()` / `member_isNewMember(7)` | 注册天数 / N 天内注册。阈值由脚本给——几天算新人是**活动**的判据 |
| `member_level()` / `member_levelAtLeast(3)` | 会员等级（0 起）/ 是否达到某一档。门槛由脚本给，同上——几级算高等级是**活动**的判据 |
| `prize_countWon([活动编码])` / `prize_hasWon('PRIZE_CODE')` | 中奖次数 / 有没有中过某个奖。活动编码可选，不传就是全部活动累计 |
| `prize_listRecent(10, [活动编码])` | 最近的中奖明细，每条一个 map，上限 50 条 |
| `draw_countDrawn()` / `draw_executeDrawByScript(池)` / `draw_executeMultiDrawByScript(池, 次数)` | 抽奖。后两个**有副作用** |
| `lottery_countMine(玩法, 期号)` / `lottery_issue(玩法, 期号)` | 彩票领号。后者**有副作用**；🔴 单人限购只能靠脚本自己用 `countMine` 判，发号引擎不管限购 |

**会员号不是参数**：所有这些函数的会员号都从内部通道取，脚本看不见也改不掉。没有
`member_infoOf(memberId)` 这种重载是刻意的——那等于让脚本查任意人的资料、拿别人的记录放宽自己的限制。

**有副作用的函数一次执行只准调一次**（发奖、领号合计），且应当是脚本的最后一步。

**等级专享**就是靠 `member_levelAtLeast` 表达的——这是会员等级的第一版权益之一：

```
// 白金及以上走专属奖池，其余走普通池
if (member_levelAtLeast(3)) {
    return draw_executeDrawByScript('POOL_PLATINUM');
}
return draw_executeDrawByScript('POOL_NORMAL');
```

⚠️ 等级是**配置**（运营随时加一档），所以脚本里写的是数字而不是「白金」两个字。
改档位名不影响脚本，**加一档要回头看看这些数字**——`levelAtLeast(3)` 的含义会跟着变。

## 时间 / 字符串 / JSON

脚本里 `s.trim()`、`s.length()` 这类 Java 方法调用**全都不通**（原因见下一节），所以这些事都走 `tool_` 函数。

**时间**——参数既可以是 `'2026-09-06 09:15:00'` 这样的串，也可以是场景变量里的时间对象（如 `eventTime`），两种都认；认不出来会直接报错，不会静默当成「现在」。

| 函数 | 说明 |
|---|---|
| `tool_now()` / `tool_today()` / `tool_timestamp()` | 当前时间串 / 当前日期串 / Unix 秒 |
| `tool_dateOf([时间])` | 日期部分 `yyyy-MM-dd`。**不传参数就是今天**，常用来拼当天的缓存键 |
| `tool_hourOfDay([时间])` / `tool_dayOfWeek([时间])` / `tool_isWeekend([时间])` | 小时 0~23 / 星期 1~7（1=周一）/ 是否周末 |
| `tool_nowBetween('09:00','12:00')` | 当前时刻在不在这个时段。**支持跨零点**：`('22:00','02:00')` 是晚 10 点到次日 2 点 |
| `tool_daysBetween(a,b)` / `tool_secondsBetween(a,b)` / `tool_daysSince(t)` | 差值。`tool_daysSince(注册时间) <= 7` 就是新人 |
| `tool_isBefore(a,b)` / `tool_isAfter(a,b)` | 先后比较 |
| `tool_plusDays(t,n)` / `tool_plusHours(t,n)` | 加减，负数往前推。结果可以再喂给别的时间函数 |
| `tool_format(t, 'yyyy-MM-dd HH:mm')` | 格式化，格式串写错直接报错 |

**字符串**——参数都收任意类型，传数字/时间进来会自动转字符串（不会变 null）。

| 函数 | 说明 |
|---|---|
| `tool_isBlank(v)` / `tool_isNotBlank(v)` | 判空 |
| `tool_str(v)` | 任意值转串，**null 转成空串**而不是 `"null"` 这四个字 |
| `tool_num(v, 默认值)` | 🔴 安全转数字。`params['times']` 是 Object，直接 `> 3` 在类型不一致时会静默判 false |
| `tool_defaultIfBlank(v, 默认值)` | 空就给默认值 |
| `tool_trim` / `tool_upper` / `tool_lower` / `tool_substring(v, n)` | 常规变形 |
| `tool_replace(v, 旧, 新)` / `tool_split(v, 分隔符)` | 替换 / 切分。**分隔符是纯文本不是正则**，写 `'|'` 就是竖线本身 |
| `tool_startsWith` / `tool_endsWith` / `tool_equalsIgnoreCase` | 判定 |
| `tool_mask(phone, 3, 7)` | 脱敏 → `138****8000`。**打日志前先过一遍** |

**JSON**

| 函数 | 说明 |
|---|---|
| `tool_jsonGet(payload, 'order.amount', 0)` | 🔴 按路径取值。点号分隔，纯数字那段按下标取；**断在哪一层都返回默认值**。入参可以是 map，也可以是 JSON 串 |
| `tool_toJson(v)` / `tool_toJsonPretty(v)` | 转 JSON 串，打日志用。⚠️ 传字符串进来原样返回，不加引号 |
| `tool_parseJson(text)` | 解析成 map/list。**解析失败直接报错**——返回 null 会让脚本静默走完一条错误分支 |

> 深层取值别写 `payload['order']['amount']`：中间某层不存在时报错信息里看不出是哪一层断的，用 `tool_jsonGet`。

## 集合怎么写（QLExpress 4.x 和 3.x 不一样）

**4.x 有原生字面量，不需要 3.x 的 `NewList` / `NewMap`：**

```javascript
l = [1, 2, 3];            // ArrayList
m = {"a": 1, "b": 2};     // LinkedHashMap
m2 = {:};                 // 空 map —— 不是 {}，那是空代码块
m['c'] = 3;  m.d = 4;     // map 写入、读取都可以
v = l[0];   n = l.length;
for (x : l) { ... }
big = l.filter(x -> x > 1);   // filter / map 是 QL 内置扩展，隔离策略下也能用
```

**🔴 但所有 Java 方法调用都是不通的**（引擎跑在 `isolation` 安全策略下）：

```javascript
l.add(x)    l.size()    l.sort()    m.put(k,v)    m.keySet()    s.length()
new ArrayList()    new HashMap()
```

在脚本顶部写 `import java.util.HashMap;` **救不了**——import 只影响「类名解析得出来吗」，不影响「能不能调用它」。
（QL 默认已经 import 了 `java.lang`、`java.util`、`java.math`、`java.util.stream`、`java.util.function` 五个包，
而且 import 语句必须在文件最开头，写在语句后面是语法错误。）

所以缺的那几件事由 `tool_` 补：

| 想干的事 | 写法 |
|---|---|
| 列表追加（`l.add` 不通） | `tool_listAdd(l, x)`，返回同一个列表 |
| 造列表 / map | `tool_listOf(a, b)` / `tool_mapOf('a', 1, 'b', 2)` |
| **集合大小** | `tool_size(coll)` |
| 安全取值 | `tool_get(params, 'tier', 'NORMAL')` |
| 包含判定 | `tool_contains(l, x)` / `tool_contains(m, 'key')` |
| 拼字符串 | `tool_join(l, ',')` |

**两个静默的坑：**

- 🔴 `m.length` 对 map **不报错，返回 null**——那是在取一个名叫 `length` 的键。大小一律用 `tool_size`。
- 🔴 `params['tier']` 键不存在时是 `null`，然后 null 一路往下走、静默走错分支。用 `tool_get` 给个默认值。
- 🔴 数字比较：字面量里的 `1` 是 Integer，算出来的 `0 + 1` 是 BigDecimal（`precise=true`），
  `tool_contains` 已经按数值比了，但自己写 `==` 时要注意。

## 脚本自己的计数器（cache_ 域）

库里没有「参与流水」表的玩法（BASIC 活动就是），「这个人今天参与过几次」只能靠它：

```javascript
times = cache_incr('join:' + memberId, 86400);   // +1 并返回累加后的值，当天有效
if (times > 3) {                                  // 注意是 > 3 不是 >= 3
    return null;                                  // 今天次数用完
}
return draw_executeDrawByScript('POOL_A');
```

| 函数 | 说明 |
|---|---|
| `cache_incr(name, ttl秒)` | +1 并返回**累加后**的值，第一次计数时开始计 TTL（固定窗口，不续期） |
| `cache_count(name)` | 读当前计数，不存在返回 0 |
| `cache_set/get/exists/del(name, ...)` | 标记位，`set` 要带 TTL |
| `cache_ttl(name)` | 剩余秒数，-2 = 键不存在，-1 = 没设过期 |

**规矩：**

- 键名你只能决定最后一段。实际的 key 是 `项目名:环境:script:{脚本编码}:{你给的名字}`——所以两个活动的脚本不会互相踩，
  **在线试跑也碰不到线上脚本的键**。名字只允许 `A-Za-z0-9_:.-`，最长 64。
- **TTL 必填，最长 30 天。** 没有 TTL 的键永不过期，键名写错一次就是一批永远没人发现的垃圾。
- 🔴 **写入不随事务回滚。** `cache_incr` 之后抛异常，数据库会回滚，这次 +1 不会退回去。
  所以它的语义是「尝试了几次」，不是「成功了几次」。要「成功几次」去数业务表（抽奖用 `draw_countDrawn`）。

## 怎么在脚本里打日志

脚本没有断点也没有单步，跑完只剩一个返回值。判定为什么是这个结果，除了它自己说出来，事后只能靠重放。

```javascript
tool_log('准入判定', 'memberId=', memberId, 'level=', memberLevel);
if (memberLevel < 3) {
    tool_logWarn('等级不够，拒绝');
    return false;
}
return true;
```

- `tool_log(...)`：INFO；`tool_logWarn(...)`：WARN，留给「走到了不该走的分支」。
- 参数个数不限，**用空格拼接**，不用 `{}` 占位符 —— 占位符对不齐时，最需要的那个值恰好打不出来。
- 每行自动带上脚本名（`[draw/vip_entry] ...`），日志名是 `solvela.script`，可以在 logback 里单独调级别。
- **单次执行最多 100 行**，超了只再说一句「已达上限」就闭嘴。循环里打日志会撞到这条 —— 把它挪到循环外。
- 返回值是拼好的那行文本，没有副作用，调多少次都行。

## 新增一个脚本

1. 按上面的规则建文件
2. 启动应用 —— 语法错、场景不存在、目录放错，都会 **直接启动失败**并指出是哪个文件
3. 在后台「脚本管理」页确认它已入库
4. 把它挂到业务对象上（`t_script_ref`），或用 `ScriptRuntime` 在代码里按挂载点调用

## 当前这三个脚本是什么

**格式样例，没有挂到任何业务对象上。**它们的作用是：证明加载链路是通的、给新脚本当模板。
`t_script_ref` 里查不到它们，所以随时可以删。

真正有业务含义的脚本要等 `member` / `risk` / `mall` 等域的 `@ScriptFunction` 铺开之后才写得出来 —— 现在只有 `tool_` 域的
5 个纯函数，表达不了任何真实的营销判据。
