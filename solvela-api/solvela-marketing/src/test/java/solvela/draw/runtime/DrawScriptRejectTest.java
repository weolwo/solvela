package solvela.draw.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.activity.runtime.ActivityPlayContext;
import solvela.marketing.api.DrawRejectReason;
import solvela.marketing.api.DrawResultView;
import solvela.scriptengine.annotation.ScriptFunction;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本怎么把「不让他抽」说出去。
 *
 * <h3>🔴 在这两个函数出现之前，脚本【说不出去】</h3>
 * 一份算出「今天额度用完了」的编排脚本只有两条出路：
 * <ul>
 *   <li>{@code return null} —— 被 {@code ScriptScene.validateOutput} 判成违约并抛出，
 *       用户看到的是一句<b>点名脚本</b>的开发者向报错。
 *       （更糟的是 {@code executeMultiDrawByScript} 原先的报错文案还在教人这么写：
 *       「不想抽就 return null，由活动域翻译成『次数用完』」—— 那句话是错的。）</li>
 *   <li>照抽 —— 额度形同虚设。</li>
 * </ul>
 *
 * <h3>⚠️ 本测试真正要守的是「脚本不能伪造任意拒绝原因」</h3>
 * 拒绝做成两个<b>具名函数</b>而不是一个 {@code draw_reject('字符串')}：
 * 一个能产出 {@code DUPLICATE_REQUEST} 的脚本，会让幂等日志变成一堆解释不了的噪音；
 * 能产出 {@code POOL_NO_PRIZE} 的脚本，会让运营去查一个根本没问题的奖池。
 * 具名之后，白名单是<b>结构性</b>的 —— 下面最后一条用例钉的就是这件事。
 *
 * @Date 2026-09-22
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrawScriptRejectTest {

    @Mock
    private DrawConfigService drawConfigService;

    @Mock
    private DrawPrizeLogDao drawPrizeLogDao;

    @Mock
    private solvela.draw.runtime.ActivityDrawFacade activityDrawFacade;

    @InjectMocks
    private DrawScriptFunctions functions;

    private static ActivityPlayContext play() {
        return new ActivityPlayContext(1L, "ACT001", "req-1");
    }

    @Test
    @DisplayName("额度用完：产出 QUOTA_EXCEEDED，且不是「受理了」")
    void 额度用完() {
        DrawResultView view = functions.rejectQuotaExceeded(play());

        assertFalse(view.accepted());
        assertEquals(DrawRejectReason.QUOTA_EXCEEDED, view.reject());
        assertEquals(0, view.times(), "被拒了就不该有任何抽奖记录");
    }

    @Test
    @DisplayName("不符合条件：产出 NOT_ELIGIBLE")
    void 不符合参与条件() {
        DrawResultView view = functions.rejectNotEligible(play());

        assertFalse(view.accepted());
        assertEquals(DrawRejectReason.NOT_ELIGIBLE, view.reject());
    }

    @Test
    @DisplayName("🔴 额度用完 ≠ 防刷限流：两个值不能合并，用户的下一步动作不同")
    void 额度与限流是两回事() {
        /*
         * TOO_FREQUENT 是「等几秒就好」，QUOTA_EXCEEDED 是「这个周期没有了」。
         * 合并成一句「稍后再试」的话，用户会一直重试一个永远不会成功的动作。
         */
        assertFalse(DrawRejectReason.QUOTA_EXCEEDED == DrawRejectReason.TOO_FREQUENT);
        assertEquals(DrawRejectReason.QUOTA_EXCEEDED, functions.rejectQuotaExceeded(play()).reject());
    }

    /**
     * 🔴 脚本只能造这两种拒绝，别的一律造不出来。
     *
     * <p>做法是扫 {@code DrawScriptFunctions} 上所有 {@code @ScriptFunction} 方法里
     * 返回 {@code DrawResultView} 的那些，检查它们暴露出去的拒绝原因集合。
     * 有人以后加一个 {@code draw_reject(String reason)}，这条会红。
     */
    @Test
    @DisplayName("🔴 脚本可造的拒绝原因是白名单，不能让脚本伪造 DUPLICATE_REQUEST 这类系统级原因")
    void 拒绝原因是白名单() {
        Set<String> rejectFunctions = Arrays.stream(DrawScriptFunctions.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(ScriptFunction.class))
                .filter(m -> DrawResultView.class.equals(m.getReturnType()))
                .map(m -> m.getAnnotation(ScriptFunction.class).name())
                .filter(name -> name.startsWith("reject"))
                .collect(Collectors.toSet());

        assertEquals(Set.of("rejectQuotaExceeded", "rejectNotEligible"), rejectFunctions,
                "脚本能产出的拒绝原因变了。新增一种拒绝前先想清楚："
                        + "脚本伪造这个原因时，看日志的人会被带到哪个方向去排查？");

        // 另一半：这两个函数都不该收「原因」参数 —— 收了就等于又把白名单打开了
        for (Method m : DrawScriptFunctions.class.getDeclaredMethods()) {
            ScriptFunction meta = m.getAnnotation(ScriptFunction.class);
            if (meta != null && meta.name().startsWith("reject")) {
                assertEquals(1, m.getParameterCount(),
                        meta.name() + " 只该收上下文一个参数。多一个参数多半就是「原因」，"
                                + "而那会让脚本重新获得伪造任意拒绝的能力");
                assertEquals(ActivityPlayContext.class, m.getParameterTypes()[0]);
            }
        }
    }

    @Test
    @DisplayName("拒绝函数不能有副作用 —— 它只是造一个结果对象，可以在任意分支返回")
    void 拒绝没有副作用() {
        List<Method> rejects = Arrays.stream(DrawScriptFunctions.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(ScriptFunction.class))
                .filter(m -> m.getAnnotation(ScriptFunction.class).name().startsWith("reject"))
                .toList();

        assertFalse(rejects.isEmpty(), "一个 reject 函数都没扫到，这条用例就是空过");
        for (Method m : rejects) {
            assertFalse(m.getAnnotation(ScriptFunction.class).sideEffect(),
                    m.getName() + " 标了 sideEffect —— 引擎会把它算进「一次执行只准调一次」的额度里，"
                            + "于是「先判额度拒绝、再在别的分支抽奖」这种正常写法会被误拦");
        }
    }
}
