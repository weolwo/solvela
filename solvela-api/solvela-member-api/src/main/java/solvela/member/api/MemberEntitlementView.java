package solvela.member.api;

import java.time.LocalDateTime;

/**
 * 我的一份权益（待领取 / 已领取 / 已过期）。
 *
 * @param grantId         领取时要带回来的 id
 * @param entitlementName 权益名，如「白金生日礼」
 * @param assetName       领到的东西叫什么，如「生日 20 元券」。
 *                        <p>⚠️ 与 {@code entitlementName} 分开给：前者是<b>为什么给你</b>，
 *                        后者是<b>给你什么</b>。合并成一个的话，页面上只能二选一说，
 *                        而用户两件都想知道。
 * @param periodKey       周期键。生日礼是 {@code yyyy}，月度券是 {@code yyyyMM}
 * @param status          0-待领取, 1-已领取, 2-已过期
 * @param expireTime      领取截止时间。已领取的仍然给出来 —— 用户会想知道当时的期限
 * @param claimTime       领取时间，没领为 null
 *
 * @author alaric
 * @date 2026-09-22
 */
public record MemberEntitlementView(
        Long grantId,
        String entitlementName,
        String assetName,
        String periodKey,
        Integer status,
        LocalDateTime expireTime,
        LocalDateTime claimTime) {
}
