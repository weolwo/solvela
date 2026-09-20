package solvela.member.grade;

/**
 * 期末结算的结果 —— 写进 {@code t_member_period_summary.settle_result}。
 *
 * <h3>它和 {@link GradeChangeType} 不是一回事</h3>
 * {@code GradeChangeType} 回答「等级为什么变了」，只有<b>变了</b>才有记录；
 * 这里回答「这个周期结算下来是个什么结局」，<b>每个周期都有一条</b>，
 * 包括「什么都没发生」。差别正是周期快照表存在的理由：
 * 平级过完一个周期的人，在等级留痕里是空的。
 *
 * @author alaric
 * @date 2026-09-20
 */
public final class GradeSettleResult {

    /** 期末算下来比原来高。⚠️ 正常情况下升级是即时的，这里出现说明中途漏判过 —— 值得告警 */
    public static final String UPGRADE = "UPGRADE";

    /** 等级没动 */
    public static final String KEEP = "KEEP";

    /** 掉级了。这是降级<b>唯一</b>会发生的地方 */
    public static final String DOWNGRADE = "DOWNGRADE";

    /** 本该掉级，给了保级缓冲期，等级暂时留着 */
    public static final String PROTECT_START = "PROTECT_START";

    /** 缓冲期内攒够了，保住了 —— 这条要让用户看见 */
    public static final String PROTECT_KEPT = "PROTECT_KEPT";

    /** 缓冲期满还是没够，这次真掉了 */
    public static final String PROTECT_FAILED = "PROTECT_FAILED";

    private GradeSettleResult() {
    }
}
