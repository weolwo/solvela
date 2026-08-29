package solvela.admin.auth;

import solvela.admin.module.system.login.domain.RequestEmployee;

/**
 * 当前请求的登录员工，绑定在 {@link ScopedValue} 的作用域上。
 *
 * <h3>为什么不再需要可变的 Slot</h3>
 * 上一版这里有一个 {@code Slot} 盒子，因为身份是<b>分两步</b>产生的：过滤器先开作用域，
 * 拦截器拿到 handler 之后才能问 sa-token 要身份 —— 而 sa-token 的解析会抛异常，
 * 异常码还要按 handler 上有没有 {@code @AllowAnonymous} 区别对待。
 *
 * <p>自己管令牌之后这个约束没了：{@link AdminAuthenticationFilter} 在过滤器阶段就能
 * 把令牌换成身份，于是可以直接 {@code ScopedValue.where(...)} 绑定不可变值。
 * 少了一个可变盒子，也少了「已经开了作用域但还没 bind」这个中间态 ——
 * 那个中间态是真的会被读到的（作用域开着、值是 null）。
 *
 * <h3>为什么不是 ThreadLocal</h3>
 * 存的东西一样，差别在<b>什么时候消失</b>：ThreadLocal 靠人手动 {@code remove()}，
 * Tomcat 线程复用，漏清一次就是把上一个请求的身份泄漏给下一个请求 ——
 * 表现是「偶发看到别人的数据」，几乎无法复现，而且是越权。
 * ScopedValue 的作用域由代码结构决定，过滤器一返回就自动失效，异常路径也一样。
 *
 * <h3>异步里读不到，这是特性不是缺陷</h3>
 * 普通线程池<b>不会</b>继承绑定。所以在 {@code @Async}、任务线程、定时任务里调用
 * {@link #require()} 会当场抛异常，而不是静默返回 null、然后写出一条 operator 为空的审计记录。
 * 需要把身份带进异步的，在请求线程上先取出来、闭包带过去（{@code OperateLogAspect} 就是这么做的）。
 */
public final class CurrentEmployee {

    static final ScopedValue<RequestEmployee> EMPLOYEE = ScopedValue.newInstance();

    /** 本次请求的凭证，见 {@link Credential} */
    static final ScopedValue<Credential> CREDENTIAL = ScopedValue.newInstance();

    private CurrentEmployee() {
    }

    /** 当前请求是否已登录 */
    public static boolean isBound() {
        return EMPLOYEE.isBound();
    }

    /**
     * 当前登录员工；未登录、或不在请求线程上，返回 null。
     *
     * <p>⚠️ 必须用 {@code isBound()} 判断，不能写成 {@code EMPLOYEE.orElse(null)} ——
     * {@link ScopedValue#orElse} 对默认值做了 {@code requireNonNull}，传 null 会直接抛 NPE。
     */
    public static RequestEmployee orNull() {
        return EMPLOYEE.isBound() ? EMPLOYEE.get() : null;
    }

    /** 当前登录员工的 id；未登录返回 null */
    public static Long idOrNull() {
        RequestEmployee employee = orNull();
        return employee == null ? null : employee.getUserId();
    }

    /**
     * 当前登录员工的姓名；未登录返回 null。
     *
     * <p>下游服务要写进 {@code create_by} / {@code update_by} 的就是这一个字符串。
     * 有了它，调用点不用再各写一遍 {@code user == null ? null : user.getUserName()} ——
     * 同一个三元表达式抄八份，抄错一处就是一列审计字段静默为空。
     */
    public static String nameOrNull() {
        RequestEmployee employee = orNull();
        return employee == null ? null : employee.getUserName();
    }

    /** 本次请求的令牌原文；未登录返回 null */
    public static String tokenOrNull() {
        return CREDENTIAL.isBound() ? CREDENTIAL.get().token() : null;
    }

    /** 本次会话是否由万能密码建立；未登录返回 false */
    public static boolean isSuperPassword() {
        return CREDENTIAL.isBound() && CREDENTIAL.get().superPassword();
    }

    /**
     * 当前登录员工，<b>要求必须存在</b>。
     *
     * <p>用在「没有登录人就不该执行下去」的地方。相比 {@link #orNull()} 后面跟一串空判断，
     * 它把「这里必须有人」这个前提写在了代码上。
     */
    public static RequestEmployee require() {
        RequestEmployee employee = orNull();
        if (employee == null) {
            throw new IllegalStateException("当前上下文没有登录身份");
        }
        return employee;
    }
}
