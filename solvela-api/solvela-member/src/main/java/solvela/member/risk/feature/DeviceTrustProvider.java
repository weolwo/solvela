package solvela.member.risk.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.api.DeviceTrust;
import solvela.member.api.StepUpCmd;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskDeviceTrust;
import solvela.member.risk.config.RiskListCache;
import solvela.member.stepup.MemberStepUpService;

/**
 * 「这台设备对这个账号可信到什么程度」。
 *
 * <h3>🔴 复用二次验证那一套，不另起一份定义</h3>
 * 登录风控和资产出口的二次验证对「这是不是他的老设备」<b>必须是同一个答案</b>。
 * 两份定义迟早漂移，而漂移之后会出现一种说不清的状态：登录时算他可信、兑换时算他不可信（或者反过来）。
 * 所以这里直接问 {@link MemberStepUpService#check}，信任的撤销（重置密码、冻结、下线其他设备）也跟着那一套。
 *
 * <h3>多出来的一档：注册设备</h3>
 * 注册成功直接就是登录态，而且<b>刻意不写登录日志</b>，所以「老交情」那一档对刚注册的人恒为否。
 * 不认注册设备的话，一个用户在注册的那台机器上第二次登录就要验一道码 —— 而那台机器恰恰是最可信的。
 * 判据与新设备登录提醒共用（同一个 Redis 键）。
 *
 * <h3>🔴 会员灰名单上的人：可信设备一律按新设备算</h3>
 * 网关方案 §5.5（2026-10-17）。于是不熟悉的网络上要 OTP —— 他只会感觉「这几天验证码多了一点」，对他本人不提示。
 * 资产出口那一侧的「每次都要二次验证」在 {@code MemberStepUpService#assetOut}，不在这里：
 * 这里只影响登录矩阵，那边还要让「刚验过的这一次」放行。
 *
 * <h3>一次尝试只算一次</h3>
 * 走 {@link RiskContext#feature}：前置的密码冷却要用它分桶，后置的矩阵也要用它，
 * 而它背后是一次 Redis 查询加一次可能的登录日志点查。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTrustProvider {

    private static final String FEATURE_KEY = "deviceTrust";

    /** 与 {@code NewDeviceLoginNotifier} 同一个键 —— 注册设备的判据只能有一个 */
    private static final String KEY_REGISTRATION_DEVICE = "mbr:login:reg-device:";

    private final MemberStepUpService stepUpService;

    private final RedisService redisService;

    private final RiskListCache listCache;

    /**
     * 算出信任档位并记进上下文。查无此人、没带设备令牌时分别是 {@link RiskDeviceTrust#NEW}
     * 与 {@link RiskDeviceTrust#NO_DEVICE} —— 都不可信。
     */
    public RiskDeviceTrust of(RiskContext ctx) {
        RiskDeviceTrust trust = ctx.feature(FEATURE_KEY, () -> compute(ctx));
        ctx.deviceTrust(trust);
        return trust;
    }

    private RiskDeviceTrust compute(RiskContext ctx) {
        RiskDeviceTrust trust = computeIgnoringGreyList(ctx);
        if (trust.trusted() && listCache.matchMember(ctx.memberId()).grey()) {
            return RiskDeviceTrust.NEW;
        }
        return trust;
    }

    private RiskDeviceTrust computeIgnoringGreyList(RiskContext ctx) {
        if (SolvelaStringUtil.isBlank(ctx.getDeviceId())) {
            return RiskDeviceTrust.NO_DEVICE;
        }
        if (ctx.memberId() == null) {
            // 查无此人：信任是「这台设备对这个账号」的属性，没有账号就谈不上信任。
            // 🔴 当成新设备（最严的那一档），而不是跳过 —— 否则有号/没号在用到信任的规则上行为就不一样了
            return RiskDeviceTrust.NEW;
        }
        if (isRegistrationDevice(ctx.memberId(), ctx.getDeviceId())) {
            return RiskDeviceTrust.REGISTRATION;
        }
        DeviceTrust trust = stepUpService.check(
                StepUpCmd.of(ctx.memberId(), ctx.getDeviceId(), ctx.getClientIp()));
        return switch (trust) {
            case VERIFIED -> RiskDeviceTrust.VERIFIED;
            case HISTORY -> RiskDeviceTrust.HISTORY;
            case NEW_DEVICE -> RiskDeviceTrust.NEW;
            case NO_DEVICE -> RiskDeviceTrust.NO_DEVICE;
        };
    }

    private boolean isRegistrationDevice(Long memberId, String deviceId) {
        try {
            return deviceId.equals(redisService.get(
                    redisService.generateRedisKey(KEY_REGISTRATION_DEVICE, String.valueOf(memberId))));
        } catch (RuntimeException e) {
            // 取不到就当「不是注册设备」—— 往严的方向偏，代价只是多验一道码
            log.warn("【风控】读注册设备失败, memberId: {}", memberId, e);
            return false;
        }
    }
}
