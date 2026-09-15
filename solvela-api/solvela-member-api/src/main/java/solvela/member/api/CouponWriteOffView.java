package solvela.member.api;

import java.math.BigDecimal;

/**
 * 锁定 / 确认 / 释放的结果。
 *
 * <h3>🔴 用返回值表达失败，不抛异常</h3>
 * 券被别人锁走、已经用掉、兜底任务已经放回去 —— 这些都是<b>预期内</b>的业务结果，
 * 不是故障。抛出去的话跨进程之后一律变成 5xx，监控上会多出一堆假的服务端错误，
 * 而真正的故障反而被淹掉。与 {@link AssetDebitResult} 同一套做法。
 *
 * @param ok             成功没有
 * @param idempotent     是不是「本来就已经是这个状态了」。重复提交、失败重试、
 *                       两条取消路径同时跑到都会走到这里。
 *                       ⚠️ 它是<b>成功</b>，但调用方不该再重复做后续动作
 * @param discountAmount 实际抵扣额。锁定和确认时有值，释放时为 null
 * @param message        失败原因。给日志和排查用，<b>不直接展示给用户</b>
 */
public record CouponWriteOffView(boolean ok,
                                 boolean idempotent,
                                 BigDecimal discountAmount,
                                 String message) {
}
