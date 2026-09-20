package solvela.task.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「等级 ≥ N」人群取值的解析。
 *
 * <h3>🔴 这里守的是「写错了会往哪一边倒」</h3>
 * 门槛值是编在取值串里的（{@code GRADE_GTE_2}），所以它可能被写坏。
 * 解析出 {@code null} 的那些取值，在 {@code TaskRecordAdvanceService} 里会被
 * <b>丢弃</b>而不是放行 —— 放行意味着普通会员领走白金专享奖，方向正好反了。
 *
 * <p>所以这个函数<b>宁可返回 null</b>，也不要「猜一个数出来」。
 *
 * @Date 2026-09-18
 */
class TaskAudienceGradeTest {

    @Test
    @DisplayName("正常取值：GRADE_GTE_2 解析成 2")
    void 正常解析() {
        assertEquals(2, TaskConst.gradeThresholdOf("GRADE_GTE_2"));
        assertEquals(0, TaskConst.gradeThresholdOf("GRADE_GTE_0"), "0 是合法门槛：等于「所有人」，但它是显式配出来的");
        assertEquals(12, TaskConst.gradeThresholdOf("GRADE_GTE_12"), "档位可以超过 9，别按单字符解析");
    }

    @Test
    @DisplayName("不是等级人群的取值一律返回 null，不要误判")
    void 非等级人群() {
        assertNull(TaskConst.gradeThresholdOf(TaskConst.AUDIENCE_ALL));
        assertNull(TaskConst.gradeThresholdOf(TaskConst.AUDIENCE_NEW_MEMBER));
        assertNull(TaskConst.gradeThresholdOf(TaskConst.AUDIENCE_OLD_MEMBER));
        assertNull(TaskConst.gradeThresholdOf(null));
        assertNull(TaskConst.gradeThresholdOf(""));
        // 前缀要完整匹配：这几个都不是等级人群
        assertNull(TaskConst.gradeThresholdOf("LEVEL_GT_2"));
        assertNull(TaskConst.gradeThresholdOf("MY_GRADE_GTE_2"));
    }

    @Test
    @DisplayName("🔴 数字写坏时返回 null —— 交给调用方丢弃，不要猜一个数出来")
    void 数字写坏() {
        assertNull(TaskConst.gradeThresholdOf("GRADE_GTE_"), "只有前缀没有数字");
        assertNull(TaskConst.gradeThresholdOf("GRADE_GTE_二"), "中文数字");
        assertNull(TaskConst.gradeThresholdOf("GRADE_GTE_2.5"), "等级是整数");
        assertNull(TaskConst.gradeThresholdOf("GRADE_GTE_ 2"), "带空格");
        assertNull(TaskConst.gradeThresholdOf("GRADE_GTE_99999999999"), "超出 int 值域");
    }

    @Test
    @DisplayName("🔴 负数门槛按非法处理，不当成 0 悄悄放行")
    void 负数门槛() {
        /*
         * GRADE_GTE_-1 在数学上等于「所有人」，但它一定是配错的。
         * 悄悄当成 0 放行的话，运营以为自己配了等级专享，实际所有人都能做，
         * 而页面上、日志里都看不出任何异常。
         */
        assertNull(TaskConst.gradeThresholdOf("GRADE_GTE_-1"));
    }
}
