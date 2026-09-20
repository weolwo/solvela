package solvela.consumer.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import solvela.anno.PrizeStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.exception.BusinessException;
import solvela.lottery.LotteryIssue;
import solvela.lottery.runtime.LotteryIssueLocator;
import solvela.lottery.runtime.TicketIssueService;
import solvela.lottery.runtime.domain.TicketObtainDTO;
import solvela.prize.PrizeLog;

/**
 * 彩票派发策略：中了奖，发一张<b>号码</b>。
 *
 * <h3>它补的是 {@code PrizeTypeEnum.LOTTERY} 上挂了很久的那句「派发策略尚未实现」</h3>
 * 在此之前，彩票玩法是一条<b>断头路</b>：FPE 算号引擎、期号、号码池、中奖规则、
 * 管理端配置页、开奖与核销全都建成了，但没有任何东西能把一张号码<b>发到会员手上</b>。
 * 运营能配、能开奖，会员既拿不到也看不到。
 *
 * <h3>🔴 不走提案，与 {@link MarkerHandler} 同一个理由</h3>
 * 提案是<b>风控 / 预算 / 审批</b>的载体，而一张彩票号码<b>不是资产</b> ——
 * 它不占预算、不动账本，ledger 侧也因此<b>刻意没有</b>对应的 {@code @AssetStrategy}。
 * 硬塞进提案链路只会在 {@code t_proposal_record} 里堆出一堆金额为 0 的空单。
 *
 * <p>真正要动资产的是<b>开奖之后</b>那一步：号码中奖 → {@code LotteryDispatchService}
 * 按中奖规则的 {@code prize_code} 再走一次正常的发奖链路，那时才有提案。
 * <b>发号和兑奖是两件事，中间隔着一次开奖。</b>
 *
 * <h3>配置怎么填</h3>
 * <ul>
 *   <li>{@code prize_code} —— <b>彩票编码</b>（{@code t_lottery_config.lottery_code}）；</li>
 *   <li>{@code prize_value} —— 没有语义。彩票的价值在开奖之后才确定，
 *       配了也不会有任何地方读它。</li>
 * </ul>
 * <b>期号刻意不配</b>：运营配奖品的那一刻，将来要发的那一期多半还不存在。
 * 发号时才由 {@link LotteryIssueLocator} 挑当前在售的那一期。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@RequiredArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.LOTTERY)
@Service
public class LotteryPrizeHandler implements IPrizeHandler {

    private final LotteryIssueLocator lotteryIssueLocator;
    private final TicketIssueService ticketIssueService;

    @Override
    public DispatchOutcome dispatch(PrizeLog prizeLog) {
        String lotteryCode = prizeLog.getPrizeCode();
        if (StringUtils.isBlank(lotteryCode)) {
            // 运营把奖品配成了彩票却没填彩票编码。重试一万次也发不出来 —— 直接判失败，
            // 让它停在「失败」而不是一直重投：fail_reason 是运营唯一能看到的线索
            return DispatchOutcome.failed("彩票奖品未配置彩票编码（prize_code）");
        }

        LotteryIssue issue = lotteryIssueLocator.currentSellable(lotteryCode);
        if (issue == null) {
            /*
             * 🔴 这一条是【可重试】的失败，和上面那条性质完全不同：
             *    上一期刚开完奖、下一期还没开售，中间的空窗是正常运营节奏。
             *    这时候中奖的人应当在下一期开售后拿到号，而不是永远拿不到。
             *
             *    对账任务（PrizeDispatchReconcileJob）会把停在半路的重投一次，
             *    所以这里判失败是安全的 —— 但 fail_reason 必须说人话，
             *    否则运营看到一堆失败会以为系统坏了。
             */
            log.warn("【彩票派发】{} 当前没有在售期号，本次发不出去（等下一期开售后会被对账重投）。LogId: {}",
                    lotteryCode, prizeLog.getId());
            return DispatchOutcome.failed("彩票 " + lotteryCode + " 当前没有在售期号，等开售后重试");
        }

        try {
            /*
             * 幂等键用发奖流水 id。
             *
             * 🔴 这是这条链路防重的【全部】依靠：TicketIssueService 按 requestId 做
             *    SETNX 防重，同一条 prize_log 无论被重投多少次都只会发出一个号。
             *    传 null 的话，对账任务每重投一次就多发一张票 —— 而票是能中奖的。
             *
             *    ⚠️ 那把锁有 TTL（见 LotteryCacheKey.request）。TTL 过后重投会再发一张，
             *    所以它挡的是「短时间内的重复投递」，不是「永久幂等」。
             *    永久那一层在 t_lottery_record 的 uk_issue_ticket 上 —— 但那个键管的是
             *    「同一期不出重号」，不是「同一次中奖不发两张」。
             *    真要补死这个缝，该在 prize_log 上记下发出去的号码（externalBizNo），
             *    发之前先查一次。今天没做，因为对账重投的窗口远小于那个 TTL。
             */
            TicketObtainDTO ticket = ticketIssueService.obtain(
                    lotteryCode, issue.getIssueNo(), prizeLog.getMemberId(),
                    "PRIZE:" + prizeLog.getId());

            log.info(">>>> [彩票派发成功] LogId: {}, 会员: {}, {} 第 {} 期, 号码: {}",
                    prizeLog.getId(), prizeLog.getMemberId(), lotteryCode,
                    ticket.issueNo(), ticket.ticketNumber());
            return DispatchOutcome.success();

        } catch (BusinessException e) {
            /*
             * 领号链路里的预期内拒绝：售罄、停售、限流、玩法下线。
             * 它们都是「现在发不了」而不是「系统坏了」，所以按失败落 fail_reason，
             * 让运营在发奖流水里看得见原因，而不是抛出去变成一条没有上下文的异常日志。
             */
            log.warn("【彩票派发】被拒绝。LogId: {}, {} 第 {} 期, 原因: {}",
                    prizeLog.getId(), lotteryCode, issue.getIssueNo(), e.getMessage());
            return DispatchOutcome.failed(e.getMessage());
        }
    }
}
