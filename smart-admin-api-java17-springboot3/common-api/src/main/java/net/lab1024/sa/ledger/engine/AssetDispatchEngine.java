package net.lab1024.sa.ledger.engine;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.ledger.handler.IAssetHandler;
import net.lab1024.sa.ledger.strategy.AssetStrategyFactory;
import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
import net.lab1024.sa.risk.proposal.dao.ProposalRecordDao;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Slf4j
@Service
public class AssetDispatchEngine {

    private final ProposalRecordDao proposalRecordDao;

    private final AssetStrategyFactory strategyFactory; // 资产策略工厂

    /**
     * 核心执行入口 (支持异步调用)
     */
    public void execute(ProposalRecord proposal, PromotionConfig config) {
        log.info(">>>> [资产分发引擎] 开始执行提案, 提案ID: {}, 资产类型: {}", proposal.getId(), config.getPrizeType());

        try {
            // 1. 推进状态：30(待执行) -> 40(执行中)。利用数据库行锁防并发重入！
            int rows = 0;//proposalRecordDao.updateStatus(proposal.getId(), 30, 40);
            if (rows == 0) {
                log.warn("【引擎拦截】提案正在执行中或已完结，忽略本次调用。提案ID: {}", proposal.getId());
                return;
            }

            // 2. 获取具体资产类型的执行策略 (如: COUPON, PHYSICAL, BALANCE)
            IAssetHandler handler = strategyFactory.getHandler(config.getPrizeType());

            // 3. 极简下发：只管抛给下层，拿到成功/失败的结果
            boolean isSuccess = handler.dispatch(proposal);

            // 4. 闭环：根据结果更新最终提案状态
            if (isSuccess) {
                // proposalRecordDao.updateStatusAndRemark(proposal.getId(), 50, "资产下发成功");
                // TODO: 可以在这里触发 t_promotion_config 的 used_amount/used_quota 的真实扣减
            } else {
                //proposalRecordDao.updateStatusAndRemark(proposal.getId(), 70, "资产下发失败，请联系研发排查");
            }

        } catch (Exception e) {
            log.error("【引擎致命异常】资产执行发生未知错误, 提案ID: {}", proposal.getId(), e);
            //proposalRecordDao.updateStatusAndRemark(proposal.getId(), 70, "系统执行异常: " + e.getMessage());
        }
    }
}