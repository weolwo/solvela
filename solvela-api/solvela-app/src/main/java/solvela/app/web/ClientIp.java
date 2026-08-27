package solvela.app.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取客户端真实 IP。
 *
 * <p>C 端的部署形态和管理端不同：请求要经过 CDN 和公网入口的负载均衡，
 * {@code request.getRemoteAddr()} 拿到的永远是最后一跳。
 *
 * <h3>⚠️ 这个值不能用于安全决策</h3>
 * {@code X-Forwarded-For} 是客户端可以随便写的请求头 —— 任何人都能伪造成任意 IP。
 * 只有当入口网关<b>覆盖</b>（而不是追加）这个头时，第一段才可信。
 * 所以这里取到的 IP 只配用于：登录日志展示、IP 归属地、粗粒度的风控信号。
 * 不要拿它做 IP 白名单、限流的唯一维度，或者「同 IP 判定为同一人」。
 */
public final class ClientIp {

    private static final String[] HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    private static final String UNKNOWN = "unknown";

    /** 单个 IP 的最大长度：IPv6 全展开 39 字符，留点余量。超长的一律当伪造丢掉。 */
    private static final int MAX_LENGTH = 45;

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        for (String header : HEADERS) {
            String value = firstValid(request.getHeader(header));
            if (value != null) {
                return value;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? UNKNOWN : remote;
    }

    /** X-Forwarded-For 是一条代理链（{@code 客户端, 代理1, 代理2}），取第一个有效段。 */
    private static String firstValid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (String segment : raw.split(",")) {
            String ip = segment.trim();
            if (!ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip) && ip.length() <= MAX_LENGTH) {
                return ip;
            }
        }
        return null;
    }
}
