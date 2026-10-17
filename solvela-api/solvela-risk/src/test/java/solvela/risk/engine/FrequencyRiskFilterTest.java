package solvela.risk.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.module.redis.RedisService;
import solvela.risk.PromotionConfig;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 频次风控：会员维度与<b>设备维度</b>。
 *
 * <h3>设备那一维此前是一列死配置</h3>
 * {@code t_promotion_config.device_limit} 从 DDL 建好那天起就有列、Model 有字段、
 * 后台表单能填、VO 能返回 —— 而<b>没有任何一行代码读它</b>。运营填上
 * 「单设备每日限领 1 次」、保存成功、列表里也显示着，然后以为限住了，
 * 真实行为是完全不限。这种失效不会有任何报错，只会在活动被薅完之后才被发现。
 *
 * <p>所以这套用例里最要紧的两条是：<b>配了就真的拦</b>、<b>拿不到设备号时不能拦</b>。
 * 后者同样重要 —— 灰度期间老客户端还没带设备令牌，把「没有设备号」当成可疑，
 * 等于在 enforce 之前就把老版本用户全挡在外面。
 *
 * @Date 2026-09-09
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FrequencyRiskFilterTest {

    private static final long PROMO_ID = 1001L;
    private static final long MEMBER_ID = 5579345309L;
    private static final String DEVICE_ID = "0123456789abcdef0123456789abcdef";

    @Mock
    private RedisService redisService;

    @InjectMocks
    private FrequencyRiskFilter filter;

    private PromotionConfig config;

    private ProposalRecordAddCommand request;

    @BeforeEach
    void setUp() {
        config = new PromotionConfig();
        config.setId(PROMO_ID);
        config.setLimitPeriod("DAILY");
        request = new ProposalRecordAddCommand();
        request.setMemberId(MEMBER_ID);
        // 默认两维都没超
        when(redisService.increment(any(), anyLong())).thenReturn(1L);
    }

    private static String any() {
        return ArgumentMatchers.anyString();
    }

    private RiskResult run(String deviceId) {
        return filter.doFilter(new RiskContext(request, config, deviceId));
    }

    // ============================== 会员维度（既有行为不能变） ==============================

    @Test
    @DisplayName("🔴 会员维度的计数键必须还是【裸的 member_id】，不能加前缀")
    void 会员键名不能变() {
        config.setIdentifyLimit(3);

        run(null);

        // 键名一变，Redis 里所有在飞的计数就全部作废 —— 配「终身限领 1 次」的活动
        // 会给每个已经领过的人重新发一次额度，而且没有任何报错
        verify(redisService).increment(eq("risk:freq:DAILY:promo_1001:" + MEMBER_ID), anyLong());
    }

    @Test
    @DisplayName("会员维度超限 → USER_FREQUENCY_LIMIT")
    void 会员超限() {
        config.setIdentifyLimit(3);
        when(redisService.increment(contains(String.valueOf(MEMBER_ID)), anyLong())).thenReturn(4L);

        RiskResult result = run(null);

        assertFalse(result.isPassed());
        assertEquals(RiskBlockCode.USER_FREQUENCY_LIMIT.getValue(), result.getRuleCode());
    }

    @Test
    @DisplayName("会员维度先判：它超限时设备维度压根不计数")
    void 会员超限时不再计设备() {
        config.setIdentifyLimit(3);
        config.setDeviceLimit(5);
        when(redisService.increment(contains(String.valueOf(MEMBER_ID)), anyLong())).thenReturn(4L);

        run(DEVICE_ID);

        verify(redisService, never()).increment(contains("d_"), anyLong());
    }

    // ============================== 设备维度 ==============================

    @Test
    @DisplayName("🔴 配了 device_limit 就真的拦 —— 这一列此前从来没有被读过")
    void 设备超限() {
        config.setDeviceLimit(2);
        when(redisService.increment(contains("d_" + DEVICE_ID), anyLong())).thenReturn(3L);

        RiskResult result = run(DEVICE_ID);

        assertFalse(result.isPassed());
        assertEquals(RiskBlockCode.DEVICE_FREQUENCY_LIMIT.getValue(), result.getRuleCode(),
                "编码必须与会员维度分开：混成一个，「一台机器上换号薅」这个信号就永远看不出来");
    }

    @Test
    @DisplayName("设备维度的计数键带 d_ 前缀，与会员维度分得开")
    void 设备键名() {
        config.setDeviceLimit(2);

        run(DEVICE_ID);

        verify(redisService).increment(eq("risk:freq:DAILY:promo_1001:d_" + DEVICE_ID), anyLong());
    }

    @Test
    @DisplayName("🔴 拿不到设备号 → 这一维直接跳过，绝不能当成可疑")
    void 没有设备号不拦() {
        config.setDeviceLimit(1);

        RiskResult result = run(null);

        assertTrue(result.isPassed(),
                "灰度期间老客户端还没带设备令牌，把「没有设备号」当成命中，"
                        + "等于在 enforce 之前就把老版本用户全挡在外面");
        verify(redisService, never()).increment(contains("d_"), anyLong());
    }

    @Test
    @DisplayName("没配 device_limit（null 或 <=0）→ 不计数不拦")
    void 没配就不管() {
        for (Integer limit : new Integer[]{null, 0, -1}) {
            config.setDeviceLimit(limit);
            assertTrue(run(DEVICE_ID).isPassed(), "device_limit=" + limit + " 应当放行");
        }
        verify(redisService, never()).increment(contains("d_"), anyLong());
    }

    @Test
    @DisplayName("两维都配、都没超 → 放行，且两个键各计各的")
    void 两维都计数() {
        config.setIdentifyLimit(5);
        config.setDeviceLimit(5);

        assertTrue(run(DEVICE_ID).isPassed());

        verify(redisService).increment(eq("risk:freq:DAILY:promo_1001:" + MEMBER_ID), anyLong());
        verify(redisService).increment(eq("risk:freq:DAILY:promo_1001:d_" + DEVICE_ID), anyLong());
    }

    @Test
    @DisplayName("周期换算两维共用一份 —— LIFETIME 不能掉进 default 变成一天")
    void 周期换算共用() {
        config.setLimitPeriod("LIFETIME");
        config.setIdentifyLimit(1);
        config.setDeviceLimit(1);

        run(DEVICE_ID);

        long tenYears = 86400L * 365 * 10;
        verify(redisService).increment(contains(String.valueOf(MEMBER_ID)), eq(tenYears));
        verify(redisService).increment(contains("d_"), eq(tenYears));
    }
}
