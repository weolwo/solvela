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
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.config.ListMatch;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.impl.RegisterDeviceBannedRule;
import solvela.member.risk.rule.impl.RegisterDeviceLimitRule;
import solvela.member.risk.rule.impl.RegisterIpBlacklistRule;
import solvela.member.risk.rule.impl.RegisterIpLimitRule;
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
 * 注册场景的四条规则（2026-10-16 从 {@code DeviceGuard.checkRegister} 与
 * {@code MemberRegisterService.consumeAttempt} 迁过来）。
 *
 * <h3>这里钉住的是「注册和登录不是同一回事」</h3>
 * <ol>
 *   <li><b>场景与阶段写死在代码里</b>：一条注册规则被改成登录场景，会在一个完全不同量级的
 *       流量上执行，而那不会报错；</li>
 *   <li><b>档位只能配放行 / 限流 / 拒绝</b>：滑块与 OTP 在注册这条链路上没有落点，
 *       配上去的后果是「注册接口返回一个客户端不认识的结果，而规则页上那一行看着很正常」；</li>
 *   <li><b>两维各算各的</b>：IP 走代理池就换，设备号得先过一次签发限频 —— 都要过。</li>
 * </ol>
 *
 * @Date 2026-10-16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("注册场景的规则")
class RegisterRuleTest {

    @Mock
    private LoginCounters counters;

    @Mock
    private RiskListCache lists;

    @Mock
    private DeviceDispositionService dispositionService;

    // ------------------------------------------------------------------ 场景与阶段

    @Test
    @DisplayName("🔴 四条都写死在【注册 + 前置】—— 表里改不动它们")
    void 场景与阶段() {
        for (RiskRule rule : new RiskRule[]{
                new RegisterIpLimitRule(counters),
                new RegisterDeviceLimitRule(counters),
                new RegisterIpBlacklistRule(lists),
                new RegisterDeviceBannedRule(dispositionService)}) {
            assertEquals(MemberRiskEventTypeEnum.REGISTER, rule.eventType(), rule.code());
            // 注册没有「验完再判一次」的位置：验证码验过账号就建出来了
            assertEquals(MemberRiskPhaseEnum.PRE, rule.phase(), rule.code());
        }
    }

    @Test
    @DisplayName("黑名单与设备封禁不受陪跑影响 —— 人工签过字的结论不该被保命开关放掉")
    void 强规则一律生效() {
        assertTrue(new RegisterIpBlacklistRule(lists).alwaysEnforce());
        assertTrue(new RegisterDeviceBannedRule(dispositionService).alwaysEnforce());
    }

    // ------------------------------------------------------------------ 档位

    @Test
    @DisplayName("🔴 配成滑块 / OTP / 冷却 → 保存那一刻就拒绝")
    void 拒绝执行不了的档位() {
        RegisterIpLimitRule rule = new RegisterIpLimitRule(counters);
        for (int decision : new int[]{2, 3, 4, 5}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> rule.validate(tiers(10, decision)));
            assertTrue(e.getMessage().contains("注册场景"), e.getMessage());
        }
    }

    @Test
    @DisplayName("放行 / 限流 / 拒绝都能配")
    void 允许的档位() {
        RegisterDeviceLimitRule rule = new RegisterDeviceLimitRule(counters);
        rule.validate(tiers(2, 1));
        rule.validate(tiers(2, 6));
        rule.validate(tiers(2, 7));
    }

    // ------------------------------------------------------------------ 计数口径

    @Test
    @DisplayName("🔴 两维都【把这一次算进去】：注册请求本身就是一次建号尝试")
    void 都算这一次() {
        when(counters.bump(LoginCounters.Counter.REGISTER_IP, "1.2.3.4")).thenReturn(11L);
        when(counters.bump(LoginCounters.Counter.REGISTER_DEVICE, "dev-1")).thenReturn(3L);
        when(counters.retryAfterSeconds(any(), anyString())).thenReturn(600L);

        RiskHit ip = new RegisterIpLimitRule(counters).evaluate(ctx(), config(tiers(10, 6)));
        RiskHit device = new RegisterDeviceLimitRule(counters).evaluate(ctx(), config(tiers(2, 6)));

        assertEquals(MemberRiskDecisionEnum.LIMIT, ip.decision());
        assertEquals(MemberRiskDecisionEnum.LIMIT, device.decision());
        assertEquals(600L, ip.retryAfterSeconds(), "不告诉他要等多久，他只会立刻再点一次");
        verify(counters, never()).read(any(), anyString());
    }

    @Test
    @DisplayName("等于阈值不算超（与改造前的 count > max 一致）")
    void 边界() {
        when(counters.bump(LoginCounters.Counter.REGISTER_DEVICE, "dev-1")).thenReturn(2L);

        assertNull(new RegisterDeviceLimitRule(counters).evaluate(ctx(), config(tiers(2, 6))));
    }

    @Test
    @DisplayName("🔴 维度取不到时不判：拿不到 IP 不该变成「全站注册不可用」")
    void 维度取不到不判() {
        RiskContext noIp = new RiskContext(MemberRiskEventTypeEnum.REGISTER, MemberLoginType.PHONE_PASSWORD,
                "13800000000", "ab".repeat(32), null, null, "H5", null, false);

        // 一律拒绝的话，任何一次取 IP 失败都会让所有人注册不了 —— 那种故障比放过几个注册严重得多
        assertNull(new RegisterIpLimitRule(counters).evaluate(noIp, config(tiers(10, 6))));
        // 没带设备令牌是常态（老客户端），不是可疑
        assertNull(new RegisterDeviceLimitRule(counters).evaluate(noIp, config(tiers(2, 6))));
    }

    @Test
    @DisplayName("设备维度命中生效时请求观察档 —— params.observe 关掉就不请求")
    void 观察档动作() {
        when(counters.bump(LoginCounters.Counter.REGISTER_DEVICE, "dev-1")).thenReturn(3L);
        RegisterDeviceLimitRule rule = new RegisterDeviceLimitRule(counters);

        JsonNode observe = JsonUtils.getMapper()
                .readTree("{\"window\":\"1d\",\"observe\":true,\"tiers\":[{\"over\":2,\"do\":6}]}");
        assertTrue(rule.evaluate(ctx(), config(observe)).actions().contains(RiskAction.OBSERVE_DEVICE));
        assertTrue(rule.evaluate(ctx(), config(tiers(2, 6))).actions().isEmpty());
    }

    // ------------------------------------------------------------------ 名单与封禁

    @Test
    @DisplayName("IP 在黑名单 → 给表里那一档（拒绝），命中依据带上条目 id")
    void 黑名单() {
        when(lists.matchIp("1.2.3.4")).thenReturn(new ListMatch(77L, false, 0));

        RiskHit hit = new RegisterIpBlacklistRule(lists)
                .evaluate(ctx(), direct(MemberRiskDecisionEnum.DENY));

        assertEquals(MemberRiskDecisionEnum.DENY, hit.decision());
        // 客服顺着它就能找到是谁、什么时候、为什么加的
        assertTrue(hit.detail().contains("77"), hit.detail());
    }

    @Test
    @DisplayName("🔴 设备被人工封禁 → 拒绝建号（改造前这道闸在 checkRegister 的第一行）")
    void 设备封禁() {
        when(dispositionService.currentStatus("dev-1")).thenReturn(DeviceStatusEnum.BANNED);

        assertEquals(MemberRiskDecisionEnum.DENY, new RegisterDeviceBannedRule(dispositionService)
                .evaluate(ctx(), direct(MemberRiskDecisionEnum.DENY)).decision());
    }

    @Test
    @DisplayName("设备正常 / 没带设备号 → 不判")
    void 设备没被封时不判() {
        when(dispositionService.currentStatus(anyString())).thenReturn(DeviceStatusEnum.NORMAL);

        assertNull(new RegisterDeviceBannedRule(dispositionService)
                .evaluate(ctx(), direct(MemberRiskDecisionEnum.DENY)));
    }

    // ------------------------------------------------------------------ 脚手架

    /** 注册场景的上下文：<b>member 恒为 null</b>，没有滑块票 */
    private static RiskContext ctx() {
        return new RiskContext(MemberRiskEventTypeEnum.REGISTER, MemberLoginType.PHONE_PASSWORD,
                "13800000000", "ab".repeat(32), null, "dev-1", "H5", "1.2.3.4", false);
    }

    /** {@code tiers(10, 6)} = 超 10 限流 */
    private static JsonNode tiers(int over, int decision) {
        return JsonUtils.getMapper().readTree("{\"window\":\"1h\",\"tiers\":[{\"over\":"
                + over + ",\"do\":" + decision + "}]}");
    }

    private static RiskRuleConfig config(JsonNode params) {
        return new RiskRuleConfig("X", "X", MemberRiskEventTypeEnum.REGISTER, MemberRiskPhaseEnum.PRE,
                MemberRiskRuleTypeEnum.TIERED, null, 0, null, MemberRiskModeEnum.ENFORCE, false, params);
    }

    private static RiskRuleConfig direct(MemberRiskDecisionEnum decision) {
        return new RiskRuleConfig("X", "X", MemberRiskEventTypeEnum.REGISTER, MemberRiskPhaseEnum.PRE,
                MemberRiskRuleTypeEnum.DIRECT, decision, 0, null, MemberRiskModeEnum.ENFORCE, false,
                JsonUtils.getMapper().createObjectNode());
    }
}
