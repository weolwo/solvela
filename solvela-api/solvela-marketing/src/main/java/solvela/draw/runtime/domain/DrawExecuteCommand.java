package solvela.draw.runtime.domain;

/**
 * 执行抽奖的入参。
 *
 * <p>用 record：这个对象要穿过 settle / tryDeduct / saveLog / publishPrizeEvent 四层，
 * 可变的话「这个 memberId 到第三层还是不是调用方传的那个」要读全链路才能回答。
 * 上一版是 {@code @Data}。
 *
 * @param activityCode 活动编码
 * @param poolCode     奖池编码。<b>绝不能由客户端传</b> —— 那等于让用户自己挑奖池，
 *                     是运营配置里最不该开放的参数。它由玩法编排脚本算出来
 * @param memberId     会员号（关联键）。v3.71.0 之前这里收的是账号 —— 账号可改，
 *                     改完这个人的限领计数、白名单、历史流水就全对不上了，而且不报错。
 *                     展示用的账号由服务端查会员表取，调用方不需要也不应该再传。
 *                     <p>⚠️ 别再改成「从登录态取」：同一个方法会被 C 端、后台补发、
 *                     定时任务三种调用方调用，后两者没有登录态。见契约方案 §3.1
 * @param requestId    <b>这一批</b>的幂等键。传入即启用：同一个 requestId 重复提交
 *                     会拿回<b>第一次的完整结果</b>，而不是一个「重复请求」的拒绝。
 *                     <p>🔴 它不是流水单号。每一次抽奖有自己的 {@code sourceBizId}
 *                     （{@code requestId#序号}），那才是落 {@code t_draw_prize_log.trace_id}
 *                     与跨系统防重（{@code t_prize_log.uk_external_biz}）的键 ——
 *                     N 连抽共用一个单号的话，发奖侧只会落第一条，后 N-1 个奖静默丢失
 * @param times        抽几次。1 即单抽。
 *                     <p>🔴 <b>引擎不校验上限</b>（2026-09-01 决定：由上游保证）。
 *                     这意味着一个写错的脚本可以一次性把奖池抽空，也可以让单个事务里
 *                     产生成千上万次 Redis + DB 往返。要加上限的话，
 *                     加在这里比加在每个调用方便宜得多
 */
public record DrawExecuteCommand(
        String activityCode,
        String poolCode,
        Long memberId,
        String requestId,
        int times) {

    /** 单抽。绝大多数调用方用这个 */
    public static DrawExecuteCommand once(String activityCode, String poolCode, Long memberId, String requestId) {
        return new DrawExecuteCommand(activityCode, poolCode, memberId, requestId, 1);
    }
}
