package solvela.app.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberIdentity;

/**
 * 会员号 → {@link MemberPrincipal}。带缓存，因为每个请求都要走一次。
 *
 * <p>单独一个 Bean 而不是塞进过滤器：Spring Cache 走代理，
 * 同类内部直接调用不经过代理，{@code @Cacheable} 会<b>静默失效</b> ——
 * 表现是「缓存明明配了却每次都回源」，没有任何报错。
 *
 * <h3>回源的是会员域，不是数据库</h3>
 * 本进程<b>不查库</b>：身份从 {@link MemberAuthApi#getAuthIdentity} 拿。
 * 今天它是同 JVM 的一次方法调用，拆成独立服务后换成一次 RPC，本类只改这一行。
 *
 * <p>30 分钟 TTL 意味着每个活跃会员 30 分钟才回源一次，RPC 量完全可接受。
 * 🔴 <b>不要为了省这次回源退回 JWT</b> —— 即时吊销的取舍见 {@code MemberRedisTokenStore} 的类注释，
 * 那个决定在拆成多服务之后只会更成立。
 *
 * <h3>状态校验在域里，不在这里</h3>
 * 「什么算一个可用身份」由会员域回答：被冻结/注销的会员那边直接返回 null，
 * 不把 status 交出来让每个调用方各判一遍。
 * 代价本来是「冻结生效有最多 30 分钟的缓存延迟」。
 * <b>2026-08-30 起这个延迟对安全不再有意义</b>：{@code MemberService.updateStatus} 冻结时会
 * {@code revokeAll} 掉该会员的全部令牌，令牌没了请求连认证都过不去，压根走不到这里。
 *
 * <p>⚠️ 在那之前，这段注释描述的是一个<b>不存在</b>的机制 —— {@code revokeAll} 当时全仓
 * 只有测试在调。被冻结的会员实际还能正常用最多 30 分钟。
 * 教训不是「忘了实现」，而是<b>注释写下了一个没有测试盯着的承诺</b>。
 *
 * <p>缓存延迟对<b>资料展示</b>仍然存在（改昵称后最多 30 分钟看到旧的），所以改资料的路径
 * 依然要调 {@link #evict}。
 *
 * <p>⚠️ 拆成独立服务后，改昵称/换头像发生在会员域那一侧，而这个缓存在网关的 Redis 里，
 * {@link #evict} 就跨进程了。届时要靠「会员资料变更」事件来驱动失效 ——
 * 所以失效的调用点必须收口，不要散落在每个改资料的地方。
 */
@Component
@RequiredArgsConstructor
public class MemberPrincipalLoader {

    /** {@code #30m} 是 CustomRedisCacheManager 的 TTL 语法。 */
    public static final String CACHE = "app_member_principal#30m";

    private final MemberAuthApi memberAuthApi;

    /**
     * 取可用的会员身份；会员不存在或状态不正常返回 null。
     *
     * <p>⚠️ 返回 null 时<b>不写缓存</b>（RedisConfig 开了 disableCachingNullValues）。
     * 也就是说不存在的 memberId 每次都回源 —— 这是刻意的：正常令牌指向的会员一定存在，
     * 会走到这里只可能是会员被物理删了（业务不允许，注销是改 status），或者有人在试探。
     * 后者恰恰不该给它缓存。
     */
    @Cacheable(value = CACHE, key = "#memberId")
    public MemberPrincipal load(Long memberId) {
        MemberIdentity identity = memberAuthApi.getAuthIdentity(memberId);
        return identity == null ? null : MemberPrincipal.of(identity);
    }

    /**
     * 清掉某个会员的身份缓存。改昵称、换头像、改账号之后必须调，否则最多 30 分钟看到旧资料。
     */
    @CacheEvict(value = CACHE, key = "#memberId")
    public void evict(Long memberId) {
        // 方法体为空是对的，语义全在 @CacheEvict 上
    }
}
