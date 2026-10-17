package solvela.security;

/**
 * 网关、app-biz、后台<b>共同读写</b>的 Redis 键 —— 名字只在这里出现一次。
 *
 * <h3>🔴 这些键不带「项目:环境:」前缀</h3>
 * app-biz 里 {@code RedisService.generateRedisKey} 会给键加 {@code 项目名:环境:} 前缀，网关不会。
 * 两边各自拼的话，同一个处罚键在两个进程里是两个名字：会员域写下的蜜罐账号处罚，网关永远读不到，
 * 而且没有任何报错。所以这里的键是<b>原样使用</b>的完整键名，两边都不许再包一层 —— 与会话键
 * {@code app:auth:*}（{@code MemberRedisTokenStore}）同一个约定。
 *
 * <p>⚠️ 改这里等于改线上协议：网关、app-biz、后台必须同时发版。
 *
 * @Date 2026-10-17
 */
public final class SecurityKeys {

    private SecurityKeys() {
    }

    // ------------------------------------------------------------------ 限流（网关写，后台查与解除）

    /** 限流计数：{@code rl:c:{规则目标}:{维度}:{值}}。规则目标是场景编码或接口键 */
    public static String rateLimitCounter(String target, int dimension, String value) {
        return "rl:c:" + target + ":" + dimension + ":" + value;
    }

    /** 限流长窗口（1h / 1d）计数：{@code rl:l:{规则目标}:{维度}:{值}}。与 10 分钟内的计数分开存，桶的粒度不同 */
    public static String rateLimitLongCounter(String target, int dimension, String value) {
        return "rl:l:" + target + ":" + dimension + ":" + value;
    }

    /** 限流处罚（档位 + 到期）：{@code rl:p:{规则目标}:{维度}:{值}} */
    public static String rateLimitPenalty(String target, int dimension, String value) {
        return "rl:p:" + target + ":" + dimension + ":" + value;
    }

    // ------------------------------------------------------------------ 安全闸（网关与会员域都会写，网关执行）

    /**
     * 全局处罚（蜜罐、关键字、蜜罐账号）：作用于所有场景，由网关的安全闸执行。
     * 🔴 值是到期时间（<b>epoch 秒</b>），TTL 与之相同；IP 先过 {@link IpKeys#ip}（IPv6 取 /64），写的一方与读的一方必须一致。
     */
    public static String securityPenaltyIp(String ip) {
        return "sec:pen:ip:" + ip;
    }

    /** 同上，设备维度 */
    public static String securityPenaltyDevice(String deviceId) {
        return "sec:pen:dev:" + deviceId;
    }

    /**
     * 蜜罐熔断：最近一个窗口里命中过蜜罐的<b>不同 IP</b>（ZSET，成员是 IP、分数是毫秒时间）。
     * 太多不同 IP 同时命中，更可能是我们自己的前端出了 bug 或蜜罐路径被公开链接了 —— 那时停止处罚（网关方案 §5.3）
     */
    public static final String HONEYPOT_BREAKER = "sec:hp:breaker";

    /**
     * 被封禁的设备号集合。<b>真相源是 {@code t_device.status}</b>，这里只是镜像：
     * 会员域在封禁 / 解封时同步，并有定时任务按库全量重建。
     */
    public static final String BANNED_DEVICES = "sec:dev:banned";

    /** 封禁期间被安全闸挡掉的请求数：{@code sec:blocked:{名单维度}:{值}}，定时同步到 {@code t_security_list.blocked_count} */
    public static String blockedCounter(int listDimension, String value) {
        return "sec:blocked:" + listDimension + ":" + value;
    }

    /**
     * 安全事件的 Redis Stream。网关（与会员域）投递，app-biz 消费写 {@code t_security_incident}。
     * 每条消息一个字段 {@link #INCIDENT_FIELD}，值是 {@link SecurityIncidentEvent} 的 JSON。
     */
    public static final String INCIDENT_STREAM = "sec:incident";

    /** Stream 消息里装事件 JSON 的那个字段名 */
    public static final String INCIDENT_FIELD = "event";

    // ------------------------------------------------------------------ 规则下发（后台写，网关读；网关回报，后台读）

    /**
     * 规则快照，一个<b>哈希</b>：{@link #SNAPSHOT_FIELD_VERSION}、{@link #SNAPSHOT_FIELD_BUILT_AT}、{@link #SNAPSHOT_FIELD_BODY}。
     * 网关每 15 秒只读 version，变了才拉 body —— 一份几 KB 的 JSON 不必每次都搬。
     * 三个字段由发布方用一段脚本原子地一起写（见 {@code GatewaySnapshotPublisher}）。
     */
    public static final String RULE_SNAPSHOT = "gw:rl:snapshot";

    /** 快照哈希里的内容摘要 */
    public static final String SNAPSHOT_FIELD_VERSION = "version";

    /** 快照哈希里的发布方读库时刻（数据库时钟，epoch 毫秒） */
    public static final String SNAPSHOT_FIELD_BUILT_AT = "builtAt";

    /** 快照哈希里的 {@link GatewayRuleSnapshot} JSON */
    public static final String SNAPSHOT_FIELD_BODY = "body";

    /** 网关上报的路由清单：后台接口覆盖的下拉框与 target 校验靠它 */
    public static final String ROUTES = "gw:routes";

    /** 某个网关实例已加载的规则版本：{@code gw:rl:loaded:{实例}}，短 TTL，实例停了自然消失 */
    public static String loadedVersion(String instance) {
        return "gw:rl:loaded:" + instance;
    }

    /** 扫描全部实例的已加载版本时用的前缀 */
    public static final String LOADED_VERSION_PREFIX = "gw:rl:loaded:";
}
