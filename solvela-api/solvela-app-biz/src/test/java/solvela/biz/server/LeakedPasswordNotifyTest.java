package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiHasher;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.RegisterFailReason;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberEmailUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「你正在用的密码已泄露」那条站内信（P3 ③ 的后半截）。
 *
 * <h3>🔴 这套用例的造数本身就说明了这个功能为什么存在</h3>
 * 它<b>没法</b>用注册接口造出一个「密码已泄露」的账号 —— 注册那一处已经把这种密码拦掉了。
 * 所以它先正常注册，再<b>直接改库</b>把密码哈希换成泄露密码的哈希。
 * 那正是这个功能唯一的服务对象：<b>存量账号</b>（在我们加上那张表之前就设好了密码的人）。
 *
 * <h3>钉住的三条</h3>
 * <ul>
 *   <li><b>不拦登录</b> —— 拦了等于把人锁在自己账号外面，而他并没做错什么；</li>
 *   <li><b>按会员节流</b> —— 每次登录都提醒一遍，用户学到的是忽略 SYSTEM 分类的所有消息；</li>
 *   <li><b>文案里没有链接</b> —— 与新设备提醒、可疑登录同一条规矩。</li>
 * </ul>
 *
 * @Date 2026-10-17
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ExtendWith(RelaxedRiskRules.class)
@TestPropertySource(properties = "solvela.member.risk.cache-refresh=0s")
class LeakedPasswordNotifyTest {

    /** 一个在公开泄露库里排得很前、却完全满足强度规则的密码 —— 这正是那张表要挡的形状 */
    private static final String LEAKED_PASSWORD = "Password1";

    private static final String CLEAN_PASSWORD = "SvLeakTest2026";

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    private String email;

    @BeforeEach
    void registerMember() {
        email = "leak" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.REGISTER, email, freshIp(), null)).success());
        MemberRegisterResult result = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, readCode(EmailCodeScene.REGISTER), null,
                CLEAN_PASSWORD, "H5", freshIp(), "H5", null));
        assertTrue(result.success(), "造数：注册应当成功，实际 " + result.reason());
        memberId = result.identity().memberId();
    }

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_notification WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            redisService.delete(redisService.generateRedisKey(
                    "mbr:pwd:leaked-notify:", String.valueOf(memberId)));
            memberId = null;
        }
    }

    // ============================== 主路径 ==============================

    @Test
    @DisplayName("🔴 用已泄露的密码登录 → 照常登进去，并收到一条 SYSTEM 站内信")
    void 提醒但不拦() {
        makePasswordLeaked();

        MemberAuthResult result = login(LEAKED_PASSWORD);

        assertTrue(result.success(), "🔴 绝不能拦登录：拦了就是把人锁在自己账号外面");
        assertEquals(1, notificationCount());
        assertEquals("SYSTEM", jdbcTemplate.queryForObject("""
                SELECT category FROM t_member_notification
                 WHERE member_id = ? AND template_code = 'LEAKED_PASSWORD'
                """, String.class, memberId), "密码安全不该是能被免打扰关掉的东西");
    }

    @Test
    @DisplayName("🔴 节流：连着登录五次只提醒一条")
    void 节流() {
        makePasswordLeaked();

        for (int i = 0; i < 5; i++) {
            assertTrue(login(LEAKED_PASSWORD).success());
        }

        assertEquals(1, notificationCount(),
                "每次登录都提醒一遍，用户学到的是忽略 SYSTEM 分类的所有消息");
    }

    @Test
    @DisplayName("密码没泄露的人一条都收不到 —— 否则这条提醒立刻变成噪音")
    void 正常密码不提醒() {
        assertTrue(login(CLEAN_PASSWORD).success());

        assertEquals(0, notificationCount());
    }

    @Test
    @DisplayName("🔴 改成一个没泄露的密码之后，就不该再提醒了")
    void 改完就不再提醒() {
        makePasswordLeaked();
        assertTrue(login(LEAKED_PASSWORD).success());
        assertEquals(1, notificationCount());

        // 用户听劝了：把密码改成干净的那个（这里直接改库，等价于走完重置密码）
        jdbcTemplate.update("UPDATE t_member SET password = ? WHERE member_id = ?",
                PasswordCipher.encode(CLEAN_PASSWORD), memberId);
        redisService.delete(redisService.generateRedisKey(
                "mbr:pwd:leaked-notify:", String.valueOf(memberId)));

        assertTrue(login(CLEAN_PASSWORD).success());
        assertEquals(1, notificationCount(), "改完之后再提醒，等于告诉他「改了也没用」");
    }

    // ============================== 文案 ==============================

    @Test
    @DisplayName("🔴 文案里没有链接，而且把这件事写给用户看")
    void 文案里没有链接() {
        String content = jdbcTemplate.queryForObject("""
                SELECT content_template FROM t_notification_template
                 WHERE template_code = 'LEAKED_PASSWORD' AND status = 1
                 ORDER BY version DESC LIMIT 1
                """, String.class);

        assertTrue(content != null && !content.contains("http") && !content.contains("<a "),
                "钓鱼消息最爱模仿这类「安全提醒」—— 我们自己的里面没有链接，用户才有依据判断假的那条");
        assertTrue(content.contains("不会在任何提醒里放链接"), "而且要把这件事写给用户看");
    }

    @Test
    @DisplayName("🔴 注册那一处本来就不让设这种密码 —— 所以本类只能靠改库造数")
    void 注册设不了这种密码() {
        String another = "leak" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.REGISTER, another, freshIp(), null)).success());
        String code = readCodeFor(another, EmailCodeScene.REGISTER);

        MemberRegisterResult result = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, another, code, null, LEAKED_PASSWORD,
                "H5", freshIp(), "H5", null));

        assertEquals(RegisterFailReason.LEAKED_PASSWORD, result.reason(),
                "入口上挡住的是新密码；本类管的是入口之前就存在的那些账号");
    }

    // ============================== 工具 ==============================

    /**
     * 把这个账号的密码换成已泄露的那一个。
     *
     * <p>🔴 <b>只能直接改库</b>：注册与重置密码两处都会拦掉这种密码 ——
     * 而这正是「存量账号」的样子（他在我们加上那张表之前就把密码设成了它）。
     */
    private void makePasswordLeaked() {
        jdbcTemplate.update("UPDATE t_member SET password = ? WHERE member_id = ?",
                PasswordCipher.encode(LEAKED_PASSWORD), memberId);
    }

    private MemberAuthResult login(String password) {
        return memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.EMAIL_PASSWORD, email, password, null, "H5", freshIp(), null, true));
    }

    private int notificationCount() {
        Integer rows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_member_notification
                 WHERE member_id = ? AND template_code = 'LEAKED_PASSWORD'
                """, Integer.class, memberId);
        return rows == null ? 0 : rows;
    }

    private String readCode(EmailCodeScene scene) {
        return readCodeFor(email, scene);
    }

    private String readCodeFor(String target, EmailCodeScene scene) {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(target)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    /** 198.18.0.0/15：整段保留，不会被解析出真实归属地。理由见方案 §12.4 */
    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198." + (18 + r.nextInt(2)) + "." + r.nextInt(256) + "." + (1 + r.nextInt(254));
    }
}
