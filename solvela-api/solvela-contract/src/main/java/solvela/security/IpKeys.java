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

    /**
     * 自动进名单时写的值（{@code t_security_list.list_value}），与限流「单 IP」同一个粒度：IPv4 原样，IPv6 取 /64 的 CIDR。
     * 与 {@link #ip} 不同，它必须能被 {@link IpRange#parse} 解回去 —— 名单会经快照下发给网关去匹配。
     *
     * @return 不是合法 IP 为 null
     */
    public static String listValue(String clientIp) {
        return cidr(clientIp, 32, 64);
    }

    /** 自动拉灰整段时写的值：IPv4 /24、IPv6 /48 的 CIDR */
    public static String segmentListValue(String clientIp) {
        return cidr(clientIp, 24, 48);
    }

    /** 名单值的维度：带前缀长度的是 IP 段（2），否则是单个 IP（1）。与 {@code SecurityListDimensionEnum} 对应 */
    public static int listDimension(String listValue) {
        return listValue.indexOf('/') >= 0 ? 2 : 1;
    }

    /**
     * 名单值 → 处罚键里用的那个值（{@link #ip} 的写法）。只有「一个 IPv4」或「IPv6 /64」能对上一个 IP 处罚键，
     * 别的（更大的段）返回 null。后台撤销一条自动黑名单时，用它找到要一并删掉的处罚键。
     */
    public static String ipOfListValue(String listValue) {
        IpRange r;
        try {
            r = IpRange.parse(listValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
        BigInteger size = r.end().subtract(r.start());
        if (!r.v6() && size.signum() == 0) {
            return dotted(r.start());
        }
        if (r.v6() && size.equals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE))) {
            return "6:" + r.start().shiftRight(64).toString(16) + "/64";
        }
        return null;
    }

    private static String cidr(String clientIp, int v4Prefix, int v6Prefix) {
        IpRange r = IpRange.single(clientIp);
        if (r == null) {
            return null;
        }
        if (!r.v6()) {
            BigInteger start = r.start().shiftRight(32 - v4Prefix).shiftLeft(32 - v4Prefix);
            return v4Prefix == 32 ? dotted(start) : dotted(start) + "/" + v4Prefix;
        }
        BigInteger start = r.start().shiftRight(128 - v6Prefix).shiftLeft(128 - v6Prefix);
        byte[] raw = start.toByteArray();
        byte[] bytes = new byte[16];
        System.arraycopy(raw, Math.max(0, raw.length - 16), bytes, Math.max(0, 16 - raw.length), Math.min(16, raw.length));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff)));
        }
        return sb + "/" + v6Prefix;
    }

    private static String dotted(BigInteger v4) {
        long v = v4.longValue();
        return ((v >> 24) & 0xff) + "." + ((v >> 16) & 0xff) + "." + ((v >> 8) & 0xff) + "." + (v & 0xff);
    }
}
