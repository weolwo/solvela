package solvela.ledger.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.ProposalStatusEnum;
import solvela.ledger.handler.IAssetHandler;
import solvela.ledger.strategy.AssetStrategyFactory;
import solvela.member.api.PrizeDispatchResultMessage;
import solvela.risk.PromotionConfig;
import solvela.risk.ProposalRecord;
import solvela.risk.promotionconfig.dao.PromotionConfigDao;
import solvela.risk.proposal.dao.ProposalRecordDao;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产分发引擎的资损路径。
 *
 * <h3>为什么这几条必须有测试</h3>
 * 这个引擎是「钱真的出去」的最后一道编排，而它的每一条分支都直接对应一种资损或卡单：
 * <ul>
 *   <li>预算扣不动却继续发 —— <b>超发</b>；</li>
 *   <li>发失败了不还预算 —— 预算水位只减不增，<b>跑一段时间就发不出奖了</b>；</li>
 *   <li>还预算的数量与扣的对不上 —— used_quota 慢性漂移；</li>
 *   <li>异常时不落终态 —— 提案<b>永远停在 40 执行中</b>，既不会重试也没人发现；</li>
 *   <li>抢不到状态闸门还往下走 —— <b>同一笔奖发两次</b>。</li>
 * </ul>
 * 这些都不会在正常联调里出现：要么并发才复现，要么要等预算耗尽那一天。
 *
 * <p>用 mock 而不是连库：要断言的是<b>编排顺序与补偿动作</b>（扣了没有、还了没有、
 * 状态落到哪一档），这些恰恰是 SQL 之外的部分。条件更新本身的正确性由 DB 保证，
 * 这里只关心「返回 0 行时引擎做了什么」。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetDispatchEngineTest {

    private static final Long PROPOSAL_ID = 1001L;
    private static final Long CONFIG_ID = 77L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private ProposalRecordDao proposalRecordDao;
    @Mock
    private PromotionConfigDao promotionConfigDao;
    @Mock
    private PrizeDispatchResultPublisher dispatchResultPublisher;
    @Mock
    private AssetStrategyFactory strategyFactory;
    @Mock
    private IAssetHandler handler;

    @InjectMocks
    private AssetDispatchEngine engine;

    private ProposalRecord proposal;
    private PromotionConfig config;

    @BeforeEach
    void setUp() {
        proposal = new ProposalRecord();
        proposal.setId(PROPOSAL_ID);
        proposal.setTradeNo("PRP0000001");
        proposal.setAssetType("SCORE");
        proposal.setAmount(AMOUNT);
        proposal.setQuantity(1);
        proposal.setSourceBizId("BIZ-1");

        config = new PromotionConfig();
        config.setId(CONFIG_ID);

        when(strategyFactory.getHandler(anyString())).thenReturn(handler);
        // 默认：闸门抢到、预算扣得动
        when(proposalRecordDao.updateStatus(eq(PROPOSAL_ID), eq(ProposalStatusEnum.PENDING_EXECUTE),
                eq(ProposalStatusEnum.EXECUTING))).thenReturn(1);
        when(promotionConfigDao.deductBudget(eq(CONFIG_ID), any(), anyInt())).thenReturn(1);
    }

    @Test
    @DisplayName("抢不到 30->40 的闸门就直接退出，绝不下发")
    void 抢不到闸门不下发() {
        when(proposalRecordDao.updateStatus(eq(PROPOSAL_ID), eq(ProposalStatusEnum.PENDING_EXECUTE),
                eq(ProposalStatusEnum.EXECUTING))).thenReturn(0);

        engine.execute(proposal, config);

        // 这一条是「同一笔奖不会发两次」的全部依据：别人已经在执行或已完结
        verify(promotionConfigDao, never()).deductBudget(any(), any(), anyInt());
        verify(handler, never()).dispatch(any());
        verify(dispatchResultPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("预算扣不动就判失败，绝不先发再说")
    void 预算不足不下发() {
        when(promotionConfigDao.deductBudget(eq(CONFIG_ID), any(), anyInt())).thenReturn(0);

        engine.execute(proposal, config);

        verify(handler, never()).dispatch(any());
        verify(proposalRecordDao).updateStatusAndRemark(eq(PROPOSAL_ID), eq(ProposalStatusEnum.FAILED), anyString());
        // 没扣成功就不能还，还了会把没占用的预算凭空加回去
        verify(promotionConfigDao, never()).releaseBudget(any(), any(), anyInt());
        assertFalse(publishedMessage().success());
    }

    @Test
    @DisplayName("下发成功：落 50 成功态，预算不回滚")
    void 下发成功() {
        when(handler.dispatch(proposal)).thenReturn(DispatchOutcome.success());

        engine.execute(proposal, config);

        verify(proposalRecordDao).updateStatusAndRemark(eq(PROPOSAL_ID), eq(ProposalStatusEnum.SUCCESS), anyString());
        verify(promotionConfigDao, never()).releaseBudget(any(), any(), anyInt());
        assertTrue(publishedMessage().success());
    }

    @Test
    @DisplayName("下发失败：预算要按原样还回去，且只还一次")
    void 下发失败要还预算() {
        when(handler.dispatch(proposal)).thenReturn(DispatchOutcome.failed("账户已冻结"));

        engine.execute(proposal, config);

        // 「按实际扣掉的数量还」——扣 3 还 1 会让 used_quota 只增不减
        verify(promotionConfigDao, times(1)).releaseBudget(CONFIG_ID, AMOUNT, 1);
        verify(proposalRecordDao).updateStatusAndRemark(eq(PROPOSAL_ID), eq(ProposalStatusEnum.FAILED), anyString());
        PrizeDispatchResultMessage message = publishedMessage();
        assertFalse(message.success());
        assertTrue(message.failReason().contains("账户已冻结"), "失败原因要原样带回给发奖侧");
    }

    @Test
    @DisplayName("下发抛异常：照样落终态并还预算，不能把提案留在 40")
    void 下发抛异常也要收口() {
        when(handler.dispatch(proposal)).thenThrow(new IllegalStateException("连不上账务库"));

        engine.execute(proposal, config);

        verify(promotionConfigDao, times(1)).releaseBudget(CONFIG_ID, AMOUNT, 1);
        // 不落终态的话，这条提案既不会被重试也不会被任何人看见
        verify(proposalRecordDao).updateStatusAndRemark(eq(PROPOSAL_ID), eq(ProposalStatusEnum.FAILED), anyString());
        assertFalse(publishedMessage().success());
    }

    @Test
    @DisplayName("扣预算之前就异常：不能还没扣的预算")
    void 扣减之前异常不回滚预算() {
        when(promotionConfigDao.deductBudget(eq(CONFIG_ID), any(), anyInt()))
                .thenThrow(new IllegalStateException("库连不上"));

        engine.execute(proposal, config);

        // budgetDeducted 还是 false —— 这就是那个布尔量存在的理由
        verify(promotionConfigDao, never()).releaseBudget(any(), any(), anyInt());
        verify(proposalRecordDao).updateStatusAndRemark(eq(PROPOSAL_ID), eq(ProposalStatusEnum.FAILED), anyString());
    }

    @Test
    @DisplayName("回写状态自己失败，也不能连累结果消息发不出去")
    void 状态回写失败不影响结果通知() {
        when(handler.dispatch(proposal)).thenReturn(DispatchOutcome.failed("余额不足"));
        when(proposalRecordDao.updateStatusAndRemark(any(), any(), anyString()))
                .thenThrow(new IllegalStateException("提案表写不进去"));

        engine.execute(proposal, config);

        // 两处回写各自兜异常：任何一处挂了都不能让另一处也不做，
        // 否则就会出现「提案是失败的、发奖记录还停在等待」这种一半对的状态
        assertFalse(publishedMessage().success());
    }

    @Test
    @DisplayName("数量为空时按 1 扣、按 1 还，两边必须是同一个数")
    void 数量缺省时扣还一致() {
        proposal.setQuantity(null);
        when(handler.dispatch(proposal)).thenReturn(DispatchOutcome.failed("随便什么原因"));

        engine.execute(proposal, config);

        verify(promotionConfigDao).deductBudget(CONFIG_ID, AMOUNT, 1);
        verify(promotionConfigDao).releaseBudget(CONFIG_ID, AMOUNT, 1);
    }

    @Test
    @DisplayName("金额为空按 0 处理，不能让 null 走进扣减")
    void 金额为空按零() {
        proposal.setAmount(null);
        when(handler.dispatch(proposal)).thenReturn(DispatchOutcome.success());

        engine.execute(proposal, config);

        verify(promotionConfigDao).deductBudget(CONFIG_ID, BigDecimal.ZERO, 1);
    }

    /** 取引擎发出去的那条派发结果消息 */
    private PrizeDispatchResultMessage publishedMessage() {
        ArgumentCaptor<PrizeDispatchResultMessage> captor =
                ArgumentCaptor.forClass(PrizeDispatchResultMessage.class);
        verify(dispatchResultPublisher).publish(captor.capture());
        PrizeDispatchResultMessage message = captor.getValue();
        assertEquals("BIZ-1", message.sourceBizId(), "关联键必须是来源单号，发奖侧靠它找回自己的记录");
        return message;
    }
}
