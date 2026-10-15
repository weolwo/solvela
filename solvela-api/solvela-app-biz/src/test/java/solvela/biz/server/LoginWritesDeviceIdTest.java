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
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.auth.MemberAuthService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备号要真的落进 {@code t_member_login_log.device_id}。
 *
 * <h3>为什么值得一条独立用例</h3>
 * 这一列是整套方案里<b>最便宜、回报最高</b>的一笔：有了它，「一台设备碰过哪些账号」
 * 才查得出来，而那正是将来判断「要不要花钱买厂商指纹」的唯一依据。
 *
 * <p>它坏掉的时候<b>不会有任何报错</b> —— 登录照常成功，日志照常写，只是那一列恒为 NULL。
 * 而等到需要它的那天（比如要判断一批号是不是同一批设备注册的），数据已经缺了几个月，
 * 补不回来。所以必须有一条会失败的测试盯着。
 *
 * <p>走的是域服务本身而不是 HTTP：本进程里 {@code MemberAuthApi} 有两个 bean
 * （HTTP 薄壳与实现），按接口注入是歧义的 —— 与 {@code FreezeRevokesSessionTest} 同一个理由。
 *
 * @Date 2026-09-09
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        // 改完规则表要立刻生效
        "solvela.member.risk.cache-refresh=0s",
})
class LoginWritesDeviceIdTest {

    private static final String PASSWORD = "SvDevice2026";

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private solvela.member.risk.dao.MemberRiskRuleDao ruleDao;

    private Long memberId;

    private String phone;

    private RiskRuleFixture rules;

    /**
     * 本类直接调域，拿不到滑块通行票，所以要把全局滑块关掉 ——
     * 不关的话每次登录都停在「要先过滑块」，压根走不到「device_id 有没有落库」这件事。
     */
    @org.junit.jupiter.api.BeforeEach
    void turnOffGlobalCaptcha() {
        rules = new RiskRuleFixture(ruleDao).off("GLOBAL_CAPTCHA");
    }

    @AfterEach
    void restoreRules() {
        if (rules != null) {
            rules.close();
            rules = null;
        }
    }

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
        }
    }

    /** 每次用一个新号、新设备、新 IP —— 三个维度都有限频，复用会互相烧配额。 */
    private static String freshPhone() {
        return "13" + (100_000_000 + ThreadLocalRandom.current().nextInt(800_000_000));
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.51." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    private void register(String deviceId) {
        phone = freshPhone();
        String ip = freshIp();
        MemberRegisterResult result = memberAuthService.register(MemberRegisterCmd.byPhonePassword(
                phone, PASSWORD,
                TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip),
                "APP", ip, "APP", deviceId));
        assertTrue(result.success(), "前提不成立，注册就失败了：" + result.reason());
        memberId = result.identity().memberId();
    }

    private MemberAuthResult login(String deviceId) {
        return memberAuthService.authenticate(
                MemberAuthCmd.byPhonePassword(phone, PASSWORD, "APP", freshIp(), deviceId));
    }

    private List<Map<String, Object>> logs() {
        return jdbcTemplate.queryForList(
                "SELECT status, device_id FROM t_member_login_log WHERE member_id = ? ORDER BY id", memberId);
    }

    @Test
    @DisplayName("🔴 带设备号登录 → device_id 落库，且就是那一个")
    void 登录落设备号() {
        String device = DeviceTokenCodec.newDeviceId();
        register(device);

        assertTrue(login(device).success(), "登录应当成功");

        List<Map<String, Object>> rows = logs();
        assertFalse(rows.isEmpty(), "登录成功却没写日志");
        Map<String, Object> last = rows.get(rows.size() - 1);
        assertEquals(device, last.get("device_id"),
                "device_id 没落库 —— 这一列坏掉不会有任何报错，等到需要它那天数据已经缺了几个月");
    }

    @Test
    @DisplayName("登录失败的那条日志同样要带设备号 —— 失败才是最该关联的")
    void 失败也落设备号() {
        String device = DeviceTokenCodec.newDeviceId();
        register(device);

        MemberAuthResult bad = memberAuthService.authenticate(
                MemberAuthCmd.byPhonePassword(phone, "WrongPassword9", "APP", freshIp(), device));
        assertFalse(bad.success());

        Map<String, Object> last = logs().get(logs().size() - 1);
        assertEquals(device, last.get("device_id"),
                "「这台设备在挨个试不同的号」全靠失败日志才看得出来");
    }

    @Test
    @DisplayName("老客户端不带设备号 → 照常登录成功，device_id 为 NULL")
    void 老客户端照常可用() {
        register(null);

        assertTrue(login(null).success(),
                "🔴 灰度期间没有设备号必须照常放行 —— 否则 enforce 之前就把老版本用户全挡在外面了");

        assertNull(logs().get(logs().size() - 1).get("device_id"), "没有设备号时这一列就该是 NULL");
    }

    @Test
    @DisplayName("换了设备登录 → 日志里两条各记各的设备号")
    void 换设备() {
        String first = DeviceTokenCodec.newDeviceId();
        String second = DeviceTokenCodec.newDeviceId();
        register(first);

        assertTrue(login(first).success());
        assertTrue(login(second).success());

        List<Map<String, Object>> rows = logs();
        List<Object> devices = rows.stream().map(r -> r.get("device_id")).toList();
        assertTrue(devices.contains(first) && devices.contains(second),
                "两次登录来自不同设备，日志必须分得开 —— 否则「这个号在几台设备上登过」就查不出来了。实际：" + devices);
    }

    @Test
    @DisplayName("注册也带设备号：注册那一步不写登录日志，但设备限频已经计过数")
    void 注册不写登录日志() {
        String device = DeviceTokenCodec.newDeviceId();
        register(device);

        assertNotNull(memberId);
        assertTrue(logs().isEmpty(),
                "注册刻意不写 t_member_login_log —— 那件事完整记在 t_member 的三列上，"
                        + "再写一条会让「这个号什么时候登过」的查询先要把它剔掉");
    }
}
