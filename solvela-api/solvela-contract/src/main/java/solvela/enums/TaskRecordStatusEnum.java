package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务记录状态，对齐 {@code t_task_record.status}。
 *
 * <h3>取值是有序的</h3>
 * {@link #RUNNING} &lt; {@link #COMPLETED} &lt; {@link #DISPATCHED}，
 * 业务上确实存在「至少达到某一档」的判断（例如验收用例里的
 * {@code status >= COMPLETED} 表示「达标了，发没发奖不管」）。
 * 换成枚举后这类比较用 {@link #atLeast(TaskRecordStatusEnum)}，
 * 而不是拿 {@code ordinal()} 去比 —— ordinal 依赖声明顺序，
 * 有人调换一下常量位置就会静默改变语义。
 *
 * <p>{@link #EXPIRED} 不在这条链上：它是时间到了的旁路终态，与达标程度无关，
 * 所以 {@code atLeast} 对它没有意义。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum TaskRecordStatusEnum implements BaseEnum {

    /**
     * 进行中（含「低档已发奖、最高档未达标」）
     */
    RUNNING(0, "进行中"),

    /**
     * 已完成 = 最高档达标
     */
    COMPLETED(1, "已完成"),

    /**
     * 已发奖 = 最高档的奖也发完了，此后不再接受事件
     */
    DISPATCHED(2, "已发奖"),

    /**
     * 已过期：时间到了的旁路终态，不在达标链上
     */
    EXPIRED(3, "已过期"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 是否至少达到了 {@code other} 这一档。
     *
     * <p>刻意基于 {@code value} 而不是 {@code ordinal()}：ordinal 依赖常量声明顺序，
     * 调换位置就会静默改变语义，而 value 是落库的、不可能被随手改。
     */
    public boolean atLeast(TaskRecordStatusEnum other) {
        return this.value >= other.value;
    }
}
