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
import solvela.member.risk.rule.impl.ResetIpBlacklistRule;
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
    @DisplayName("🔴 两条都写死在【重置密码 + 前置】")
    void 场景与阶段() {
        // 重置密码的 IP / 设备次数限频 2026-10-17 移交网关限流（PASSWORD_RESET 场景），这里只剩名单与封禁
        for (RiskRule rule : new RiskRule[]{
                new ResetIpBlacklistRule(lists),
                new ResetDeviceBannedRule(dispositionService)}) {
            assertEquals(MemberRiskEventTypeEnum.RESET_PASSWORD, rule.eventType(), rule.code());
            // 验证码验过就改密码了，没有「验完再判一次」的位置
            assertEquals(MemberRiskPhaseEnum.PRE, rule.phase(), rule.code());
        }
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
