package solvela.task.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.common.enumeration.BaseEnum;

import java.util.Arrays;

/**
 * 事件丢弃原因分类，落 {@code t_task_record_flow.discard_code}。
 *
 * <p>🔴 <b>为什么要和 {@code discard_reason} 并存，而不是二选一</b>：
 * 两者的读者不同，需求正好相反。
 * <ul>
 *   <li>{@code discard_reason} 是<b>给人读的</b>（客诉自证）：必须带上具体数值，
 *       「单笔金额 99 未达门槛 100」才回答得了客服的问题。
 *       但也正因为带了数值，它是<b>自由文本</b>，{@code GROUP BY} 会炸成几百个不同的值。</li>
 *   <li>{@code discard_code} 是<b>给机器读的</b>（大屏聚类）：取值封闭、稳定，
 *       改提示文案不会让统计图悄悄裂开。</li>
 * </ul>
 * 只留文本会统计不了，只留码会查不了客诉 —— 所以两个都要。
 *
 * <p>⚠️ 字段必须叫 {@code value} 且不要手写 getValue()（铁律 12），对齐 solvela-base 的 GenderEnum。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Getter
@AllArgsConstructor
public enum TaskDiscardCode implements BaseEnum {

    /**
     * 计额型任务但事件没带金额。多半是上游漏传，或注册表的 metric_source 配错
     */
    AMOUNT_MISSING("AMOUNT_MISSING", "事件未携带金额"),

    /**
     * 单笔金额未达 minAmount 门槛。属正常业务规则，不是故障
     */
    AMOUNT_BELOW_MIN("AMOUNT_BELOW_MIN", "未达单笔门槛"),

    /**
     * 连续型任务当日已计入。防御性分支，正常情况下流水表的唯一索引已挡在外层
     */
    STREAK_SAME_DAY("STREAK_SAME_DAY", "当日已计入连续进度"),

    /**
     * 任务记录已完成/已发奖/已过期，不再接受推进。属正常，用户已经拿到奖了
     */
    RECORD_NOT_RUNNING("RECORD_NOT_RUNNING", "任务已完成或已过期"),

    /**
     * 会员不符合任务限定的人群。属正常业务规则
     */
    AUDIENCE_MISMATCH("AUDIENCE_MISMATCH", "人群不符"),

    /**
     * 🔴 任务限定了人群，但上游没传 isNewMember。<b>这条是要去找上游修的</b>，
     * 不是正常业务现象 —— 大屏上应与 AUDIENCE_MISMATCH 分开看
     */
    AUDIENCE_UNKNOWN("AUDIENCE_UNKNOWN", "上游未告知会员属性"),

    /**
     * 本周期可完成轮次已用尽（limit_count）。属正常业务规则
     */
    ROUND_LIMIT_EXCEEDED("ROUND_LIMIT_EXCEEDED", "本周期次数已用尽"),

    /**
     * 🔴 任务配置本身坏了（taskType 非法、没有对应策略实现）。
     * <b>这个任务谁也跑不了</b>，出现即需要开发介入
     */
    CONFIG_INVALID("CONFIG_INVALID", "任务配置异常"),

    /**
     * 🔴 事件线程池队列打满被拒（AbortPolicy）。<b>系统过载信号</b>，
     * 频繁出现要扩容；上游应重投
     */
    POOL_REJECTED("POOL_REJECTED", "系统繁忙被拒"),
    ;

    private final String value;

    private final String desc;

    /**
     * 是否属于<b>需要人介入</b>的丢弃（相对于「正常业务规则导致的丢弃」）。
     *
     * <p>大屏据此把丢弃分成两类展示：正常规则拦截的量再大也不用管，
     * 而这三类哪怕只有几条都该报警。判据收在这一处，别在前端散写（铁律 3）。
     */
    public boolean needsAttention() {
        return this == AUDIENCE_UNKNOWN || this == CONFIG_INVALID || this == POOL_REJECTED;
    }

    public static TaskDiscardCode resolve(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
