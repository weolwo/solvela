package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberRiskLabelEnum;
import solvela.enums.MemberRiskLabelSourceEnum;
import solvela.member.risk.dao.MemberRiskEventDao;

import java.time.Duration;

/**
 * 给决策流水<b>事后打标</b>：那一次登录到底是本人还是被盗。
 *
 * <h3>它是整套风控唯一的「答案」</h3>
 * 规则给出的是猜测，标注才是事实。有了它，「打扰了多少本人」「漏过了多少盗号」
 * 才从感觉变成两个能跑 SQL 的数（方案 §8）。
 *
 * <h3>🔴 为什么不是「按会话号直接找到那一行」</h3>
 * 会话（{@code solvela-auth} 的令牌库）和决策流水（会员域的表）之间<b>没有引用</b>，
 * 而且刻意不加：令牌在网关签发、流水在域里写，要串起来就得让认证契约多带一个
 * 纯展示用的字段，并且让令牌库记住它。
 *
 * <p>代价是这里要按「会员 + 设备 + 时间相近」去找。这个匹配<b>可能找不到</b>
 * （老会话没有设备号、流水已过期被 DROP PARTITION），而那时正确的反应是
 * <b>什么都不做</b> —— 标注是给分析用的，找不到不该让「这不是我」这件事失败。
 * 用户真正要的是下线和冻结，那两件事必须成。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskLabelService {

    /**
     * 会话登录时间与流水时间的容差。
     *
     * <p>两者相差只有「签发令牌」那几毫秒，但流水的 {@code create_time} 由数据库写、
     * 会话时间由网关的 JVM 写 —— 两台机器的时钟不会完全一致。
     * 给 5 分钟：足够吃下任何正常的时钟漂移，又不至于把同一台设备上<b>另一次</b>登录标错。
     */
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final MemberRiskEventDao memberRiskEventDao;

    /**
     * 把「某台设备上那一次登录」标成被盗 / 本人。
     *
     * @param loginEpochSecond 会话的登录时间（秒）。0 或负数表示未知（老会话）—— 直接放弃
     * @return 是否真的标上了
     */
    public boolean labelLogin(Long memberId, String deviceId, long loginEpochSecond,
                              MemberRiskLabelEnum label, MemberRiskLabelSourceEnum source) {
        if (memberId == null || SolvelaStringUtil.isBlank(deviceId) || loginEpochSecond <= 0) {
            return false;
        }
        try {
            long tolerance = TOLERANCE.toSeconds();
            int marked = memberRiskEventDao.labelNearestLogin(memberId, deviceId,
                    loginEpochSecond - tolerance, loginEpochSecond + tolerance, loginEpochSecond,
                    label.getValue(), source.getValue());
            if (marked == 0) {
                log.info("【风控标注】没找到对应的流水, memberId: {}, deviceId: {}", memberId, deviceId);
            }
            return marked > 0;
        } catch (RuntimeException e) {
            // 🔴 标不上只是少一条分析数据，绝不能让「这不是我」这件事失败 ——
            //    用户真正要的是下线和冻结，那两件事必须成
            log.error("【风控标注】打标失败, memberId: {}, deviceId: {}", memberId, deviceId, e);
            return false;
        }
    }
}
