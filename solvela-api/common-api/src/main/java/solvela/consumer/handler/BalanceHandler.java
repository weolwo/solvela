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
 * 现金派发类
 */
@Slf4j
@AllArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.BALANCE)
@Service
public class BalanceHandler implements IPrizeHandler {

    private final ProposalRecordService proposalRecordService;
    private final PrizeConfigService prizeConfigService;
    private final ProposalSourceResolver proposalSourceResolver;

    /**
     * 一次中奖发放一份
     */
    private static final int QUANTITY_PER_PRIZE = 1;


    @Override
    public ResponseDTO dispatch(PrizeLog prizeLog) {
        log.info(">>>> [余额派发策略] 开始派发奖金，提案LogId: {}", prizeLog.getId());

        try {
            // 1. 金额参数强校验 (绝对不允许发负数或 0 的余额，防黑客改包)
            BigDecimal amount = new BigDecimal(prizeLog.getPrizeValue());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("【重大风控拦截】发奖金额小于等于0！LogId: {}, 金额: {}", prizeLog.getId(), amount);
                return ResponseDTO.userErrorParam("派发金额不能为0");
            }

            // 2. 构建资金入账请求 (把营销单号传给财务底层，做跨域幂等)
            ProposalRecordAddForm req = new ProposalRecordAddForm();
            PrizeConfig prizeConfig = prizeConfigService.getByPrizeCode(prizeLog.getPrizeCode());
            if (prizeConfig == null) {
                return ResponseDTO.userErrorParam("奖品配置不存在");
            }
            req.setMemberId(prizeLog.getMemberId());
            req.setPromotionConfigId(prizeConfig.getPromotionConfigId());
            // 现金是值类资产：金额即全部信息，assetRef 留空
            req.setAssetType(PrizeTypeEnum.BALANCE.name());
            req.setAmount(amount);
            req.setQuantity(QUANTITY_PER_PRIZE);
            req.setSourceType(proposalSourceResolver.resolve(prizeLog.getActivityCode()));
            req.setSourceBizId(prizeLog.getExternalBizNo()); // 极度关键：原彩票记录ID
            req.setRemark("参与活动[" + prizeLog.getActivityCode() + "]中奖发放");

            // 3. 调用底层的资金账户服务进行加钱
            // 提案被风控拦截 / 资产配置异常时返回的是非 ok，必须如实上抛：
            // 之前这里丢弃了返回值直接 return ok，会把根本没入账的记录标成「发货成功」
            ResponseDTO result = proposalRecordService.addProposal(req);
            if (!result.getOk()) {
                log.warn("【发奖提案未通过】LogId: {}, 原因: {}", prizeLog.getId(), result.getMsg());
            }
            return result;
        } catch (NumberFormatException e) {
            log.error("【发奖异常】金额格式错误: {}", prizeLog.getPrizeValue());
            return ResponseDTO.userErrorParam("金额格式错误");
        } catch (Exception e) {
            // 捕获底层资金服务的业务异常（如账户被冻结等）
            log.error("【发奖异常】调用资金底层服务失败: {}", e.getMessage());
            throw e; // 这里建议抛出去，让上一层的 PrizeDispatchHandler 捕获并记录 fail_reason
        }
    }
}
