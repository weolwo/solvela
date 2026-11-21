package solvela.enums;

/**
 * 任务档位奖励的计算类型：{@code t_task_prize_mapping.prize_mode}，按枚举名存，JSON 也是枚举名。
 *
 * <p>⚠️ 只有 {@link #FIXED} 真正实现了。RATIO / FORMULA 配得上、存得进，派发时退回奖品配置的固定值
 * （TaskPrizeDispatcher.resolvePrizeValue），管理端的配置体检会把这一点提示出来（TaskPrizeMappingService）。
 * 保留这两个值是因为管理端下拉可选、库里可能已有，删掉会让那些行读不出来。
 */
public enum TaskPrizeModeEnum {

    /** 固定值，取 prize_strategy.value */
    FIXED,

    /** 按比例，如 {"ratio": 0.05}（未实现） */
    RATIO,

    /** 表达式，交给规则引擎算（未实现） */
    FORMULA,
}
