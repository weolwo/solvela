package solvela.member.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.api.MemberLoginType;

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

    /** 一张票上的全部内容 */
    public record Challenge(Long memberId, MemberLoginType loginType, String deviceId, String deviceType,
                            String identity) {
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
        String[] parts = value.split("\\" + SEP, 5);
        if (parts.length != 5) {
            return null;
        }
        try {
            return new Challenge(Long.valueOf(parts[0]), MemberLoginType.valueOf(parts[1]),
                    emptyToNull(parts[2]), emptyToNull(parts[3]), parts[4]);
        } catch (IllegalArgumentException e) {
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
