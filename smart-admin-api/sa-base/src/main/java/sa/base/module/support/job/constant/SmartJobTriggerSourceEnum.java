package sa.base.module.support.job.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import sa.base.common.enumeration.BaseEnum;

/**
 * 执行记录的触发来源。
 *
 * <p>🔴 <b>刻意没有 {@code RETRY} 这个取值。</b> 重试记录<b>原样继承</b>原记录的来源
 * （定时的重试仍是 {@link #SCHEDULE}，手动的重试仍是 {@link #MANUAL}），
 * 靠 {@code retry_seq > 0} 表达「这是一次重试」。两个理由：
 * <ul>
 *   <li>{@code retry_seq > 0} 本身就是重试标记，不需要第二处表达同一件事；</li>
 *   <li>若重试统一记成 RETRY，「手动触发的重试」与「定时的重试」可能在同一
 *       {@code trigger_time} 上撞进 {@code uk_job_trigger} 的同一个键空间。</li>
 * </ul>
 *
 * <p>本枚举同时是唯一索引 {@code uk_job_trigger} 的组成部分，作用是把两类记录物理隔离 ——
 * 语义上它们本就该分开：{@link #SCHEDULE} 的 {@code trigger_time} 是「原定调度时刻」，
 * {@link #MANUAL} 的是「点击时刻」，不是一个概念。
 * 不隔离的话，运营点「立即执行」的那一秒若恰好撞上该任务的 cron 触发时刻，
 * 唯一键冲突会让手动执行直接失败。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Getter
@AllArgsConstructor
public enum SmartJobTriggerSourceEnum implements BaseEnum {

    /**
     * 定时调度。trigger_time = 原定触发时刻
     */
    SCHEDULE("SCHEDULE", "定时调度"),

    /**
     * 手动触发（含一键重跑）。trigger_time = 点击时刻
     */
    MANUAL("MANUAL", "手动触发"),

    ;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）
     */
    private final String value;

    private final String desc;
}
