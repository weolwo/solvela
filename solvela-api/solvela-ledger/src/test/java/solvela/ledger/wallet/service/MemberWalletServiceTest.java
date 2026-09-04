package solvela.ledger.wallet.service;

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
import solvela.enums.PrizeTypeEnum;
import solvela.enums.TransactionTypeEnum;
import solvela.enums.WalletStatusEnum;
import solvela.exception.BusinessException;
import solvela.ledger.MemberAssetTransaction;
import solvela.ledger.MemberWallet;
import solvela.ledger.transaction.dao.MemberAssetTransactionDao;
import solvela.ledger.transaction.service.MemberAssetTransactionService;
import solvela.ledger.wallet.dao.MemberWalletDao;
import solvela.member.service.MemberService;
import solvela.risk.ProposalRecord;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钱包动账的三条路：入账、扣减、退还。
 *
 * <h3>这里断言的是「钱没动成的时候，有没有留下痕迹」</h3>
 * 三个方法都是「校验 -> 条件更新 -> 写流水」，而每一步失败的后果都不一样：
 * <ul>
 *   <li>金额非正还往下走 —— 负数入账等于给自己发钱；</li>
 *   <li>条件更新返回 0 行却继续写流水 —— <b>钱没加，流水说加了</b>，对账永远对不平；</li>
 *   <li>余额不足没拦住 —— 扣成负数；</li>
 *   <li>冻结账户放行 —— 冻结形同虚设。</li>
 * </ul>
 * 这几条都不会在正常路径上出现，正因如此才需要测试盯着。
 *
 * <p>条件更新本身（{@code WHERE balance >= amount AND version = ?}）的正确性由 SQL 保证，
 * 这里只关心<b>返回 0 行时服务做了什么</b> —— 那是 SQL 管不到的部分。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberWalletServiceTest {

    private static final Long MEMBER_ID = 900001L;
    private static final Long WALLET_ID = 5L;
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock
    private MemberWalletDao memberWalletDao;
    @Mock
    private MemberAssetTransactionDao memberAssetTransactionDao;
    @Mock
    private MemberAssetTransactionService memberAssetTransactionService;
    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberWalletService service;

    private MemberWallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new MemberWallet();
        wallet.setId(WALLET_ID);
        wallet.setMemberId(MEMBER_ID);
        wallet.setAssetType(PrizeTypeEnum.SCORE.name());
        wallet.setBalance(new BigDecimal("500"));
        wallet.setStatus(WalletStatusEnum.NORMAL);
        wallet.setVersion(3);

        when(memberService.requireMemberName(MEMBER_ID)).thenReturn("sv900001");
        when(memberWalletDao.getByMemberIdAndAssetType(eq(MEMBER_ID), eq(PrizeTypeEnum.SCORE.name())))
                .thenReturn(wallet);
    }

    // ------------------------------------------------------------------ 扣减

    @Test
    @DisplayName("扣减：金额非正一律拒绝")
    void 扣减金额必须为正() {
        assertThrows(BusinessException.class,
                () -> service.executeWalletDeduct(MEMBER_ID, PrizeTypeEnum.SCORE, BigDecimal.ZERO, "DRAW", "1", null));
        assertThrows(BusinessException.class,
                () -> service.executeWalletDeduct(MEMBER_ID, PrizeTypeEnum.SCORE, new BigDecimal("-1"), "DRAW", "1", null));

        verify(memberWalletDao, never()).deductBalanceWithVersion(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("扣减：余额不足在动账之前就拦住")
    void 余额不足不动账() {
        wallet.setBalance(new BigDecimal("99"));

        assertThrows(BusinessException.class,
                () -> service.executeWalletDeduct(MEMBER_ID, PrizeTypeEnum.SCORE, AMOUNT, "DRAW", "1", null));

        verify(memberWalletDao, never()).deductBalanceWithVersion(anyLong(), any(), anyInt());
        verify(memberAssetTransactionDao, never()).insert(any(MemberAssetTransaction.class));
    }

    @Test
    @DisplayName("扣减：冻结账户不许动")
    void 冻结账户不许扣() {
        wallet.setStatus(WalletStatusEnum.FROZEN);

        assertThrows(BusinessException.class,
                () -> service.executeWalletDeduct(MEMBER_ID, PrizeTypeEnum.SCORE, AMOUNT, "DRAW", "1", null));

        verify(memberWalletDao, never()).deductBalanceWithVersion(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("扣减：乐观锁没抢到就抛，绝不补一条流水")
    void 扣减冲突不写流水() {
        when(memberWalletDao.deductBalanceWithVersion(eq(WALLET_ID), eq(AMOUNT), eq(3))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.executeWalletDeduct(MEMBER_ID, PrizeTypeEnum.SCORE, AMOUNT, "DRAW", "1", null));

        // 钱没扣成却落一条流水，就是账实不符 —— 而且事后没有任何办法分辨
        verify(memberAssetTransactionDao, never()).insert(any(MemberAssetTransaction.class));
    }

    @Test
    @DisplayName("扣减成功：流水记支出、变动后余额是扣完的数")
    void 扣减成功落流水() {
        when(memberWalletDao.deductBalanceWithVersion(eq(WALLET_ID), eq(AMOUNT), eq(3))).thenReturn(1);

        service.executeWalletDeduct(MEMBER_ID, PrizeTypeEnum.SCORE, AMOUNT, "DRAW_TICKET", "REF-9", "抽奖门票");

        MemberAssetTransaction txn = capturedTransaction();
        assertEquals(TransactionTypeEnum.EXPENSE, txn.getTransactionType());
        // change_amount 存的是「变动绝对值」，方向由 transaction_type 单独表示
        assertEquals(0, AMOUNT.compareTo(txn.getChangeAmount()), "变动金额必须是正的绝对值");
        assertEquals(0, new BigDecimal("400").compareTo(txn.getBalanceAfter()), "变动后余额 = 扣之前 - 变动额");
        assertEquals("sv900001", txn.getMemberName(), "流水是单据，要留下当时那个账号");
        assertEquals("DRAW_TICKET", txn.getBizType());
        assertEquals("REF-9", txn.getBizRefId(), "溯源单号不能丢，对账靠它");
    }

    // ------------------------------------------------------------------ 入账

    @Test
    @DisplayName("入账：金额非正一律拒绝")
    void 入账金额必须为正() {
        ProposalRecord proposal = proposalOf(BigDecimal.ZERO);

        assertThrows(BusinessException.class, () -> service.executeWalletCharge(proposal, PrizeTypeEnum.SCORE));

        verify(memberWalletDao, never()).addBalanceWithVersion(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("入账：乐观锁没抢到就抛，绝不补一条流水")
    void 入账冲突不写流水() {
        when(memberWalletDao.addBalanceWithVersion(eq(WALLET_ID), eq(AMOUNT), eq(3))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.executeWalletCharge(proposalOf(AMOUNT), PrizeTypeEnum.SCORE));

        verify(memberAssetTransactionDao, never()).insert(any(MemberAssetTransaction.class));
    }

    @Test
    @DisplayName("入账成功：账号快照取提案上的那一份，不再查一次会员表")
    void 入账成功用提案上的账号快照() {
        when(memberWalletDao.addBalanceWithVersion(eq(WALLET_ID), eq(AMOUNT), eq(3))).thenReturn(1);

        service.executeWalletCharge(proposalOf(AMOUNT), PrizeTypeEnum.SCORE);

        MemberAssetTransaction txn = capturedTransaction();
        assertEquals(TransactionTypeEnum.INCOME, txn.getTransactionType());
        assertEquals(0, new BigDecimal("600").compareTo(txn.getBalanceAfter()));
        assertEquals("提案上的账号", txn.getMemberName(), "提案落库时已经记下了当时那个账号，不必再查");
        // 入账这条路刻意不查会员表：提案本身就是凭证
        verify(memberService, never()).requireMemberName(anyLong());
    }

    @Test
    @DisplayName("入账：钱包不存在时自愈初始化，不是报错")
    void 入账时钱包自愈() {
        when(memberWalletDao.getByMemberIdAndAssetType(eq(MEMBER_ID), eq(PrizeTypeEnum.SCORE.name())))
                .thenReturn(null);
        when(memberWalletDao.addBalanceWithVersion(any(), any(), anyInt())).thenReturn(1);

        service.executeWalletCharge(proposalOf(AMOUNT), PrizeTypeEnum.SCORE);

        ArgumentCaptor<MemberWallet> captor = ArgumentCaptor.forClass(MemberWallet.class);
        verify(memberWalletDao).insert(captor.capture());
        MemberWallet created = captor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(created.getBalance()), "新开的钱包必须从 0 起算");
        assertEquals(WalletStatusEnum.NORMAL, created.getStatus());
        assertEquals(0, created.getVersion(), "乐观锁计数器从 0 起");
    }

    // ------------------------------------------------------------------ 退还

    @Test
    @DisplayName("退还：走的是入账方向，金额同样必须为正")
    void 退还金额必须为正() {
        assertThrows(BusinessException.class,
                () -> service.executeWalletRefund(MEMBER_ID, PrizeTypeEnum.SCORE, BigDecimal.ZERO, "DRAW", "1", null));

        verify(memberWalletDao, never()).addBalanceWithVersion(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("退还成功：记收入，变动后余额是加完的数")
    void 退还成功落流水() {
        when(memberWalletDao.addBalanceWithVersion(eq(WALLET_ID), eq(AMOUNT), eq(3))).thenReturn(1);

        service.executeWalletRefund(MEMBER_ID, PrizeTypeEnum.SCORE, AMOUNT, "DRAW_REFUND", "REF-9", "无货退门票");

        MemberAssetTransaction txn = capturedTransaction();
        assertEquals(TransactionTypeEnum.INCOME, txn.getTransactionType());
        assertEquals(0, new BigDecimal("600").compareTo(txn.getBalanceAfter()));
    }

    private ProposalRecord proposalOf(BigDecimal amount) {
        ProposalRecord proposal = new ProposalRecord();
        proposal.setId(1L);
        proposal.setMemberId(MEMBER_ID);
        proposal.setMemberName("提案上的账号");
        proposal.setAmount(amount);
        proposal.setRemark("活动发奖");
        return proposal;
    }

    private MemberAssetTransaction capturedTransaction() {
        ArgumentCaptor<MemberAssetTransaction> captor = ArgumentCaptor.forClass(MemberAssetTransaction.class);
        verify(memberAssetTransactionDao).insert(captor.capture());
        return captor.getValue();
    }
}
