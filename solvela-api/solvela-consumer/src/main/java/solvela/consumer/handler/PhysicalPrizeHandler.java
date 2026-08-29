package solvela.consumer.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.PrizeStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.exception.BusinessException;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.prize.PrizeLog;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;
import solvela.risk.proposal.service.ProposalRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 实物派发策略
 * <p>
 * 补这个类之前，实物奖是一颗审批流上的地雷：实物一般配 approve_mode=1，
 * 平时卡在审批前不会走 doDispatch，所以压测一路无异常；
 * 可运营在审批工作台点「通过」的那一刻，就会抛「不支持的奖品类型: PHYSICAL」。
 * <p>
 * 与其它策略同构：只负责生成提案，真正的物流单由 ledger 层的 PhysicalAssetHandler 落。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Slf4j
@AllArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.PHYSICAL)
@Service
public class PhysicalPrizeHandler implements IPrizeHandler {

    private final ProposalRecordService proposalRecordService;
    private final PrizeConfigService prizeConfigService;
    private final ProposalSourceResolver proposalSourceResolver;

    /**
     * 一次中奖发放一件
     */
    private static final int QUANTITY_PER_PRIZE = 1;

    @Override
    public DispatchOutcome dispatch(PrizeLog prizeLog) {
        log.info(">>>> [实物派发策略] 开始生成实物提案，LogId: {}", prizeLog.getId());

        BigDecimal amount;
        try {
            amount = new BigDecimal(prizeLog.getPrizeValue());
        } catch (NumberFormatException e) {
            log.error("【发奖异常】实物价值格式错误: {}", prizeLog.getPrizeValue());
            return DispatchOutcome.failed("实物价值格式错误");
        }
        // 实物价值只用于风控预算口径（如 iPhone 记 7999），必须为正
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("【重大风控拦截】实物价值小于等于0！LogId: {}, 价值: {}", prizeLog.getId(), amount);
            return DispatchOutcome.failed("实物价值必须大于0");
        }

        PrizeConfig prizeConfig = prizeConfigService.getByPrizeCode(prizeLog.getPrizeCode());
        if (prizeConfig == null) {
            return DispatchOutcome.failed("奖品配置不存在");
        }

        ProposalRecordAddCommand req = new ProposalRecordAddCommand();
        req.setMemberId(prizeLog.getMemberId());
        req.setPromotionConfigId(prizeConfig.getPromotionConfigId());
        req.setAssetType(PrizeTypeEnum.PHYSICAL.name());
        // 实物是实例类资产：必须指明发哪个 SKU。当前用 prize_code 占位，
        // 等实物与 t_goods 的映射建立后改传商品 SKU
        req.setAssetRef(prizeConfig.getPrizeCode());
        // 与券同理：实物也是实例类资产，履约单要展示商品名，而账务侧不能回查营销域
        req.setAssetName(prizeLog.getPrizeName());
        req.setAmount(amount);
        req.setQuantity(QUANTITY_PER_PRIZE);
        req.setSourceType(proposalSourceResolver.resolve(prizeLog.getActivityCode()));
        req.setSourceBizId(prizeLog.getExternalBizNo());
        req.setRemark("参与活动[" + prizeLog.getActivityCode() + "]中奖发放实物：" + prizeLog.getPrizeName());

        // 风控拦截 / 资产配置异常由 addProposal 抛 BusinessException，必须如实上报：
        // 吞掉失败会让 PrizeDispatchHandler 把一条根本没入账的记录标成「发货成功」
        try {
            proposalRecordService.addProposal(req);
            return DispatchOutcome.success();
        } catch (BusinessException e) {
            log.warn("【实物提案未通过】LogId: {}, 原因: {}", prizeLog.getId(), e.getMessage());
            return DispatchOutcome.failed(e.getMessage());
        }
    }
}
