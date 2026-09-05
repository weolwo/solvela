package solvela.member.api;

/**
 * 扣减 / 退还的结果。
 *
 * <h3>🔴 用返回值表达失败，不抛异常</h3>
 * 余额不足、账户冻结、并发冲突都是<b>预期内</b>的 —— 它们是业务结果，不是故障。
 * 抛出去的话跨进程之后一律变成 5xx，监控上会多出一堆假的服务端错误，
 * 而真正的故障反而被淹掉。这和 {@code DrawResultView} / {@code ProposalResult}
 * 是同一套做法。
 *
 * @param accepted 受理了没有。为 true 时 {@link #reason} 必为 null
 * @param reason   没受理的原因。调用方用 switch 表达式接，别写 default ——
 *                 新增一种拒绝时编译不过，比悄悄显示成「操作失败」好
 */
public record AssetDebitResult(boolean accepted, AssetDebitReason reason) {

    private static final AssetDebitResult ACCEPTED = new AssetDebitResult(true, null);

    public static AssetDebitResult ofAccepted() {
        return ACCEPTED;
    }

    public static AssetDebitResult ofReject(AssetDebitReason reason) {
        return new AssetDebitResult(false, reason);
    }
}
