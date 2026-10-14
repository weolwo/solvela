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

    /** 查无此人时为 null */
    private final Member member;

    private final String deviceId;

    private final String deviceType;

    private final String clientIp;

    /** 网关已经核销了一张有效的滑块通行票 */
    private final boolean captchaPassed;

    /** 后置才知道；前置阶段为 null */
    private RiskDeviceTrust deviceTrust;

    private final Map<String, Object> features = new HashMap<>();

    public RiskContext(MemberRiskEventTypeEnum eventType, MemberLoginType loginType, String identity,
                       String identityHashHex, Member member, String deviceId, String deviceType,
                       String clientIp, boolean captchaPassed) {
        this.eventType = eventType;
        this.loginType = loginType;
        this.identity = identity;
        this.identityHashHex = identityHashHex;
        this.member = member;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.clientIp = clientIp;
        this.captchaPassed = captchaPassed;
    }

    public Long memberId() {
        return member == null ? null : member.getMemberId();
    }

    public boolean memberExists() {
        return member != null;
    }

    public void deviceTrust(RiskDeviceTrust trust) {
        this.deviceTrust = trust;
    }

    /** 设备可信（老交情 / 验证过 / 注册设备）。没算过信任时一律当不可信 —— 往严的方向偏 */
    public boolean deviceTrusted() {
        return deviceTrust != null && deviceTrust.trusted();
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
