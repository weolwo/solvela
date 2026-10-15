package solvela.member.risk.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.util.SolvelaStringUtil;
import tools.jackson.databind.JsonNode;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 关于一个 IP，风控需要知道的那几件事：它在哪、属于哪个网段、是不是机房。
 *
 * <h3>为什么不直接用落库的 {@code ip_region}</h3>
 * 那一列是<b>展示用</b>的串，缺失的段被压掉了（{@code 中国|0|0|0|阿里云} → {@code 中国|阿里云}），
 * 第二项是省还是运营商分不出来。判断必须按位置取字段，所以这里拿 {@code client_ip} 重新解析
 * （{@code SolvelaIpUtil.getRegionFields}，进程内 xdb 查找，一次微秒级）。
 *
 * <p>代价：xdb 数据文件升级后，历史登录的归属地会按新数据重算。对「常用城市」这个用途，
 * 用新数据反而更准 —— 要的是「他现在这个 IP 像不像他平时那些 IP」，不是「当时判成了什么」。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
public class IpIntel {

    /** 判「同一个网段」时 IPv4 取多少位 */
    private static final int DEFAULT_V4_PREFIX = 24;

    /**
     * IPv6 取多少位。
     *
     * <p>/64 是一个终端用户通常分到的整段。再细就没有意义 —— 手机的 IPv6 隐私地址
     * 每天都在换后 64 位，按整个地址比，同一个人每次都是「新 IP」。
     */
    private static final int DEFAULT_V6_PREFIX = 64;

    /** ip2region 对内网地址的回答 */
    private static final String INTRANET = "内网IP";

    /**
     * 一个 IP 解析出来的样子。每一项都可能是 null —— 数据缺失是常态，不是故障。
     *
     * @param prefix 网段标识（如 {@code 4:1.2.3}），用来判「是不是同一个网段」
     */
    public record Location(String country, String province, String city, String isp, String prefix) {

        /** 连国家都解析不出来 → 地理类规则一律不判，而不是当成「新国家」 */
        public boolean unknown() {
            return country == null;
        }

        public boolean intranet() {
            return INTRANET.equals(country);
        }

        /** 常用城市的键。三段任一缺失就返回 null —— 宁可不判，也不要拿半个地名去比 */
        public String cityKey() {
            return country == null || province == null || city == null
                    ? null : country + "|" + province + "|" + city;
        }

        /** 宽松口径用的键：同城市 + 同运营商 */
        public String cityIspKey() {
            String key = cityKey();
            return key == null || isp == null ? null : key + "|" + isp;
        }
    }

    public Location resolve(String ip) {
        return resolve(ip, DEFAULT_V4_PREFIX, DEFAULT_V6_PREFIX);
    }

    public Location resolve(String ip, int v4Prefix, int v6Prefix) {
        if (SolvelaStringUtil.isBlank(ip)) {
            return new Location(null, null, null, null, null);
        }
        String[] fields = SolvelaIpUtil.getRegionFields(ip);
        return new Location(fields[0], fields[1], fields[2], fields[3], prefix(ip, v4Prefix, v6Prefix));
    }

    /**
     * 网段标识：把地址按位掩掉主机部分，再连同 IP 版本一起当键。
     *
     * <h3>🔴 为什么不按字符串截断</h3>
     * 「取前三段」对 {@code 1.2.3.4} 看着能用，对 IPv6 的各种缩写写法（{@code ::1}、
     * {@code 2001:db8::}、{@code ::ffff:1.2.3.4}）完全不成立 —— 同一个地址有多种写法，
     * 字符串截断会把它们判成不同网段。所以解析成字节再掩位。
     *
     * @return 解析不了时返回 null（不参与网段比较），不抛异常
     */
    public String prefix(String ip, int v4Prefix, int v6Prefix) {
        byte[] bytes = toBytes(ip);
        if (bytes == null) {
            return null;
        }
        boolean v6 = bytes.length == 16;
        int bits = Math.max(0, Math.min(v6 ? v6Prefix : v4Prefix, bytes.length * 8));
        StringBuilder sb = new StringBuilder(v6 ? "6:" : "4:");
        for (int i = 0; i < bytes.length; i++) {
            int remaining = bits - i * 8;
            if (remaining <= 0) {
                break;
            }
            int value = bytes[i] & 0xFF;
            if (remaining < 8) {
                value &= (0xFF << (8 - remaining)) & 0xFF;
            }
            if (i > 0) {
                sb.append('.');
            }
            sb.append(value);
        }
        return sb.append('/').append(bits).toString();
    }

    /**
     * ISP 字段命中机房关键字。
     *
     * <h3>⚠️ 这是关键字级的判断，覆盖不全</h3>
     * 它只认得我们列出来的那几家。真要可靠地识别机房、代理、Tor，得接商业 IP 情报 ——
     * 那是 P3 的事。所以这条规则的分值不该高到能单独决定什么。
     */
    public boolean datacenter(Location location, JsonNode params) {
        if (location == null || location.isp() == null) {
            return false;
        }
        String isp = location.isp().toLowerCase(Locale.ROOT);
        for (String keyword : keywords(params)) {
            if (!keyword.isEmpty() && isp.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> keywords(JsonNode params) {
        JsonNode node = params == null ? null : params.path("keywords");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            keywords.add(item.asString());
        }
        return keywords;
    }

    private static byte[] toBytes(String ip) {
        try {
            if (ip == null) {
                return null;
            }
            // 🔴 用 getByName 会在 ip 不是字面量时去做 DNS 解析 —— 一次网络往返，而这里在登录路径上。
            // 先挡掉明显不是地址的输入；真正的字面量解析不触发 DNS
            String text = ip.trim();
            if (text.isEmpty() || (text.indexOf(':') < 0 && text.indexOf('.') < 0)) {
                return null;
            }
            // ofLiteral 只认字面量，不会去做 DNS 解析（getByName 会），所以它不抛受检异常
            return InetAddress.ofLiteral(text).getAddress();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
