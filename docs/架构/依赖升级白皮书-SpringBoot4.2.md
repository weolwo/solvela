# 依赖升级白皮书 · Spring Boot 4.1.0 → 4.2.0 与两端前端

> 日期：2026-11-20
> 分支：`upgrade/springboot4`。提交：`7eb65d50`（后端）· `cf0da29f`（C 端）· `af959ce9`（管理端），以及修正 §2.2 选错 starter 并补本文的提交
> 状态：已推送，待合并
> 验证状态：`clean` 后全量编译通过 · 后端全部可离线运行的单测通过 · C 端 `npm run ci`（格式 / lint / 类型检查 / 313 个用例 / 构建）通过 · 管理端 `npm run ci`（176 个用例 / 构建）通过 · 两端 dev server 在浏览器里实开过页面
> **未验证**：依赖 MySQL / Redis / RabbitMQ 的集成测试（admin 174 个、app-biz 263 个、app 134 个）本次环境起不来，见 §4.2
> 前置阅读：`docs/架构/后端升级白皮书-SpringBoot4.md`（3.5 → 4.1 那一次，Jackson 3、Redisson 4 空密码等坑都在那里）

---

## 0. 一句话结论

这次是小版本升级，代码改动只有两处，但**两处都是「pom 不报错、类却没了」**：
Spring Boot 4.2 把经典 RabbitMQ 从 `spring-boot-starter-amqp` 里拆了出去（§2.2），Tika 4 把 `TikaConfig` 移出了 tika-core（§2.3）。
而它们一开始都被**增量编译盖住了** —— 改完版本号 `mvn compile` 照样通过，`clean` 之后才现形（§2.1）。
AMQP 那一处**第一次还修错了**：选了一个名字很像、编译也能过的 starter，直到写本文核对配置前缀时才发现（§2.2）。

最值得记住的三条：

1. **升依赖之后的第一次构建必须 `clean`。** 源码没变时 maven-compiler 可能根本不重编，`target/` 里是旧依赖编出来的产物，「编译通过」毫无意义。
2. **换了实现就做差异测试。** Tika 的探测入口换了，用 119 个真实文件让新旧实现逐条比对，零差异才算数（§2.3）。
3. **跨端的加密契约要有两边共用的黄金向量。** `sm-crypto` 管着登录密码的 SM4，升级后前端测试直接比对后端那串值（§2.7）。
4. **换 starter 要验到「装没装上」，编译通过不算。** 选错的那个 starter 编译、单测全绿，启动才会失败（§2.2）。

---

## 1. 版本变更总表

下面三张表由脚本从 `git show f05d1aeb:` 与 `af959ce9:` 的 `pom.xml` / `package.json` 比对生成，不是手抄的。

### 1.1 后端（`solvela-api/pom.xml`）

| 属性 / 插件 | 升级前 | 升级后 | 备注 |
|---|---|---|---|
| springboot | 4.1.0 | **4.2.0** | 随 BOM 带上 Spring Framework 7.0.8 → 7.1.0、Jackson 3.1.4 → 3.1.8、Tomcat 11.0.22 → 11.0.27、Spring AMQP 4.1 → 4.2、JUnit 6.0.3 → 6.1.3 |
| spring-security-crypto | 7.1.0 | 7.2.0 | 与 4.2 BOM 的 spring-security 对齐 |
| mybatis-plus | 3.5.17 | 3.5.18 | |
| redisson | 4.6.1 | 4.8.0 | |
| mysql-connector-j | 26.7.0 | 26.10.0 | 与 4.2 BOM 对齐 |
| tika | 3.3.2 | **4.2.0** | 大版本，见 §2.3 |
| knife4j-baizhukui | 5.2.1 | 5.8.3 | |
| awssdk-s3 | 2.50.2 | 2.59.5 | |
| bcprov | 1.85 | 1.87 | |
| commons-lang3 | 3.20.0 | 3.22.0 | |
| commons-codec | 1.22.1 | 1.23.0 | |
| httpcomponents | 5.6.3 | 5.6.5 | |
| snakeyaml | 2.6 | 2.7 | |
| freemarker | 2.3.34 | 2.3.35 | |
| jsoup | 1.23.1 | 1.24.1 | |
| qlexpress | 4.1.3 | 4.1.4 | |
| ip2region | 3.3.7 | 3.3.8 | |
| lombok | 1.18.46 | 1.18.48 | |
| therapi | 0.15.0 | 0.16.0 | |
| maven-compiler-plugin | 3.13.0 | 3.16.0 | |
| maven-surefire-plugin | 未声明，随本机 Maven（3.9.16 自带 3.5.4） | **3.6.1** | 新增显式锁定，见 §2.6 |

未变的：p6spy 3.9.1、reflections 0.10.2、commons-io 2.22.0、commons-compress 1.28.0、dhatim fastexcel 0.20.2 —— 它们已是最新稳定版。

**核对过实际解析出的版本**：`mvn compile dependency:list` 全 reactor 跑一遍，上表每个库都只解析出一个版本，且就是目标版本，没有被别的依赖按「同深度先声明者胜」拽回旧版。

### 1.2 C 端（`solvela-app-web/package.json`，精确版本）

| 包 | 升级前 | 升级后 | |
|---|---|---|---|
| vitest | 4.1.11 | 5.1.1 | 大版本 |
| jsdom | 29.0.0 | 31.0.1 | 大版本 |
| typescript | 6.0.3 | 6.0.3 | **想升 7.0.2，退回了**，见 §2.4 |
| vue | 3.5.42 | 3.5.46 | |
| vue-router | 5.3.0 | 5.5.1 | |
| pinia | 4.0.3 | 4.1.0 | |
| axios | 1.20.0 | 1.21.1 | |
| vite | 8.2.2 | 8.3.8 | |
| @vitejs/plugin-vue | 6.0.8 | 6.0.11 | |
| @vue/test-utils | 2.5.0 | 2.6.0 | |
| eslint | 10.9.1 | 10.15.0 | |
| eslint-plugin-vue | 10.10.0 | 10.12.1 | |
| typescript-eslint | 8.68.0 | 8.73.0 | |
| vue-eslint-parser | 10.4.1 | 10.4.2 | |
| vue-tsc | 3.3.11 | 3.3.14 | |
| prettier | 3.9.6 | 3.10.1 | 见 §2.5 |
| globals | 17.11.0 | 17.14.0 | |
| @types/node | 26.4.0 | 26.6.6 | |

### 1.3 管理端（`solvela-admin-web/package.json`，保留原有 `^` / `~` 写法）

| 包 | 升级前 | 升级后 | |
|---|---|---|---|
| vitest | ^4.1.11 | ^5.1.1 | 大版本 |
| jsdom | ^29.0.0 | ^31.0.1 | 大版本 |
| vue | 3.5.40 | 3.5.46 | |
| vue-router | 5.2.0 | 5.5.1 | |
| vue-i18n | 11.4.8 | 11.4.23 | |
| pinia | 4.0.3 | 4.1.0 | |
| axios | 1.19.0 | 1.21.1 | |
| sm-crypto | 0.5.4 | 0.5.9 | 登录加密，见 §2.7 |
| vite | 8.2.0 | 8.3.8 | |
| @vitejs/plugin-vue | 6.0.8 | 6.0.11 | |
| @vue/test-utils | ^2.5.0 | ^2.6.0 | |
| @wangeditor-next/editor、editor-for-vue | 6.2.0 | 6.7.0 | |
| @codemirror/*（7 个） | — | 各自小版本 | |
| tailwindcss、@tailwindcss/vite | ^4.3.3 | ^4.3.5 | |
| dayjs | 1.11.21 | 1.11.23 | |
| uuid | ^14.0.1 | ^14.0.3 | |
| less | ~4.8.1 | ~4.9.1 | |
| terser | ~5.49.0 | ~5.51.3 | |
| prettier | ~3.9.6 | ~3.10.1 | |
| rollup-plugin-visualizer | ^7.0.1 | ^7.1.3 | |

两个前端改完 `package.json` 后都跑了 `npm install` 再 `npm update`，锁文件里的传递依赖一并刷到各自范围内的最新版。
**两端的版本写法刻意不统一**（C 端精确、管理端范围）：根 `CLAUDE.md` 写明两个前端不共享约定。

---

## 2. 遇到的问题与解决方式

### 2.1 🔴 增量编译掩盖了不兼容

**现象**：改完 `pom.xml` 里的版本号，`mvn -q compile test-compile` 退出码 0；接着 `mvn test` 时 base-file 冒出 3 个失败、6 个错误，全是运行期的 `NoClassDefFoundError: org/apache/tika/config/TikaConfig`。

**原因**：源码一行没动，maven-compiler 的增量判断认为不需要重编，`target/classes` 还是用 tika 3.3.2 编出来的。编译「通过」的是旧产物，运行时 classpath 上却是 4.2.0。

**做法**：`mvn clean compile test-compile` 重来一遍，这才看到两处真正的编译错误（§2.2、§2.3）。之后的所有结论都基于 `clean` 构建。

**下次**：升依赖后第一次构建一律带 `clean`。这条与 `docs/架构/后端升级白皮书-SpringBoot4.md` §4.4「先确认跑的是刚构建出来的那一份」是同一个道理，只是这次骗人的不是僵尸 JVM，是增量编译。

### 2.2 🔴 Spring Boot 4.2 拆分了 AMQP starter —— 第一次还选错了

**现象**：`clean` 后 `solvela-base-mq` 编译失败：`程序包 org.springframework.amqp.rabbit.connection 不存在`，`RabbitTemplate`、`ConnectionFactory` 都找不到。pom 本身没有任何报错。

**原因**：4.1 的 `spring-boot-starter-amqp` 就是经典 RabbitMQ。4.2 把它拆成了三个名字很像、内容完全不同的 starter：

| starter（4.2） | 底层 | 配置前缀 | 是什么 |
|---|---|---|---|
| `spring-boot-starter-amqp` | qpid protonj2 + spring-amqp-client | — | 通用 AMQP 1.0，**不带 spring-rabbit** |
| `spring-boot-starter-amqp-rabbitmq` | spring-rabbitmq-client | `spring.amqp.rabbitmq.*` | RabbitMQ 的 **AMQP 1.0 客户端** |
| `spring-boot-starter-rabbitmq` | spring-rabbit | `spring.rabbitmq.*` | **经典 RabbitMQ（AMQP 0.9.1）**，即 4.1 里 starter-amqp 原来的内容 ← 本项目用的是这个 |

旧 artifact 名字还在、照样能解析，所以 pom 层面毫无征兆。

**第一次修错了**：看名字选了 `spring-boot-starter-amqp-rabbitmq`。它**编译通过、全部单测通过**，因为它间接带着 `RabbitTemplate` 这些类；可经典的 `RabbitAutoConfiguration` 根本不在里面，yaml 里的 `spring.rabbitmq.host` 也没人读 —— app-biz 启动时会因为没有 `ConnectionFactory` 而失败。这个错误已经随 `7eb65d50` 推到了远端。

**怎么发现的**：写本文时核对「`spring.rabbitmq.*` 配置项不用改」这句话，去读了 `spring-boot-amqp-rabbitmq-4.2.0.jar` 的 `spring-configuration-metadata.json`，发现里面只有 `spring.amqp.rabbitmq.*`，健康检查的描述写着「AMQP 1.0 RabbitMQ」。再查 4.2 BOM，才找到真正的经典模块 `spring-boot-rabbitmq`（其 `RabbitAutoConfiguration` 在 `org.springframework.boot.rabbitmq.autoconfigure`，4.1 时在 `...boot.amqp.autoconfigure`）。

**做法**：

- `solvela-api/solvela-base-mq/pom.xml` 改依赖 `spring-boot-starter-rabbitmq`，pom 里把三个 starter 的区别写成 🔴 注释。`spring.rabbitmq.*` 配置项不用改。
- 新增 `solvela-api/solvela-base-mq/src/test/java/solvela/base/mq/RabbitAutoConfigurationWiringTest.java`：用 `ApplicationContextRunner` 装上 `RabbitAutoConfiguration` 与 `MqConfig`，断言 `spring.rabbitmq.host / port` 绑定到了连接工厂、`RabbitTemplate` 用的是 `MqConfig` 那个 Jackson 3 转换器。连接是懒加载的，不需要 broker。
- **反证过**：把 pom 临时改回 `amqp-rabbitmq`，这条测试直接编译失败（`org.springframework.boot.rabbitmq.autoconfigure` 不存在）；改回来后通过。
- 全仓搜过，没有任何地方按类名排除或引用 Rabbit 的自动配置类（类不存在会让启动失败，见 `~/.claude/constitution/stack-notes.md`），4.1 → 4.2 的包名变化不影响本项目。

**影响面**：只有 `solvela-base-mq` 一处直接依赖它（三个进程里只有 app-biz 引了 base-mq）。

**教训**：这正是宪法开篇说的「不报错、只是不起作用」—— 编译和单测证明不了自动配置装没装上。starter 换了，要么真启动一次，要么像上面那样用 `ApplicationContextRunner` 把「装配 + 属性绑定」钉住。

### 2.3 🔴 Tika 4 移走了 `TikaConfig`

**现象**：`clean` 后 `solvela-base-file` 的 `FileMimeTypeUtil` 编译失败，`org.apache.tika.config.TikaConfig` 找不到。它是**上传链路的必经之路**（`FileAssetService.upload` → `detectAllowedType` → `FileMimeTypeUtil.detect`）—— 如果没被抓到，上线后表现是所有上传全部失败。

**原因**：tika-core 4.x 的 `org.apache.tika.config` 包里只剩新的配置读取类，`TikaConfig` 不在 core 里了；`Detector.detect` 的签名也从 `(InputStream, Metadata)` 变成了 `(TikaInputStream, Metadata, ParseContext)`。

**做法**：改用 `new DefaultDetector()`，建一次存成 `static final` 复用。

- 3.x 的 `new TikaConfig().getDetector()` 拿到的就是 `DefaultDetector`（默认 MIME 库 + SPI 加载的探测器），所以这不是换了一种探测方式，只是换了入口。
- 以前每次上传都 `new TikaConfig()`，白白重新加载一遍 MIME 库；探测器无状态、线程安全，复用是净收益。
- 异常只剩 `IOException`，去掉了 `TikaException` 分支；探测失败仍返回 `application/octet-stream`，行为不变。

**怎么证明行为没变 —— 差异测试**（宪法六.5）：

1. 从系统目录与仓库里收了 **119 个真实文件，18 种类型**：png / jpg / gif / webp / svg / ico / pdf / ogg / wav / ttf / gz / json / xml / html / txt / csv，外加三个手工构造的恶意样本 —— 内容是 svg 却叫 `.png`、内容是 `MZ` 头（Windows 可执行文件）却叫 `.jpg`、空文件。
2. 写两个小程序，分别用「tika 3.3.2 + 旧写法 `TikaConfig`」与「tika 4.2.0 + 新写法 `DefaultDetector`」探测同一批文件，带上与生产代码相同的文件名元数据。
3. 结果 **119 / 119 逐条一致，零差异**。两个伪装样本两边都识破了（分别是 `image/svg+xml` 与 `application/x-msdownload`）。
4. `FileAssetService` 里另有一处 `MimeTypes.getDefaultMimeTypes().forName(contentType).getExtension()`（MIME → 扩展名），14 个常用类型新旧对照也完全一致。

**附带影响**：tika-core 4.2 多了三个运行期传递依赖 —— `commons-secure-xml`、`commonmark`（及其 gfm-tables / gfm-strikethrough 两个扩展）。它们会进入所有用到文件模块的进程。

**顺带一提**：Dependabot 早先开过一个 `tika-core-4.0.0` 的分支（`dependabot/maven/solvela-api/upgrade/springboot4/org.apache.tika-tika-core-4.0.0`），它只改了版本号，没改 `TikaConfig` 的用法，合进来就是上面那个运行期故障。

### 2.4 TypeScript 7 装不上，退回 6.0.3

**现象**：C 端 `npm install` 报 ERESOLVE：`typescript-eslint@8.73.0` 声明的 peer 是 `typescript >=4.8.4 <6.1.0`，与 `typescript@7.0.2` 冲突。

**判断**：typescript-eslint 是 lint 的类型感知规则引擎。用 `--force` / `--legacy-peer-deps` 硬装的结果是 lint 跑在一个它不认识的编译器上 —— 不报错，但类型规则的结论不可信，正是「不报错、只是不起作用」那一类。

**做法**：TypeScript 留在 6.x 线最新的 6.0.3（升级前也是它）。等 typescript-eslint 放开 7.x 的 peer 范围再升。`vue-tsc` 的 peer 是 `>=5.0.0`，不构成阻碍。

### 2.5 Prettier 3.10 让 7 个文件「格式不合格」

**现象**：C 端 `npm run ci` 第一步 `format:check` 失败，7 个文件被判格式有问题，而这些文件这次一行没改。

**原因**：Prettier 3.10 调整了中文等宽字符的行宽计算。原来算作没超宽的、含中文的长行，现在超了。

**做法**：对这 7 个文件跑 `prettier --write`，逐个看过 diff —— 全部是换行与对象展开，没有任何逻辑改动。这 7 个文件随 C 端那个提交一起提交了：

`src/composables/__tests__/useRetryAfter.spec.ts` · `src/utils/cost.ts` · `src/views/grade/__tests__/grade-deck.spec.ts` · `src/views/home/Tasks.vue` · `src/views/home/__tests__/tasks-limited.spec.ts` · `src/views/lottery/LotteryActivityView.vue` · `src/views/product/__tests__/product.spec.ts`

管理端没有 `format:check`，不受影响。

### 2.6 surefire 3.6.1：跳过数从 2 变成 15

**背景**：根 pom 以前没有声明 surefire，版本由本机 Maven 自带的默认绑定决定（Maven 3.9.16 → surefire 3.5.4）。换一台机器、换一个 Maven 就可能是另一版。这次显式锁成 3.6.1，与 4.2 BOM 里的 `maven-surefire-plugin.version` 对齐。

**现象**：锁定后 base-file 的统计从「170 个用例、2 个跳过」变成「183 个用例、15 个跳过」。

**原因**：`S3ObjectStorageMinioTest` 要连本地 MinIO，连不上时在 `@BeforeAll` 里用 `assumeTrue` 整类跳过。surefire 3.5.4 把这种情况记成「0 个用例」，3.6.1 改为把类里 13 个用例逐个记为跳过。

**结论**：实际执行的用例数前后都是 168（170 − 2 = 183 − 15），没有丢测试，只是统计口径变了。

### 2.7 sm-crypto 升级：给登录加密钉一个跨端黄金向量

**风险**：管理端登录密码由 `src/lib/encrypt.js` 用 `sm-crypto` 做 SM4 加密，后端 `ApiEncryptServiceSmImpl` 解密。后端 `ApiEncryptServiceTest` 早就钉着一串「前端实测能登录成功」的密文，但前端这侧没有对应的测试：`sm-crypto` 0.5.4 → 0.5.9 如果改了输出格式，前后端各自的测试都会是绿的，而所有人都登录不了。

**做法**：新增 `solvela-admin-web/src/lib/__tests__/encrypt.spec.js`，常量 `GOLDEN_123456` 直接抄自后端测试（不按当前实现重新生成，否则只是自证）：

- `encryptData('123456')` 必须等于后端那串值；
- `decryptData(那串值)` 必须得到 `123456`；
- 中文、特殊字符、跨分组长度的明文往返一致。

升级到 0.5.9 后三条全过。以后任何一侧改了 SM4 的参数，这条测试会先红。

### 2.8 npm audit：各剩 5 个 moderate，没有采纳「修复」

| | 升级前 | 升级后 |
|---|---|---|
| C 端 | 37（1 critical / 17 high / 19 moderate） | **5 moderate** |
| 管理端 | 22（15 high / 7 moderate） | **5 moderate** |

两边剩下的 5 个完全相同，全部来自测试工具 `@vue/test-utils` 的传递依赖链 `js-beautify → editorconfig / glob → minimatch`，只在跑测试时存在，不进生产包。npm 给出的修复方案是**降级**到 `@vue/test-utils@2.4.11`，没有采纳。

后端没有本地扫描手段（Dependabot 页面需要登录，项目也没有引入 OWASP 之类的插件），以 GitHub 上 Dependabot 的结果为准。

### 2.9 过程里的两个小坑（不影响代码，但会骗人）

- **git 中文路径转义**：分提交时用 `git status --short` 列文件，`docs/` 下的中文文件名被转义成 `"docs/\346\236..."`，按前缀分组时被漏掉。用 `git -c core.quotepath=false status` 才拿到原样路径。集合比对（分组并集 = 全部改动）当场抓到了这 2 个差集，否则文档订正会漏提交。
- **npm 的 allow-scripts 提示**：管理端 `npm install` 提示 `core-js`、`es5-ext` 的 install 脚本未执行（npm 新版默认拦截依赖的安装脚本）。这两个脚本只打印赞助信息，不影响构建；构建与 176 个用例均通过。

---

## 3. 订正的文档

- `README.md`、`docs/Solvela-项目现状总览.md`、`docs/架构/SonicExcel/SonicExcel-架构设计文档.md` 中描述现状的「Spring Boot 4.1」改为 4.2。
- **刻意没改**：`docs/架构/后端升级白皮书-SpringBoot4.md`、`docs/营销中台-会话交接文档.md` 是历史记录；SonicExcel 文档 §9.1 里的 `tika-core-3.3.2`、`mysql-connector-j-26.7.0` 是当时实测扫描 214 个 jar 的结果，改了版本号等于篡改证据。

---

## 4. 验证

### 4.1 做了什么、怎么做的

| 层 | 怎么验 | 结果 |
|---|---|---|
| 后端编译 | `mvn clean compile test-compile`（全 reactor） | 通过，唯一告警是 Lombok 调用 `sun.misc.Unsafe`（升级前就有） |
| 依赖解析 | `mvn compile dependency:list` 全 reactor，核对 §1.1 每个库 | 每个库只有一个版本，均为目标版本 |
| 后端单测 | `mvn clean test` 全 reactor | 所有域模块与底座模块零失败，含 §2.2 新增的 Rabbit 装配测试 |
| Tika 行为 | §2.3 的差异测试 | 119 / 119 一致，MIME → 扩展名 14 / 14 一致 |
| C 端 | `npm run ci` | 格式、lint（`--max-warnings 0`）、vue-tsc、44 个测试文件 313 个用例、构建全部通过 |
| 管理端 | `npm run ci` | 7 个测试文件 176 个用例（含 §2.7 新增的 3 个）、构建通过 |
| 前端运行时 | 两端 dev server 在浏览器里打开 | C 端登录页 → 切换登录方式 → 跳到注册页，控制台零错误；管理端登录页渲染正常，唯一报错是连不上 1024 端口的后端（本机没起后端，预期内） |

### 4.2 没验证到的

- **依赖 MySQL / Redis / RabbitMQ 的集成测试**：admin 174 个、app-biz 263 个、app 134 个用例都因 Spring 上下文起不来而报错（日志目录 `/home/logs` 不可写、本机没有 MySQL / Redis），逐个核对过报告，没有一个是因为别的原因失败的。也就是说，**MyBatis-Plus 3.5.18、Redisson 4.8.0、MySQL 驱动 26.10.0、Spring AMQP 4.2 都还没在真实中间件上跑过**。合并前应在有中间件的环境把这三个模块跑一遍。
- **三个后端进程没有真实启动过**。Rabbit 自动配置的装配与属性绑定已由 §2.2 的测试验到「容器层」，但没连过真的 broker；knife4j 5.8.3 的 `/doc.html` 还停在「编译通过」这一层。
- **管理端登录全流程**没有实测（需要后端）；SM4 只验到了「前端算出的密文与后端黄金向量一致」这一层。

---

## 5. 回滚

三个提交互不依赖，可以单独 `git revert`：

| 提交 | 回滚它会怎样 |
|---|---|
| `7eb65d50` 后端 | 回到 Spring Boot 4.1.0 + tika 3.3.2；AMQP starter 与 `FileMimeTypeUtil` 一并回到旧写法。三份文档里的「4.2」也会回退。**必须连同其后修正 starter 的那个提交一起回滚**，否则 4.1 下 `spring-boot-starter-rabbitmq` 不存在 |
| `cf0da29f` C 端 | 回到旧依赖；7 个文件的换行也会回退，此时它们在旧 Prettier 下本来就是合格的 |
| `af959ce9` 管理端 | 回到旧依赖，SM4 黄金向量测试一并删除（建议回滚后单独把这条测试加回来） |

---

## 6. 下次升级的检查清单

1. 改完版本号，**第一次构建带 `clean`**。
2. 读一遍目标版本的 release notes 里关于 **starter 拆分 / 改名** 的部分 —— 那类变化 pom 不会报错。换了 starter，去读它 jar 里的 `spring-configuration-metadata.json` 确认配置前缀，并用 `ApplicationContextRunner` 或真实启动验证自动配置装上了。
3. 跑 `dependency:list`，确认关键库只解析出一个版本、且是目标版本。
4. 换了实现入口的地方（这次是 Tika），**新旧实现跑同一批真实输入逐条比对**，零差异再固化。
5. 跨端契约（加密、签名、序列化）要有**两边共用同一个常量**的黄金向量测试。
6. 前端遇到 peer 冲突，退版本，**不用 `--force` 硬装**。
7. 测试数字变了（用例数、跳过数），先弄清楚是统计口径还是真少跑了，再继续。
