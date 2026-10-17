package solvela.auth.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 会员令牌：不透明随机串 + Redis。
 *
 * <h3>Redis 里有两个键</h3>
 * <ul>
 *   <li>{@code app:auth:t:{摘要}} → <b>会话记录</b>（下面那个格式），TTL = 令牌有效期；</li>
 *   <li>{@code app:auth:m:{会员号}} → 该会员所有令牌摘要的集合，用来「全部下线」与超限挤出。</li>
 * </ul>
 *
 * <p>存的是<b>摘要</b>不是令牌本身：Redis 被 dump、被运维 {@code KEYS *} 打到终端时，
 * 那些串不能直接拿去登录。
 *
 * <h3>🔴 会话记录的格式，以及为什么必须兼容旧的</h3>
 * 2026-09-10 之前这个值是<b>光秃秃一个 memberId</b>。现在是
 * {@code memberId|sessionId|loginTime|deviceType|deviceId|ip|region}，
 * 为的是让用户能看到「我的登录设备」—— 光一个 memberId 渲染不出任何一行。
 *
 * <p>但线上此刻正躺着一批旧格式的令牌，TTL 长达 30 天。
 * {@link #resolve} 如果只认新格式，<b>发版当天所有已登录用户全部掉线</b>。
 * 所以解析对两种格式都认，旧格式在列表里显示成一条信息不全的会话 ——
 * 而不是消失（消失会让用户以为「有个设备我看不见」，那比信息不全更吓人）。
 * 这段兼容可以在 30 天之后删掉，那时最后一个旧令牌也过期了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberRedisTokenStore implements MemberTokenStore {

    private static final String TOKEN_PREFIX = "mb_";

    private static final String KEY_TOKEN = "app:auth:t:";

    private static final String KEY_MEMBER = "app:auth:m:";

    private static final int TOKEN_BYTES = 32;

    /** sessionId 的长度。够短好传，够长不会撞 */
    private static final int SESSION_ID_BYTES = 12;

    /** 会话记录的字段分隔符。{@code |} 不会出现在会员号、时间戳、设备号里 */
    private static final String SEP = "|";

    private static final int FIELD_COUNT = 7;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    private final MemberSessionProperties properties;

    @Override
    public MemberAccessToken issue(Long memberId, MemberSessionContext context) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String value = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String digest = digest(value);

        MemberSessionContext ctx = context == null ? MemberSessionContext.empty() : context;
        redis.opsForValue().set(KEY_TOKEN + digest, encode(memberId, newSessionId(), ctx),
                properties.tokenTtl());

        String memberKey = KEY_MEMBER + memberId;
        redis.opsForSet().add(memberKey, digest);
        redis.expire(memberKey, properties.tokenTtl());
        trimSessions(memberId, memberKey);
        return new MemberAccessToken(value, properties.tokenTtl());
    }

    @Override
    public Long resolve(String tokenValue) {
        if (tokenValue == null || !tokenValue.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String stored = redis.opsForValue().get(KEY_TOKEN + digest(tokenValue));
        if (stored == null) {
            return null;
        }
        Long memberId = memberIdOf(stored);
        if (memberId == null) {
            // 只可能是有人手改了 Redis，或 key 撞了别的系统。删掉，让用户重新登录
            log.warn("[Auth] 令牌记录读不出会员号，已清理：{}", stored);
            redis.delete(KEY_TOKEN + digest(tokenValue));
        }
        return memberId;
    }

    @Override
    public void revoke(String tokenValue) {
        if (tokenValue == null || !tokenValue.startsWith(TOKEN_PREFIX)) {
            return;
        }
        String digest = digest(tokenValue);
        String stored = redis.opsForValue().get(KEY_TOKEN + digest);
        redis.delete(KEY_TOKEN + digest);
        Long memberId = stored == null ? null : memberIdOf(stored);
        if (memberId != null) {
            redis.opsForSet().remove(KEY_MEMBER + memberId, digest);
        }
    }

    @Override
    public int revokeAll(Long memberId) {
        String memberKey = KEY_MEMBER + memberId;
        Set<String> digests = redis.opsForSet().members(memberKey);
        if (digests == null || digests.isEmpty()) {
            redis.delete(memberKey);
            return 0;
        }
        redis.delete(digests.stream().map(d -> KEY_TOKEN + d).toList());
        redis.delete(memberKey);
        return digests.size();
    }

    // ------------------------------------------------------------------ 登录设备列表

    @Override
    public List<MemberSession> listSessions(Long memberId, String currentTokenValue) {
        String currentDigest = currentTokenValue == null ? null : digest(currentTokenValue);
        List<MemberSession> sessions = new ArrayList<>();
        for (Entry entry : liveEntries(memberId)) {
            sessions.add(entry.toSession(entry.digest().equals(currentDigest)));
        }
        /*
         * 当前这一台排最前，其余按登录时间倒序。
         * 用户打开这个页面第一眼要确认的是「哪个是我」—— 找不到自己的话，
         * 他不敢点任何一个下线按钮。
         */
        sessions.sort(Comparator.comparing(MemberSession::current).reversed()
                .thenComparing(Comparator.comparingLong(MemberSession::loginTime).reversed()));
        return sessions;
    }

    @Override
    public boolean revokeSession(Long memberId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        for (Entry entry : liveEntries(memberId)) {
            /*
             * 🔴 只在【这个会员自己的】集合里找。
             * 拿 sessionId 全局去查的话，任何人猜中/拿到一个别人的 sessionId
             * 就能把别人踢下线 —— 而 sessionId 会出现在响应体里，不是秘密。
             */
            if (sessionId.equals(entry.sessionId())) {
                redis.delete(KEY_TOKEN + entry.digest());
                redis.opsForSet().remove(KEY_MEMBER + memberId, entry.digest());
                log.info("[Auth] 会员 {} 主动下线了一个会话", memberId);
                return true;
            }
        }
        return false;
    }

    @Override
    public int revokeOthers(Long memberId, String currentTokenValue) {
        String currentDigest = currentTokenValue == null ? null : digest(currentTokenValue);
        int revoked = 0;
        for (Entry entry : liveEntries(memberId)) {
            if (entry.digest().equals(currentDigest)) {
                // 留下自己这一个：全部踢掉（含自己）会让人犹豫要不要点
                continue;
            }
            redis.delete(KEY_TOKEN + entry.digest());
            redis.opsForSet().remove(KEY_MEMBER + memberId, entry.digest());
            revoked++;
        }
        log.info("[Auth] 会员 {} 下线了其它 {} 个会话", memberId, revoked);
        return revoked;
    }

    /**
     * 读出这个会员当前还活着的所有会话，<b>顺手清掉集合里的残留</b>。
     *
     * <p>集合成员没有自己的 TTL：令牌自然过期后，摘要还留在集合里。
     * 不清的话这个集合只增不减，最后「全部下线」会去删一批根本不存在的键，
     * 而「我的登录设备」会显示出一堆早就失效的设备。
     */
    private List<Entry> liveEntries(Long memberId) {
        String memberKey = KEY_MEMBER + memberId;
        Set<String> digests = redis.opsForSet().members(memberKey);
        if (digests == null || digests.isEmpty()) {
            return List.of();
        }
        List<Entry> alive = new ArrayList<>();
        Set<String> dead = new HashSet<>();
        for (String d : digests) {
            String stored = redis.opsForValue().get(KEY_TOKEN + d);
            if (stored == null) {
                dead.add(d);
                continue;
            }
            Entry entry = decode(d, stored);
            if (entry == null) {
                dead.add(d);
            } else {
                alive.add(entry);
            }
        }
        if (!dead.isEmpty()) {
            redis.opsForSet().remove(memberKey, dead.toArray());
        }
        return alive;
    }

    // ------------------------------------------------------------------ 会话数上限

    private void trimSessions(Long memberId, String memberKey) {
        Set<String> digests = redis.opsForSet().members(memberKey);
        if (digests == null || digests.size() <= properties.maxSessions()) {
            return;
        }
        record Session(String digest, long ttl) {
        }
        List<Session> alive = new ArrayList<>();
        Set<String> dead = new HashSet<>();
        for (String d : digests) {
            Long ttl = redis.getExpire(KEY_TOKEN + d);
            // -2 = key 不存在（令牌已自然过期），集合里的这条是残留，顺手清掉
            if (ttl == null || ttl < 0) {
                dead.add(d);
            } else {
                alive.add(new Session(d, ttl));
            }
        }
        if (!dead.isEmpty()) {
            redis.opsForSet().remove(memberKey, dead.toArray());
        }
        int excess = alive.size() - properties.maxSessions();
        if (excess <= 0) {
            return;
        }
        alive.sort(Comparator.comparingLong(Session::ttl));
        for (int i = 0; i < excess; i++) {
            String d = alive.get(i).digest();
            redis.delete(KEY_TOKEN + d);
            redis.opsForSet().remove(memberKey, d);
        }
        log.info("[Auth] 会员 {} 会话数超限，挤掉最旧的 {} 个", memberId, excess);
    }

    // ------------------------------------------------------------------ 编解码

    /** Redis 里那一行。{@code digest} 不在值里，它是键。 */
    private record Entry(String digest, Long memberId, String sessionId, long loginTime,
                         String deviceType, String deviceId, String ip, String region) {

        MemberSession toSession(boolean current) {
            return new MemberSession(sessionId, deviceType, deviceId, ip, region, loginTime, current);
        }
    }

    private static String encode(Long memberId, String sessionId, MemberSessionContext ctx) {
        return String.join(SEP,
                String.valueOf(memberId),
                sessionId,
                String.valueOf(System.currentTimeMillis()),
                nullToEmpty(ctx.deviceType()),
                nullToEmpty(ctx.deviceId()),
                nullToEmpty(ctx.ip()),
                // region 放最后：它是唯一可能含奇怪字符的一项，
                // 万一将来某个地名带了分隔符，只会污染它自己
                nullToEmpty(ctx.region()));
    }

    /**
     * 解析会话记录，<b>两种格式都认</b>。
     *
     * <p>旧格式（2026-09-10 之前）是光秃秃一个 memberId。只认新格式的话，
     * 发版当天所有已登录用户全部掉线 —— 而令牌有效期是 30 天。
     */
    private static Entry decode(String digest, String stored) {
        String[] parts = stored.split("\\" + SEP, -1);
        if (parts.length == 1) {
            Long memberId = parseLong(parts[0]);
            if (memberId == null) {
                return null;
            }
            /*
             * 旧令牌：除了会员号什么都不知道。
             * 仍然要列出来 —— 让它消失的话，用户会以为「有个设备我看不见」，
             * 那比信息不全更吓人。sessionId 由摘要前 16 位派生，够稳定也够用来下线。
             */
            return new Entry(digest, memberId, "legacy-" + digest.substring(0, 16), 0L,
                    null, null, null, null);
        }
        if (parts.length != FIELD_COUNT) {
            return null;
        }
        Long memberId = parseLong(parts[0]);
        if (memberId == null) {
            return null;
        }
        Long loginTime = parseLong(parts[2]);
        return new Entry(digest, memberId, parts[1], loginTime == null ? 0L : loginTime,
                emptyToNull(parts[3]), emptyToNull(parts[4]), emptyToNull(parts[5]),
                emptyToNull(parts[6]));
    }

    /** 只取会员号 —— 认证路径上每个请求都会走，不需要解析整条记录。 */
    private static Long memberIdOf(String stored) {
        int sep = stored.indexOf(SEP);
        return parseLong(sep < 0 ? stored : stored.substring(0, sep));
    }

    private static String newSessionId() {
        byte[] raw = new byte[SESSION_ID_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static Long parseLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String digest(String tokenValue) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须实现的算法，走到这里说明 JRE 被裁剪过
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", e);
        }
    }
}
