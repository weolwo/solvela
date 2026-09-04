package solvela.risk.proposal.service;

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
import org.springframework.dao.DuplicateKeyException;
import solvela.enums.EnableStatusEnum;
import solvela.enums.ProposalStatusEnum;
import solvela.enums.ReviewLevelEnum;
import solvela.exception.BusinessException;
import solvela.member.service.MemberService;
import solvela.risk.PromotionConfig;
import solvela.risk.ProposalRecord;
import solvela.risk.engine.RiskBlockCode;
import solvela.risk.engine.RiskChainEngine;
import solvela.risk.engine.RiskResult;
import solvela.risk.promotionconfig.service.PromotionConfigService;
import solvela.risk.proposal.dao.ProposalRecordDao;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;
import solvela.risk.spi.AssetDispatcher;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提案受理：风控拦截、审批分档、幂等、下发时机。
 *
 * <h3>这里盯的是「钱该不该出去、以及出去之前留没留下证据」</h3>
 * <ul>
 *   <li>被风控拦下时<b>必须先落一条 status=80 的记录再抛</b> ——
 *       那条记录是合规审计与客诉排查的唯一证据，也正是 addProposal 上
 *       {@code noRollbackFor = BusinessException.class} 存在的全部理由。
 *       这个注解一旦被人「顺手清理」掉，记录会随异常一起回滚，
 *       表现是「风控明明拦了，提案表里一条都没有」，而且不报任何错；</li>
 *   <li>拦截分类 {@code riskCode} 要跟着一起落库 —— 文案会改，编码才是漏斗聚类的判据；</li>
 *   <li>触发审批阈值的绝不能当场下发，否则双层审批形同虚设；</li>
 *   <li>撞唯一键要幂等收口，不能把重复请求变成 500。</li>
 * </ul>
 *
 * <p>下发本身走 {@code dispatchAfterCommit}：单测里没有活动事务，它会退化为直接执行 ——
 * 这正好让「有没有触发下发」变得可断言。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposalRecordServiceTest {

    private static final Long CONFIG_ID = 77L;
    private static final Long MEMBER_ID = 900001L;

    @Mock
    private ProposalRecordDao proposalRecordDao;
    @Mock
    private PromotionConfigService promotionConfigService;
    @Mock
    private RiskChainEngine riskChainEngine;
    @Mock
    private AssetDispatcher assetDispatcher;
    @Mock
    private MemberService memberService;

    @InjectMocks
    private ProposalRecordService service;

    private PromotionConfig config;

    @BeforeEach
    void setUp() {
        config = new PromotionConfig();
        config.setId(CONFIG_ID);
        config.setStatus(EnableStatusEnum.ENABLED);
        config.setReviewLevel(ReviewLevelEnum.NONE);
        config.setFirstReviewThreshold(new BigDecimal("1000"));

        when(promotionConfigService.getById(CONFIG_ID)).thenReturn(config);
        when(memberService.requireMemberName(MEMBER_ID)).thenReturn("sv900001");
        when(riskChainEngine.execute(any())).thenReturn(RiskResult.pass());
        // 插入时补一个 id，模拟自增回填
        when(proposalRecordDao.insert(any(ProposalRecord.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ProposalRecord.class).setId(2001L);
            return 1;
        });
    }

    @Test
    @DisplayName("优惠配置不存在或已停用：当场拒绝，不落记录不下发")
    void 配置停用直接拒绝() {
        config.setStatus(EnableStatusEnum.DISABLED);

        assertThrows(BusinessException.class, () -> service.addProposal(command(new BigDecimal("10"))));

        verify(proposalRecordDao, never()).insert(any(ProposalRecord.class));
        verify(assetDispatcher, never()).execute(any(), any());
    }

    @Test
    @DisplayName("风控拦截：先落 80 的审计记录再抛，且带上 riskCode")
    void 风控拦截要留证据() {
        when(riskChainEngine.execute(any()))
                .thenReturn(RiskResult.reject(RiskBlockCode.GLOBAL_BUDGET_LIMIT, "预算已耗尽"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.addProposal(command(new BigDecimal("10"))));
        assertTrue(error.getMessage().contains("预算已耗尽"), "拒绝原因要原样带给调用方");

        ProposalRecord saved = capturedProposal();
        assertEquals(ProposalStatusEnum.RISK_BLOCKED, saved.getStatus(), "拦截也要落库，否则事后查无此事");
        assertEquals(RiskBlockCode.GLOBAL_BUDGET_LIMIT.getValue(), saved.getRiskCode(),
                "文案会改，编码才是漏斗聚类的判据");
        assertTrue(saved.getRemark().contains("预算已耗尽"));
        // 被拦了就绝不能下发
        verify(assetDispatcher, never()).execute(any(), any());
    }

    @Test
    @DisplayName("免审：落待执行并当场下发")
    void 免审当场下发() {
        Long id = service.addProposal(command(new BigDecimal("10")));

        assertEquals(2001L, id);
        assertEquals(ProposalStatusEnum.PENDING_EXECUTE, capturedProposal().getStatus());
        verify(assetDispatcher).execute(any(), any());
    }

    @Test
    @DisplayName("金额达到一审阈值：挂进审批池，绝不当场下发")
    void 达到阈值挂审批() {
        config.setReviewLevel(ReviewLevelEnum.SINGLE);

        service.addProposal(command(new BigDecimal("1000")));

        assertEquals(ProposalStatusEnum.FIRST_REVIEW, capturedProposal().getStatus());
        // 这一条挡住的是「审批还没点，钱先出去了」
        verify(assetDispatcher, never()).execute(any(), any());
    }

    @Test
    @DisplayName("配了审批但金额没够门槛：自动豁免，直接下发")
    void 未达阈值自动豁免() {
        config.setReviewLevel(ReviewLevelEnum.SINGLE);

        service.addProposal(command(new BigDecimal("999.99")));

        assertEquals(ProposalStatusEnum.PENDING_EXECUTE, capturedProposal().getStatus());
        verify(assetDispatcher).execute(any(), any());
    }

    @Test
    @DisplayName("撞唯一键：幂等收口返回 null，不抛也不重复下发")
    void 重复单号幂等() {
        when(proposalRecordDao.insert(any(ProposalRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_t_prm_prop_tsk_stg"));

        Long id = service.addProposal(command(new BigDecimal("10")));

        assertNull(id, "重复请求返回 null 而不是回查一次 —— 调用方要的是不报错、不重复发");
        verify(assetDispatcher, never()).execute(any(), any());
    }

    @Test
    @DisplayName("单号由提案域自己发，不采信调用方传值")
    void 单号自己发() {
        service.addProposal(command(new BigDecimal("10")));

        ProposalRecord saved = capturedProposal();
        assertTrue(saved.getTradeNo() != null && saved.getTradeNo().startsWith("PRP"),
                "交易号是本域对外的凭证，唯一性必须由发号方保证");
        assertEquals("sv900001", saved.getMemberName(),
                "展示快照由服务端查会员表补，不让调用方自己传名字");
    }

    // ------------------------------------------------------------------ 审批

    @Test
    @DisplayName("审批：抢不到条件更新就抛，绝不下发")
    void 审批并发抢不到不下发() {
        ProposalRecord pending = proposalAt(ProposalStatusEnum.FIRST_REVIEW);
        when(proposalRecordDao.selectById(anyLong())).thenReturn(pending);
        when(proposalRecordDao.updateReview(anyLong(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.approve(1L, "财务A", "同意"));

        // 两个审批人同时点，只有一个能真的放行 —— 否则同一笔奖会被下发两次
        verify(assetDispatcher, never()).execute(any(), any());
    }

    @Test
    @DisplayName("单层审批一审通过：直接待执行并下发")
    void 单层审批一次放行() {
        config.setReviewLevel(ReviewLevelEnum.SINGLE);
        when(proposalRecordDao.selectById(anyLong())).thenReturn(proposalAt(ProposalStatusEnum.FIRST_REVIEW));
        when(proposalRecordDao.updateReview(anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        service.approve(1L, "财务A", "同意");

        verify(assetDispatcher).execute(any(), any());
    }

    @Test
    @DisplayName("双层审批一审通过：转二审，此时还不能下发")
    void 双层审批一审不下发() {
        config.setReviewLevel(ReviewLevelEnum.DOUBLE);
        when(proposalRecordDao.selectById(anyLong())).thenReturn(proposalAt(ProposalStatusEnum.FIRST_REVIEW));
        when(proposalRecordDao.updateReview(anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        service.approve(1L, "财务A", "同意");

        verify(proposalRecordDao).updateReview(anyLong(), any(), eqStatus(ProposalStatusEnum.SECOND_REVIEW),
                any(), any(), any());
        verify(assetDispatcher, never()).execute(any(), any());
    }

    @Test
    @DisplayName("不在审批态的提案不许审批")
    void 非审批态不许审批() {
        when(proposalRecordDao.selectById(anyLong())).thenReturn(proposalAt(ProposalStatusEnum.SUCCESS));

        assertThrows(BusinessException.class, () -> service.approve(1L, "财务A", "同意"));

        verify(proposalRecordDao, never()).updateReview(anyLong(), any(), any(), any(), any(), any());
    }

    private ProposalStatusEnum eqStatus(ProposalStatusEnum status) {
        return org.mockito.ArgumentMatchers.eq(status);
    }

    private ProposalRecordAddCommand command(BigDecimal amount) {
        ProposalRecordAddCommand req = new ProposalRecordAddCommand();
        req.setMemberId(MEMBER_ID);
        req.setPromotionConfigId(CONFIG_ID);
        req.setAssetType("SCORE");
        req.setAmount(amount);
        req.setQuantity(1);
        req.setSourceType("DRAW");
        req.setSourceBizId("BIZ-1");
        req.setRemark("中奖发放");
        return req;
    }

    private ProposalRecord proposalAt(ProposalStatusEnum status) {
        ProposalRecord proposal = new ProposalRecord();
        proposal.setId(1L);
        proposal.setMemberId(MEMBER_ID);
        proposal.setPromotionConfigId(CONFIG_ID);
        proposal.setStatus(status);
        proposal.setAmount(new BigDecimal("10"));
        return proposal;
    }

    private ProposalRecord capturedProposal() {
        ArgumentCaptor<ProposalRecord> captor = ArgumentCaptor.forClass(ProposalRecord.class);
        verify(proposalRecordDao).insert(captor.capture());
        return captor.getValue();
    }
}
