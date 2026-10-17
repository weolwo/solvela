package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import solvela.auth.device.DeviceTokenCodec;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.MemberStatusEnum;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLoginType;
import solvela.member.domain.dto.MemberDTO;
import solvela.member.domain.query.MemberQuery;
import solvela.member.id.MemberIdAllocator;
import solvela.member.auth.MemberAuthService;
import solvela.member.register.MemberRegisterDao;
import solvela.member.risk.security.CanaryAccountService;
import solvela.member.risk.security.SecurityIncidentQueue;
import solvela.member.service.MemberService;
import solvela.member.util.MemberEmailUtil;
import solvela.security.IpKeys;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 蜜罐账号（网关方案 §5.1、任务 T11）：对它的登录与真实账号输错密码<b>逐字节相同</b>，背后罚了 IP 与设备、投了事件；
 * 它不出现在会员列表里。连真库、真 Redis；事件队列换成 mock，不往本机真队列里投。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
@ExtendWith(RelaxedRiskRules.class)
@TestPropertySource(properties = "solvela.member.risk.cache-refresh=0s")
class CanaryAccountTest {

    @MockitoBean
    private SecurityIncidentQueue incidents;

    @Autowired
    private CanaryAccountService canaryAccountService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRegisterDao memberRegisterDao;

    @Autowired
    private MemberIdAllocator memberIdAllocator;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private PiiCipher piiCipher;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> members = new ArrayList<>();

    private final List<String> keys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : members) {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", id);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", id);
        }
        redis.delete(keys);
    }

    @Test
    @DisplayName("🔴 对蜜罐账号的登录与真实账号输错密码逐字节相同；背后 IP 与设备已罚、事件已投（类型 2，拉黑到处罚到期）")
    void canaryLooksLikeWrongPassword() {
        String realEmail = freshEmail();
        realMember(realEmail, "RealPass2026");
        String canaryEmail = freshEmail();
        members.add(canaryAccountService.create(canaryEmail, "test"));
        String realIp = freshIp(), canaryIp = freshIp();
        String canaryDevice = DeviceTokenCodec.newDeviceId();
        String ipPenalty = track(SecurityKeys.securityPenaltyIp(IpKeys.ip(canaryIp)));
        String devicePenalty = track(SecurityKeys.securityPenaltyDevice(canaryDevice));
        track(SecurityKeys.securityPenaltyIp(IpKeys.ip(realIp)));

        MemberAuthResult real = login(realEmail, "Guess2026!", realIp, DeviceTokenCodec.newDeviceId());
        MemberAuthResult canary = login(canaryEmail, "Guess2026!", canaryIp, canaryDevice);

        assertEquals(real, canary, "回答哪怕差一个字段，对方就知道这个号不一样");
        assertFalse(canary.success());
        assertNotNull(redis.opsForValue().get(ipPenalty), "IP 罚了：下一个请求在网关就结束");
        assertNotNull(redis.opsForValue().get(devicePenalty));
        assertNull(redis.opsForValue().get(SecurityKeys.securityPenaltyIp(IpKeys.ip(realIp))), "真实账号输错密码不罚");
        String hash = piiHasher.hash(MemberEmailUtil.normalize(canaryEmail));
        verify(incidents).publish(argThat(e -> e.incidentType() == SecurityIncidentEvent.CANARY
                && "CANARY_ACCOUNT".equals(e.ruleCode())
                && e.action() == SecurityIncidentEvent.ACTION_BLACK
                && canaryIp.equals(e.clientIp())
                && canaryDevice.equals(e.deviceId())
                && hash.equalsIgnoreCase(e.identityHashHex())));
    }

    @Test
    @DisplayName("真实账号的登录不触发蜜罐")
    void realAccountNotTrapped() {
        String email = freshEmail();
        realMember(email, "RealPass2026");

        assertTrue(login(email, "RealPass2026", freshIp(), DeviceTokenCodec.newDeviceId()).success());

        verify(incidents, never()).publish(any());
    }

    @Test
    @DisplayName("🔴 蜜罐账号不出现在会员列表里（显式按来源 CANARY 筛才看得到）；已经是会员的邮箱不能建成蜜罐")
    void excludedFromListsAndNoHijack() {
        long canaryId = canaryAccountService.create(freshEmail(), "test");
        members.add(canaryId);
        MemberQuery query = new MemberQuery();
        query.setMemberId(canaryId);
        query.setPageNum(1L);
        query.setPageSize(10L);

        assertTrue(memberService.queryPage(query).list().isEmpty(), "默认列表里不该有它");
        query.setRegisterSource(CanaryAccountService.REGISTER_SOURCE);
        List<MemberDTO> found = memberService.queryPage(query).list();
        assertEquals(1, found.size());

        String realEmail = freshEmail();
        realMember(realEmail, "RealPass2026");
        assertThrows(IllegalArgumentException.class, () -> canaryAccountService.create(realEmail, "test"),
                "把真人的号变成蜜罐，等于让他一登录就被封一天");
    }

    // ------------------------------------------------------------------ 造数

    private MemberAuthResult login(String email, String password, String ip, String deviceId) {
        return memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_PASSWORD, email, password, null,
                "H5", ip, deviceId, true));
    }

    /** 一个自主注册来源的真实会员（直接写库：这里要的只是「有密码的普通账号」） */
    private void realMember(String email, String password) {
        long id = memberIdAllocator.nextMemberId();
        String normalized = MemberEmailUtil.normalize(email);
        memberRegisterDao.insertMember(id, "sv" + id, "会员" + id, 0, null, null, piiCipher.encrypt(normalized),
                piiHasher.hash(normalized), PasswordCipher.encode(password), MemberStatusEnum.NORMAL.getValue(), "H5",
                null);
        members.add(id);
    }

    private String track(String key) {
        keys.add(key);
        return key;
    }

    private static String freshEmail() {
        return "canary" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }
}
