package solvela.app.auth;

import solvela.enums.MemberStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import solvela.member.Member;
import solvela.member.constant.MemberConst;

/**
 * 会员号 → {@link MemberPrincipal}。带缓存，因为每个请求都要走一次。
 *
 * <p>单独一个 Bean 而不是塞进过滤器：Spring Cache 走代理，
 * 同类内部直接调用不经过代理，{@code @Cacheable} 会<b>静默失效</b> ——
 * 表现是「缓存明明配了却每次都查库」，没有任何报错。
 *
 * <h3>状态校验放在缓存之内还是之外</h3>
 * 放在<b>之内</b>：被冻结的会员直接从这里返回 null，而不是把状态带出去让调用方判断。
 * 这样「什么算一个可用的身份」只有一个地方定义。
 * 代价是冻结生效有缓存延迟 —— 所以冻结那条路径必须调 {@link #evict}，
 * 而这一版还多了一层保险：冻结时会 {@code revokeAll} 掉全部令牌，
 * 令牌没了就压根走不到这里，不再依赖缓存过期。
 */
@Component
@RequiredArgsConstructor
public class MemberPrincipalLoader {

    /** {@code #30m} 是 CustomRedisCacheManager 的 TTL 语法。 */
    public static final String CACHE = "app_member_principal#30m";

    private final MemberAuthDao memberAuthDao;

    /**
     * 取可用的会员身份；会员不存在或状态不正常返回 null。
     *
     * <p>⚠️ 返回 null 时<b>不写缓存</b>（RedisConfig 开了 disableCachingNullValues）。
     * 也就是说不存在的 memberId 每次都打库 —— 这是刻意的：正常令牌指向的会员一定存在，
     * 会走到这里只可能是会员被物理删了（业务不允许，注销是改 status），或者有人在试探。
     * 后者恰恰不该给它缓存。
     */
    @Cacheable(value = CACHE, key = "#memberId")
    public MemberPrincipal load(Long memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = memberAuthDao.selectForAuth(memberId);
        if (member == null || member.getStatus() == null
                || member.getStatus() != MemberStatusEnum.NORMAL) {
            return null;
        }
        return new MemberPrincipal(
                member.getMemberId(),
                member.getMemberName(),
                member.getNickname(),
                member.getAvatarFileId(),
                member.getGender());
    }

    /**
     * 清掉某个会员的身份缓存。改昵称、换头像、改账号之后必须调，否则最多 30 分钟看到旧资料。
     */
    @CacheEvict(value = CACHE, key = "#memberId")
    public void evict(Long memberId) {
        // 方法体为空是对的，语义全在 @CacheEvict 上
    }
}
