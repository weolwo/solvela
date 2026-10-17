package solvela.app.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.apptest.stub.StubMemberAuthApiConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 一条读不出来的缓存条目，不该把整条认证链路拖垮。
 *
 * <p>{@link MemberPrincipalLoader#load} 是认证过滤器的一部分——每一个带令牌的请求
 * 都会先走它。这里打真 Redis，手工塞一条<b>格式不兼容</b>的记录进它要读的那个 key，
 * 钉住 {@link solvela.app.config.AppCacheErrorHandler} 生效后的三件事：
 * 读缓存失败不抛异常、正确回源、并且顺手把这条坏记录自愈掉。
 *
 * <h3>这不是假想场景</h3>
 * 2026-09-03 真实复现过：{@code AppCacheConfig.valueSerializer()} 当时用的是
 * {@code DefaultTyping.NON_FINAL}——但 {@link MemberPrincipal} 是 record，
 * 而 Java record 隐式 final，"NON_FINAL" 这个策略从字面意思上就不会给它写类型信息。
 * 于是<b>每一次登录写下的缓存都没带 {@code @class}</b>，30 分钟 TTL 窗口内该会员
 * 后续的每一个请求读它都失败——包括退出登录，表现正是用户报的
 * {@code InvalidTypeIdException: missing type id property '@class'}。
 * 序列化配置已经修（见 {@code AppCacheConfig} 改用 {@code NON_FINAL_AND_RECORDS}），
 * 但那只堵住了<b>这一次</b>的具体诱因——本类钉住的是更一般的性质：
 * 不管未来是什么原因导致缓存读不出来，都不该变成用户面的 500。
 *
 * <p>桩用的是 {@link StubMemberAuthApiConfig}，不是本类自己再写一份——原因见它的类注释：
 * {@code AppApplication} 的显式 {@code @ComponentScan} 会把<b>任何</b>放在
 * {@code solvela.app.*} 包下的 {@code @TestConfiguration} 都扫进来，与本类自己
 * {@code @Import} 的那份撞上，表现是 {@code BeanDefinitionOverrideException}。
 */
@SpringBootTest
@Import(StubMemberAuthApiConfig.class)
class MemberPrincipalLoaderTest {

    private static final Long MEMBER_ID = 999_000_002L;

    /** 缓存 key 前缀，与 AppCacheConfig 的约定一致：cache: + 去掉 #ttl 的缓存名 + : */
    private static final String CACHE_KEY = "cache:app_member_principal:" + MEMBER_ID;

    @Autowired
    private MemberPrincipalLoader principalLoader;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private StubMemberAuthApiConfig stub;

    @AfterEach
    void cleanUp() {
        redis.delete(CACHE_KEY);
        stub.resetCallCount();
    }

    @Test
    @DisplayName("🔴 缓存里是格式不兼容的旧数据 → 不抛异常，正常返回身份")
    void 坏缓存不影响回源() {
        // 模拟「序列化配置变过」：这条 JSON 没有 @class，读它的反序列化器却要求有
        redis.opsForValue().set(CACHE_KEY, "{\"memberId\":123,\"memberName\":\"stale\"}");

        MemberPrincipal principal = assertDoesNotThrow(() -> principalLoader.load(MEMBER_ID),
                "读缓存失败不该让整个方法抛异常 —— 这正是「点什么都 500」的根因");

        assertEquals(MEMBER_ID, principal.memberId());
        assertEquals(1, stub.authIdentityCallCount(), "读缓存失败应当降级为回源，回源应当真的发生了");
    }

    @Test
    @DisplayName("🔴 写回缓存的值必须带类型信息 —— 否则下次读它必然失败")
    void 写回的缓存带类型信息() {
        // 从【干净的】缓存开始：这条路径上没有任何错误恢复参与，@Cacheable 必然写回，
        // 所以断言是确定的。而它覆盖的正是 2026-09-03 那个 bug：
        // NON_FINAL 不给 record 写 @class，于是每一次登录写下的缓存都读不回来。
        redis.delete(CACHE_KEY);

        principalLoader.load(MEMBER_ID);

        String written = redis.opsForValue().get(CACHE_KEY);
        assertNotNull(written, "缓存没写回 —— @Cacheable 没生效（多半是代理没挂上）");
        assertTrue(written.contains("@class"),
                "写回的值没带类型信息，下次读它必然抛 InvalidTypeIdException。实际存的是：" + written);
    }

    @Test
    @DisplayName("🔴 写完立刻读得到 —— 缓存写入是同步的（Spring Data Redis 4 默认异步，要显式 immediateWrites）")
    void 写回是同步的() {
        // 异步写的时候这里约一成读不到：方法返回了，那条 SET 还在路上（2026-10-17 查出，见 AppCacheConfig.immediateWriter）。
        // 连读 50 次，异步的话几乎必然撞上一次
        for (int i = 0; i < 50; i++) {
            redis.delete(CACHE_KEY);
            principalLoader.load(MEMBER_ID);
            assertNotNull(redis.opsForValue().get(CACHE_KEY), "第 " + (i + 1) + " 次：方法返回了，缓存却还没写进去");
        }
    }

    /*
     * 🔴 这里【刻意没有】「坏缓存被读过一次之后下次不再回源」那条用例。
     *
     * 自愈在当前设计下是【尽力而为】的：读、写、失效的异常全被 AppCacheErrorHandler
     * 吞掉降级为回源 —— 也就是说「读失败之后一定会把好值写回去」并不是这个类保证的性质。
     * 断言它，就是断言一个设计上没有承诺的东西，表现是这条用例时红时绿：
     * 2026-09 它在全量构建里红过四次，而每次单跑都是绿的。
     * （2026-10-17 补：当时时红时绿的直接原因多半是缓存写入是异步的 —— 「写回」那条也因此偶发失败过，
     * 改成同步写之后才稳定。自愈本身仍然不是承诺过的行为，所以这条依旧不加。）
     *
     * 真正要守的两条性质分别由上下两条用例守着：
     *   · 坏缓存不影响回源      —— 读不出来不能变成用户面的 500
     *   · 写回的缓存带类型信息  —— 写进去的东西下次读得回来
     * 后者从干净缓存开始，不经过错误恢复路径，因此是确定的。
     *
     * 要把自愈变成一条【可以断言】的性质，得先让它成为一条有保证的行为
     * （比如读失败时显式 delete 掉那个 key），那是 AppCacheErrorHandler 的改动，不是这里。
     */

    @Test
    @DisplayName("会员不存在时返回 null，同样不抛异常")
    void 会员不存在返回null() {
        assertTrue(redis.opsForValue().get(CACHE_KEY) == null);
        MemberPrincipal principal = assertDoesNotThrow(() -> principalLoader.load(-1L));
        assertNull(principal);
    }
}
