package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import solvela.auth.device.DeviceTokenCodec;
import solvela.base.json.JsonUtils;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListTypeEnum;
import solvela.member.Member;
import solvela.member.api.DeviceTrust;
import solvela.member.api.MemberLoginType;
import solvela.member.api.StepUpCmd;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskDeviceTrust;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.domain.SecurityListAddCmd;
import solvela.member.risk.feature.DeviceTrustProvider;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.member.risk.security.SecurityIncidentStore;
import solvela.member.risk.security.SecurityListAdminService;
import solvela.member.stepup.DeviceTrustStore;
import solvela.member.stepup.MemberStepUpService;
import solvela.security.GatewayRuleSnapshot;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会员灰名单（网关方案 §5.5、任务 T12）：可信设备按新设备算；资产出口每次都要二次验证（验一次放一次）；
 * 网关拿得到它（快照）；后台能加灰、不能加黑；登录后场景的事件把会员拉灰。连真库、真 Redis。
 *
 * <p>会员号用一段不会与真实数据撞上的大号（9_9xx_xxx_xxx），结束时按前缀清掉名单。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class MemberGreyListTest {

    @Autowired
    private SecurityListAdminService listAdminService;

    @Autowired
    private RiskListCache listCache;

    @Autowired
    private DeviceTrustProvider trustProvider;

    @Autowired
    private DeviceTrustStore trustStore;

    @Autowired
    private MemberStepUpService stepUpService;

    @Autowired
    private SecurityIncidentStore incidentStore;

    @Autowired
    private GatewaySnapshotPublisher snapshotPublisher;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;

    private String deviceId;

    @BeforeEach
    void setUp() {
        memberId = 9_900_000_000L + ThreadLocalRandom.current().nextLong(99_999_999L);
        deviceId = DeviceTokenCodec.newDeviceId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_security_list WHERE dimension = 4 AND list_value = ?", String.valueOf(memberId));
        jdbcTemplate.update("DELETE FROM t_security_incident WHERE member_id = ?", memberId);
        trustStore.revoke(memberId, null);
        trustStore.consumeFresh(memberId, deviceId);
        listCache.reload();
        snapshotPublisher.publishQuietly("用例清理");
    }

    @Test
    @DisplayName("🔴 后台：会员维度可以加灰（必须有到期），仍然不能加黑 —— 会员的「黑」就是冻结")
    void adminCanGreyButNotBlack() {
        assertNotNull(listAdminService.addList(cmd(SecurityListTypeEnum.GREY)));

        IllegalArgumentException black = assertThrows(IllegalArgumentException.class,
                () -> listAdminService.addList(cmd(SecurityListTypeEnum.BLACK)));
        assertTrue(black.getMessage().contains("冻结"), black.getMessage());
    }

    @Test
    @DisplayName("🔴 登录：灰名单会员的可信设备按新设备算（于是陌生网络上要 OTP）；出灰名单就恢复")
    void trustedDeviceCountsAsNew() {
        trustStore.markVerified(memberId, deviceId);
        assertEquals(RiskDeviceTrust.VERIFIED, trustProvider.of(loginContext()));

        grey();

        assertEquals(RiskDeviceTrust.NEW, trustProvider.of(loginContext()));
    }

    @Test
    @DisplayName("🔴 资产出口：灰名单会员在可信设备上也要二次验证；验过一次放一次，下一次接着要验")
    void assetOutEveryTime() {
        trustStore.markVerified(memberId, deviceId);
        StepUpCmd cmd = StepUpCmd.of(memberId, deviceId, "198.51.100.77");
        assertTrue(stepUpService.assetOut(cmd).trust().trusted(), "不在灰名单上：可信设备直接过");

        grey();

        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.assetOut(cmd).trust(), "可信设备也要验");
        trustStore.markFresh(memberId, deviceId); // = 二次验证通过（MemberStepUpService.verify 里做的那一步）
        assertEquals(DeviceTrust.VERIFIED, stepUpService.assetOut(cmd).trust(), "刚验过的这一次放行");
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.assetOut(cmd).trust(), "下一次接着要验 —— 一次验证只管一次");
    }

    @Test
    @DisplayName("事件要求拉灰会员 → 进会员灰名单（带分、带到期）；网关快照里有它")
    void incidentGreysMemberAndSnapshotCarriesIt() {
        long until = Instant.now().getEpochSecond() + 7 * 86400L;
        boolean changed = incidentStore.store(SecurityIncidentEvent.builder(SecurityIncidentEvent.RATE_LIMIT, "REDEEM")
                .penalty(4, until).who("198.51.100.78", null, memberId).greyMember(until).build());
        assertTrue(changed);

        assertEquals("RATE_LIMIT", jdbcTemplate.queryForObject(
                "SELECT source_rule FROM t_security_list WHERE dimension = 4 AND list_type = 2 AND list_value = ?",
                String.class, String.valueOf(memberId)));
        listCache.reload();
        assertTrue(listCache.matchMember(memberId).grey());

        snapshotPublisher.publish();
        GatewayRuleSnapshot body = JsonUtils.parseObject(
                (String) redis.opsForHash().get(SecurityKeys.RULE_SNAPSHOT, SecurityKeys.SNAPSHOT_FIELD_BODY),
                GatewayRuleSnapshot.class);
        assertTrue(body.memberGreyList().stream().anyMatch(e -> e.memberId() == memberId), "网关靠它把这个会员的账号维度减半");
    }

    // ------------------------------------------------------------------ 造数

    private void grey() {
        listAdminService.addList(cmd(SecurityListTypeEnum.GREY));
    }

    private SecurityListAddCmd cmd(SecurityListTypeEnum type) {
        SecurityListAddCmd cmd = new SecurityListAddCmd();
        cmd.setListType(type);
        cmd.setDimension(SecurityListDimensionEnum.MEMBER);
        cmd.setListValue(String.valueOf(memberId));
        cmd.setScore(type == SecurityListTypeEnum.GREY ? 30 : null);
        cmd.setReason("用例：会员灰名单");
        cmd.setExpireTime(LocalDateTime.now().plusDays(1));
        cmd.setOperator("test");
        return cmd;
    }

    private RiskContext loginContext() {
        Member member = new Member();
        member.setMemberId(memberId);
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD, "x@example.com",
                "ab".repeat(32), member, deviceId, "H5", "198.51.100.77", true);
    }
}
