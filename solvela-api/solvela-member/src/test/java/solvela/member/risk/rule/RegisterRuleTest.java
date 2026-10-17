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
import solvela.member.risk.rule.impl.RegisterIpBlacklistRule;
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
    @DisplayName("🔴 两条都写死在【注册 + 前置】—— 表里改不动它们")
    void 场景与阶段() {
        // 注册的 IP / 设备次数限频 2026-10-17 移交网关限流（REGISTER 场景），这里只剩名单与封禁
        for (RiskRule rule : new RiskRule[]{
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
