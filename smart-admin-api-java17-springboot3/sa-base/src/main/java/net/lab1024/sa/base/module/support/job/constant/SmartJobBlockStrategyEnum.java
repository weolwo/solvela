package net.lab1024.sa.base.module.support.job.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

/**
 * 阻塞策略：上一次还没跑完时，本次怎么办。
 *
 * <p>🔴 <b>判定时序是「先抢占、再判阻塞」，不能反过来。</b>
 * 若写成「发现阻塞就不抢占」，一个 1 分钟触发、实际跑 5 分钟的任务会让
 * {@code next_trigger_time} 永远停在过去 —— 每秒被扫到、每秒被判阻塞，
 * <b>时间轮再也不转</b>；更糟的是它会顺带触发「超期未触发」告警，
 * 把一个正常的长任务报成「调度器挂了」。
 *
 * <p>这与背压的「池满就跳过、不抢占」看似矛盾，其实规则只有一条：
 * <b>节点级的判据跳过（换个节点可能有用），集群级的判据抢占（换谁都一样）。</b>
 * 池满是节点级的，上一次还在跑是集群级的。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Getter
@AllArgsConstructor
public enum SmartJobBlockStrategyEnum implements BaseEnum {

    /**
     * 丢弃本次，写一条 {@code BLOCKED} 日志。默认策略 ——
     * 周期性任务错过一次通常无所谓，下一轮还会来
     */
    DISCARD("DISCARD", "丢弃本次"),

    /**
     * 排队等上一次结束。⚠️ 队列有界，满了降级为 DISCARD ——
     * 无界排队会让一次抖动演变成雪崩式的积压
     */
    SERIAL("SERIAL", "排队串行"),

    /**
     * 中断上一次，跑本次。
     *
     * <p>⚠️ 中断能不能生效取决于执行器有没有响应中断（见 {@code SmartJob} 的 javadoc）。
     * 不响应中断的任务，这个策略实际退化成「两个实例并发跑」—— 比 DISCARD 危险得多，
     * 所以只在明确知道执行器可被中断时才选它
     */
    OVERRIDE("OVERRIDE", "中断上一次"),

    ;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）
     */
    private final String value;

    private final String desc;

    public static SmartJobBlockStrategyEnum resolve(String value) {
        for (SmartJobBlockStrategyEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return DISCARD;
    }
}
