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
    @DisplayName("坏缓存被读过一次之后，应当自愈：下一次不再回源")
    void 读取后自我修复() {
        // 纯 ASCII：这条只是要让反序列化失败，跟内容语言无关，避免这个字面量本身
        // 被源文件编码问题连累（曾经真的因为这个多绕了一圈弯路）
        redis.opsForValue().set(CACHE_KEY, "not-a-valid-cache-record");

        principalLoader.load(MEMBER_ID);
        assertEquals(1, stub.authIdentityCallCount());

        // 第一次读失败 -> 回源 -> Spring Cache 用正确的序列化器把结果 PUT 回去，
        // 坏记录被覆盖。第二次应当直接命中缓存，不再回源
        principalLoader.load(MEMBER_ID);
        assertEquals(1, stub.authIdentityCallCount(),
                "第二次调用又回源了，说明坏缓存没有被自愈覆盖掉，PUT 失败也被吞掉了");
    }

    @Test
    @DisplayName("会员不存在时返回 null，同样不抛异常")
    void 会员不存在返回null() {
        assertTrue(redis.opsForValue().get(CACHE_KEY) == null);
        MemberPrincipal principal = assertDoesNotThrow(() -> principalLoader.load(-1L));
        assertNull(principal);
    }
}
