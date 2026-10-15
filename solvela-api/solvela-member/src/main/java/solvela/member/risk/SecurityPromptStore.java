package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 待确认的「刚才是你吗」。一个会员一个 Redis 哈希，字段是确认单号。
 *
 * <h3>为什么不落库</h3>
 * 它是<b>一次性的提问</b>：答完就没了，7 天没人答也就作废了。落库换来的只有「历史上问过什么」，
 * 而那件事决策流水已经答得更全（那一行里有设备、IP、结论）。
 * 代价是 Redis 丢了这批提问就没了 —— 而那只意味着少问一次，新设备登录提醒（邮件 + 站内信）还在。
 *
 * <h3>🔴 为什么是哈希，不是一个装着 JSON 数组的 key</h3>
 * 数组要「读出来 → 改 → 写回去」，两次登录同时发生时后写的会覆盖前一个 —— <b>丢掉一次提问</b>，
 * 而丢掉的恰好是并发登录（最可疑）的那一次。{@code HSET} / {@code HDEL} 每个字段各自原子。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityPromptStore {

    private static final String KEY = "mbr:sec:prompt:";

    /** 7 天没人答就作废。再长也没意义：那时主人早就从邮件里知道了 */
    private static final Duration TTL = Duration.ofDays(7);

    /** 一个会员同时最多挂几条。被撞库刷出几十条提问，等于把弹窗变成骚扰 */
    private static final int MAX_PENDING = 5;

    private static final String PREFIX = "sp_";

    private static final String SEP = "|";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    private final RedisService redisService;

    /**
     * 一条提问。
     *
     * @param deviceId         被问的那次登录用的设备号。<b>列表要靠它把「本机」滤掉</b> ——
     *                         🔴 在新设备本身上问「是你吗」，盗号者会点「是我」，
     *                         而那条「确认本人」会污染整个误伤率（方案 §8.1）
     * @param loginEpochSecond 那次登录的时间，答完要靠它给决策流水打标
     */
    public record Prompt(String promptId, String deviceId, String deviceType,
                         String location, long loginEpochSecond) {
    }

    /** 记一条待确认，返回单号。满了就不再记 —— 旧的那几条更值得答 */
    public String add(Long memberId, String deviceId, String deviceType, String location,
                      long loginEpochSecond) {
        if (memberId == null || SolvelaStringUtil.isBlank(deviceId)) {
            return null;
        }
        try {
            String key = key(memberId);
            if (redis.opsForHash().size(key) >= MAX_PENDING) {
                log.info("【安全确认】待确认已满，本次不再记, memberId: {}", memberId);
                return null;
            }
            String promptId = PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(randomBytes());
            // location 放最后：它是唯一可能含奇怪字符的一项，按 limit 切分时只会污染它自己
            String value = String.join(SEP, deviceId, nullToEmpty(deviceType),
                    String.valueOf(loginEpochSecond), nullToEmpty(location));
            redis.opsForHash().put(key, promptId, value);
            // 每次新增都续期：最后一条的 7 天内都还答得上
            redis.expire(key, TTL);
            return promptId;
        } catch (RuntimeException e) {
            // 记不上只是少问一次，绝不能影响登录
            log.error("【安全确认】记录待确认失败, memberId: {}", memberId, e);
            return null;
        }
    }

    /** 这个会员所有待确认的。取不到时返回空列表，不抛 */
    public List<Prompt> list(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        try {
            Map<Object, Object> entries = redis.opsForHash().entries(key(memberId));
            List<Prompt> prompts = new ArrayList<>(entries.size());
            entries.forEach((field, value) -> {
                Prompt prompt = decode(String.valueOf(field), String.valueOf(value));
                if (prompt != null) {
                    prompts.add(prompt);
                }
            });
            // 新的排前面：一个人同时有几条时，最该先答的是刚刚那次
            prompts.sort((a, b) -> Long.compare(b.loginEpochSecond(), a.loginEpochSecond()));
            return List.copyOf(prompts);
        } catch (RuntimeException e) {
            // 🔴 查不到就不弹。绝不能反过来「查失败就拦住用户」—— 那会让一次 Redis 抖动变成整个 App 打不开
            log.error("【安全确认】读待确认失败, memberId: {}", memberId, e);
            return List.of();
        }
    }

    /**
     * 取出并删掉一条。返回 null 表示这条不存在（答过了、过期了、单号是编的）。
     *
     * <p>🔴 <b>先删再处理</b>：删成功才算「这一答是有效的」。
     * 反过来的话，连点两次「不是我」会走两遍冻结 + 打标。
     */
    public Prompt take(Long memberId, String promptId) {
        if (memberId == null || promptId == null || !promptId.startsWith(PREFIX)) {
            return null;
        }
        String key = key(memberId);
        Object value = redis.opsForHash().get(key, promptId);
        if (value == null) {
            return null;
        }
        Long removed = redis.opsForHash().delete(key, promptId);
        if (removed == null || removed == 0) {
            // 另一个请求刚好抢先删掉了 —— 这一答当作无效，不重复执行副作用
            return null;
        }
        return decode(promptId, String.valueOf(value));
    }

    private static Prompt decode(String promptId, String value) {
        String[] parts = value.split("\\" + SEP, 4);
        if (parts.length != 4) {
            return null;
        }
        try {
            return new Prompt(promptId, parts[0], emptyToNull(parts[1]),
                    emptyToNull(parts[3]), Long.parseLong(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String key(Long memberId) {
        return redisService.generateRedisKey(KEY, String.valueOf(memberId));
    }

    private static byte[] randomBytes() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return raw;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String emptyToNull(String v) {
        return SolvelaStringUtil.isEmpty(v) ? null : v;
    }
}
