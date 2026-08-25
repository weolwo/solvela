package solvela.app.module.login.manager;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import solvela.member.domain.entity.Member;
import solvela.app.constant.AppCacheConst;
import solvela.app.module.login.dao.MemberLoginDao;
import solvela.app.module.login.domain.RequestMember;

/**
 * 登录态的读取与失效。
 *
 * <p>🔴 单独一个 Bean，不是把这两个方法塞进 {@code MemberLoginService}：
 * Spring Cache 走的是<b>代理</b>，同类内部直接调用不经过代理，
 * {@code @Cacheable} 会静默失效 —— 表现是「缓存明明写了却每次都查库」，
 * 且没有任何报错。管理端的 {@code LoginManager} 分出来也是同一个原因。
 *
 * @Date 2026-08-25
 */
@Service
@RequiredArgsConstructor
public class MemberLoginManager {

    private final MemberLoginDao memberLoginDao;

    /**
     * 取会员的请求上下文；会员不存在返回 null。
     *
     * <p>⚠️ 返回 null 时缓存<b>不会</b>写入 —— {@code RedisConfig} 里
     * {@code disableCachingNullValues()} 是开着的。也就是说「查不到的 memberId」
     * 每次都会打到库上。这是刻意的取舍：正常流程下 token 里的 memberId 一定存在，
     * 会走到这里只有两种情况 —— 会员被物理删了（业务上不允许，注销是改 status），
     * 或者有人在伪造 token。后者恰恰不该给它缓存。
     */
    @Cacheable(value = AppCacheConst.Login.REQUEST_MEMBER, key = "#memberId")
    public RequestMember getRequestMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = memberLoginDao.selectForAuthById(memberId);
        if (member == null) {
            return null;
        }

        RequestMember requestMember = new RequestMember();
        requestMember.setMemberId(member.getMemberId());
        requestMember.setMemberName(member.getMemberName());
        requestMember.setNickname(member.getNickname());
        requestMember.setAvatarFileId(member.getAvatarFileId());
        requestMember.setGender(member.getGender());
        requestMember.setStatus(member.getStatus());
        // ip / userAgent 是每个请求各不相同的，刻意不在这里赋值 ——
        // 它们会随缓存一起被复用给别的请求，日志里就会记到上一个人的 IP。
        // 由 MemberLoginService#getLoginMember 在缓存之外逐请求填充。
        return requestMember;
    }

    /**
     * 清掉某个会员的登录态缓存。
     *
     * <p>改昵称、换头像、改账号、注销、被冻结之后必须调用它，否则最多 30 分钟内
     * 用户看到的还是旧资料。
     */
    @CacheEvict(value = AppCacheConst.Login.REQUEST_MEMBER, key = "#memberId")
    public void clearRequestMemberCache(Long memberId) {
        // 方法体为空是对的，语义全在 @CacheEvict 上
    }
}
