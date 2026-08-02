# 后端升级白皮书 · Spring Boot 3.5.16 → 4.1.0

> 分支：`upgrade/springboot4`（从 `task` 拉出）
> 日期：2026-08-02
> 范围：`smart-admin-api-java17-springboot3/` 全部四个模块（sa-base / common-api / sa-marketing / sa-admin）
> 验证状态：全模块编译通过 · 单测 73/73 全绿 · **真实启动成功（10.051s，日志无 ERROR）** · 登录/验证码/Swagger 接口实测 200
> · sa-token 新 Redis DAO 读路径实测通 · ip2region 新旧数据布局逐字段核对 · 前端 `vite build` 通过

---

## 0. 一句话结论

升级分两步走：**先做依赖清理（可独立回滚），再做 Spring Boot 4 迁移**。
迁移的主体工作量不在 Spring 本身，而在 **Jackson 2 → Jackson 3（`com.fasterxml` → `tools.jackson`）**，
共改动 40 个源文件；真正卡住路的只有一个 —— **knife4j 没有 Boot 4 版本，只能换回官方 springdoc**。

最值得记住的三条：**①** Jackson 3 的 mapper 不可变、受检异常变非受检，破坏性远超"改个包名"（§4.2）；
**②** Redisson 4 不再把空密码当"没密码"，一行空的 `password:` 就能让启动失败（§4.3）；
**③** 验证升级结果前，**先确认跑的是不是刚构建出来的那个包**（§4.4）——
僵尸 JVM 锁着 jar 会让 `mvn clean` 静默失败，报错一模一样，极易误判成"这个升级做不通"。

---

## 1. 版本变更总表

### 1.1 主干框架

| 组件 | 升级前 | 升级后 | 性质 |
|---|---|---|---|
| Spring Boot | 3.5.16 | **4.1.0** | 大版本 |
| Spring Framework | 6.2.19（传递） | **7.0.8**（传递） | 大版本 |
| Jackson | 2.21.4（`com.fasterxml`） | **3.1.4（`tools.jackson`）** | 大版本、换包名 |
| Redisson | 3.50.0 | **4.6.1** | 大版本 |
| spring-security-crypto | 6.5.1 | **7.1.0** | 大版本 |
| MySQL Connector/J | 9.7.0 | **26.7.0** | Oracle 改用日历版本号 |
| API 文档 | knife4j 4.6.0（内含 springdoc 2.7.0） | **knife4j(baizhukui) 5.2.1 + springdoc 3.0.3** | 换 fork，见 §4.1 |
| MyBatis-Plus | 3.5.17（boot3 starter） | 3.5.17（**boot4 starter**） | 换 artifact |
| sa-token | 1.45.0（boot3 starter） | 1.45.0（**boot4 starter**） | 换 artifact |

### 1.2 常规版本推进（本轮顺带升到最新）

`awssdk-s3` 2.48.3→2.50.2 · `commons-codec` 1.22.0→1.22.1 · `httpclient5` 5.6.2→5.6.3 · `jsoup` 1.22.2→1.23.1
· `ip2region` 2.7.0→**3.3.7** · `tika-core` 3.1.0→**3.3.2**

其余 20 余个依赖经 `versions:display-property-updates` 核对**已是最新**（hutool 5.8.47、guava 33.6.0-jre、
commons-* 全系、poi 5.5.1、bcprov 1.85、velocity、freemarker、p6spy、reflections、qlexpress 等）。

### 1.3 有意不升的

| 组件 | 可升版本 | 不升原因 |
|---|---|---|
| tika-core | 4.0.0-**beta**-1 | 只有 beta，不进生产。**已升到 4.x 之前的最新稳定版 3.3.2** |

> ⚠️ `versions:display-property-updates` 报的是 `tika 3.1.0 → 4.0.0-beta-1`，**它会跳过 3.x 线直接指到最高版本**，
> 容易让人以为 3.1.0 已经是 3.x 的终点。实际 3.x 一路发到了 3.3.2。核对时要看完整版本列表，别只看插件那一行。

> ⚠️ `ip2region` 的 GitHub release tag 是 **v3.17.0**，但那是**整个项目**的发版号
> （涵盖 IPv4/IPv6 数据文件与 Go/Erlang/Nginx/Java 等各语言绑定）。
> Java 的 Maven 构件 `org.lionsoul:ip2region` 版本号是**独立**的，Central 上最高只到 **3.3.7**
> （实测 3.17.0 / 3.10.0 / 3.4.0 的 pom 均 404）。两个号不要对齐着看。

---

## 2. 依赖清理（升级前置动作，可独立评审）

清理和升级是两件事，先清理能让升级面变小，也方便出问题时二分定位。

### 2.1 删除的未使用依赖

| 依赖 | 判定证据 |
|---|---|
| `net.1024lab:smartdb` 1.2.0 | 源码零引用 `net.lab1024.smartdb`，jar 停留在 2021 年 |
| `concurrentlinkedhashmap-lru` 1.4.2 | 源码零引用 |
| `poi-ooxml-full` 5.5.1 | 零引用 `org.openxmlformats`，且拖着 xmlbeans 全量 schema，**21MB** |
| `poi-scratchpad` 5.5.1 | HSLF/HWPF/HSSF 一处没用，**4MB** |
| `redisson-spring-data-27`（depMgmt） | 从未被任何模块声明，死条目 |
| `springdoc-openapi.version`（property） | 属性声明了但没有 dependency 引用（后来在换 springdoc 时复活） |

### 2.2 结构性修正

**① common-api 的 227 行 pom 删到 66 行。**
它把 sa-base 的 40 条依赖整段抄了一遍，而它第一条就是 `depend on sa-base` ——
那些依赖全是 compile 作用域，本来就传递过来。抄的那份对 classpath 零影响
（`mybatis-plus-extension` 就没抄，一直靠传递用着），只是让"这里到底用了什么"完全看不出来。
现在只留 `dependency:analyze` 认定的 7 条直接依赖，与 sa-marketing 的写法对齐。

**② poi 版本错配修复。**
原先显式声明 `poi` 5.5.1，而 `poi-ooxml` 是 fastexcel 传递进来的 **5.4.1** —— POI 要求两者同版本，
这是个潜伏故障。修法有个坑：只在 depMgmt 里声明 `poi-ooxml` 是不够的，
`poi` 会按 Maven「同深度先声明者胜」被 fastexcel 拽回 5.4.1，**必须把 `poi` 核心一起管起来**。

**③ 依赖归位。** `qlexpress4` 只有 common-api 用（从 sa-base 挪走）；`reflections` 只有 sa-admin 用（从 sa-base 挪走）。

**④ 版本 pin 与依赖声明分离。** `snakeyaml` / `commons-compress` 源码零直接引用，
从 `<dependencies>` 移除、**版本 pin 保留在 dependencyManagement**，实测树上仍是 2.6 / 1.28.0，无降版本。

### 2.4 传递依赖瘦身（第三轮，2026-08-02）

`dependency:analyze` 层面已无可删（sa-base 剩余 13 条 unused 全是 starter / 驱动 / 运行期装配的误报，
common-api 与 sa-marketing 已无该告警）。剩下的空间在**传递依赖**：

| 排除项 | 从哪来 | 依据 | 省 |
|---|---|---|---|
| `com.google.protobuf:protobuf-java` | `mysql-connector-j` | 只服务 X DevAPI（`mysqlx://`），本项目走经典 JDBC | 1.78 MB |
| `software.amazon.awssdk:netty-nio-client` | `awssdk:s3` | 只用同步 `S3Client`，`S3AsyncClient` 一处没用；连带去掉 netty-codec-http/http2 等 | 1.55 MB |
| `io.reactivex.rxjava3:rxjava` | `redisson-spring-boot-starter` | 只用 Redisson 同步 API（RLock/RScript/RTopic） | 2.54 MB |

合计 **6 个构件、5.87 MB**（运行期 classpath 215 → 209 个构件，120.0 → 114.2 MB；fat jar 128M → 123M）。

**评估后决定不动的**（记录理由，避免重复讨论）：

| 候选 | 体积 | 为什么不动 |
|---|---|---|
| `org.ehcache:ehcache` + `cache-api` | 2.4 MB | **在用**。`GoodsService` 有 `FastExcel.read`，ehcache 在 xlsx 读取的共享字符串缓存路径上 |
| `commons-math3`（poi 传递） | 2.1 MB | POI 公式求值要用。Excel 是本轮唯一没有自动化覆盖的功能面（§6.1），不在这里加风险 |
| `kryo` / `reflectasm` / `minlog` / `jodd-util` | ~1 MB | Redisson 4 默认编解码器就是 Kryo5Codec，yaml 未显式改过，排掉会在第一次写 Redis 时炸 |
| `io.projectreactor:reactor-core` | 2.2 MB | **排不掉**：真实来源是 `spring-boot-starter-data-redis`（Lettuce 需要），不是 Redisson |
| `net.bytebuddy:byte-buddy` | 4.4 MB | 看着像 test 泄漏，实为 **Redisson 的 compile 依赖** |
| `bcprov-jdk18on` | 9.8 MB | 单体加密提供者，无法按需裁剪，且国密相关代码在用 |

> ⚠️ **教训**：`reactor-core` 那条我先写进了 pom，实测才发现它另有来源、排了等于没排 ——
> 属于自己制造死配置，已删掉。**加 `<exclusion>` 后必须用 `dependency:list` 复核构件是否真的消失**，
> 不能只看构建有没有报错。

### 2.3 收益

fat jar 少了约 27MB；`dependency:analyze` 的 unused-declared 告警从 60 条（sa-base 21 + common-api 39）降到个位数。

---

## 3. 升级改动点清单

### 3.1 构建配置（pom）

| 改动 | 说明 |
|---|---|
| `spring-boot-starter-aop` → `spring-boot-starter-aspectj` | Boot 4 中 aop starter **已删除并改名** |
| `mybatis-plus-spring-boot3-starter` → `...-boot4-starter` | MP 3.5.17 已提供 boot4 starter |
| `sa-token-spring-boot3-starter` → `...-boot4-starter` | sa-token 1.45.0 已提供，内部换用 `sa-token-jackson3` |
| knife4j starter → `com.baizhukui:knife4j-openapi3-boot4-spring-boot-starter` 5.2.1 | 见 §4.1 |
| redisson 排除项改名 | `redisson-spring-data-32` 在 4.x 已并入本体不存在；actuator 依赖从 starter 改成 `spring-boot-actuator` 本体 |

### 3.2 Spring API 迁移（源码）

| 文件 | 改动 |
|---|---|
| `WebServerListener` | `org.springframework.boot.web.context.*` → `org.springframework.boot.web.server.context.*` |
| `AdminApplication` | 删除 `@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)` —— 该类在 Boot 4 挪进了独立的 `spring-boot-security` 模块，本项目只依赖 `spring-security-crypto`，classpath 上根本没有它；**Boot 对 exclude 里不存在的类会直接启动失败**，必须删 |
| `RestClientConfig` | ① `MappingJackson2HttpMessageConverter` → `JacksonJsonHttpMessageConverter`（Spring 7 已移除前者）；② `HttpComponentsClientHttpRequestFactory.setConnectTimeout` **被删掉了**，连接超时改由连接管理器的 `ConnectionConfig` 承担；③ `setReadTimeout`/`setConnectionRequestTimeout` 只剩 `Duration` 重载 |
| `RedisConfig` | `Jackson2JsonRedisSerializer` → `JacksonJsonRedisSerializer`（spring-data-redis 4 的 Jackson 3 版本） |

### 3.3 Jackson 2 → 3 迁移（改动主体，40 个文件）

包名映射：

| Jackson 2 | Jackson 3 |
|---|---|
| `com.fasterxml.jackson.annotation.*` | **不变**（注解仍在原包） |
| `com.fasterxml.jackson.core.*` | `tools.jackson.core.*` |
| `com.fasterxml.jackson.databind.*` | `tools.jackson.databind.*` |
| `com.fasterxml.jackson.datatype.jsr310.*` | `tools.jackson.databind.ext.javatime.*`（已并入 databind） |

类型与 API 改名：

| Jackson 2 | Jackson 3 |
|---|---|
| `JsonSerializer<T>` / `JsonDeserializer<T>` | `ValueSerializer<T>` / `ValueDeserializer<T>` |
| `SerializerProvider` | `SerializationContext` |
| `JsonMappingException` | `DatabindException` |
| `ContextualSerializer` 接口 | **接口取消**，`createContextual` 直接是 `ValueSerializer` 的方法 |
| `ObjectCodec` / `JsonParser.getCodec()` | **移除**，改用 `DeserializationContext.readTree(p)` / `readTreeAsValue(node, Class)` |
| `gen.writeObject(x)` / `writeObjectField(n,v)` | `gen.writePOJO(x)` / `writePOJOProperty(n,v)` |
| `gen.getOutputContext().getCurrentName()` | `gen.streamWriteContext().currentName()` |
| `parser.getText()` | `parser.getString()` |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` | `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS` |
| `findValueSerializer(type, property)` | `findValueSerializer(type)`（不再接受 BeanProperty） |
| `JsonProcessingException`（受检） | `JacksonException`（**非受检**）；`serialize`/`deserialize` 不再允许声明 `throws IOException` |
| `new ObjectMapper()` 然后 `setXxx/configure/registerModule` | **mapper 不可变**，一律 `JsonMapper.builder()....build()` |
| `Jackson2ObjectMapperBuilderCustomizer` | `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer` |
| builder 的 `serializerByType(...)` | 已无此方法，统一走 `SimpleModule` 注册后 `addModule` |
| `LaissezFaireSubTypeValidator.instance` | 降级为包级私有，换用 `BasicPolymorphicTypeValidator` |

### 3.4 配置文件（四个 profile 各一处，dev/test/pre/prod 全改）

```yaml
# 改前
spring:
  jackson:
    serialization:
      write-enums-using-to-string: true
      write-dates-as-timestamps: false
    deserialization:
      read-enums-using-to-string: true

# 改后
spring:
  jackson:
    datatype:
      enum:
        write-enums-using-to-string: true
        read-enums-using-to-string: true
      datetime:
        write-dates-as-timestamps: false
```

以及删掉 `spring.data.redis.password` 那行空值（见 §4.3）。

### 3.5 knife4j 残留清理

换掉 knife4j 后，散落在四处的耦合点一并摘除：

| 位置 | 改动 |
|---|---|
| `WebServerListener` 启动横幅 | "knife4j地址: /doc.html" → "OpenAPI文档: /v3/api-docs"（原地址已 404） |
| `MvcConfig.addResourceHandlers` | 删掉 `doc.html` 的静态资源映射（那是 knife4j 的 UI 入口页）；`/webjars/**` 保留，Swagger UI 要用 |
| `SwaggerConfig.SWAGGER_WHITELIST` | 删掉 `/doc.html`（免登录白名单里的死条目） |
| `SwaggerConfig` 注释 | 删掉 "如果使用knife4j则不需要" 这句已失效的说明 |
| 四个 profile 的 yaml | 删掉整段 `knife4j:` 配置块（enable / basic 认证用户名密码） |

### 3.6 第三方 API

`RScript.ReturnType.INTEGER` → `RScript.ReturnType.LONG`（Redisson 4 枚举改名，返回类型仍是 `Long`），
落在 `DrawStockService` 的两处扣减/回滚 Lua 调用上。

### 3.7 遗留项收口（第二轮）

| 项 | 改动 | 验证 |
|---|---|---|
| `ip2region` 2.7.0 → 3.3.7 | ① 3.x 为支持 IPv6 改了加载 API：`loadContentFromFile` 返回值 `byte[]` → `LongByteArray`，`newWithBuffer` 必须显式传 IP 版本；② 数据格式差异做了归一化，见 §4.5 | **旧 xdb 数据文件（v2 格式）能被 3.3.7 直接读取**，实测三个 IP 解析正确；启动日志打印 `数据格式版本:[2]` |
| `tika-core` 3.1.0 → 3.3.2 | 纯版本推进，`SecurityFileService` 的 API 无变化 | 编译通过、启动正常 |
| sa-token Redis DAO 换官方实现 | `sa-token-redis-jackson`（依赖 Jackson 2）→ `sa-token-redis-template`（只依赖 sa-token-core + spring-boot-starter-data-redis） | 启动日志 `SaTokenDao 注入成功: SaTokenDaoForRedisTemplate`；带伪 token 请求受保护接口，正确返回"未登录"（证明读路径通） |
| 前端死注释清理 | 删掉 4 处 `// eslint-disable-*`（eslint 已卸载，注释无作用） | `npx vite build` 通过 |

**关于 Jackson 2 是否还在 classpath 上**：换掉 sa-token 的 Jackson 2 依赖后，
Jackson 2 **仍然存在**，但来源变成了 `springdoc-openapi-starter-webmvc-ui` → `swagger-core-jakarta`。
这是 springdoc 官方链路，无可替代方案。区别在于：

- 之前：Jackson 2 在**数据通路**上（sa-token 用它序列化 Redis 里的会话对象）；
- 现在：Jackson 2 只在**接口文档生成**这条旁路上，不碰任何业务数据。

后者无害，前者才是要消除的隐患。

---

## 4. 升级中的难点与解决方案

### 4.1 【拦路虎，后已解决】knife4j 的 Boot 4 适配

**现象**：knife4j 官方与主流社区分支（`com.github.xiaoymin` 4.5.0 / `com.github.xingfudeshi` 4.6.0）
都把 `springdoc-openapi-jakarta` 钉死在 **2.7.0**，而 springdoc 2.x 只支持 Spring Boot 3。

**为什么不能硬扛**：springdoc 2.7.0 的自动配置引用的是 Boot 3 的类，强行覆盖 springdoc 版本到 3.x
只会把问题从"编译期"推到"启动期 NoClassDefFoundError"。

**第一版方案**：换回官方 `springdoc-openapi-starter-webmvc-ui` 3.1.0。
项目里对 springdoc 的使用（`SwaggerConfig` / `SmartOperationCustomizer` / `SchemaEnumPropertyCustomizer`）
都是 `org.springdoc.core.*` 公共 API，一行没改就过了。代价是 UI 退回 Swagger UI，失去 `/doc.html`。

**最终方案（2026-08-02 修正）**：改用社区适配 Boot 4 的
**`com.baizhukui:knife4j-openapi3-boot4-spring-boot-starter:5.2.1`**，`/doc.html` 恢复，实测可用。

> ⚠️ 三点需要知悉：
> 1. **这是第三方 fork，不是 knife4j 官方**。groupId `com.baizhukui` 与官方 `com.github.xiaoymin`、
>    社区常用的 `com.github.xingfudeshi` 都不同。当初离开 knife4j 的理由就是"受制于 fork 的发版节奏"，
>    换到另一个 fork 并没有消除这个风险，只是换了个上游。引入前建议自行评估该发布者。
> 2. **它会把 springdoc 拉低**：实测解析结果 springdoc **3.1.0 → 3.0.3**、
>    swagger-core **2.2.52 → 2.2.47**、swagger-ui webjar 5.32.11 → 5.32.2。
>    因为父 pom 里 springdoc 的 depMgmt 条目已移除，版本完全由 knife4j 传递决定。
>    要锁高版本得自己加 depMgmt，但那样就脱离了 knife4j 的适配矩阵，需要重新验证。
> 3. **`knife4j.*` 配置块四个 profile 要一起加**。切换时只加到了 dev，
>    test/pre/prod 缺失会导致 basic 认证等设置在这三个环境静默不生效，已补齐。

**实测**：`/doc.html`、`/v3/api-docs`、`/swagger-ui/index.html`、业务接口全部 200；
启动日志 0 条 ERROR；重复启动耗时 10.8s / 11.3s，与换之前（9.5~10.5s）基本持平
（构建后首次启动会到 30~38s，那是冷启动假象，不是回归）。

### 4.2 【工作量大头】Jackson 3 的破坏性远超"改个包名"

**现象**：初看只是 `com.fasterxml` → `tools.jackson` 的替换，实际有三类硬伤：

1. **mapper 变成不可变对象**。所有 `MAPPER.configure(...)` / `setSerializationInclusion(...)` /
   `registerModule(...)` 全部失效，必须重写成 builder 链。踩点：`JsonUtils`、`RedisConfig`、`RestClientConfig`。
2. **受检异常变非受检**。`JsonProcessingException` 没了，`serialize`/`deserialize` 的父类签名
   只声明 `JacksonException`，**子类再写 `throws IOException` 会直接编译失败**（"overridden method does not throw IOException"）。
3. **`ObjectCodec` 整个删掉**。自定义反序列化器里 `jsonParser.getCodec().readTree(...)` 这种写法全废，
   要改走 `DeserializationContext`。

**解决方案**：分两轮。第一轮用脚本做机械包名替换（39 个文件），第二轮按编译错误逐类手工修 API 语义
（9 个自定义序列化/反序列化器 + 3 个配置类 + 2 个 Advice）。

**关键判断**：不做"一半 Jackson 2 一半 Jackson 3"的妥协方案。
虽然 Boot 4 的 BOM 同时管理 Jackson 2（2.21.4）与 Jackson 3（3.1.4），Jackson 2 可以留在 classpath 上
（sa-token 的 redis 序列化就还在用它），但**应用自己的代码必须单一栈**——
否则 `@JsonSerialize(using = XxxSerializer.class)` 这类注解会因为注解来自哪个包而静默不生效，
这是最难排查的一类故障。

### 4.3 【最阴的坑】Redisson 4 不再宽容空密码

**现象**：升级后启动直接失败：

```
org.redisson.client.RedisException: ERR Client sent AUTH, but no password is set
```

**根因**：四个 profile 的 yaml 里都有一行空值的 `spring.data.redis.password:`。
Redisson 3 会把空串/空值当成"没有配密码"而跳过 AUTH，**Redisson 4 取消了这个宽容处理，照发 AUTH**。
对无密码的 Redis（开发机的默认状态）就是启动失败。

**解决方案**：没有密码就**不要留这个键**。四个 profile 已一并处理并加注释。

**推论**：这类"空值被上游默默容忍"的配置在升级时是高危项，值得全局扫一遍。

### 4.4 【会误判成"升级失败"】Windows 上 JVM 不退导致跑的是旧包

**现象**：改完配置重新 `mvn clean package` 再启动，**报的还是上一次的错**，看起来像"改了没用"。

**根因**：Spring Boot 启动失败后，Redisson/Netty 的非守护线程会把 JVM 吊住不退。
这个僵尸进程在 Windows 上锁着 fat jar 和日志文件，导致：
① `mvn clean` 删不掉 jar，构建**静默**失败，于是启动的是旧包；
② `rm` 删不掉日志，新日志追加到旧文件，读到的是上一轮的堆栈（连 netty channel id 都一模一样）。

**解决方案**：每次重启前先 `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` 按命令行过滤掉残留进程。
**排查口诀：验证升级结果时，先确认跑的是不是刚构建出来的那个包。**

### 4.5 【最容易埋雷】ip2region 的数据格式是「段数不变、位置错位」

**现象**：ip2region 换数据文件时，新旧格式**都是 5 段**，既不会报错也不会长度异常：

```
v2 数据： 国家 | 区域 | 省份 | 城市 | ISP                例：中国|0|江苏省|南京市|0
v3 数据： 国家 | 省份 | 城市 | ISP  | iso-alpha2-code    例：中国|江苏省|南京市|电信|CN
```

从 index 1 起整体错开一格。原代码是 `region.split("|")` 后原样落库，
换数据文件会**静默把「省份」读成「区域」**，而且因为字符串看起来"还挺像那么回事"，
不做逐字段比对根本发现不了。

**解决方案**：不按位置硬编码，改为**在 `init()` 时读 xdb 文件头的 `version` 字段**
（`Searcher.loadHeaderFromFile(path).version`，实测现有文件返回 2），
按版本决定字段下标，并统一归一化为 `[国家, 省份, 城市, 运营商]`、剔除 `0` 占位符。

顺带治好了一个老毛病：原来落库的是 `中国|0|江苏省|南京市|0` 这种带占位符的脏值，
现在是 `中国|江苏省|南京市`。

**实测佐证**（同一份 v2 数据，强行按 v3 布局解析，数据会真的丢）：

| IP | 改造前（原样落库） | 改造后（按文件头识别） | 强行按 v3 布局 |
|---|---|---|---|
| 114.114.114.114 | `中国\|0\|江苏省\|南京市\|0` | `中国\|江苏省\|南京市` | `中国\|江苏省\|南京市` |
| 8.8.8.8 | `美国\|0\|0\|0\|Level3` | `美国\|Level3` | `美国` ← ISP 丢了 |
| 223.5.5.5 | `中国\|0\|浙江省\|杭州市\|阿里云` | `中国\|浙江省\|杭州市\|阿里云` | `中国\|浙江省\|杭州市` ← 阿里云丢了 |

**收益**：以后换 xdb 数据文件，落库内容的**含义和形状保持不变**，历史数据与新数据可比。

### 4.6 【工具链】maven-dependency-plugin 读不了 JDK 25 字节码

**现象**：`mvn dependency:analyze` 报 `Unsupported class file major version 69`。

**根因**：仓库里的 3.7.0 内置 ASM 版本太老。

**解决方案**：显式指定版本跑：

```bash
mvn org.apache.maven.plugins:maven-dependency-plugin:3.11.0:analyze -DignoreNonCompile=true
```

**同时提醒**：`dependency:analyze` 是基于字节码的，**会把所有靠反射/配置装配的依赖误报成 unused** ——
starter 类、`mysql-connector-j`、`p6spy`、`caffeine`、`commons-pool2` 全在误报名单里，
必须逐条人工复核，不能照着告警删。

### 4.7 【小坑集合】

| 现象 | 根因与修法 |
|---|---|
| `spring-boot-starter-aop` 解析不到版本 | Boot 4 BOM 里没有这个 artifact 了，改名为 `spring-boot-starter-aspectj` |
| `poi` 声明了 5.5.1 却解析成 5.4.1 | Maven 同深度「先声明者胜」，fastexcel 排在前面。必须把 `poi` 核心也纳入 depMgmt |
| `spring.jackson.deserialization` 绑定失败 | 枚举/日期开关在 Jackson 3 拆到了 `EnumFeature`/`DateTimeFeature`，Boot 4 对应 `spring.jackson.datatype.*`。**这是启动期硬失败，不是运行期才暴露** |

---

## 5. 升级带来的收益

### 5.1 生命周期与安全

- Spring Boot 3.5.x 的开源支持窗口正在关闭，4.1 是当前主线，**能持续拿到安全补丁**。
- Spring Framework 7 / Spring Security 7 全线跟进，不再需要为了迁就旧版而钉死传递依赖版本。
- 顺带把 awssdk / commons-codec / httpclient5 / jsoup 推到最新，减少已知 CVE 面。

### 5.2 技术栈现代化

- **Jackson 3 的不可变 mapper**：配置只能在 builder 阶段完成，杜绝了"运行期某处偷偷 `setXxx` 改全局 mapper"
  这类极难排查的串扰问题——本项目原来有 3 处这种写法。
- **Jakarta EE 11 / Tomcat 11**：实测启动跑在 Apache Tomcat/11.0.22 上。
- **API 文档回到官方 springdoc**：不再受第三方 fork 的发版节奏牵制（这次的拦路虎正是它）。

### 5.3 工程质量（清理阶段的收益）

- fat jar 从 155M 级别减到 **123M**（依赖清理约 27MB + 传递依赖瘦身 5.87MB）。
- common-api 的 pom 从 227 行降到 66 行，"这个模块用了什么"重新变得可读。
- 修掉一个潜伏故障（poi / poi-ooxml 版本错配）和两个死配置（redisson-spring-data-27、springdoc 空属性）。

### 5.4 可度量的验证结果

| 项 | 结果 |
|---|---|
| 全模块编译 | ✅ 通过 |
| 单元测试 | ✅ 73/73（draw 引擎 9 · FPE 12 · TicketMatcher 12 · SequenceCursor 7 · Settle 5 · TaskPeriod 8 · TaskProgress 20） |
| 真实启动 | ✅ `Started AdminApplication in 10.051 seconds`，启动日志无 ERROR |
| `GET /login/getCaptcha` | ✅ 200，返回验证码 base64 |
| `POST /login` | ✅ 200，请求体反序列化 + ResponseDTO 序列化正常（Jackson 3 全链路） |
| `GET /swagger-ui/index.html` | ✅ 200 |
| `GET /v3/api-docs` | ✅ 200 |
| MyBatis-Plus / Redis / sa-token | ✅ 启动日志可见正常查询与连接 |
| sa-token 新 Redis DAO | ✅ `SaTokenDao 注入成功: SaTokenDaoForRedisTemplate`；伪 token 打受保护接口正确返回"未登录"（证明读路径通） |
| ip2region 3.3.7 读旧数据 | ✅ 启动日志 `ip2region.xdb 加载完成，数据格式版本:[2]`；三个 IP 解析结果逐字段核对正确 |
| ip 地区归一化 | ✅ 两种字段布局分别实测，见 §4.5 对照表 |
| 前端 `npx vite build` | ✅ 通过 |

> 验证时 1024 端口被开发者 IDE 里的调试实例占用，测试实例改跑 11024 端口 —— 不要把 IDE 实例的响应当成本包的结果（这个坑见 §4.4）。

---

## 6. 遗留事项

> 状态截至 2026-08-02。原「单独排期」的三项（ip2region / tika / sa-token Jackson 2）已全部完成，
> 见 §3.7；数据格式差异已归一化，见 §4.5。

### 6.1 上线前必须做

1. **Redis 必须清一次**。两处格式都变了：
   ① `RedisConfig` 的默认类型信息从 Jackson 2 换成 Jackson 3，类型标记格式不保证互通；
   ② sa-token 的会话存储从 `sa-token-redis-jackson` 换成 `sa-token-redis-template`，
   序列化方式从「Jackson 2 对象」变成「sa-token(jackson3) 序列化的字符串」。
   **不清的话，老 key 反序列化会失败；清了则所有在线用户被登出**，请安排在低峰期。
2. **Excel 导入导出跑一次冒烟**。删掉 `poi-ooxml-full` 后 OOXML schema 走的是 `poi-ooxml-lite`，
   现有代码（XSSFWorkbook/XSSFSheet/XSSFPictureData/XSSFRelation）与 fastexcel 常规读写都在 lite 覆盖范围内，
   但若碰到冷门 xlsx 特性会在运行期抛 `NoClassDefFoundError: org.openxmlformats.schemas.*`，
   届时把 `poi-ooxml-full` 加回来即可。
   **这是本轮唯一没被自动化覆盖到的功能面**，其余都有单测或真实启动实测背书。
3. **通知前端/测试：接口文档地址变了**。`/doc.html` 已不存在，改用 `/swagger-ui/index.html`。
   如果有 CI 脚本、Nginx 规则、书签或对外文档指向 `/doc.html`，需要一并改。

### 6.2 已评估并决定「不做」（记录理由，避免下次重复讨论）

4. **不改用第三方 IP 查询 API（如 ipdata）**。本地 ip2region 方案完全可用，且 API 方案在本项目是退步：
   - **调用量对不上**：`SmartIpUtil.getRegion()` 挂在登录、**每条操作日志**、数据追踪三个切面上
     （`OperateLogAspect` / `DataTracerService` / `LoginService`），每个后台写操作都会打一次，
     免费额度 1500 次/天几个活跃运营就用光。
   - **把外部网络调用塞进登录链路**，多一个延迟来源与故障点；本地查表是微秒级、零失败。
   - **把用户 IP 送到第三方**，与本项目「满足《网络安全》《数据安全》、三级等保」的定位冲突，会是审计问题。
   - 若将来确有需求（IPv6、更精确的境外数据），正确做法是**本地库做主路径 + API 只做兜底**，
     并配套限流、缓存、密钥管理与降级策略 —— 那是独立需求，不应混进版本升级。
5. **不升 tika 4.0**。目前只有 4.0.0-beta-1，beta 不进生产。已升到 3.x 线最新的 3.3.2。
   - ⚠️ 复核时注意：`versions:display-property-updates` 报的是 `3.1.0 → 4.0.0-beta-1`，
     **它会跳过 3.x 线直接指最高版本**，容易误判成"3.1.0 已经是终点"。要看完整版本列表。

### 6.3 观察项（无需动作，定期回看）

6. **Jackson 2 仍在 classpath 上**，来源是 `springdoc-openapi-starter-webmvc-ui` → `swagger-core-jakarta`。
   属于**接口文档生成旁路，不碰任何业务数据**，无官方替代方案。
   观察 springdoc / swagger-core 后续是否迁移到 Jackson 3，届时可彻底移除。
7. **ip2region 数据文件可随时更新，代码已不受格式影响**。解析逻辑按 xdb 文件头版本
   自适应字段布局（见 §4.5），换成 v3 数据文件不需要改代码。
   新数据的地区名做过标准化、且多了国家代码字段（国家代码不进展示串）。
   当前 v2 数据文件工作正常，不换也没有故障风险。
   - 注意：GitHub 的 v3.17.0 是**整个项目**的发版号，Java 构件 `org.lionsoul:ip2region`
     版本号独立，Central 上最高就是 3.3.7，两个号不要对齐着看。
8. **knife4j 若发布 Boot 4 版本**，可考虑换回（当前是被迫改用 springdoc，UI 有落差）。

### 6.4 可选的小清理

9. **前端 lint 是彻底卸掉的状态**。若要恢复，需要写 `eslint.config.js`（flat config，eslint 9 起不认 eslintrc）、
   从 stylelint 配置里摘掉已废弃的 `stylelint-config-prettier`，并补上 `lint` 脚本 ——
   不要只把包装回来，那样仍然跑不起来。
10. `smart-admin-web-javascript/postcss.config.cjs` 的 `plugins` 是空对象（Tailwind v4 走 vite 插件），文件可删。
11. `uuid` 依赖的唯一使用者是 `src/components/framework/text-ellipsis/index.vue`，而该组件全项目无人引用；
    删组件即可连带卸掉 uuid，或把那行换成原生 `crypto.randomUUID()`。
12. `smart-app/`（uni-app）那套 eslint 8 链同样闲置无脚本，本轮按要求未动。

## 7. 回滚方案

本轮全部改动在 `upgrade/springboot4` 分支上，`task` 分支未受影响，直接切回即可。

分支内共 6 个提交，**前端清理与后端升级是分开的**，可按需 revert 或 cherry-pick：

| # | 提交 | 内容 | 可否单独摘出 |
|---|---|---|---|
| 1 | 前端依赖瘦身 | 仅 `smart-admin-web-javascript/`，卸 v-viewer 与失效的 eslint/stylelint 链 | ✅ 与后端无耦合，可单独摘到 `task` |
| 2 | 后端升级 Spring Boot 4 | 依赖清理 + Boot 4 迁移。两阶段在同一提交里（都改了同一批 pom，无法干净拆分） | ❌ 本轮的地基 |
| 3 | 白皮书版本号订正 | 纯文档 | ✅ |
| 4 | 清掉 knife4j 全部残留 | 横幅 / MvcConfig / 白名单 / 四个 yaml | 依赖 #2 |
| 5 | 收口遗留排期项 | ip2region 3.3.7、tika 3.3.2、sa-token Redis DAO 换官方、前端死注释 | 依赖 #2 |
| 6 | ip2region 数据格式归一化 | `SmartIpUtil` 按 xdb 文件头版本自适应 | 依赖 #5 |

> ⚠️ **数据面的注意**：Redis 里的值格式变了（见 §6.1），**回滚后同样要清一次 Redis**，
> 否则 Jackson 3 写入的值会让回滚后的 Jackson 2 反序列化失败 —— 这个方向的污染同样存在，别只防单向。

> ⚠️ 回滚会一并退回 `ip_region` 的归一化（重新落 `中国|0|江苏省|南京市|0` 这种带占位符的值）。
> 已归一化的历史数据不会自动还原，两种格式会在表里共存 —— 不影响功能，但做数据分析时要注意。
