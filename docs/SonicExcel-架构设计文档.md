# SonicExcel 架构设计文档

> 目标：用 `org.dhatim:fastexcel` 作底层，配合 Java 24+ 的 Gatherers 与拉模型引擎，自研一套对标
> 阿里系 EasyExcel / FastExcel 的声明式 Excel 读写框架，替换并彻底摘除 `cn.idev.excel:fastexcel` + `org.apache.poi:*`。
> 前置阅读：`docs/营销中台-会话交接文档.md` §10（依赖瘦身轮次的记账口径与踩坑）
> 撰写 2026-08-08 · **v2 修订 2026-08-08（并入底层引擎源码级尽调 + 依赖隔离补丁）** · 状态：**已定稿，待实施**
>
> 本文所有「现状」与「底层行为」结论均来自**实测**（`mvn dependency:tree`、`javap`、jar 字节码扫描、JDK 25 实跑），
> 不是从 README 或文档推断。凡标注 🔴 的是**尚未拍板**的点。

### v2 改了什么

v1 回答了"框架该长什么样"，**没有回答"底层引擎到底是什么货色"**。v2 补的是后一半 ——
对 `fastexcel` 0.20.2 做了源码级扫描，结论推翻了 v1 的四处判断。

| 来源 | 结论 |
|---|---|
| v1 说"引入 dhatim 无传递依赖膨胀，可删 commons-compress" | ❌ **错**。reader 依赖 commons-compress，删不掉。见 §2.4 |
| v1 收益账估 13.5MB | ✅ **低估**。第③档落地后按 classpath 差集实测净减 **18.66MB**（idev-fastexcel 拖了 ehcache + JAXB，poi 拖了 commons-math3）。见 §2.2 |
| v1 说"fastexcel 写端不支持图片" | ❌ **错**。0.20.2 的 `Worksheet#addImage` 存在。水印是**产品决策**砍的，不是技术限制。见 §1.2 |
| v1 D6「reader 是否落临时文件」待实测 | ✅ **已定案**：不落盘，**整个文件进堆**。读侧 API 因此强制 `Path` 入参。见 §8.5 / §2.4 |
| 🔴 **新发现：aalto-xml 会劫持 JVM 全局 StAX** | 实测 classpath 上有 **6 个库**会受影响（含 S3）。见 §9.1 |
| 🔴 **新发现：写端 shared strings 无法关闭（无开关）** | 但 `inlineString()` 可绕过。这是内存红线，见 §7.6 |

---

## 0. 一句话结论

**这不是"换个门面"，是把 Excel 层的三件脏活（类型转换、字典翻译、脏数据定位）从 service 里收走，
顺手把 20.9MB 的 POI 全家桶连根拔掉，并且白捡一个更好的编程模型（拉模型 → 事务边界回到业务手里）。**

现有代码只有 3 处用到 Excel，迁移面极小；设计重量集中在
**依赖隔离（§9.1）**、**错误模型（§6）**、**转换器的 Spring 集成（§5.3）**、**内存红线（§7.2 / §7.6 / §8.5）**。

### 已确认的产品决策

| 决策 | 结论 | 影响 |
|---|---|---|
| 框架命名 | **SonicExcel**，不跟随 `Smart*` 前缀，为将来独立开源留口子 | 见 §2.3 |
| Excel 水印 | **直接砍掉** —— 拿到文件的人只觉得碍事。**注意：这是产品决策，不是引擎不支持**（`addImage` 是有的） | `exportExcelWithWatermark` 与 `Watermark` / `CustomWaterMarkHandler` 内部类整体删除；`EnterpriseController` 改调普通导出，接口路径与出入参不变，**前端零改动** |
| 水印删除后的审计线索 | **靠现有操作日志承接，不拼文件名、不加新代码** | `OperateLogAspect` 的切点是 `@within(...) \|\| @annotation(...)`，而 `EnterpriseController` 的 `@OperateLog` 打在**类上** —— `exportExcel` 本就被覆盖，操作人 / 时间 / IP / 入参已入库。**零改动** |
| 模板填充（`fill`） | **不做，风险关闭** —— 中台没有发票/合同这类票据打印需求；真出现走**导出 PDF**，不走 Excel 模板 | 这是 v1 §9 里唯一可能让整个方案翻盘的因素，现已排除 |

---

## 1. 定位与 Non-Goals

### 1.1 定位

| 维度 | 目标 |
|---|---|
| 运行环境 | Java 25 / Spring Boot 4.1，非模块化（无 JPMS） |
| 底层引擎 | `org.dhatim:fastexcel` **0.20.2**（写）+ `org.dhatim:fastexcel-reader` **0.20.2**（读），**不引入任何 POI** |
| 数据载体 | POJO（Lombok `@Data`）与 `record` **一等公民并列支持** |
| 内存 | 导出堆占用与数据量解耦（前提：§7.6 的 inline strings 红线）；导入按行惰性流出（前提：§8.5 的 Path 入参） |
| 定位边界 | **业务系统的导入导出组件**，不是通用 Excel 操作库 |

**语言版本的诚实说法**：这套设计的真实门槛是 **Java 24**（`Gatherers` 转正），
降到 21 只需把 `windowFixed` 换成约 15 行手写攒批。对外不要宣传"Java 25 最新 API 打造"，经不起追问。

### 1.2 Non-Goals

| 不做 | 原因 |
|---|---|
| 图片 / 水印 | **产品决策**（引擎 0.20.2 其实支持 `addImage`，写清楚以免后人误读） |
| **模板填充（`fill`）** | **产品决策**：中台无票据打印需求，此类场景走 PDF |
| `.xls`（BIFF97）读写 | fastexcel 不支持。**但必须给出可诊断的报错**，见 §8.3 |
| **兼容非 Excel 工具产出的 xlsx（WPS 等）** | **产品决策：不兼容。** 只承诺 Microsoft Excel 生成的标准 OOXML。<br>注意这只影响**导入**：我们自己导出的是标准 xlsx，WPS 打开没问题。<br>这类文件是合法 zip，`WorkbookGuard` 按字节头挡不住，只会在解析到一半时炸 —— 所以读引擎把 `ExcelReaderException` 统一翻译成「请用 Excel 另存为 .xlsx 后重试」，见 §8.7 |
| 公式求值、富文本、批注、条件格式 | 业务用不到；读端遇到公式只取缓存值 |
| 多级 / 合并表头 | v1 不做。靠 `ws.merge` 可扩展，但会污染表头寻址逻辑（§8.1），需单独设计 |
| **`WriteHandler` 式扩展点体系** | 有意放弃。EasyExcel 的万能后门换来的是庞大 API 面；我们要小而清晰。**代价见 §15** |
| 单元格样式的完全可编程 | 只提供表头样式 + 数据格式化，不开放任意 style DSL |

### 1.3 性能目标（可证伪的写法）

基准环境：本机开发容器，`-Xmx512m`，10 列（5 String / 2 BigDecimal / 1 LocalDateTime / 1 Integer / 1 Boolean）。

| 场景 | 目标 | 验证方式 |
|---|---|---|
| 导出 100 万行 | 堆峰值 < 256MB，耗时 < 40s | §13 基准用例，JFR 采样 |
| 导出 1000 万行 | **必须走自动滚 Sheet**（§7.3）+ **强制 inline strings**（§7.6），不承诺单文件可被 Excel 流畅打开 | 同上 |
| 导入 100 万行 | 堆峰值 < 256MB。**前提：走 `Path` 入参**（§8.5），且含 sharedStrings 常驻 | 同上 |
| 属性访问开销 | 目标 < 总耗时 5%（**不是 0**，见 §5.4） | JFR 火焰图 |

---

## 2. 现状、收益与底层引擎尽调

### 2.1 现存使用面（全部）

| 位置 | 用法 | 迁移后 |
|---|---|---|
| `SmartExcelUtil#exportExcel` | 一次性 `Collection` 导出 | 保留签名，内部换引擎 |
| `SmartExcelUtil#exportExcelWithWatermark` | POI 注入 sheet 背景图 | **整体删除** |
| `GoodsService#importGoods` | `doReadSync()` 全量读 List | 换 `SonicExcel.read(path, ...)`，顺带拿到行级错误 |
| `GoodsExcelVO` / `GoodsImportForm` / `EnterpriseExcelVO` | 仅 `@ExcelProperty("中文名")` | 换 `@SonicTitle`，一对一平移 |
| `GoodsService#getAllGoods` | **手写字典/枚举翻译拼 VO** | 用 converter 收走，见 §5.3 |

### 2.2 依赖收益账（**第③档完成后的实测结果**）

对比 `sa-admin` 完整 runtime classpath 在改造前后的差集（在 500a9aaa 建临时 worktree 各跑一次
`dependency:build-classpath`，逐 jar 量尺寸）：**214 个 jar → 204 个，净减 18.66 MB**。

| 移除（15 个） | KB |
|---|---|
| `poi-ooxml-lite` 5.5.1 | 5855 |
| `poi` 5.5.1 | 2936 |
| **`ehcache` 3.12.0**（cn.idev.excel 拖入） | 2413 |
| **`commons-math3` 3.6.1**（poi 拖入） | 2162 |
| `xmlbeans` 5.3.0 | 2160 |
| `poi-ooxml` 5.5.1 | 2004 |
| **`jaxb-runtime` + `jaxb-core` + `txw2` + `istack-commons-runtime`**（ehcache 拖入） | 1134 |
| `cn.idev.excel:fastexcel` + `fastexcel-support` | 983 |
| `curvesapi` / `commons-csv` / `SparseBitSet` | 194 |
| **小计** | **19842** |

| 新增（5 个） | KB |
|---|---|
| `aalto-xml` 1.4.0 | 351 |
| `stax2-api` 4.3.0 | 190 |
| `org.dhatim:fastexcel` 0.20.2 | 129 |
| `org.dhatim:fastexcel-reader` 0.20.2 | 40 |
| `opczip` 1.2.0 | 27 |
| **小计** | **736** |

**净减 18.66 MB**，另有 POI / xmlbeans 历年 CVE 面一并消失。

⚠️ **三个原本以为能删、实际删不掉的**（v2 的估算里错误地算进了收益，这里修正）：

| | 为什么还在 |
|---|---|
| `commons-compress` | `fastexcel-reader` 的编译期依赖，换个上游继续存在 |
| `commons-codec` | 原以为随 `commons-csv` 一起走，实测它现在挂在 `commons-compress` 下面 |
| `cache-api` | 原以为是 ehcache 拖进来的，实测 **redisson** 也依赖它，与 Excel 无关 |

⚠️ `commons-lang3` / `commons-io` / `commons-collections4` **仍然删不掉**（项目自身分别用了 41 / 7 / 66 处），
但摘掉 POI 后它们从"被迫保留"变成"我们自己在用"，**依赖关系变干净，体积不变**。别把这一项算进收益。

⚠️ `log4j-api` **不会被删除** —— 它会退回到 `spring-boot-starter-logging → log4j-to-slf4j` 这条路径上。

### 2.3 包结构

```
sa-base/src/main/java/net/lab1024/sa/base/sonicexcel/
├── SonicExcel.java                 门面（唯一入口）
├── SonicExcelConfiguration.java    与 Spring 的唯一接线点：StAX 隔离 / BeanFactory / profile
├── SonicExcelSettings.java         框架级开关（严格元数据模式）
├── annotation/SonicOptions.java    导入模板的下拉选项（§8.6）
├── SonicStaxIsolation.java         StAX SPI 隔离与启动自检（§9.1）
├── annotation/SonicTitle.java
├── converter/
│   ├── SonicConverter.java
│   ├── SonicConverterFactory.java  实例解析：Spring Bean 优先，回退无参构造
│   └── builtin/                    仅 JDK 级：枚举、Y/N、BigDecimal 精度
├── meta/
│   ├── ColumnMeta.java  SheetMeta.java  MetaResolver.java
├── option/
│   └── SonicOptionProvider.java    导入模板下拉的选项来源（§8.6）
├── write/
│   ├── SonicSheetBuilder.java      xlsx 写门面（AutoCloseable）
│   ├── SonicCsvWriter.java         CSV 通道（§7.7）
│   ├── SonicTemplateWriter.java    导入模板 + 下拉校验（§8.6）
│   ├── RowConverter.java           实体 → 行值数组 + 错误策略（xlsx / CSV 共用）
│   ├── CellWriter.java             类型路由 + inline strings 红线（§7.6）
│   ├── ColumnWidths.java           列宽估算（§7.4）
│   └── SheetRoller.java            刷盘 + 超行数自动滚 Sheet + 列宽落地
├── SonicTempFiles.java             导入临时文件：创建 + finally 删 + 启动扫残留（§10.2）
├── read/
│   ├── SonicSheetReader.java       读门面
│   ├── WorkbookGuard.java          入口体检：.xls 探测 + zip 炸弹（§8.3 / §9.2）
│   ├── HeaderMatcher.java          表头动态寻址 + 隐形空白字符归一化
│   ├── CellCoercion.java           单元格 → Java 类型（文本形态的数字/日期也要能读）
│   └── RowMapper.java              行 → 对象（POJO setter / record canonical 两条路径）
├── error/
│   ├── SonicErrorPolicy.java  SonicRowError.java  SonicReadResult.java
└── SonicExcelException.java

sa-base/src/main/java/net/lab1024/sa/base/module/support/dict/excel/   ← 业务侧（D2）
├── SonicDictConverter.java         依赖 DictService，不进框架目录
└── SonicDict.java                  @SonicDict("GOODS_PLACE")

sa-base/src/main/java/net/lab1024/sa/base/common/excel/
├── SonicEnumConverter.java         依赖 SmartEnumUtil / BaseEnum，同样是项目侧
├── SonicEnumOptionProvider.java    复用 @SonicEnum 给模板生成下拉选项
└── SonicEnum.java                  @SonicEnum(GoodsStatusEnum.class)

sa-admin/.../module/business/goods/excel/
└── GoodsCategoryConverter.java     依赖 CategoryQueryService
```

**转换器的归属规则：与它包装的组件同模块。** 框架目录 `sonicexcel/` 里一个业务依赖都没有，
将来要独立开源，整个目录搬走即可。

✅ **D1 已定案：包放 `net.lab1024.sa.base.sonicexcel`，与 `common` 平级。**
将来拆子模块独立开源时，一整个目录搬走即可，不用从 `common` 里往外挑文件。

### 2.4 底层引擎尽调（实测结论，实施前必读）

**真实依赖树与尺寸**（0.20.2，2026-06-08 发布，项目活跃）：

```
org.dhatim:fastexcel:0.20.2                    129 KB
└── com.github.rzymek:opczip:1.2.0              27 KB   ← 自身零传递依赖
                                     写端 =    156 KB   ✅ 约等于零依赖

org.dhatim:fastexcel-reader:0.20.2              40 KB
├── com.fasterxml:aalto-xml:1.4.0              351 KB   ⚠️ 见 §9.1
│   └── org.codehaus.woodstox:stax2-api:4.3.0  190 KB
└── org.apache.commons:commons-compress:1.28.0 1091 KB
                                     读端 =   1672 KB   ❌ 不是零依赖
                                     合计 =   1828 KB
```

**四条源码级事实**，每条都直接约束了本文档的设计：

| # | 事实（如何验证） | 约束了哪一节 |
|---|---|---|
| 1 | `aalto-xml` 的 jar 内含 `META-INF/services/javax.xml.stream.{XMLInputFactory,XMLOutputFactory,XMLEventFactory}`，会**篡夺 JVM 全局 StAX 实现** | §9.1 必须做隔离 |
| 2 | `fastexcel-reader` 有一个类 `DefaultXMLInputFactory`，**直接 `new com.fasterxml.aalto.stax.InputFactoryImpl()`**，不走 SPI | §9.1 的隔离**不会**影响 fastexcel 自身，可以放心做 |
| 3 | `ReadableWorkbook(InputStream)` 内部用 `commons-compress` 的 **`SeekableInMemoryByteChannel`** —— **整个 xlsx 读进堆里的 byte[]**（不是临时文件）。`ReadableWorkbook(File)` 才走磁盘随机访问 | §8.5 读侧 API 强制 `Path` 入参 |
| 4 | `Workbook` 的公开 API（javap 全量核对）**没有任何关闭 shared strings 的开关**；`StringCache` 是 package-private 的 `HashMap<String,CachedString>`，无条件启用、永不清理。但 `Worksheet` 有 **`public void inlineString(int,int,String)`** | §7.6 定为红线：String 一律走 `inlineString` |

**关于"顶级方案"的定位（写进文档避免误判）**：

| 维度 | 评价 |
|---|---|
| 流式写 xlsx 的吞吐 / 内存 | 这个赛道最好的之一，写端设计干净（opczip 做流式 zip） |
| 功能覆盖面 | **远不如 POI**：无 `.xls`、无公式求值、读端丢弃全部样式 |
| 破损文件兼容性（WPS / Numbers 产物） | POI 真正的护城河，fastexcel 这块基本没保障。**已按产品决策放弃**，转为给明确报错（§1.2 / §8.7） |
| 项目体量 | 0.x 版本、小团队。**心理预期：出兼容性 bug，大概率自己 fork 修** |

**这正是要在它上面糊一层 SonicExcel 门面的理由：底层可换，业务代码不动。**

---

## 3. 整体架构

```
                      ┌──────────────────────────────────────────┐
业务代码              │  SmartExcelUtil（HTTP 协议防腐层，§10）    │
                      │  · 下载头/文件名编码  · 探活  · 临时文件    │
                      └───────────────┬──────────────────────────┘
                                      │
                      ┌───────────────▼──────────────────────────┐
门面                  │            SonicExcel                     │
                      │  write(os, Foo.class) / read(path, Foo)   │
                      └───────┬───────────────────────┬──────────┘
                              │                       │
              ┌───────────────▼────────┐   ┌──────────▼─────────────────┐
              │  SonicSheetBuilder<T>  │   │   SonicSheetReader<T>      │
              │  · append / doWrite    │   │   · doRead / doReadAll     │
              │  · flush + 滚 Sheet    │   │   · 表头寻址 / 错误收集     │
              │  · inline strings 红线 │   │                            │
              └───────────┬────────────┘   └──────────┬─────────────────┘
                          └────────────┬──────────────┘
                      ┌────────────────▼───────────────────────────┐
元数据层（共享、缓存） │  MetaResolver → SheetMeta{List<ColumnMeta>} │
                      │  ColumnMeta = 标题+序号+格式                 │
                      │      + LambdaMetafactory 生成的访问器        │
                      │      + SonicConverter 实例                   │
                      │  缓存载体：ClassValue<SheetMeta>             │
                      └────────────────┬───────────────────────────┘
                      ┌────────────────▼───────────────────────────┐
底层引擎              │  org.dhatim:fastexcel / fastexcel-reader    │
                      │  ⚠️ StAX 隔离由 SonicStaxIsolation 兜底      │
                      └────────────────────────────────────────────┘
```

**一条铁律：元数据层对读写完全对称。** 同一个 `SheetMeta` 既驱动导出也驱动导入 ——
这是"下载的模板就是能上传的模板"能成立的前提，也是只需要一个 `@SonicTitle` 的原因。

---

## 4. 元数据模型

```java
public record ColumnMeta(
        String title,
        int order,                          // 最终解析出的绝对列序（从 0 起）
        String format,
        Class<?> javaType,
        Function<Object, Object> getter,    // LambdaMetafactory 生成
        BiConsumer<Object, Object> setter,  // POJO 用；record 为 null
        int componentIndex,                 // record canonical 构造器参数下标；POJO 为 -1
        SonicConverter<Object, Object> converter
) {}

public record SheetMeta(
        Class<?> type,
        List<ColumnMeta> columns,           // 已按 order 排序，不可变
        RowConstructor constructor          // sealed：PojoNoArg | RecordCanonical
) {}
```

**缓存用 `ClassValue<SheetMeta>`，不用 `ConcurrentHashMap<Class, SheetMeta>`。**
`ClassValue` 与类加载器生命周期绑定，类卸载时条目自动回收；后者持有强引用，是经典类加载器泄漏源。

### 4.1 列顺序规则

| 载体 | 规则 |
|---|---|
| `record` | 按 `getRecordComponents()` 顺序 —— **JLS 保证有序** |
| POJO | 按 `getDeclaredFields()` 顺序 —— **JLS 不保证**，javac 实践上是声明序 |
| 显式 `index` | **要么全不写，要么全写。** 出现"部分写"直接 `SonicExcelException` |
| 重复 index / 重复 title | 启动期报错，不静默覆盖 |

POJO 依赖 `getDeclaredFields()` 顺序是**已知的赌**。缓解：dev profile 下打印一次解析出的列顺序供肉眼核对。

### 4.2 基本类型的启动期拦截

`record` 的 canonical 构造器遇到缺失列时，包装类型得 `null`，而 **`int` / `boolean` 被强制赋 `0` / `false`**，
静默污染业务语义（"库存 0" 和 "这一列没填" 分不清）。**POJO 的 `private int status` 完全同理** —— 不只是 record 的问题。

**规则**：`MetaResolver` 解析时若发现 `@SonicTitle` 修饰在基本类型上：

- **dev / test profile：直接抛 `SonicExcelException`**（在 CI 阶段拦住）
- **prod：降级为显著的 `WARN`**（不阻断线上）

只打 warn 会被忽略，这就是为什么要分 profile 区别对待。

---

## 5. API 契约

### 5.1 注解 `@SonicTitle`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface SonicTitle {
    String value();                       // 表头名称（导出写出、导入匹配）
    String[] alias() default {};          // 导入兼容的历史表头名
    int index() default -1;
    String format() default "";           // "yyyy-MM-dd HH:mm:ss" / "#,##0.00"
    int width() default -1;               // 列宽（字符数），-1 表示自动估算
    boolean forceText() default false;    // 强制文本写出，见 §7.5
    Class<? extends SonicConverter> converter() default SonicConverter.None.class;
}
```

- **`@Target` 必须含 `RECORD_COMPONENT`**，否则解析端要绕道 `getDeclaringRecord().getDeclaredField(name)`。
- **`alias()`** 是刚需：中文表头改字是日常（`GoodsExcelVO` 里就躺着"商品状态错误"这个历史错别字）。
  没有 alias，改一次表头，用户手里所有旧模板全部导入失败。

### 5.2 转换器 `SonicConverter`

```java
public interface SonicConverter<J, E> {
    E exportConvert(J value, SonicContext ctx);
    J importConvert(E value, SonicContext ctx);
    final class None implements SonicConverter<Object, Object> { /* 恒等 */ }
}

// 不带整个 ColumnMeta：避免 converter ←→ meta 两个包互相依赖。
// element 是字段本身（Field / RecordComponent）——「带参转换器」靠它读自己的配置注解，
// 比如 @SonicDict("GOODS_PLACE")。转换器实例是按类缓存的单例，参数只能从这里来。
public record SonicContext(int rowIndex, int columnIndex, String title,
                           Class<?> javaType, AnnotatedElement element, Object rowObject) {}
```

### 5.3 转换器的实例化策略（**本设计最关键的一条**）

**阿里系与我们最实质的差异点。** EasyExcel 的 `Converter` 同样靠反射无参构造实例化，够不到 Spring 容器 ——
这就是为什么 [GoodsService.java:196](../smart-admin-api-java17-springboot3/sa-admin/src/main/java/net/lab1024/sa/admin/module/business/goods/service/GoodsService.java) 到今天还在手写字典翻译：

```java
.place(Arrays.stream(e.getPlace().split(","))
        .map(code -> dictService.getDictDataLabel("GOODS_PLACE", code))
        .collect(Collectors.joining(",")))
.goodsStatus(SmartEnumUtil.getEnumDescByValue(e.getGoodsStatus(), GoodsStatusEnum.class))
.categoryName(categoryQueryService.queryCategoryName(e.getCategoryId()))
```

**结论：转换器必须能是 Spring Bean。** `SonicConverterFactory.resolve()` 解析顺序：

```
1. 命中 ClassValue 缓存 → 直接返回
2. Spring 上下文可用 && 容器里有该类型 Bean → 取 Bean（单例）
3. 否则 → 反射调无参构造
4. 都失败 → SonicExcelException（启动期暴露，不等导出到一半才炸）
```

"无状态"降级为**文档约定**：转换器是单例、会被多线程并发调用，**不得持有可变字段**；
但**允许注入 Spring 单例依赖**（`DictService`、`CategoryQueryService` 这类本身线程安全的东西）。

✅ **D2 已定案：字典转换器放业务侧。**

- `sonicexcel/converter/builtin` **只放 JDK 级**的内置转换器：枚举、Y/N、BigDecimal 精度。这些不依赖任何业务组件。
- `SonicDictConverter` + `@SonicDict` 放业务包 **`net.lab1024.sa.base.module.support.dict.excel`**，
  与 `DictService` 同模块 —— 独立开源时框架目录不用拆，业务侧那份留在项目里。

### 5.4 访问器：`LambdaMetafactory` 主路径

原始规格要求"强制 `MethodHandle`"。方向对（避开 `setAccessible`），但**性能论证站不住**：
从 Map 里取出的 `MethodHandle` 只能走 `invoke`（非 `invokeExact`、非 `static final`），JIT 拿不到常量折叠，
实测通常**不比 `setAccessible` 后的 `Field` 快**。

**规范**：

1. getter → `Function<T,Object>`，setter → `BiConsumer<T,Object>`，均由 `LambdaMetafactory.metafactory` 生成，
   调用点退化为 `invokeinterface`，**JIT 可内联**。
   ⚠️ **LMF 只接受方法/构造器句柄，不接受字段句柄** —— 直接喂 `unreflectGetter` 会抛
   `LambdaConversionException: Unsupported MethodHandle kind: getField`（实现时实际踩到）。
   所以"没有 getter 的字段"必须退回 `MethodHandle`（`asType` 成 `(Object)Object` 后 `invokeExact`）。
   业务 DTO 基本都有 Lombok getter，走的是快路径；兜底路径同样要有测试覆盖。
2. 非 public 成员需要 **`MethodHandles.privateLookupIn(targetClass, lookup)`**，不能只用 `lookup()`。
3. **record 的 canonical 构造器做不成 `Function`**（参数个数不定），这条路径只能走
   `MethodHandle#invokeWithArguments` / `asSpreader`。"getter/setter 用 LMF、record 构造用 MH"必须分开写。
4. 每个访问器生成一个 hidden class，**必须靠 `ClassValue` 保证每个 DTO 只生成一次**，
   绝不能每次导出重建，否则 metaspace 会涨。

同时把"零反射性能损耗"这个提法删掉 —— 真实瓶颈是 XML 序列化 + Deflate + IO，属性访问占比 < 5%。
**这条路径的价值是"不用 `setAccessible`"，不是"快 10 倍"。**

### 5.5 门面

```java
public final class SonicExcel {
    public static <T> SonicSheetBuilder<T> write(OutputStream os, Class<T> head);

    /** 唯一的读入口：必须是落盘文件。原因见 §8.5 */
    public static <T> SonicSheetReader<T> read(Path file, Class<T> head);

    /**
     * 逃生口：仅限确定很小的场景（如从对象存储拉模板）。全量进堆，硬上限 5MB。
     * 刻意只给一次性读完的重载、不返回 Stream —— 临时文件的生命周期要和流绑定，
     * 徒增一处可能泄漏的地方，而这条路径本来就不该用于大文件。
     */
    public static <T> SonicReadResult<T> readBytes(byte[] content, Class<T> head);
}
```

### 5.6 写侧 API

```java
public final class SonicSheetBuilder<T> implements AutoCloseable {

    // ---- 配置（doWrite/append 之后调用抛 IllegalStateException） ----
    public SonicSheetBuilder<T> sheet(String name);
    public SonicSheetBuilder<T> flushEvery(int rows);        // 默认 1000
    public SonicSheetBuilder<T> maxRowsPerSheet(int rows);   // 默认 1_000_000
    public SonicSheetBuilder<T> freezeHeader(boolean on);    // 默认 true
    public SonicSheetBuilder<T> escapeFormula(boolean on);   // 默认 true，见 §9.2
    public SonicSheetBuilder<T> onError(SonicErrorPolicy p); // 默认 FailFast

    // 场景 A：千万级流式导出（配合数据库 Cursor / MyBatis ResultHandler）
    public void doWrite(Stream<? extends T> dataStream);
    // 场景 B：日常小数据量
    public void doWrite(Collection<? extends T> dataList);
    // 场景 C：分批追加，复用现有分页查询
    public SonicSheetBuilder<T> append(Collection<? extends T> dataList);

    @Override public void close();   // flush 残余 + finish() + 按需关流
}
```

### 5.7 读侧 API

```java
public final class SonicSheetReader<T> {
    public SonicSheetReader<T> sheet(int index);      // 默认 0
    public SonicSheetReader<T> sheet(String name);
    public SonicSheetReader<T> headerRow(int index);  // 默认 0
    public SonicSheetReader<T> onError(SonicErrorPolicy p); // 默认 Collect(200)
    public SonicSheetReader<T> maxRows(int rows);     // 默认 100_000

    /** 惰性流。必须 try-with-resources —— close 释放底层 zip 句柄 */
    public Stream<T> doRead();

    /** 一次性读完 + 行级错误清单。中小数据量（<10 万行）用这个 */
    public SonicReadResult<T> doReadAll();
}
```

`doRead()` 返回的 `Stream` **必须 `onClose()` 绑定 `ReadableWorkbook`**，javadoc 写死 try-with-resources。

标准用法（配合 Java 24 转正的 Gatherers）：

```java
try (Stream<GoodsImportForm> s = SonicExcel.read(tmp, GoodsImportForm.class).doRead()) {
    s.gather(Gatherers.windowFixed(1000)).forEach(goodsService::insertBatch);
}
```

**注意这里的事务边界**：循环在调用方手里，`@Transactional` 加在 `goodsService.insertBatch(List)` 上
就是**每批一个事务** —— 这是拉模型白送的，推模型（监听器）做不到，详见 §15。

---

## 6. 错误模型

原始规格的"转换异常 → warn 日志 + 跳过该行"**在导入侧是事故制造机**：
用户传 500 行，30 行被静默丢掉，界面回一句"成功导入 470 条"，没人知道丢了谁。导出侧更糟。

```java
public sealed interface SonicErrorPolicy {
    record FailFast() implements SonicErrorPolicy {}       // 立即抛（含行号列名）。导出默认
    record Collect(int maxErrors) implements SonicErrorPolicy {} // 收集续跑，超限熔断。导入默认 200
    record Skip() implements SonicErrorPolicy {}           // 静默跳过，只打汇总日志
}

public record SonicRowError(int rowIndex, String title, String rawValue, String message) {}

public record SonicReadResult<T>(List<T> data, List<SonicRowError> errors) {
    public boolean hasError() { return !errors.isEmpty(); }
}
```

三条硬性要求：

1. **默认值不对称是有意的**：导出 `FailFast`（数据不全等于错），导入 `Collect`（用户就是会传脏数据）。
2. **必须有 `maxErrors` 熔断**。千万行全脏时，一行一条 warn 能把磁盘写满。
3. **错误必须带行号 + 列标题 + 原始值**。只说"转换失败"等于没说。

v2 可加 `SonicExcel.writeErrorReport(errors)` —— 把错误清单导成 xlsx 回给用户。v1 先把数据结构留出来。

---

## 7. 写引擎实现要点

### 7.1 类型路由（switch 模式匹配）

```java
switch (value) {
    case null              -> { /* 留空，不写 */ }
    case String s          -> writeText(ws, r, c, s);   // ⚠️ 必须走 §7.6 的 inlineString
    case BigDecimal d      -> ws.value(r, c, d);
    case Number n          -> ws.value(r, c, n);
    case Boolean b         -> ws.value(r, c, b);
    case LocalDateTime dt  -> ws.value(r, c, dt);
    case LocalDate d       -> ws.value(r, c, d);
    case Date d            -> ws.value(r, c, d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    case Enum<?> e         -> writeText(ws, r, c, e.name());
    case Collection<?> col -> writeText(ws, r, c, col.stream().map(String::valueOf).collect(joining(",")));
    default                -> writeText(ws, r, c, String.valueOf(value));
}
```

**与旧库的一处刻意差异：值为 `null` 或空串时，SonicExcel 完全不写这个单元格**，
而 EasyExcel 会写一个空单元格占位。Excel 里"单元格不存在"和"单元格是空的"渲染完全一样，
但前者在千万行导出时能省下可观的 XML 体积。语义固化测试里对这一处做了补齐后比对。

### 7.2 flush 策略（**"防 OOM" 的开关之一**）

fastexcel 的 `Worksheet` 在 `flush()` 之前，所有 cell 攒在内存里 —— **不 flush 的流式写和一次性写占用一模一样**。

- 每写满 `flushEvery`（默认 1000）行调一次 `ws.flush()`。
- **flush 有顺序约束**：只能顺序向前，不能跳行，因此行号分配必须单线程。
- `close()` 时先 flush 残余，再 `wb.finish()`，最后按 `autoCloseStream` 决定是否关流
  （Web 导出**不能**关 `response.getOutputStream()`，沿用现有 `autoCloseStream(false)` 语义）。

### 7.3 自动滚 Sheet

xlsx 单表硬上限 **1,048,576 行**，"千万级导出"必须滚 Sheet，否则指标不成立。
达到 `maxRowsPerSheet`（默认 1,000,000）时新建 `名称_2`、`名称_3`…，每个新 Sheet 重写表头。

✅ **D4 已落地（第④档）**：`SonicExcel.writeCsv(...)`，见 §7.7。

### 7.4 列宽（第④档已落地为数据自适应）

fastexcel 只有 `ws.width(col, w)`，**没有 auto-size**，不处理的话中文列全是 `####`。

**硬约束：`<cols>` 在第一次 flush 时一次性写出，之后再调 `width()` 毫无效果。**
所以估算只能采样 **前 100 行**（必须远小于 `flushEvery` 默认的 1000），并在三个时机强制落定：
采样满 100 行时、每次 flush 之前、`finish()` 之前（行数不足 100 的小表走的是最后这条）。

- `@SonicTitle(width=12)` 显式指定 → **原样 12**，既不补余量也不钳制。
  用户明确指定的东西被框架偷偷改掉是最难排查的那类问题（实现时正是先写错、被测试抓出来的）。
- 未指定 → `max(表头宽, 前 100 行数据宽) + 2`，钳制在 8..60。ASCII 记 1，CJK 记 2。

### 7.5 数字被 Excel 转科学计数

`forceText=true` 显式声明 → 按文本写；否则**长度 > 15 的纯数字字符串自动按文本写**
（15 是 IEEE 754 双精度能精确表示的十进制位数，超过必然失真）。覆盖手机号、身份证、订单号、长 ID。

### 7.6 🔴 内存红线：String 一律走 `inlineString`

**实测事实**：`Workbook` 的公开 API 里**没有任何关闭 shared strings 的开关**；
`StringCache` 是 package-private 的 `HashMap<String, CachedString>`，**无条件启用、永不清理**。
只要调了 `ws.value(r, c, String)`，那个字符串就永久驻留堆中 ——
千万级高基数文本（订单号、地址、姓名）会直接把堆吃穿。

**但 `Worksheet` 有 `public void inlineString(int, int, String)`**，绕开 `StringCache`。

> **规范（红线，非优化项）：`CellWriter` 中所有 String 单元格一律走 `ws.inlineString(r, c, s)`，
> 禁止使用 `ws.value(r, c, String)`。**

代价是文件体积变大（重复文本不去重），换取**堆占用与数据量彻底解耦**。
落地要求：`CellWriter` 上写死注释说明原因；加单测 —— 导出 10 万条唯一字符串后，
断言产出包中**不存在 `xl/sharedStrings.xml`**。

---

### 7.7 CSV 通道（第④档已落地，原 D4）

`SonicExcel.writeCsv(os, Foo.class)`，与 xlsx 共用 `SheetMeta` 和转换器 —— **同一个 DTO 注解一份，两种格式都能导**。

**什么时候该用**：真·千万级。xlsx 光 Deflate 就是分钟级，生成的文件 Excel 打开还要几十秒，
而且有单表 1,048,576 行的硬上限逼着滚 Sheet。CSV 没有行数上限、几乎不耗 CPU。
代价是没有样式、没有多 sheet。

两个不写就要被投诉的细节：

- **UTF-8 BOM 默认写**。不写这三个字节，Excel 打开中文 CSV 就是乱码 —— CSV 导出被投诉最多的一件事。
  只有确认下游是程序而不是 Excel 时才该 `withBom(false)`。
- **BigDecimal 用 `toPlainString()`**。否则大数写成科学计数，下游再读解析不回原值。

## 8. 读引擎实现要点

### 8.1 表头动态寻址

读第 `headerRow`（默认 0）行，建 `Map<归一化表头, 实际列下标>`，与 `SheetMeta` 对齐。

- **归一化**：`trim` + 去全角空格 + 去 BOM(U+FEFF) + 去不间断空格(U+00A0)。
  这几个字符恰恰最常出现在用户从网页粘进 Excel 的文本里（见交接文档 §10 关于 `isBlank` 口径的记录）。
- 匹配顺序：`value()` 精确 → `alias()` 精确 → 缺失。
- **缺列**：POJO 留 null；record 走 canonical 构造器。**基本类型的语义污染由 §4.2 在启动期拦截。**
  导入 DTO **强制建议全用包装类型**。
- 用户模板多出来的列：忽略，不报错。

### 8.2 行 → 对象

| 载体 | 路径 |
|---|---|
| POJO | 无参构造 + `BiConsumer` setter 逐列注入 |
| record | 攒 `Object[]`，一次性调 canonical 构造器（`MethodHandle#invokeWithArguments`） |

### 8.3 `.xls` 上传的可诊断报错（**最高频工单**）

读端入口先探 magic number，**不能靠扩展名**（用户把 `.xls` 改名成 `.xlsx` 是常规操作）：

| 头字节 | 处置 |
|---|---|
| `50 4B 03 04`（`PK`） | xlsx，正常走 |
| `D0 CF 11 E0 A1 B1 1A E1`（OLE2） | 抛 `SonicExcelException("检测到旧版 .xls 格式，请用 Excel 另存为 .xlsx 后重新上传")` |
| 其他 | 抛 `SonicExcelException("文件不是有效的 Excel 文件")` |

**实现期踩到的坑：`Row#getCell(int)` 越界会直接抛 `IndexOutOfBoundsException`。**
而"最后几列全空、所以这一行根本没那么多单元格"是 xlsx 里最普通不过的形态 ——
写侧不写 null 单元格（§7.1），自己写出去的文件读回来就会撞上。
所有取单元格的地方必须先判 `position < row.getCellCount()`。已有往返测试固化。

### 8.4 空行与尾部脏数据

Excel 常带成千上万个"看起来是空的"行。读端必须**过滤全空行**，
否则"导入了 3 条却提示处理了 5000 行"。判空用 §8.1 的归一化口径，不是 `String#isBlank`。

### 8.5 读侧强制 `Path` 入参（**已定案，原 D6**）

**实测事实**：解析 OOXML 依赖 zip 的随机访问。给 `ReadableWorkbook(InputStream)` 时，
它内部用 `commons-compress` 的 **`SeekableInMemoryByteChannel`** —— **把整个 xlsx 读成堆里的 byte[]**（不落临时文件）。
上传 100MB 文件，读第一行之前先吃 100MB 连续堆内存，**百分百 OOM**。

**决议：`SonicExcel.read(...)` 只接受 `Path`，从 API 层面掐断"假流式"误用。**
`readBytes(byte[])` 作为逃生口，javadoc 写明"全量进堆，硬上限 5MB"。

**内存的诚实说明**：即便走 `Path`，`sharedStrings.xml` 仍是**全量载入内存的查找表**
（OOXML 的结构决定的，POI 的 SAX 模式同样如此）。导入内存下限 ≈ 文件中**不重复字符串总量**。
100 万行低基数文本没问题，100 万行全是唯一长文本会顶到几百 MB。
**这条要写进 README，不能宣称"绝对不 OOM"。**

---

### 8.6 导入模板下载（第④档已落地）

`SonicExcel.writeTemplate(os, Foo.class).sheet("导入模板").sample("张三", "在售").write()`

表头 + 可选示例行 + **下拉校验**。下拉是"防脏数据"最划算的一招 —— 用户根本填不出非法值，
比事后报错强得多。

选项来源是 `@SonicOptions`：字面量 `{"在售","售罄"}`，或 `provider = XxxProvider.class`
（实例解析规则同转换器：Spring Bean 优先、无参构造兜底）。
`SonicEnumOptionProvider` 复用字段上已有的 `@SonicEnum`，**枚举加一项模板下拉自动跟着变**，
不会出现"代码改了模板没改"的漂移。

两处必须防的坑，都已固化成测试：

- fastexcel 只能通过 `validateWithListByFormula` 写内联列表，选项里**不能含逗号和引号**，
  否则会把 `formula1` 撕开 —— 含这两个字符的选项直接跳过并告警。
- Excel 对内联下拉的 `formula1` 有 **255 字符上限**，超了整个文件会被判定损坏。
  超长时跳过下拉、保住文件可用，而不是产出一个打不开的模板。

**注意还没有对外的下载接口。** 加 `/goods/importTemplate` 这类端点要配权限和前端入口，
属于产品决策，不在本轮范围内；框架能力已经就绪，接上去只是几行 Controller。

### 8.7 解析异常的翻译（兼容性边界的兜底）

`WorkbookGuard`（§8.3）只能按**字节头**挡掉「根本不是 xlsx」的东西。
非 Excel 工具产出的 xlsx 是**合法 zip**，挡不住，会一路走进解析器再抛
`ExcelReaderException`（往往是一段 StAX 天书）。

既然产品决策是不兼容这类文件（§1.2），那至少要让用户知道该怎么办：
读引擎把所有 `ExcelReaderException` 统一翻译成

> Excel 文件解析失败。本系统只支持 Microsoft Excel 生成的标准 .xlsx，
> 如果这个文件来自其他表格软件，请先用 Excel 打开并另存为 .xlsx 后重试

⚠️ **翻译要包两层**：`doRead()` 里的 try 只覆盖打开工作簿和读表头；
**惰性流是在 try 块之外被消费的**，逐行解析时抛出的异常会绕过它 —— 所以迭代器那一层要再兜一次。

## 9. 依赖隔离与安全

### 9.1 🔴 拆除 aalto-xml 的 SPI 全局劫持

**风险已被实测证实，不是理论。** 扫描 sa-admin 全部 **214 个 runtime jar**，
引用 `javax/xml/stream/XMLInputFactory` 的有 8 个：

```
spring-core-7.0.8          spring-web-7.0.8
hibernate-validator-9.1.0  mysql-connector-j-26.7.0
aws-query-protocol-2.50.2  ← S3 客户端解析响应/错误走的就是它
tika-core-3.3.2
poi-5.5.1  xmlbeans-5.3.0  ← 本轮删除
```

**摘掉 POI 后仍有 6 个库会走 SPI 查找。** `aalto-xml` 一旦进 classpath，
`XMLInputFactory.newFactory()` 全局返回 Aalto，这 6 个库的 XML 行为都可能漂移。

**同时（§2.4 事实 2）：`fastexcel-reader` 通过 `DefaultXMLInputFactory` 直接 `new InputFactoryImpl()`，
不走 SPI —— 所以拦截 SPI 不影响它自己，可以放心做。**

**决议**：把 JDK 标准实现写死到系统属性。**注意 `XMLEventFactory` 的实现类在 `.events.` 子包** ——
这一处极易写错，且**不会在启动时暴露**，要等某组件首次使用 `XMLEventFactory` 才抛
`FactoryConfigurationError`（典型的"上线三天后随机报错"）。JDK 25 实测确认：

```java
System.setProperty("javax.xml.stream.XMLInputFactory",  "com.sun.xml.internal.stream.XMLInputFactoryImpl");
System.setProperty("javax.xml.stream.XMLOutputFactory", "com.sun.xml.internal.stream.XMLOutputFactoryImpl");
System.setProperty("javax.xml.stream.XMLEventFactory",  "com.sun.xml.internal.stream.events.XMLEventFactoryImpl");
//                                                       注意这里 ↑ 多一层 .events
```

**落地方式（不要写在 `main()` 第一行）**：

1. **主路径走 JVM 参数** `-Djavax.xml.stream.XMLInputFactory=...`（运维可见、可摘、可回滚）。
   `main()` 里的全局副作用藏在业务启动类中，将来没人找得到，且 Spring Boot 的 `main`
   不保证是最先执行的代码（Agent、静态初始化都可能更早）。
2. **代码兜底**放 `SonicStaxIsolation` 的 `@AutoConfiguration` 静态块。
3. **启动自检**：`log.info` 打印三个 factory 的实际实现类名。
4. **加断言测试**：将来谁引入了新的 StAX 实现，CI 直接红。

### 9.2 其余安全项

| 风险 | 处置 |
|---|---|
| **XXE** | ✅ **已由上游处理**：`fastexcel-reader` 自带的 `DefaultXMLInputFactory` 就显式设了 `SUPPORT_DTD=false`、`IS_SUPPORTING_EXTERNAL_ENTITIES=false`。我们不重复实现，只加一条回归测试当哨兵 —— 哪天升级把这个行为改没了要立刻知道 |
| **Zip bomb** | 限制解压总字节数（默认 200MB）、zip 条目数（默认 100）、单条目大小 |
| **行数炸弹** | `maxRows` 默认 10 万，Web 上传口径可再收紧 |
| **公式注入** | 提供 `escapeFormula(boolean)`，以 `=` `+` `-` `@` `\t` `\r` 开头的文本前置 `'`。**默认关闭，见下方说明** |
| **单元格长度** | 上限 32767 字符，超长截断并计一条 `SonicRowError` |
| 上传文件大小 | 由 Spring `multipart.max-file-size` 兜底，框架层不重复限制 |

**🔁 公式转义默认值在实现期改了（v2 原文是"默认开启"）。** 理由是 §7.6 定案之后前提变了：
所有文本都写成 `inlineStr`，而 **Excel 对文本型单元格根本不做公式求值** —— xlsx 内的注入面几乎不存在，
真正的风险只剩"用户另存为 CSV 再打开"。而默认开启的代价是确定发生的数据污染：
`+8613800000000` 这类合法手机号会被改成 `'+8613800000000` 写进文件。
**用确定的数据污染换几乎不存在的收益，不划算**，因此默认关闭、保留开关。已有测试固化两种行为。

---

## 10. Web 适配层：`SmartExcelUtil` 升级为 HTTP 协议防腐层

框架只认 `OutputStream` / `Path`；HTTP 相关的脏活全归 `SmartExcelUtil`。

### 10.1 职责

1. **统管中文文件名 URL 编码**与 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 头下发。
2. **`MultipartFile` → 临时文件**的完整生命周期（§10.2）。
3. **探活与异常阻断**（§10.3）。
4. **屏蔽网络波动报错**（§10.4）。

### 10.2 临时文件生命周期（K8s 环境）

```java
Path tmp = Files.createTempFile("sonic-", ".xlsx");
try {
    file.transferTo(tmp);
    // ... 读取
} finally {
    Files.deleteIfExists(tmp);
}
```

**❌ 明确禁止 `File#deleteOnExit()`。** 它是本方案里唯一会**主动制造问题**的写法：

- `DeleteOnExitHook` 把文件名注册进一个 **static `LinkedHashSet<String>`，永久持有**，只在 JVM **正常退出**时执行。
- K8s 里 Pod 被 `SIGKILL` / OOMKilled 时钩子根本不跑 —— **它想兜的底恰恰兜不住**。
- 运行期间随每次上传持续增长，是长跑 JVM 的经典内存泄漏点。

**正确的兜底是「finally 删 + 启动扫」**：`finally` 负责正常路径；
**应用启动时扫一遍专用临时目录，删掉 mtime 超过 N 小时（建议 2h）的残留**，这才真正覆盖 crash 场景。

**不允许将临时文件句柄泄漏给异步线程。**

**部署规范（给运维）**：

- 挂 `emptyDir` + `sizeLimit` 到专用目录，`-Djava.io.tmpdir` 指过去。
  写满时是干净的 `IOException`，而不是 Pod 被驱逐。
- `ephemeral-storage` 的 **`requests` 和 `limits` 都要设**。只设 limit 的后果是 **Pod 被 Evicted**，比报错更糟。
- ⚠️ **绝不能配 `emptyDir.medium: Memory`** —— 那是 tmpfs 走内存，正好把落盘的意义全抵消掉。

### 10.3 探活与异常阻断

**在写入任何 HTTP Header 之前，必须先校验数据源**（尝试拉取第一批数据）。首批失败直接回写 JSON 错误。

一旦开始写 `response.getOutputStream()`，响应头已 committed，**再也返回不了 JSON**
（commit 后 `sendError` 会抛 `IllegalStateException`）。所以：

- **小数据量（< 5 万行）**：先写 `ByteArrayOutputStream`，成功后再设响应头 + copy。异常可正常返回 JSON。
- **大数据量**：只能流式。推流中途发生致命异常时，**异常直接往外抛，不要 catch 后正常 `return`** ——
  响应走 chunked（不设 `Content-Length`），容器不写结束 chunk，客户端自然拿到网络错误。
  Servlet API 里**没有可移植的"主动 Connection Reset"手段**，这是唯一可靠的做法。

**天然保险（可写进用户文档降低焦虑）**：xlsx 的 zip 中央目录是 `finish()` 最后才写的。
中途挂掉产出的字节流**根本没有中央目录，Excel / WPS 打开必然报"文件已损坏"** ——
不存在"用户打开是空表却以为导出成功"的情况。

### 10.4 屏蔽 `ClientAbortException`

用户点"取消下载"会刷屏异常日志。降级为 DEBUG。三条约束：

1. 它是 **Tomcat 私有类，别硬 `import`**（将来换 Undertow 编译不过），按类名或 `IOException` 断开特征判断。
2. **吞异常绝不能吞资源清理** —— `finally` 里的 workbook close、临时文件删除照跑。
3. 判断收在**全局异常处理器**里，别每个导出方法抄一遍。

---

## 11. Java 版本特性：用了什么、有意没用什么

| 特性 | 用途 | 状态 |
|---|---|---|
| **Stream Gatherers** `windowFixed` | 分批入库，替掉整套攒批样板 | **正式（24）· 唯一真正的"新版本红利"** |
| `record` | DTO + 元数据模型。**阿里系读侧靠 setter 注入，record 用不了** | 正式（16）· 能力差距 |
| `switch` 模式匹配 + `sealed` | 单元格类型路由（§7.1）、`SonicErrorPolicy` | 正式（21/17）· 真实但温和 |
| `SequencedCollection#getFirst` | 列序、错误清单取首条 | 正式（21） |
| `LambdaMetafactory` / `ClassValue` | 访问器、元数据缓存 | **与 Java 25 无关**，只是用对了 |
| ~~虚拟线程~~ | 瓶颈是单线程 XML + Deflate | ❌ 排除，别写进宣传语 |
| ~~`ScopedValue` / 结构化并发 / `StableValue`~~ | 无场景 / 25 里仍是 preview | ❌ 排除 |

**关于"多线程并发流写"**：fastexcel 支持的是**不同 Worksheet 各跑一个线程**，
不是同一 Sheet 多线程写（flush 有顺序约束，§7.2）。v1 **不开放**并行写，
文档只注明"多 Sheet 场景可由调用方各起一个 builder"。把没验证过的并发模型写进规格书是给后人埋雷。

---

## 12. 落地分档（每档可独立回滚，沿用 hutool 那轮的节奏）

| 档 | 内容 | 可回滚点 | 依赖变化 |
|---|---|---|---|
| **①** ✅ **已完成 2026-08-08** | `SonicStaxIsolation` + 元数据层 + 写引擎（含 inline strings 红线、flush、滚 Sheet）；**2 个导出 VO 平移**；`SmartExcelUtil#exportExcel` 切换成"先攒 byte[] 再落头"；**删除水印全部代码**，`EnterpriseController` 改调普通导出。35 条测试全绿 | 注解层保留 EasyExcel 可并行 | 引入 dhatim writer，POI 暂留 |
| **②** ✅ **已完成 2026-08-08** | 读引擎（`Path` 入参 + 入口体检 + 临时文件闭环）+ 转换器 Spring 解析落地；`GoodsImportForm` **改成 record**；`GoodsService#importGoods` 改造成带行级错误回显；`getAllGoods` 的三处翻译全部收进 converter。累计 100 条测试全绿 | 导入可临时切回旧实现 | 引入 dhatim reader（带 aalto，StAX 隔离此时开始真正起作用） |
| **③** ✅ **已完成 2026-08-08** | 摘掉 `cn.idev.excel:fastexcel` + `poi` + `poi-ooxml`（连带 xmlbeans / ehcache / JAXB / commons-math3 等 15 个 jar）；测试回读改用 fastexcel-reader，迁移语义测试退化为固定快照 | git revert 单 commit | **实测 −18.66 MB** |
| **④** ✅ **已完成 2026-08-08** | 列宽按数据自适应、CSV 通道、错误报告导出、导入模板 + 下拉校验。累计 215 条测试全绿 | 纯新增 | 无 |

每档一个 commit，message 沿用现有风格（`SonicExcel 第①档：…（−xMB）`）。

---

## 13. 测试矩阵

| 类别 | 用例 |
|---|---|
| **语义固化**（对齐 hutool 那轮） | 迁移前后，同一份数据用旧 EasyExcel 与 SonicExcel 各导一次，**逐单元格比对文本** |
| **内存红线** | 导出 10 万条唯一字符串后，断言产出包中**不存在 `xl/sharedStrings.xml`**（§7.6） |
| **StAX 隔离** | 断言三个 factory 的实现类是 JDK 内置；断言 classpath 上没有新增的 StAX 实现（§9.1） |
| 类型 | String / Integer / Long / BigDecimal(精度) / Boolean / LocalDate(Time) / Date / Enum / null / 空串 |
| 边界 | 0 行、只有表头、1 行、100 万行滚 Sheet、单元格 32767+ 字符、超长纯数字 |
| 表头 | 列顺序打乱、多余列、缺列、表头带空格/BOM/U+00A0、alias 命中、重复表头 |
| **兼容性** | `.xls` 改名成 `.xlsx`、合法 zip 但不是 xlsx、加密文件、空文件、非 Excel 文件 —— 一律要给出**用户能照着做**的报错。<br>**不测 WPS / Numbers 产出的文件**：已明确不兼容（§1.2） |
| 错误模型 | FailFast 抛出且带行号、Collect 到 maxErrors 熔断、Skip 只出汇总日志 |
| 资源 | `doRead()` 未 close 的句柄泄漏检测；导出异常路径下的临时文件清理；启动扫残留 |
| 安全 | XXE payload、zip bomb、公式注入文本 |
| 性能 | §1.3 四条指标，JFR 记录堆峰值 |

**兼容性那一行的口径变了**：POI 的价值有很大一部分是"什么破文件都能读"，换成 fastexcel 后这块保护没了。
产品决策是**不去追这个兼容性**，所以测试目标从"能读进来"降级为"读不了的时候话说清楚"。

---

## 14. 决策清单

| # | 问题 | 状态 |
|---|---|---|
| ~~D1~~ | ~~包路径~~ | **已定：`net.lab1024.sa.base.sonicexcel`，与 `common` 平级**（§2.3） |
| ~~D2~~ | ~~字典转换器放哪~~ | **已定：业务侧 `support.dict.excel`；框架 builtin 只留 JDK 级**（§5.3） |
| ~~D3~~ | ~~水印删除后的审计线索~~ | **已定：靠现有 `@OperateLog` 承接，零改动**（§1.2） |
| ~~D10~~ | ~~字典转换器的导入方向~~ | **已落地 2026-08-08**：新增 `DICT_DATA_LABEL` 独立缓存 + `DictManager#listDictDataByLabel`，见 §16 |
| ~~D4~~ | ~~CSV 导出通道~~ | **已落地（第④档）**，见 §7.7 |
| ~~D5~~ | ~~sharedStrings vs inline~~ | **已定：强制 inline，且底层无开关，走 `inlineString()`**（§7.6） |
| ~~D6~~ | ~~reader 是否落临时文件~~ | **已定：不落盘、整个进堆 → 读侧强制 `Path` 入参**（§8.5） |
| ~~D7~~ | ~~导出中途异常的响应契约~~ | **已定：小数据先缓冲、大数据流式抛异常**（§10.3） |
| ~~D8~~ | ~~导入 DTO 是否强制包装类型~~ | **已定：dev/test 抛异常、prod warn**（§4.2） |
| ~~D9~~ | ~~模板填充要不要做~~ | **已定：不做，票据类走 PDF**（§1.2） |

---

## 15. 附录：相对阿里系（EasyExcel / cn.idev.excel）的收益评估

> **红利的 80% 来自「拉模型取代推模型」，只有 20% 来自新语言特性。**
> 读侧是架构层面的碾压，写侧只是小胜。

### 15.1 为什么阿里系必须有监听器

EasyExcel 底层是 **POI 的 SAX 推模型** —— 解析器解到一行回调一次，控制权在解析器手里。
**在推模型上，`AnalysisEventListener` 不是选择，是唯一解。**

`fastexcel-reader` 底层是 **StAX + Spliterator 的拉模型**（`RowSpliterator`），控制权在调用方手里。
**在拉模型上，`Stream<T>` 也不是我们的巧思，是自然结果。**

**这场比较的实质是：我们换了个引擎，白捡了一个更好的编程模型。不是我们比阿里的人聪明。**

### 15.2 同一个需求的代码对比

需求：导入商品，每 1000 条入库一次，脏行不中断、要能告诉用户第几行错了。

**阿里系**（约 40 行监听器 + 4 行调用）：

```java
public class GoodsImportListener extends AnalysisEventListener<GoodsImportForm> {
    private static final int BATCH_COUNT = 1000;
    private List<GoodsImportForm> cached = new ArrayList<>(BATCH_COUNT);
    private final List<String> errors = new ArrayList<>();
    private final GoodsDao goodsDao;                       // ① 只能构造器传

    public GoodsImportListener(GoodsDao dao) { this.goodsDao = dao; }

    @Override public void invoke(GoodsImportForm d, AnalysisContext c) {
        cached.add(d);
        if (cached.size() >= BATCH_COUNT) { save(); cached = new ArrayList<>(BATCH_COUNT); } // ② 忘清空=泄漏
    }
    @Override public void doAfterAllAnalysed(AnalysisContext c) { save(); }                  // ③ 忘了=丢最后一批
    @Override public void onException(Exception e, AnalysisContext c) throws Exception {
        if (e instanceof ExcelDataConvertException ex) {
            errors.add("第 " + (ex.getRowIndex()+1) + " 行第 " + (ex.getColumnIndex()+1) + " 列解析失败");
            return;                                                                          // ④ 忘 return=中断
        }
        throw e;
    }
    private void save() { goodsDao.insertBatch(cached); }                                    // ⑤ 事务加不上
    public List<String> getErrors() { return errors; }
}

// 调用方
var listener = new GoodsImportListener(goodsDao);          // ① 每次都必须 new，不能是 Bean
FastExcel.read(file.getInputStream(), GoodsImportForm.class, listener).sheet().doRead();
if (!listener.getErrors().isEmpty()) { ... }               // ⑥ 结果只能从字段里捞
```

**SonicExcel**（3 行）：

```java
Path tmp = SmartExcelUtil.toTempFile(file);
try (Stream<GoodsImportForm> rows = SonicExcel.read(tmp, GoodsImportForm.class).doRead()) {
    rows.gather(Gatherers.windowFixed(1000)).forEach(goodsService::insertBatch);
}
```

| 坑 | 为什么不存在了 |
|---|---|
| ① 监听器不能被 Spring 管理，必须每次 new | **没有监听器对象**，无状态可言 |
| ② 攒批后忘了清空 list | `windowFixed` 是 JDK 实现的 |
| ③ 忘了 `doAfterAllAnalysed` 补最后一批 | `windowFixed` 自动吐出最后一个不满的窗口 |
| ④ `onException` 忘 return 导致整体中断 | 策略是 `SonicErrorPolicy` **数据**，不是要你写对的控制流 |
| ⑥ 结果只能从字段里捞 | `doReadAll()` 直接返回 `SonicReadResult<T>` |

### 15.3 最值钱的一条：事务边界

**推模型的结构性缺陷。** `save()` 在 listener 内部，而 listener 不是 Spring Bean，
**`@Transactional` 加上去不生效**（没有代理）。真实项目里只有三种收场：

1. 把 dao 操作包成 Service Bean 传进 listener —— 多一层类，新人不知道为什么要这么绕；
2. 在外层 `doRead()` 上套 `@Transactional` —— **100 万行一个事务**，undo log 撑爆、锁全表；
3. 干脆不要事务 —— 中途失败，库里躺着一半数据。

**SonicExcel**：循环在调用方手里，`@Transactional` 加在 `insertBatch(List)` 上就是**每批一个事务**，
天生正确，不需要任何人知道任何技巧。

> 拉模型把事务边界还给了业务代码。**这是本次自研最大的单项收益，比省下的 19MB 更重要。**

### 15.4 组合性

| 需求 | 阿里系 | SonicExcel |
|---|---|---|
| 只预览前 100 行 | 重写 `hasNext()` 返回 false | `.limit(100)` |
| 只导入启用状态的行 | 在 `invoke` 里写 `if` | `.filter(...)` |
| 边导入边统计 | 再加字段自己累加 | `.peek(...)` / `Collectors.teeing` |
| 单测"读 5 行验证解析" | 驱动完整 `doRead` + 假 listener | 断言 `Stream` 前 5 个元素 |

### 15.5 红利账单

| 维度 | 阿里系现状 | SonicExcel | 差值 |
|---|---|---|---|
| 依赖体积 | 19.4 MB / 15 个 jar | 0.74 MB / 5 个 jar | **实测 −18.66 MB** |
| CVE 面 | POI + xmlbeans + ehcache + JAXB | opczip + aalto + stax2 | 大幅收窄 |
| 一次导入的业务代码 | ~44 行 | ~3 行 | **−93%** |
| 专有概念数 | `AnalysisEventListener` / `AnalysisContext` / `ReadRowHolder` / `ExcelDataConvertException` / `BATCH_COUNT` / `doAfterAllAnalysed` / `WriteHandler` = **7 个** | `Stream` + `Gatherers` = **0 个**（JDK 常识） | 心智负担归零 |
| "忘了就出事"的坑 | 5 处 | 0 处 | 结构性消除 |
| 每批事务 | 三种收场都不体面 | 默认正确 | **最大收益** |
| record 支持 | 读侧靠 setter 注入，用不了 | 一等公民 | 能力差距 |
| 字典翻译 | 转换器同样够不到 Spring | 收进 converter | **唯一主动多做的设计** |

### 15.6 代价（必须写进决策记录）

| 放弃 | 说明 |
|---|---|
| **`WriteHandler` 扩展点体系** | EasyExcel 的万能后门（现有水印就是用它做的）。我们换来小 API，代价是**没有后门**，意料外的需求只能改框架本身 |
| ~~模板填充（`fill`）~~ | **已按产品决策关闭**：中台无票据打印需求，真需要走 PDF |
| 多级/合并表头、复杂样式策略 | 中后台迟早有人提 |
| `.xls` 支持 | 已列 Non-Goal，靠明确报错兜 |
| **破损文件兼容性** | POI 真正的护城河。**已按产品决策放弃**：只支持 Excel 产出的标准 xlsx，其余给明确报错（§8.7） |
| 生态与人力 | EasyExcel 的坑全网有答案；SonicExcel 的坑只有我们自己有答案。**新人上手快了（API 小），排障慢了（没得搜）** |

### 15.7 总评

| 面 | 判定 |
|---|---|
| **读侧** | **压倒性胜利，架构级的**。事务边界、组合性、无状态，是阿里系在 POI SAX 上做不到的事 |
| **写侧** | **小胜**。EasyExcel 的 `.sheet().doWrite(list)` 本就简洁，我们赢在内存确定性和依赖体积，**不在优雅** |
| **依赖** | **大胜**，实测 −18.66 MB（214 个 jar → 204 个），POI 全家桶连根拔起 |
| **扩展性** | **净输**。放弃 `WriteHandler` 万能后门，换来小而清晰的 API |
| **风险** | 兼容性与生态是长期负债，用测试矩阵 + 门面层（底层可换）对冲 |

**值得做，但理由要摆正**：不是"自研的更优雅"，而是
①摘掉 POI 省 18.66MB 和一整片 CVE 面，②拉模型顺手解决了事务边界这个真问题，③现存使用面只有 3 处、迁移成本近乎为零。

模板填充这个唯一可能翻盘的因素，已按产品决策关闭（§1.2）。

---

## 16. 字典反查（D10 落地记录）

字典配在库里，导入不能反查等于半个功能 —— 本节记录实现时的三个非显然决策。

**① 为什么是独立缓存 `DICT_DATA_LABEL`，不是复用 `DICT_DATA` 加前缀。**
复用时 key 形如 `CODE_L_xxx`，而 `dataValue` 本身完全可能就等于 `L_xxx`，两种 key 会撞在一起。

**② 为什么 `listDictDataByLabel` 返回 List 而不是单个。**
两个原因：

- 同一字典下标签**没有唯一约束**，理论上会重复。撞了必须让调用方知道 ——
  映射有歧义还硬挑一个，就是往库里写脏数据。`DictService#getDictDataValueByLabel` 遇到多条直接抛。
- 缓存配置是 `disableCachingNullValues`，返回 null 会在写缓存时抛异常。
  而"标签查不到"在导入场景里是**常态**（用户填错字），返回空 List 既能正常缓存、
  又不会把这条路变成异常路径。

**③ 顺带修正了缓存失效的三处漏洞** —— 不修的话反查缓存会让"字典改了、导入还按旧配置映射"，
比原来的正查缓存后果更严重（写进库的是错的码值）：

| 位置 | 原来 | 现在 |
|---|---|---|
| `update` / `batchDelete` / `delete`（字典级） | `@CacheEvict(DICT_DATA)` 不带 key 也不带 allEntries，用的是默认 key（dictId / form 对象），**永远匹配不上 `CODE_value` 形状的 key，等于没清** | 两个缓存都 `allEntries = true`。字典级改动是低频管理操作，全清最省心 |
| `updateDictData` | 只按 `(新 code, 新 value)` 清正查缓存；**改标签时旧标签的缓存留在原地** | 改成更新**前**按 id 反查出旧的 code/value/label 一起清 |
| `clearDictDataCache` | 只清正查 | 正查 + 反查一起清 |

## 附：与初版规格书的差异汇总

| 初版 | 本版 | 原因 |
|---|---|---|
| "彻底抛弃 POI，基于 fastexcel" | 明确两个 GAV：移除 `cn.idev.excel:fastexcel`，引入 `org.dhatim:fastexcel` | 两者毫无关系，混写会误导实现者 |
| "零反射性能损耗"，强制 `MethodHandle` | `LambdaMetafactory` 主路径 + 三条实现约束；不宣称零损耗 | Map 里取出的 MH 走 `invoke` 并不快；瓶颈也不在这（§5.4） |
| "完美支持千万级导出" | 补 flush 策略 + 自动滚 Sheet + **inline strings 红线** | xlsx 单表上限 1,048,576 行；StringCache 无法关闭（§7.2 / §7.3 / §7.6） |
| "多线程并发流写" | 收紧为"多 Sheet 可各起 builder"，v1 不开放并行 | fastexcel 只支持跨 Worksheet 并行（§11） |
| 转换器"绝对无状态" | 允许注入 Spring 单例；无状态降为文档约定 | 否则字典翻译够不到 `DictService`（§5.3） |
| 出错"warn 日志 + 跳过该行" | 三态策略 + 行级错误收集 + maxErrors 熔断 | 静默丢数据是事故；每行一条日志会写满磁盘（§6） |
| `Stream<?> doRead()` | `Stream<T>`，`onClose` 绑定底层资源 | 泛型 + 句柄泄漏（§5.5 / §5.7） |
| `read(InputStream, ...)` | **`read(Path, ...)`** | InputStream 路径整个文件进堆，必 OOM（§8.5） |
| `ConcurrentHashMap<Class,...>` 缓存 | `ClassValue` | 前者是类加载器泄漏源（§4） |
| `@Target(FIELD)` | `@Target({FIELD, RECORD_COMPONENT, METHOD})` | 主打 record 就要一次写对（§5.1） |
| 未提 SPI 冲突 | §9.1 完整隔离方案 + 实测证据 + **`.events.` 类名修正** | aalto 劫持 JVM 全局 StAX，影响 6 个库含 S3 |
| 未提临时文件 | §10.2 完整生命周期 + **明确禁止 `deleteOnExit()`** | 它是内存泄漏且在 K8s SIGKILL 下不执行 |
| 水印列为 Non-Goal，理由"引擎不支持" | 理由改为**产品决策** | 0.20.2 的 `addImage` 是存在的，理由写错会误导后人 |
| 收益估 13.5MB | **实测 −18.66MB**；commons-compress / commons-codec / cache-api 三个删不掉 | classpath 差集逐 jar 实测（§2.2） |
| 未提 | `alias()`、`.xls` 探测、空行过滤、列宽、科学计数、基本类型拦截、Web 异常契约、阿里系对比 | 全是线上工单高发区（§5.1 / §7 / §8 / §10 / §15） |
