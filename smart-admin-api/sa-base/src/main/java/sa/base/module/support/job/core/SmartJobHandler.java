package sa.base.module.support.job.core;

import sa.base.module.support.job.constant.SmartJobLaneEnum;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务执行器声明。
 *
 * <p>🔴 <b>这个注解的存在就是为了替掉「按全限定类名注册」</b>（方案 §1.2 缺陷 1）：
 * 原先 {@code SmartJob#getClassName()} 返回 {@code this.getClass().getName()}，
 * 实现类只要挂了 {@code @Transactional} / {@code @Async} 就会被 CGLIB 代理，
 * 类名变成 {@code Xxx$$SpringCGLIB$$0}，与库里配置的类名对不上 ——
 * 匹配不到就静默跳过，<b>不报错、不打日志、任务从此不跑</b>；
 * 而新增任务时的 {@code Class.forName} 校验用的是原类名，<b>校验还必过</b>。
 *
 * <p>改用注解声明的名字后，注册表读注解时统一走 {@code AopUtils.getTargetClass(bean)}
 * 拿真实类，整条链路对代理免疫；重构改包名/类名也不会再断掉线上任务。
 *
 * <p>名字一旦上线就等同于一份契约：库里的 {@code t_smart_job.handler_name} 指向它。
 * 要改名，得连同数据一起迁。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartJobHandler {

    /**
     * 执行器名称，对应 {@code t_smart_job.handler_name}。
     *
     * <p>全局唯一。重名会让启动<b>直接失败</b>而不是静默取其一 —— 见
     * {@link SmartJobHandlerRegistry}。
     */
    String name();

    /**
     * 中文标题，给后台「未挂载 handler」列表和新增任务的下拉框用
     */
    String title();

    /**
     * 分组：SYSTEM / DATA / ACTIVITY / OPS / BUSINESS ...
     */
    String group() default "BUSINESS";

    /**
     * 执行车道。
     *
     * <p>🔴 <b>由执行器自己声明，运营在后台改不了</b> —— 否则运营把一个慢任务标成 FAST，
     * 快车道当场被毒死。写执行器的人才知道自己快慢。
     *
     * <p>声明 {@link SmartJobLaneEnum#FAST} 就等于承诺「我 30 秒内跑完」，
     * 框架会把超时硬性压到 {@link SmartJobLaneEnum#FAST_MAX_TIMEOUT_SECONDS} 秒并按时中断 ——
     * <b>声明必须被执行强制，否则隔离只存在于注释里。</b>
     */
    SmartJobLaneEnum lane() default SmartJobLaneEnum.SLOW;

    /**
     * 业务日期相对触发日的偏移天数。
     *
     * <p>{@code bizDate = triggerTime.toLocalDate() + bizDateOffset}。
     *
     * <p>🔴 <b>默认 0，框架不替业务猜 T-1。</b> 「定时任务通常处理 T-1 数据」这个说法
     * 只对数据统计类成立：文件孤儿清理、任务过期扫描、活动状态流转的业务日期就是当天；
     * 每小时跑一次的任务谈 T-1 更没有意义。
     *
     * <p>框架给一个「通常」的默认值，恰恰会<b>制造</b>那类「手工执行是对的、自动跑就不对」的 bug ——
     * 以后每个非统计类执行器的作者都要记得覆盖它，忘一个错一个，而且错得很安静。
     * <b>这是执行器的语义，不是框架的默认</b>，与 {@link #idempotent()} 同理。
     *
     * <p>统计类执行器写 {@code bizDateOffset = -1}。
     */
    int bizDateOffset() default 0;

    /**
     * 参数声明。后台据此渲染表单，而不是让运营在文本框里手填 JSON。
     *
     * <p>不声明也能跑（参数退化成裸字符串），但那就回到了
     * 「谁也不知道该填什么」的老路上 —— 新写执行器时请顺手声明。
     */
    JobParam[] params() default {};

    /**
     * 是否幂等（同样的参数重复执行结果一致、副作用不叠加）。
     *
     * <p>只有声明为 {@code true} 的执行器才允许配置失败重试 ——
     * 不幂等的任务自动重试等于把一次失败变成两次副作用。
     * 重试本身是第二档的能力，这里先把契约立上。
     */
    boolean idempotent() default false;

    /**
     * 默认超时秒数，任务未单独配置时取这个值；0 表示不限。
     *
     * <p>⚠️ 超时靠 {@code Future#cancel(true)} 实现，本质是
     * {@link Thread#interrupt()} —— <b>不响应中断的任务砍不掉</b>。
     * 循环处理批量数据的执行器，每批开头必须自查中断标记；
     * 发起 HTTP / 远程调用的，必须自带连接与读取超时。
     */
    int defaultTimeoutSeconds() default 300;
}
