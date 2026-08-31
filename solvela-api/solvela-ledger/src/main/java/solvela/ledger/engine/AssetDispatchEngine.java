package solvela.ledger.engine;

import solvela.enums.ProposalStatusEnum;
import solvela.enums.PrizeDispatchStatusEnum;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.dispatch.DispatchOutcome;
import solvela.base.util.SolvelaStringUtil;
import solvela.ledger.handler.IAssetHandler;
import solvela.ledger.strategy.AssetStrategyFactory;
import solvela.member.api.PrizeDispatchResultMessage;
import solvela.risk.promotionconfig.dao.PromotionConfigDao;
import solvela.risk.PromotionConfig;
import solvela.risk.proposal.dao.ProposalRecordDao;
import solvela.risk.ProposalRecord;
import solvela.risk.spi.AssetDispatcher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 资产分发引擎：把「待执行」的提案真正下发到各资产域
 * <p>
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
public class AssetDispatchEngine implements AssetDispatcher {

    private final ProposalRecordDao proposalRecordDao;

    private final PromotionConfigDao promotionConfigDao;


    /** 派发结果的出口：单体直接改库，会员服务发消息。见 PrizeDispatchResultPublisher */
    private final PrizeDispatchResultPublisher dispatchResultPublisher;

    private final AssetStrategyFactory strategyFactory; // 资产策略工厂

    /**
     * 一条提案占用一个发放名额（used_quota）。数量型资产（券/实物）靠它卡总量
     */
    private static final int DEFAULT_QUANTITY = 1;

    /**
     * 对齐 t_proposal_record.remark 的列长度：异常 message 直接入库会抛 Data too long，
     * 而这句正是失败兜底，一抛就把状态永远留在 40(执行中)
     */
    private static final int REMARK_MAX_LENGTH = 255;

    /**
     * 发奖记录状态（对齐 t_prize_log.status）与其 fail_reason 列长度
     */
    private static final int PRIZE_LOG_SUCCESS = 1;
    private static final int PRIZE_LOG_FAILED = 2;
    private static final int FAIL_REASON_MAX_LENGTH = 128;

    /**
     * 核心执行入口 (支持异步调用)
     */
    @Override
    public void execute(ProposalRecord proposal, PromotionConfig config) {
        log.info(">>>> [资产分发引擎] 开始执行提案, 提案ID: {}, 单号: {}, 资产类型: {}",
                proposal.getId(), proposal.getTradeNo(), proposal.getAssetType());

        BigDecimal amount = proposal.getAmount() == null ? BigDecimal.ZERO : proposal.getAmount();
        // 数量参与 used_quota 扣减：券/实物靠它卡总量，值类资产恒为 1
        int quantity = proposal.getQuantity() == null ? DEFAULT_QUANTITY : proposal.getQuantity();
        // 记录预算是否真的扣成功：异常兜底时只有扣过才允许回滚，否则会把没占用的预算凭空还回去
        boolean budgetDeducted = false;

        try {
            // 1. 推进状态：30(待执行) -> 40(执行中)。条件更新即并发闸门，抢不到说明别人已在执行或已完结
            int rows = proposalRecordDao.updateStatus(proposal.getId(), ProposalStatusEnum.PENDING_EXECUTE, ProposalStatusEnum.EXECUTING);
            if (rows == 0) {
                log.warn("【引擎拦截】提案正在执行中或已完结，忽略本次调用。提案ID: {}", proposal.getId());
                return;
            }

            // 2. 预算硬限流：把「够不够」压进 UPDATE 的 WHERE，一条 SQL 完成校验+扣减。
            //    风控链上的 GlobalBudgetRiskFilter 是「先读后判」的弱校验，高并发下读到的余量早已过期，
            //    真正防超发的是这里的条件更新。必须在动账之前扣，扣不动就别发。
            if (promotionConfigDao.deductBudget(config.getId(), amount, quantity) == 0) {
                log.warn("【预算不足】提案ID: {}, 优惠配置: {}, 申请额: {}, 数量: {}",
                        proposal.getId(), config.getId(), amount, quantity);
                markFailed(proposal, "预算或发放数量已耗尽");
                return;
            }
            budgetDeducted = true;

            // 3. 按**提案自带的**资产类型选执行策略。
            //    以前是读 config.getPrizeType()，等于「为了知道发什么，先得加载预算配置」——
            //    路由和预算是两件事，不该耦合。提案自带 assetType 后引擎自洽了。
            IAssetHandler handler = strategyFactory.getHandler(proposal.getAssetType());

            // 4. 极简下发：只管抛给下层，拿到成功/失败的结果
            DispatchOutcome outcome = handler.dispatch(proposal);

            // 5. 闭环：根据结果落终态。失败原因写进 remark，运营/研发能直接从提案列表看出卡在哪
            if (outcome.ok()) {
                proposalRecordDao.updateStatusAndRemark(proposal.getId(), ProposalStatusEnum.SUCCESS, "资产下发成功");
                syncPrizeLog(proposal, PrizeDispatchStatusEnum.SUCCESS, null);
            } else {
                // 没发出去就得把预算还回去，否则预算只减不加，跑一段时间水位就虚高到发不出奖
                releaseBudgetQuietly(config, amount, quantity);
                markFailed(proposal, "资产下发失败：" + outcome.failReason());
            }

        } catch (Exception e) {
            log.error("【引擎致命异常】资产执行发生未知错误, 提案ID: {}", proposal.getId(), e);
            if (budgetDeducted) {
                releaseBudgetQuietly(config, amount, quantity);
            }
            // 状态已经是 40(执行中)，不落终态就会永远卡住，必须兜底改成 70 让它可被排查/重试
            markFailed(proposal, "系统执行异常: " + e.getMessage());
        }
    }

    /**
     * 落失败终态：提案 -> 70，同时把发奖记录也标成失败
     * 两处回写各自兜异常，任何一处挂了都不能影响另一处，否则又会出现「一半状态是对的」
     */
    private void markFailed(ProposalRecord proposal, String reason) {
        String remark = SolvelaStringUtil.truncate(reason, REMARK_MAX_LENGTH);
        try {
            proposalRecordDao.updateStatusAndRemark(proposal.getId(), ProposalStatusEnum.FAILED, remark);
        } catch (Exception e) {
            log.error("【引擎状态回写失败】提案ID: {} 将停留在 40(执行中)，请人工核对", proposal.getId(), e);
        }
        syncPrizeLog(proposal, PrizeDispatchStatusEnum.FAIL, remark);
    }

    /**
     * 把派发结果交回给发奖侧。
     *
     * <p>2026-08-30 之前这里是<b>直接 update t_prize_log</b>，注释里写着「分层上略有妥协：
     * ledger 的引擎去改 prize 域的流水」。拆成两个服务之后那条路不通了 ——
     * 发奖流水在营销服务，一个服务不能写另一个服务的表。
     *
     * <p>现在交给 {@link PrizeDispatchResultPublisher}：单体形态下它仍然直接更新（行为不变），
     * 会员服务里换成发消息。<b>本方法不再关心结果落到哪</b>。
     *
     * <p>关联键仍是 {@code external_biz_no == source_biz_id} 这条既定契约。
     */
    private void syncPrizeLog(ProposalRecord proposal, PrizeDispatchStatusEnum status, String failReason) {
        dispatchResultPublisher.publish(new PrizeDispatchResultMessage(
                // 消息 id 用来源单号加状态后缀：同一笔奖的成功与失败是两条不同的消息，
                // 而重复投递同一条时消费方能靠它判重
                proposal.getSourceBizId() + ":" + status.name(),
                proposal.getSourceBizId(),
                proposal.getId(),
                status == PrizeDispatchStatusEnum.SUCCESS,
                SolvelaStringUtil.truncate(failReason, FAIL_REASON_MAX_LENGTH)));
    }

    /**
     * 预算回滚：本身失败也不能再往外抛，否则会盖掉真正的失败原因、还会让状态停在 40
     */
    private void releaseBudgetQuietly(PromotionConfig config, BigDecimal amount, int quantity) {
        try {
            // 必须按实际扣掉的数量还，扣 3 还 1 会让 used_quota 只增不减
            promotionConfigDao.releaseBudget(config.getId(), amount, quantity);
        } catch (Exception e) {
            log.error("【预算回滚失败】优惠配置: {}, 金额: {}, 数量: {}，预算水位将虚高，请人工核对",
                    config.getId(), amount, quantity, e);
        }
    }
}
