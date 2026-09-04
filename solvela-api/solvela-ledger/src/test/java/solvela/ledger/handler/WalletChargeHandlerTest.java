package solvela.ledger.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import solvela.code.BizErrorCode;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.risk.ProposalRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 钱包入账门面：把领域异常翻译成引擎认识的下发结果。
 *
 * <h3>最要紧的一条：撞唯一索引算成功，不算失败</h3>
 * 资金流水上有唯一索引，同一提案重复入账会撞它。这时候<b>钱其实已经在上一次进账了</b> ——
 * 判成失败的后果是引擎会把预算还回去（{@code releaseBudgetQuietly}），
 * 于是「钱发出去了，预算却退回来了」，账目从此对不平，而且没有任何报错。
 *
 * <p>另一条：锁键必须用 member_id 不能用账号。账号可改，改名之后同一个人的两个请求
 * 会落到两把不同的锁上，而钱包行还是同一行 —— 并发保护当场失效，且完全没有报错。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class WalletChargeHandlerTest {

    @Mock
    private MemberWalletService memberWalletService;

    private ScoreAssetHandler scoreHandler;
    private WalletAssetHandler balanceHandler;
    private ProposalRecord proposal;

    @BeforeEach
    void setUp() {
        scoreHandler = new ScoreAssetHandler();
        balanceHandler = new WalletAssetHandler();
        // 父类用 @Resource 注入，单测里直接塞
        ReflectionTestUtils.setField(scoreHandler, "memberWalletService", memberWalletService);
        ReflectionTestUtils.setField(balanceHandler, "memberWalletService", memberWalletService);

        proposal = new ProposalRecord();
        proposal.setId(1001L);
        proposal.setMemberId(900001L);
        proposal.setMemberName("sv900001");
    }

    @Test
    @DisplayName("入账成功就是成功")
    void 入账成功() {
        DispatchOutcome outcome = scoreHandler.executeWithLock(proposal);

        assertTrue(outcome.ok());
        verify(memberWalletService).executeWalletCharge(proposal, PrizeTypeEnum.SCORE);
    }

    @Test
    @DisplayName("领域异常翻译成失败结果，原因原样带出去")
    void 业务异常翻成失败() {
        doThrow(new BusinessException(BizErrorCode.ACCOUNT_BALANCE_CHANGED))
                .when(memberWalletService).executeWalletCharge(any(), any());

        DispatchOutcome outcome = scoreHandler.executeWithLock(proposal);

        assertFalse(outcome.ok());
        assertNotNull(outcome.failReason(), "失败原因要落进提案备注给运营看，不能是 null");
    }

    @Test
    @DisplayName("🔴 撞唯一索引判成功：钱已经进去了，判失败会让引擎把预算错误地退回来")
    void 重复入账算幂等成功() {
        doThrow(new DuplicateKeyException("uk_txn_biz"))
                .when(memberWalletService).executeWalletCharge(any(), any());

        DispatchOutcome outcome = scoreHandler.executeWithLock(proposal);

        assertTrue(outcome.ok(), "同一提案重复入账是幂等成功，不是失败");
        assertEquals(null, outcome.failReason());
    }

    @Test
    @DisplayName("积分与现金各用各的资产类型，别串了")
    void 两种资产互不串台() {
        scoreHandler.executeWithLock(proposal);
        balanceHandler.executeWithLock(proposal);

        verify(memberWalletService).executeWalletCharge(proposal, PrizeTypeEnum.SCORE);
        verify(memberWalletService).executeWalletCharge(proposal, PrizeTypeEnum.BALANCE);
    }

    @Test
    @DisplayName("锁键用 member_id 且带资产类型：同一个人的积分与现金互不阻塞")
    void 锁键的粒度() {
        String scoreKey = scoreHandler.getLockKey(proposal);
        String balanceKey = balanceHandler.getLockKey(proposal);

        assertTrue(scoreKey.contains("900001"), "锁键必须用会员号 —— 账号可改，改完并发保护就失效了");
        assertFalse(scoreKey.contains("sv900001"), "不能拿账号当锁键");
        assertEquals(scoreKey.replace(PrizeTypeEnum.SCORE.name(), PrizeTypeEnum.BALANCE.name()), balanceKey,
                "两种资产只差资产类型那一段，说明粒度是「会员 + 资产类型」");
    }
}
