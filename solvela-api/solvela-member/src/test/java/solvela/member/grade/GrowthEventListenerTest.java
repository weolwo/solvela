package solvela.member.grade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
import solvela.member.grade.service.GrowthEventListener;
import solvela.member.grade.service.MemberGrowthService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 成长值的入口判断。
 *
 * <h3>🔴 这里守的两条都是「算错了没人会发现」</h3>
 * ① <b>退回不算成长值</b> —— 算了的话，下单再取消就能刷等级，
 *    而等级换的是长期权益。这条不会报错，只会表现为某些人等级异常地高；
 * ② <b>商城发的积分要算</b> —— 它和退回<b>走同一个方法</b>
 *    （{@code executeWalletRefund}），只能靠 bizType 区分。
 *    按方法名跳过的话，商城那一半会被静默漏掉。
 *
 * @Date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrowthEventListenerTest {

    private static final Long MEMBER_ID = 1001L;

    @Mock
    private MemberGrowthService memberGrowthService;

    private GrowthEventListener listener;

    @BeforeEach
    void setUp() {
        GrowthProperties properties = new GrowthProperties();
        listener = new GrowthEventListener(memberGrowthService, properties);
    }

    private static BizActionEvent scoreEarned(String bizType, String amount, String bizId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bizType", bizType);
        payload.put("bizRefId", bizId);
        return new BizActionEvent(BizActionCodes.SCORE_EARNED, MEMBER_ID, bizId,
                new BigDecimal(amount), LocalDateTime.now(), payload);
    }

    @Test
    @DisplayName("发奖入账：算成长值，基数等于入账积分")
    void 发奖入账算成长值() {
        listener.onBizAction(scoreEarned("PROPOSAL_REWARD", "188", "P-1"));

        ArgumentCaptor<Long> base = ArgumentCaptor.forClass(Long.class);
        verify(memberGrowthService).accrue(eq(MEMBER_ID), base.capture(),
                eq(BizActionCodes.SCORE_EARNED), eq("PROPOSAL_REWARD"), eq("P-1"), anyString());
        assertEquals(188L, base.getValue());
    }

    @Test
    @DisplayName("🔴 退回不算成长值 —— 算了的话下单再取消就能刷等级")
    void 退回不算成长值() {
        /*
         * 退回走的是 executeWalletRefund，和商城发积分【同一个方法】，
         * 所以只能靠 bizType 区分。MALL_EXCHANGE 不在白名单里。
         */
        listener.onBizAction(scoreEarned("MALL_EXCHANGE", "5000", "M-1"));

        verify(memberGrowthService, never()).accrue(anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("🔴 白名单是白名单不是黑名单：没见过的来源默认【不算】")
    void 未知来源默认不算() {
        /*
         * 将来新增一种入账来源时，安全的默认是「不算」——
         * 黑名单的默认是「算」，于是任何一个没人想到要排除的新来源
         * 都会悄悄抬高所有人的等级，而且没有任何报错。
         */
        listener.onBizAction(scoreEarned("SOME_NEW_SOURCE_NOBODY_THOUGHT_OF", "9999", "X-1"));

        verify(memberGrowthService, never()).accrue(anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("bizType 为空不算 —— 判不出来源就不该给成长值")
    void 没有bizType不算() {
        BizActionEvent event = new BizActionEvent(BizActionCodes.SCORE_EARNED, MEMBER_ID,
                "N-1", new BigDecimal("100"), LocalDateTime.now(), Map.of());

        listener.onBizAction(event);

        verify(memberGrowthService, never()).accrue(anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("别的业务动作不产生成长值 —— 今天只有积分入账这一个来源")
    void 其它动作不产生成长值() {
        listener.onBizAction(new BizActionEvent(BizActionCodes.ORDER_PAID, MEMBER_ID,
                "O-1", new BigDecimal("100"), LocalDateTime.now(), Map.of()));

        verify(memberGrowthService, never()).accrue(anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("🔴 小数向下取整，不四舍五入 —— 0.6 积分不该换来 1 点成长值")
    void 小数向下取整() {
        listener.onBizAction(scoreEarned("PROPOSAL_REWARD", "0.6", "P-2"));

        /*
         * 四舍五入的话，活动里大量的小额入账积少成多，就是凭空多给的等级。
         * 宁可少给 —— 少给用户不会来问，多给没人会发现。
         */
        verify(memberGrowthService, never()).accrue(anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString());

        listener.onBizAction(scoreEarned("PROPOSAL_REWARD", "1.9", "P-3"));

        ArgumentCaptor<Long> base = ArgumentCaptor.forClass(Long.class);
        verify(memberGrowthService).accrue(eq(MEMBER_ID), base.capture(),
                anyString(), anyString(), eq("P-3"), anyString());
        assertEquals(1L, base.getValue(), "1.9 应当向下取整成 1");
    }

    @Test
    @DisplayName("🔴 下游抛异常不能往上冒 —— 此刻积分已经到账了")
    void 异常不上抛() {
        org.mockito.Mockito.doThrow(new RuntimeException("库挂了"))
                .when(memberGrowthService).accrue(anyLong(), anyLong(),
                        anyString(), any(), anyString(), anyString());

        // 不抛即通过：抛出去会变成一次没有归属的 500，而用户的积分明明已经到了
        listener.onBizAction(scoreEarned("PROPOSAL_REWARD", "100", "P-4"));
    }
}
