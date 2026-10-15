package solvela.member.risk.feature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.base.json.JsonUtils;
import solvela.base.module.redis.RedisService;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import solvela.member.Member;
import solvela.member.api.MemberLoginType;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskDeviceTrust;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRuleConfig;
import solvela.member.risk.rule.impl.PasswordCooldownRule;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 密码阶梯冷却。取代改造前的账号锁（30 分钟错 5 次、锁 30 分钟）。
 *
 * <h3>这里钉的是两条「别让安全机制变成攻击手段」</h3>
 * <ol>
 *   <li><b>按身份摘要计数</b>，不存在的账号也照样冷却 —— 否则「会不会被冷却」就是一个账号枚举接口；</li>
 *   <li><b>可信设备单独分桶</b> —— 否则任何知道你邮箱的人连错几次就能把你锁在门外。</li>
 * </ol>
 *
 * @Date 2026-10-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("密码阶梯冷却")
class PasswordCooldownTest {

    private static final String HASH = "ab".repeat(32);

    private static final JsonNode PARAMS = JsonUtils.getMapper().readTree("""
            {"free": 2, "steps": ["60s", "5m", "10m", "60m", "24h"], "idleReset": "24h", "recordFrom": "60m"}
            """);

    @Mock
    private RedisService redisService;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private DeviceTrustProvider trustProvider;

    private PasswordCooldownStore store;

    private PasswordCooldownRule rule;

    @BeforeEach
    void setUp() {
        // 规则表用不到：这里测的是低层的计数与分桶，参数由用例直接传
        store = new PasswordCooldownStore(redisService, redis, emptyRegistry());
        rule = new PasswordCooldownRule(store, trustProvider);
        when(redisService.generateRedisKey(anyString(), anyString()))
                .thenAnswer(i -> "solvela:dev:" + i.getArgument(0) + i.getArgument(1));
        when(trustProvider.of(any())).thenReturn(RiskDeviceTrust.NEW);
    }

    // ------------------------------------------------------------------ 阶梯

    @Test
    @DisplayName("前两次不冷却：手滑输错一次就等一分钟是过度反应")
    void firstTwoAreFree() {
        when(redisService.increment(anyString(), anyLong())).thenReturn(1L, 2L);

        assertFalse(store.recordFailure(HASH, "u", 2, steps(), Duration.ofHours(24)).cooling());
        assertFalse(store.recordFailure(HASH, "u", 2, steps(), Duration.ofHours(24)).cooling());
    }

    @Test
    @DisplayName("第 3 次起依次 60 秒、5 分、10 分、60 分、24 小时")
    void ladderSteps() {
        assertEquals(Duration.ofSeconds(60), cooldownAt(3));
        assertEquals(Duration.ofMinutes(5), cooldownAt(4));
        assertEquals(Duration.ofMinutes(10), cooldownAt(5));
        assertEquals(Duration.ofMinutes(60), cooldownAt(6));
        assertEquals(Duration.ofHours(24), cooldownAt(7));
    }

    @Test
    @DisplayName("阶梯用完之后一直停在最后一档，不会越界")
    void ladderSaturates() {
        assertEquals(Duration.ofHours(24), cooldownAt(8));
        assertEquals(Duration.ofHours(24), cooldownAt(99));
    }

    @Test
    @DisplayName("🔴 计数用 EXPIRE 续期，不是读出来再写回去（并发失败会丢计数）")
    void renewsWithExpire() {
        when(redisService.increment(anyString(), anyLong())).thenReturn(3L);

        store.recordFailure(HASH, "u", 2, steps(), Duration.ofHours(24));

        verify(redis).expire(contains(HASH), eq(Duration.ofHours(24)));
    }

    // ------------------------------------------------------------------ 分桶

    @Test
    @DisplayName("🔴 可信设备单独一个桶，其余一律共用 —— 否则知道你邮箱的人就能把你锁在门外")
    void trustedDeviceHasOwnBucket() {
        assertEquals("dev-1", store.bucket("dev-1", true));
        assertEquals("u", store.bucket("dev-1", false), "陌生设备挤在共用桶里，换设备令牌也躲不开");
        assertEquals("u", store.bucket(null, true), "没有设备号就没地方分桶");
    }

    @Test
    @DisplayName("🔴 清零把两个桶都清掉：只清当前桶会让主人下次换手机一脚踩进攻击者攒的计数")
    void clearWipesBothBuckets() {
        store.clear(HASH, "dev-1");

        verify(redisService).delete(contains(HASH + ":u"), contains(HASH + ":u:cd"));
        verify(redisService).delete(contains(HASH + ":dev-1"), contains(HASH + ":dev-1:cd"));
    }

    // ------------------------------------------------------------------ 规则

    @Test
    @DisplayName("冷却中 → COOLDOWN，并把剩余秒数带出去")
    void coolingBlocks() {
        when(redisService.getExpire(contains(":cd"))).thenReturn(275L);
        when(redisService.get(anyString())).thenReturn("4");

        RiskHit hit = rule.evaluate(ctx(MemberLoginType.EMAIL_PASSWORD), config());

        assertEquals(MemberRiskDecisionEnum.COOLDOWN, hit.decision());
        assertEquals(275L, hit.retryAfterSeconds());
        assertTrue(hit.detail().contains("4 次"));
    }

    @Test
    @DisplayName("🔴 长冷却请求 RECORD_LIMIT（落一行让后台看得见），短的不请求")
    void longCooldownRecordsLimit() {
        when(redisService.get(anyString())).thenReturn("5");

        // 默认 recordFrom 是 60 分钟：剩 24 小时 → 落库；剩 60 秒 → 不落
        when(redisService.getExpire(contains(":cd"))).thenReturn(86400L);
        assertTrue(rule.evaluate(ctx(MemberLoginType.EMAIL_PASSWORD), config())
                        .actions().contains(solvela.member.risk.RiskAction.RECORD_LIMIT),
                "客服要找的恰恰是「被锁了很久」的那几个人");

        when(redisService.getExpire(contains(":cd"))).thenReturn(60L);
        assertTrue(rule.evaluate(ctx(MemberLoginType.EMAIL_PASSWORD), config()).actions().isEmpty(),
                "🔴 60 秒那一档也落库的话，那张表会被手滑的人刷满");
    }

    @Test
    @DisplayName("没有冷却时不命中 —— 判断本身不加计数，计数在密码验错之后")
    void notCoolingPasses() {
        when(redisService.getExpire(anyString())).thenReturn(-2L);

        assertNull(rule.evaluate(ctx(MemberLoginType.EMAIL_PASSWORD), config()));
        verify(redisService, org.mockito.Mockito.never()).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("🔴 邮箱验证码登录不进这条规则：码自己有「错 5 次作废」，而这是被恶意冷却时剩下的那条路")
    void emailCodeLoginUnaffected() {
        when(redisService.getExpire(anyString())).thenReturn(3600L);

        assertNull(rule.evaluate(ctx(MemberLoginType.EMAIL_CODE), config()));
    }

    @Test
    @DisplayName("🔴 查无此人照样算：按身份摘要计数，不按会员号")
    void unknownAccountStillCools() {
        when(redisService.getExpire(contains(":cd"))).thenReturn(60L);
        when(redisService.get(anyString())).thenReturn("3");
        RiskContext noMember = new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD,
                "nobody@x.com", HASH, null, "dev-1", "H5", "1.2.3.4", false);

        RiskHit hit = rule.evaluate(noMember, config());

        assertEquals(MemberRiskDecisionEnum.COOLDOWN, hit.decision(),
                "不存在的账号永远不被冷却的话，「会不会被冷却」就是一个账号枚举接口");
    }

    // ------------------------------------------------------------------ 参数校验

    @Test
    @DisplayName("steps 必须越来越长，否则错得越多反而冷却越短")
    void stepsMustGrow() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> rule.validate(JsonUtils.getMapper().readTree(
                        "{\"steps\": [\"5m\", \"60s\"]}")));
        assertTrue(e.getMessage().contains("越来越长"));
    }

    @Test
    @DisplayName("steps 为空或单位非法 → 拒绝保存")
    void badStepsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> rule.validate(JsonUtils.getMapper().readTree("{\"steps\": []}")));
        assertThrows(IllegalArgumentException.class,
                () -> rule.validate(JsonUtils.getMapper().readTree("{\"steps\": [\"5x\"]}")));
    }

    @Test
    @DisplayName("首批那份真实参数解析得出来")
    void seedParamsParse() {
        rule.validate(PARAMS);

        assertEquals(2, PasswordCooldownRule.free(PARAMS));
        assertEquals(5, PasswordCooldownRule.steps(PARAMS).size());
        assertEquals(Duration.ofHours(24), PasswordCooldownRule.idleReset(PARAMS));
        assertEquals(Duration.ofMinutes(60), PasswordCooldownRule.recordFrom(PARAMS));
    }

    // ------------------------------------------------------------------ 脚手架

    private Duration cooldownAt(long failures) {
        when(redisService.increment(anyString(), anyLong())).thenReturn(failures);
        return store.recordFailure(HASH, "u", 2, steps(), Duration.ofHours(24)).cooldown();
    }

    private static List<Duration> steps() {
        return PasswordCooldownRule.steps(PARAMS);
    }

    private static RiskContext ctx(MemberLoginType loginType) {
        Member member = new Member();
        member.setMemberId(1000000001L);
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, loginType, "a@x.com", HASH, member,
                "dev-1", "H5", "1.2.3.4", false);
    }

    private static org.springframework.beans.factory.ObjectProvider<
            solvela.member.risk.config.RiskRuleRegistry> emptyRegistry() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public solvela.member.risk.config.RiskRuleRegistry getObject() {
                throw new org.springframework.beans.factory.NoSuchBeanDefinitionException("registry");
            }

            @Override
            public solvela.member.risk.config.RiskRuleRegistry getObject(Object... args) {
                return getObject();
            }

            @Override
            public solvela.member.risk.config.RiskRuleRegistry getIfAvailable() {
                return null;
            }

            @Override
            public solvela.member.risk.config.RiskRuleRegistry getIfUnique() {
                return null;
            }
        };
    }

    private static RiskRuleConfig config() {
        return new RiskRuleConfig("PWD_COOLDOWN", "密码阶梯冷却", MemberRiskPhaseEnum.PRE,
                MemberRiskRuleTypeEnum.TIERED, null, 0, null, MemberRiskModeEnum.ENFORCE, false, PARAMS);
    }
}
