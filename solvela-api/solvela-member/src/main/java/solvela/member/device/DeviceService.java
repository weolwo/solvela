package solvela.member.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.auth.device.DeviceTokenCodec;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.Device;
import solvela.member.api.DeviceApi;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.api.DeviceRegisterFailReason;
import solvela.member.api.DeviceRegisterResult;
import solvela.member.device.dao.DeviceDao;

import java.util.Locale;
import java.util.Set;

/**
 * 设备签发：给一台没有身份的终端发一个<b>服务端说了算</b>的设备号。
 *
 * <h3>这一步存在的全部理由</h3>
 * 客户端自报 deviceId 的方案没用：脚本每次换一个 UUID，{@code t_device} 就成了
 * 「攻击者想写多少行就写多少行」的表，设备维度的计数永远是 1。
 * 服务端签发之后，<b>刷登录必须先刷设备注册</b> —— 而设备注册这一步可以让它很贵。
 *
 * <p>本类现在只贵在「一个 IP 一天 10 个」这一道上。后面还能往上加（图形验证码、
 * 厂商证明），加的位置都在本方法最前面，其余逻辑不用动 ——
 * 与 {@code MemberRegisterService} 给短信验证码留的位置是同一个形状。
 *
 * <h3>令牌签完就不再存在于服务端</h3>
 * 它是自包含的（见 {@code DeviceTokenCodec}），服务端只存 {@code t_device} 那一行元数据。
 * 所以客户端<b>必须持久化</b>它：丢了就只能重新注册，那会多出一行，也会消耗一次 IP 配额。
 *
 * @Date 2026-09-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService implements DeviceApi {

    private static final String RATE_LIMIT_KEY_PREFIX = "dev:reg:ip";

    /**
     * 允许的设备端取值，与 {@code t_device.device_type} 和 {@code t_member_login_log.device_type}
     * 的列注释<b>逐字一致</b>。
     *
     * <p>这里收得比登录日志那边严（那边收任意字符串）是有意的：{@code t_device} 是新表，
     * 从第一行开始就干净，成本为零；而这个值将来要决定限流阈值走哪一套
     * （H5 的设备可信度天然低于 App —— 它没有安装概念，清个缓存就是新设备），
     * 一旦混进 {@code "app"}、{@code "iOS"}、{@code "H5 "} 这类变体，
     * 阈值就会按「未知端」兜底，而没人会发现。
     *
     * <p>⚠️ 加新端要连同阈值一起想清楚，所以刻意<b>不做成配置项</b> ——
     * 配置项会让「加一个端」变成改一行 yaml 的事。
     */
    private static final Set<String> ALLOWED_DEVICE_TYPES = Set.of("APP", "H5", "WECHAT", "PC");

    private final DeviceDao deviceDao;
    private final DeviceTokenCodec deviceTokenCodec;
    private final DeviceProperties properties;
    private final RedisService redisService;

    /**
     * 签发一台新设备。
     *
     * <p>分支顺序有讲究：<b>参数校验在限频之前</b>。反过来的话，一个把 deviceType 写错的
     * 客户端版本会把整个 IP 的配额烧光 —— 而那是我们自己的 bug，不该由用户承担。
     * 与 {@code MemberRegisterService.register} 的顺序同源。
     */
    @Override
    public DeviceRegisterResult register(DeviceRegisterCmd cmd) {

        // ---------- 设备端校验 ----------
        String deviceType = normalizeDeviceType(cmd.deviceType());
        if (deviceType == null) {
            return DeviceRegisterResult.fail(DeviceRegisterFailReason.BAD_DEVICE_TYPE);
        }

        // ---------- IP 限频 ----------
        long retryAfter = consumeAttempt(cmd.clientIp());
        if (retryAfter > 0) {
            log.info("【设备签发】IP 配额已耗尽, ip: {}, deviceType: {}, 还需等待 {} 秒",
                    cmd.clientIp(), deviceType, retryAfter);
            return DeviceRegisterResult.tooManyAttempts(retryAfter);
        }

        // ---------- 签发 ----------
        String deviceId = DeviceTokenCodec.newDeviceId();
        String token = deviceTokenCodec.issue(deviceId, deviceType);

        // 🔴 先签令牌再落库，落库失败就整个失败 —— 反过来（先落库再签）在签发抛异常时
        //    会留下一行「有记录但客户端从没拿到令牌」的孤儿数据，而它会一直算在
        //    「这个 IP 签过几台设备」里，白白占用真实用户的配额。
        //    device_id 撞唯一约束的概率是 2^-128，真撞上说明 SecureRandom 坏了，
        //    那种情况必须炸出来，不能悄悄重试。
        deviceDao.insert(buildDevice(deviceId, deviceType, cmd));

        log.info("【设备签发】成功, deviceId: {}, deviceType: {}, ip: {}, keyVersion: {}",
                deviceId, deviceType, cmd.clientIp(), deviceTokenCodec.currentKeyVersion());
        return DeviceRegisterResult.ok(token, deviceId);
    }

    private Device buildDevice(String deviceId, String deviceType, DeviceRegisterCmd cmd) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setDeviceType(deviceType);
        // 下面三个都是客户端自报、不验的，只供人工排查。截断到列宽，
        // 而不是让一个超长值把整次签发变成 500 —— 客户端撒谎不该是服务端的错误
        device.setModel(truncate(cmd.model(), 64));
        device.setOsVersion(truncate(cmd.osVersion(), 32));
        device.setAppVersion(truncate(cmd.appVersion(), 32));
        device.setRegisterIp(cmd.clientIp());
        device.setRegisterRegion(SolvelaIpUtil.getRegion(cmd.clientIp()));
        device.setKeyVersion(deviceTokenCodec.currentKeyVersion());
        // 0-仅自报：还没有任何验证码或厂商证明。这一档是本期的全部现实
        device.setAttestLevel(0);
        // 0-正常。降进观察档由风控引擎的设备规则请求（RiskAction.OBSERVE_DEVICE），封禁只能人工
        device.setStatus(0);
        // create_time / last_active_time / update_time 交给列上的 CURRENT_TIMESTAMP 默认值：
        // MyBatis-Plus 默认跳过 null 字段，让库里的时间来自同一个时钟源
        return device;
    }

    /**
     * 规范化并校验设备端；不认识的返回 null。
     *
     * <p>只做 trim + 转大写这两件<b>不改变语义</b>的事。刻意不做「iOS/Android 映射成 APP」
     * 之类的猜测：猜错了会把一个本该被拒的脏值悄悄放进库里，
     * 而这一列将来要决定限流阈值走哪一套。
     */
    private static String normalizeDeviceType(String raw) {
        if (SolvelaStringUtil.isEmpty(raw)) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_DEVICE_TYPES.contains(normalized) ? normalized : null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /**
     * 消耗一次 IP 配额。返回 0 表示放行，正数表示还要等多少秒。
     *
     * <p>拿不到 IP 时<b>放行并打警告</b> —— 与 {@code MemberRegisterService.consumeAttempt}
     * 同一条判据：一律拒绝会让任何一次取 IP 失败变成「全站装不上 App」，
     * 那种故障比放过几台设备严重得多。警告日志让这件事至少能被发现。
     */
    private long consumeAttempt(String clientIp) {
        if (SolvelaStringUtil.isEmpty(clientIp)) {
            log.warn("【设备签发】拿不到客户端 IP，本次签发【未受限频保护】");
            return 0L;
        }
        String key = redisService.generateRedisKey(RATE_LIMIT_KEY_PREFIX, clientIp);
        // INCR 与首次 EXPIRE 必须原子完成，否则会留下没有 TTL 的计数键 ——
        // 那个 IP 的限流【再也不会解除】。理由见 RedisService.increment 的注释
        long attempts = redisService.increment(key, properties.registerWindow().toSeconds());
        if (attempts <= properties.maxRegisterPerIp()) {
            return 0L;
        }
        // 已经超了，告诉调用方还要等多久 —— 让用户点第二次才知道被限，是投诉的主要来源
        long ttl = redisService.getExpire(key);
        return Math.max(1L, ttl);
    }
}
