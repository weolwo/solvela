package solvela.member.risk.feature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.json.JsonUtils;
import solvela.enums.MemberRiskDecisionEnum;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 规则参数的解析与校验。
 *
 * <h3>为什么这块值得单测</h3>
 * {@code params} 是<b>后台可改</b>的，而配错的表现全是静默的：要么这条规则直接关掉
 * （参数读不出来 → 当作关闭），要么「配了却不起作用」。所以这里钉住的主要是**拒绝**：
 * 什么样的参数必须存不进去。
 *
 * @Date 2026-10-15
 */
@DisplayName("风控规则参数")
class RiskParamsTest {

    private static JsonNode json(String raw) {
        return JsonUtils.getMapper().readTree(raw);
    }

    @Test
    @DisplayName("时长认 s / m / h / d")
    void parseDuration() {
        assertEquals(Duration.ofSeconds(60), RiskParams.parseDuration("60s", "window"));
        assertEquals(Duration.ofMinutes(5), RiskParams.parseDuration("5m", "window"));
        assertEquals(Duration.ofHours(1), RiskParams.parseDuration("1h", "window"));
        assertEquals(Duration.ofDays(1), RiskParams.parseDuration("1d", "window"));
    }

    @Test
    @DisplayName("🔴 裸数字不接受：「60」是秒还是分钟，看的人一半会猜错，而猜错不会报错")
    void bareNumberRejected() {
        assertThrows(IllegalArgumentException.class, () -> RiskParams.parseDuration("60", "window"));
    }

    @Test
    @DisplayName("非法时长一律抛，不悄悄退回默认值")
    void badDurationRejected() {
        assertThrows(IllegalArgumentException.class, () -> RiskParams.parseDuration("1w", "window"));
        assertThrows(IllegalArgumentException.class, () -> RiskParams.parseDuration("0s", "window"));
        assertThrows(IllegalArgumentException.class, () -> RiskParams.parseDuration("-5m", "window"));
        assertThrows(IllegalArgumentException.class, () -> RiskParams.parseDuration("h", "window"));
    }

    @Test
    @DisplayName("字段缺失时用默认值 —— 缺失是常态，不是错误")
    void missingFieldFallsBack() {
        assertEquals(Duration.ofHours(2), RiskParams.duration(json("{}"), "window", Duration.ofHours(2)));
        assertEquals(7, RiskParams.positiveInt(json("{}"), "free", 7));
    }

    @Test
    @DisplayName("分级按 over 升序排好，命中取超过的那一档里最大的")
    void tiersSortedAndMatched() {
        List<RiskParams.Tier> tiers = RiskParams.tiers(
                json("""
                        {"tiers": [{"over": 20, "do": 6}, {"over": 5, "do": 2}]}
                        """));

        assertEquals(5, tiers.get(0).over());
        assertEquals(MemberRiskDecisionEnum.CAPTCHA, tiers.get(0).decision());

        assertNull(RiskParams.hit(tiers, 5), "等于阈值不算超 —— 与改造前的 count > max 一致");
        assertEquals(MemberRiskDecisionEnum.CAPTCHA, RiskParams.hit(tiers, 6).decision());
        assertEquals(MemberRiskDecisionEnum.CAPTCHA, RiskParams.hit(tiers, 20).decision());
        assertEquals(MemberRiskDecisionEnum.LIMIT, RiskParams.hit(tiers, 21).decision());
    }

    @Test
    @DisplayName("🔴 处置不随 over 变严 → 拒绝保存：超得更多的人反而被放松了")
    void nonMonotonicSeverityRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RiskParams.tiers(json("""
                        {"tiers": [{"over": 5, "do": 6}, {"over": 20, "do": 2}]}
                        """)));
        assertEquals(true, e.getMessage().contains("越来越严"));
    }

    @Test
    @DisplayName("🔴 两档 over 相同 → 拒绝保存：后一档永远轮不到")
    void duplicateOverRejected() {
        assertThrows(IllegalArgumentException.class, () -> RiskParams.tiers(json("""
                {"tiers": [{"over": 5, "do": 2}, {"over": 5, "do": 6}]}
                """)));
    }

    @Test
    @DisplayName("tiers 为空或处置值非法 → 拒绝保存")
    void emptyOrBadTiersRejected() {
        assertThrows(IllegalArgumentException.class, () -> RiskParams.tiers(json("{}")));
        assertThrows(IllegalArgumentException.class, () -> RiskParams.tiers(json("{\"tiers\": []}")));
        assertThrows(IllegalArgumentException.class,
                () -> RiskParams.tiers(json("{\"tiers\": [{\"over\": 5, \"do\": 99}]}")));
        assertThrows(IllegalArgumentException.class,
                () -> RiskParams.tiers(json("{\"tiers\": [{\"over\": -1, \"do\": 2}]}")));
    }

    @Test
    @DisplayName("首批规则里那几份真实参数都解析得出来")
    void seedParamsParse() {
        assertEquals(MemberRiskDecisionEnum.LIMIT, RiskParams.hit(RiskParams.tiers(
                json("{\"window\": \"1h\", \"tiers\": [{\"over\": 60, \"do\": 6}]}")), 61).decision());
        assertEquals(MemberRiskDecisionEnum.CAPTCHA, RiskParams.hit(RiskParams.tiers(
                        json("{\"window\": \"1h\", \"tiers\": [{\"over\": 3, \"do\": 2}, {\"over\": 10, \"do\": 6}]}")), 4)
                .decision());
        assertEquals(Duration.ofDays(1), RiskParams.duration(
                json("{\"window\": \"1d\", \"tiers\": [{\"over\": 30, \"do\": 6}], \"observe\": true}"),
                "window", Duration.ofHours(1)));
    }
}
