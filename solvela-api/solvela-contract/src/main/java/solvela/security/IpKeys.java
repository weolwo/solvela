package solvela.security;

import java.math.BigInteger;

/**
 * 把客户端 IP 归一成限流计数用的「值」。网关数的时候用它，后台按 IP 查处罚的时候也用它 —— 两边必须算出同一个串。
 *
 * <h3>为什么 IPv6 不按完整地址数</h3>
 * 手机的 IPv6 隐私地址通常一天一换，同一个家庭 / 同一台手机前 64 位基本不变。按完整地址数等于没限，
 * 所以「单 IP」在 IPv6 下是 /64；「IP 段」在 IPv4 下是 /24、在 IPv6 下是 /48（网关方案 §2.2、§2.5）。
 *
 * <p>IPv4-mapped 地址（{@code ::ffff:1.2.3.4}）按 IPv4 处理（{@link IpRange#single} 已经这么做了）。
 * 不是合法 IP 字面量时返回 null —— 调用方跳过这个维度，而不是拿一个垃圾串去计数。
 *
 * @Date 2026-10-17
 */
public final class IpKeys {

    private IpKeys() {
    }

    /** 「单 IP」维度的值：IPv4 原样（点分），IPv6 取 /64 */
    public static String ip(String clientIp) {
        IpRange r = IpRange.single(clientIp);
        if (r == null) {
            return null;
        }
        return r.v6() ? "6:" + r.start().shiftRight(64).toString(16) + "/64" : dotted(r.start());
    }

    /** 「IP 段」维度的值：IPv4 取 /24，IPv6 取 /48 */
    public static String segment(String clientIp) {
        IpRange r = IpRange.single(clientIp);
        if (r == null) {
            return null;
        }
        return r.v6()
                ? "6:" + r.start().shiftRight(80).toString(16) + "/48"
                : dotted(r.start().shiftRight(8).shiftLeft(8)) + "/24";
    }

    private static String dotted(BigInteger v4) {
        long v = v4.longValue();
        return ((v >> 24) & 0xff) + "." + ((v >> 16) & 0xff) + "." + ((v >> 8) & 0xff) + "." + (v & 0xff);
    }
}
