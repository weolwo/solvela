package solvela.app.auth;

import java.util.concurrent.Callable;

/**
 * 测试辅助：在「某个会员已登录」的作用域里跑一段代码。
 *
 * <p>{@link CurrentMember#MEMBER} 是包内可见的（生产代码只该由 AuthenticationFilter 绑定），
 * 别的包里的测试要直接调控制器时，走这里。只在测试源码里，不进生产包。
 */
public final class AsMember {

    private AsMember() {
    }

    public static <T> T call(MemberPrincipal member, Callable<T> body) throws Exception {
        return ScopedValue.where(CurrentMember.MEMBER, member).call(body::call);
    }
}
