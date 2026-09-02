package solvela.marketing.api;

/**
 * 抽奖的<b>硬性上限</b>。契约模块里的一个数，网关与脚本函数共用。
 *
 * <h3>为什么上限不在引擎里</h3>
 * {@code DrawExecuteService} 刻意不校验次数上限（2026-09-01 决定：由上游保证）——
 * 它是纯粹的判定与派发引擎，「一次能抽几次」是业务规则不是引擎规则。
 *
 * <p>但「上游保证」不等于「没人管」。真正需要设防的是两处<b>不可信来源</b>：
 * <ul>
 *   <li><b>客户端</b>：{@code DrawRequest.times} 是用户可以随便填的数字。
 *       不封顶的话一个 {@code times=100000} 就能在一个事务里打十万轮
 *       Redis 预扣 + 十万行流水 insert + 十万个派发事件；</li>
 *   <li><b>编排脚本</b>：运营手写的 QLExpress。一个笔误
 *       {@code draw_executeMultiDrawByScript(pool, 1000)} 会把奖池当场抽空，
 *       而且它是「成功」的 —— 没有任何报错。</li>
 * </ul>
 * 两处都在这个常量上封顶，所以引擎那条「不校验上限」的决定是安全的：
 * 到得了引擎的 times 已经被两道关卡都过了一遍。
 *
 * <h3>两处封顶的处置刻意不同</h3>
 * 客户端超限是<b>用户输入错误</b> -> 400；脚本超限是<b>配置事故</b> -> 抛异常变 5xx 并告警。
 * 同一个数，不同的责任方。
 */
public final class DrawLimits {

    /** 一次请求最多抽几次。十连抽 */
    public static final int MAX_TIMES = 10;

    private DrawLimits() {
    }
}
