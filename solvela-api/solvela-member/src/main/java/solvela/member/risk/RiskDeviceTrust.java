package solvela.member.risk;

/**
 * 这台设备对<b>这个账号</b>来说可信到什么程度。取值写进 {@code t_member_risk_event.device_trust}。
 *
 * <p>定义复用二次验证那一套（{@code MemberStepUpService.check}）：登录风控和资产出口的二次验证
 * 对「这是不是他的老设备」必须是同一个答案，两份定义迟早漂移。
 * 唯一多出来的是 {@link #REGISTRATION}：注册时那台设备 —— 与新设备提醒的判据一致。
 *
 * @Date 2026-10-15
 */
public enum RiskDeviceTrust {

    /** 没带设备令牌。矩阵里按「新设备」处理 */
    NO_DEVICE(0, false),

    NEW(1, false),

    /** 这台设备上的成功登录早于门槛（老交情） */
    HISTORY(2, true),

    /** 这台设备上通过过一次二次验证 */
    VERIFIED(3, true),

    /** 注册时用的那台 */
    REGISTRATION(4, true),
    ;

    private final int value;

    private final boolean trusted;

    RiskDeviceTrust(int value, boolean trusted) {
        this.value = value;
        this.trusted = trusted;
    }

    public int value() {
        return value;
    }

    public boolean trusted() {
        return trusted;
    }
}
