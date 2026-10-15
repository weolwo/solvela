package solvela.member.risk.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.json.JsonUtils;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import solvela.member.Member;
import solvela.member.api.MemberLoginType;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.AccountFailTracker;
import solvela.member.risk.feature.Familiarity;
import solvela.member.risk.feature.IpIntel;
import solvela.member.risk.feature.LoginProfile;
import solvela.member.risk.rule.impl.AccountFailBurstRule;
import solvela.member.risk.rule.impl.ImpossibleTravelRule;
import solvela.member.risk.rule.impl.IpDatacenterRule;
import solvela.member.risk.rule.impl.NewCountryRule;
import solvela.member.risk.rule.impl.NoDeviceRule;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 打分规则。
 *
 * <h3>这里钉的主要是「什么时候不判」</h3>
 * 每条打分规则都有一个「数据不够就别判」的边界，而越过它的后果都是同一种：
 * 给一大批正常用户无端加分，然后他们被要求多验一道码。这类问题没人会报障。
 *
 * @Date 2026-10-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("打分规则")
class ScoreRuleTest {

    @Mock
    private Familiarity familiarity;

    @Mock
    private IpIntel ipIntel;

    @Mock
    private AccountFailTracker failTracker;

    // ------------------------------------------------------------------ 没带设备令牌

    @Test
    @DisplayName("没带设备令牌时命中；带了就不命中")
    void noDevice() {
        NoDeviceRule rule = new NoDeviceRule();

        assertNotNull(rule.evaluate(ctx(null), config(empty())));
        assertNull(rule.evaluate(ctx("dev-1"), config(empty())));
    }

    // ------------------------------------------------------------------ 机房 IP

    @Test
    @DisplayName("机房 IP 命中")
    void datacenter() {
        IpDatacenterRule rule = new IpDatacenterRule(ipIntel, familiarity);
        JsonNode params = json("{\"keywords\":[\"阿里云\"]}");
        when(familiarity.location(any(), any())).thenReturn(location("中国", "广东省", "深圳市", "阿里云"));
        when(ipIntel.datacenter(any(), any())).thenReturn(true);

        assertNotNull(rule.evaluate(ctx("dev-1"), config(params)));
    }

    @Test
    @DisplayName("🔴 内网 IP 与解析不出来的 IP 都不判 —— 内网只可能是内部工具或压测")
    void datacenterSkipsIntranetAndUnknown() {
        IpDatacenterRule rule = new IpDatacenterRule(ipIntel, familiarity);
        JsonNode params = json("{\"keywords\":[\"阿里云\"]}");

        when(familiarity.location(any(), any())).thenReturn(location("内网IP", null, null, null));
        assertNull(rule.evaluate(ctx("dev-1"), config(params)));

        when(familiarity.location(any(), any())).thenReturn(location(null, null, null, null));
        assertNull(rule.evaluate(ctx("dev-1"), config(params)));
    }

    @Test
    @DisplayName("关键字列表为空 → 拒绝保存：这条规则会永远不命中，而页面上看着很正常")
    void datacenterRejectsEmptyKeywords() {
        IpDatacenterRule rule = new IpDatacenterRule(ipIntel, familiarity);

        assertThrows(IllegalArgumentException.class, () -> rule.validate(json("{\"keywords\":[]}")));
        rule.validate(json("{\"keywords\":[\"阿里云\"]}"));
    }

    // ------------------------------------------------------------------ 新国家

    @Test
    @DisplayName("近期没来过这个国家 → 命中")
    void newCountry() {
        NewCountryRule rule = new NewCountryRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("美国", "加州", "洛杉矶", "AT&T"));
        when(familiarity.profile(any(), any())).thenReturn(
                new LoginProfile(Set.of(), Set.of(), Set.of("中国"), null, 20));

        assertNotNull(rule.evaluate(ctx("dev-1"), config(json("{\"minSuccess\":3}"))));
    }

    @Test
    @DisplayName("🔴 冷启动不判：没有画像时「从没见过的国家」是必然的")
    void newCountrySkipsColdStart() {
        NewCountryRule rule = new NewCountryRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("美国", "加州", "洛杉矶", "AT&T"));
        when(familiarity.profile(any(), any())).thenReturn(
                new LoginProfile(Set.of(), Set.of(), Set.of(), null, 2));

        assertNull(rule.evaluate(ctx("dev-1"), config(json("{\"minSuccess\":3}"))),
                "判了等于给每个新用户都加一笔分");
    }

    @Test
    @DisplayName("来过这个国家 → 不命中")
    void knownCountry() {
        NewCountryRule rule = new NewCountryRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("中国", "广东省", "深圳市", "电信"));
        when(familiarity.profile(any(), any())).thenReturn(
                new LoginProfile(Set.of(), Set.of(), Set.of("中国"), null, 20));

        assertNull(rule.evaluate(ctx("dev-1"), config(json("{\"minSuccess\":3}"))));
    }

    // ------------------------------------------------------------------ 不可能旅行

    @Test
    @DisplayName("跨国 + 短间隔 → 命中")
    void impossibleTravelCrossCountry() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("美国", "纽约州", "纽约", "Verizon"));
        when(familiarity.profile(any(), any())).thenReturn(profileWithLast("中国", "广东省", 20));

        RiskHit hit = rule.evaluate(ctx("dev-1"), config(travelParams()));

        assertNotNull(hit);
        assertTrue(hit.detail().contains("中国") && hit.detail().contains("美国"));
    }

    @Test
    @DisplayName("跨国但间隔够长 → 不命中（人是真的能飞过去的）")
    void crossCountryWithEnoughTime() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("美国", "纽约州", "纽约", "Verizon"));
        when(familiarity.profile(any(), any())).thenReturn(profileWithLast("中国", "广东省", 60 * 24));

        assertNull(rule.evaluate(ctx("dev-1"), config(travelParams())));
    }

    @Test
    @DisplayName("跨省 + 极短间隔 → 命中；同省不判")
    void crossProvince() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("中国", "北京市", "北京", "联通"));
        when(familiarity.profile(any(), any())).thenReturn(profileWithLast("中国", "广东省", 10));
        assertNotNull(rule.evaluate(ctx("dev-1"), config(travelParams())));

        when(familiarity.profile(any(), any())).thenReturn(profileWithLast("中国", "北京市", 10));
        assertNull(rule.evaluate(ctx("dev-1"), config(travelParams())));
    }

    @Test
    @DisplayName("🔴 省份缺一方就不判 —— 拿 null 去比会把它判成「换省了」")
    void missingProvinceSkips() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("中国", null, null, "移动"));
        when(familiarity.profile(any(), any())).thenReturn(profileWithLast("中国", "广东省", 5));

        assertNull(rule.evaluate(ctx("dev-1"), config(travelParams())));
    }

    @Test
    @DisplayName("没有上一次成功登录 → 不判")
    void noLastSuccessSkips() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("美国", "纽约州", "纽约", "Verizon"));
        when(familiarity.profile(any(), any())).thenReturn(LoginProfile.EMPTY);

        assertNull(rule.evaluate(ctx("dev-1"), config(travelParams())));
    }

    @Test
    @DisplayName("🔴 上一次登录在「未来」→ 不判，而不是当成 0 分钟（那只可能是时钟或时区坏了）")
    void futureLastSuccessSkips() {
        ImpossibleTravelRule rule = new ImpossibleTravelRule(familiarity);
        when(familiarity.location(any(), any())).thenReturn(location("美国", "纽约州", "纽约", "Verizon"));
        when(familiarity.profile(any(), any())).thenReturn(profileWithLast("中国", "广东省", -60));

        assertNull(rule.evaluate(ctx("dev-1"), config(travelParams())));
    }

    // ------------------------------------------------------------------ 账号被围攻

    @Test
    @DisplayName("次数够 + 来源够 → 命中")
    void failBurst() {
        AccountFailBurstRule rule = new AccountFailBurstRule(failTracker);
        when(failTracker.burst(any(), any())).thenReturn(new AccountFailTracker.Burst(5, 3));

        RiskHit hit = rule.evaluate(ctx("dev-1"), config(burstParams()));

        assertNotNull(hit);
        assertTrue(hit.detail().contains("3 个不同 IP"));
    }

    @Test
    @DisplayName("🔴 只有次数够不算：一个自己输错几遍的人不该命中「被围攻」")
    void failBurstNeedsMultipleSources() {
        AccountFailBurstRule rule = new AccountFailBurstRule(failTracker);
        when(failTracker.burst(any(), any())).thenReturn(new AccountFailTracker.Burst(9, 1));

        assertNull(rule.evaluate(ctx("dev-1"), config(burstParams())));
    }

    @Test
    @DisplayName("来源够但次数不够 → 不命中")
    void failBurstNeedsEnoughFails() {
        AccountFailBurstRule rule = new AccountFailBurstRule(failTracker);
        when(failTracker.burst(any(), any())).thenReturn(new AccountFailTracker.Burst(2, 2));

        assertNull(rule.evaluate(ctx("dev-1"), config(burstParams())));
    }

    @Test
    @DisplayName("首批那几份真实参数都校验得过")
    void seedParamsValidate() {
        new NewCountryRule(familiarity).validate(json("{\"minSuccess\":3,\"lookbackDays\":90}"));
        new ImpossibleTravelRule(familiarity).validate(travelParams());
        new AccountFailBurstRule(failTracker).validate(burstParams());
        new IpDatacenterRule(ipIntel, familiarity).validate(json(
                "{\"keywords\":[\"阿里云\",\"腾讯云\",\"华为云\",\"Amazon\",\"Google\"]}"));
    }

    // ------------------------------------------------------------------ 脚手架

    private static JsonNode json(String raw) {
        return JsonUtils.getMapper().readTree(raw);
    }

    private static JsonNode empty() {
        return JsonUtils.getMapper().createObjectNode();
    }

    private static JsonNode travelParams() {
        return json("{\"crossCountryMinutes\":120,\"crossProvinceMinutes\":30}");
    }

    private static JsonNode burstParams() {
        return json("{\"window\":\"1h\",\"minFails\":3,\"minIps\":2}");
    }

    private static IpIntel.Location location(String country, String province, String city, String isp) {
        return new IpIntel.Location(country, province, city, isp, "4:1.2.3/24");
    }

    private static LoginProfile profileWithLast(String country, String province, long minutesAgo) {
        return new LoginProfile(Set.of(), Set.of(), Set.of(country),
                new LoginProfile.LastSuccess(country, province, LocalDateTime.now().minusMinutes(minutesAgo)), 20);
    }

    private static RiskContext ctx(String deviceId) {
        Member member = new Member();
        member.setMemberId(1000000001L);
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD, "a@x.com",
                "ab".repeat(32), member, deviceId, "H5", "1.2.3.4", false);
    }

    private static RiskRuleConfig config(JsonNode params) {
        return new RiskRuleConfig("X", "X", MemberRiskPhaseEnum.POST, MemberRiskRuleTypeEnum.SCORE, null, 30,
                null, MemberRiskModeEnum.ENFORCE, true, params);
    }
}
