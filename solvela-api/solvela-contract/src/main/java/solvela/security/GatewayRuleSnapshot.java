package solvela.security;

import java.util.List;
import java.util.Map;

/**
 * 后台下发给网关的<b>规则快照</b>的线上格式：发布方（会员域，后台进程里跑）与网关共用。
 * 存在 {@link SecurityKeys#RULE_SNAPSHOT} 这个哈希的 {@link SecurityKeys#SNAPSHOT_FIELD_BODY} 字段里，序列化成 JSON。
 *
 * <h3>为什么全局参数是「原样的 JSON 字符串」</h3>
 * {@code t_config} 里那两条配置（{@code gateway_rate_limit}、{@code gateway_security_gate}）的形状
 * 由网关里各自的功能解释（阶梯处罚、蜜罐、关键字……），而那些功能是一个任务一个任务长出来的。
 * 在这里把它们写成强类型，等于每加一个功能就要同时改协议、发布方、网关三处。
 * 快照只负责「原样搬过去」，解释与校验在使用它的那一侧。
 *
 * <h3>版本号是内容摘要，不是自增数</h3>
 * 后台保存时发布一次、定时任务每 5 分钟再发布一次。内容没变时摘要不变，网关就不会白白重载；
 * 而「后台改了一行 → 摘要变了 → 15 秒内网关重载」只依赖内容本身。
 *
 * <p>⚠️ 加字段向后兼容（网关忽略不认识的字段）；删字段、改含义要发布方与网关同时发版。
 *
 * @param version   内容摘要（不含 {@code builtAt}）：网关拿它判断要不要重载
 * @param builtAt   发布方读库那一刻的<b>数据库时钟</b>（epoch 毫秒）。两个发布方同时写时，只认更新的那份
 * @param rules     {@code t_rate_limit_rule} 的全部行（含关闭的，网关按 mode 自己判）
 * @param configs   全局参数：{@code t_config.config_key} → 原样的 {@code config_value}
 * @param ipLists   名单里当前有效的 IP / IP 段条目（黑、灰、白）
 * @param memberGreyList 会员灰名单（网关方案 §5.5）：这些会员在所有场景的账号维度阈值减半。可能为 null（旧格式）
 * @Date 2026-10-17
 */
public record GatewayRuleSnapshot(
        String version,
        long builtAt,
        List<Rule> rules,
        Map<String, String> configs,
        List<IpEntry> ipLists,
        List<MemberEntry> memberGreyList) {

    /** 没有会员灰名单的快照（内置默认值、测试） */
    public GatewayRuleSnapshot(String version, long builtAt, List<Rule> rules, Map<String, String> configs,
                               List<IpEntry> ipLists) {
        this(version, builtAt, rules, configs, ipLists, null);
    }

    /**
     * 一条会员灰名单。
     *
     * @param expireAt 到期时间（epoch 毫秒）。🔴 会员名单必有到期；网关按自己的时钟过滤，不等下一次快照
     */
    public record MemberEntry(long memberId, Long expireAt) {
    }

    /**
     * 一行限流规则，字段与 {@code t_rate_limit_rule} 一一对应。
     *
     * @param targetType 1-场景, 2-接口
     * @param dimension  0-全局, 1-IP, 2-账号, 3-设备, 4-IP段
     * @param mode       0-关闭, 1-陪跑, 2-生效
     */
    public record Rule(
            int targetType,
            String target,
            int dimension,
            int burst,
            int limit3m,
            int limit5m,
            int limit10m,
            String longWindow,
            Integer limitLong,
            int mode) {
    }

    /**
     * 一条 IP / IP 段名单。
     *
     * @param listType  1-黑, 2-灰, 3-白
     * @param dimension 1-IP, 2-IP段（CIDR）
     * @param source    1-人工, 2-自动。人工黑名单不豁免老设备，自动的豁免（网关方案 §5.5）
     * @param expireAt  到期时间（epoch 毫秒），null = 长期。网关按自己的时钟过滤，不等下一次快照
     */
    public record IpEntry(
            int listType,
            int dimension,
            String value,
            Integer score,
            int source,
            Long expireAt) {
    }
}
