package solvela.member.api;

/**
 * 资产扣减没被受理的原因。
 *
 * <p>用枚举而不是自由文本：调用方要按原因决定怎么说
 *（「积分不足」得提示差多少，「账户冻结」得引导去客服），
 * 而字符串匹配是那种改一个字就静默失效的写法。
 *
 * <p>🔴 新增一个值时，所有 {@code switch} 上它的地方<b>编译不过</b> ——
 * 这正是要的：多一种拒绝原因，就得有人决定对用户怎么说。
 */
public enum AssetDebitReason {

    /** 余额不够。调用方应当算出差额告诉用户 */
    BALANCE_NOT_ENOUGH,

    /** 钱包被冻结或不可用 */
    WALLET_UNAVAILABLE,

    /**
     * 并发冲突：余额在这一瞬被别的操作改了（乐观锁 CAS 失败）。
     * <b>可重试</b> —— 和「余额不足」不是一回事，别合并成同一个提示。
     */
    CONCURRENT_CONFLICT,

    /** 会员不存在。走到这里说明调用方拿了一个假的 memberId */
    MEMBER_NOT_FOUND,

    /** 其它。调用方一律翻成「操作失败，请稍后再试」，具体原因在服务端日志里 */
    UNKNOWN
}
