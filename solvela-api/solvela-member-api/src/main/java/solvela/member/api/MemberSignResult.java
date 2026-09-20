package solvela.member.api;

/**
 * 签到结果。
 *
 * <h3>为什么不返回"涨了多少进度"</h3>
 * 因为签到<b>不知道</b>，而且不该知道。签到只做一件事：广播"这个人今天来了"。
 * 谁把它算成任务进度、算成几分，是任务域的事，而且那条链路是<b>异步</b>的 ——
 * 签到返回的那一刻它可能还没处理完。
 *
 * <p>硬要同步返回进度，就得让签到接口去等任务引擎，那等于把
 * {@code task-event-executor} 那道异步隔离拆了 —— 任务系统抖一下，签到按钮就转圈。
 * 端上要看进度，刷新一次任务中心即可。
 *
 * @param firstToday 今天<b>第一次</b>签到。false = 今天已经签过了，本次什么都没发生。
 *                   端上据此决定是提示「签到成功」还是「今天已签到」——
 *                   两者都不是错误，所以这里不用错误码
 * @author alaric
 * @date 2026-09-17
 */
public record MemberSignResult(boolean firstToday) {

    public static MemberSignResult first() {
        return new MemberSignResult(true);
    }

    public static MemberSignResult repeated() {
        return new MemberSignResult(false);
    }
}
