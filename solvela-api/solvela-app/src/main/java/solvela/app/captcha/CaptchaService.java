package solvela.app.captcha;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * 滑块验证码：出题、判题、签发与消费通行票。全部在网关，状态在 Redis。
 *
 * <h3>两段式</h3>
 * <pre>
 *   POST /captcha          → 一张图（答案 x 只存在 Redis）
 *   POST /captcha/verify   → 拖对了发一张通行票
 *   受保护的接口           → 请求头 X-Captcha-Token 带着通行票，用一次就作废
 * </pre>
 * 拆成两段而不是「受保护接口直接收 x」：后者要给每个受保护接口的请求体加字段，
 * 而通行票走请求头，受保护接口只需要一行 {@link #require}，前端也能统一在 http 层重试。
 *
 * <h3>每张图只能验一次</h3>
 * 判题时把答案连同 key 一起原子地取走删掉（见 {@link #takeOnce}）。不这样的话，一张图可以被脚本从 0 到 300 逐个试。
 *
 * @Date 2026-09-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    /** 通行票放在这个请求头里 */
    public static final String HEADER = "X-Captcha-Token";

    private static final String KEY_CHALLENGE = "app:captcha:c:";

    private static final String KEY_PASS = "app:captcha:p:";

    private static final String KEY_CREATE = "app:captcha:ip:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DefaultRedisScript<String> TAKE_ONCE = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) if v then redis.call('DEL', KEYS[1]) end return v", String.class);

    private final SliderCaptchaGenerator generator;

    private final StringRedisTemplate redis;

    private final CaptchaProperties properties;

    /** 下发给客户端的题面。没有答案 */
    public record Challenge(String captchaId, String background, String piece, int pieceY, int width, int height,
                            int pieceSize) {
    }

    /** 出一张图。同一 IP 每分钟超限返回 429 */
    public Challenge create(String ip) {
        if (ip != null) {
            String key = KEY_CREATE + ip;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > properties.maxCreatePerIpPerMinute()) {
                throw new ApiException(ApiErrors.OPERATION_LIMITED, "操作过于频繁，请稍后再试");
            }
        }
        SliderCaptchaGenerator.Puzzle puzzle = generator.generate();
        String id = randomToken();
        redis.opsForValue().set(KEY_CHALLENGE + id, String.valueOf(puzzle.answerX()), properties.challengeTtl());
        return new Challenge(id, puzzle.background(), puzzle.piece(), puzzle.pieceY(),
                SliderCaptchaGenerator.WIDTH, SliderCaptchaGenerator.HEIGHT, SliderCaptchaGenerator.PIECE);
    }

    /**
     * 判题。拖对了返回一张通行票；拖错、图过期、图已被验过都返回 null。
     * 不区分这三种：区分等于告诉脚本「这张图还能再试」。
     */
    public String verify(String captchaId, Integer x) {
        if (captchaId == null || x == null) {
            return null;
        }
        String answer = takeOnce(KEY_CHALLENGE + captchaId);
        if (answer == null) {
            return null;
        }
        if (Math.abs(Integer.parseInt(answer) - x) > properties.tolerance()) {
            return null;
        }
        String pass = randomToken();
        redis.opsForValue().set(KEY_PASS + pass, "1", properties.passTtl());
        return pass;
    }

    /**
     * 受保护的接口调这一行：请求头里没有有效通行票就抛 {@code CAPTCHA_REQUIRED}，
     * 有就把它作废（一张票只放行一次请求）。
     *
     * @param enabled 这个场景开没开（{@link CaptchaProperties} 里的场景开关）
     */
    public void require(HttpServletRequest request, boolean enabled) {
        if (!enabled) {
            return;
        }
        String pass = request.getHeader(HEADER);
        if (pass == null || pass.isBlank() || takeOnce(KEY_PASS + pass.trim()) == null) {
            throw new ApiException(ApiErrors.CAPTCHA_REQUIRED);
        }
    }

    /**
     * 原子地「取出并删除」—— 同一张图、同一张通行票在并发下也只能被用一次。
     *
     * <p>🔴 不用 {@code GETDEL}：那是 Redis 6.2 才有的命令，本地开发用的 Redis 不认（报 unknown command），
     * 生产是 8.x。「只在某个环境坏」正是最难查的一类问题，所以用一段两行的 Lua，任何版本都认。
     * 先 GET 再 DEL 分两次调用则不行：两个并发请求会同时 GET 到同一个值。
     */
    private String takeOnce(String key) {
        return redis.execute(TAKE_ONCE, List.of(key));
    }

    private static String randomToken() {
        byte[] raw = new byte[18];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
