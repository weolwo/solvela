package solvela.draw.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import solvela.activity.runtime.ActivityPlayContext;
import solvela.draw.DrawConfig;
import solvela.draw.DrawPrizeLog;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.marketing.api.ActivityPlayKeys;
import solvela.marketing.api.DrawCmd;
import solvela.marketing.api.DrawLimits;
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
 *   return draw_executeDrawByScript(pool);        // 单抽
 *   // 或 draw_executeMultiDrawByScript(pool, times);  // 连抽
 *   // 最后一步，且整段脚本只准出现一次
 * </pre>
 *
 * <h3>🔴 会员号、活动编码、幂等键都不从脚本参数取</h3>
 * 它们从 {@link EngineContext} 的<b>内部通道</b>取，读取统一走 {@link ActivityPlayContext}
 * （通道的键定义见 {@link ActivityPlayKeys}，但别在这里直接用它）。
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
    public int countDrawn(ActivityPlayContext play) {
        DrawConfig drawConfig = drawConfigService.getByActivityCode(play.activityCode());
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
                .eq(DrawPrizeLog::getMemberId, play.memberId())
                .eq(DrawPrizeLog::getActivityCode, play.activityCode())
                .ge(finalSince != null, DrawPrizeLog::getCreateTime, finalSince));
        return Math.toIntExact(count);
    }

    /**
     * 在指定奖池<b>抽一次</b>。就是 {@code executeMultiDrawByScript(pool, 1)} 的别名。
     *
     * <p>留着它<b>只为了存量脚本</b>：{@code draw_play.ql} 这类已经上线的编排都在调它，
     * 而脚本是运营在后台写的，库里有多少份我们并不知道。新脚本一律用
     * {@link #executeMultiDrawByScript}，单抽就传 1。
     *
     * <p>标了 {@code sideEffect = true}：引擎保证同一次脚本执行里，有副作用的函数最多调一次。
     * 这个保证是<b>跨函数</b>的 —— 本函数与 {@code executeMultiDrawByScript} 合起来也只准调一次，
     * 所以不存在「先单抽再连抽」偷偷多发一份奖。
     */
    @ScriptFunction(name = "executeDrawByScript", sideEffect = true,
            description = "在指定奖池抽一次（= executeMultiDrawByScript(pool, 1)）。"
                    + "会员号与幂等键由引擎从上下文取，脚本只传奖池编码。"
                    + "⚠️ 有副作用：一次执行只准调一次，且应当是脚本的最后一步")
    public DrawResultView executeDrawByScript(ActivityPlayContext play, String poolCode) {
        return executeMultiDrawByScript(play, poolCode, 1);
    }

    /**
     * 在指定奖池<b>连抽 times 次</b>。
     *
     * <p>脚本这样用 —— {@code times} 是客户端点的那个按钮（单抽 / 十连抽），
     * 由活动域绑成脚本变量；<b>真正抽几次由这段脚本说了算</b>：
     * <pre>
     *   remain = 3 - draw_countDrawn();
     *   if (remain &lt;= 0) {
     *       return null;                                    // 次数用完
     *   }
     *   if (times &gt; remain) {
     *       return null;                                    // 次数不够，别少抽几次糊弄用户
     *   }
     *   return draw_executeMultiDrawByScript(pool, times);
     * </pre>
     *
     * <h3>⚠️ 为什么连抽是个新名字，而不是给老函数加个参数</h3>
     * 两个原因，第二个才是要紧的：
     * <ol>
     *   <li>脚本函数注册表按限定名去重，<b>不支持按参数个数重载</b>（重名在启动期直接抛）；</li>
     *   <li><b>更重要</b>：让老函数从上下文隐式读 times，会让已上线脚本<b>无声地</b>
     *       从单抽变成连抽。一段
     *       {@code if (draw_countDrawn() >= 3) return null; return draw_executeDrawByScript(pool);}
     *       在只剩 1 次时会照抽 10 次，而且不报错。</li>
     * </ol>
     * 所以连抽必须是<b>显式写出来</b>的：写它的人被迫想一遍次数够不够。
     *
     * <h3>🔴 次数上限在这里挡，不在引擎里</h3>
     * 引擎刻意不校验上限（由上游保证），而<b>本类就是那个上游</b> ——
     * 它是运营手写的 QLExpress 与引擎之间的边界。一个笔误
     * {@code draw_executeMultiDrawByScript(pool, 1000)} 不挡的话会把奖池当场抽空，
     * 而且那是一次「成功」的调用。
     *
     * <p>超限<b>抛异常</b>而不是截断到上限：截断意味着脚本要 1000 次、拿到 10 次，
     * 没有人会知道这件事。抛出去会变成 5xx 并进日志，运营能看见自己写错了。
     * 与「脚本返回了错误类型」是同一档处置 —— 配置事故，不是业务失败。
     *
     * @param times 抽几次。上限 {@link DrawLimits#MAX_TIMES}，超了直接抛
     */
    @ScriptFunction(name = "executeMultiDrawByScript", sideEffect = true,
            description = "在指定奖池连抽 times 次，返回 times 条抽奖记录。会员号与幂等键由引擎从上下文取。"
                    + "times 上限 " + DrawLimits.MAX_TIMES + "，超了直接报错。"
                    + "⚠️ 有副作用：一次执行只准调一次（与 executeDrawByScript 合计），且应当是脚本的最后一步")
    public DrawResultView executeMultiDrawByScript(ActivityPlayContext play, String poolCode, int times) {
        if (poolCode == null || poolCode.isBlank()) {
            throw new BusinessException("脚本调用抽奖函数时没有给出奖池编码");
        }
        if (times <= 0) {
            throw new BusinessException("脚本给抽奖函数的次数是 " + times
                    + "。抽 0 次不是一种玩法 —— 不想抽就 return null，由活动域翻译成「次数用完」");
        }
        if (times > DrawLimits.MAX_TIMES) {
            throw new BusinessException("脚本给抽奖函数的次数是 " + times
                    + "，超过上限 " + DrawLimits.MAX_TIMES + "。这多半是脚本把次数算错了 —— "
                    + "不截断是刻意的：截断之后没有人会知道脚本要的是 " + times + " 次");
        }

        log.info("[抽奖-脚本] activityCode: {}, poolCode: {}, memberId: {}, times: {}",
                play.activityCode(), poolCode, play.memberId(), times);
        return activityDrawFacade.draw(
                new DrawCmd(play.activityCode(), poolCode, play.memberId(), play.requestId(), times));
    }
}
