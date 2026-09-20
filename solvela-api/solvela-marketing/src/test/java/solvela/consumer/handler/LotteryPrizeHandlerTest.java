package solvela.consumer.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.dispatch.DispatchOutcome;
import solvela.exception.BusinessException;
import solvela.lottery.LotteryIssue;
import solvela.lottery.runtime.LotteryIssueLocator;
import solvela.lottery.runtime.TicketIssueService;
import solvela.lottery.runtime.domain.TicketObtainDTO;
import solvela.prize.PrizeLog;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 彩票派发策略。
 *
 * <h3>这个类补的是挂了很久的那句「派发策略尚未实现」</h3>
 * 在它之前，彩票玩法是一条断头路：引擎、期号、号码池、中奖规则、管理端配置页、
 * 开奖与核销全都建成了，但没有任何东西能把一张号码发到会员手上。
 *
 * <h3>🔴 这里守的三件事都是「不做会静默出错」</h3>
 * ① <b>幂等键必须传</b> —— 派发失败会被对账任务重投，不传就是每重投一次多发一张票，
 *    而票是能中奖的；
 * ② <b>没有在售期时判失败而不是抛异常</b> —— 空窗是正常运营节奏，
 *    抛出去会变成一条没有上下文的异常日志，而判失败会留下运营看得懂的 fail_reason；
 * ③ <b>领号被拒要落原因</b> —— 售罄 / 停售 / 限流都是「现在发不了」，不是系统坏了。
 *
 * @Date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LotteryPrizeHandlerTest {

    private static final String LOTTERY_CODE = "RMAAUK45TG";
    private static final String ISSUE_NO = "2026_MID_01";
    private static final Long MEMBER_ID = 1001L;
    private static final Long PRIZE_LOG_ID = 55L;

    @Mock
    private LotteryIssueLocator lotteryIssueLocator;
    @Mock
    private TicketIssueService ticketIssueService;

    private LotteryPrizeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LotteryPrizeHandler(lotteryIssueLocator, ticketIssueService);

        LotteryIssue issue = new LotteryIssue();
        issue.setLotteryCode(LOTTERY_CODE);
        issue.setIssueNo(ISSUE_NO);
        when(lotteryIssueLocator.currentSellable(LOTTERY_CODE)).thenReturn(issue);
        when(ticketIssueService.obtain(anyString(), anyString(), anyLong(), any()))
                .thenReturn(new TicketObtainDTO(LOTTERY_CODE, ISSUE_NO, "48213", 42L,
                        "sign", "2026-09-18 10:00:00"));
    }

    private PrizeLog prizeLog(String prizeCode) {
        PrizeLog log = new PrizeLog();
        log.setId(PRIZE_LOG_ID);
        log.setMemberId(MEMBER_ID);
        log.setPrizeCode(prizeCode);
        log.setPrizeName("彩票一张");
        log.setActivityCode("ACT001");
        return log;
    }

    @Test
    @DisplayName("正常派发：按当前在售期发一个号")
    void 正常派发() {
        DispatchOutcome outcome = handler.dispatch(prizeLog(LOTTERY_CODE));

        assertTrue(outcome.ok());
        verify(ticketIssueService).obtain(eq(LOTTERY_CODE), eq(ISSUE_NO), eq(MEMBER_ID), any());
    }

    @Test
    @DisplayName("🔴 幂等键必须带上发奖流水 id —— 不带的话对账每重投一次就多发一张票")
    void 幂等键用发奖流水id() {
        handler.dispatch(prizeLog(LOTTERY_CODE));

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(ticketIssueService).obtain(anyString(), anyString(), anyLong(), requestId.capture());

        /*
         * TicketIssueService 按 requestId 做 SETNX 防重，这是这条链路防重的【全部】依靠。
         * 传 null 的话 checkNotDuplicate 直接 return —— 不报错，只是不防重了，
         * 而多发出去的票是能中奖的。
         */
        assertNotNull(requestId.getValue(), "requestId 为 null 等于关掉了防重");
        assertTrue(requestId.getValue().contains(String.valueOf(PRIZE_LOG_ID)),
                "幂等键里必须含发奖流水 id，否则同一次中奖的两次重投会被当成两次不同的领号："
                        + requestId.getValue());
    }

    @Test
    @DisplayName("🔴 没配彩票编码：判失败并写原因，不要抛异常也不要判成功")
    void 没配彩票编码() {
        DispatchOutcome outcome = handler.dispatch(prizeLog("  "));

        assertAll(
                () -> assertFalse(outcome.ok()),
                () -> assertTrue(outcome.failReason().contains("prize_code"),
                        "fail_reason 要说清缺的是什么，那是运营唯一能看到的线索：" + outcome.failReason()),
                // 重试一万次也发不出来，连引擎都不该调
                () -> verify(ticketIssueService, never()).obtain(anyString(), anyString(), anyLong(), any()));
    }

    @Test
    @DisplayName("🔴 当前没有在售期：判失败（可被对账重投），不是抛异常")
    void 没有在售期() {
        when(lotteryIssueLocator.currentSellable(LOTTERY_CODE)).thenReturn(null);

        DispatchOutcome outcome = handler.dispatch(prizeLog(LOTTERY_CODE));

        /*
         * 上一期开完奖、下一期还没开售，中间的空窗是正常运营节奏。
         * 这时候中奖的人应当在下一期开售后拿到号 —— 判失败之后
         * PrizeDispatchReconcileJob 会把它重投出去。
         *
         * 抛异常的话会变成一条没有上下文的异常日志，而且运营在发奖流水里看不到原因。
         */
        assertAll(
                () -> assertFalse(outcome.ok()),
                () -> assertTrue(outcome.failReason().contains("在售期"),
                        "原因要说人话，不然运营会以为系统坏了：" + outcome.failReason()),
                () -> verify(ticketIssueService, never()).obtain(anyString(), anyString(), anyLong(), any()));
    }

    @Test
    @DisplayName("🔴 领号被拒（售罄/停售/限流）：原样落进 fail_reason")
    void 领号被拒() {
        when(ticketIssueService.obtain(anyString(), anyString(), anyLong(), any()))
                .thenThrow(new BusinessException("本期号码已售罄"));

        DispatchOutcome outcome = handler.dispatch(prizeLog(LOTTERY_CODE));

        assertAll(
                () -> assertFalse(outcome.ok()),
                // 引擎给的就是人话，别在这里换一套说法 —— 同一件事不该有两种措辞
                () -> assertEquals("本期号码已售罄", outcome.failReason()));
    }

    @Test
    @DisplayName("不生成提案 —— 一张号码不是资产，这一点和标记类一致")
    void 不走提案() {
        DispatchOutcome outcome = handler.dispatch(prizeLog(LOTTERY_CODE));

        /*
         * 提案是风控/预算/审批的载体。号码不动账，硬塞进提案链路只会在
         * t_proposal_record 里堆出一堆金额为 0 的空单，把真正要盯的预算口径冲淡。
         *
         * 判据就是 proposalId 为空 —— 走了提案的 handler 会把 id 带回来。
         */
        assertTrue(outcome.ok());
        assertEquals(null, outcome.proposalId(),
                "彩票派发不该生成提案：一张号码不是资产，ledger 侧也没有对应的 @AssetStrategy");
    }
}
