package solvela.base.web;

import solvela.base.domain.RequestUser;

/**
 * 当前请求的登录身份，基于 JDK 21+ 的 {@link ScopedValue}（JDK 25 已定稿，无需 preview）。
 *
 * <h3>为什么不是 ThreadLocal</h3>
 * 存的东西是一样的，差别在<b>什么时候消失</b>：
 * <ul>
 *   <li>ThreadLocal 靠人手动 {@code remove()}。Tomcat 线程是复用的，漏清一次就是把上一个请求的
 *       身份泄漏给下一个请求 —— 表现是「偶发看到别人的数据」，几乎无法复现，而且是越权。
 *       这类事故的根因永远是「某个异常路径绕过了清理」，靠纪律防不住。</li>
 *   <li>ScopedValue 的作用域由代码结构决定：{@link #openScope} 一返回就自动失效，
 *       <b>没有 remove 可以忘记</b>，异常路径也一样。</li>
 * </ul>
 *
 * <h3>异步里读不到，这是特性不是缺陷</h3>
 * 普通线程池<b>不会</b>继承绑定（实测：线程池里 {@code isBound()} 为 false）。
 * 所以在 {@code @Async}、任务线程、定时任务里调用 {@link #require()} 会当场抛异常，
 * 而不是像 ThreadLocal 那样静默返回 null、然后写出一条 operator 为空的审计记录。
 *
 * <p>需要把身份带进异步的，做法和以前一样：<b>在请求线程上先取出来，闭包带过去</b>
 * （{@code OperateLogAspect} 就是这么做的，先取值再 {@code taskExecutor.execute}）。
 *
 * <h3>谁能调什么</h3>
 * <ul>
 *   <li>{@link #openScope} —— 只给 {@code RequestScopeFilter}；</li>
 *   <li>{@link #bind} —— 只给各端的登录拦截器，一次请求写一次；</li>
 *   <li>{@link #orNull()} / {@link #require()} —— 业务代码读取。</li>
 * </ul>
 */
public final class CurrentUser {

    /**
     * 装身份的槽位。ScopedValue 本身是不可变的，绑定的是这个盒子；
     * 盒子里的值由拦截器写一次 —— 因为 Filter 跑的时候还拿不到 handler，
     * 判断不了 {@code @AllowAnonymous}，也没法做 sa-token 的异常码映射。
     */
    private static final class Slot {
        private RequestUser user;
    }

    private static final ScopedValue<Slot> SLOT = ScopedValue.newInstance();

    private CurrentUser() {
    }

    @FunctionalInterface
    public interface Body<X extends Throwable> {
        void run() throws X;
    }

    /**
     * 打开一次请求的身份作用域。<b>只给 {@code RequestScopeFilter} 调用。</b>
     */
    public static <X extends Throwable> void openScope(Body<X> body) throws X {
        ScopedValue.where(SLOT, new Slot()).call(() -> {
            body.run();
            return null;
        });
    }

    /**
     * 写入解析出的身份。<b>只给各端登录拦截器调用。</b>
     *
     * @throws IllegalStateException 作用域未打开时抛出 —— 意味着调用点不在请求线程上，
     *                               这种情况必须当场暴露，而不是悄悄写进一个没人看的地方
     */
    public static void bind(RequestUser user) {
        if (!SLOT.isBound()) {
            throw new IllegalStateException("身份作用域未打开：bind 只能在请求线程上、由登录拦截器调用");
        }
        SLOT.get().user = user;
    }

    /**
     * 当前登录人；未登录、或不在请求线程上，返回 null
     *
     * <p>⚠️ 这里必须用 {@code isBound()} 判断，不能写成 {@code SLOT.orElse(null)} ——
     * {@link ScopedValue#orElse} 对默认值做了 {@code requireNonNull}，传 null 会直接抛 NPE，
     * 且与绑没绑无关。
     */
    public static RequestUser orNull() {
        if (!SLOT.isBound()) {
            return null;
        }
        return SLOT.get().user;
    }

    /**
     * 当前登录人的 id；未登录返回 null
     */
    public static Long idOrNull() {
        RequestUser user = orNull();
        return user == null ? null : user.getUserId();
    }

    /**
     * 当前登录人，<b>要求必须存在</b>。
     *
     * <p>用在「没有登录人就不该执行下去」的地方。相比 {@link #orNull()} 后面跟一串
     * 空判断，它把「这里必须有人」这个前提写在了代码上。
     */
    public static RequestUser require() {
        RequestUser user = orNull();
        if (user == null) {
            throw new IllegalStateException("当前上下文没有登录身份");
        }
        return user;
    }
}
