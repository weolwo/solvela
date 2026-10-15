package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.mail.MailService;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberRiskLabelEnum;
import solvela.enums.MemberRiskLabelSourceEnum;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.SecurityPromptAnswerCmd;
import solvela.member.api.SecurityPromptView;
import solvela.member.api.StepUpCmd;
import solvela.member.auth.MemberAuthService;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.risk.SecurityPromptService;
import solvela.member.util.MemberEmailUtil;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 老设备上问「刚才是你吗」（方案 §8.1）。连真库、真 Redis。
 *
 * <h3>最要紧的两条，都是「在哪问」</h3>
 * <ul>
 *   <li>🔴 <b>绝不在被问的那台设备上问</b> —— 盗号者就在那儿，他会点「是我」，
 *       而那条「确认本人」会系统性地污染误伤率，让我们把阈值越调越松；</li>
 *   <li>🔴 <b>只在受信任的老设备上问，答题也要在那儿</b> —— 只拦列表那一步的话，
 *       盗号者拿到一个单号（比如从主人的截图里）就能直接调答题接口点「是我」。</li>
 * </ul>
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class SecurityPromptTest {

    /** 主人的老设备：下面会给它造一条 30 天前的成功登录 */
    private static final String OLD_DEVICE = "e5".repeat(16);

    /** 被问的那台新设备 */
    private static final String NEW_DEVICE = "f6".repeat(16);

    @MockitoBean
    private MailService mailService;

    @Autowired
    private SecurityPromptService promptService;

    @Autowired
    private MemberOperationLimitService operationLimitService;

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
        email = "sv" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.REGISTER, email, freshIp(), null)).success());
        MemberRegisterResult result = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, readCode(), null, null,
                "H5", freshIp(), "H5", null));
        assertTrue(result.success(), "造数：注册应当成功，实际 " + result.reason());
        memberId = result.identity().memberId();
        // 主人的老设备：30 天前成功登录过一次 → 受信任
        jdbcTemplate.update("""
                INSERT INTO t_member_login_log (member_id, device_id, status, create_time)
                VALUES (?, ?, ?, NOW() - INTERVAL 30 DAY)
                """, memberId, OLD_DEVICE, LoginLogResultEnum.LOGIN_SUCCESS.getValue());
    }

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_risk_event WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_operation_limit WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            redisService.delete(
                    redisService.generateRedisKey("mbr:sec:prompt:", String.valueOf(memberId)),
                    redisService.generateRedisKey("mbr:trust:v:", String.valueOf(memberId)),
                    redisService.generateRedisKey("mbr:trust:since:", String.valueOf(memberId)));
            memberId = null;
        }
    }

    @Test
    @DisplayName("老设备上问得到；🔴 被问的那台新设备上问不到 —— 盗号者会点「是我」")
    void 只在别的设备上问() {
        promptService.onNewDeviceLogin(memberId, NEW_DEVICE, "APP", "深圳", now());

        List<SecurityPromptView> onOld = promptService.list(cmd(OLD_DEVICE));
        assertEquals(1, onOld.size());
        assertEquals("APP", onOld.getFirst().deviceType());
        assertEquals("深圳", onOld.getFirst().location());

        assertTrue(promptService.list(cmd(NEW_DEVICE)).isEmpty(),
                "🔴 在新设备本身上问，那条「确认本人」会污染整个误伤率");
    }

    @Test
    @DisplayName("🔴 不受信任的设备上一条也看不到 —— 盗号者的第二台新设备也不该能替主人回答")
    void 不受信任的设备看不到() {
        promptService.onNewDeviceLogin(memberId, NEW_DEVICE, "APP", "深圳", now());

        assertTrue(promptService.list(cmd("aa".repeat(16))).isEmpty());
    }

    @Test
    @DisplayName("没带设备号时返回空列表，不是报错 —— 客户端冷启动都会调它")
    void 没有设备号() {
        promptService.onNewDeviceLogin(memberId, NEW_DEVICE, "APP", "深圳", now());

        assertTrue(promptService.list(cmd(null)).isEmpty());
    }

    @Test
    @DisplayName("答「是我」→ 流水标成确认本人（来源：老设备确认），而且这条提问消失")
    void 答是我() {
        long at = now();
        long eventId = insertRiskEvent(NEW_DEVICE);
        promptService.onNewDeviceLogin(memberId, NEW_DEVICE, "APP", "深圳", at);
        String promptId = promptService.list(cmd(OLD_DEVICE)).getFirst().promptId();

        assertTrue(promptService.answer(answer(promptId, true, OLD_DEVICE)));

        assertEquals(MemberRiskLabelEnum.OWNER.getValue(), label(eventId));
        assertEquals(MemberRiskLabelSourceEnum.OLD_DEVICE_CONFIRM.getValue(), labelSource(eventId),
                "🔴 来源必须分开记：老设备确认比用户主动举报弱，混在一起算会低估漏过率");
        assertTrue(promptService.list(cmd(OLD_DEVICE)).isEmpty(), "答过的不该再问");
    }

    @Test
    @DisplayName("🔴 答「不是我」= 「这不是我」的全套：冻结资产出口 + 流水标成确认被盗")
    void 答不是我() {
        long at = now();
        long eventId = insertRiskEvent(NEW_DEVICE);
        promptService.onNewDeviceLogin(memberId, NEW_DEVICE, "APP", "深圳", at);
        String promptId = promptService.list(cmd(OLD_DEVICE)).getFirst().promptId();

        assertTrue(promptService.answer(answer(promptId, false, OLD_DEVICE)));

        assertTrue(operationLimitService.isFrozen(memberId, MemberOperationTypeEnum.ASSET_OUT),
                "两个入口的后果必须完全一样 —— 不一样的话，用户从哪儿点的决定了他被保护到什么程度");
        assertEquals(MemberRiskLabelEnum.STOLEN.getValue(), label(eventId));
        assertEquals(MemberRiskLabelSourceEnum.USER_REPORT.getValue(), labelSource(eventId));
    }

    @Test
    @DisplayName("🔴 在不受信任的设备上答题一律不受理 —— 否则拿到单号就能替主人点「是我」")
    void 不受信任的设备不能答() {
        promptService.onNewDeviceLogin(memberId, NEW_DEVICE, "APP", "深圳", now());
        String promptId = promptService.list(cmd(OLD_DEVICE)).getFirst().promptId();

        assertFalse(promptService.answer(answer(promptId, true, NEW_DEVICE)));
        assertEquals(1, promptService.list(cmd(OLD_DEVICE)).size(), "没受理，这条还该在");
    }

    @Test
    @DisplayName("同一个单号答两次：第二次不受理（副作用不该执行两遍）")
    void 答两次() {
        promptService.onNewDeviceLogin(memberId, NEW_DEVICE, "APP", "深圳", now());
        String promptId = promptService.list(cmd(OLD_DEVICE)).getFirst().promptId();

        assertTrue(promptService.answer(answer(promptId, true, OLD_DEVICE)));
        assertFalse(promptService.answer(answer(promptId, true, OLD_DEVICE)));
    }

    @Test
    @DisplayName("编一个单号不受理")
    void 单号是编的() {
        assertFalse(promptService.answer(answer("sp_whatever", false, OLD_DEVICE)));
        assertFalse(operationLimitService.isFrozen(memberId, MemberOperationTypeEnum.ASSET_OUT),
                "🔴 要是受理了，任何人试几个单号就能把别人的兑换冻住");
    }

    @Test
    @DisplayName("待确认最多挂 5 条 —— 被撞库刷出几十条提问等于把弹窗变成骚扰")
    void 上限() {
        for (int i = 0; i < 8; i++) {
            promptService.onNewDeviceLogin(memberId, "b" + i + "b".repeat(30), "APP", "深圳", now());
        }

        assertEquals(5, promptService.list(cmd(OLD_DEVICE)).size());
    }

    // ============================== 工具 ==============================

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private StepUpCmd cmd(String deviceId) {
        return StepUpCmd.of(memberId, deviceId, freshIp());
    }

    private SecurityPromptAnswerCmd answer(String promptId, boolean isMe, String deviceId) {
        return new SecurityPromptAnswerCmd(memberId, promptId, isMe, deviceId, freshIp());
    }

    private long insertRiskEvent(String deviceId) {
        jdbcTemplate.update("""
                INSERT INTO t_member_risk_event
                  (event_type, member_id, device_id, decision, shadow_decision, outcome, create_time)
                VALUES (1, ?, ?, 1, 1, 1, NOW())
                """, memberId, deviceId);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private Integer label(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT label FROM t_member_risk_event WHERE id = ?", Integer.class, id);
    }

    private Integer labelSource(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT label_source FROM t_member_risk_event WHERE id = ?", Integer.class, id);
    }

    private String readCode() {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:REGISTER:" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }
}
