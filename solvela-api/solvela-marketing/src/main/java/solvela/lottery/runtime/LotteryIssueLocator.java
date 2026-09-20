package solvela.lottery.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.enums.IssueStatusEnum;
import solvela.lottery.LotteryIssue;
import solvela.lottery.issue.dao.LotteryIssueDao;
import solvela.lottery.issue.manager.LotteryIssueManager;

import java.time.LocalDateTime;

/**
 * 「这个玩法现在能领哪一期」—— 一个问题，一处答案。
 *
 * <h3>为什么要单独一个类</h3>
 * 两个地方要问同一件事，而它们在完全不同的链路上：
 * <ul>
 *   <li><b>派奖</b>（{@code LotteryPrizeHandler}）：奖品配置里只有 {@code lotteryCode}，
 *       发号时才知道该发哪一期 —— 运营配奖品的时候那一期可能还不存在；</li>
 *   <li><b>C 端</b>（{@code LotteryFacade}）：页面要显示「本期截止时间」「我这期有几张」。</li>
 * </ul>
 *
 * <p>两边各写一遍 lambdaQuery 的后果不是报错，是<b>慢慢对不上</b>：
 * 有人给其中一处加了「排除已停售」，另一处没加，于是页面显示能领、点下去说停售了。
 *
 * <h3>🔴 判据必须和 {@code TicketIssueService.requireSellableIssue} 一致</h3>
 * 那边是<b>权威</b>：真正发号时它还会再判一次，不通过就抛。
 * 本类只是<b>提前选一期</b>，让调用方不用自己猜期号 ——
 * 它宽了会让用户看到一个点下去就报错的按钮，窄了则是明明能领却找不到期。
 *
 * <p>没做成「本类给判据、那边调本类」，是因为那边拿到的是<b>指定期号</b>
 * （脚本里写死某一期是合法用法），而本类回答的是「自动挑一期」——
 * 两个问题，共用判据但不共用入口。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Service
@RequiredArgsConstructor
public class LotteryIssueLocator {

    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryIssueDao lotteryIssueDao;

    /**
     * 当前可领号的那一期。没有就返回 {@code null} —— 那不是错误：
     * 上一期开完奖、下一期还没开售，中间本来就有空窗。
     *
     * <p>有多期同时在售时取<b>售卖开始最晚</b>的那一期（最新的一期）。
     * 正常配置下不该出现这种情况，但表结构没禁止它，所以要有一个确定的答案 ——
     * 「不确定取哪一期」会表现为同一个用户两次领号落在不同期，而且没人能解释。
     */
    public LotteryIssue currentSellable(String lotteryCode) {
        if (lotteryCode == null || lotteryCode.isBlank()) {
            return null;
        }
        // 用数据库时钟，不用应用时钟：售卖窗口是库里的时间列，
        // 两边时钟有偏差时，跨临界的那一刻会出现「查得到但领不了」
        LocalDateTime now = lotteryIssueDao.selectDbNow();
        return lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                // 只有待开奖的期能领号 —— 与 requireSellableIssue 同一个判据
                .eq(LotteryIssue::getStatus, IssueStatusEnum.WAIT)
                .le(LotteryIssue::getSaleStartTime, now)
                .ge(LotteryIssue::getSaleEndTime, now)
                .orderByDesc(LotteryIssue::getSaleStartTime)
                .last("limit 1")
                .one();
    }
}
