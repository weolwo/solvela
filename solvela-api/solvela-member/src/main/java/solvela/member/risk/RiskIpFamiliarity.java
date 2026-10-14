package solvela.member.risk;

/**
 * 本次登录的 IP 对<b>这个账号</b>来说有多熟。取值写进 {@code t_member_risk_event.ip_familiar}。
 *
 * <h3>为什么按网段、不按整个 IP</h3>
 * 手机的 IPv6 隐私地址通常一天一换，4G 出口也在一个地址池里漂。按整个 IP 比，
 * 手机用户几乎每次都是「新 IP」—— 那条规则就只会误伤，抓不到任何东西。
 * 所以 IPv4 取 /24、IPv6 取 /64（方案 §3.3）。
 *
 * <h3>🔴 前置只认 {@link #STRICT}</h3>
 * 前置的「熟悉网络免滑块」是对<b>还没验过密码的人</b>放宽。用 {@link #LOOSE}（同城市 + 同运营商）的话，
 * 一个和受害者在同一个城市、用同一家运营商的攻击者，对一部分账号就能跳过滑块。
 * 后置时密码已经验对，放宽到 {@link #LOOSE} 的代价小得多。
 *
 * @Date 2026-10-15
 */
public enum RiskIpFamiliarity {

    /** 都不是。注意：画像为空（新号）时就是这一档 —— 新号在新网络上登录本来就该多验一道 */
    UNFAMILIAR(0),

    /** 同城市 + 同运营商出现过。只有后置认它 */
    LOOSE(1),

    /** 网段出现在最近的成功登录里（或是注册 IP 的网段） */
    STRICT(2),
    ;

    private final int value;

    RiskIpFamiliarity(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** 后置口径：宽松熟悉也算熟 */
    public boolean familiar() {
        return this != UNFAMILIAR;
    }

    /** 前置口径：只认严格同网段 */
    public boolean strict() {
        return this == STRICT;
    }
}
