package solvela.app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.app.domain.ActivityPrizeItem;
import solvela.app.domain.ActivityView;
import solvela.enums.ActivityStatusEnum;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.ActivityPrizeView;
import solvela.marketing.api.ActivityRuleView;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 活动详情下发的奖品盘面。
 *
 * <h3>取自一个真实事故</h3>
 * 2026-09-05：中秋活动（A8P0Y3ANGI）点进去什么都没有。
 * 数据是好的 —— 奖池里 10 个奖品全部启用，抽奖配置也是启用的。
 * 坏在<b>奖品有两个源</b>：抽奖引擎从 {@code t_prize_pool_item} 抽，
 * 而 C 端的转盘是从 {@code t_activity_display.extra_config} 里一段
 * <b>运营手写的 JSON</b> 解析的，而那一列是 null。
 *
 * <p>两个源的另一半后果更隐蔽：写了但和奖池对不上时，
 * 转盘会转出一个奖池里根本没有的奖，或者能中的奖压根不在盘面上。
 *
 * <h3>这里守两条</h3>
 * ① 盘面来自奖池，MARKER 是正常的一格；
 * ② <b>库存、限领、白名单一个字都不能出公网</b> —— 前两样能反推中奖概率，
 *    白名单里装的是会员号。
 *
 * @Date 2026-09-05
 */
class ActivityPrizeViewTest {

    @Test
    @DisplayName("奖品盘面原样下发，MARKER 翻成「谢谢参与」那一格")
    void 盘面下发() {
        ActivityView view = translate(List.of(
                new ActivityPrizeView("PIDOWALQUV", "Huawei Mate 90 Pro Max", "PHYSICAL", 1),
                new ActivityPrizeView("P628BXATGQ", "积分1888", "SCORE", 0),
                new ActivityPrizeView("PLNTDQBNI5", "谢谢参与", "MARKER", 0)));

        assertAll(
                () -> assertEquals(3, view.prizes().size()),
                () -> assertEquals("Huawei Mate 90 Pro Max", view.prizes().get(0).prizeName()),
                // prize_level = 1 才描金
                () -> assertTrue(view.prizes().get(0).featured()),
                () -> assertFalse(view.prizes().get(1).featured()),
                /*
                 * 🔴 MARKER 是「谢谢参与」那一格，是正常的一格，不是「没有奖品」。
                 * 它有真实编码 —— 抽中它时后端回的就是这个码，转盘靠它停位。
                 */
                () -> assertTrue(view.prizes().get(2).thanks()),
                () -> assertFalse(view.prizes().get(0).thanks()),
                () -> assertEquals("PLNTDQBNI5", view.prizes().get(2).prizeCode()));
    }

    @Test
    @DisplayName("🔴 前端不该认识 PrizeTypeEnum —— 下发的是布尔，不是类型字符串")
    void 不下发奖品类型() {
        ActivityView view = translate(List.of(
                new ActivityPrizeView("P1", "积分1888", "SCORE", 0),
                new ActivityPrizeView("P2", "谢谢参与", "MARKER", 0)));
        /*
         * 原样下发 prizeType 的话，前端就要维护一张「类型 → 怎么画」的映射表，
         * 而那是域里的字典 —— 域里加一种奖品类型时，那张表会静默说错话。
         */
        assertFalse(view.prizes().toString().contains("SCORE"), view.prizes().toString());
        assertFalse(view.prizes().toString().contains("MARKER"), view.prizes().toString());
    }

    @Test
    @DisplayName("🔴 库存 / 限领 / 白名单 / 面值一个都不许出现在 C 端形状里")
    void 不下发内部字段() {
        /*
         * 前三样能反推中奖概率；白名单里装的是<b>会员号</b>。
         * 这条读的是 record 的组件名，所以有人往 ActivityPrizeItem 上加字段时会红 ——
         * 而那正是这类泄漏发生的方式：顺手多带一个字段，没人觉得有什么。
         */
        List<String> leaky = List.of("stock", "count", "white", "value", "budget", "approve", "rate", "weight");
        for (RecordComponent component : ActivityPrizeItem.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            for (String bad : leaky) {
                assertFalse(name.contains(bad),
                        () -> "ActivityPrizeItem 上出现了 " + component.getName()
                                + " —— 库存/限领/白名单/面值都不该出公网："
                                + "前几样能反推中奖概率，白名单里装的是会员号");
            }
        }
    }

    @Test
    @DisplayName("非抽奖玩法没有盘面：空列表，不是 null")
    void 无奖品给空列表() {
        // null 会让端上多写一处判空，而「这个活动没有转盘」和「一格都没有」是同一件事
        assertTrue(translate(List.of()).prizes().isEmpty());
    }

    private static ActivityView translate(List<ActivityPrizeView> prizes) {
        ActivityApi api = org.mockito.Mockito.mock(ActivityApi.class);
        org.mockito.Mockito.when(api.getActivityRule("A8P0Y3ANGI")).thenReturn(new ActivityRuleView(
                "A8P0Y3ANGI", "中秋", "DRAW", ActivityStatusEnum.ONLINE,
                LocalDateTime.now().minusDays(1), null, LocalDateTime.now().plusDays(20),
                "中秋佳节", null, 126L, 126L, null, "中秋佳节", "花好月圆",
                null, "<p>规则</p>", prizes));
        return new ActivityService(api).getActivity("A8P0Y3ANGI");
    }
}
