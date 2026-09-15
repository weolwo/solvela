package solvela.ledger.coupon.writeoff.domain;

import java.math.BigDecimal;

/**
 * 锁定 / 确认 / 释放的结果。
 *
 * <h3>🔴 为什么是返回值而不是抛异常</h3>
 * 和 {@code AssetGrantApiService} 同一条规矩：
 *
 * <ul>
 *   <li><b>返回失败 = 别再试了</b>（券被别人锁走了、已经用掉了、不是这个人的券）——
 *       调用方该把这一单按「没用券」处理或直接拒单；</li>
 *   <li><b>抛异常 = 可以再试</b>（数据库抖动）—— 调用方该回滚，让下一轮重试接手。</li>
 * </ul>
 *
 * <p>把这两类混成一种，结果要么是永远重试一个永远不会成功的动作，
 * 要么是一次网络抖动让用户的券彻底卡死。
 *
 * @param ok             成功没有
 * @param idempotent     是不是「本来就已经是这个状态了」。重复提交、失败重试、
 *                       两条取消路径同时跑到，都会走到这里。
 *                       ⚠️ 它是<b>成功</b>，但调用方不该再重复做后续动作
 *                       —— 比如再退一次积分
 * @param discountAmount 实际抵扣额。锁定和确认时有值，释放时为 null
 * @param message        失败原因。给日志和排查用，<b>不直接展示给用户</b>
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record CouponWriteOffResult(boolean ok,
                                   boolean idempotent,
                                   BigDecimal discountAmount,
                                   String message) {

    public static CouponWriteOffResult ok(BigDecimal discountAmount) {
        return new CouponWriteOffResult(true, false, discountAmount, null);
    }

    /** 本来就已经是这个状态了。是成功，但调用方别再重复做后续动作 */
    public static CouponWriteOffResult alreadyDone(BigDecimal discountAmount) {
        return new CouponWriteOffResult(true, true, discountAmount, null);
    }

    public static CouponWriteOffResult fail(String message) {
        return new CouponWriteOffResult(false, false, null, message);
    }
}
