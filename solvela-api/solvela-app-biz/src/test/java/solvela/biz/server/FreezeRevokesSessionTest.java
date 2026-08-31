package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.MemberStatusEnum;
import solvela.member.auth.MemberAuthService;
import solvela.member.service.MemberService;
import solvela.member.session.MemberAccessToken;
import solvela.member.session.MemberTokenStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 冻结会员必须<b>当场</b>吊销他的全部会话。
 *
 * <h3>这个测试为什么必须存在</h3>
 * 2026-08-30 之前，{@code MemberPrincipalLoader} 的类注释写着「冻结时会 revokeAll 掉全部令牌」，
 * 而 {@code MemberTokenStore.revokeAll} 在全仓<b>只有测试在调</b> —— 那句注释描述的是一个
 * 不存在的机制。实际后果是被冻结的会员还能正常用最多 30 分钟（身份缓存 TTL），
 * 风控封掉的刷子账号还有半小时可以继续刷。
 *
 * <p>教训不是「有人忘了实现」，而是<b>写下了一个没有测试盯着的安全承诺</b>。
 * 所以这条链路必须有一个会失败的测试 —— 它坏掉的时候不会有任何报错，
 * 接口照常响应，只是封不住人。
 *
 * <p>⚠️ 本测试 2026-08-30 从网关模块搬到这里：冻结发生在会员服务，
 * 而网关已经没有 {@code MemberService}，也没有数据源了。
 * <b>注入的是 {@code MemberAuthService} 而不是 {@code MemberAuthApi}</b> ——
 * 本进程里那个接口有两个 bean（HTTP 薄壳与实现），按类型注入是歧义的。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class FreezeRevokesSessionTest {

    private static final long MEMBER_ID = 9999999998L;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberTokenStore tokenStore;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO t_member (member_id, member_name, nickname, status)
                VALUES (?, ?, ?, 1)
                """, MEMBER_ID, "ZzFreezeRevoke", "冻结吊销验收");
    }

    @AfterEach
    void cleanUp() {
        tokenStore.revokeAll(MEMBER_ID);
        jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", MEMBER_ID);
    }

    @Test
    @DisplayName("🔴 冻结之后，此前签发的令牌立刻失效")
    void 冻结当场吊销全部会话() {
        MemberAccessToken phone = tokenStore.issue(MEMBER_ID);
        MemberAccessToken pad = tokenStore.issue(MEMBER_ID);
        // 前提确认：不先证明令牌本来是有效的，下面的断言就可能是空过
        assertEquals(MEMBER_ID, tokenStore.resolve(phone.value()), "前提不成立：令牌一开始就解析不出会员");
        assertEquals(MEMBER_ID, tokenStore.resolve(pad.value()));

        memberService.updateStatus(MEMBER_ID, MemberStatusEnum.FROZEN, "acceptance-test");

        assertNull(tokenStore.resolve(phone.value()),
                "冻结后令牌仍然有效 —— 被封的账号还能继续用，这正是本测试要防的那件事");
        assertNull(tokenStore.resolve(pad.value()), "多设备的会话必须一起吊销，只吊销一个等于没吊销");
    }

    @Test
    @DisplayName("冻结后身份也取不到了 —— 令牌与身份两道都得断")
    void 冻结后取不到可用身份() {
        assertNotNull(memberAuthService.getAuthIdentity(MEMBER_ID), "前提不成立：正常会员就该取得到身份");

        memberService.updateStatus(MEMBER_ID, MemberStatusEnum.FROZEN, "acceptance-test");

        assertNull(memberAuthService.getAuthIdentity(MEMBER_ID),
                "「什么算一个可用身份」由会员域回答，冻结的会员不该再算");
    }

    @Test
    @DisplayName("解冻不恢复旧会话 —— 用户重新登录，这是刻意的")
    void 解冻不恢复旧会话() {
        MemberAccessToken token = tokenStore.issue(MEMBER_ID);
        memberService.updateStatus(MEMBER_ID, MemberStatusEnum.FROZEN, "acceptance-test");

        memberService.updateStatus(MEMBER_ID, MemberStatusEnum.NORMAL, "acceptance-test");

        assertNull(tokenStore.resolve(token.value()),
                "被封期间的那批会话很可能正是导致被封的原因，解冻不该把它们放回来");
        // 但新登录必须能用
        MemberAccessToken fresh = tokenStore.issue(MEMBER_ID);
        assertEquals(MEMBER_ID, tokenStore.resolve(fresh.value()), "解冻后应当能重新签发可用的令牌");
    }
}
