package solvela.auth.device;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 设备令牌的签发与验签。<b>自包含，不查任何存储。</b>
 *
 * <pre>
 *   dv_1.{base64url("deviceId|deviceType|签发秒")}.{base64url(HMAC-SHA256 前 16 字节)}
 *   │  │  └── 载荷，明文可读（里面没有秘密）        └── 签名，覆盖上面两段
 *   │  └── 密钥版本，参与签名，改不动
 *   └── 前缀。与会员令牌的 mb_ 对齐：看一眼就知道是哪一类凭证，
 *       「把设备令牌当会员令牌用」这种事在第一步就失败
 * </pre>
 *
 * <h3>为什么不像会员令牌那样进 Redis</h3>
 * 两者的验证频率不是一个量级：{@link solvela.auth.member.MemberTokenStore} 只在
 * <b>已登录</b>请求上解析，而设备令牌要在<b>每一个</b>请求上验 —— 包括注册、登录、活动页
 * 这些匿名请求，而防刷要防的恰恰是它们。给它也加一次 Redis GET，等于把 Redis 的读压力翻数倍。
 *
 * <p>换来的「即时吊销」在这里也不值那个价：吊销一个会员会话是安全事件（号被盗了，
 * 现在就要断），封禁一台设备是<b>风控处置</b>，晚几秒没有任何区别。
 * 所以封禁由处置那一侧单独管（{@code DeviceDispositionService}，状态在 {@code t_device.status}），而不是让每个请求都去查一次。
 *
 * <h3>🔴 这个令牌可以被复制，而这是设计意图</h3>
 * 一个签发好的令牌完全可以被贴进十万个脚本里用 —— 自包含令牌的固有代价，
 * 密码学上解决不了。<b>但它会自己暴露</b>：同一个 deviceId 出现在几百个 IP 上、
 * 登录频次远超单人可能，设备维度的计数当场命中。
 *
 * <p>换句话说，复制令牌把「分散的、难归因的攻击」变成了「集中的、一眼可见的攻击」。
 * 所以别指望本类拦住谁 —— 它的作用是<b>让 deviceId 这个维度变得可信到值得拿来计数</b>。
 * 客户端自报 deviceId 的方案之所以没用，正是因为脚本每次换一个 UUID，计数永远是 1。
 *
 * <h3>签的是「线上的那串字节」，不是「字段拼起来的字符串」</h3>
 * 签名覆盖的是 {@code 版本 + "." + 载荷base64}，也就是令牌里<b>逐字节出现</b>的内容。
 * 不是先把字段拼成 {@code "1|abc|APP|123"} 再签 —— 那种做法要求签发与验签两侧的
 * 拼接、编码、转义规则完全一致，而它们不一致的那天不会报错，只会「有些令牌验不过」。
 * 签线上字节没有这个失配面：验签方连字段都不用解就能先验完。
 */
@Slf4j
@Component
public class DeviceTokenCodec {

    /** 令牌前缀。与 {@code MemberRedisTokenStore} 的 {@code mb_} 同一个用意。 */
    private static final String PREFIX = "dv_";

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 签名截断到 16 字节 = 128 bit。
     *
     * <p>MAC 不是哈希，不需要抗碰撞，只需要「猜不中」——128 bit 已经在穷举不可行那一档。
     * 留全 32 字节只会让每个请求的头多 22 个字符，而这个头<b>每个请求都带</b>。
     */
    private static final int SIGNATURE_BYTES = 16;

    /** deviceId 的字节数。16 字节 → 32 位 hex，正好是 {@code t_device.device_id} 的 char(32)。 */
    private static final int DEVICE_ID_BYTES = 16;

    private static final int DEVICE_ID_HEX_LENGTH = DEVICE_ID_BYTES * 2;

    /**
     * 密钥长度下限。
     *
     * <p>比 {@code PiiHasher} 多了这一道，是因为两者的失效方式不同：PII 摘要的密钥弱，
     * 后果是脱库后手机号可能被还原（要先脱库）；设备密钥弱，<b>后果是任何人都能自签令牌</b>，
     * 不需要脱库，而且伪造出来的令牌和真的一模一样，日志里看不出来。
     */
    private static final int MIN_KEY_LENGTH = 32;

    private static final char PAYLOAD_SEPARATOR = '|';

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Map<Integer, SecretKeySpec> keys;

    private final int currentKeyVersion;

    /**
     * 配置不对就<b>启动即失败</b>，不给任何默认密钥。
     *
     * <p>理由同 {@code PiiHasher}：给了默认值，忘配置的人会用一个全世界都知道的密钥签令牌，
     * 功能完全正常 —— 注册得到令牌、请求验得过、限流也在计数，
     * 而「设备号不可伪造」这个<b>整套设计赖以成立的前提</b>直接归零，且没有任何迹象。
     */
    public DeviceTokenCodec(DeviceTokenProperties properties) {
        Map<Integer, String> raw = properties.getKeys();
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException(
                    "solvela.auth.device.keys 未配置：设备令牌的防伪完全依赖它，不允许用默认密钥兜底。"
                            + "请在配置中心/环境变量里注入，且【不要】复用 solvela.crypto.pii.hmac-key"
                            + "——那把不能改，这把必须能换。");
        }
        Map<Integer, SecretKeySpec> parsed = new LinkedHashMap<>();
        raw.forEach((version, key) -> {
            if (version == null || version <= 0) {
                throw new IllegalStateException("solvela.auth.device.keys 的版本号必须是正整数，实际：" + version);
            }
            if (key == null || key.length() < MIN_KEY_LENGTH) {
                // 不打印密钥本身，只说哪个版本不合格
                throw new IllegalStateException("solvela.auth.device.keys[" + version + "] 长度不足 "
                        + MIN_KEY_LENGTH + " 位。设备密钥弱 = 任何人都能自签令牌，且伪造的令牌在日志里看不出来。");
            }
            parsed.put(version, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        });
        if (!parsed.containsKey(properties.getKeyVersion())) {
            throw new IllegalStateException("solvela.auth.device.key-version=" + properties.getKeyVersion()
                    + " 在 keys 里不存在。轮换的正确顺序是【先加密钥、确认全部实例加载到，再改 key-version】"
                    + "——反过来会让新签的令牌全部验不过。");
        }
        this.keys = Map.copyOf(parsed);
        this.currentKeyVersion = properties.getKeyVersion();
        log.info("[Device] 设备令牌密钥已加载，可用版本 {}，签发用 v{}", parsed.keySet(), currentKeyVersion);
    }

    /**
     * 当前签发用的密钥版本。
     *
     * <p>签发方要把它落进 {@code t_device.key_version} —— 轮换期间「这批设备是哪把钥匙签的」
     * 是个会被问到的问题，而令牌本身在服务端不留存，事后没地方查。
     */
    public int currentKeyVersion() {
        return currentKeyVersion;
    }

    /**
     * 生成一个新的设备号。
     *
     * <p>放在本类而不是调用方，是因为「什么形状的 deviceId 是合法的」由验签这一侧说了算：
     * {@link #verify} 会拒绝不是 32 位 hex 的载荷。生成与校验分居两处时，
     * 改了一边忘了另一边的表现是「新签的令牌全部验不过」。
     */
    public static String newDeviceId() {
        byte[] raw = new byte[DEVICE_ID_BYTES];
        RANDOM.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    /**
     * 签发令牌。
     *
     * <p>入参不合法时<b>抛异常</b>，与 {@link #verify} 返回 null 的做法相反 ——
     * 判据是「谁的错」：验签的入参来自网络，非法是常态；签发的入参来自本进程，
     * 非法说明代码写错了，该在测试里就炸掉，而不是签出一个后面验不过的令牌。
     *
     * @param deviceId   32 位小写 hex，通常来自 {@link #newDeviceId()}
     * @param deviceType APP/H5/WECHAT/PC
     */
    public String issue(String deviceId, String deviceType) {
        if (!isValidDeviceId(deviceId)) {
            throw new IllegalArgumentException("deviceId 必须是 32 位小写 hex，实际：" + deviceId);
        }
        if (deviceType == null || deviceType.isBlank()) {
            throw new IllegalArgumentException("deviceType 不能为空");
        }
        if (deviceType.indexOf(PAYLOAD_SEPARATOR) >= 0) {
            // 允许的话，载荷会多切出一段，验签解析时整条令牌被判为非法 ——
            // 而那时已经发给客户端了，表现是「这个端的用户全都用不了」
            throw new IllegalArgumentException("deviceType 不能包含 '" + PAYLOAD_SEPARATOR + "'：" + deviceType);
        }

        String payload = ENCODER.encodeToString(
                (deviceId + PAYLOAD_SEPARATOR + deviceType + PAYLOAD_SEPARATOR + Instant.now().getEpochSecond())
                        .getBytes(StandardCharsets.UTF_8));
        String signed = currentKeyVersion + "." + payload;
        return PREFIX + signed + "." + ENCODER.encodeToString(sign(signed, currentKeyVersion));
    }

    /**
     * 验签并还原身份；<b>任何一处不对都返回 null，绝不抛异常</b>。
     *
     * <p>理由同 {@code MemberTokenStore.resolve}：令牌无效在 C 端是正常情况
     * （老版本客户端还没带、用户清了缓存、有人在扫接口），不是异常事件。
     * 用异常表达它，会让调用方在最热的那条路径上写 try-catch，
     * 也会让监控里的异常率永远是噪声，真出事时反而看不见。
     *
     * <p>同理这里<b>不打日志</b>：每个没带令牌的扫描请求都打一行，日志就没法看了。
     * 「有多少请求的令牌验不过」是个指标，由调用方按自己的口径埋，不在这里。
     */
    public DeviceIdentity verify(String tokenValue) {
        if (tokenValue == null || !tokenValue.startsWith(PREFIX)) {
            return null;
        }
        // limit=-1：末尾的空段也要保留，否则 "dv_1.payload." 会被切成 2 段而不是 3 段，
        // 一个签名为空的令牌就绕过了段数检查
        String[] parts = tokenValue.substring(PREFIX.length()).split("\\.", -1);
        if (parts.length != 3) {
            return null;
        }

        Integer version = parseVersion(parts[0]);
        if (version == null) {
            return null;
        }
        SecretKeySpec key = keys.get(version);
        if (key == null) {
            // 令牌是用一把【已经被删掉】的旧密钥签的，或者版本号被人乱改。
            // 两种都只能拒绝：验不了就是验不了
            return null;
        }

        byte[] expected = sign(parts[0] + "." + parts[1], version);
        byte[] actual = decode(parts[2]);
        // 🔴 必须用 MessageDigest.isEqual 而不是 Arrays.equals 或 String.equals：
        //    前者是常数时间的。逐字节短路比较会让「猜对了前几个字节」比「第一个就错」慢一点点，
        //    攻击者据此可以一个字节一个字节地把签名试出来，不需要知道密钥
        if (actual == null || !MessageDigest.isEqual(expected, actual)) {
            return null;
        }

        return parsePayload(parts[1], version);
    }

    /** 载荷验过签之后才解析：先证明它是我们签的，再相信它的内容。 */
    private DeviceIdentity parsePayload(String payloadSegment, int version) {
        byte[] decoded = decode(payloadSegment);
        if (decoded == null) {
            return null;
        }
        String[] fields = new String(decoded, StandardCharsets.UTF_8).split("\\" + PAYLOAD_SEPARATOR, -1);
        if (fields.length != 3) {
            return null;
        }
        if (!isValidDeviceId(fields[0]) || fields[1].isBlank()) {
            return null;
        }
        long epochSecond;
        try {
            epochSecond = Long.parseLong(fields[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new DeviceIdentity(fields[0], fields[1], version, Instant.ofEpochSecond(epochSecond));
    }

    private static Integer parseVersion(String segment) {
        try {
            int version = Integer.parseInt(segment);
            return version > 0 ? version : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isValidDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() != DEVICE_ID_HEX_LENGTH) {
            return false;
        }
        for (int i = 0; i < deviceId.length(); i++) {
            char c = deviceId.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    /** base64url 解码；不是合法 base64 时返回 null 而不是抛 —— 这个串来自网络。 */
    private static byte[] decode(String segment) {
        try {
            return DECODER.decode(segment);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] sign(String signedMaterial, int version) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keys.get(version));
            byte[] full = mac.doFinal(signedMaterial.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[SIGNATURE_BYTES];
            System.arraycopy(full, 0, truncated, 0, SIGNATURE_BYTES);
            return truncated;
        } catch (GeneralSecurityException e) {
            // HmacSHA256 是 JDK 必备算法、密钥在构造器里验过，走到这里只可能是运行环境被改坏了。
            // 🔴 绝不能吞掉返回 null：那会让 verify 变成「所有令牌都验不过」，
            //    表现是全站请求都被当成没有设备身份 —— 而如果哪天设备身份是硬闸，就是全站 401
            throw new IllegalStateException("设备令牌签名计算失败", e);
        }
    }
}
