package solvela.member.stepup;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;

import java.time.Duration;

/**
 * 「在哪些设备上验证过」与「信任从什么时候起算」。
 *
 * <h3>为什么在 Redis，不建表</h3>
 * {@code t_device} 刻意不带 member_id、也刻意不建会员-设备关系表 ——
 * 「这台设备登过哪些号」从登录日志聚合。本类存的是另一种东西：<b>一次验证通过的决定</b>，
 * 登录日志里没有它。
 *
 * <p>它丢了的后果是<b>往安全的方向偏</b>：用户在这台设备上再验一次码，仅此而已。
 * 与 {@code DeviceGuard} 的计数放 Redis 是同一个判据 —— 丢了不要紧的东西不值一张表。
 *
 * <h3>两个键</h3>
 * <ul>
 *   <li>{@code mbr:trust:v:{会员号}} → 验证通过的设备号集合，TTL = {@code verifiedTtl}，每次新增续期；</li>
 *   <li>{@code mbr:trust:since:{会员号}} → 信任起算的 Unix 秒。撤销时写成「现在」，
 *       此前的登录历史一律不再算数。TTL 365 天：它一过期，撤销之前的登录历史就重新算数 ——
 *       但那只影响一年以前的登录，而不设 TTL 的代价是每冻结一个会员、每重置一次密码
 *       都永久多一个键，只增不减。</li>
 * </ul>
 *
 * <p>起算点存的是 Unix 秒而不是日期字符串：它要拿去和 {@code t_member_login_log.create_time}
 * 比，而那一列是数据库按会话时区写的。在 SQL 里用 {@code FROM_UNIXTIME} 还原，
 * 两边都落在同一个会话时区里 —— Java 这一侧不做任何时区换算（交接文档铁律 10）。
 *
 * @Date 2026-09-26
 */
@Component
@RequiredArgsConstructor
public class DeviceTrustStore {

    private static final String KEY_VERIFIED = "mbr:trust:v:";

    private static final String KEY_SINCE = "mbr:trust:since:";

    /** 起算点的保留期。理由见类注释 */
    private static final Duration SINCE_TTL = Duration.ofDays(365);

    private final StringRedisTemplate redis;

    private final RedisService redisService;

    private final MemberStepUpProperties properties;

    public boolean isVerified(Long memberId, String deviceId) {
        if (memberId == null || SolvelaStringUtil.isBlank(deviceId)) {
            return false;
        }
        return Boolean.TRUE.equals(redis.opsForSet().isMember(verifiedKey(memberId), deviceId));
    }

    public void markVerified(Long memberId, String deviceId) {
        String key = verifiedKey(memberId);
        redis.opsForSet().add(key, deviceId);
        // 每次都续期：集合是逐台长起来的，只在首次设 TTL 的话，
        // 第一台设备验证满 180 天时整个集合一起消失，后来验证的那几台也跟着失效
        redis.expire(key, properties.verifiedTtl());
    }

    /**
     * 信任起算点，Unix 秒。从没撤销过返回 0（等于不限制）。
     */
    public long trustSince(Long memberId) {
        String raw = redis.opsForValue().get(sinceKey(memberId));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // 只可能是有人手改了 Redis。当成「刚刚撤销过」—— 往严的方向偏，
            // 而不是当成 0 把所有历史信任都放回来
            return System.currentTimeMillis() / 1000;
        }
    }

    /**
     * 撤销全部信任：验证记录清空，登录历史从此刻起重新累计。
     *
     * @param keepDeviceId 撤销后仍保留信任的那台设备，可为 null。
     *                     🔴 由调用方保证它<b>本来就受信任</b>，本类不做这个判断
     */
    public void revoke(Long memberId, String keepDeviceId) {
        redis.opsForValue().set(sinceKey(memberId), String.valueOf(System.currentTimeMillis() / 1000), SINCE_TTL);
        redis.delete(verifiedKey(memberId));
        if (!SolvelaStringUtil.isBlank(keepDeviceId)) {
            markVerified(memberId, keepDeviceId);
        }
    }

    private String verifiedKey(Long memberId) {
        return redisService.generateRedisKey(KEY_VERIFIED, String.valueOf(memberId));
    }

    private String sinceKey(Long memberId) {
        return redisService.generateRedisKey(KEY_SINCE, String.valueOf(memberId));
    }
}
