package solvela.draw.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.marketing.api.ActivityPlayKeys;
import solvela.marketing.api.DrawCmd;
import solvela.marketing.api.DrawResultView;
import solvela.exception.BusinessException;
import solvela.scriptengine.annotation.ScriptFunction;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptFunctionHandler;

/**
 * 暴露给编排脚本的抽奖函数。
 *
 * <p>脚本里这样写：
 * <pre>
 *   if (剩余次数 &lt;= 0) {
 *       return null;                      // 由活动域翻译成「次数用完」
 *   }
 *   pool = 是新人 ? 'POOL_NEW' : 'POOL_NORMAL';
 *   return draw_draw(pool);               // 最后一步，且整段脚本只准出现一次
 * </pre>
 *
 * <h3>🔴 会员号、活动编码、幂等键都不从脚本参数取</h3>
 * 它们从 {@link EngineContext} 的<b>内部通道</b>取（{@link ActivityPlayKeys}）。
 * 脚本变量里虽然也有一份 memberId 供脚本判断，但那是<b>脚本能改的</b>：
 * <pre>
 *   memberId = 10086;
 *   return draw_draw('POOL_A');   // 如果函数读脚本变量，就替 10086 抽了
 * </pre>
 * 内部通道脚本看不见，也就改不掉。
 *
 * <h3>为什么脚本只传 poolCode</h3>
 * 「抽哪个池」正是这段脚本存在的理由，也是它唯一该决定的事。
 * 多给一个参数就多一个能被写错的地方，而写错的后果是替别人抽奖。
 *
 * @Date 2026-08-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrawScriptFunctions implements ScriptFunctionHandler {

    private final ActivityDrawFacade activityDrawFacade;

    @Override
    public ScriptDomain domain() {
        return ScriptDomain.DRAW;
    }

    /**
     * 在指定奖池抽一次。
     *
     * <p>标了 {@code sideEffect = true}：引擎保证同一次脚本执行里，有副作用的函数最多调一次。
     * 两个 if 分支各写一次 {@code draw_draw(...)} 而条件同时成立时，第二次会直接抛出 ——
     * 而不是安静地多发一份奖。
     */
    @ScriptFunction(name = "draw", sideEffect = true,
            description = "在指定奖池抽一次，返回抽奖结果。会员号与幂等键由引擎从上下文取，脚本只传奖池编码。"
                    + "⚠️ 有副作用：一次执行只准调一次，且应当是脚本的最后一步")
    public DrawResultView draw(EngineContext context, String poolCode) {
        Long memberId = context.getInternal(ActivityPlayKeys.MEMBER_ID, Long.class);
        String activityCode = context.getInternal(ActivityPlayKeys.ACTIVITY_CODE, String.class);
        String requestId = context.getInternal(ActivityPlayKeys.REQUEST_ID, String.class);

        // 走到这里还拿不到身份，说明调用方没按 ACTIVITY_PLAY 的约定绑上下文 —— 这是编程错误，
        // 不是业务失败，抛出去让它变成 5xx 是对的。绝不能兜底成「按匿名抽一次」
        if (memberId == null || activityCode == null) {
            throw new BusinessException("抽奖函数缺少执行上下文（memberId / activityCode）。"
                    + "本函数只能在 ACTIVITY_PLAY 场景里调用，其它场景没有绑定这些内部数据。");
        }
        if (poolCode == null || poolCode.isBlank()) {
            throw new BusinessException("脚本调用抽奖函数时没有给出奖池编码");
        }

        log.info("[抽奖-脚本] activityCode: {}, poolCode: {}, memberId: {}", activityCode, poolCode, memberId);
        return activityDrawFacade.draw(new DrawCmd(activityCode, poolCode, memberId, requestId));
    }
}
