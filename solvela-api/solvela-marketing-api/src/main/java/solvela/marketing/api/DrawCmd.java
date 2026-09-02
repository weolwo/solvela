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
 * @param times        抽几次。1 即单抽，上限 {@link DrawLimits#MAX_TIMES}。
 *                     <p>整批共用一个 {@code requestId}：重投拿回的是<b>第一次的完整结果</b>，
 *                     不是「重复请求」的拒绝。每一次抽奖自己的业务单号由引擎派生
 *                     （{@code requestId#序号}），调用方不需要也不应该自己拼
     *                     <p>⚠️ 类型是装箱的 {@code Integer} 而不是 {@code int}，因为本对象是
     *                     <b>跨进程 body</b>：原生 int 遇到 JSON 里缺这个字段会让 Jackson 直接报
     *                     「Cannot map null into type int」-> 400。滚动发布时老调用方不带 times，
     *                     整条抽奖链路会当场 400。规范构造器把 null 归一成 1（不传 = 单抽），
     *                     所以访问器<b>永不返回 null</b>
 */
public record DrawCmd(String activityCode, String poolCode, Long memberId, String requestId, Integer times) {

    public DrawCmd {
        times = times == null ? 1 : times;
    }

    /** 单抽 */
    public static DrawCmd once(String activityCode, String poolCode, Long memberId, String requestId) {
        return new DrawCmd(activityCode, poolCode, memberId, requestId, 1);
    }
}
