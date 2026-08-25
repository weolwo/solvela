package solvela.base.module.support.jobspi.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.common.enumeration.BaseEnum;

/**
 * 执行车道：快慢任务的物理隔离维度。
 *
 * <p>🔴 <b>隔离是稳定性的第一要义。</b> 所有任务共用一个池时，
 * 两个跑 30 分钟的清洗任务能瞬间吃光线程，让每分钟一次的探活任务全部被丢弃 ——
 * 而那种探活任务恰恰是「出事时唯一还能告诉你系统活着」的东西。
 *
 * <p>🔴 <b>lane 由 {@code @SolvelaJobHandler} 声明，运营在后台改不了。</b>
 * 否则运营把一个慢任务标成 FAST，快车道当场被毒死。执行器的作者才知道自己快慢。
 *
 * <p>而光靠声明还不够 —— 声明必须被执行强制，否则是纸面隔离：
 * FAST 车道的超时上限被硬性压到 {@link #FAST_MAX_TIMEOUT_SECONDS} 秒，
 * 保存任务时校验、运行时超时即中断。一个「声称 FAST 实则跑 5 分钟」的执行器，
 * 会在第 30 秒被砍掉，而不是继续占着快车道。
 *
 * <p>只做两条，不做 N 条可配的：SLOW 池的 core 大小本身就是慢任务的全局并发限流器，
 * 用池大小当限流器比引入独立的限流概念简单得多，在这个规模下也够用。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Getter
@AllArgsConstructor
public enum SolvelaJobLaneEnum implements BaseEnum {

    /**
     * 快车道：秒级完成的轻任务（探活、状态流转）。队列可以大，短任务堆积很快消化
     */
    FAST("FAST", "快车道"),

    /**
     * 慢车道：长耗时批处理（清理、统计、对账）。
     *
     * <p>队列刻意设得极小：长任务排队毫无意义 —— 排 30 分钟才开始跑，
     * 不如直接拒绝并告警，让「容量不够」这件事立刻可见
     */
    SLOW("SLOW", "慢车道"),

    ;

    /**
     * 🔴 FAST 车道的超时硬上限。声明与执行必须对齐，否则隔离只存在于注释里
     */
    public static final int FAST_MAX_TIMEOUT_SECONDS = 30;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）
     */
    private final String value;

    private final String desc;
}
