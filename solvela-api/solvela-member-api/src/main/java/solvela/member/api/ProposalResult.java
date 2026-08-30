package solvela.member.api;

/**
 * 新增提案的结果。
 *
 * <h3>🔴 它只回答「收没收下」，不回答「用户拿到没有」</h3>
 * 提案可能进人工审批池，几小时后才有结论；即便自动通过，资产入账也在提案事务提交之后。
 * 所以本结果为 {@code accepted=true} 时<b>不能</b>把发奖流水标成成功 ——
 * 那会造成「记录显示成功、用户其实没收到」，这个坑本项目已经踩过一次
 * （见 {@code PrizeDispatchHandler} 里那段注释）。
 *
 * <p>终态由会员服务在资产真正入账后异步回调。
 *
 * @param accepted   会员服务是否收下了这笔奖
 * @param proposalId 提案 id；被拒绝时为 null
 * @param failReason 被拒原因。<b>写给用户看的</b> —— 它会落进 {@code t_prize_log.fail_reason}
 *                   并可能展示到 C 端，所以不要放表名、字段名、内部编码
 */
public record ProposalResult(boolean accepted, Long proposalId, String failReason) {

    public static ProposalResult accepted(Long proposalId) {
        return new ProposalResult(true, proposalId, null);
    }

    public static ProposalResult rejected(String failReason) {
        return new ProposalResult(false, null, failReason);
    }
}
