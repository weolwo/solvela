package solvela.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 严格 mapper 的解析上限与响应形状（{@link InboundJsonConfig}）。接线（真的接在 Controller 上）见 {@code InboundStrictJsonTest}。
 *
 * @Date 2026-10-17
 */
class InboundJsonConfigTest {

    private final JsonMapper strict = InboundJsonConfig.inboundMapper(JsonFactory.builder().build(),
            List.of(new AppJsonConfig().appJsonCustomizer()));

    @Test
    @DisplayName("🔴 嵌套超过上限 → 解析阶段就失败（Jackson 默认 500 层）")
    void nestingIsCapped() {
        int depth = InboundJsonConfig.MAX_NESTING_DEPTH;
        assertDoesNotThrow(() -> strict.readValue("[".repeat(depth) + "]".repeat(depth), Object.class));
        assertThrows(Exception.class,
                () -> strict.readValue("[".repeat(depth + 1) + "]".repeat(depth + 1), Object.class));
    }

    @Test
    @DisplayName("数字、字段名超过上限 → 解析阶段就失败（默认数字 1000 位、字段名 5 万字符）")
    void numberAndNameAreCapped() {
        String longNumber = "1".repeat(InboundJsonConfig.MAX_NUMBER_LENGTH + 1);
        assertThrows(Exception.class, () -> strict.readValue("{\"n\":" + longNumber + "}", Map.class));
        String longName = "k".repeat(InboundJsonConfig.MAX_NAME_LENGTH + 1);
        assertThrows(Exception.class, () -> strict.readValue("{\"" + longName + "\":1}", Map.class));
    }

    @Test
    @DisplayName("🔴 响应的形状与应用的 mapper 相同：时间无时区、金额是字符串、超出 2^53 的 Long 是字符串")
    void writesTheSameShape() {
        Map<String, Object> view = Map.of(
                "time", LocalDateTime.of(2026, 10, 17, 9, 30, 0),
                "amount", new BigDecimal("12.50"),
                "big", 9_007_199_254_740_993L,
                "small", 42L);

        String json = strict.writeValueAsString(view);
        Map<?, ?> back = JsonMapper.builder().build().readValue(json, Map.class);

        assertEquals("2026-10-17 09:30:00", back.get("time"));
        assertEquals("12.50", back.get("amount"));
        assertEquals("9007199254740993", back.get("big"));
        assertEquals(42, back.get("small"));
    }
}
