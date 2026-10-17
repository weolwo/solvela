package solvela.member.risk.rule;

import org.junit.jupiter.api.BeforeEach;
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
import solvela.member.Member;
import solvela.member.api.MemberLoginType;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskDeviceTrust;
import solvela.member.risk.RiskIpFamiliarity;
import solvela.member.risk.feature.DeviceTrustProvider;
import solvela.member.risk.feature.Familiarity;
import solvela.member.risk.rule.impl.CaptchaMatrixRule;
import solvela.member.risk.rule.impl.LoginMatrixRule;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 两个矩阵 —— 整套风控的主模型。
 *
 * <h3>这里钉的是四件事</h3>
 * <ol>
 *   <li><b>前置只能放宽</b>：查无此人时必须给出那个最严的默认值，否则这个接口就是账号枚举器；</li>
 *   <li><b>前置只认严格同网段</b>：认「同城市 + 同运营商」的话，和受害者同城同运营商的攻击者
 *       就能对一部分账号跳过滑块；</li>
 *   <li><b>可信设备沉睡回来照样放行</b>：一台老设备隔一个月再来大概率是本人，
 *       它要是被复制了由不可能旅行去抓；</li>
 *   <li><b>矩阵配不出「拒绝」</b>：一格配错就会把一整类正常用户挡在门外，而他们不会来报障。</li>
 * </ol>
 *
 * @Date 2026-10-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("风控矩阵")
class MatrixRuleTest {

    private static final JsonNode CAPTCHA_PARAMS = JsonUtils.getMapper().readTree("""
            {"v4Prefix": 24, "v6Prefix": 64, "recentIps": 10, "lookbackDays": 90}
            """);

    private static final JsonNode LOGIN_PARAMS = JsonUtils.getMapper().readTree("""
            {"cells": {"TRUSTED": 1, "NEW_UNFAMILIAR": 3, "NEW_FAMILIAR_ACTIVE": 1, "NEW_FAMILIAR_DORMANT": 3},
             "dormantDays": 30}
            """);

    @Mock
    private Familiarity familiarity;

    @Mock
    private DeviceTrustProvider trustProvider;

    private CaptchaMatrixRule captchaMatrix;

    private LoginMatrixRule loginMatrix;

    @BeforeEach
    void setUp() {
        captchaMatrix = new CaptchaMatrixRule(familiarity);
        loginMatrix = new LoginMatrixRule(trustProvider, familiarity);
    }

    // ------------------------------------------------------------------ 前置

    @Test
    @DisplayName("账号存在 + 严格同网段 → 放宽，不要滑块")
    void familiarIpSkipsCaptcha() {
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.STRICT);

        assertNull(captchaMatrix.evaluate(ctx(member(), null), captchaConfig()));
    }

    @Test
    @DisplayName("🔴 只认严格同网段：同城同运营商在前置【不算熟】")
    void looseIsNotEnoughForCaptcha() {
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.LOOSE);

        RiskHit hit = captchaMatrix.evaluate(ctx(member(), null), captchaConfig());

        assertEquals(MemberRiskDecisionEnum.CAPTCHA, hit.decision(),
                "宽松口径是给后置用的 —— 在前置放宽，同城同运营商的攻击者就能跳过滑块");
    }

    @Test
    @DisplayName("🔴 查无此人 → 给最严的默认值，与「有号但网段陌生」逐字相同")
    void unknownAccountGetsStrictDefault() {
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.STRICT);

        RiskHit noMember = captchaMatrix.evaluate(ctx(null, null), captchaConfig());

        assertEquals(MemberRiskDecisionEnum.CAPTCHA, noMember.decision(),
                "哪怕熟悉度算出来是严格熟悉，没有账号就不能放宽 —— 否则放不放宽本身就是答案");
    }

    // ------------------------------------------------------------------ 后置四格

    @Test
    @DisplayName("可信设备 → 放行")
    void trustedPasses() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.VERIFIED);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.UNFAMILIAR);

        assertNull(loginMatrix.evaluate(ctx(member(), LocalDateTime.now()), loginConfig()));
    }

    @Test
    @DisplayName("🔴 可信设备沉睡回来也放行 —— 令牌被复制那种情况由不可能旅行去抓")
    void trustedDormantStillPasses() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.HISTORY);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.UNFAMILIAR);

        assertNull(loginMatrix.evaluate(ctx(member(), LocalDateTime.now().minusDays(200)), loginConfig()));
    }

    @Test
    @DisplayName("新设备 + 熟悉网络 + 活跃 → 放行（新设备提醒照发）")
    void newDeviceFamiliarActivePasses() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.NEW);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.LOOSE);

        assertNull(loginMatrix.evaluate(ctx(member(), LocalDateTime.now()), loginConfig()),
                "后置认宽松口径 —— 这时候密码已经验对了");
    }

    @Test
    @DisplayName("新设备 + 熟悉网络 + 沉睡 → OTP")
    void newDeviceFamiliarDormantNeedsOtp() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.NEW);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.STRICT);

        RiskHit hit = loginMatrix.evaluate(ctx(member(), LocalDateTime.now().minusDays(31)), loginConfig());

        assertEquals(MemberRiskDecisionEnum.OTP, hit.decision());
        assertTrue(hit.detail().contains("沉睡"), "命中依据要让客服看得懂");
    }

    @Test
    @DisplayName("新设备 + 陌生网络 → OTP")
    void newDeviceUnfamiliarNeedsOtp() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.NEW);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.UNFAMILIAR);

        assertEquals(MemberRiskDecisionEnum.OTP,
                loginMatrix.evaluate(ctx(member(), LocalDateTime.now()), loginConfig()).decision());
    }

    @Test
    @DisplayName("矩阵算出的三个维度要记进上下文 —— 流水靠它们回答「上周有多少人落在这一格」")
    void writesCoordinatesIntoContext() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.NEW);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.UNFAMILIAR);
        RiskContext ctx = ctx(member(), LocalDateTime.now().minusDays(31));

        loginMatrix.evaluate(ctx, loginConfig());

        assertEquals(Boolean.TRUE, ctx.getDormant());
    }

    // ------------------------------------------------------------------ 沉睡的判据

    @Test
    @DisplayName("🔴 沉睡按最后活跃算；为空时退回注册时间，而不是最后登录时间")
    void dormancyFallsBackToRegistration() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.NEW);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.STRICT);

        Member old = member();
        old.setLastActiveTime(null);
        old.setCreateTime(LocalDateTime.now().minusDays(100));
        assertEquals(MemberRiskDecisionEnum.OTP,
                loginMatrix.evaluate(ctx(old, null), loginConfig()).decision(), "老号、注册很久没来过 → 沉睡");

        Member fresh = member();
        fresh.setLastActiveTime(null);
        fresh.setCreateTime(LocalDateTime.now().minusMinutes(5));
        assertNull(loginMatrix.evaluate(ctx(fresh, null), loginConfig()),
                "🔴 刚注册完的人第二次打开不该被判沉睡 —— 那是这个账号最不可疑的时候");
    }

    @Test
    @DisplayName("两个时间都没有 → 不判沉睡（乱判的方向是人人都要验码）")
    void noTimestampsMeansNotDormant() {
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.NEW);
        when(familiarity.of(any(), any())).thenReturn(RiskIpFamiliarity.STRICT);
        Member blank = member();
        blank.setLastActiveTime(null);
        blank.setCreateTime(null);

        assertNull(loginMatrix.evaluate(ctx(blank, null), loginConfig()));
    }

    // ------------------------------------------------------------------ 参数校验

    @Test
    @DisplayName("🔴 某一格配成「拒绝」→ 拒绝保存：一格配错会挡住一整类正常用户")
    void cellCannotBeDeny() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> loginMatrix.validate(JsonUtils.getMapper().readTree(
                        "{\"cells\": {\"NEW_UNFAMILIAR\": 7}}")));
        assertTrue(e.getMessage().contains("只能配到 OTP 为止"));
    }

    @Test
    @DisplayName("格子的值不是数字 → 拒绝保存")
    void cellMustBeNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> loginMatrix.validate(JsonUtils.getMapper().readTree(
                        "{\"cells\": {\"TRUSTED\": \"PASS\"}}")));
    }

    @Test
    @DisplayName("首批那两份真实参数都校验得过")
    void seedParamsValidate() {
        loginMatrix.validate(LOGIN_PARAMS);
        captchaMatrix.validate(CAPTCHA_PARAMS);
        assertEquals(MemberRiskPhaseEnum.PRE, captchaMatrix.phase());
        assertEquals(MemberRiskPhaseEnum.POST, loginMatrix.phase());
    }

    // ------------------------------------------------------------------ 脚手架

    private static Member member() {
        Member member = new Member();
        member.setMemberId(1000000001L);
        member.setCreateTime(LocalDateTime.now().minusDays(365));
        return member;
    }

    private static RiskContext ctx(Member member, LocalDateTime lastActive) {
        if (member != null) {
            member.setLastActiveTime(lastActive);
        }
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD, "a@x.com",
                "ab".repeat(32), member, "dev-1", "H5", "1.2.3.4", false);
    }

    private static RiskRuleConfig captchaConfig() {
        return config("CAPTCHA_MATRIX", MemberRiskPhaseEnum.PRE, CAPTCHA_PARAMS);
    }

    private static RiskRuleConfig loginConfig() {
        return config("LOGIN_MATRIX", MemberRiskPhaseEnum.POST, LOGIN_PARAMS);
    }

    private static RiskRuleConfig config(String code, MemberRiskPhaseEnum phase, JsonNode params) {
        return new RiskRuleConfig(code, code, MemberRiskEventTypeEnum.LOGIN, phase, MemberRiskRuleTypeEnum.TIERED, null, 0, null,
                MemberRiskModeEnum.ENFORCE, false, params);
    }
}
