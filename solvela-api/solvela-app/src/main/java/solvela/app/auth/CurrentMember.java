package solvela.app.auth;

import java.util.Optional;

/**
 * 当前请求的会员身份，绑定在 {@link ScopedValue} 的作用域上。
 *
 * <p>与 solvela-base 的 {@code CurrentUser} 是<b>两套独立机制</b>，不是复用也不是继承：
 * 那边持有的是 {@code RequestUser}（员工形状），本进程只有会员。
 * 共用一个上下文的唯一好处是少写一个类，坏处是两个端的身份模型从此绑在一起。
 *
 * <h3>为什么比 CurrentUser 简单</h3>
 * {@code CurrentUser} 需要一个可变的 Slot：管理端的拦截器要先开作用域、
 * 再在拿到员工信息后 bind 进去。本进程的 {@link AuthenticationFilter}
 * <b>在开作用域之前就已经解析完 token</b>，所以可以直接
 * {@code ScopedValue.where(MEMBER, principal)} —— 值一旦绑定就不可变，
 * 没有「已经开了作用域但还没 bind」这个中间态。
 *
 * <p>匿名请求不绑定（ScopedValue 不接受 null 值），所以 {@link #isBound()}
 * 就是「这个请求有没有登录」的准确答案。
 */
public final class CurrentMember {

    static final ScopedValue<MemberPrincipal> MEMBER = ScopedValue.newInstance();

    private CurrentMember() {
    }

    /**
     * 当前是否已登录。
     *
     * <p>只在<b>请求线程</b>上有意义。丢进线程池的任务不会继承作用域 ——
     * 那不是缺陷，是 ScopedValue 的设计：身份跟着调用栈走，不跟着对象走。
     * 异步任务需要身份时，在请求线程上取出来当参数传进去。
     */
    public static boolean isBound() {
        return MEMBER.isBound();
    }

    /**
     * 取当前会员；未登录返回空。
     *
     * <p>🔴 不能写成 {@code MEMBER.orElse(null)} —— ScopedValue 的 orElse
     * 不接受 null 参数，未绑定时会抛 NullPointerException 而不是返回 null。
     */
    public static Optional<MemberPrincipal> find() {
        return MEMBER.isBound() ? Optional.of(MEMBER.get()) : Optional.empty();
    }

    /**
     * 取当前会员；未登录直接抛。
     *
     * <p>给「必须登录」的接口用。这些接口已经被 {@code AuthorizationInterceptor} 挡过一道，
     * 走到业务代码时身份一定在 —— 所以这里抛的是编程错误，不是用户错误。
     */
    public static MemberPrincipal require() {
        return find().orElseThrow(() -> new IllegalStateException(
                "当前请求没有会员身份。要么这个接口漏了鉴权（检查是不是误挂了 @Anonymous），"
                        + "要么这段代码跑在请求线程之外（作用域不会跨线程池传播）。"));
    }

    /**
     * 取当前会员号；未登录返回 null。给「登录与否都能看，登录了多返回一点」的接口用。
     */
    public static Long memberIdOrNull() {
        return find().map(MemberPrincipal::memberId).orElse(null);
    }
}
