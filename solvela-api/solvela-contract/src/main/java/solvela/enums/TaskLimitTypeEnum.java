package solvela.enums;

/**
 * 任务参与频次：{@code t_task_config.limit_type}，按枚举名存，JSON 也是枚举名。
 */
public enum TaskLimitTypeEnum {

    /** 终身一次 */
    ONCE,

    /** 每日重复：按自然日分轮 */
    DAILY,

    /** 每周重复：按 ISO 周分轮 */
    WEEKLY,

    /**
     * 无限制：<b>轮次不限</b>，此时 {@code limit_count} 不参与判定 ——
     * 否则「无限制 + 限制 1 次」会变成「终身一次」，与它自己的名字矛盾。
     */
    UNLIMITED;

    /** 按周期分轮（每日 / 每周）：只有这两种才有「每轮限几次」的说法 */
    public boolean isPeriodic() {
        return this == DAILY || this == WEEKLY;
    }
}
