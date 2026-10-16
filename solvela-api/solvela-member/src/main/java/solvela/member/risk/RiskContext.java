package solvela.member.risk;

import lombok.Getter;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.Member;
import solvela.member.api.MemberLoginType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 一次登录尝试里规则能看到的全部东西。
 *
 * <h3>请求自带的 vs 算出来的</h3>
 * 构造时只放请求自带、零 IO 就有的东西（身份、IP、设备号）。画像、设备信任这类要查库 / Redis 的，
 * 走 {@link #feature}：<b>第一条用到它的规则才去算，同一次尝试里只算一次</b>。
 * 不用的规则（比如被可信设备绕过了）就一次都不查。
 *
 * <h3>🔴 member 可以是 null</h3>
 * 查无此人时照样要过前置决策（结论必须与「有这个号」一模一样，方案 §3.1）。
 * 前置规则用到账号信息时，必须先判 null，而且 null 时只能给<b>最严的默认值</b>。
 *
 * @Date 2026-10-15
 */
@Getter
public final class RiskContext {

    private final MemberRiskEventTypeEnum eventType;

    private final MemberLoginType loginType;

    /** 规范化之后的身份明文。<b>只在内存里</b>，不进流水、不进日志 */
    private final String identity;

    /** {@code PiiHasher} 摘要，64 位 hex。不管号存不存在都有 */
    private final String identityHashHex;

    /** 查无此人时为 null；只拿到会员号、没有加载整行时也为 null（见 {@link #memberIdOnly}） */
    private final Member member;

    /**
     * 只有会员号、没有加载整行的场景用它（资产出口、换绑 —— 它们本来就是登录态）。
     *
     * <h3>🔴 为什么不顺手查一行 Member 填进去</h3>
     * 那是在<b>每一次加地址 / 充话费</b>上加一次查询，而这些场景的规则一条都不读账号字段
     * （数的是「这台设备替几个人搬过东西」）。
     *
     * <p>也<b>刻意不</b>造一个只填了 memberId 的空 {@code Member} 塞进上面那个字段：
     * 那个对象看起来是「加载过的」，于是将来某条规则读 {@code getStatus()} 时拿到 null
     * —— 而那不会报错，只会静默判错。{@link #getMember()} 为 null 的语义是
     * 「账号字段此刻不可用」，两种情况都该如实说 null。
     */
    private final Long memberIdOnly;

    private final String deviceId;

    private final String deviceType;

    private final String clientIp;

    /** 网关已经核销了一张有效的滑块通行票 */
    private final boolean captchaPassed;

    /** 后置才知道；前置阶段为 null */
    private RiskDeviceTrust deviceTrust;

    /**
     * IP 熟悉度。由前置矩阵算出来（它是前置唯一用到账号信息的东西，而且<b>只用来放宽</b>），
     * 后置矩阵直接复用同一个值 —— 一次尝试里不该算出两个不同的熟悉度。
     */
    private RiskIpFamiliarity ipFamiliar;

    /** 账号是不是沉睡（最后活跃早于门槛）。null = 还没判 / 判不了（查无此人） */
    private Boolean dormant;

    private final Map<String, Object> features = new HashMap<>();

    public RiskContext(MemberRiskEventTypeEnum eventType, MemberLoginType loginType, String identity,
                       String identityHashHex, Member member, String deviceId, String deviceType,
                       String clientIp, boolean captchaPassed) {
        this(eventType, loginType, identity, identityHashHex, member, null,
                deviceId, deviceType, clientIp, captchaPassed);
    }

    /** 只有会员号、没有加载整行的场景（见 {@link #memberIdOnly}） */
    public RiskContext(MemberRiskEventTypeEnum eventType, Long memberId, String deviceId,
                       String clientIp) {
        this(eventType, null, null, null, null, memberId, deviceId, null, clientIp, false);
    }

    private RiskContext(MemberRiskEventTypeEnum eventType, MemberLoginType loginType, String identity,
                        String identityHashHex, Member member, Long memberIdOnly, String deviceId,
                        String deviceType, String clientIp, boolean captchaPassed) {
        this.eventType = eventType;
        this.loginType = loginType;
        this.identity = identity;
        this.identityHashHex = identityHashHex;
        this.member = member;
        this.memberIdOnly = memberIdOnly;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.clientIp = clientIp;
        this.captchaPassed = captchaPassed;
    }

    public Long memberId() {
        return member == null ? memberIdOnly : member.getMemberId();
    }

    /**
     * 知不知道这是谁。
     *
     * <p>⚠️ 它<b>不是</b>「账号字段可不可用」—— 那个问题由 {@link #getMember()} 是否为 null 回答。
     * 登录那条链路上两者等价（查得到人才有会员号）；资产出口只有会员号。
     */
    public boolean memberExists() {
        return memberId() != null;
    }

    public void deviceTrust(RiskDeviceTrust trust) {
        this.deviceTrust = trust;
    }

    /** 设备可信（老交情 / 验证过 / 注册设备）。没算过信任时一律当不可信 —— 往严的方向偏 */
    public boolean deviceTrusted() {
        return deviceTrust != null && deviceTrust.trusted();
    }

    public void ipFamiliar(RiskIpFamiliarity familiarity) {
        this.ipFamiliar = familiarity;
    }

    public void dormant(boolean value) {
        this.dormant = value;
    }

    /**
     * 惰性特征：同一次尝试里同一个 key 只算一次。
     *
     * <p>不用 {@code computeIfAbsent}：supplier 里如果又取了别的特征，会在遍历中修改同一个 HashMap。
     */
    @SuppressWarnings("unchecked")
    public <T> T feature(String key, Supplier<T> supplier) {
        if (features.containsKey(key)) {
            return (T) features.get(key);
        }
        T value = supplier.get();
        features.put(key, value);
        return value;
    }
}
