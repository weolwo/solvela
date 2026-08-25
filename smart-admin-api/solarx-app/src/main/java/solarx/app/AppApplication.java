package solarx.app;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import sa.base.listener.Ip2RegionListener;
import solarx.app.config.SaControllerExcludeFilter;

/**
 * solarx C 端应用服务 启动类。
 *
 * <p>与 {@code sa.admin.AdminApplication} 是<b>两个独立进程</b>：管理端跑 1024，本服务跑 1025。
 * 分开的理由不是模块化洁癖，而是三件具体的事：
 * <ul>
 *   <li><b>故障隔离</b>：后台跑一个大导出把进程拖垮时，用户侧不该跟着白屏；</li>
 *   <li><b>鉴权是两套</b>：员工登录（{@code t_employee} + 菜单权限）和会员登录
 *       （{@code t_member} + 无权限模型）没有一行共用逻辑，塞在一个拦截器里迟早互相污染；</li>
 *   <li><b>容量特征相反</b>：管理端是低并发重查询，C 端是高并发轻查询，扩缩容策略不同。</li>
 * </ul>
 *
 * <h3>为什么要排除 sa.** 下的 controller</h3>
 * 本模块依赖 sa-base 与 common-api 是为了复用领域层（service / manager / dao），
 * 但它们自带的 controller 是<b>管理端视角</b>的接口。如果照 AdminApplication 那样直接
 * {@code @ComponentScan("sa")}，这些 controller 会连同服务一起注册到 1025 端口上。
 * 拦截器确实会拦住未登录请求，但那意味着<b>安全性依赖于「拦截器没写错」这一个假设</b>，
 * 而正确答案是这些路由压根不该存在于这个进程里。
 *
 * <p>判据见 {@link SaControllerExcludeFilter}：按 {@code @Controller} 注解认，不按包名认 ——
 * 早先用包名正则时漏掉过 {@code ConfigController} / {@code TableColumnController}
 * 这两个没放在 controller 包下的类，实测确实注册上了。
 *
 * <p>🔴 本模块的接口<b>一律写在 {@code solarx.app.module.**.controller} 下</b>，
 * 需要复用某个查询时注入它的 service，不要试图把 {@code sa} 的 controller 放进来。
 *
 * <h3>刻意没有 @EnableScheduling</h3>
 * 管理端已经开了。两个进程连的是<b>同一个库同一个 Redis</b>，再开一次等于所有 {@code @Scheduled}
 * 任务每天跑两遍 —— 发奖、结算这类任务重复执行，多发的那一份没人会主动来报。
 * 定时任务归管理端进程所有，本服务只处理请求。同理 {@code smart.job.enabled} 在
 * 本模块的 application.yaml 里显式关掉了。
 *
 * @Date 2026-08-25
 */
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan(
        basePackages = {AppApplication.COMPONENT_SCAN_APP, AppApplication.COMPONENT_SCAN_SA},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.CUSTOM,
                classes = SaControllerExcludeFilter.class))
// Dao 与 controller 相反，必须全量扫：会员、钱包、券的 Mapper 都在 sa.** 下面
@MapperScan(value = {AppApplication.COMPONENT_SCAN_APP, AppApplication.COMPONENT_SCAN_SA}, annotationClass = Mapper.class)
@SpringBootApplication
public class AppApplication {

    public static final String COMPONENT_SCAN_APP = "solarx";

    public static final String COMPONENT_SCAN_SA = "sa";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AppApplication.class);
        // ip2region 的 xdb 需要在容器启动前落到本地文件，登录日志的「IP 归属地」依赖它
        application.addListeners(new Ip2RegionListener());
        application.run(args);
    }
}
