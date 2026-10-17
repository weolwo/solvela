package solvela.member.risk.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.json.JsonUtils;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.impl.AssetDeviceFanoutRule;
import solvela.member.risk.rule.impl.BindDeviceFanoutRule;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 换绑联系方式场景的三条规则（2026-10-16，P3 ② 最后一条链路）。
 *
 * <h3>它与资产出口共用一个基类，但<b>允许的处置不同</b></h3>
 * 两边都在数「这台设备替几个人操作」，所以计数、先记后判、不看信任那一套是同一份实现
 * （{@link DeviceMemberFanoutRule}）。不同的只有一件事：
 * <b>资产出口能要求核身，换绑要不到</b> —— 换绑本来就要新地址的码，
 * 而「证明你是原主」那一步对首次绑定压根不存在。
 *
 * <p>所以这里重点钉那个差异：同一个基类，两个场景的 {@code validate} 结论必须不一样。
 *
 * @Date 2026-10-16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("换绑场景的规则")
class ContactBindRuleTest {

    @Mock
    private LoginCounters counters;

    @Test
    @DisplayName("🔴 写死在【换绑 + 前置】")
    void 场景与阶段() {
        // 黑名单与设备封禁 2026-10-17 移交网关安全闸（挡所有接口，不只这条链路），这里只剩一机多号
        for (RiskRule rule : new RiskRule[]{new BindDeviceFanoutRule(counters)}) {
            assertEquals(MemberRiskEventTypeEnum.CONTACT_BIND, rule.eventType(), rule.code());
            assertEquals(MemberRiskPhaseEnum.PRE, rule.phase(), rule.code());
        }
    }

    @Test
    @DisplayName("🔴 同一个基类，两个场景的档位白名单不同：资产出口能要 OTP，换绑不能")
    void 两个场景的档位不同() {
        BindDeviceFanoutRule bind = new BindDeviceFanoutRule(counters);
        AssetDeviceFanoutRule asset = new AssetDeviceFanoutRule(counters);

        // 换绑：OTP / 换通道 OTP 都存不进去
        for (int otp : new int[]{3, 4}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> bind.validate(tiers(2, otp)));
            assertTrue(e.getMessage().contains("换绑"), e.getMessage());
        }
        // 资产出口：同一个档位是允许的
        asset.validate(tiers(2, 3));

        // 两边都不许配滑块与密码冷却
        for (int bad : new int[]{2, 5}) {
            assertThrows(IllegalArgumentException.class, () -> bind.validate(tiers(2, bad)));
            assertThrows(IllegalArgumentException.class, () -> asset.validate(tiers(2, bad)));
        }
    }

    @Test
    @DisplayName("超过阈值 → 限流；命中依据里写清是「换过联系方式」而不是「搬过东西」")
    void 超限限流() {
        when(counters.addToSet(LoginCounters.Counter.BIND_DEVICE_MEMBER, "dev-1", "900001")).thenReturn(3L);
        when(counters.retryAfterSeconds(LoginCounters.Counter.BIND_DEVICE_MEMBER, "dev-1")).thenReturn(7200L);

        RiskHit hit = new BindDeviceFanoutRule(counters).evaluate(ctx(), config(tiers(2, 6)));

        assertEquals(MemberRiskDecisionEnum.LIMIT, hit.decision());
        assertTrue(hit.detail().contains("换过联系方式"), hit.detail());
        assertEquals(7200L, hit.retryAfterSeconds());
    }

    @Test
    @DisplayName("🔴 两个场景各记一本：换绑数到的不是资产出口那本")
    void 计数器各记一本() {
        when(counters.addToSet(LoginCounters.Counter.BIND_DEVICE_MEMBER, "dev-1", "900001")).thenReturn(3L);
        // 资产出口那本是空的 —— 一台设备可能只干其中一件事
        when(counters.addToSet(LoginCounters.Counter.ASSET_DEVICE_MEMBER, "dev-1", "900001")).thenReturn(1L);

        assertEquals(MemberRiskDecisionEnum.LIMIT,
                new BindDeviceFanoutRule(counters).evaluate(ctx(), config(tiers(2, 6))).decision());
        assertNull(new AssetDeviceFanoutRule(counters).evaluate(ctx(), config(tiers(2, 3))),
                "合成一个计数器的话，两个阈值就只能一起调 —— 而它们的正常量级不一样");
    }

    // ------------------------------------------------------------------ 脚手架

    private static RiskContext ctx() {
        return new RiskContext(MemberRiskEventTypeEnum.CONTACT_BIND, 900001L, "dev-1", "1.2.3.4");
    }

    private static JsonNode tiers(int over, int decision) {
        return JsonUtils.getMapper().readTree("{\"window\":\"1d\",\"tiers\":[{\"over\":"
                + over + ",\"do\":" + decision + "}]}");
    }

    private static RiskRuleConfig config(JsonNode params) {
        return new RiskRuleConfig("X", "X", MemberRiskEventTypeEnum.CONTACT_BIND, MemberRiskPhaseEnum.PRE,
                MemberRiskRuleTypeEnum.TIERED, null, 0, null, MemberRiskModeEnum.ENFORCE, false, params);
    }
}
