package solvela.marketing.api;

/**
 * 抽一次。
 *
 * @param activityCode 活动编码
 * @param poolCode     奖池编码
 * @param memberId     会员号。<b>由网关从登录态解析后显式传入</b>，不靠 ThreadLocal ——
 *                     域服务不该有「当前登录用户」这个概念：同一个方法还会被后台补发、
 *                     定时任务调用，那些场景根本没有登录态，而 ThreadLocal 跨线程池和跨进程都会丢
 * @param requestId    幂等键。<b>由客户端生成并携带</b>（一次点击一个 id），不是网关生成的 ——
 *                     网关生成的话，客户端重试会拿到不同的 id，幂等当场失效。
 *                     远程调用超时不代表没执行，没有它就只能选择「可能重复发奖」或「可能白扣一次机会」
 */
public record DrawCmd(String activityCode, String poolCode, Long memberId, String requestId) {
}
