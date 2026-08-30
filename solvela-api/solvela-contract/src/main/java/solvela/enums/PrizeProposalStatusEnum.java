package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发奖流水的<b>提案侧</b>结果，对齐 {@code t_prize_log.proposal_status}。
 *
 * <h3>🔴 与 {@code PrizeDispatchStatusEnum} 是两件事，别混</h3>
 * <ul>
 *   <li><b>本枚举</b>回答「<b>会员服务收没收下这笔奖</b>」—— 同步 HTTP 调用当场就知道；</li>
 *   <li>{@code PrizeDispatchStatusEnum}（{@code status} 列）回答
 *       「<b>用户最终有没有拿到</b>」—— 提案可能进人工审批池，几小时后才有结果，
 *       由会员服务的异步回调落终态。</li>
 * </ul>
 * 拆成两个字段之前这两件事压在一个 status 上，于是「已受理但还没入账」和
 * 「已入账」长得一模一样 —— 排查时分不清是卡在审批还是根本没提交过去。
 *
 * @Date 2026-08-30
 */
@Getter
@AllArgsConstructor
public enum PrizeProposalStatusEnum implements BaseEnum {

    /**
     * 待提交：还没调用过会员服务，或调用前进程挂了。
     *
     * <p>这个状态是<b>可重试的</b>：重投任务扫的就是停在这里的行。
     * 所以它必须是默认值 —— 默认值若是「已受理」，漏掉的那批就永远没人管了。
     */
    PENDING(0, "待提交"),

    /** 已受理：会员服务生成了提案。<b>不等于用户拿到了奖</b>，终态看 status */
    ACCEPTED(1, "已受理"),

    /** 被拒绝：风控拦截、资产配置异常等。原因在 fail_reason 里，C 端可以直接展示 */
    REJECTED(2, "被拒绝"),
    ;

    private final Integer value;

    private final String desc;
}
