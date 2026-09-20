package solvela.member.api;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 每日签到契约。
 *
 * <h3>🔴 为什么签到在<b>会员域</b>，不在任务域</h3>
 * 因为「今天来过」是会员自己的一个行为事实，不是任务的一部分。
 * 放进任务域的直接后果是：<b>没有配任务的时候签到按钮就失灵了</b> ——
 * 而一个签到按钮在运营还没配任务时也应当能点、能显示"已签到"。
 *
 * <p>反过来说，签到<b>不知道</b>任务系统存在：它只广播一个
 * {@code BizActionEvent(DAILY_SIGN, ...)}，翻译成任务事件是营销侧防腐层的活。
 * 这条和商城下单、会员注册是同一个形状。
 *
 * <h3>挂在 {@code /internal} 下</h3>
 * 与 {@code MemberAuthApi} / {@code DeviceApi} 同一个性质：这是<b>服务间</b>契约，
 * 由网关转发过来，永远不对公网开放。面向客户端的那条路由在网关上，
 * {@code memberId} 由网关<b>从登录态取</b>，绝不接受客户端传 ——
 * 不然任何人都能替别人签到。
 *
 * @author alaric
 * @date 2026-09-17
 */
@HttpExchange("/internal/member/sign")
public interface MemberSignApi {

    /**
     * 签到。
     *
     * <h3>一天只算一次，靠 Redis 的原子自增判</h3>
     * 重复点不是错误，返回 {@code firstToday=false} —— 端上按"今天已签到"提示即可。
     * 用返回值而不是异常，是因为「今天签过了」是一个<b>完全正常的结果</b>，
     * 而异常会让端上分不清它和"服务挂了"。
     *
     * <p>⚠️ 幂等只在<b>这一层</b>做了一次。任务那一侧还有第二道
     * （{@code t_task_record_flow} 的唯一键 + 按事件自然日兜底的幂等键），
     * 两道是独立的：Redis 被清空时这一道会失效，但进度依然不会重复涨。
     */
    @PostExchange
    MemberSignResult sign(@RequestParam Long memberId);

    /**
     * 今天签过没有。给页面画按钮状态用。
     *
     * <p>⚠️ 它读的是 Redis，<b>不是账本</b>：Redis 被清空之后这里会回到"未签到"，
     * 用户可以再签一次。那次重签在任务侧仍然不会重复计数（见上面第二道幂等），
     * 所以最坏后果只是按钮状态短暂不准，不是数据错。
     *
     * <p>真要做签到日历、连续签到天数，那需要一张表 —— 那是另一个需求，
     * 别把它塞进这个只回答一个布尔值的接口里。
     */
    @GetExchange("/today")
    boolean signedToday(@RequestParam Long memberId);
}
