# syntax=docker/dockerfile:1
#
# solvela-admin 生产镜像。构建上下文是 solvela-api/：
#     docker build -t solvela-admin:3.0.0 solvela-api
#
# 四段：Maven 构建 → jlink 裁剪运行时 → Spring Boot 分层解包 → 组装。
# 最终镜像不含 JDK、不含 Maven、不含源码，只有一个 30 模块的 JRE 加解包后的应用。

# ============================================================================
# ① 构建。用 -Pprod：dev 是 activeByDefault，裸 mvn package 打出来的是 dev 包，
#    solvela-admin/pom.xml 里那套 API 文档依赖排除一条都不会生效。
# ============================================================================
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src

# 先只拷 pom 预热依赖，源码变动时这一层仍能命中缓存
COPY pom.xml ./
COPY solvela-base/pom.xml       solvela-base/
COPY common-api/pom.xml    common-api/
COPY solvela-marketing/pom.xml  solvela-marketing/
COPY solvela-admin/pom.xml      solvela-admin/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Pprod -DskipTests dependency:go-offline -Dsilent=true || true

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Pprod -DskipTests clean package \
 && cp solvela-admin/target/solvela-admin-prod-*.jar /app.jar

# ============================================================================
# ② jlink 裁剪运行时。JDK 25 共 70 个模块，这里保留 30 个（含传递依赖），
#    365MB → 76MB。清单来自对 fat jar 逐 jar 跑 jdeps，再补上反射/SPI 盲区。
# ============================================================================
FROM eclipse-temurin:25-jdk-noble AS jre
RUN jlink \
      --add-modules "\
java.base,\
java.compiler,\
java.desktop,\
java.instrument,\
java.logging,\
java.management,\
java.naming,\
java.net.http,\
java.prefs,\
java.rmi,\
java.scripting,\
java.security.jgss,\
java.security.sasl,\
java.sql,\
java.sql.rowset,\
java.transaction.xa,\
java.xml,\
jdk.jfr,\
jdk.net,\
jdk.unsupported,\
jdk.localedata,\
jdk.management,\
jdk.management.agent,\
jdk.jcmd,\
jdk.naming.dns,\
jdk.zipfs" \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --output /jre
#
# 各模块的来源（jdeps 逐 jar 归因，已排除传递污染）：
#   java.desktop        26 个 jar 直接引用。Spring 的 java.beans.Introspector 在这里，
#                       FileAssetService 的 javax.imageio 也在这里。删不掉，别试。
#   java.security.sasl  mysql-connector-j 的认证插件
#   java.security.jgss  httpclient5 的 SPNEGO
#   java.transaction.xa mysql-connector-j / p6spy / spring-tx
#   jdk.net             httpclient5 / httpcore5 / lettuce 的 socket 选项
#   java.rmi            freemarker      java.prefs  spring-beans
#   jdk.unsupported     Netty 要 sun.misc.Unsafe（Redisson 依赖 Netty）
#
#   jdk.localedata      🔴 jdeps 看不见，但缺了中文日期/数字格式化会【静默】退化成英文
#                       （实测：2026年8月16日 → "2026 Aug 16"，不报错，就是输出错了）
#   jdk.management      com.sun.management 的 CPU/内存 MXBean，HikariCP 与监控要用
#   jdk.jcmd            线上 jcmd / jstack 排障
#   jdk.management.agent 远程 JMX。不需要远程监控可以删，省不了多少
#   jdk.naming.dns      K8s 里 JNDI 走 DNS
#
# 【故意不带的几个，都是踩过的坑】
#   jdk.charsets    JDK 25 里 GBK / GB18030 已在 java.base（Linux 与 Windows 均已实测）
#   jdk.crypto.ec   SunEC provider 同样已并入 java.base，TLS 握手不受影响（已实测）
#   jdk.compiler    唯一引用者是 lombok，而 lombok 已改 provided + repackage 排除
#   jdk.attach      javassist / mybatis 的调试路径，运行时不走
#   jdk.jdi         同上

# ============================================================================
# ③ Spring Boot 分层解包。依赖层单独成层，业务代码改动时不必重传 70MB 依赖。
# ============================================================================
FROM eclipse-temurin:25-jdk-noble AS extract
COPY --from=build /app.jar /app.jar
RUN java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /extracted

# ============================================================================
# ④ 运行镜像
# ============================================================================
FROM debian:bookworm-slim

# tzdata：Java 自带 tzdb，但容器里的 /etc/localtime 影响 JVM 推断出的默认时区，
# 而 JDBC URL 里配了 serverTimezone=Asia/Shanghai，两边不一致会让时间字段整体偏移。
RUN apt-get update \
 && apt-get install -y --no-install-recommends tzdata ca-certificates curl \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo Asia/Shanghai > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:$PATH

COPY --from=jre /jre /opt/jre

# 两个必须预建且可写的目录，缺一个都起不来：
#   /home/logs/solvela       ← prod/application.yaml 的 project.log-directory
#   /home/solvela/upload     ← prod/solvela-base.yaml 的 file.storage.local.upload-path
# 后者尤其容易漏：FileConfig 的 localObjectStorage 在启动期就会探测可写性，
# 目录不可写会直接抛 StorageException 让整个上下文 refresh 失败，
# 而 prod 的 logback 没有 CONSOLE appender，docker logs 里只能看到一个 Spring 横幅，
# 真正的原因躺在容器里的 error.log —— 排查起来非常费劲。
#
# 这两个目录都应该在生产上挂成 volume：日志不挂就随容器消失，
# 上传目录不挂则用户传的文件在下次发版时全部蒸发。
RUN groupadd -r solvela && useradd -r -g solvela -d /app -s /usr/sbin/nologin solvela \
 && mkdir -p /app /home/logs/solvela /home/solvela/upload \
 && chown -R solvela:solvela /app /home/logs /home/solvela

VOLUME ["/home/logs", "/home/solvela/upload"]

WORKDIR /app
# 顺序即分层顺序：越靠前越少变动，Docker 层缓存命中率越高
COPY --from=extract --chown=solvela:solvela /extracted/dependencies/      ./
COPY --from=extract --chown=solvela:solvela /extracted/spring-boot-loader/ ./
COPY --from=extract --chown=solvela:solvela /extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=solvela:solvela /extracted/application/       ./

USER solvela
EXPOSE 1024

# -XX:+UseContainerSupport 是默认开的，这里显式给堆一个上限比例，
# 免得容器内存限制变化时还要改启动脚本。
#
# ⚠️ -Duser.language=zh -Duser.country=CN 不是可有可无的：
# 容器里 LANG=C.UTF-8，JVM 推出来的默认 Locale 是 en（已实测）。
# 凡是没显式指定 Locale 的格式化 —— DateTimeFormatter.ofLocalized*、String.format("%,d")、
# NumberFormat.getInstance() —— 输出会跟着变成英文格式，且【不报错】。
# 开发机上是 zh_CN 所以看不出来，一进容器就变了。这也是 jlink 保留 jdk.localedata 的意义所在。
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Shanghai -Duser.language=zh -Duser.country=CN"

# 项目排除了 spring-boot-actuator，没有 /actuator/health 可用。
# 这里借用登录模块里唯一免鉴权且无副作用的接口做存活探测。
# 若以后引入 actuator，请换成 /actuator/health。
# ⚠️ 不能用 `java 某个.java` 这种单文件源码启动写探针 —— 那需要 jdk.compiler，
#    而裁剪后的运行时没有这个模块。所以老老实实装了个 curl。
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS -o /dev/null http://127.0.0.1:1024/login/getTwoFactorLoginFlag || exit 1

# ⚠️ 这里依赖 WORKDIR=/app：没有 -cp，java 用默认类路径 "."，
#    靠的是 extract --launcher 把 loader 类和 BOOT-INF 一起摊在了 /app 下。
#    改 WORKDIR 会直接启动失败。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher \"$@\"", "--"]

# ============================================================================
# 附录：JDK 25 AOT cache（JEP 514）—— 实测下来【不建议默认开】
# ============================================================================
# 实测数据（本机 Docker，各跑 3 次，连同一套 MySQL/Redis）：
#     无 AOT   8.71 / 8.28 / 8.11 s   平均 8.37s
#     有 AOT   6.44 / 6.95 / 6.54 s   平均 6.64s
#   → 快 1.7s（21%），代价是镜像多 126MB（未压缩）/ 30MB（压缩后传输体积）。
#   这个投入产出比对本项目不划算，所以没有写进主流程。
#   只有在「按量弹性扩容、冷启动延迟直接影响可用性」的场景下才值得开。
#
# 若确实要用，流程如下（三个坑都在注释里）：
#
#   # 1. 训练：必须能连到 MySQL 和 Redis —— AOT 缓存要在上下文 refresh 完之后才有内容，
#   #    而 refresh 期间 HikariCP 和 Redisson 都会真连。构建期没有 DB，所以【没法在
#   #    Dockerfile 里烤进去】，只能起一个训练容器。
#   docker run -d --name solvela-train \
#     -e JAVA_OPTS="-XX:AOTCacheOutput=app.aot -Duser.timezone=Asia/Shanghai -Duser.language=zh -Duser.country=CN" \
#     solvela-admin:3.0.0 <各种 --spring.xxx 覆盖>
#
#   # 2. 等启动完成 + 跑一段真实流量预热，然后【优雅停机】。
#   #    ⚠️ 缓存是 JVM 退出时才写的，docker kill（SIGKILL）会一无所获，必须 docker stop。
#   #    ⚠️ --spring.context.exit=onRefresh 在本项目上【无效】（实测应用照常常驻），
#   #       原因是 SolvelaJob 线程池等非守护线程还活着，别指望它。
#   docker stop -t 90 solvela-train
#
#   # 3. 取出缓存，叠一层薄镜像
#   docker cp solvela-train:/app/app.aot ./app.aot
#   #    Dockerfile.aot:
#   #      FROM solvela-admin:3.0.0
#   #      COPY --chown=solvela:solvela app.aot /app/app.aot
#   #      ENV JAVA_OPTS="...原有参数... -XX:AOTCache=app.aot"
#
# 另外两个 JDK 25 的实测坑：
#   · -XX:AOTCacheOutput 【不接受绝对路径】，只能给裸文件名，写到 CWD。
#     传 /aot/app.aot 会直接 "Option AOTCacheOutput must specify a single file name" 起不来。
#   · 缓存和 JVM + classpath 强绑定，镜像每次重建都得重新训练，否则缓存失效静默回退。
