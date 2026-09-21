package solvela.lottery.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.enums.LotteryConfigStatusEnum;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.manager.LotteryConfigManager;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.config.spi.LotteryActivityRefProvider;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 活动能不能上线：彩票这一侧的判据。
 *
 * <h3>🔴 这条测试对应一次线上空白页</h3>
 * 2026-09-21：「国庆彩票狂欢」在活动列表里是上线状态，用户点进去只有两个
 * 灰块。原因是它挂的玩法自己还躺在<b>下线</b>状态 ——
 * 奖级规则配全了 4 条，{@code checkOnlineReady} 一路全绿，
 * 于是活动顺利上线，而 C 端的 {@code resolveByActivity} 只认 ONLINE 的玩法，
 * 拿到 null。
 *
 * <p>「能上线」和「已上线」是两件事，而此前的完备度判据只验了前者。
 *
 * <h3>⚠️ 判据必须是「至少一个上线」，不是「全部上线」</h3>
 * 一个活动下挂多个玩法是正常的：在备的、退役的都可能留着下线状态。
 * 写成「全部上线」会把「618仲夏夜幸运号」那种一上线一下线的<b>正常活动</b>
 * 也拦掉 —— 而那个活动在 C 端跑得好好的。
 *
 * @Date 2026-09-21
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LotteryActivityRefProviderTest {

    private static final String ACTIVITY = "A981UTZ5DP";

    @Mock
    private LotteryConfigManager lotteryConfigManager;

    @Mock
    private LotteryIssueManager lotteryIssueManager;

    @Mock
    private LotteryConfigService lotteryConfigService;

    @Mock
    private LotteryPrizeRuleManager lotteryPrizeRuleManager;

    @InjectMocks
    private LotteryActivityRefProvider provider;

    private static LotteryConfig config(String code, String name, LotteryConfigStatusEnum status) {
        LotteryConfig c = new LotteryConfig();
        c.setLotteryCode(code);
        c.setLotteryName(name);
        c.setStatus(status);
        c.setActivityCode(ACTIVITY);
        return c;
    }

    /** 把 lambdaQuery() 那条链桩成返回指定列表 */
    @SuppressWarnings("unchecked")
    private void givenConfigs(List<LotteryConfig> list) {
        LambdaQueryChainWrapper<LotteryConfig> chain = mock(LambdaQueryChainWrapper.class);
        when(lotteryConfigManager.lambdaQuery()).thenReturn(chain);
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.list()).thenReturn(list);
    }

    @BeforeEach
    void setUp() {
        // 默认「都配了奖级、都能上线」—— 本测试要验的是上线状态那一条，不是奖级那一条
        when(lotteryConfigService.checkOnlineReady(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("🔴 玩法能上线但一个都没上线：必须拦住，否则活动页是一片空白")
    void 玩法未上线时拦住() {
        givenConfigs(List.of(config("L5FB1P01ZC", "彩票狂欢", LotteryConfigStatusEnum.OFFLINE)));

        String notReady = provider.checkConfigured(ACTIVITY);

        assertNotNull(notReady, "奖级配全了但玩法没上线，C 端 resolveByActivity 仍然拿到 null");
        assertTrue(notReady.contains("还没上线"), "错误信息要说清是「没上线」而不是「没配置」：" + notReady);
    }

    @Test
    @DisplayName("🔴 一个上线一个下线：放行 —— 挂多个玩法是正常的，别把好活动拦掉")
    void 至少一个上线就放行() {
        /*
         * 「618仲夏夜幸运号」的真实形状：主玩法上线，另一个备着没上。
         * C 端 resolveByActivity 取第一个 ONLINE 的，跑得好好的 ——
         * 判据写成「全部上线」的话，这个活动就再也上不了线了。
         */
        givenConfigs(List.of(
                config("RMAAUK45TG", "618仲夏夜幸运号", LotteryConfigStatusEnum.ONLINE),
                config("NQ4W55L661", "七星幸运号", LotteryConfigStatusEnum.OFFLINE)));

        assertNull(provider.checkConfigured(ACTIVITY));
    }

    @Test
    @DisplayName("一个玩法都没配：还是报「尚未配置」，不要变成「没上线」")
    void 没有玩法时报未配置() {
        givenConfigs(List.of());

        String notReady = provider.checkConfigured(ACTIVITY);

        // 两种状态要说不同的话：一个是「去配一个」，一个是「去把它上线」
        assertNotNull(notReady);
        assertTrue(notReady.contains("尚未配置"), notReady);
    }

    @Test
    @DisplayName("奖级没配的优先报奖级：先说最里面那一层，别让人上线完才发现还差奖级")
    void 奖级缺失优先报() {
        givenConfigs(List.of(config("L5FB1P01ZC", "彩票狂欢", LotteryConfigStatusEnum.OFFLINE)));
        when(lotteryConfigService.checkOnlineReady("L5FB1P01ZC")).thenReturn("尚未配置任何奖级规则");

        String notReady = provider.checkConfigured(ACTIVITY);

        assertNotNull(notReady);
        assertTrue(notReady.contains("奖级"), notReady);
    }
}
