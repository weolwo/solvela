package solvela.consumer.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.PrizeStrategy;
import solvela.base.common.domain.ResponseDTO;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.prizeconfig.domain.entity.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.prize.prizelog.domain.entity.PrizeLog;
import solvela.risk.proposal.domain.form.ProposalRecordAddForm;
import solvela.risk.proposal.service.ProposalRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 优惠券派发策略
 *
 * 与 {@link BalanceHandler} 同构：统一走风控提案链路，externalBizNo 作为跨域幂等键。
 * 券的面额只用于风控预算口径，真正的券实例发放由 ledger 层的 CouponAssetHandler 负责。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Slf4j
@AllArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.COUPON)
@Service
public class CouponHandler implements IPrizeHandler {

    private final ProposalRecordService proposalRecordService;
    private final PrizeConfigService prizeConfigService;
    private final ProposalSourceResolver proposalSourceResolver;

    /**
     * 一次中奖发放一张券
     */
    private static final int QUANTITY_PER_PRIZE = 1;

    @Override
    public ResponseDTO dispatch(PrizeLog prizeLog) {
        log.info(">>>> [优惠券派发策略] 开始发券，提案LogId: {}", prizeLog.getId());

        BigDecimal amount;
        try {
            amount = new BigDecimal(prizeLog.getPrizeValue());
        } catch (NumberFormatException e) {
            log.error("【发奖异常】券面额格式错误: {}", prizeLog.getPrizeValue());
            return ResponseDTO.userErrorParam("券面额格式错误");
        }
        // 券面额参与风控预算核算，必须为正
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("【重大风控拦截】券面额小于等于0！LogId: {}, 面额: {}", prizeLog.getId(), amount);
            return ResponseDTO.userErrorParam("券面额必须大于0");
        }

        PrizeConfig prizeConfig = prizeConfigService.getByPrizeCode(prizeLog.getPrizeCode());
        if (prizeConfig == null) {
            return ResponseDTO.userErrorParam("奖品配置不存在");
        }

        ProposalRecordAddForm req = new ProposalRecordAddForm();
        req.setMemberId(prizeLog.getMemberId());
        req.setPromotionConfigId(prizeConfig.getPromotionConfigId());
        req.setAssetType(PrizeTypeEnum.COUPON.name());
        // 券是实例类资产：光有面额发不出来，必须指明发哪张券模。
        // 由营销侧主动传给账务域，账务域不再反查 prize_log（依赖方向从「账务->营销」翻转为「营销->账务」）。
        // 当前值仍是 prize_code —— 券模表尚未建立，等建好后这里改传券模ID，账务侧代码一行都不用动。
        req.setAssetRef(prizeConfig.getPrizeCode());
        // 展示名随提案下传：账务侧发券时要落 t_member_coupon.coupon_name，
        // 而它不能回头查 prize_log（依赖方向单向）。不传的话那边只能退而用 remark，
        // 结果就是发出去的券全叫「提案生成成功」。
        req.setAssetName(prizeLog.getPrizeName());
        req.setAmount(amount);
        req.setQuantity(QUANTITY_PER_PRIZE);
        req.setSourceType(proposalSourceResolver.resolve(prizeLog.getActivityCode()));
        req.setSourceBizId(prizeLog.getExternalBizNo());
        req.setRemark("参与活动[" + prizeLog.getActivityCode() + "]中奖发放优惠券");

        ResponseDTO result = proposalRecordService.addProposal(req);
        if (!result.getOk()) {
            log.warn("【发券提案未通过】LogId: {}, 原因: {}", prizeLog.getId(), result.getMsg());
        }
        return result;
    }
}
