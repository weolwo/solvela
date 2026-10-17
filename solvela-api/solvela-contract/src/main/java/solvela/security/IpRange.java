package solvela.security;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 一个 IP 段（含单个 IP），以及 IP 字面量的解析。IPv4 与 IPv6 分开比较，互不相交。
 *
 * <h3>🔴 解析前先确认它是字面量</h3>
 * {@link InetAddress#getByName} 拿到的如果不是 IP 字面量，会<b>去做 DNS 解析</b> ——
 * 名单里一条手滑录错的值（比如 {@code example.com}）就会让每次加载都去查一次 DNS，
 * 而请求路径上解析客户端 IP 时更不能出现网络调用。所以只放行由十六进制数字、{@code .}、{@code :} 组成的串。
 *
 * @param v6 是否 IPv6；IPv4-mapped 地址（{@code ::ffff:1.2.3.4}）按 IPv4 处理
 */
public record IpRange(boolean v6, BigInteger start, BigInteger end) {

    /**
     * 解析 {@code 1.2.3.4}、{@code 1.2.3.0/24}、{@code 2001:db8::/48}。不合法抛 {@link IllegalArgumentException}。
     */
    public static IpRange parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("空的 IP");
        }
        String s = text.trim();
        int slash = s.indexOf('/');
        String addr = slash < 0 ? s : s.substring(0, slash);
        byte[] bytes = literalBytes(addr);
        if (bytes == null) {
            throw new IllegalArgumentException("不是 IP 字面量: " + text);
        }
        int bits = bytes.length * 8;
        int prefix = bits;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(s.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("前缀长度不合法: " + text);
            }
            if (prefix < 0 || prefix > bits) {
                throw new IllegalArgumentException("前缀长度越界: " + text);
            }
        }
        BigInteger value = new BigInteger(1, bytes);
        BigInteger hostMask = BigInteger.ONE.shiftLeft(bits - prefix).subtract(BigInteger.ONE);
        BigInteger start = value.andNot(hostMask);
        return new IpRange(bytes.length == 16, start, start.or(hostMask));
    }

    /** 单个 IP → 数值。不是合法字面量返回 null（内网标识、空值、垃圾数据都可能出现在请求里） */
    public static IpRange single(String ip) {
        if (ip == null) {
            return null;
        }
        byte[] bytes = literalBytes(ip.trim());
        if (bytes == null) {
            return null;
        }
        BigInteger v = new BigInteger(1, bytes);
        return new IpRange(bytes.length == 16, v, v);
    }

    public boolean contains(IpRange ip) {
        return ip != null && ip.v6 == v6 && start.compareTo(ip.start) <= 0 && end.compareTo(ip.end) >= 0;
    }

    private static byte[] literalBytes(String addr) {
        if (addr.isEmpty() || addr.length() > 45) {
            return null;
        }
        boolean hasColon = false;
        for (int i = 0; i < addr.length(); i++) {
            char c = addr.charAt(i);
            if (c == ':') {
                hasColon = true;
            } else if (!(c == '.' || Character.digit(c, 16) >= 0)) {
                return null;
            }
        }
        // 没有冒号的只能是 IPv4，而且必须是完整的四段十进制。
        // 🔴 InetAddress 认老式写法：「1.2.3」会被解析成 1.2.0.3、「16909060」会被解析成 1.2.3.4 ——
        //    名单里录错一个值，就会悄悄封错一个地址
        if (!hasColon && !isDottedQuad(addr)) {
            return null;
        }
        try {
            return InetAddress.getByName(addr).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean isDottedQuad(String addr) {
        String[] parts = addr.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3 || !p.chars().allMatch(c -> c >= '0' && c <= '9')
                    || Integer.parseInt(p) > 255) {
                return false;
            }
        }
        return true;
    }
}
