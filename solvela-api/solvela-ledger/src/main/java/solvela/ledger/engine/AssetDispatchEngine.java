package solvela.ledger.engine;

import solvela.enums.PrizeDispatchStatusEnum;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.dispatch.DispatchOutcome;
import solvela.base.util.SolvelaStringUtil;
import solvela.ledger.strategy.AssetStrategyFactory;
import solvela.member.api.PrizeDispatchResultMessage;
import solvela.risk.promotionconfig.dao.PromotionConfigDao;
import solvela.risk.PromotionConfig;
import solvela.risk.proposal.dao.ProposalRecordDao;
import solvela.risk.ProposalRecord;
import solvela.risk.spi.AssetDispatcher;
import solvela.observe.DegradePoint;
import solvela.base.observe.Degradations;
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
     * 发奖记录 fail_reason 的列长度
     */
    private static final int FAIL_REASON_MAX_LENGTH = 128;

    /**
     * 核心执行入口：<b>抢闸门 → 扣预算 → 下发 → 落终态</b>，四步都在同一个 try 里。
     *
     * <p>🔴 try 必须罩住前两步，不能只罩下发：{@code claimForExecution} 或
     * {@code deductBudget} 自己抛异常时，提案要么还停在 30、要么已经进了 40，
     * 不兜底就会永远卡在那里没人管。{@code budgetDeducted} 让兜底知道「该不该还预算」——
     * 还没扣就还，等于把没占用的额度凭空补进去；{@code claimed} 让兜底知道「该从哪个状态落失败」，
     * 见 {@link ProposalRecordDao#markFailedUnclaimed}。
     */
    @Override
    public void execute(ProposalRecord proposal, PromotionConfig config) {
        log.info(">>>> [资产分发引擎] 开始执行提案, 提案ID: {}, 单号: {}, 资产类型: {}",
                proposal.getId(), proposal.getTradeNo(), proposal.getAssetType());

        BigDecimal amount = proposal.getAmount() == null ? BigDecimal.ZERO : proposal.getAmount();
        // 数量参与 used_quota 扣减：券/实物靠它卡总量，值类资产恒为 1
        int quantity = proposal.getQuantity() == null ? DEFAULT_QUANTITY : proposal.getQuantity();
        boolean claimed = false;
        boolean budgetDeducted = false;

        try {
            if (!claimForExecution(proposal)) {
                return;
            }
            claimed = true;
            if (!deductBudget(proposal, config, amount, quantity)) {
                markFailed(proposal, true, "预算或发放数量已耗尽");
                return;
            }
            budgetDeducted = true;

            settle(proposal, config, amount, quantity, dispatch(proposal));

        } catch (Exception e) {
            log.error("【引擎致命异常】资产执行发生未知错误, 提案ID: {}", proposal.getId(), e);
            if (budgetDeducted) {
                releaseBudgetQuietly(config, amount, quantity);
            }
            // 不落终态就会永远卡在 30 / 40，必须兜底改成 70 让它可被排查/重试
            markFailed(proposal, claimed, "系统执行异常: " + e.getMessage());
        }
    }

    /**
     * 抢执行权：30(待执行) -> 40(执行中)。
     *
     * <p>这次条件更新<b>就是</b>并发闸门。抢不到说明别人已在执行或已完结，
     * 直接退出而不是重试 —— 重试等于同一笔奖被发两次。
     */
    private boolean claimForExecution(ProposalRecord proposal) {
        int rows = proposalRecordDao.claimForExecution(proposal.getId());
        if (rows == 0) {
            log.warn("【引擎拦截】提案正在执行中或已完结，忽略本次调用。提案ID: {}", proposal.getId());
            return false;
        }
        return true;
    }

    /**
     * 预算硬限流：把「够不够」压进 UPDATE 的 WHERE，一条 SQL 完成校验 + 扣减。
     *
     * <p>风控链上的 {@code GlobalBudgetRiskFilter} 是「先读后判」的弱校验，
     * 高并发下读到的余量早已过期 —— <b>真正防超发的是这里的条件更新</b>。
     * 必须在动账之前扣，扣不动就别发。
     */
    private boolean deductBudget(ProposalRecord proposal, PromotionConfig config,
                                 BigDecimal amount, int quantity) {
        if (promotionConfigDao.deductBudget(config.getId(), amount, quantity) > 0) {
            return true;
        }
        log.warn("【预算不足】提案ID: {}, 优惠配置: {}, 申请额: {}, 数量: {}",
                proposal.getId(), config.getId(), amount, quantity);
        return false;
    }

    /**
     * 下发。按<b>提案自带的</b>资产类型选策略，不读 {@code config.getPrizeType()} ——
     * 那等于「为了知道发什么，先得加载预算配置」，把路由和预算耦在一起。
     *
     * <p>引擎不认识任何一种具体资产，加一种资产不用改这里。
     */
    private DispatchOutcome dispatch(ProposalRecord proposal) {
        return strategyFactory.getHandler(proposal.getAssetType()).dispatch(proposal);
    }

    /**
     * 闭环：无论成败都要落终态。
     *
     * <p>失败原因写进 remark —— 运营和研发都只看得到提案列表，不写的话
     * 「卡在哪」这个问题只能去翻日志。失败还要把预算还回去，
     * 否则预算只减不加，跑一段时间水位就虚高到发不出奖。
     */
    private void settle(ProposalRecord proposal, PromotionConfig config,
                        BigDecimal amount, int quantity, DispatchOutcome outcome) {
        if (outcome.ok()) {
            // 走到这里的一定是抢到执行权的那个线程，没有别人会动 40 的提案，影响 0 行只可能是有人手工改了库。
            // 钱已经出去了，发奖侧照样要知道成功，所以不因 0 行而不通知
            if (proposalRecordDao.markSucceeded(proposal.getId(), "资产下发成功") == 0) {
                log.error("【引擎状态回写未命中】资产已下发，但提案ID: {} 已不是执行中，成功态没写进去，请人工核对",
                        proposal.getId());
            }
            syncPrizeLog(proposal, PrizeDispatchStatusEnum.SUCCESS, null);
            return;
        }
        releaseBudgetQuietly(config, amount, quantity);
        markFailed(proposal, true, "资产下发失败：" + outcome.failReason());
    }

    /**
     * 落失败终态：提案 -> 70，同时把发奖记录也标成失败
     * 两处回写各自兜异常，任何一处挂了都不能影响另一处，否则又会出现「一半状态是对的」
     *
     * @param claimed 本线程是否已抢到执行权。抢到了从 40 落，没抢到（抢的那一步抛了异常）只从 30 落
     */
    private void markFailed(ProposalRecord proposal, boolean claimed, String reason) {
        String remark = SolvelaStringUtil.truncate(reason, REMARK_MAX_LENGTH);
        try {
            int rows = claimed
                    ? proposalRecordDao.markFailed(proposal.getId(), remark)
                    : proposalRecordDao.markFailedUnclaimed(proposal.getId(), remark);
            if (rows == 0) {
                /*
                 * 提案已不在本线程以为的状态，终态归别人写，发奖侧的结果也归它通知。
                 * 这里再发一条失败过去，发奖记录「先到先写」，会把随后真正的成功挡在外面 ——
                 * 钱出去了，发奖记录却是失败。没抢到执行权时停在 40 的那种，由卡单扫描报出来。
                 */
                log.warn("【引擎状态回写未命中】提案ID: {} 已不是{}，不落失败、不通知发奖侧。原因: {}",
                        proposal.getId(), claimed ? "执行中" : "待执行", remark);
                return;
            }
        } catch (Exception e) {
            Degradations.record(DegradePoint.PROPOSAL_STATUS_WRITEBACK_FAILED);
            log.error("【引擎状态回写失败】提案ID: {} 将停留在 30(待执行) 或 40(执行中)，请人工核对", proposal.getId(), e);
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
            Degradations.record(DegradePoint.BUDGET_RELEASE_FAILED);
            log.error("【预算回滚失败】优惠配置: {}, 金额: {}, 数量: {}，预算水位将虚高，请人工核对",
                    config.getId(), amount, quantity, e);
        }
    }
}
