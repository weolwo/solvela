package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.auth.device.DeviceTokenCodec;
import solvela.base.domain.PageResult;
import solvela.member.Device;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.device.DeviceQueryService;
import solvela.member.device.DeviceService;
import solvela.member.device.domain.dto.DeviceMemberDTO;
import solvela.member.device.domain.query.DeviceQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「这台设备碰过哪些账号」。
 *
 * <h3>🔴 这是整套设备方案最终要产出的那张表</h3>
 * 方案里那句「有了 device_id，『一台设备碰过哪些账号』才查得出来，
 * 而那正是将来判断『要不要花钱买厂商指纹』的唯一依据」—— 说的就是这个查询。
 *
 * <p>在它之前，{@code device_id} 只是躺在库里的一列：数据一直在写，
 * 但没有任何一条代码路径读过它。
 *
 * <h3>它坏掉时不会报错</h3>
 * GROUP BY 写错、JOIN 写成 LEFT JOIN、聚合列取错 —— 结果都是一张
 * <b>看起来很正常但数字不对</b>的表。而运营会拿这张表去下「封不封」的判断。
 *
 * @Date 2026-09-10
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DeviceFanoutQueryTest {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceQueryService deviceQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String deviceId;

    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : memberIds) {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", id);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", id);
        }
        memberIds.clear();
        if (deviceId != null) {
            jdbcTemplate.update("DELETE FROM t_device WHERE device_id = ?", deviceId);
            deviceId = null;
        }
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.19." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    /**
     * 直接造登录日志，不走真实登录。
     *
     * <p>走真实登录要先造会员、发验证码、再登若干次，而本类要验的是
     * <b>那条 GROUP BY 查得对不对</b> —— 中间那些步骤只会让用例变脆。
     * 「登录会不会写 device_id」由 {@code LoginWritesDeviceIdTest} 负责。
     */
    private void writeLoginLog(long memberId, String device, int times) {
        writeLoginLog(memberId, device, times, 0);
    }

    /**
     * @param secondsAgo 登录时间往前推多少秒。
     *                   🔴 排序用例必须用它：两行写在同一秒里的话，{@code MAX(create_time)}
     *                   完全并列，ORDER BY 的结果由存储引擎决定 —— 那种用例时绿时红，
     *                   而红的时候看起来像是排序写错了。
     */
    private void writeLoginLog(long memberId, String device, int times, int secondsAgo) {
        memberIds.add(memberId);
        jdbcTemplate.update("""
                INSERT IGNORE INTO t_member (member_id, member_name, nickname, status, register_source)
                VALUES (?, ?, ?, 1, 'TEST')
                """, memberId, "sv" + memberId, "测试" + memberId);
        for (int i = 0; i < times; i++) {
            jdbcTemplate.update("""
                    INSERT INTO t_member_login_log (member_id, client_ip, device_type, device_id, status, create_time)
                    VALUES (?, '127.0.0.1', 'H5', ?, 0, DATE_SUB(NOW(), INTERVAL ? SECOND))
                    """, memberId, device, secondsAgo);
        }
    }

    @Test
    @DisplayName("🔴 一台设备关联多个账号 —— 这正是要看的信号")
    void 一机多号() {
        deviceId = deviceService.register(
                new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();
        long base = 970_000_000L + ThreadLocalRandom.current().nextInt(900_000);
        writeLoginLog(base, deviceId, 3);
        writeLoginLog(base + 1, deviceId, 1);

        List<DeviceMemberDTO> members = deviceQueryService.listMembers(deviceId);

        assertEquals(2, members.size(), "两个账号在这台设备上登过，就该列出两行");
        DeviceMemberDTO busiest = members.stream()
                .filter(m -> m.getMemberId().equals(base)).findFirst().orElseThrow();
        assertEquals(3, busiest.getLoginCount(),
                "🔴 次数必须是【每个账号在这台设备上的次数】。"
                        + "一台设备上 20 个账号各登 1 次，和 1 个账号登 20 次，是完全不同的两件事");
        assertNotNull(busiest.getMemberName(), "账号名要带出来 —— 运营说得出口的是账号，不是 10 位会员号");
        assertNotNull(busiest.getFirstLoginTime());
        assertNotNull(busiest.getLastLoginTime());
    }

    @Test
    @DisplayName("按最近登录倒序 —— 最值得看的那个排最前")
    void 按最近登录排序() {
        deviceId = deviceService.register(
                new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();
        long base = 971_000_000L + ThreadLocalRandom.current().nextInt(900_000);
        writeLoginLog(base, deviceId, 1, 60);
        writeLoginLog(base + 1, deviceId, 1, 0);

        List<DeviceMemberDTO> members = deviceQueryService.listMembers(deviceId);

        assertEquals(base + 1, members.get(0).getMemberId(), "后登的那个应该排在前面");
    }

    @Test
    @DisplayName("没碰过任何账号的设备 → 空列表，不是报错")
    void 没有关联账号() {
        deviceId = deviceService.register(
                new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();

        assertTrue(deviceQueryService.listMembers(deviceId).isEmpty(),
                "刚领到设备还没登录过是最常见的状态，不该是异常");
    }

    @Test
    @DisplayName("不存在的设备号 → 空列表")
    void 设备号不存在() {
        assertTrue(deviceQueryService.listMembers(DeviceTokenCodec.newDeviceId()).isEmpty());
    }

    @Test
    @DisplayName("按签发 IP 查 —— 识别「一个 IP 批量领设备」最直接的信号")
    void 按IP查设备() {
        String ip = freshIp();
        deviceId = deviceService.register(
                new DeviceRegisterCmd("H5", null, null, null, ip)).deviceId();

        DeviceQuery query = new DeviceQuery();
        query.setRegisterIp(ip);
        // 分页参数在 PageParam 上是 @NotNull 的 —— 走 HTTP 时由前端保证，
        // 直接调 service 就得自己给，否则 convert2PageQuery 会 NPE
        query.setPageNum(1L);
        query.setPageSize(10L);
        PageResult<Device> page = deviceQueryService.queryPage(query);

        assertEquals(1, page.list().size());
        assertEquals(deviceId, page.list().get(0).getDeviceId());
    }
}
