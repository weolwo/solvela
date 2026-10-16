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
import solvela.member.device.DeviceDispositionService;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskDeviceTrust;
import solvela.member.risk.config.ListMatch;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.impl.AssetDeviceBannedRule;
import solvela.member.risk.rule.impl.AssetDeviceFanoutRule;
import solvela.member.risk.rule.impl.AssetIpBlacklistRule;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产出口场景的三条规则（2026-10-16，P3 ②）。
 *
 * <h3>这里钉的是「它和别的场景哪里不一样」</h3>
 * <ol>
 *   <li><b>它能要求核身</b>：唯一一个 OTP 有落点的非登录场景 —— 二次验证本来就长在这条链路上；</li>
 *   <li><b>可信设备也要判</b>：规则看的是「这台设备替第几个人搬东西」，
 *       而不是「这台设备可不可信」。那两件事必须能同时表达；</li>
 *   <li><b>先记后判</b>：超了也要把关联关系记下来，否则这台设备的真实量级被抹平。</li>
 * </ol>
 *
 * @Date 2026-10-16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("资产出口场景的规则")
class AssetOutRuleTest {

    @Mock
    private LoginCounters counters;

    @Mock
    private RiskListCache lists;

    @Mock
    private DeviceDispositionService dispositionService;

    @Test
    @DisplayName("🔴 三条都写死在【资产出口 + 前置】")
    void 场景与阶段() {
        for (RiskRule rule : new RiskRule[]{
                new AssetDeviceFanoutRule(counters),
                new AssetIpBlacklistRule(lists),
                new AssetDeviceBannedRule(dispositionService)}) {
            assertEquals(MemberRiskEventTypeEnum.ASSET_OUT, rule.eventType(), rule.code());
            assertEquals(MemberRiskPhaseEnum.PRE, rule.phase(), rule.code());
        }
    }

    @Test
    @DisplayName("🔴 一机多号超限 → OTP（而不是硬拦）：误判一次只是多收一封信")
    void 一机多号要核身() {
        when(counters.addToSet(LoginCounters.Counter.ASSET_DEVICE_MEMBER, "dev-1", "900001")).thenReturn(4L);
        when(counters.retryAfterSeconds(any(), anyString())).thenReturn(3600L);

        RiskHit hit = new AssetDeviceFanoutRule(counters).evaluate(ctx(), config(tiers(3, 3)));

        assertEquals(MemberRiskDecisionEnum.OTP, hit.decision());
        assertTrue(hit.detail().contains("4"), hit.detail());
    }

    @Test
    @DisplayName("🔴 可信设备照样判 —— 规则自己不看信任，跳过与否由 bypass_by_trust 决定")
    void 可信设备照样判() {
        when(counters.addToSet(LoginCounters.Counter.ASSET_DEVICE_MEMBER, "dev-1", "900001")).thenReturn(4L);
        RiskContext trusted = ctx();
        trusted.deviceTrust(RiskDeviceTrust.VERIFIED);

        // 一台「验证过」的老设备被偷了会话之后照样是可信的 —— 而这条规则看的是另一件事
        assertEquals(MemberRiskDecisionEnum.OTP,
                new AssetDeviceFanoutRule(counters).evaluate(trusted, config(tiers(3, 3))).decision());
    }

    @Test
    @DisplayName("🔴 先记后判：没超也要把关联关系记下来，否则真实量级被抹平")
    void 先记后判() {
        when(counters.addToSet(LoginCounters.Counter.ASSET_DEVICE_MEMBER, "dev-1", "900001")).thenReturn(2L);

        assertNull(new AssetDeviceFanoutRule(counters).evaluate(ctx(), config(tiers(3, 3))));
        verify(counters).addToSet(LoginCounters.Counter.ASSET_DEVICE_MEMBER, "dev-1", "900001");
    }

    @Test
    @DisplayName("没带设备令牌时不判 —— 那一维无从计数（资产出口那边会先要设备身份）")
    void 没有设备号不判() {
        RiskContext noDevice = new RiskContext(MemberRiskEventTypeEnum.ASSET_OUT, 900001L, null, "1.2.3.4");

        assertNull(new AssetDeviceFanoutRule(counters).evaluate(noDevice, config(tiers(3, 3))));
    }

    @Test
    @DisplayName("🔴 配成滑块 / 密码冷却 → 存不进去；OTP、限流、拒绝都能配")
    void 允许的档位() {
        AssetDeviceFanoutRule rule = new AssetDeviceFanoutRule(counters);
        for (int decision : new int[]{2, 5}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> rule.validate(tiers(3, decision)));
            assertTrue(e.getMessage().contains("资产出口"), e.getMessage());
        }
        // OTP / 换通道 OTP / 限流 / 拒绝
        for (int decision : new int[]{3, 4, 6, 7}) {
            rule.validate(tiers(3, decision));
        }
    }

    @Test
    @DisplayName("黑名单与设备封禁：硬拦，且不受陪跑影响")
    void 名单与封禁() {
        when(lists.matchIp("1.2.3.4")).thenReturn(new ListMatch(77L, false, 0));
        when(dispositionService.currentStatus("dev-1")).thenReturn(DeviceStatusEnum.BANNED);

        AssetIpBlacklistRule black = new AssetIpBlacklistRule(lists);
        AssetDeviceBannedRule banned = new AssetDeviceBannedRule(dispositionService);

        assertEquals(MemberRiskDecisionEnum.DENY,
                black.evaluate(ctx(), direct(MemberRiskDecisionEnum.DENY)).decision());
        assertEquals(MemberRiskDecisionEnum.DENY,
                banned.evaluate(ctx(), direct(MemberRiskDecisionEnum.DENY)).decision());
        assertTrue(black.alwaysEnforce());
        assertTrue(banned.alwaysEnforce());
    }

    // ------------------------------------------------------------------ 脚手架

    /**
     * 资产出口的上下文：<b>只有会员号</b>，没有加载整行 Member。
     *
     * <p>这正是那个构造器存在的理由 —— 为了填一个 Member 去查一次库，
     * 是在每一次加地址 / 充话费上加一次查询，而这三条规则一条都不读账号字段。
     */
    private static RiskContext ctx() {
        return new RiskContext(MemberRiskEventTypeEnum.ASSET_OUT, 900001L, "dev-1", "1.2.3.4");
    }

    @Test
    @DisplayName("🔴 只有会员号时：memberId 拿得到，而 getMember() 如实说 null")
    void 只有会员号的上下文() {
        RiskContext ctx = ctx();

        assertEquals(900001L, ctx.memberId());
        assertTrue(ctx.memberExists(), "知道这是谁");
        // 🔴 不造一个只填了 memberId 的空 Member：那个对象看起来是「加载过的」，
        //    于是将来某条规则读 getStatus() 时拿到 null —— 而那不会报错，只会静默判错
        assertNull(ctx.getMember(), "账号字段此刻不可用，就该如实说 null");
    }

    private static JsonNode tiers(int over, int decision) {
        return JsonUtils.getMapper().readTree("{\"window\":\"1d\",\"tiers\":[{\"over\":"
                + over + ",\"do\":" + decision + "}]}");
    }

    private static RiskRuleConfig config(JsonNode params) {
        return new RiskRuleConfig("X", "X", MemberRiskEventTypeEnum.ASSET_OUT, MemberRiskPhaseEnum.PRE,
                MemberRiskRuleTypeEnum.TIERED, null, 0, null, MemberRiskModeEnum.ENFORCE, false, params);
    }

    private static RiskRuleConfig direct(MemberRiskDecisionEnum decision) {
        return new RiskRuleConfig("X", "X", MemberRiskEventTypeEnum.ASSET_OUT, MemberRiskPhaseEnum.PRE,
                MemberRiskRuleTypeEnum.DIRECT, decision, 0, null, MemberRiskModeEnum.ENFORCE, false,
                JsonUtils.getMapper().createObjectNode());
    }
}
