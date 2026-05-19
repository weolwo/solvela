package net.lab1024.sa.consumer.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.PrizeStrategy;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.enums.EventTypeEnum;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordAddForm;
import net.lab1024.sa.risk.proposal.service.ProposalRecordService;
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
            req.setMemberName(prizeLog.getMemberName());
            req.setPromotionConfigId(prizeLog.getPromotionConfigId());
            req.setPromotionValue(amount);
            req.setSourceType(EventTypeEnum.LOTTERY_DRAW.name()); // 业务分类：彩票奖励
            req.setSourceBizId(prizeLog.getExternalBizNo()); // 极度关键：原彩票记录ID
            req.setRemark("参与活动[" + prizeLog.getActivityCode() + "]中奖发放");

            // 3. 调用底层的资金账户服务进行加钱
            proposalRecordService.addProposal(req);

            return ResponseDTO.ok();
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
