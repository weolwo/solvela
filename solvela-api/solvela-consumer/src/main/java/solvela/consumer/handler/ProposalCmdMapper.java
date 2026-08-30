package solvela.consumer.handler;

import solvela.member.api.CreateProposalCmd;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;

/**
 * 把四个发奖 handler 各自拼好的提案请求，翻成跨服务的 {@link CreateProposalCmd}。
 *
 * <h3>为什么是一层转换而不是直接建 CreateProposalCmd</h3>
 * 四个 handler 的拼装逻辑各不相同（券要 assetRef、实物要 assetName、现金只要金额），
 * 而它们原本都在拼 {@code ProposalRecordAddCommand}。<b>一次只改一件事</b>：
 * 这一步只把「调用方式」从进程内方法调用换成跨服务契约，拼装逻辑一行不动，
 * 出问题时能确定是调用方式引起的。
 *
 * <p>⚠️ <b>这是过渡形态。</b>{@code ProposalRecordAddCommand} 属于会员侧的风控域，
 * 发奖侧本不该认识它。等 consumer 整体搬到营销服务之后，
 * 四个 handler 应当直接拼 {@code CreateProposalCmd}，本类随之删除。
 *
 * <p>它现在还带着几个跨服务用不上的字段（tradeNo、审批人、审批时间…）——
 * 那些是提案在<b>会员侧内部</b>流转时才产生的，调用方填了也没意义，所以不搬。
 */
final class ProposalCmdMapper {

    private ProposalCmdMapper() {
    }

    static CreateProposalCmd toCmd(ProposalRecordAddCommand req) {
        return new CreateProposalCmd(
                req.getSourceBizId(),
                req.getMemberId(),
                req.getAssetType(),
                req.getAssetRef(),
                req.getAssetName(),
                req.getAmount(),
                req.getQuantity(),
                req.getSourceType(),
                req.getPromotionConfigId(),
                req.getRemark());
    }
}
