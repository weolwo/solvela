package net.lab1024.sa.base.module.support.job.config;

import lombok.Data;
import net.lab1024.sa.base.module.support.job.constant.SmartJobRoleEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * smart job 配置，与配置文件参数对应
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@ConfigurationProperties(prefix = SmartJobConfig.CONFIG_PREFIX)
@Data
public class SmartJobConfig {

    public static final String CONFIG_PREFIX = "smart.job";

    /**
     * 节点角色。决定这个进程装管理接口、还是装调度执行、还是都装。
     *
     * <p>独立部署时：业务节点 {@code ADMIN}，定时任务节点 {@code WORKER}。
     * 好处是业务发版只滚 ADMIN 节点，WORKER 不动，跑着的任务不受影响。
     */
    private SmartJobRoleEnum role = SmartJobRoleEnum.ALL;

    /**
     * 环境标识。第二档起用于「只抢本环境的任务」，本档先用于隔离 pub/sub topic。
     *
     * <p>🔴 不隔离会怎样：四套环境共用一份 DDL，一旦 dev 连到与 prod 同源的库或共用 Redis，
     * <b>dev 机器会收到并执行生产任务的消息</b>。原实现的 topic 名是写死的
     * {@code smart-job-instance}，跨环境串台没有任何阻拦。
     */
    private String env = "dev";

    // ==================== 快车道：秒级完成的轻任务 ====================

    private Integer fastCoreSize = 4;

    private Integer fastMaxSize = 8;

    /**
     * 快车道队列可以大：短任务堆积很快消化
     */
    private Integer fastQueueCapacity = 64;

    // ==================== 慢车道：长耗时批处理 ====================

    private Integer slowCoreSize = 2;

    private Integer slowMaxSize = 4;

    /**
     * 🔴 慢车道队列刻意近乎为 0：长任务排队毫无意义 ——
     * 排 30 分钟才开始跑，不如直接拒绝并告警，让「容量不够」立刻可见。
     * 同时 slowCoreSize 本身就是慢任务的全局并发限流器，
     * 用池大小当限流器比引入独立的限流概念简单得多。
     */
    private Integer slowQueueCapacity = 1;

    // ==================== 扫描 ====================

    /**
     * 扫描间隔秒数。
     *
     * <p>1 秒下 200 行的索引扫描对 MySQL 是无感的，换来的是
     * 「任何节点挂掉，最多 1 秒后别的节点接管」。
     */
    private Integer scanIntervalSeconds = 1;

    /**
     * 单轮最多捞多少条到期任务，防止一次拉爆内存
     */
    private Integer scanBatchSize = 200;

    // ⚠️ 曾有一个 scanPreloadSeconds（预读窗口）配置，已于 2026-08-12 实测后删除：
    //    它让任务真的提前执行、把 schedule_delay_ms 变成负数、并且抵消了 jitter 的打散效果。
    //    详见 SmartJobScanner#scanScheduledJob 的注释。
    //    留一个不起作用的旋钮比没有更糟 —— 会让人以为调它有用。

    /**
     * 任务默认超时秒数；0 表示不限。
     *
     * <p>执行器可用 {@code @SmartJobHandler#defaultTimeoutSeconds()} 单独声明，
     * 声明值优先。
     */
    private Integer defaultTimeoutSeconds = 300;

    /**
     * 优雅停机时等待任务跑完的最长秒数。
     *
     * <p>原实现是 10 秒且超时硬砍，会留下悬挂的「执行中」记录。
     * WORKER 独立部署后这个值可以放宽 —— 它不接流量，没有健康检查压力。
     *
     * <p>⚠️ 容器环境下必须让 {@code terminationGracePeriodSeconds} 大于本值 + 30，
     * 否则 K8s 默认 30 秒就 SIGKILL，这里配多久都不会被完整执行。
     */
    private Integer shutdownAwaitSeconds = 60;

    /**
     * 扫描线程启动延迟秒数，给容器留出预热时间
     */
    private Integer initDelay = 10;

    /**
     * 执行日志采集的最低级别：低于此级别的日志不进采集缓冲。
     *
     * <p>🔴 默认 INFO 而不是全采。实测（2026-08-12）一次 6 毫秒的任务采到 16 行，
     * 其中 <b>15 行是 MyBatis 的 DEBUG SQL</b>，执行器自己那句话只占 1 行 ——
     * 94% 是噪音。而缓冲区只保留最后 2000 行，一个跑上万次查询的批处理任务
     * 会把执行器真正想说的话<b>整个挤出去</b>，那时「查看实时日志」看到的全是 SQL，
     * 排查价值归零 —— 比没有这个功能更浪费时间，因为你会先花十分钟翻完它。
     *
     * <p>取值：TRACE / DEBUG / INFO / WARN / ERROR。确实要看 SQL 时临时调成 DEBUG。
     */
    private String logCollectLevel = "INFO";

    /**
     * 执行日志保留天数，由内置任务 {@code _jobLogClean} 使用
     */
    private Integer logRetainDays = 30;

    /**
     * 连续失败多少次触发告警，由内置任务 {@code _jobHealthCheck} 使用
     */
    private Integer alarmContinuousFailTimes = 3;

    /**
     * 「任务超期未触发」的告警阈值秒数。
     *
     * <p>🔴 这是三条告警里最容易漏、却唯一能发现「调度器本身挂了」的那条：
     * 前两条（连续失败、执行超时）只能告诉你任务跑错了，
     * 而调度线程死掉时系统会安静如常 —— 没有任何一条日志会说「我不跑了」。
     */
    private Integer alarmOverdueSeconds = 300;

    /**
     * 内置 HTTP 任务的目标 host 白名单。
     *
     * <p>🔴 <b>默认空 = 一切拒绝。</b> 没配就说明没人评估过风险，
     * 这时候「放行」是最坏的默认值 —— HTTP 任务本质是在后台开了一个 SSRF 入口。
     *
     * <p>支持两种写法：精确 host（{@code inner-api.example.com}），
     * 或以点开头的后缀（{@code .example.com}）。不做通配符，避免写出过宽的规则。
     *
     * <p>⚠️ 只能配在 yaml 里，<b>后台不可维护</b> —— 白名单若能在后台改，它就不是闸门了。
     */
    private java.util.List<String> httpAllowHosts = new java.util.ArrayList<>();

    // ⚠️ dbRefreshEnabled / dbRefreshInterval 已删除：
    //    那是「定时全表轮询数据库刷新内存 Trigger」时代的配置。
    //    抢占式调度下配置变更由 trigger_version 天然感知，不需要轮询兜底。
}
