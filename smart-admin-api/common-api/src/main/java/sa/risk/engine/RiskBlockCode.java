package sa.risk.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import sa.base.common.enumeration.BaseEnum;

import java.util.Arrays;

/**
 * 风控拦截原因分类，落 {@code t_proposal_record.risk_code}。
 *
 * <p>🔴 <b>为什么要和 {@code remark} 并存，而不是二选一</b>（与 {@code TaskDiscardCode} 同一理由）：
 * 两者的读者不同，需求正好相反。
 * <ul>
 *   <li>{@code remark} 是<b>给人读的</b>（客诉自证）：「活动过于火爆，当前奖项预算已耗尽」
 *       是直接给用户看的话术，将来必然会改，也早晚会带上具体数值；
 *   <li>{@code risk_code} 是<b>给机器读的</b>（漏斗聚类）：取值封闭、稳定，
 *       改提示文案不会让统计图悄悄裂开。</li>
 * </ul>
 * 这三个编码本来就在 {@link RiskResult#getRuleCode()} 里（注释写着「用于打日志和数仓分析」），
 * 只是此前<b>只进了日志、没进库</b> —— 于是提案漏斗只能按 remark 这个自由文本聚类。
 *
 * <p>⚠️ 字段必须叫 {@code value} 且不要手写 getValue()（铁律 12），对齐 sa-base 的 GenderEnum。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Getter
@AllArgsConstructor
public enum RiskBlockCode implements BaseEnum {

    /**
     * 🔴 单次发奖金额超过 {@code t_promotion_config.single_max_amount} 兜底上限。
     * <b>这是系统兜底被真的触发了</b>：上游算出了一个超过配置上限的金额，
     * 要么规则配错、要么金额计算有 bug，出现即需要人去查。
     */
    SINGLE_MAX_AMOUNT_LIMIT("SINGLE_MAX_AMOUNT_LIMIT", "单次金额超限（系统兜底）"),

    /**
     * 会员参与频次超过 {@code identify_limit}。属正常防刷，量再大也不用管 ——
     * 拦掉的本来就是薅羊毛的请求。
     */
    USER_FREQUENCY_LIMIT("USER_FREQUENCY_LIMIT", "参与过于频繁"),

    /**
     * 🔴 活动预算已耗尽。规则本身生效得没错，但<b>从这一刻起所有人都拿不到奖了</b> ——
     * 运营必须知道并决定加不加预算，否则活动就是在空转。
     */
    GLOBAL_BUDGET_LIMIT("GLOBAL_BUDGET_LIMIT", "预算已耗尽"),
    ;

    private final String value;

    private final String desc;

    /**
     * 是否属于<b>需要人介入</b>的拦截（相对于「正常规则拦掉薅羊毛」）。
     *
     * <p>漏斗据此把拦截分成两类展示：防刷拦得再多也不用管，
     * 而这两类哪怕只有几条都该有人看。判据收在这一处，别在前端散写（铁律 3）。
     */
    public boolean needsAttention() {
        return this == SINGLE_MAX_AMOUNT_LIMIT || this == GLOBAL_BUDGET_LIMIT;
    }

    public static RiskBlockCode resolve(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
