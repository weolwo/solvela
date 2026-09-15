package solvela.ledger.coupon.manual;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import solvela.coupon.CouponTemplate;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.issue.CouponIssueCmd;
import solvela.ledger.coupon.issue.CouponIssueService;
import solvela.ledger.coupon.manual.domain.ManualCouponGrantCmd;
import solvela.ledger.coupon.manual.domain.ManualCouponGrantResult;
import solvela.ledger.coupon.template.service.CouponTemplateService;
import solvela.member.service.MemberService;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人工发券。
 *
 * <h3>这里守的是三条，每一条对应一种「多发钱出去」</h3>
 * <ol>
 *   <li><b>批量上限</b> —— 不拦的话这个入口就是「给全体用户发券」的后门；</li>
 *   <li><b>没模板就拒绝</b> —— 发出去的券没有规则，用户永远用不了；</li>
 *   <li><b>幂等</b> —— 双击一次就多发一批券，而且没人会发现。</li>
 * </ol>
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponManualGrantServiceTest {

    private static final String CODE = "SUMMER2026";
    private static final String TICKET = "T20260915-001";
    private static final String OPERATOR = "admin";

    @Mock
    private CouponTemplateService couponTemplateService;
    @Mock
    private CouponIssueService couponIssueService;
    @Mock
    private MemberCouponDao memberCouponDao;
    @Mock
    private MemberService memberService;
    @Mock
    private NotificationService notificationService;

    private CouponManualGrantService service;

    @BeforeEach
    void setUp() {
        service = new CouponManualGrantService(couponTemplateService, couponIssueService,
                memberCouponDao, memberService, notificationService);
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(template());
        when(memberService.requireMemberName(anyLong())).thenReturn("tester");
        when(couponIssueService.newCoupon(any())).thenAnswer(inv -> {
            CouponIssueCmd cmd = inv.getArgument(0);
            MemberCoupon coupon = new MemberCoupon();
            coupon.setMemberId(cmd.memberId());
            coupon.setCouponCode(cmd.couponCode());
            coupon.setCouponName("夏日券");
            coupon.setSourceType(cmd.sourceType());
            coupon.setSourceBizId(cmd.sourceBizId());
            coupon.setValidEndTime(LocalDateTime.now().plusDays(30));
            return coupon;
        });
    }

    @Test
    @DisplayName("发给 3 个人各 1 张：3 行券、3 条通知")
    void 正常发券() {
        ManualCouponGrantResult result = service.grant(cmd(List.of(1L, 2L, 3L), 1), OPERATOR);

        assertAll(
                () -> assertEquals(3, result.granted()),
                () -> assertTrue(result.skipped().isEmpty()),
                () -> assertTrue(result.failed().isEmpty()),
                () -> assertEquals("夏日券", result.couponName()));
        verify(memberCouponDao, times(3)).insert(any(MemberCoupon.class));
    }

    @Test
    @DisplayName("🔴 一人发 3 张只发【一条】通知，不是 3 条")
    void 一人多张只发一条通知() {
        service.grant(cmd(List.of(1L), 3), OPERATOR);

        verify(memberCouponDao, times(3)).insert(any(MemberCoupon.class));
        // 和 COUPON_EXPIRING 那条「8 张券发一条」是同一个道理
        verify(notificationService, times(1)).send(any(NotifyRequest.class));
    }

    @Test
    @DisplayName("🔴 幂等键带着会员号 —— 少了它，一个工单发给 5 个人只有第一个能收到")
    void 幂等键必须带会员号() {
        service.grant(cmd(List.of(11L, 22L), 1), OPERATOR);

        ArgumentCaptor<CouponIssueCmd> captor = ArgumentCaptor.forClass(CouponIssueCmd.class);
        verify(couponIssueService, times(2)).newCoupon(captor.capture());
        List<String> bizIds = captor.getAllValues().stream().map(CouponIssueCmd::sourceBizId).toList();

        // 只用「工单号:序号」的话，第二个人会撞上 uk_manual_src，而且不报错 ——
        // 运营看到的是「成功 1 跳过 1」，以为第二个人上次已经发过了
        assertEquals(List.of(TICKET + ":11:1", TICKET + ":22:1"), bizIds);
    }

    @Test
    @DisplayName("🔴 重复提交：撞唯一键 = 已经发过，算跳过不算失败")
    void 重复提交是跳过不是失败() {
        when(memberCouponDao.insert(any(MemberCoupon.class)))
                .thenThrow(new DuplicateKeyException("uk_manual_src"));

        ManualCouponGrantResult result = service.grant(cmd(List.of(1L), 1), OPERATOR);

        assertAll(
                () -> assertEquals(0, result.granted()),
                () -> assertEquals(List.of(1L), result.skipped()),
                () -> assertTrue(result.failed().isEmpty()),
                // 已经发过就别再发一条通知 —— 用户会以为自己又收到一张
                () -> verify(notificationService, never()).send(any(NotifyRequest.class)));
    }

    @Test
    @DisplayName("🔴 没有券模板 → 拒绝，不发。和自动发券那条路刚好相反")
    void 没有模板就拒绝() {
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(null);

        /*
         * 自动发券找不到模板时【照发】，因为拒发会把一个在架商品变成兑换必失败。
         * 人工发券是一个人从列表里选券模，选到没模板的只可能是选错了 ——
         * 这时候发出去一张没规则的券，等于用一次「看起来成功」换一张用户永远用不了的券。
         */
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.grant(cmd(List.of(1L), 1), OPERATOR));
        assertTrue(e.getMessage().contains("没有启用中的模板"));
        verify(memberCouponDao, never()).insert(any(MemberCoupon.class));
    }

    @Test
    @DisplayName("🔴 收件人超上限 → 整批拒绝。这个入口不能变成「给全体发券」的后门")
    void 收件人有硬上限() {
        List<Long> tooMany = IntStream.rangeClosed(1, 201).mapToObj(Long::valueOf).toList();

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.grant(cmd(tooMany, 1), OPERATOR));
        assertTrue(e.getMessage().contains("一次最多发给"));
        verify(memberCouponDao, never()).insert(any(MemberCoupon.class));
    }

    @Test
    @DisplayName("每人张数也有上限 —— 「每人 1000 张」和「发给 1000 个人」是同一件事")
    void 每人张数有上限() {
        assertThrows(BusinessException.class, () -> service.grant(cmd(List.of(1L), 999), OPERATOR));
    }

    @Test
    @DisplayName("🔴 没有工单号 → 拒绝：那是防重发的唯一依据")
    void 工单号不能为空() {
        ManualCouponGrantCmd noTicket =
                new ManualCouponGrantCmd(List.of(1L), CODE, 1, "  ", "补偿");

        // 没有它，双击一次就多发一批券出去，而且没人会发现
        assertThrows(BusinessException.class, () -> service.grant(noTicket, OPERATOR));
    }

    @Test
    @DisplayName("没有操作人 → 拒绝：事后要回答得了「这批券是谁发的」")
    void 操作人不能为空() {
        assertThrows(BusinessException.class, () -> service.grant(cmd(List.of(1L), 1), " "));
    }

    @Test
    @DisplayName("收件人去重：同一个人贴了两遍只发一次")
    void 收件人去重() {
        ManualCouponGrantResult result = service.grant(cmd(List.of(1L, 1L, 2L), 1), OPERATOR);

        assertEquals(2, result.granted());
        verify(memberCouponDao, times(2)).insert(any(MemberCoupon.class));
    }

    @Test
    @DisplayName("发券原因原样带给用户 —— 没有它，用户收到的是一张来路不明的券")
    void 原因带给用户() {
        service.grant(new ManualCouponGrantCmd(List.of(1L), CODE, 1, TICKET, "就您9月12日的问题补偿"),
                OPERATOR);

        ArgumentCaptor<NotifyRequest> captor = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notificationService).send(captor.capture());
        assertEquals("就您9月12日的问题补偿。", captor.getValue().params().get("reason"));
    }

    private static ManualCouponGrantCmd cmd(List<Long> memberIds, int quantity) {
        return new ManualCouponGrantCmd(memberIds, CODE, quantity, TICKET, "补偿");
    }

    private static CouponTemplate template() {
        CouponTemplate template = new CouponTemplate();
        template.setCouponCode(CODE);
        template.setVersion(1);
        template.setCouponName("夏日券");
        template.setDiscountType(CouponDiscountTypeEnum.FIXED);
        template.setDiscountValue(new BigDecimal("20"));
        template.setMinAmount(BigDecimal.ZERO);
        template.setDeductTarget(CouponDeductTargetEnum.CASH);
        template.setScopeType(CouponScopeTypeEnum.ALL);
        template.setValidDays(30);
        template.setStatus(1);
        return template;
    }
}
