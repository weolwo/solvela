package solvela.risk.proposal.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.exception.BusinessException;
import solvela.member.api.CreateProposalCmd;
import solvela.member.api.MemberProposalApi;
import solvela.member.api.ProposalResult;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;
import solvela.risk.proposal.service.ProposalRecordService;

/**
 * {@link MemberProposalApi} 的实现：把营销侧交过来的一笔奖变成一条提案。
 *
 * <h3>它只做一件事：把异常翻成返回值</h3>
 * {@link ProposalRecordService#addProposal} 用 {@code BusinessException} 表达
 * 风控拦截、资产配置异常这些<b>预期内</b>的拒绝 —— 那在进程内是合适的，
 * 但跨进程之后异常一律变成 5xx，而「被风控拒了」不是服务端故障。
 *
 * <p>所以这一层把它翻成 {@link ProposalResult#failReason}。
 * 翻译放在这里而不是改 {@code addProposal} 本身：后台的审批、人工补发也在调它，
 * 那些路径上抛异常是对的（调用方是人，需要当场看到报错）。
 * <b>同一段逻辑，对内抛异常、对外给返回值</b>，差别只在这一层。
 *
 * <h3>为什么失败原因可以直接给用户看</h3>
 * 它会落进 {@code t_prize_log.fail_reason}，C 端可能展示。
 * 所以 {@code addProposal} 抛出的 message 必须是人话 —— 目前是（「资产配置异常」
 * 「风控拦截: 单日限额已达上限」这类）。往里加表名、字段名之前先想想这一点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalApiService implements MemberProposalApi {

    private final ProposalRecordService proposalRecordService;

    @Override
    public ProposalResult createProposal(CreateProposalCmd cmd) {
        ProposalRecordAddCommand req = new ProposalRecordAddCommand();
        req.setMemberId(cmd.memberId());
        req.setAssetType(cmd.assetType());
        req.setAssetRef(cmd.assetRef());
        req.setAssetName(cmd.assetName());
        req.setAmount(cmd.amount());
        req.setQuantity(cmd.quantity());
        req.setSourceType(cmd.sourceType());
        req.setSourceBizId(cmd.sourceBizId());
        req.setPromotionConfigId(cmd.promotionConfigId());
        req.setRemark(cmd.remark());

        try {
            return ProposalResult.accepted(proposalRecordService.addProposal(req));
        } catch (BusinessException e) {
            // 预期内的拒绝：风控拦了、配置有问题、金额非法。原样把话带回给调用方，
            // 它会落进发奖流水的 fail_reason —— 这正是拆服务后仍要同步调用的理由
            log.warn("【提案被拒】sourceBizId: {}, 原因: {}", cmd.sourceBizId(), e.getMessage());
            return ProposalResult.rejected(e.getMessage());
        }
    }

}
