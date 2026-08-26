package solvela.consumer.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.PrizeStrategy;
import solvela.base.domain.ResponseDTO;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.prize.PrizeLog;
import solvela.risk.proposal.domain.form.ProposalRecordAddForm;
import solvela.risk.proposal.service.ProposalRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 积分派发策略
 * <p>
 * 与 {@link BalanceHandler} 同构：不自己动账，统一走风控提案链路，
 * 由提案域做防刷/预算/审批阈值判定，externalBizNo 作为跨域幂等键。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Slf4j
@AllArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.SCORE)
@Service
public class ScoreHandler implements IPrizeHandler {

    private final ProposalRecordService proposalRecordService;
    private final PrizeConfigService prizeConfigService;
    private final ProposalSourceResolver proposalSourceResolver;

    /**
     * 一次中奖发放一份。奖品配置目前没有数量维度，将来支持「一次发N份」时改从配置读
     */
    private static final int QUANTITY_PER_PRIZE = 1;

    @Override
    public ResponseDTO dispatch(PrizeLog prizeLog) {
        log.info(">>>> [积分派发策略] 开始派发积分，提案LogId: {}", prizeLog.getId());

        BigDecimal amount;
        try {
            amount = new BigDecimal(prizeLog.getPrizeValue());
        } catch (NumberFormatException e) {
            log.error("【发奖异常】积分数值格式错误: {}", prizeLog.getPrizeValue());
            return ResponseDTO.userErrorParam("积分数值格式错误");
        }

        // 负数一律拦死，防改包
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            log.error("【重大风控拦截】派发积分为负！LogId: {}, 数值: {}", prizeLog.getId(), amount);
            return ResponseDTO.userErrorParam("派发积分不能为负数");
        }
        // 0 分是「谢谢参与」这类占位奖品的正常取值：无需入账，直接判成功。
        // 若按 BalanceHandler 那样把 0 也当异常，抽奖的兜底奖项会刷出满屏失败流水，淹没真正的故障
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            log.info("【无需入账】奖品价值为0（如谢谢参与），跳过提案。LogId: {}", prizeLog.getId());
            return ResponseDTO.ok();
        }

        PrizeConfig prizeConfig = prizeConfigService.getByPrizeCode(prizeLog.getPrizeCode());
        if (prizeConfig == null) {
            return ResponseDTO.userErrorParam("奖品配置不存在");
        }

        ProposalRecordAddForm req = new ProposalRecordAddForm();
        req.setMemberId(prizeLog.getMemberId());
        req.setPromotionConfigId(prizeConfig.getPromotionConfigId());
        // 积分是值类资产：金额即全部信息，assetRef 留空
        req.setAssetType(PrizeTypeEnum.SCORE.name());
        req.setAmount(amount);
        req.setQuantity(QUANTITY_PER_PRIZE);
        req.setSourceType(proposalSourceResolver.resolve(prizeLog.getActivityCode()));
        req.setSourceBizId(prizeLog.getExternalBizNo());
        req.setRemark("参与活动[" + prizeLog.getActivityCode() + "]中奖发放积分");

        // 提案被风控拦截 / 资产配置异常时会返回非 ok，必须把失败如实传上去，
        // 否则 PrizeDispatchHandler 会把一条根本没入账的记录标成「发货成功」
        ResponseDTO result = proposalRecordService.addProposal(req);
        if (!result.getOk()) {
            log.warn("【积分提案未通过】LogId: {}, 原因: {}", prizeLog.getId(), result.getMsg());
        }
        return result;
    }
}
