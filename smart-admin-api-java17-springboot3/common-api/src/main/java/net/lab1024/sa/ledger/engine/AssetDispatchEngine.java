package net.lab1024.sa.ledger.engine;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartStringUtil;
import net.lab1024.sa.ledger.handler.IAssetHandler;
import net.lab1024.sa.ledger.strategy.AssetStrategyFactory;
import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
import net.lab1024.sa.risk.proposal.dao.ProposalRecordDao;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.springframework.stereotype.Service;

/**
 * 资产分发引擎：把「待执行」的提案真正下发到各资产域
 *
 * 状态机（对齐 t_proposal_record.status 的字典）：
 * 30 待执行 -> 40 执行中 -> 50 成功 / 70 彻底失败
 * 其中 30 -> 40 用条件更新做并发闸门，抢不到的直接退出，保证同一提案只被执行一次。
 *
 * @Author weolwo
 * @Date 2026-04-19
 */
@AllArgsConstructor
@Slf4j
@Service
public class AssetDispatchEngine {

    private final ProposalRecordDao proposalRecordDao;

    private final AssetStrategyFactory strategyFactory; // 资产策略工厂

    /**
     * 提案状态：对齐 t_proposal_record.status 注释，避免散落魔法值
     */
    private static final int STATUS_PENDING_EXECUTE = 30;
    private static final int STATUS_EXECUTING = 40;
    private static final int STATUS_SUCCESS = 50;
    private static final int STATUS_FAILED = 70;

    /**
     * 对齐 t_proposal_record.remark 的列长度：异常 message 直接入库会抛 Data too long，
     * 而这句正是失败兜底，一抛就把状态永远留在 40(执行中)
     */
    private static final int REMARK_MAX_LENGTH = 255;

    /**
     * 核心执行入口 (支持异步调用)
     */
    public void execute(ProposalRecord proposal, PromotionConfig config) {
        log.info(">>>> [资产分发引擎] 开始执行提案, 提案ID: {}, 资产类型: {}", proposal.getId(), config.getPrizeType());

        try {
            // 1. 推进状态：30(待执行) -> 40(执行中)。条件更新即并发闸门，抢不到说明别人已在执行或已完结
            int rows = proposalRecordDao.updateStatus(proposal.getId(), STATUS_PENDING_EXECUTE, STATUS_EXECUTING);
            if (rows == 0) {
                log.warn("【引擎拦截】提案正在执行中或已完结，忽略本次调用。提案ID: {}", proposal.getId());
                return;
            }

            // 2. 获取具体资产类型的执行策略 (如: SCORE, BALANCE, COUPON, PHYSICAL)
            IAssetHandler handler = strategyFactory.getHandler(config.getPrizeType());

            // 3. 极简下发：只管抛给下层，拿到成功/失败的结果
            ResponseDTO responseDTO = handler.dispatch(proposal);

            // 4. 闭环：根据结果落终态。失败原因写进 remark，运营/研发能直接从提案列表看出卡在哪
            if (responseDTO.getOk()) {
                proposalRecordDao.updateStatusAndRemark(proposal.getId(), STATUS_SUCCESS, "资产下发成功");
            } else {
                proposalRecordDao.updateStatusAndRemark(proposal.getId(), STATUS_FAILED,
                        SmartStringUtil.truncate("资产下发失败：" + responseDTO.getMsg(), REMARK_MAX_LENGTH));
            }

        } catch (Exception e) {
            log.error("【引擎致命异常】资产执行发生未知错误, 提案ID: {}", proposal.getId(), e);
            // 状态已经是 40(执行中)，不落终态就会永远卡住，必须兜底改成 70 让它可被排查/重试
            try {
                proposalRecordDao.updateStatusAndRemark(proposal.getId(), STATUS_FAILED,
                        SmartStringUtil.truncate("系统执行异常: " + e.getMessage(), REMARK_MAX_LENGTH));
            } catch (Exception ex) {
                log.error("【引擎状态回写失败】提案ID: {} 将停留在 40(执行中)，请人工核对", proposal.getId(), ex);
            }
        }
    }
}
