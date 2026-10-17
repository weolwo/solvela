package solvela.security;

/**
 * 限流的计数维度。{@link #code} 就是 {@code t_rate_limit_rule.dimension}、也是 Redis 计数 / 处罚键里的那个数字
 * （{@link SecurityKeys#rateLimitPenalty}）。网关按它计数，后台按它查、按它校验，两边共用这一份。
 *
 * @Date 2026-10-17
 */
public enum RateLimitDimension {

    GLOBAL(0), IP(1), ACCOUNT(2), DEVICE(3), IP_SEGMENT(4);

    private final int code;

    RateLimitDimension(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /** 按 IP 数的两个维度：老设备与 IP 白名单都跳过它们 */
    public boolean ipBased() {
        return this == IP || this == IP_SEGMENT;
    }

    /** 不认识的编码返回 null（快照里有新版后台加的维度时，旧网关忽略那一行） */
    public static RateLimitDimension of(int code) {
        for (RateLimitDimension d : values()) {
            if (d.code == code) {
                return d;
            }
        }
        return null;
    }
}
