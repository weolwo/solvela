package solvela.member.risk.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.LoginLogResultEnum;
import solvela.member.risk.MemberRiskProperties;
import solvela.member.risk.RiskContext;
import solvela.member.risk.dao.RiskDeviceDao;

/**
 * 「这是一台用了很久的老设备」——  IP 维度规则的<b>限流档</b>对它不生效（方案 §4.1）。
 *
 * <h3>它解决的是一个很具体的误伤</h3>
 * 校园网、公司出口、运营商 NAT 后面一个人故意试错，整片真人跟着一小时登不上。
 * 而那些人手机里躺着一个用了半年、天天在登的 App —— 他们是这个 IP 上最不可能是攻击者的那批。
 *
 * <h3>🔴 只压限流档，不跳过整条规则</h3>
 * 滑块档照常出、计数照常加。整条跳过的话，一台「老设备」就成了一张免检通行证，
 * 而设备令牌是匿名领的 —— 攻击者也能提前领一批养着（所以判据里还有「有过成功登录」那一半）。
 *
 * <h3>🔴 判的是【设备】，不是【这个账号的设备】</h3>
 * 这条判断发生在前置，那时可能查无此人。用「这台设备对这个账号可信吗」
 * （{@link DeviceTrustProvider}）的话，有号和没号在 IP 规则上的行为就不一样了 ——
 * 那是一个精确的账号枚举口子。两者刻意是两个不同的概念，不要合并。
 *
 * <h3>一次尝试只算一次</h3>
 * 走 {@link RiskContext#feature}：四条 IP 规则都会问它，而它背后是一次点查。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OldDeviceExemption {

    private static final String FEATURE_KEY = "oldDevice";

    private final RiskDeviceDao riskDeviceDao;

    private final MemberRiskProperties properties;

    /** 这一次请求能不能免掉 IP 限流档 */
    public boolean exempt(RiskContext ctx) {
        if (!properties.oldDeviceExemptEnabled() || SolvelaStringUtil.isBlank(ctx.getDeviceId())) {
            return false;
        }
        return ctx.feature(FEATURE_KEY, () -> compute(ctx.getDeviceId()));
    }

    private boolean compute(String deviceId) {
        try {
            return riskDeviceDao.isOldDevice(deviceId,
                    properties.getOldDeviceAge().toSeconds(),
                    LoginLogResultEnum.LOGIN_SUCCESS.getValue());
        } catch (RuntimeException e) {
            /*
             * 查不到就<b>不豁免</b>（往严的方向偏）。
             * 反过来写的话，数据库一抖动，所有人都成了「老设备」—— 而被攻击时数据库恰恰最容易抖。
             */
            log.warn("【风控】判老设备失败，本次不豁免, deviceId: {}", deviceId, e);
            return false;
        }
    }
}
