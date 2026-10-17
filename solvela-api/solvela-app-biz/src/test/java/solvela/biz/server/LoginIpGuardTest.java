package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.mail.MailService;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.enums.MemberRiskModeEnum;
import solvela.member.api.AuthFailReason;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.auth.MemberAuthService;
import solvela.member.device.DeviceService;
import solvela.member.risk.dao.MemberRiskRuleDao;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.util.MemberEmailUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录的 IP 维度闸门：连真实 Redis、真实登录流程。阈值在本类里调小，免得循环几十次。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>对不存在账号的尝试超限 → 这个 IP 被限 —— 以前这类尝试一行记录都不留；</li>
 *   <li>🔴 不带设备、每个号只试一次的撞库脚本，设备闸与密码冷却都数不到，只有这里拦得住；</li>
 *   <li>换个 IP 不受影响；IP 为空一律放行。</li>
 * </ul>
 *
 * <h3>🔴 2026-10-15：阈值改成从规则表调，不再用配置项</h3>
 * {@code solvela.member.login.ip-guard.*} 已经随 IP 闸门进风控引擎一起删掉了 ——
 * 阈值现在在 {@code t_member_risk_rule.params}（后台可改、不发版）。
 * 配置项留在这里的话，测试会「设了一个没人读的值」，然后拿默认阈值 60/20/10 去跑，
 * 看起来像是闸门失效了。所以改用 {@link RiskRuleFixture} 改表，并把缓存刷新间隔设成 0。
 *
 * <p>同时把<b>密码冷却</b>（免罚 2 次，第 3 次起冷却）在这里关掉：它比 IP 失败阈值先触发，
 * 会把用例想看的那个 IP_LIMITED 盖成 OPERATION_LIMITED。冷却本身由
 * {@code PasswordCooldownTest} 覆盖。
 *
 * @Date 2026-09-27
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(properties = {
        "solvela.member.code.email-transport=REAL",
        // 改完规则表要立刻生效，不然用例跑完了缓存还没刷
        "solvela.member.risk.cache-refresh=0s",
})
class LoginIpGuardTest {

    private static final String PASSWORD = "SvIpGuard2026";

    @MockitoBean
    private MailService mailService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberRiskRuleDao ruleDao;

    @Autowired
    private LoginCounters counters;

    @Autowired
    private DeviceService deviceService;

    private final List<String> usedIps = new ArrayList<>();

    private final List<String> usedDevices = new ArrayList<>();

    private Long memberId;

    private RiskRuleFixture rules;

    /**
     * 阈值调小 + 让开会抢先命中的规则。
     *
     * <p>全局滑块也要关：本类直接调域、拿不到滑块通行票，不关的话每次登录都停在 CAPTCHA_REQUIRED，
     * 压根走不到 IP 闸门。
     */
    @org.junit.jupiter.api.BeforeEach
    void tuneRules() {
        rules = new RiskRuleFixture(ruleDao)
                .off("GLOBAL_CAPTCHA", "CAPTCHA_MATRIX", "PWD_COOLDOWN", "LOGIN_MATRIX")
                .params("IP_FAIL_LIMIT", "{\"window\":\"1h\",\"tiers\":[{\"over\":3,\"do\":6}]}")
                .params("IP_UNKNOWN_LIMIT", "{\"window\":\"1h\",\"tiers\":[{\"over\":2,\"do\":6}]}");
    }

    @AfterEach
    void restoreRules() {
        if (rules != null) {
            rules.close();
            rules = null;
        }
    }

    /**
     * 🔴 清理走 {@code counters.clear(...)}，<b>不自己拼 Redis 键</b>。
     *
     * <p>键名只在 {@code LoginCounters} 出现一次是那个类的全部意义；测试里再抄一份，
     * 它就有了两个源头 —— 2026-10-16 换滑动窗口时键名跟着值的类型一起变了
     * （带上 {@code w2:}），而抄在这里的那一份不会跟着变：
     * 表现是「测试之间互相污染」，而且看起来像是产品代码的问题。
     */
    @AfterEach
    void cleanUp() {
        for (String ip : usedIps) {
            counters.clear(LoginCounters.Counter.IP_FAIL, ip);
            counters.clear(LoginCounters.Counter.IP_UNKNOWN, ip);
            counters.clear(LoginCounters.Counter.IP_IDENTITY, ip);
        }
        usedIps.clear();
        for (String deviceId : usedDevices) {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE device_id = ?", deviceId);
            jdbcTemplate.update("DELETE FROM t_device WHERE device_id = ?", deviceId);
            counters.clear(LoginCounters.Counter.DEVICE_FAIL, deviceId);
            counters.clear(LoginCounters.Counter.DEVICE_MEMBER, deviceId);
        }
        usedDevices.clear();
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_operation_limit WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            memberId = null;
        }
    }

    @Test
    @DisplayName("🔴 对不存在的账号试超了 → 这个 IP 被限（每个号只试一次，账号锁数不到它）")
    void 不存在账号超限() {
        String ip = freshIp();
        // 阈值 2：记到第 3 次之后，第 4 次被拦
        for (int i = 0; i < 3; i++) {
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, freshEmail(), "whatever1").reason(),
                    "被拦之前，对外回答必须仍是那句含糊的 BAD_CREDENTIALS —— 不能泄露账号是否存在");
        }
        assertEquals(AuthFailReason.IP_LIMITED, login(ip, freshEmail(), "whatever1").reason());
    }

    // ------------------------------------------------------------------ 老设备豁免限流档（§4.1）

    @Test
    @DisplayName("🔴 老设备：IP 失败超限也不再限流 —— 校园网里一个人试错，不该让整片真人登不上")
    void 老设备免限流档() {
        String email = registerWithPassword();
        String device = oldDeviceWithSuccessfulLogin(email);
        String ip = freshIp();

        // 阈值 3，本来第 5 次就该 IP_LIMITED（见「密码错超限」那条用例）
        for (int i = 0; i < 6; i++) {
            assertEquals(AuthFailReason.BAD_CREDENTIALS,
                    login(ip, email, "WrongPass2026", device).reason(),
                    "第 " + (i + 1) + " 次：老设备不该撞上限流档");
        }
    }

    @Test
    @DisplayName("同一个 IP 上，新设备照样被限 —— 豁免跟着设备走，不是把这个 IP 整个放过")
    void 新设备照样被限() {
        String email = registerWithPassword();
        String device = oldDeviceWithSuccessfulLogin(email);
        String ip = freshIp();

        for (int i = 0; i < 6; i++) {
            login(ip, email, "WrongPass2026", device);
        }

        assertEquals(AuthFailReason.IP_LIMITED, login(ip, email, "WrongPass2026", null).reason(),
                "🔴 计数是照常加的 —— 豁免压的是处置，不是观察");
    }

    @Test
    @DisplayName("设备令牌刚领的（不够 7 天）→ 没有豁免，照常被限")
    void 新领的令牌不算老设备() {
        String email = registerWithPassword();
        // 领一个令牌、登成功一次，但【不】把 create_time 往前调
        String device = deviceService.register(
                new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();
        usedDevices.add(device);
        assertTrue(login(freshIp(), email, PASSWORD, device).success());
        String ip = freshIp();

        for (int i = 0; i < 4; i++) {
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026", device).reason());
        }
        assertEquals(AuthFailReason.IP_LIMITED, login(ip, email, "WrongPass2026", device).reason(),
                "光有年龄或光有成功登录都不算 —— 设备令牌是匿名领的，"
                        + "只看一个条件就等于给攻击者一张可以提前养好的免检通行证");
    }

    /**
     * 造一台「老设备」：领令牌 → 成功登录一次（写下那条成功日志）→ 把签发时间往前调 8 天。
     *
     * <p>🔴 顺序不能换：先调时间再登录也行，但先登录更接近真实 ——
     * 而「有过成功登录」这一半正是判据里不能少的那一半。
     */
    private String oldDeviceWithSuccessfulLogin(String email) {
        String device = deviceService.register(
                new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();
        usedDevices.add(device);
        assertTrue(login(freshIp(), email, PASSWORD, device).success(), "造数：这一次登录应当成功");
        jdbcTemplate.update("UPDATE t_device SET create_time = NOW() - INTERVAL 8 DAY WHERE device_id = ?",
                device);
        return device;
    }

    @Test
    @DisplayName("换一个 IP 不受影响")
    void 换IP不受影响() {
        String blocked = freshIp();
        for (int i = 0; i < 4; i++) {
            login(blocked, freshEmail(), "whatever1");
        }
        assertEquals(AuthFailReason.IP_LIMITED, login(blocked, freshEmail(), "whatever1").reason());

        assertEquals(AuthFailReason.BAD_CREDENTIALS, login(freshIp(), freshEmail(), "whatever1").reason());
    }

    @Test
    @DisplayName("密码错超了 → IP 被限（本用例关掉了密码冷却，否则它会先触发）")
    void 密码错超限() {
        String email = registerWithPassword();
        String ip = freshIp();
        // 阈值 3：记到第 4 次之后，第 5 次被拦
        for (int i = 0; i < 4; i++) {
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026").reason());
        }
        assertEquals(AuthFailReason.IP_LIMITED, login(ip, email, "WrongPass2026").reason());
    }

    @Test
    @DisplayName("🔴 密码冷却开着时它先于 IP 失败阈值触发 —— 两者谁先到，看的是规则表里的数")
    void 冷却先于IP失败阈值() {
        try (RiskRuleFixture cooldownOn = new RiskRuleFixture(ruleDao)
                .mode("PWD_COOLDOWN", MemberRiskModeEnum.ENFORCE)) {
            String email = registerWithPassword();
            String ip = freshIp();

            // 免罚 2 次，第 3 次失败起冷却；而 IP 失败阈值是 3（第 4 次才拦）
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026").reason());
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026").reason());
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026").reason(),
                    "🔴 第 3 次失败触发了冷却，但这一次的回答仍然是那句含糊的 BAD_CREDENTIALS —— "
                            + "当场说出剩余时间等于回答了「这个号存不存在」");
            assertEquals(AuthFailReason.PASSWORD_COOLDOWN, login(ip, email, "WrongPass2026").reason(),
                    "下一次才看到冷却。🔴 是 PASSWORD_COOLDOWN 不是 OPERATION_LIMITED —— "
                            + "被冷却的只有密码这条通道，用户还能走验证码登录和找回密码，"
                            + "而那两个按钮只有专门的码才带得出来");
        }
    }

    @Test
    @DisplayName("🔴 单纯的请求次数不归域管：同一个 IP 连续成功登录，域里不再按次数限它（那是网关 LOGIN 场景的事）")
    void 请求次数归网关() {
        String email = registerWithPassword();
        String ip = freshIp();
        // 2026-10-17 之前这里有一条「同 IP 每小时登录请求 > N 就限」的 IP_ATTEMPT_LIMIT；它只数次数、不看结果，
        // 移交给了网关限流。域里两边都数的话，同一件事有两个阈值、两套文案、两处要查
        for (int i = 0; i < 10; i++) {
            assertTrue(login(ip, email, PASSWORD).success(), "第 " + (i + 1) + " 次应当成功");
        }
    }

    @Test
    @DisplayName("IP 为空一律放行 —— 只可能是内部调用没传，不该把人挡在外面")
    void 空IP放行() {
        for (int i = 0; i < 5; i++) {
            assertNotEquals(AuthFailReason.IP_LIMITED, login(null, freshEmail(), "whatever1").reason());
        }
    }

    // ============================== 工具 ==============================

    private MemberAuthResult login(String ip, String email, String password) {
        return login(ip, email, password, null);
    }

    private MemberAuthResult login(String ip, String email, String password,
                                                      String deviceId) {
        return memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.EMAIL_PASSWORD, email, password, null, "H5", ip, deviceId, true));
    }

    /** 注册一个有密码的会员，返回邮箱 */
    private String registerWithPassword() {
        String email = freshEmail();
        String regIp = freshIp();
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.REGISTER, email, regIp, null)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "email:REGISTER:" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String code = redisService.get(key).split("\\|")[0];
        MemberRegisterResult result = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, code, null, PASSWORD, "H5", regIp, "H5", null));
        assertTrue(result.success(), "造数：注册应当成功，实际 " + result.reason());
        memberId = result.identity().memberId();
        return email;
    }

    private String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String ip = "198.51." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
        usedIps.add(ip);
        return ip;
    }

    private static String freshEmail() {
        return "svip" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }
}
