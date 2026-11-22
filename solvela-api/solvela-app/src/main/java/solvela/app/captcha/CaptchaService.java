package solvela.app.captcha;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
 * <h3>指标：滑块是不是已经被破了</h3>
 * 自建滑块挡不住专门针对它写的脚本（见 {@link SliderCaptchaGenerator}），所以要能<b>看出来</b>它什么时候被破，
 * 而不是等撞库成功了才知道。三条计数，面板在 {@code deploy/monitoring/dashboards/solvela-app.json} 的「滑块验证码」一行：
 * <ul>
 *   <li>{@code solvela_captcha_total{outcome}}：出图与判题结果，见 {@link Outcome}。
 *       「通过 / 出图」突然升高 = 脚本开始解得出图了；</li>
 *   <li>{@code solvela_captcha_track_rejected_total{reason}}：轨迹被拒的原因，见 {@link TrackChecker.Reject}。
 *       位置对、轨迹不像人 —— 有人解出了图、还没学会装人手；</li>
 *   <li>{@code solvela_captcha_ticket_total{result}}：受保护接口核销通行票的结果，见 {@link TicketResult}。
 *       {@code INVALID} 持续出现 = 有人在重放或伪造通行票。</li>
 * </ul>
 * 「过了滑块但密码错」不在这里数：它要看登录结果，网关看不到。查 {@code t_member_risk_event} 的 {@code captcha_passed} 与处置结果。
 *
 * <p>⚠️ 三个枚举的名字就是标签值，面板和告警按名字查；改名等于把序列断掉。
 * 所有取值在构造时就注册成 0：否则从没发生过的结果在 Prometheus 里是「没有数据」而不是 0，
 * 「通过 / 出图」这种比值在出事之前一直算不出来。
 *
 * @Date 2026-09-27
 */
@Slf4j
@Service
public class CaptchaService {

    /** 通行票放在这个请求头里 */
    public static final String HEADER = "X-Captcha-Token";

    static final String METER_CAPTCHA = "solvela.captcha";

    static final String METER_TRACK_REJECTED = "solvela.captcha.track.rejected";

    static final String METER_TICKET = "solvela.captcha.ticket";

    private static final String KEY_CHALLENGE = "app:captcha:c:";

    private static final String KEY_PASS = "app:captcha:p:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DefaultRedisScript<String> TAKE_ONCE = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) if v then redis.call('DEL', KEYS[1]) end return v", String.class);

    private final SliderCaptchaGenerator generator;

    private final StringRedisTemplate redis;

    private final CaptchaProperties properties;

    private final Map<Outcome, Counter> outcomes;

    private final Map<TrackChecker.Reject, Counter> trackRejects;

    private final Map<TicketResult, Counter> tickets;

    /** 出图与判题的结果。判题的几种失败对客户端是同一个 {@code CAPTCHA_FAILED}，只在这里分开数 */
    public enum Outcome {
        /** 出了一张图 */
        ISSUED,
        /** 判题通过，发了通行票 */
        PASSED,
        /** 请求里缺 captchaId 或 x —— 正常前端不会这样，多半是脚本 */
        MALFORMED,
        /** 图不存在：过期、已经被验过一次，或者 id 是编的 */
        CHALLENGE_GONE,
        /** 位置不对 */
        WRONG_POSITION,
        /** 位置对，轨迹不像人。原因另见 {@code solvela_captcha_track_rejected_total} */
        TRACK_REJECTED
    }

    /** 受保护接口核销通行票的结果 */
    public enum TicketResult {
        /** 带了有效票，已核销 */
        CONSUMED,
        /**
         * 没带票。发码时是「还没拖」的正常第一步；登录对每个请求都核销（见 {@link #consumeIfPresent}），
         * 所以不需要滑块的登录也会落在这里 —— 它本身不是异常信号
         */
        MISSING,
        /** 带了票但无效：过期、已用过，或者是编的 */
        INVALID
    }

    public CaptchaService(SliderCaptchaGenerator generator, StringRedisTemplate redis, CaptchaProperties properties,
                          MeterRegistry meters) {
        this.generator = generator;
        this.redis = redis;
        this.properties = properties;
        this.outcomes = counters(meters, METER_CAPTCHA, "outcome", Outcome.class);
        this.trackRejects = counters(meters, METER_TRACK_REJECTED, "reason", TrackChecker.Reject.class);
        this.tickets = counters(meters, METER_TICKET, "result", TicketResult.class);
    }

    /** 下发给客户端的题面。没有答案 */
    public record Challenge(String captchaId, String background, String piece, int pieceY, int width, int height,
                            int pieceSize) {
    }

    /**
     * 出一张图。
     *
     * <p>出图要吃 CPU，频率由网关限流的 {@code CAPTCHA} 场景管（同 IP、同设备，阶梯处罚）——
     * 这里原来那把「同 IP 每分钟 20 张」2026-10-17 移交了过去：同一件事只在一个地方数。
     */
    public Challenge create() {
        SliderCaptchaGenerator.Puzzle puzzle = generator.generate();
        String id = randomToken();
        redis.opsForValue().set(KEY_CHALLENGE + id, String.valueOf(puzzle.answerX()), properties.challengeTtl());
        outcomes.get(Outcome.ISSUED).increment();
        return new Challenge(id, puzzle.background(), puzzle.piece(), puzzle.pieceY(),
                SliderCaptchaGenerator.WIDTH, SliderCaptchaGenerator.HEIGHT, SliderCaptchaGenerator.PIECE);
    }

    /**
     * 判题。拖对了返回一张通行票；拖错、轨迹不像人、图过期、图已被验过都返回 null。
     * 不区分这几种：区分等于告诉脚本「这张图还能再试」或「是轨迹露馅了」。
     *
     * @param track 拖动轨迹，见 {@link TrackChecker}
     */
    public String verify(String captchaId, Integer x, int[][] track) {
        if (captchaId == null || x == null) {
            outcomes.get(Outcome.MALFORMED).increment();
            return null;
        }
        String answer = takeOnce(KEY_CHALLENGE + captchaId);
        if (answer == null) {
            outcomes.get(Outcome.CHALLENGE_GONE).increment();
            return null;
        }
        if (Math.abs(Integer.parseInt(answer) - x) > properties.tolerance()) {
            outcomes.get(Outcome.WRONG_POSITION).increment();
            return null;
        }
        if (properties.trackCheck()) {
            TrackChecker.Reject reason = TrackChecker.reject(track, x, properties.tolerance());
            if (reason != null) {
                log.info("滑块轨迹不像人，拒绝: reason={}, points={}", reason, track == null ? 0 : track.length);
                outcomes.get(Outcome.TRACK_REJECTED).increment();
                trackRejects.get(reason).increment();
                return null;
            }
        }
        String pass = randomToken();
        redis.opsForValue().set(KEY_PASS + pass, "1", properties.passTtl());
        outcomes.get(Outcome.PASSED).increment();
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
        if (!consumeIfPresent(request)) {
            throw new ApiException(ApiErrors.CAPTCHA_REQUIRED);
        }
    }

    /**
     * 带了有效通行票就核销掉并返回 true；没带或票无效返回 false，<b>不抛异常</b>。
     *
     * <h3>给「由域来判要不要滑块」的接口用</h3>
     * 登录走这一条：网关不再无条件先拦（那样「全局滑块开关」「熟悉网络免滑块」就没有地方落 ——
     * 它们要看规则表和账号画像，都在域里）。网关只负责把票核销掉，把结论交给域。
     *
     * <p>🔴 核销仍然<b>只能发生在这里</b>：它是一次 Redis 的「取出即删」，
     * 「一张票只放行一次请求」这件事只有一个地方做得到。域拿到的只是一个布尔值。
     *
     * <p>⚠️ 票是无条件核销的 —— 哪怕这次登录其实不需要滑块。否则客户端带着同一张票
     * 可以反复重试，而那正是「一张票只放行一次」要堵的。
     */
    public boolean consumeIfPresent(HttpServletRequest request) {
        String pass = request.getHeader(HEADER);
        if (pass == null || pass.isBlank()) {
            tickets.get(TicketResult.MISSING).increment();
            return false;
        }
        boolean consumed = takeOnce(KEY_PASS + pass.trim()) != null;
        tickets.get(consumed ? TicketResult.CONSUMED : TicketResult.INVALID).increment();
        return consumed;
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

    /** 枚举的每个取值注册一条计数，初值 0 */
    private static <E extends Enum<E>> Map<E, Counter> counters(MeterRegistry meters, String name, String tag,
                                                                 Class<E> values) {
        Map<E, Counter> map = new EnumMap<>(values);
        for (E value : values.getEnumConstants()) {
            map.put(value, meters.counter(name, tag, value.name()));
        }
        return map;
    }

    private static String randomToken() {
        byte[] raw = new byte[18];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
