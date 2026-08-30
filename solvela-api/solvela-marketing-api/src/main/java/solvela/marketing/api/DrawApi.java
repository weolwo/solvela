package solvela.marketing.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 抽奖引擎的直调契约。由 {@code solvela-marketing} 实现。
 *
 * <h3>🔴 这不是 C 端接口，不要挂到公网路由上</h3>
 * 它收 {@code poolCode} —— 而<b>抽哪个奖池是脚本算出来的</b>：C 端请求进来后，
 * 活动域先做活动与会员校验，再进 {@code ACTIVITY_PLAY} 脚本，脚本按次数、身份、时段
 * 动态决定池子，最后在脚本里调用抽奖。把 poolCode 交给客户端传，等于让客户端绕过那段编排，
 * 自己挑一个池子抽 —— 那是运营配置里最不该开放的一个参数。
 *
 * <p>C 端走的是 {@link ActivityApi} 的 {@code draw}（随 ACTIVITY_PLAY 场景一起落地）。
 * 本接口留给<b>脚本函数适配器、内部工具与联调</b>。
 *
 * <h3>🔴 一次调用，不许拆</h3>
 * 「扣积分 → 抽奖 → 发奖」必须在<b>本接口的实现里</b>用一个本地事务完成。
 * 网关绝不能写成「先调资产 api 扣分，再调本接口抽奖」—— 拆成两个服务之后，
 * 这两次网络调用中间断电就是用户扣了分没抽奖，而且没有任何补偿路径。
 *
 * <p>今天抽奖链路本身不扣任何资产（{@code DrawExecuteService} 是纯粹的命中判定与库存派发引擎，
 * 定价由上游决定），所以实现类现在只是转发。<b>将来加消耗规则时，那段编排长在实现类里，
 * 不长在网关里</b> —— 这是本接口存在的主要理由之一。
 *
 * <h3>抽奖有两个调用方，它们对「被拒」的处置本来就不同</h3>
 * <ul>
 *   <li><b>脚本引擎</b>在编排里调用（走 {@code DrawExecuteService}，不经过本接口）——
 *       被拒时该<b>中断整段脚本</b>，由脚本侧的函数适配器把 reason 变成异常；</li>
 *   <li><b>C 端</b>经本接口纯 Java 调用 —— 被拒时要给用户一句人话和一个 4xx。</li>
 * </ul>
 * 所以引擎<b>只陈述事实</b>（返回 {@link DrawRejectReason}），抛不抛由调用方决定。
 * 如果引擎自己抛，第二种调用方就只能 catch 了再按 message 字符串分类 —— 那比不分类更糟。
 */
// 🔴 路径刻意不在 /internal/activity 之下：ActivityApi 的 draw 就映射在
// POST /internal/activity/draw，两者会撞成同一个 URL（2026-08-30 装配时真撞过，
// 表现是启动期 Ambiguous mapping）。分开也顺带把「引擎直调」和「C 端入口」在路由上就区分开
@HttpExchange("/internal/draw-engine")
public interface DrawApi {

    /**
     * 抽一次。
     *
     * <p>三种结果都由返回值表达，见 {@link DrawRejectReason}：中奖 / 没中奖 / 没被受理。
     * 只有<b>意外</b>（库挂了、代码 bug）才会抛异常 —— 那些本来就该是 5xx。
     */
    @PostExchange
    DrawResultView draw(@RequestBody DrawCmd cmd);
}
