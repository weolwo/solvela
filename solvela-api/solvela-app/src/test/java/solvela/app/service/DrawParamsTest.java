package solvela.app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.marketing.api.DrawParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 抽奖 {@code params} 的契约（{@link DrawParams}）：全网关唯一一个「什么都能装」的入参。
 *
 * @Date 2026-10-17
 */
class DrawParamsTest {

    private static final Set<String> DECLARED = Set.of("seat", "answers");

    @Test
    @DisplayName("没带、带空对象 → 放行（今天的前端从不带 params）")
    void absentIsFine() {
        assertNull(DrawParams.violation(null));
        assertNull(DrawParams.violation(Map.of()));
    }

    @Test
    @DisplayName("🔴 今天没有声明任何键：带什么都拒，并说出是哪个键")
    void undeclaredKeyIsRejected() {
        assertEquals("不认识的参数 poolCode", DrawParams.violation(Map.of("poolCode", "VIP")),
                "这正是 DrawRequest 刻意没有 poolCode 的原因 —— 不能从 params 绕回来");
    }

    @Test
    @DisplayName("声明过的键：字符串、数字、布尔、一层对象或数组都行")
    void declaredShapesPass() {
        assertNull(DrawParams.violation(Map.of("seat", "A1"), DECLARED));
        assertNull(DrawParams.violation(Map.of("seat", 3, "answers", List.of("a", true, 1.5)), DECLARED));
    }

    @Test
    @DisplayName("🔴 形状封顶：键太多、字符串太长、嵌套太深、元素太多")
    void shapesAreCapped() {
        Map<String, Object> many = new HashMap<>();
        for (int i = 0; i <= DrawParams.MAX_ENTRIES; i++) {
            many.put("k" + i, i);
        }
        assertEquals("自定义参数最多 " + DrawParams.MAX_ENTRIES + " 个", DrawParams.violation(many, DECLARED));
        assertEquals("参数 seat 过长",
                DrawParams.violation(Map.of("seat", "x".repeat(DrawParams.MAX_STRING_LENGTH + 1)), DECLARED));
        assertEquals("参数 answers 嵌套太深",
                DrawParams.violation(Map.of("answers", List.of(List.of(1))), DECLARED));
        assertEquals("参数 answers 元素太多",
                DrawParams.violation(Map.of("answers", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9)), DECLARED));
    }
}
