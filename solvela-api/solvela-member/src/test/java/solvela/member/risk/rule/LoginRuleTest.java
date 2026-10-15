package solvela.member.risk.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import solvela.member.Member;
import solvela.member.api.MemberLoginType;
import solvela.member.device.DeviceDispositionService;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.config.ListMatch;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.OldDeviceExemption;
import solvela.member.risk.rule.impl.DeviceBannedRule;
import solvela.member.risk.rule.impl.DeviceFailLimitRule;
import solvela.member.risk.rule.impl.DeviceFanoutRule;
import solvela.member.risk.rule.impl.DeviceLoginLimitRule;
import solvela.member.risk.rule.impl.DeviceObserveRule;
import solvela.member.risk.rule.impl.GlobalCaptchaRule;
import solvela.member.risk.rule.impl.IpAccountFanoutRule;
import solvela.member.risk.rule.impl.IpAttemptLimitRule;
import solvela.member.risk.rule.impl.IpBlacklistRule;
import solvela.member.risk.rule.impl.IpFailLimitRule;
import solvela.member.risk.rule.impl.IpGreylistRule;
import solvela.member.risk.rule.impl.IpUnknownLimitRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 从旧闸门迁过来的那批规则。
 *
 * <h3>这里钉住的是「迁移有没有变味」</h3>
 * 阈值、窗口、键名都搬到了别处，而搬错的表现全是静默的。所以重点测三件事：
 * <ol>
 *   <li><b>谁加计数、谁只读</b> —— 失败类计数器在判断那一步 +1 的话，每次正常登录都被算成一次失败，
 *       阈值当场失去意义；</li>
 *   <li><b>「超过」的边界</b> —— 等于阈值不算超，与改造前的 {@code count > max} 一致；</li>
 *   <li><b>维度取不到时不判</b> —— 没带设备令牌是常态（灰度期间的老客户端），不是可疑。</li>
 * </ol>
 *
 * @Date 2026-10-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("迁移自旧闸门的规则")
class LoginRuleTest {

    @Mock
    private LoginCounters counters;

    @Mock
    private RiskListCache lists;

    @Mock
    private DeviceDispositionService dispositionService;

    /** 默认不豁免，所以下面那些阈值用例的断言一字不用改 */
    @Mock
    private OldDeviceExemption exemption;

    @InjectMocks
    private IpAttemptLimitRule ipAttempt;

    // ------------------------------------------------------------------ 计数口径

    @Test
    @DisplayName("🔴 登录请求总数：判断时把这一次算进去")
    void attemptCountsThisRequest() {
        IpAttemptLimitRule rule = new IpAttemptLimitRule(counters, exemption);
        when(counters.bump(LoginCounters.Counter.IP_ATTEMPT, "1.2.3.4")).thenReturn(61L);
        when(counters.retryAfterSeconds(any(), anyString())).thenReturn(1200L);

        RiskHit hit = rule.evaluate(ctx(), config(tiers(60, 6)));

        assertEquals(MemberRiskDecisionEnum.LIMIT, hit.decision());
        assertEquals(1200L, hit.retryAfterSeconds());
        verify(counters, never()).read(eq(LoginCounters.Counter.IP_ATTEMPT), anyString());
    }

    @Test
    @DisplayName("🔴 登录失败数：只读不加")
    void failOnlyReads() {
        IpFailLimitRule rule = new IpFailLimitRule(counters, exemption);
        when(counters.read(LoginCounters.Counter.IP_FAIL, "1.2.3.4")).thenReturn(21L);

        assertEquals(MemberRiskDecisionEnum.LIMIT, rule.evaluate(ctx(), config(tiers(5, 2, 20, 6))).decision());
        verify(counters, never()).bump(any(), anyString());
    }

    @Test
    @DisplayName("🔴 不存在账号数：只读不加")
    void unknownOnlyReads() {
        IpUnknownLimitRule rule = new IpUnknownLimitRule(counters, exemption);
        when(counters.read(LoginCounters.Counter.IP_UNKNOWN, "1.2.3.4")).thenReturn(4L);

        assertEquals(MemberRiskDecisionEnum.CAPTCHA, rule.evaluate(ctx(), config(tiers(3, 2, 10, 6))).decision());
        verify(counters, never()).bump(any(), anyString());
    }

    @Test
    @DisplayName("设备失败数只读不加，设备登录数算这一次")
    void deviceCounters() {
        when(counters.read(LoginCounters.Counter.DEVICE_FAIL, "dev-1")).thenReturn(11L);
        assertNotNull(new DeviceFailLimitRule(counters).evaluate(ctx(), config(tiers(10, 6))));
        verify(counters, never()).bump(any(), anyString());

        when(counters.bump(LoginCounters.Counter.DEVICE_LOGIN, "dev-1")).thenReturn(31L);
        assertNotNull(new DeviceLoginLimitRule(counters).evaluate(ctx(), config(tiers(30, 6))));
    }

    // ------------------------------------------------------------------ 边界

    @Test
    @DisplayName("等于阈值不算超 —— 与改造前的 count > max 完全一致")
    void equalToThresholdPasses() {
        IpAttemptLimitRule rule = new IpAttemptLimitRule(counters, exemption);
        when(counters.bump(any(), anyString())).thenReturn(60L);

        assertNull(rule.evaluate(ctx(), config(tiers(60, 6))));
    }

    @Test
    @DisplayName("两档都超时取更严的那一档")
    void higherTierWins() {
        IpFailLimitRule rule = new IpFailLimitRule(counters, exemption);
        when(counters.read(any(), anyString())).thenReturn(100L);

        assertEquals(MemberRiskDecisionEnum.LIMIT, rule.evaluate(ctx(), config(tiers(5, 2, 20, 6))).decision());
    }

    @Test
    @DisplayName("🔴 维度取不到就不判：没带设备令牌是常态，不是可疑")
    void missingDimensionSkips() {
        RiskContext noDevice = new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD,
                "a@x.com", "ab".repeat(32), member(), null, "H5", "1.2.3.4", false);

        assertNull(new DeviceLoginLimitRule(counters).evaluate(noDevice, config(tiers(30, 6))));
        assertNull(new DeviceFailLimitRule(counters).evaluate(noDevice, config(tiers(10, 6))));
        verify(counters, never()).bump(any(), anyString());
    }

    @Test
    @DisplayName("IP 取不到也不判（内部工具、测试不传 IP）")
    void missingIpSkips() {
        RiskContext noIp = new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD,
                "a@x.com", "ab".repeat(32), member(), "dev-1", "H5", null, false);

        assertNull(new IpAttemptLimitRule(counters, exemption).evaluate(noIp, config(tiers(60, 6))));
    }

    // ------------------------------------------------------------------ 集合类

    @Test
    @DisplayName("一个 IP 尝试的不同账号：存摘要前缀，不论账号存不存在都算")
    void ipAccountFanout() {
        IpAccountFanoutRule rule = new IpAccountFanoutRule(counters, exemption);
        when(counters.addToSet(eq(LoginCounters.Counter.IP_IDENTITY), eq("1.2.3.4"), anyString())).thenReturn(6L);

        RiskHit hit = rule.evaluate(ctx(), config(tiers(5, 2, 30, 6)));

        assertEquals(MemberRiskDecisionEnum.CAPTCHA, hit.decision());
        verify(counters).addToSet(LoginCounters.Counter.IP_IDENTITY, "1.2.3.4", "abababababababab");
    }

    @Test
    @DisplayName("🔴 一机多号：关联关系先记下再判阈值 —— 陪跑期间要的正是这份数据")
    void fanoutRecordsBeforeDeciding() {
        DeviceFanoutRule rule = new DeviceFanoutRule(counters);
        when(counters.addToSet(any(), anyString(), anyString())).thenReturn(2L);

        assertNull(rule.evaluate(ctx(), config(tiers(3, 6))), "没超阈值");
        verify(counters).addToSet(LoginCounters.Counter.DEVICE_MEMBER, "dev-1", "1000000001");
    }

    @Test
    @DisplayName("设备闸命中时请求降档，params.observe 为 false 就不请求")
    void observeActionFollowsParams() {
        when(counters.bump(any(), anyString())).thenReturn(31L);

        RiskHit withObserve = new DeviceLoginLimitRule(counters).evaluate(ctx(),
                config(JsonUtils.getMapper().readTree(
                        "{\"tiers\":[{\"over\":30,\"do\":6}],\"observe\":true}")));
        assertTrue(withObserve.actions().contains(RiskAction.OBSERVE_DEVICE));

        RiskHit without = new DeviceLoginLimitRule(counters).evaluate(ctx(), config(tiers(30, 6)));
        assertTrue(without.actions().isEmpty());
    }

    // ------------------------------------------------------------------ 名单与状态

    @Test
    @DisplayName("IP 黑名单命中即拒绝，命中依据带条目 id 好让客服顺着查")
    void blacklist() {
        IpBlacklistRule rule = new IpBlacklistRule(lists);
        when(lists.matchIp("1.2.3.4")).thenReturn(new ListMatch(77L, false, 0));

        RiskHit hit = rule.evaluate(ctx(), config(MemberRiskDecisionEnum.DENY));

        assertEquals(MemberRiskDecisionEnum.DENY, hit.decision());
        assertTrue(hit.detail().contains("#77"));
    }

    @Test
    @DisplayName("灰名单加的分跟着条目走，不跟着规则走")
    void greylistScoreFromEntry() {
        IpGreylistRule rule = new IpGreylistRule(lists);
        when(lists.matchIp("1.2.3.4")).thenReturn(new ListMatch(null, false, 45));

        RiskHit hit = rule.evaluate(ctx(), config(tiers()));

        assertEquals(45, hit.score());
        assertNull(hit.decision(), "打分规则不给处置");
    }

    @Test
    @DisplayName("没命中名单时返回 null，不是返回 0 分")
    void listMissReturnsNull() {
        when(lists.matchIp(anyString())).thenReturn(ListMatch.NONE);

        assertNull(new IpBlacklistRule(lists).evaluate(ctx(), config(MemberRiskDecisionEnum.DENY)));
        assertNull(new IpGreylistRule(lists).evaluate(ctx(), config(tiers())));
    }

    @Test
    @DisplayName("设备封禁 → 拒绝；观察档 → OTP")
    void deviceStatusRules() {
        when(dispositionService.currentStatus("dev-1")).thenReturn(DeviceStatusEnum.BANNED);
        assertEquals(MemberRiskDecisionEnum.DENY,
                new DeviceBannedRule(dispositionService).evaluate(ctx(), config(MemberRiskDecisionEnum.DENY))
                        .decision());

        when(dispositionService.currentStatus("dev-1")).thenReturn(DeviceStatusEnum.OBSERVE);
        assertEquals(MemberRiskDecisionEnum.OTP,
                new DeviceObserveRule(dispositionService).evaluate(ctx(), config(MemberRiskDecisionEnum.OTP))
                        .decision());
    }

    @Test
    @DisplayName("🔴 邮箱验证码登录不再要第二道码：它本身就是一道")
    void emailCodeLoginSkipsObserve() {
        when(dispositionService.currentStatus("dev-1")).thenReturn(DeviceStatusEnum.OBSERVE);
        RiskContext codeLogin = new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_CODE,
                "a@x.com", "ab".repeat(32), member(), "dev-1", "H5", "1.2.3.4", false);

        assertNull(new DeviceObserveRule(dispositionService).evaluate(codeLogin, config(MemberRiskDecisionEnum.OTP)));
    }

    @Test
    @DisplayName("设备正常时这两条都不命中")
    void normalDevicePasses() {
        when(dispositionService.currentStatus("dev-1")).thenReturn(DeviceStatusEnum.NORMAL);

        assertNull(new DeviceBannedRule(dispositionService).evaluate(ctx(), config(MemberRiskDecisionEnum.DENY)));
        assertNull(new DeviceObserveRule(dispositionService).evaluate(ctx(), config(MemberRiskDecisionEnum.OTP)));
    }

    @Test
    @DisplayName("全局滑块：开着就是无条件命中，档位由引擎处理")
    void globalCaptchaAlwaysHits() {
        GlobalCaptchaRule rule = new GlobalCaptchaRule();

        assertEquals(MemberRiskDecisionEnum.CAPTCHA,
                rule.evaluate(ctx(), config(MemberRiskDecisionEnum.CAPTCHA)).decision());
        assertTrue(rule.alwaysEnforce(), "保命开关不该顺手把这道门也打开");
    }

    @Test
    @DisplayName("黑名单、封禁、全局滑块都是 alwaysEnforce；普通限流不是")
    void alwaysEnforceSet() {
        assertTrue(new IpBlacklistRule(lists).alwaysEnforce());
        assertTrue(new DeviceBannedRule(dispositionService).alwaysEnforce());
        assertTrue(new GlobalCaptchaRule().alwaysEnforce());
        assertEquals(false, new IpFailLimitRule(counters, exemption).alwaysEnforce());
        assertEquals(false, new DeviceObserveRule(dispositionService).alwaysEnforce());
    }

    // ------------------------------------------------------------------ 参数校验

    @Test
    @DisplayName("分级规则自己校验参数：tiers 缺了直接抛，不等到运行时才发现")
    void tieredRulesValidate() {
        assertEquals(MemberRiskPhaseEnum.PRE, ipAttempt.phase());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ipAttempt.validate(JsonUtils.getMapper().readTree("{}")));
        ipAttempt.validate(JsonUtils.getMapper().readTree("{\"window\":\"1h\",\"tiers\":[{\"over\":60,\"do\":6}]}"));
    }

    // ------------------------------------------------------------------ 脚手架

    private static Member member() {
        Member member = new Member();
        member.setMemberId(1000000001L);
        return member;
    }

    // ------------------------------------------------------------------ 老设备豁免限流档（§4.1）

    @Test
    @DisplayName("🔴 老设备：超到限流档也只出滑块 —— 校园网里一个人试错，不该让整片真人登不上")
    void 老设备免限流档() {
        when(exemption.exempt(any())).thenReturn(true);
        IpAttemptLimitRule rule = new IpAttemptLimitRule(counters, exemption);
        when(counters.bump(LoginCounters.Counter.IP_ATTEMPT, "1.2.3.4")).thenReturn(300L);
        when(counters.retryAfterSeconds(any(), anyString())).thenReturn(1200L);

        // 超 60 出滑块、超 200 限流；数到 300，没有豁免的话是限流
        RiskHit hit = rule.evaluate(ctx(), config(tiers(60, 2, 200, 6)));

        assertNotNull(hit, "🔴 不是整条跳过：滑块档照常出");
        assertEquals(MemberRiskDecisionEnum.CAPTCHA, hit.decision());
    }

    @Test
    @DisplayName("🔴 豁免压的是处置，不是观察：计数照常加")
    void 豁免也照常计数() {
        when(exemption.exempt(any())).thenReturn(true);
        IpAttemptLimitRule rule = new IpAttemptLimitRule(counters, exemption);
        when(counters.bump(LoginCounters.Counter.IP_ATTEMPT, "1.2.3.4")).thenReturn(300L);

        rule.evaluate(ctx(), config(tiers(60, 2, 200, 6)));

        verify(counters).bump(LoginCounters.Counter.IP_ATTEMPT, "1.2.3.4");
    }

    @Test
    @DisplayName("老设备 + 只配了限流一档 → 不命中（没有更低的档可落）")
    void 老设备且只有限流档时不命中() {
        when(exemption.exempt(any())).thenReturn(true);
        IpAttemptLimitRule rule = new IpAttemptLimitRule(counters, exemption);
        when(counters.bump(LoginCounters.Counter.IP_ATTEMPT, "1.2.3.4")).thenReturn(300L);

        assertNull(rule.evaluate(ctx(), config(tiers(200, 6))));
    }

    @Test
    @DisplayName("🔴 设备维度的规则【没有】这个豁免：一台用了半年的设备开始挨个试号，更值得警惕")
    void 设备规则不受豁免() {
        when(exemption.exempt(any())).thenReturn(true);
        DeviceFanoutRule rule = new DeviceFanoutRule(counters);
        when(counters.addToSet(eq(LoginCounters.Counter.DEVICE_MEMBER), eq("dev-1"), anyString()))
                .thenReturn(10L);
        when(counters.retryAfterSeconds(any(), anyString())).thenReturn(600L);

        RiskHit hit = rule.evaluate(ctx(), config(tiers(3, 6)));

        assertNotNull(hit);
        assertEquals(MemberRiskDecisionEnum.LIMIT, hit.decision(),
                "它挡的正是「这台设备在挨个试号」，设备老恰恰不是免责理由");
    }

    @Test
    @DisplayName("IP_ACCOUNT_FANOUT 也受豁免（它没走基类，所以单独钉一条）")
    void 一IP多账号也受豁免() {
        when(exemption.exempt(any())).thenReturn(true);
        IpAccountFanoutRule rule = new IpAccountFanoutRule(counters, exemption);
        when(counters.addToSet(eq(LoginCounters.Counter.IP_IDENTITY), eq("1.2.3.4"), anyString()))
                .thenReturn(80L);
        when(counters.retryAfterSeconds(any(), anyString())).thenReturn(600L);

        RiskHit hit = rule.evaluate(ctx(), config(tiers(10, 2, 50, 6)));

        assertNotNull(hit);
        assertEquals(MemberRiskDecisionEnum.CAPTCHA, hit.decision());
    }

    private static RiskContext ctx() {
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD, "a@x.com",
                "ab".repeat(32), member(), "dev-1", "H5", "1.2.3.4", false);
    }

    /** {@code tiers(5, 2, 20, 6)} = 超 5 出滑块、超 20 限流 */
    private static tools.jackson.databind.JsonNode tiers(int... overAndDo) {
        StringBuilder sb = new StringBuilder("{\"window\":\"1h\",\"tiers\":[");
        for (int i = 0; i < overAndDo.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"over\":").append(overAndDo[i]).append(",\"do\":").append(overAndDo[i + 1]).append('}');
        }
        return JsonUtils.getMapper().readTree(sb.append("]}").toString());
    }

    private static RiskRuleConfig config(tools.jackson.databind.JsonNode params) {
        return new RiskRuleConfig("X", "X", MemberRiskPhaseEnum.PRE, MemberRiskRuleTypeEnum.TIERED, null, 0, null,
                MemberRiskModeEnum.ENFORCE, false, params);
    }

    private static RiskRuleConfig config(MemberRiskDecisionEnum direct) {
        return new RiskRuleConfig("X", "X", MemberRiskPhaseEnum.PRE, MemberRiskRuleTypeEnum.DIRECT, direct, 0, null,
                MemberRiskModeEnum.ENFORCE, false, JsonUtils.getMapper().createObjectNode());
    }
}
