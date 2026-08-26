package solvela.base.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static solvela.base.util.SolvelaCaseFormat.LOWER_CAMEL;
import static solvela.base.util.SolvelaCaseFormat.LOWER_HYPHEN;
import static solvela.base.util.SolvelaCaseFormat.LOWER_UNDERSCORE;
import static solvela.base.util.SolvelaCaseFormat.UPPER_CAMEL;
import static solvela.base.util.SolvelaCaseFormat.UPPER_UNDERSCORE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SolvelaCaseFormat} 语义固化测试。
 *
 * <p><b>这些期望值不是我想出来的，是跑出来的</b>：移除 Guava 之前，用 5×5×33 = 825 种
 * 「源格式 × 目标格式 × 语料」的组合与 {@code com.google.common.base.CaseFormat}
 * 逐条比对，全部一致后才删的依赖。本测试把其中的代表性用例固化下来，
 * 防止以后有人"顺手优化"实现时悄悄改掉语义。
 *
 * <p>⚠️ 改这个类之前先想清楚：代码生成器产出的类名/字段名、数据变更追踪的列名映射
 * 都依赖它，语义一变，生成出来的代码和历史数据的字段对不上。
 *
 * @author 1024创新实验室
 */
class SolvelaCaseFormatTest {

    @Test
    @DisplayName("项目实际用到的 7 种组合")
    void testUsedCombinations() {
        // 代码生成器：模块名 -> 各种产物命名
        assertEquals("UserName", LOWER_CAMEL.to(UPPER_CAMEL, "userName"));
        assertEquals("userName", UPPER_CAMEL.to(LOWER_CAMEL, "UserName"));
        assertEquals("user-name", LOWER_CAMEL.to(LOWER_HYPHEN, "userName"));
        assertEquals("user-name", UPPER_CAMEL.to(LOWER_HYPHEN, "UserName"));
        assertEquals("user_name", UPPER_CAMEL.to(LOWER_UNDERSCORE, "UserName"));
        assertEquals("USER_NAME", UPPER_CAMEL.to(UPPER_UNDERSCORE, "UserName"));
        // 同格式转换是恒等操作
        assertEquals("UserName", UPPER_CAMEL.to(UPPER_CAMEL, "UserName"));
    }

    @Test
    @DisplayName("真实标识符：任务/奖池/枚举命名")
    void testRealIdentifiers() {
        assertEquals("TaskConfigId", LOWER_CAMEL.to(UPPER_CAMEL, "taskConfigId"));
        assertEquals("task_config_id", UPPER_CAMEL.to(LOWER_UNDERSCORE, "TaskConfigId"));
        assertEquals("TASK_CONFIG_ID", UPPER_CAMEL.to(UPPER_UNDERSCORE, "TaskConfigId"));
        assertEquals("prize-pool-config", UPPER_CAMEL.to(LOWER_HYPHEN, "PrizePoolConfig"));
        assertEquals("ORDER_STATUS_ENUM", UPPER_CAMEL.to(UPPER_UNDERSCORE, "OrderStatusEnum"));
    }

    @Test
    @DisplayName("🔴 连续大写：按 Guava 语义逐字母拆词，不做缩写识别")
    void testConsecutiveUpperCase() {
        // userURLName -> user|u|r|l|name，这正是 Guava 的行为。
        // 别"优化"成把 URL 当一个词 —— 那会让既有生成物的命名变样
        assertEquals("user_u_r_l_name", LOWER_CAMEL.to(LOWER_UNDERSCORE, "userURLName"));
        assertEquals("USER_I_D", LOWER_CAMEL.to(UPPER_UNDERSCORE, "userID"));
    }

    @Test
    @DisplayName("🔴 下划线↔中划线只换分隔符，不动大小写（Guava 的快捷路径）")
    void testDelimiterOnlyConversion() {
        // 这两对是 Guava 刻意的特例：不做大小写规范化。
        // 项目当前没用到，但实现里保留了，免得以后有人用上时行为与 Guava 不符
        assertEquals("UserName", LOWER_UNDERSCORE.to(LOWER_HYPHEN, "UserName"));
        assertEquals("USER-NAME", LOWER_UNDERSCORE.to(LOWER_HYPHEN, "USER_NAME"));
        assertEquals("UserName", LOWER_HYPHEN.to(LOWER_UNDERSCORE, "UserName"));
        assertEquals("TASK_CONFIG_ID", LOWER_HYPHEN.to(UPPER_UNDERSCORE, "task-config-id"));
        assertEquals("USER_NAME", LOWER_UNDERSCORE.to(UPPER_UNDERSCORE, "user_name"));
    }

    @Test
    @DisplayName("边界：null / 空串 / 单字母 / 带数字")
    void testEdgeCases() {
        assertEquals(null, LOWER_CAMEL.to(UPPER_CAMEL, null));
        assertEquals("", LOWER_CAMEL.to(UPPER_CAMEL, ""));
        assertEquals("A", LOWER_CAMEL.to(UPPER_CAMEL, "a"));
        assertEquals("a", UPPER_CAMEL.to(LOWER_CAMEL, "A"));
        assertEquals("user2_name", LOWER_CAMEL.to(LOWER_UNDERSCORE, "user2Name"));
        assertEquals("X1", LOWER_CAMEL.to(UPPER_CAMEL, "x1"));
    }
}
