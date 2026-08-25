package solvela.admin.module.system.job.config;

import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.job.api.SolvelaJobClientManager;
import solvela.admin.module.system.job.api.SolvelaJobMsgPublisher;
import solvela.admin.module.system.job.bootstrap.SolvelaJobBootstrapInitializer;
import solvela.admin.module.system.job.alarm.SolvelaJobAlarmSender;
import solvela.admin.module.system.job.alarm.SolvelaJobLogAlarmSender;
import solvela.admin.module.system.job.constant.SolvelaJobRoleEnum;
import solvela.base.module.support.jobspi.core.SolvelaJob;
import solvela.admin.module.system.job.core.SolvelaJobHandlerRegistry;
import solvela.admin.module.system.job.core.SolvelaJobLaneExecutePool;
import solvela.admin.module.system.job.core.SolvelaJobRunner;
import solvela.admin.module.system.job.core.SolvelaJobRunningRegistry;
import solvela.admin.module.system.job.core.SolvelaJobScanner;
import solvela.admin.module.system.job.log.SolvelaJobLogAppenderRegistrar;
import solvela.admin.module.system.job.log.SolvelaJobLogCollector;
import solvela.admin.module.system.job.repository.SolvelaJobDao;
import solvela.admin.module.system.job.repository.SolvelaJobRepository;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 定时任务装配。
 *
 * <p>🔴 <b>本类的第一职责是把「执行」和「管理」拆开。</b>
 *
 * <p>原实现只有一个 {@code solvela.job.enabled} 开关，而管理接口
 * {@code SolvelaJobService}、消息通道都挂着
 * {@code @ConditionalOnBean(SolvelaJobAutoConfiguration.class)} ——
 * 于是「业务节点不跑任务、但仍能在后台管理任务」<b>物理上做不到</b>：
 * 关掉 enabled，后台的定时任务菜单直接报错。而这正是独立部署要的形态。
 *
 * <p>拆开之后：
 * <ul>
 *   <li>{@link SolvelaJobHandlerRegistry} / {@link SolvelaJobMsgPublisher} —— <b>所有角色都装</b>
 *       （ADMIN 用注册表校验 handler、渲染下拉框，用 publisher 通知 WORKER 去执行）；</li>
 *   <li>执行池 / 扫描器 / 消息订阅端 / 内置任务自举 —— <b>只有 WORKER 装</b>。</li>
 * </ul>
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SolvelaJobConfig.class)
@ConditionalOnProperty(
        prefix = SolvelaJobConfig.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true"
)
public class SolvelaJobAutoConfiguration {

    /**
     * 「本节点是否执行任务」的条件表达式：WORKER 或 ALL，未配置时默认 ALL。
     *
     * <p>用 {@code @ConditionalOnExpression} 而不是两个
     * {@code @ConditionalOnProperty(havingValue=...)} 的 Bean：后者的 {@code havingValue}
     * 只能匹配单值，凑出「WORKER 或 ALL」要写两个同类型 Bean 再靠
     * {@code @ConditionalOnBean} 串起来 —— 而
     * <b>{@code @ConditionalOnBean} 在普通 {@code @Configuration} 里依赖 Bean 定义的注册顺序</b>，
     * 官方明确不推荐。顺序一旦变，症状是「WORKER 节点悄悄不跑任务」，
     * 又是一个查起来要命的静默失效。
     */
    private static final String WORKER_ROLE_EXPRESSION =
            "'${solvela.job.role:ALL}'.equalsIgnoreCase('WORKER') or '${solvela.job.role:ALL}'.equalsIgnoreCase('ALL')";

    private final SolvelaJobConfig jobConfig;

    public SolvelaJobAutoConfiguration(SolvelaJobConfig jobConfig) {
        this.jobConfig = jobConfig;
        log.info("==== SolvelaJob ==== 角色={} 环境={}", jobConfig.getRole(), jobConfig.getEnv());
    }

    /**
     * 执行器注册表：ADMIN 与 WORKER 都要。
     *
     * <p>只有 WORKER 做启动对账 —— ADMIN 节点的 classpath 上可能压根没有执行器实现，
     * 在那里对账会刷出一屏无意义的 ERROR。
     */
    @Bean
    public SolvelaJobHandlerRegistry solvelaJobHandlerRegistry(ObjectProvider<SolvelaJob> jobBeanProvider,
                                                               SolvelaJobDao jobDao) {
        SolvelaJobRoleEnum role = jobConfig.getRole();
        // 把 ObjectProvider 传进去，同时把是否为 NONE 角色的标识也传进去
        return new SolvelaJobHandlerRegistry(
                jobBeanProvider,
                jobDao,
                role.isWorker(),
                role == SolvelaJobRoleEnum.NONE // 新增一个标识位
        );
    }

    /**
     * 消息发布端：<b>所有角色都装</b>。
     *
     * <p>🔴 ADMIN 节点必须有它，否则后台的「立即执行」和「保存后生效」无法通知到 WORKER。
     */
    @Bean
    public SolvelaJobMsgPublisher solvelaJobMsgPublisher(RedissonClient redissonClient) {
        return new SolvelaJobMsgPublisher(jobConfig, redissonClient);
    }

    /**
     * 执行日志采集器：ADMIN 侧也要（后台读日志），WORKER 侧写
     */
    @Bean
    public SolvelaJobLogCollector solvelaJobLogCollector(RedissonClient redissonClient) {
        return new SolvelaJobLogCollector(redissonClient);
    }

    /**
     * 告警通道。默认只落 ERROR 日志并带抑制；
     * 用 {@code @ConditionalOnMissingBean} 留出替换点 —— 接企微机器人时自己声明一个 Bean 即可，
     * 不用改这里
     */
    @Bean
    @ConditionalOnMissingBean(SolvelaJobAlarmSender.class)
    public SolvelaJobAlarmSender solvelaJobAlarmSender() {
        return new SolvelaJobLogAlarmSender();
    }

    // ==================== 以下只有会执行任务的节点才装 ====================

    /**
     * 本节点正在跑哪些执行记录 —— 支撑「终止」。终止请求靠 pub/sub 广播，
     * 只有真正持有那个 Future 的节点会响应
     */
    @Bean
    @ConditionalOnExpression(WORKER_ROLE_EXPRESSION)
    public SolvelaJobRunningRegistry solvelaJobRunningRegistry() {
        return new SolvelaJobRunningRegistry();
    }

    @Bean
    @ConditionalOnExpression(WORKER_ROLE_EXPRESSION)
    public SolvelaJobLaneExecutePool solvelaJobLaneExecutePool(SolvelaJobRunningRegistry runningRegistry) {
        return new SolvelaJobLaneExecutePool(jobConfig, runningRegistry);
    }

    /**
     * 把日志采集 Appender 挂到 root logger。只有 WORKER 需要 ——
     * ADMIN 不执行任务，挂上去纯属白耗
     */
    @Bean
    @ConditionalOnExpression(WORKER_ROLE_EXPRESSION)
    public SolvelaJobLogAppenderRegistrar solvelaJobLogAppenderRegistrar(SolvelaJobLogCollector collector) {
        return new SolvelaJobLogAppenderRegistrar(collector, jobConfig.getLogCollectLevel());
    }

    @Bean
    @ConditionalOnExpression(WORKER_ROLE_EXPRESSION)
    public SolvelaJobRunner solvelaJobRunner(SolvelaJobRepository jobRepository,
                                             SolvelaJobLaneExecutePool executePool) {
        return new SolvelaJobRunner(jobRepository, executePool);
    }

    /**
     * 内置任务自举必须在扫描器<b>之前</b>完成。
     *
     * <p>两者都是 {@code SmartInitializingSingleton} / 构造期启动，
     * 这里用参数依赖把顺序钉死：先有任务配置，扫描器才有东西可扫。
     */
    @Bean
    @ConditionalOnExpression(WORKER_ROLE_EXPRESSION)
    public SolvelaJobBootstrapInitializer solvelaJobBootstrapInitializer(SolvelaJobDao jobDao) {
        return new SolvelaJobBootstrapInitializer(jobDao, jobConfig);
    }

    @Bean
    @ConditionalOnExpression(WORKER_ROLE_EXPRESSION)
    public SolvelaJobScanner solvelaJobScanner(SolvelaJobRepository jobRepository,
                                               SolvelaJobHandlerRegistry handlerRegistry,
                                               SolvelaJobRunner jobRunner,
                                               SolvelaJobLaneExecutePool executePool,
                                               SolvelaJobBootstrapInitializer bootstrapInitializer) {
        return new SolvelaJobScanner(jobConfig, jobRepository, handlerRegistry, jobRunner, executePool);
    }

    @Bean
    @ConditionalOnExpression(WORKER_ROLE_EXPRESSION)
    public SolvelaJobClientManager solvelaJobClientManager(RedissonClient redissonClient,
                                                           SolvelaJobRunningRegistry runningRegistry) {
        return new SolvelaJobClientManager(jobConfig, redissonClient, runningRegistry);
    }
}
