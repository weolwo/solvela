package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.auth.device.DeviceTokenCodec;
import solvela.base.module.redis.RedisService;
import solvela.enums.DeviceStatusEnum;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.device.DeviceDispositionService;
import solvela.member.device.DeviceService;
import solvela.security.SecurityKeys;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备处置闭环。
 *
 * <h3>在这套用例之前，t_device.status 是一列死数据</h3>
 * 签发时写 0，此后全仓没有一处读它、没有一处改它 —— 三档、{@code idx_dev_status}
 * 索引、{@code remark} 与 {@code operator} 两列，全是为一件从没发生过的事准备的。
 *
 * <h3>这里钉的四条，坏掉时都【没有任何报错】</h3>
 * <ul>
 *   <li><b>自动降档不能覆盖人工封禁</b> —— 客服刚封的设备，下一次命中限流规则
 *       就自己解封了；</li>
 *   <li><b>解封要同时清掉观察键</b> —— 否则后台显示「已恢复正常」，
 *       而用户下次登录仍被要求验证码；</li>
 *   <li><b>观察期满要自动回档</b> —— 不回的话观察档是个只进不出的单向门；</li>
 *   <li><b>活跃刷新要节流</b> —— 不节流会把 t_device 写成热点表。</li>
 * </ul>
 *
 * @Date 2026-09-10
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DeviceDispositionTest {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceDispositionService dispositionService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    private String deviceId;

    @AfterEach
    void cleanUp() {
        if (deviceId != null) {
            jdbcTemplate.update("DELETE FROM t_device WHERE device_id = ?", deviceId);
            redisService.delete(redisService.generateRedisKey("dev:observe:", deviceId));
            redisService.delete(redisService.generateRedisKey("dev:active:", deviceId));
            redis.opsForSet().remove(SecurityKeys.BANNED_DEVICES, deviceId);
            deviceId = null;
        }
    }

    /** 每次换 IP：签发有 IP 日限，复用会互相烧配额。 */
    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.18." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    /** 走真实签发，而不是手写一行 —— 顺带保证签发写下的 status 就是 0。 */
    private void registerDevice() {
        deviceId = deviceService.register(
                new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();
    }

    private Map<String, Object> row() {
        return jdbcTemplate.queryForMap(
                "SELECT status, remark, operator, last_active_time FROM t_device WHERE device_id = ?",
                deviceId);
    }

    private int status() {
        return ((Number) row().get("status")).intValue();
    }

    // ============================== 自动降档 / 回档 ==============================

    @Test
    @DisplayName("命中规则 → 进观察档，库里那一行跟着变")
    void 进观察档() {
        registerDevice();

        dispositionService.observe(deviceId, "设备登录过于频繁");

        assertEquals(DeviceStatusEnum.OBSERVE, dispositionService.currentStatus(deviceId));
        assertEquals(DeviceStatusEnum.OBSERVE.getValue(), status(), "影子没跟上，后台就看不见这台设备");
        assertEquals("设备登录过于频繁", row().get("remark"), "原因要落库 —— 客服看到 status=1 得知道为什么");
    }

    @Test
    @DisplayName("重复命中只续期，不重复写库")
    void 重复命中幂等() {
        registerDevice();
        dispositionService.observe(deviceId, "第一次");

        dispositionService.observe(deviceId, "第二次");

        assertEquals(DeviceStatusEnum.OBSERVE, dispositionService.currentStatus(deviceId));
        assertEquals("第一次", row().get("remark"),
                "第二次不该重写 remark —— 首次命中的原因才是这台设备被盯上的理由");
    }

    @Test
    @DisplayName("🔴 观察期满 → 下次读到它时自动回档，不需要定时任务")
    void 观察期满自动回档() {
        registerDevice();
        dispositionService.observe(deviceId, "设备登录过于频繁");
        assertEquals(DeviceStatusEnum.OBSERVE.getValue(), status());

        // 计时器到期 = 观察期满。这里直接删掉那个键来模拟
        redisService.delete(redisService.generateRedisKey("dev:observe:", deviceId));

        assertEquals(DeviceStatusEnum.NORMAL, dispositionService.currentStatus(deviceId),
                "不回档的话，观察档就是个只进不出的单向门");
        assertEquals(DeviceStatusEnum.NORMAL.getValue(), status(), "影子也要跟着纠正回来");
    }

    // ============================== 人工封禁 ==============================

    @Test
    @DisplayName("人工封禁 → 库里记下操作人；封禁不会自动解除")
    void 人工封禁() {
        registerDevice();

        assertTrue(dispositionService.disposeManually(
                deviceId, DeviceStatusEnum.BANNED, "批量注册", "admin-7"));

        assertEquals(DeviceStatusEnum.BANNED, dispositionService.currentStatus(deviceId));
        assertEquals("admin-7", row().get("operator"),
                "🔴 operator 必须落库：封禁是有人签字的决定，事后要追溯得到是谁签的");
    }

    @Test
    @DisplayName("🔴 自动降档【不能】覆盖人工封禁 —— 否则客服刚封的设备会自己解封")
    void 自动降档不覆盖封禁() {
        registerDevice();
        dispositionService.disposeManually(deviceId, DeviceStatusEnum.BANNED, "批量注册", "admin-7");

        // 这台设备照常会继续命中限流规则
        dispositionService.observe(deviceId, "设备登录过于频繁");

        assertEquals(DeviceStatusEnum.BANNED.getValue(), status(),
                "被改回观察档的话，封禁悄悄失效，而没有任何迹象");
        assertEquals(DeviceStatusEnum.BANNED, dispositionService.currentStatus(deviceId),
                "🔴 判断也要以封禁为准：Redis 里此刻【有】观察键，但它不该盖过库里的封禁");
    }

    @Test
    @DisplayName("🔴 解封要同时清掉观察键 —— 否则后台说恢复了，用户下次登录还要验码")
    void 解封要清计时器() {
        registerDevice();
        dispositionService.observe(deviceId, "设备登录过于频繁");
        assertEquals(DeviceStatusEnum.OBSERVE, dispositionService.currentStatus(deviceId));

        dispositionService.disposeManually(deviceId, DeviceStatusEnum.NORMAL, "误判，已核实", "admin-7");

        assertEquals(DeviceStatusEnum.NORMAL, dispositionService.currentStatus(deviceId),
                "「后台说的和实际不一样」是这类功能最常见的失效方式");
        assertNull(redisService.get(redisService.generateRedisKey("dev:observe:", deviceId)));
    }

    // ============================== 封禁镜像（网关安全闸读它） ==============================

    @Test
    @DisplayName("🔴 封禁 → 进镜像；解封 → 出镜像。网关不连库，镜像里没有就等于没封")
    void 封禁同步镜像() {
        registerDevice();

        dispositionService.disposeManually(deviceId, DeviceStatusEnum.BANNED, "批量注册", "admin-7");
        assertTrue(inMirror(deviceId), "被封的设备不在镜像里，它在网关那边照常能抽奖、签到");

        dispositionService.disposeManually(deviceId, DeviceStatusEnum.NORMAL, "误判，已核实", "admin-7");
        assertFalse(inMirror(deviceId), "解封了还在镜像里，用户会被一直挡在网关外面");
    }

    @Test
    @DisplayName("🔴 镜像丢了 / 多了 → 按库全量重建：库是真相源")
    void 按库重建镜像() {
        registerDevice();
        dispositionService.disposeManually(deviceId, DeviceStatusEnum.BANNED, "批量注册", "admin-7");
        String stray = DeviceTokenCodec.newDeviceId();
        // 模拟：同步那一下没写进去，另有一条库里早已解封的残留
        redis.opsForSet().remove(SecurityKeys.BANNED_DEVICES, deviceId);
        redis.opsForSet().add(SecurityKeys.BANNED_DEVICES, stray);

        int size = dispositionService.rebuildBannedMirror();

        assertTrue(inMirror(deviceId), "库里封着的设备要补回来");
        assertFalse(inMirror(stray), "库里没封的要清掉");
        assertEquals(size, redis.opsForSet().size(SecurityKeys.BANNED_DEVICES).intValue());
    }

    private boolean inMirror(String id) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(SecurityKeys.BANNED_DEVICES, id));
    }

    private static void assertNull(Object o) {
        Assertions.assertNull(o);
    }

    @Test
    @DisplayName("人工处置必须带操作人 —— 不带就拒，而不是记一个匿名的处置")
    void 人工处置必须署名() {
        registerDevice();

        assertThrows(IllegalArgumentException.class, () -> dispositionService.disposeManually(
                deviceId, DeviceStatusEnum.BANNED, "批量注册", null));
    }

    @Test
    @DisplayName("处置一个不存在的设备号 → 返回 false，而不是抛异常")
    void 处置不存在的设备() {
        assertFalse(dispositionService.disposeManually(
                DeviceTokenCodec.newDeviceId(), DeviceStatusEnum.BANNED, "试试", "admin-7"));
    }

    // ============================== 活跃刷新 ==============================

    @Test
    @DisplayName("🔴 活跃刷新要节流 —— 每个请求都写库会把 t_device 写成热点表")
    void 活跃刷新节流() {
        registerDevice();
        Object first = row().get("last_active_time");

        // 连着调 5 次，只有第一次真的落库
        for (int i = 0; i < 5; i++) {
            dispositionService.touch(deviceId);
        }

        assertNotEquals(null, redisService.get(redisService.generateRedisKey("dev:active:", deviceId)),
                "节流键没建起来的话，下一个请求还会再写一次库");
        // 同一秒内 NOW() 可能与签发时间相同，所以这里断言的是「节流键在」，
        // 而不是时间真的变了 —— 后者在秒级精度下不稳定
        assertNotEquals(null, first);
    }

    @Test
    @DisplayName("设备号为空时什么都不做 —— 老客户端没有设备号是常态，不是异常")
    void 空设备号() {
        dispositionService.touch(null);
        dispositionService.observe(" ", "无所谓");

        assertEquals(DeviceStatusEnum.NORMAL, dispositionService.currentStatus(null));
    }
}
