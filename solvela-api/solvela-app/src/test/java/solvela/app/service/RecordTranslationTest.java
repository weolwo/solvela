package solvela.app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.app.domain.OrderView;
import solvela.app.domain.RecordView;
import solvela.enums.MallOrderStatusEnum;
import solvela.enums.ProposalStatusEnum;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallOrderView;
import solvela.member.api.ProposalRecordApi;
import solvela.member.api.ProposalRecordView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 网关把域的状态翻成「给用户看的话」—— 这一层翻错了，用户会去投诉。
 *
 * <h3>三条不能错的</h3>
 * ① <b>风控拦截不能说破。</b>说成「未通过」，和「驳回」同一句话。
 *    告诉用户「单笔超限」等于告诉他下次怎么绕 —— 而 {@code risk_code}
 *    那一列正是按这几种拦截分类的。
 * ② <b>审批环节一律「处理中」。</b>「待一审」「待二审」是运营视角的说法，
 *    摊给用户看只会让他去催客服「我的二审为什么还没过」。
 * ③ <b>履约失败必须说清积分还在不在。</b>失败不退积分（东西还欠着他），
 *    不说的话他会以为积分白扣了。
 *
 * @Date 2026-09-05
 */
class RecordTranslationTest {

    /* ---------------- 优惠记录（提案） ---------------- */

    @Test
    @DisplayName("🔴 风控拦截说成「未通过」，一个字都不透露拦截原因")
    void 风控拦截不说破() {
        RecordView view = promoOf(ProposalStatusEnum.RISK_BLOCKED, "SINGLE_MAX_AMOUNT_LIMIT 单笔超限");

        assertAll(
                // 和「驳回」同一句话：对用户是同一件事，而区分开来只有坏处
                () -> assertEquals("未通过", view.statusText()),
                () -> assertEquals("FAILED", view.status()),
                /*
                 * remark 那一列在风控拦截时写的是拦截原因。整个 RecordView
                 * 里都不该出现它 —— 泄漏一次就够用户摸清阈值了。
                 */
                () -> assertFalse(view.toString().contains("超限"), "拦截原因泄漏了: " + view),
                () -> assertFalse(view.toString().contains("SINGLE_MAX"), "风控码泄漏了: " + view));
    }

    @Test
    @DisplayName("🔴 驳回与风控拦截给完全一样的话 —— 能区分就能被摸出规律")
    void 驳回和拦截说法一致() {
        RecordView rejected = promoOf(ProposalStatusEnum.REJECTED, "运营驳回");
        RecordView blocked = promoOf(ProposalStatusEnum.RISK_BLOCKED, "命中频次限制");

        assertAll(
                () -> assertEquals(rejected.statusText(), blocked.statusText()),
                () -> assertEquals(rejected.status(), blocked.status()));
    }

    @Test
    @DisplayName("审批的四个环节对用户都是「处理中」，不摊内部流程")
    void 审批环节统一说处理中() {
        for (ProposalStatusEnum status : List.of(
                ProposalStatusEnum.WAITING, ProposalStatusEnum.FIRST_REVIEW,
                ProposalStatusEnum.SECOND_REVIEW, ProposalStatusEnum.PENDING_EXECUTE,
                ProposalStatusEnum.EXECUTING)) {
            RecordView view = promoOf(status, null);
            assertEquals("处理中", view.statusText(), status + " 不该暴露内部环节");
            assertEquals("PENDING", view.status(), status.toString());
        }
    }

    @Test
    @DisplayName("部分成功说「部分到账」，不能说成「已到账」")
    void 部分成功不说成已到账() {
        // 说成已到账，用户会以为全到了，然后发现少了一半
        assertEquals("部分到账", promoOf(ProposalStatusEnum.PARTIAL_SUCCESS, null).statusText());
    }

    @Test
    @DisplayName("🔴 积分显示整数，现金保留小数 —— 库里是同一个 decimal(13,4)")
    void 积分不带小数() {
        RecordView score = promoOf(ProposalStatusEnum.SUCCESS, null,
                "SCORE", new BigDecimal("45000.0000"));
        RecordView cash = promoOf(ProposalStatusEnum.SUCCESS, null,
                "BALANCE", new BigDecimal("10.5000"));

        assertAll(
                // 「45000.0000 积分」是错的展示
                () -> assertEquals("45000", score.amount()),
                () -> assertEquals("10.5", cash.amount()));
    }

    @Test
    @DisplayName("值类资产没有名字时用类型兜底，不显示空标题")
    void 无名资产用类型兜底() {
        ProposalRecordView record = new ProposalRecordView(
                1L, "SCORE", null, null, BigDecimal.TEN,
                ProposalStatusEnum.SUCCESS, null, LocalDateTime.now());
        assertEquals("积分", translate(record).title());
    }

    /* ---------------- 兑换记录（订单） ---------------- */

    @Test
    @DisplayName("🔴 履约失败要说清积分还在不在")
    void 履约失败说明积分未退() {
        OrderView view = orderOf(MallOrderStatusEnum.FAILED, 45000, BigDecimal.ZERO);

        assertAll(
                () -> assertEquals("发放失败", view.statusText()),
                () -> assertNotNull(view.hint()),
                /*
                 * 用户看到「发放失败」第一个念头就是「我的积分呢」。
                 * 履约失败刻意不退积分（东西还欠着他，不是没买），
                 * 不明说的话他会以为积分白扣了。
                 */
                () -> assertTrue(view.hint().contains("积分未退回"), view.hint()));
    }

    @Test
    @DisplayName("已取消要说「积分已退回」—— 和失败是相反的两件事")
    void 已取消说明积分已退() {
        assertTrue(orderOf(MallOrderStatusEnum.CANCELLED, 45000, BigDecimal.ZERO)
                .hint().contains("积分已退回"));
    }

    @Test
    @DisplayName("🔴 域侧的失败原因是给运营看的，不下发给用户")
    void 运营话术不下发() {
        MallOrderView order = new MallOrderView(
                "M2026", 1L, "限量T恤", "COUPON", null, Map.of(), 1, 5000, BigDecimal.ZERO,
                MallOrderStatusEnum.FAILED, "券商品未配置券模编码（asset_ref），补齐后可重发",
                LocalDateTime.now());
        OrderView view = translate(order);

        // 「asset_ref」这种话甩给用户，他既看不懂也无从下手
        assertFalse(view.toString().contains("asset_ref"), "运营话术泄漏了: " + view);
        assertFalse(view.toString().contains("券模编码"), "运营话术泄漏了: " + view);
    }

    @Test
    @DisplayName("纯积分单不写「+ ¥0.00」—— 那截会让人以为还要再付钱")
    void 纯积分不拼现金() {
        assertEquals("45,000 积分",
                orderOf(MallOrderStatusEnum.FINISHED, 45000, BigDecimal.ZERO).cost());
        assertEquals("45,000 积分 + ¥299.00",
                orderOf(MallOrderStatusEnum.FINISHED, 45000, new BigDecimal("299.00")).cost());
    }

    @Test
    @DisplayName("待履约与履约中对用户是同一件事：东西还没到")
    void 履约两态合并说处理中() {
        assertEquals(orderOf(MallOrderStatusEnum.PENDING, 1, BigDecimal.ZERO).statusText(),
                orderOf(MallOrderStatusEnum.FULFILLING, 1, BigDecimal.ZERO).statusText());
    }

    @Test
    @DisplayName("已完成没有多余的提示行")
    void 已完成不画提示() {
        assertNull(orderOf(MallOrderStatusEnum.FINISHED, 1, BigDecimal.ZERO).hint());
    }

    /* ---------------- 造数 ---------------- */

    private static RecordView promoOf(ProposalStatusEnum status, String remark) {
        return promoOf(status, remark, "SCORE", BigDecimal.TEN);
    }

    private static RecordView promoOf(ProposalStatusEnum status, String remark,
                                      String assetType, BigDecimal amount) {
        return translate(new ProposalRecordView(1L, assetType, "REF", "新人礼包",
                amount, status, remark, LocalDateTime.now()));
    }

    private static RecordView translate(ProposalRecordView record) {
        RecordService service = new RecordService(null, stubProposalApi(record));
        return service.listPromoRecords(1L).getFirst();
    }

    private static ProposalRecordApi stubProposalApi(ProposalRecordView record) {
        return (memberId, limit) -> List.of(record);
    }

    private static OrderView orderOf(MallOrderStatusEnum status, int points, BigDecimal cash) {
        return translate(new MallOrderView(
                "M2026", 1L, "限量T恤", "PHYSICAL", null, Map.of("颜色", "黑"),
                1, points, cash, status, null, LocalDateTime.now()));
    }

    /** 翻译是私有静态的，从公开入口走一遍拿结果 */
    private static OrderView translate(MallOrderView order) {
        MallApi mallApi = mock(MallApi.class);
        when(mallApi.listMyOrders(anyLong(), anyInt())).thenReturn(List.of(order));
        return new OrderService(mallApi).listMyOrders(1L).getFirst();
    }
}
