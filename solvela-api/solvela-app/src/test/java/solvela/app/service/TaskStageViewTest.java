package solvela.app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.app.domain.TaskView;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.TaskCenterItem;
import solvela.marketing.api.TaskStageView;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 阶梯任务的展示。
 *
 * <h3>取自一个真实配置</h3>
 * 任务 51「每日签到」：
 * <ul>
 *   <li>档位 1 —— 签到 <b>1</b> 天，得「积分188」</li>
 *   <li>档位 2 —— 连签 <b>5</b> 天，得「红包8」</li>
 * </ul>
 *
 * <p>旧版把它压成「目标 5」+「积分188 / 红包8」一句话。两个问题：
 * <b>看不出哪个奖对应哪一档</b>，而且签到 1 天真拿到 188 积分之后，
 * <b>界面上没有任何变化</b> —— 用户不知道自己已经得手了。
 *
 * @Date 2026-09-05
 */
class TaskStageViewTest {

    @Test
    @DisplayName("🔴 两档分开出，各自带阈值和奖励，不拼成一句话")
    void 档位分开展示() {
        TaskView view = translate(signInTask(BigDecimal.ZERO));

        assertAll(
                () -> assertEquals(2, view.stages().size()),
                () -> assertEquals("1", view.stages().get(0).target()),
                () -> assertEquals("积分188", view.stages().get(0).rewardText()),
                () -> assertEquals("5", view.stages().get(1).target()),
                () -> assertEquals("红包8", view.stages().get(1).rewardText()),
                /*
                 * 多档不给那行摘要：拼成「积分188 / 红包8」正是这次要修掉的展示。
                 * 前端据此决定画阶梯而不是画一行。
                 */
                () -> assertNull(view.rewardText()));
    }

    @Test
    @DisplayName("🔴 签到 1 天：第一档已达标要看得出来，第二档还没")
    void 已达标的档位标出来() {
        TaskView view = translate(signInTask(BigDecimal.ONE));

        assertAll(
                // 不标出来的话，用户拿到了 188 积分而界面毫无变化
                () -> assertTrue(view.stages().get(0).reached(), "签到 1 天，第一档该是已达标"),
                () -> assertFalse(view.stages().get(1).reached(), "才 1 天，第二档不该已达标"));
    }

    @Test
    @DisplayName("进度条的满格值是最高档的阈值")
    void 进度条按最高档() {
        // 5 来自档位 2 的 stage_condition，不是 rule_config.targetCount ——
        // 两者今天碰巧相等，但发奖判的是前者，进度条必须跟着发奖走
        assertEquals("5", translate(signInTask(BigDecimal.ONE)).target());
    }

    @Test
    @DisplayName("单档任务照旧给一行摘要，不画阶梯")
    void 单档给摘要() {
        TaskCenterItem item = new TaskCenterItem(
                49L, "每日浏览", "DAILY", BigDecimal.ONE, BigDecimal.ZERO, null,
                List.of(new TaskStageView(1, BigDecimal.ONE, "积分10", false)),
                null, 90);
        TaskView view = translate(item);

        assertAll(
                () -> assertEquals("积分10", view.rewardText()),
                () -> assertEquals(1, view.stages().size()));
    }

    /* ---------------- 造数 ---------------- */

    /** 任务 51 的真实形状：1 天 → 积分188，5 天 → 红包8 */
    private static TaskCenterItem signInTask(BigDecimal current) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal five = new BigDecimal("5");
        return new TaskCenterItem(
                51L, "每日签到", "DAILY", five, current, null,
                List.of(
                        new TaskStageView(1, one, "积分188", current.compareTo(one) >= 0),
                        new TaskStageView(2, five, "红包8", current.compareTo(five) >= 0)),
                "/signIn", 60);
    }

    private static TaskView translate(TaskCenterItem item) {
        ActivityApi api = mock(ActivityApi.class);
        when(api.getMyTasks(anyLong())).thenReturn(List.of(item));
        return new TaskService(api).listMyTasks(1L).getFirst();
    }
}
