package solvela.member.risk.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.json.JsonUtils;
import solvela.enums.DeviceStatusEnum;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import solvela.member.api.MemberLoginType;
import solvela.member.device.DeviceDispositionService;
import solvela.member.risk.RiskContext;
import solvela.member.risk.config.ListMatch;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.impl.ResetDeviceBannedRule;
import solvela.member.risk.rule.impl.ResetDeviceLimitRule;
import solvela.member.risk.rule.impl.ResetIpBlacklistRule;
import solvela.member.risk.rule.impl.ResetIpLimitRule;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 重置密码场景的四条规则（2026-10-16，P3 ②）。
 *
 * <h3>这里钉的是「它和注册同形，但代价不同」</h3>
 * 形状上的约束与注册一模一样（只有前置、只能放行 / 限流 / 拒绝），所以那几条照着钉一遍；
 * 不一样的是<b>命中生效的后果</b>：被挡下的人已经登不进账号了，重置密码是他最后一条自助出路。
 * 所以这里额外钉两件事：
 * <ol>
 *   <li>设备维度<b>不请求观察档</b> —— 把一台正在帮人找回账号的设备降档，等于连它的登录也一起罚；</li>
 *   <li>两条计数规则<b>把这一次提交算进去</b>，而不是只读 —— 否则提交多少次都不会超。</li>
 * </ol>
 *
 * @Date 2026-10-16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("重置密码场景的规则")
class ResetRuleTest {

    @Mock
    private LoginCounters counters;

    @Mock
    private RiskListCache lists;

    @Mock
    private DeviceDispositionService dispositionService;

    @Test
    @DisplayName("🔴 四条都写死在【重置密码 + 前置】")
    void 场景与阶段() {
        for (RiskRule rule : new RiskRule[]{
                new ResetIpLimitRule(counters),
                new ResetDeviceLimitRule(counters),
                new ResetIpBlacklistRule(lists),
                new ResetDeviceBannedRule(dispositionService)}) {
            assertEquals(MemberRiskEventTypeEnum.RESET_PASSWORD, rule.eventType(), rule.code());
            // 验证码验过就改密码了，没有「验完再判一次」的位置
            assertEquals(MemberRiskPhaseEnum.PRE, rule.phase(), rule.code());
        }
    }

    @Test
    @DisplayName("🔴 配成滑块 / OTP / 冷却 → 保存那一刻就拒绝（与注册共用那份校验）")
    void 拒绝执行不了的档位() {
        ResetIpLimitRule rule = new ResetIpLimitRule(counters);
        for (int decision : new int[]{2, 3, 4, 5}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> rule.validate(tiers(20, decision)));
            assertTrue(e.getMessage().contains("重置密码"), e.getMessage());
        }
        rule.validate(tiers(20, 6));
        rule.validate(tiers(20, 7));
    }

    @Test
    @DisplayName("🔴 两维都把这一次提交算进去")
    void 都算这一次() {
        when(counters.bump(LoginCounters.Counter.RESET_IP, "1.2.3.4")).thenReturn(21L);
        when(counters.bump(LoginCounters.Counter.RESET_DEVICE, "dev-1")).thenReturn(6L);
        when(counters.retryAfterSeconds(any(), anyString())).thenReturn(900L);

        RiskHit ip = new ResetIpLimitRule(counters).evaluate(ctx(), config(tiers(20, 6)));
        RiskHit device = new ResetDeviceLimitRule(counters).evaluate(ctx(), config(tiers(5, 6)));

        assertEquals(MemberRiskDecisionEnum.LIMIT, ip.decision());
        assertEquals(MemberRiskDecisionEnum.LIMIT, device.decision());
        // 走到这一步的人已经登不进去了，「请稍后再试」对他等于「不知道还有没有希望」
        assertEquals(900L, ip.retryAfterSeconds());
        verify(counters, never()).read(any(), anyString());
    }

    @Test
    @DisplayName("🔴 设备维度【不】请求观察档 —— 帮人找回账号的那台设备不该连登录一起被罚")
    void 不降设备档() {
        when(counters.bump(LoginCounters.Counter.RESET_DEVICE, "dev-1")).thenReturn(6L);

        // 即使有人在 params 里配了 observe，这条规则也不认它（它根本不读那个字段）
        JsonNode withObserve = JsonUtils.getMapper()
                .readTree("{\"window\":\"1d\",\"observe\":true,\"tiers\":[{\"over\":5,\"do\":6}]}");
        assertTrue(new ResetDeviceLimitRule(counters).evaluate(ctx(), config(withObserve))
                .actions().isEmpty());
    }

    @Test
    @DisplayName("等于阈值不算超；维度取不到时不判")
    void 边界与维度缺失() {
        when(counters.bump(LoginCounters.Counter.RESET_IP, "1.2.3.4")).thenReturn(20L);
        assertNull(new ResetIpLimitRule(counters).evaluate(ctx(), config(tiers(20, 6))));

        RiskContext noIp = new RiskContext(MemberRiskEventTypeEnum.RESET_PASSWORD,
                MemberLoginType.EMAIL_CODE, "a@x.com", "ab".repeat(32), null, null, null, null, false);
        // 拿不到 IP 不该变成「全站找不回密码」
        assertNull(new ResetIpLimitRule(counters).evaluate(noIp, config(tiers(20, 6))));
        assertNull(new ResetDeviceLimitRule(counters).evaluate(noIp, config(tiers(5, 6))));
    }

    @Test
    @DisplayName("黑名单与设备封禁：拒绝，且不受陪跑影响")
    void 名单与封禁() {
        when(lists.matchIp("1.2.3.4")).thenReturn(new ListMatch(77L, false, 0));
        when(dispositionService.currentStatus("dev-1")).thenReturn(DeviceStatusEnum.BANNED);

        ResetIpBlacklistRule black = new ResetIpBlacklistRule(lists);
        ResetDeviceBannedRule banned = new ResetDeviceBannedRule(dispositionService);

        assertEquals(MemberRiskDecisionEnum.DENY,
                black.evaluate(ctx(), direct(MemberRiskDecisionEnum.DENY)).decision());
        assertEquals(MemberRiskDecisionEnum.DENY,
                banned.evaluate(ctx(), direct(MemberRiskDecisionEnum.DENY)).decision());
        // 人工签过字的结论，保命开关不该把它放掉
        assertTrue(black.alwaysEnforce());
        assertTrue(banned.alwaysEnforce());
    }

    // ------------------------------------------------------------------ 脚手架

    /** 重置密码场景的上下文：<b>member 恒为 null</b>（这一步在验码之前，还不知道是谁） */
    private static RiskContext ctx() {
        return new RiskContext(MemberRiskEventTypeEnum.RESET_PASSWORD, MemberLoginType.EMAIL_CODE,
                "a@x.com", "ab".repeat(32), null, "dev-1", null, "1.2.3.4", false);
    }

    private static JsonNode tiers(int over, int decision) {
        return JsonUtils.getMapper().readTree("{\"window\":\"1h\",\"tiers\":[{\"over\":"
                + over + ",\"do\":" + decision + "}]}");
    }

    private static RiskRuleConfig config(JsonNode params) {
        return new RiskRuleConfig("X", "X", MemberRiskEventTypeEnum.RESET_PASSWORD,
                MemberRiskPhaseEnum.PRE, MemberRiskRuleTypeEnum.TIERED, null, 0, null,
                MemberRiskModeEnum.ENFORCE, false, params);
    }

    private static RiskRuleConfig direct(MemberRiskDecisionEnum decision) {
        return new RiskRuleConfig("X", "X", MemberRiskEventTypeEnum.RESET_PASSWORD,
                MemberRiskPhaseEnum.PRE, MemberRiskRuleTypeEnum.DIRECT, decision, 0, null,
                MemberRiskModeEnum.ENFORCE, false, JsonUtils.getMapper().createObjectNode());
    }
}
