package solvela.base.web;

import solvela.base.util.SolvelaStringUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Servlet 工具类
 *
 * 移除 hutool 后接管 cn.hutool.extra.servlet.JakartaServletUtil 的用量。
 *
 * @Date 2026-08-08
 */
public class SolvelaServletUtil {

    /**
     * 取客户端 IP 时依次尝试的请求头，顺序与原 JakartaServletUtil 一致。
     * 前面的优先级更高：X-Forwarded-For 是标准做法，后面几个是各家反代/容器的历史遗留写法。
     */
    private static final String[] CLIENT_IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private static final String UNKNOWN = "unknown";

    private SolvelaServletUtil() {
    }

    /**
     * 获取客户端真实 IP。
     *
     * 逐个头去找，找到第一个「有值且不是 unknown」的就用它；都没有才回落到 remoteAddr。
     *
     * 🔴 X-Forwarded-For 可能是一条代理链：{@code 客户端, 代理1, 代理2}。
     * **要取的是第一个非 unknown 的段，也就是最靠近客户端的那个**，不是最后一段、也不是整串。
     * 取错了拿到的是自家网关地址，登录日志/风控里所有人看起来都来自同一个 IP。
     *
     * ⚠️ 这个值来自请求头，客户端可以随便伪造。只能用于日志展示与粗粒度统计，
     * 不能作为鉴权、限流白名单之类的判断依据 —— 除非前面的反向代理会强制覆写该头。
     */
    public static String getClientIP(HttpServletRequest request) {
        for (String header : CLIENT_IP_HEADERS) {
            String ip = request.getHeader(header);
            if (!isUnknown(ip)) {
                return firstValidIp(ip);
            }
        }
        return firstValidIp(request.getRemoteAddr());
    }

    /**
     * 从可能是代理链的字符串里取第一个有效段
     */
    private static String firstValidIp(String ip) {
        if (ip == null || ip.indexOf(',') <= 0) {
            return ip;
        }
        for (String segment : ip.split(",")) {
            String trimmed = segment.trim();
            if (!isUnknown(trimmed)) {
                return trimmed;
            }
        }
        return ip;
    }

    private static boolean isUnknown(String ip) {
        return SolvelaStringUtil.isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }
}
