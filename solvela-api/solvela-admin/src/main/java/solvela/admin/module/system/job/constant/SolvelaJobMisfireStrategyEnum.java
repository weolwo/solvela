package solvela.admin.module.system.job.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.enumeration.BaseEnum;

/**
 * 错过调度（misfire）策略：{@code now - next_trigger_time} 超过阈值时怎么办。
 *
 * <p>抢占式调度带来的一个白送的好处，就是「错过」这件事第一次<b>可见</b>了：
 * 停机期间该跑没跑的任务，它的 {@code next_trigger_time} 明晃晃地停在过去。
 * 原来那套（各节点内存 Trigger）里，漏掉的调度是无痕消失的 ——
 * 运营永远不知道昨晚那次统计没跑。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Getter
@AllArgsConstructor
public enum SolvelaJobMisfireStrategyEnum implements BaseEnum {

    /**
     * 跳过：把 {@code next_trigger_time} 直接推到当前之后的第一个点。
     *
     * <p>🔴 <b>跳过也必须写一条 {@code MISFIRE} 日志。</b>
     * 「跳过了」和「从来没触发过」在运营那儿是两件完全不同的事，
     * 不记录的话这次重构最大的可观测性收益就白拿了。
     */
    SKIP("SKIP", "跳过并记录"),

    /**
     * 立即补跑一次，再推到下一个点。数据类任务（统计、对账）该用这个 ——
     * 那天的数据不能少一天
     */
    FIRE_ONCE("FIRE_ONCE", "立即补跑一次"),

    ;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）
     */
    private final String value;

    private final String desc;

    public static SolvelaJobMisfireStrategyEnum resolve(String value) {
        for (SolvelaJobMisfireStrategyEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        // 解析不出来时按 SKIP 处理：这是较保守的一侧 ——
        // 脏数据导致意外补跑，比意外跳过更危险（补跑可能是发奖）
        return SKIP;
    }
}
