package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.auth.device.DeviceIdentity;
import solvela.auth.device.DeviceTokenCodec;
import solvela.base.module.redis.RedisService;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.api.DeviceRegisterFailReason;
import solvela.member.api.DeviceRegisterResult;
import solvela.member.device.DeviceProperties;
import solvela.member.device.DeviceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备签发的真实验收：连库、连 Redis、走 {@link DeviceService} 本身。
 *
 * <h3>这里要钉住的是「服务端说了算」</h3>
 * 整套设备防刷的地基是<b>设备号由服务端生成</b>——客户端自报的话，脚本每次换个 UUID，
 * 设备维度的计数永远是 1，限流形同虚设。所以下面既验「签出来的令牌验得回同一个身份」，
 * 也验「同一个 IP 领不到无限个身份」。
 *
 * <p>注入的是 {@link DeviceService} 而不是 {@code DeviceApi}：本进程里那个接口有两个 bean
 * （HTTP 薄壳 {@code DeviceInternalController} 与实现本身），按类型注入是歧义的 ——
 * 与 {@code FreezeRevokesSessionTest} 同一个理由。
 *
 * @Date 2026-09-08
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DeviceRegisterTest {

    /** 每条用例用一个独立 IP，避免彼此烧对方的配额 —— 限流键是按 IP 分的。 */
    /**
     * 造数用的 IP。
     *
     * <h3>🔴 地址空间要足够大，而且必须落在保留段里</h3>
     * 「同一 IP 一天最多签发几个设备」那个计数器在 Redis 里<b>按天累积、跨测试运行不清</b>，
     * 而本类有三个用例会把某个 IP 一路刷到上限。于是地址空间小的时候，
     * 后面某一次运行随机到一个<b>被前几次运行刷过</b>的 IP，
     * 「两次签发都该成功」这条就会莫名其妙地挂掉 —— 而单跑它又是绿的。
     * 2026-10-16 实测挂过一次：抽到的那个 IP 之前已经被刷到 9 次。
     *
     * <p>原来是 {@code 203.0.X.Y}（6.25 万个，而且只有 203.0.113.0/24 是保留段）。
     * 换成 {@code 100.64.0.0/10}（RFC 6598 运营商级 NAT，整段保留、约 400 万个地址）：
     * 空间大到碰撞可以忽略，而且不会被解析出真实归属地。
     */
    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "100." + (64 + r.nextInt(64)) + "." + r.nextInt(256) + "." + (1 + r.nextInt(250));
    }

    private final List<String> createdDeviceIds = new ArrayList<>();

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceTokenCodec deviceTokenCodec;

    @Autowired
    private DeviceProperties properties;

    @Autowired
    private RedisService redisService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        for (String deviceId : createdDeviceIds) {
            jdbcTemplate.update("DELETE FROM t_device WHERE device_id = ?", deviceId);
        }
        createdDeviceIds.clear();
    }

    private DeviceRegisterResult register(DeviceRegisterCmd cmd) {
        DeviceRegisterResult result = deviceService.register(cmd);
        if (result.success()) {
            createdDeviceIds.add(result.deviceId());
        }
        return result;
    }

    // ============================== 签发 ==============================

    @Test
    @DisplayName("签发成功：令牌验得回同一个身份，且身份是服务端定的")
    void 签发往返() {
        String ip = freshIp();

        DeviceRegisterResult result = register(
                new DeviceRegisterCmd("APP", "iPhone 15 Pro", "iOS 26.1", "1.4.0", ip));

        assertTrue(result.success(), "签发失败：" + result.reason());
        assertNotNull(result.deviceToken());
        assertTrue(result.deviceToken().startsWith("dv_"), "令牌前缀不对：" + result.deviceToken());

        DeviceIdentity identity = deviceTokenCodec.verify(result.deviceToken());
        assertNotNull(identity, "刚签出来的令牌自己验不过 —— 签发与验签用的不是同一把密钥或同一套格式");
        assertEquals(result.deviceId(), identity.deviceId());
        assertEquals("APP", identity.deviceType());
        assertTrue(result.deviceId().matches("[0-9a-f]{32}"), "设备号形状不对：" + result.deviceId());
    }

    @Test
    @DisplayName("落库的元数据与签发一致，key_version 必须跟当前密钥对上")
    void 落库() {
        String ip = freshIp();

        DeviceRegisterResult result = register(
                new DeviceRegisterCmd("H5", "Xiaomi 14", "Android 15", "2.0.1", ip));
        assertTrue(result.success());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_device WHERE device_id = ?", result.deviceId());

        assertEquals("H5", row.get("device_type"));
        assertEquals("Xiaomi 14", row.get("model"));
        assertEquals("Android 15", row.get("os_version"));
        assertEquals("2.0.1", row.get("app_version"));
        assertEquals(ip, row.get("register_ip"));
        assertEquals(deviceTokenCodec.currentKeyVersion(), ((Number) row.get("key_version")).intValue(),
                "key_version 对不上，轮换期间就查不出「这批设备是哪把钥匙签的」");
        assertEquals(0, ((Number) row.get("attest_level")).intValue(), "还没有任何验证码或厂商证明，只能是 0");
        assertEquals(0, ((Number) row.get("status")).intValue(), "新设备必须是正常档");
        assertNotNull(row.get("create_time"), "时间应当来自列上的 CURRENT_TIMESTAMP 默认值");
        assertNotNull(row.get("last_active_time"));
        assertNull(row.get("operator"), "自动签发不该有操作人");
    }

    @Test
    @DisplayName("🔴 两次签发得到两个不同的设备号 —— 服务端每次都新生成")
    void 每次都是新设备() {
        String ip = freshIp();

        DeviceRegisterResult first = register(new DeviceRegisterCmd("APP", null, null, null, ip));
        DeviceRegisterResult second = register(new DeviceRegisterCmd("APP", null, null, null, ip));

        assertTrue(first.success() && second.success());
        assertFalse(first.deviceId().equals(second.deviceId()),
                "两次签发拿到同一个设备号 —— 那等于客户端能复用身份，限流就废了");
        assertFalse(first.deviceToken().equals(second.deviceToken()));
    }

    // ============================== 设备端校验 ==============================

    @Test
    @DisplayName("设备端不在允许集合里 → 拒绝，且不落库")
    void 设备端非法() {
        String ip = freshIp();
        long before = countDevices();

        for (String bad : new String[]{null, "", "  ", "IOS", "ANDROID", "app store", "APP|X"}) {
            DeviceRegisterResult result = register(new DeviceRegisterCmd(bad, null, null, null, ip));
            assertEquals(DeviceRegisterFailReason.BAD_DEVICE_TYPE, result.reason(),
                    "deviceType=" + bad + " 不该被接受");
            assertNull(result.deviceToken());
        }

        assertEquals(before, countDevices(), "被拒的请求不该在 t_device 留下任何行");
    }

    @Test
    @DisplayName("🔴 参数错不烧 IP 配额 —— 那是我们自己的 bug，不该由用户承担")
    void 参数错不烧配额() {
        String ip = freshIp();

        // 先用非法参数打满配额次数还多一次
        for (int i = 0; i < properties.maxRegisterPerIp() + 1; i++) {
            register(new DeviceRegisterCmd("IOS", null, null, null, ip));
        }

        DeviceRegisterResult result = register(new DeviceRegisterCmd("APP", null, null, null, ip));
        assertTrue(result.success(),
                "一个把 deviceType 写错的客户端版本把整个 IP 的配额烧光了 —— 校验必须排在限频之前");
    }

    @Test
    @DisplayName("设备端只做 trim + 转大写，不做「iOS 猜成 APP」这类映射")
    void 设备端规范化() {
        String ip = freshIp();

        DeviceRegisterResult result = register(new DeviceRegisterCmd("  wechat  ", null, null, null, ip));

        assertTrue(result.success());
        assertEquals("WECHAT", deviceTokenCodec.verify(result.deviceToken()).deviceType());
        assertEquals("WECHAT", jdbcTemplate.queryForObject(
                "SELECT device_type FROM t_device WHERE device_id = ?", String.class, result.deviceId()));
    }

    // ============================== 限频 ==============================

    @Test
    @DisplayName("🔴 同一 IP 领不到无限个设备身份 —— 这是整套防刷的地基")
    void IP限频() {
        String ip = freshIp();
        int max = properties.maxRegisterPerIp();

        for (int i = 1; i <= max; i++) {
            DeviceRegisterResult ok = register(new DeviceRegisterCmd("APP", null, null, null, ip));
            assertTrue(ok.success(), "配额内的第 " + i + " 次签发不该被拒");
        }

        DeviceRegisterResult limited = register(new DeviceRegisterCmd("APP", null, null, null, ip));

        assertEquals(DeviceRegisterFailReason.TOO_MANY_ATTEMPTS, limited.reason());
        assertNull(limited.deviceToken());
        assertTrue(limited.retryAfterSeconds() > 0,
                "必须告诉调用方还要等多久 —— 让用户点第二次才知道被限，是投诉的主要来源");
        assertTrue(limited.retryAfterSeconds() <= properties.registerWindow().toSeconds(),
                "剩余时间不该超过窗口本身");
    }

    @Test
    @DisplayName("限频是按 IP 分的，一个 IP 被限不影响另一个")
    void 限频按IP隔离() {
        String busy = freshIp();
        for (int i = 0; i < properties.maxRegisterPerIp() + 1; i++) {
            register(new DeviceRegisterCmd("APP", null, null, null, busy));
        }
        assertEquals(DeviceRegisterFailReason.TOO_MANY_ATTEMPTS,
                register(new DeviceRegisterCmd("APP", null, null, null, busy)).reason());

        DeviceRegisterResult other = register(new DeviceRegisterCmd("APP", null, null, null, freshIp()));

        assertTrue(other.success(), "限流键没有按 IP 分开，一个人能把全站的设备注册打瘫");
    }

    @Test
    @DisplayName("限流键必须带 TTL，否则那个 IP 的限制再也不会解除")
    void 限流键有过期时间() {
        String ip = freshIp();
        register(new DeviceRegisterCmd("APP", null, null, null, ip));

        long ttl = redisService.getExpire(redisService.generateRedisKey("dev:reg:ip", ip));

        assertTrue(ttl > 0, "计数键没有 TTL —— 这个 IP 的限流永远不会解除。见 RedisService.increment 的注释");
        assertTrue(ttl <= properties.registerWindow().toSeconds(), "TTL 不该超过配置的窗口");
    }

    @Test
    @DisplayName("拿不到 IP 时放行 —— 取 IP 失败不该变成「全站装不上 App」")
    void 没有IP时放行() {
        DeviceRegisterResult result = register(new DeviceRegisterCmd("APP", null, null, null, null));

        assertTrue(result.success(), "拿不到 IP 就一律拒绝，比放过几台设备严重得多");
    }

    // ============================== 自报字段 ==============================

    @Test
    @DisplayName("客户端自报的超长字段被截断，而不是让整次签发变成 500")
    void 超长字段截断() {
        String ip = freshIp();
        String tooLong = UUID.randomUUID().toString().repeat(10);

        DeviceRegisterResult result = register(
                new DeviceRegisterCmd("PC", tooLong, tooLong, tooLong, ip));

        assertTrue(result.success(), "客户端撒谎不该是服务端的错误");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT model, os_version, app_version FROM t_device WHERE device_id = ?", result.deviceId());
        assertEquals(64, ((String) row.get("model")).length());
        assertEquals(32, ((String) row.get("os_version")).length());
        assertEquals(32, ((String) row.get("app_version")).length());
    }

    private long countDevices() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_device", Long.class);
        return count == null ? 0L : count;
    }
}
