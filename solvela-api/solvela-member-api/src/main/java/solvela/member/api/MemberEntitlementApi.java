package solvela.member.api;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 我的等级权益：<b>看一眼有什么，点一下领走</b>。
 *
 * <h3>🔴 这里只有「领」，没有「发」</h3>
 * 发是 {@code GradeEntitlementJob} 的事，按等级与周期生成待领取记录。
 * C 端给一个「发」的口子等于把权益发放的判据交给客户端 ——
 * 而那正是这个域存在的理由的反面。
 *
 * <h3>⚠️ memberId 由网关从登录态取，不从请求体读</h3>
 * 领取那一步的条件更新里带着它，是<b>越权防线</b>：
 * 信客户端传的话，改一个参数就能领走别人的权益。
 *
 * @author alaric
 * @date 2026-09-22
 */
@HttpExchange("/internal/entitlement")
public interface MemberEntitlementApi {

    /**
     * 我的权益列表，待领取的在前。
     *
     * <p>⚠️ 已领取与已过期的也返回：只给待领取的话，用户点完就什么都看不见了，
     * 会以为「刚才那个东西没了」。
     */
    @GetExchange("/mine")
    List<MemberEntitlementView> mine(@RequestParam("memberId") Long memberId,
                                     @RequestParam("limit") int limit);

    /**
     * 领取一份。
     *
     * <p>🔴 返回 record 而不是裸 {@code String}：后者会被序列化成 {@code text/plain}，
     * 而网关的 RestClient 没有对应的转换器 —— 单进程内正常，跨进程直接炸。
     * 详见 {@link EntitlementClaimResult} 的类注释。
     */
    @PostExchange("/claim")
    EntitlementClaimResult claim(@RequestParam("memberId") Long memberId,
                                 @RequestParam("grantId") Long grantId);
}
