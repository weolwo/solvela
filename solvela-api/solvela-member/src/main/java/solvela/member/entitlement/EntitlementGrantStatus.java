package solvela.member.entitlement;

/**
 * 权益发放记录的状态。
 *
 * <p>🔴 「待领取」和「已过期」必须分开，不能靠 {@code expire_time < now} 现算：
 * 那样「这个月有多少权益没人领」这个数只能靠扫全表算，
 * 而它恰恰是运营最常问的那个数。
 *
 * @author alaric
 * @date 2026-09-22
 */
public final class EntitlementGrantStatus {

    private EntitlementGrantStatus() {
    }

    /** 待领取 */
    public static final int PENDING = 0;

    /** 已领取 */
    public static final int CLAIMED = 1;

    /** 已过期，再也领不了了 */
    public static final int EXPIRED = 2;
}
