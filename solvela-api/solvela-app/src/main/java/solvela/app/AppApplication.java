package solvela.app;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import solvela.base.listener.Ip2RegionListener;

/**
 * solvela C 端应用服务 启动类。
 *
 * <p>与 {@code solvela.admin.AdminApplication} 是<b>两个独立进程</b>：管理端跑 1024，本服务跑 1025。
 * 分开的理由不是模块化洁癖，而是三件具体的事：
 * <ul>
 *   <li><b>故障隔离</b>：后台跑一个大导出把进程拖垮时，用户侧不该跟着白屏；</li>
 *   <li><b>鉴权是两套</b>：员工登录（{@code t_employee} + 菜单权限）与会员登录
 *       （{@code t_member}，无权限模型）没有一行共用逻辑；</li>
 *   <li><b>容量特征相反</b>：管理端低并发重查询，C 端高并发轻查询，扩缩容策略不同。</li>
 * </ul>
 *
 * <h3>🔴 组件扫描是「列出要什么」，不是「扫全世界再减掉」</h3>
 * 上一版写的是 {@code @ComponentScan("solvela")} 加一个自定义 TypeFilter，
 * 把共享模块里的 controller 一个个排除掉 —— 那是在给一个错误的默认值打补丁：
 * 默认装配了不该装配的东西，然后靠一段过滤逻辑写对来兜底，漏一个就是把后台接口摆到公网。
 *
 * <p>现在反过来：本进程<b>只装配自己列出来的这几个包</b>。
 * <ul>
 *   <li>{@code solvela.app} —— 本模块；</li>
 *   <li>{@code solvela.base} —— 基础设施（数据源、Redis、缓存、加解密、Excel）；</li>
 *   <li>{@code solvela.member} —— 会员域（登录日志、操作限制、发号）；</li>
 *   <li>{@code solvela.crypto} —— PII 的加解密与摘要（{@code PiiCipher} / {@code PiiHasher}）。</li>
 * </ul>
 *
 * <p>要把 {@code solvela.crypto} 单列出来，是显式扫描的代价：上一版扫 {@code solvela} 全包，
 * 这类散落在 model 里的 {@code @Component} 自动就有了。代价换来的是
 * <b>「本进程装配了什么」是一份可以读的清单</b>，而不是一条 basePackage 加一串排除规则。
 * 漏了会怎样：启动即失败，报 NoSuchBeanDefinitionException 并指名道姓说缺哪个 ——
 * 这比「多装了一个后台接口而没人发现」好得多。
 *
 * <p>清单里的共享包<b>一个 controller 都没有</b>，所以不需要任何排除规则 ——
 * 这个前提由 {@code AppBoundaryTest} 守着。
 *
 * <p>特别注意<b>没有</b> {@code solvela.web}：那是管理端的 HTTP 层，
 * 它的 {@code GlobalExceptionHandler} 一律返回 200 + {@code ResponseDTO}，
 * 与本进程的错误契约（真实状态码 + {@code ApiErrorResponse}）直接冲突。
 * 两个 {@code @RestControllerAdvice} 同时在场时，返回什么取决于哪个先匹配上 ——
 * 那是最难查的一类问题。sa-token 也随之不在本进程的 classpath 上，
 * 由 {@code NoSaTokenTest} 守着。
 *
 * <h3>刻意没有 @EnableScheduling</h3>
 * 管理端已经开了。两个进程连的是<b>同一个库同一个 Redis</b>，再开一次等于所有
 * {@code @Scheduled} 任务每天跑两遍 —— 发奖、结算重复执行，多发的那一份没人会主动来报。
 * 定时任务归管理端进程所有，本服务只处理请求。
 */
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan({"solvela.app", "solvela.base", "solvela.member", "solvela.crypto"})
@ConfigurationPropertiesScan("solvela.app")
// Mapper 与 controller 不同，共享模块里的要全量扫：会员、钱包、券的 Mapper 都在那边
@MapperScan(value = {"solvela.app", "solvela.base", "solvela.member"}, annotationClass = Mapper.class)
@SpringBootApplication
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AppApplication.class);
        // ip2region 的 xdb 要在容器启动前落到本地文件，登录日志的「IP 归属地」依赖它
        application.addListeners(new Ip2RegionListener());
        application.run(args);
    }
}
