package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.domain.PageResult;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.enums.SecurityListTypeEnum;
import solvela.member.SecurityList;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.domain.SecurityListAddCmd;
import solvela.member.risk.domain.query.SecurityListQuery;
import solvela.member.risk.security.SecurityListAdminService;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台的名单增删查。连真库 —— 这里要验的恰恰是「写进去的东西读得出来」。
 *
 * <h3>最要紧的几条</h3>
 * <ul>
 *   <li>🔴 <b>加完立刻生效</b>：写库之后要重载缓存，否则客服加了黑名单、过了 30 秒才生效，
 *       而他已经在页面上看到那一行了；</li>
 *   <li>🔴 <b>值解不开就拒绝</b>：一条格式写错的 IP 黑名单在列表里好端端躺着，却永远不命中；</li>
 *   <li>🔴 <b>撤销不删行</b>，而且记下谁解的、为什么；</li>
 *   <li>语义校验在域里：灰名单必须有分、会员维度只许白名单且必须填到期。</li>
 * </ul>
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(properties = "solvela.member.risk.cache-refresh=0s")
class SecurityListAdminTest {

    @Autowired
    private SecurityListAdminService listAdminService;

    @Autowired
    private RiskListCache listCache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本类造的名单都用这个前缀的 IP，清理时一把删掉 */
    private static final String IP_PREFIX = "198.19.";

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_security_list WHERE list_value LIKE ?", IP_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_security_list WHERE reason LIKE '用例：%'");
        listCache.reload();
    }

    @Test
    @DisplayName("🔴 加一条 IP 黑名单 → 缓存立刻命中。不重载的话客服加完要等 30 秒才生效")
    void 加完立刻生效() {
        String ip = freshIp();
        assertFalse(listCache.matchIp(ip).black(), "造数：一开始不该命中");

        listAdminService.addList(add(SecurityListTypeEnum.BLACK,
                SecurityListDimensionEnum.IP, ip, null, null));

        assertTrue(listCache.matchIp(ip).black());
    }

    @Test
    @DisplayName("IP 段（CIDR）命中段内任意地址")
    void 网段命中() {
        listAdminService.addList(add(SecurityListTypeEnum.BLACK,
                SecurityListDimensionEnum.IP_RANGE, IP_PREFIX + "0.0/16", null, null));

        assertTrue(listCache.matchIp(IP_PREFIX + "7.7").black());
    }

    @Test
    @DisplayName("🔴 格式解不开直接拒绝 —— 否则列表里会躺着一条永远不命中的名单")
    void 格式不对就拒绝() {
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                add(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IP, "不是IP", null, null)));
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                add(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IP_RANGE,
                        "203.0.113.0/99", null, null)));
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                add(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IDENTITY,
                        "短了点", null, null)));
    }

    @Test
    @DisplayName("灰名单必须给分；黑白名单不许给分")
    void 分数的规矩() {
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                        add(SecurityListTypeEnum.GREY, SecurityListDimensionEnum.IP, freshIp(), null, null)),
                "不给分的灰名单命中了也什么都不会发生");
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                        add(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IP, freshIp(), 30, null)),
                "黑名单直接拒绝，分数在这一档上没有意义");
    }

    @Test
    @DisplayName("🔴 会员维度只许白名单，而且必须填到期 —— 长期白名单会在没人记得时继续生效")
    void 会员维度的规矩() {
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                        add(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.MEMBER, "900001", null,
                                LocalDateTime.now().plusDays(3))),
                "封号请用会员状态 —— 名单里再放一份就有了两个答案");
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                        add(SecurityListTypeEnum.WHITE, SecurityListDimensionEnum.MEMBER, "900001", null, null)),
                "会员白名单必须填到期");
        assertNotNull(listAdminService.addList(
                add(SecurityListTypeEnum.WHITE, SecurityListDimensionEnum.MEMBER, "900001", null,
                        LocalDateTime.now().plusDays(3))));
    }

    @Test
    @DisplayName("原因必填 —— 那句话是给下一个看到它的人的")
    void 原因必填() {
        SecurityListAddCmd cmd = add(SecurityListTypeEnum.BLACK,
                SecurityListDimensionEnum.IP, freshIp(), null, null);
        cmd.setReason("  ");
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(cmd));
    }

    @Test
    @DisplayName("到期时间不能是过去 —— 那条名单加完立刻就失效了")
    void 到期不能是过去() {
        assertThrows(IllegalArgumentException.class, () -> listAdminService.addList(
                add(SecurityListTypeEnum.BLACK, SecurityListDimensionEnum.IP, freshIp(), null,
                        LocalDateTime.now().minusDays(1))));
    }

    @Test
    @DisplayName("🔴 撤销不删行：状态改成已撤销，并记下谁解的、为什么；缓存立刻不再命中")
    void 撤销留痕() {
        String ip = freshIp();
        SecurityList row = listAdminService.addList(add(SecurityListTypeEnum.BLACK,
                SecurityListDimensionEnum.IP, ip, null, null));

        assertTrue(listAdminService.revokeList(row.getId(), "客服小王", "误封，公司出口"));

        assertFalse(listCache.matchIp(ip).black());
        SecurityListQuery query = new SecurityListQuery();
        query.setPageNum(1L);
        query.setPageSize(10L);
        query.setListValue(ip);
        query.setStatus(SecurityListStatusEnum.REVOKED);
        PageResult<SecurityList> page = listAdminService.queryListPage(query);
        assertEquals(1, page.list().size(), "🔴 删行的话，下一个人看到的是「这个 IP 从来没被封过」");
        assertEquals("客服小王", page.list().getFirst().getRevokeOperator());
        assertEquals("误封，公司出口", page.list().getFirst().getRevokeReason());
    }

    @Test
    @DisplayName("连点两次撤销：第二次返回 false，不覆盖第一次记的人")
    void 撤销幂等() {
        SecurityList row = listAdminService.addList(add(SecurityListTypeEnum.BLACK,
                SecurityListDimensionEnum.IP, freshIp(), null, null));

        assertTrue(listAdminService.revokeList(row.getId(), "客服小王", "误封"));
        assertFalse(listAdminService.revokeList(row.getId(), "客服小李", "又点了一次"));

        String operator = jdbcTemplate.queryForObject(
                "SELECT revoke_operator FROM t_security_list WHERE id = ?", String.class, row.getId());
        assertEquals("客服小王", operator);
    }

    @Test
    @DisplayName("撤销必须写原因")
    void 撤销要写原因() {
        assertThrows(IllegalArgumentException.class, () -> listAdminService.revokeList(1L, "客服", " "));
    }

    @Test
    @DisplayName("列表默认只看有效的：客服日常要的是「现在谁被封着」")
    void 默认只看有效() {
        String ip = freshIp();
        SecurityList row = listAdminService.addList(add(SecurityListTypeEnum.BLACK,
                SecurityListDimensionEnum.IP, ip, null, null));
        listAdminService.revokeList(row.getId(), "客服", "误封");

        SecurityListQuery query = new SecurityListQuery();
        query.setPageNum(1L);
        query.setPageSize(10L);
        query.setListValue(ip);
        assertEquals(0, listAdminService.queryListPage(query).list().size());
    }

    // ============================== 工具 ==============================

    private static SecurityListAddCmd add(SecurityListTypeEnum type,
                                            SecurityListDimensionEnum dimension,
                                            String value, Integer score, LocalDateTime expireTime) {
        SecurityListAddCmd cmd = new SecurityListAddCmd();
        cmd.setListType(type);
        cmd.setDimension(dimension);
        cmd.setListValue(value);
        cmd.setScore(score);
        cmd.setReason("用例：" + type + " " + dimension);
        cmd.setExpireTime(expireTime);
        cmd.setOperator("用例");
        return cmd;
    }

    /** 198.19.0.0/16 在 198.18.0.0/15 的保留段里，不会撞上真实归属地 */
    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return IP_PREFIX + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }
}
