package solvela.member.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.api.MemberLoginType;
import solvela.member.risk.RiskEventRef;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 登录二次验证的凭票：密码验对、设备在观察档时发一张，之后发码与验码都只认它。
 *
 * <h3>票上记着什么</h3>
 * 会员号、登录方式、登录用的身份（邮箱 / 手机号）、设备号、设备端。
 * 发码发到哪、验码验哪个、登录成功记在哪台设备上 —— 全部由票决定，<b>不信客户端当时传的任何东西</b>。
 *
 * <h3>与会员令牌同一套存法</h3>
 * 票原文是 32 字节随机数，Redis 里只存它的 SHA-256 —— Redis 被 dump 时拿不到能用的票。
 * 5 分钟有效：够收一封邮件，不够攻击者慢慢琢磨。
 *
 * @Date 2026-09-27
 */
@Component
@RequiredArgsConstructor
public class LoginChallengeStore {

    private static final String KEY = "login:challenge:";

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final String PREFIX = "lc_";

    private static final String SEP = "|";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    private final RedisService redisService;

    /**
     * 一张票上的全部内容。
     *
     * @param eventRef 签这张票的那次登录在风控流水里的位置（id + 时间）。验码那一行靠它填
     *                 {@code ref_event_id} —— 不然「签票」和「验码」在流水里是两条互不相干的记录，
     *                 而误伤分析恰恰要顺着这条线看「要了码的人后来验过没有」。可为 null（流水写失败时）
     * @param channel  码走哪条通道，由核身路由在签票时选定（{@code VerifyRouter}，方案 §3.7）。
     *                 <p>🔴 <b>写在票上，不在发码那一步按登录方式再算一次</b>：
     *                 换通道（手机号登录、码发邮箱）和退回（短信没接、只能走邮箱）都只有路由知道，
     *                 发码时重算等于把那套判断复制一份 —— 而两份一旦漂移，表现是
     *                 「告诉用户发到了邮箱，码却发去了短信」。
     */
    public record Challenge(Long memberId, MemberLoginType loginType, String deviceId, String deviceType,
                            RiskEventRef eventRef, MemberVerifyChannelEnum channel, String identity) {
    }

    /** 签一张票，返回原文 */
    public String issue(Challenge challenge) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String ticket = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        // identity 放最后：它是唯一可能含奇怪字符的一项，按 limit 切分时只会污染它自己
        String value = String.join(SEP,
                String.valueOf(challenge.memberId()),
                challenge.loginType().name(),
                nullToEmpty(challenge.deviceId()),
                nullToEmpty(challenge.deviceType()),
                refToText(challenge.eventRef()),
                challenge.channel().name(),
                challenge.identity());
        redis.opsForValue().set(key(ticket), value, TTL);
        return ticket;
    }

    /** 读票，不消费。无效（不存在、过期、格式坏）返回 null */
    public Challenge find(String ticket) {
        if (ticket == null || !ticket.startsWith(PREFIX)) {
            return null;
        }
        return decode(redis.opsForValue().get(key(ticket)));
    }

    /** 作废一张票（验码成功，或码被错到作废） */
    public void discard(String ticket) {
        if (ticket != null && ticket.startsWith(PREFIX)) {
            redis.delete(key(ticket));
        }
    }

    private static Challenge decode(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\" + SEP, 7);
        if (parts.length != 7) {
            return null;
        }
        try {
            return new Challenge(Long.valueOf(parts[0]), MemberLoginType.valueOf(parts[1]),
                    emptyToNull(parts[2]), emptyToNull(parts[3]), textToRef(parts[4]),
                    MemberVerifyChannelEnum.valueOf(parts[5]), parts[6]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code 事件id,秒级时间戳}；没有引用时是空串 */
    private static String refToText(RiskEventRef ref) {
        if (ref == null || ref.eventId() == null || ref.createTime() == null) {
            return "";
        }
        return ref.eventId() + "," + ref.createTime().toEpochSecond(java.time.ZoneOffset.UTC);
    }

    /**
     * 解不出来就当没有引用。
     *
     * <p>🔴 绝不因此让整张票失效：票的作用是「这个人已经验过密码了」，而流水引用只是为了
     * 事后分析能串起来 —— 为了一个分析字段把人挡在登录外面是本末倒置。
     */
    private static RiskEventRef textToRef(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String[] parts = text.split(",", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new RiskEventRef(Long.valueOf(parts[0]),
                    java.time.LocalDateTime.ofEpochSecond(Long.parseLong(parts[1]), 0, java.time.ZoneOffset.UTC));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String key(String ticket) {
        return redisService.generateRedisKey(KEY, digest(ticket));
    }

    private static String digest(String ticket) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", e);
        }
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String emptyToNull(String v) {
        return SolvelaStringUtil.isEmpty(v) ? null : v;
    }
}
