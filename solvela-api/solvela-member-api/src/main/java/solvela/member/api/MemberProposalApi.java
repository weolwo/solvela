package solvela.member.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 资产提案契约：营销侧把一笔奖交给会员服务。
 *
 * <h3>发奖为什么被切成同步 + 异步两段</h3>
 * <pre>
 *   marketing ──同步 HTTP──▶ member    收下 / 拒绝 + 原因（当场返回）
 *        ↓ 立刻落 t_prize_log.proposal_status + fail_reason
 *
 *   member ────异步消息────▶ marketing  资产真正入账完成 / 失败
 *        ↓ 落 t_prize_log.status（终态）
 * </pre>
 * 同步那段是为了<b>拿到失败原因</b>：C 端要能告诉用户为什么没发成，开发不用翻日志。
 * 异步那段是因为<b>审批与入账是慢的</b>，同步等不了。
 *
 * <h3>为什么不整条都走消息</h3>
 * 全异步的话，「被风控拒了」这个当场就知道的结论要等一个来回才回来，
 * 而这期间发奖流水停在「待提交」——分不清是没提交过去还是被拒了。
 *
 * <h3>幂等归会员服务</h3>
 * 重投必然带来重复请求。会员服务按 {@code sourceBizId} 判重，
 * <b>重复请求要返回与第一次相同的结果</b>，不能报错 —— 报错会让调用方把一笔
 * 其实已经受理的奖标成失败。
 */
@HttpExchange("/internal/member/proposal")
public interface MemberProposalApi {

    /**
     * 新增一笔资产提案。
     *
     * <p>风控拦截、配置异常等<b>预期内的拒绝</b>由 {@link ProposalResult#failReason} 表达，
     * 不抛异常 —— 跨进程后异常一律变成 5xx，而「被风控拒了」不是服务端故障。
     */
    @PostExchange
    ProposalResult createProposal(@RequestBody CreateProposalCmd cmd);
}
