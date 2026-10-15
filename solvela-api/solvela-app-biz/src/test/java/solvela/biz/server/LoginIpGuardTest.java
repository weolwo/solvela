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
import solvela.member.api.AuthFailReason;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.auth.MemberAuthService;
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
    private solvela.member.risk.dao.MemberRiskRuleDao ruleDao;

    private final List<String> usedIps = new ArrayList<>();

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
                .params("IP_ATTEMPT_LIMIT", "{\"window\":\"1h\",\"tiers\":[{\"over\":6,\"do\":6}]}")
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

    @AfterEach
    void cleanUp() {
        for (String ip : usedIps) {
            redisService.delete(
                    redisService.generateRedisKey("login:ip:attempt:", ip),
                    redisService.generateRedisKey("login:ip:fail:", ip),
                    redisService.generateRedisKey("login:ip:unknown:", ip));
        }
        usedIps.clear();
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
                .mode("PWD_COOLDOWN", solvela.enums.MemberRiskModeEnum.ENFORCE)) {
            String email = registerWithPassword();
            String ip = freshIp();

            // 免罚 2 次，第 3 次失败起冷却；而 IP 失败阈值是 3（第 4 次才拦）
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026").reason());
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026").reason());
            assertEquals(AuthFailReason.BAD_CREDENTIALS, login(ip, email, "WrongPass2026").reason(),
                    "🔴 第 3 次失败触发了冷却，但这一次的回答仍然是那句含糊的 BAD_CREDENTIALS —— "
                            + "当场说出剩余时间等于回答了「这个号存不存在」");
            assertEquals(AuthFailReason.OPERATION_LIMITED, login(ip, email, "WrongPass2026").reason(),
                    "下一次才看到冷却");
        }
    }

    @Test
    @DisplayName("总次数超限 → 被限（含成功的登录）")
    void 总次数超限() {
        String email = registerWithPassword();
        String ip = freshIp();
        for (int i = 0; i < 6; i++) {
            assertTrue(login(ip, email, PASSWORD).success(), "第 " + (i + 1) + " 次应当成功");
        }
        assertEquals(AuthFailReason.IP_LIMITED, login(ip, email, PASSWORD).reason());
    }

    @Test
    @DisplayName("IP 为空一律放行 —— 只可能是内部调用没传，不该把人挡在外面")
    void 空IP放行() {
        for (int i = 0; i < 5; i++) {
            assertNotEquals(AuthFailReason.IP_LIMITED, login(null, freshEmail(), "whatever1").reason());
        }
    }

    // ============================== 工具 ==============================

    private solvela.member.api.MemberAuthResult login(String ip, String email, String password) {
        return memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.EMAIL_PASSWORD, email, password, null, "H5", ip, null, true));
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
