package solvela.draw.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import solvela.draw.DrawConfig;
import solvela.draw.DrawPrizeLog;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.marketing.api.ActivityPlayKeys;
import solvela.marketing.api.DrawCmd;
import solvela.marketing.api.DrawResultView;
import solvela.exception.BusinessException;
import solvela.scriptengine.annotation.ScriptFunction;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptFunctionHandler;

import java.time.LocalDateTime;

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

    private final DrawConfigService drawConfigService;

    private final DrawPrizeLogDao drawPrizeLogDao;

    @Override
    public ScriptDomain domain() {
        return ScriptDomain.DRAW;
    }

    /**
     * 这个会员在<b>本周期内</b>已经抽了几次。
     *
     * <p>脚本这样用：
     * <pre>
     *   if (draw_countDrawn() &gt;= 3) {
     *       return null;                  // 由活动域翻译成「次数用完」
     *   }
     *   return draw_executeDrawByScript(pool);
     * </pre>
     *
     * <h3>次数不存在第二份账</h3>
     * 「抽了几次」直接数 {@code t_draw_prize_log}，<b>不另建计数表</b>。
     * 抽奖记录本来就是每抽一次一行，再维护一个计数器等于同一件事有两份账，
     * 而两份账迟早会对不上 —— 到那天你不知道该信哪一个。
     *
     * <p>时间下界来自抽奖配置的 {@code reset_period}：按天就从今天零点数起，
     * 按周从周一，按月从 1 号，{@code ACTIVITY} 则不设下界（整个活动累计）。
     * 见 {@link DrawPeriodResolver#periodStart}。
     *
     * <h3>⚠️ 未中奖、库存不足、异常的记录<b>都算一次</b></h3>
     * 因为它们都是「用户点了一次抽奖」。方向与本模块其它地方一致：
     * 宁可严格 —— 少算一次是资损，多算一次用户会来反馈。
     * 真要区分状态，应该由脚本拿到明细自己判，而不是在这里悄悄过滤掉几种。
     *
     * <p>无副作用：只读，可以在脚本里任意位置调、调多次。
     */
    @ScriptFunction(name = "countDrawn",
            description = "本周期内该会员已经抽了几次。周期由抽奖配置的 reset_period 决定"
                    + "（按天/周/月从周期起点数起，ACTIVITY 则整个活动累计）。"
                    + "未中奖与异常记录都算一次。只读，可多次调用")
    public int countDrawn(EngineContext context) {
        Long memberId = context.getInternal(ActivityPlayKeys.MEMBER_ID, Long.class);
        String activityCode = context.getInternal(ActivityPlayKeys.ACTIVITY_CODE, String.class);
        if (memberId == null || activityCode == null) {
            // 与抽奖函数同一套判据：拿不到身份是编程错误，不是业务失败。
            // 绝不能兜底成 0 —— 那等于「查不到就当没抽过」，直接把次数限制废掉
            throw new BusinessException("次数查询函数缺少执行上下文（memberId / activityCode）。"
                    + "本函数只能在 ACTIVITY_PLAY 场景里调用。");
        }

        DrawConfig drawConfig = drawConfigService.getByActivityCode(activityCode);
        String resetPeriod = drawConfig == null ? null : drawConfig.getResetPeriod();

        LocalDateTime since = null;
        if (DrawPeriodResolver.needsClock(resetPeriod)) {
            // 时钟取数据库的：多实例部署下各节点 JVM 时钟未必一致，跨零点那一刻
            // A 节点认为还是昨天、B 认为已是今天，同一个用户能在两个周期里各抽一轮
            since = DrawPeriodResolver.periodStart(resetPeriod, drawPrizeLogDao.selectDbNow());
        }

        LocalDateTime finalSince = since;
        // 走 idx_mem_act (member_id, activity_code)
        long count = drawPrizeLogDao.selectCount(Wrappers.<DrawPrizeLog>lambdaQuery()
                .eq(DrawPrizeLog::getMemberId, memberId)
                .eq(DrawPrizeLog::getActivityCode, activityCode)
                .ge(finalSince != null, DrawPrizeLog::getCreateTime, finalSince));
        return Math.toIntExact(count);
    }

    /**
     * 在指定奖池抽一次。
     *
     * <p>标了 {@code sideEffect = true}：引擎保证同一次脚本执行里，有副作用的函数最多调一次。
     * 两个 if 分支各写一次 {@code draw_executeDrawByScript(...)} 而条件同时成立时，第二次会直接抛出 ——
     * 而不是安静地多发一份奖。
     */
    @ScriptFunction(name = "executeDrawByScript", sideEffect = true,
            description = "在指定奖池抽一次，返回抽奖结果。会员号与幂等键由引擎从上下文取，脚本只传奖池编码。"
                    + "⚠️ 有副作用：一次执行只准调一次，且应当是脚本的最后一步")
    public DrawResultView executeDrawByScript(EngineContext context, String poolCode) {
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
