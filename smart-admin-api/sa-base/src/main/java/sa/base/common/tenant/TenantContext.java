package sa.base.common.tenant;

import sa.base.common.constant.TenantConst;

import java.util.function.Supplier;

/**
 * 当前请求的租户上下文。
 *
 * <p>系统目前单租户运行，所以 {@link #get()} 在没有显式设置时返回
 * {@link TenantConst#DEFAULT_TENANT_ID}。将来接入多租户时，
 * 在登录/鉴权环节调用 {@link #set(String)} 即可，<b>其余代码一行都不用改</b> ——
 * 这正是把租户过滤下沉到拦截器的意义。
 *
 * <p>⚠️ 用的是 {@link ThreadLocal}，<b>穿不透线程池</b>。异步任务、{@code @Async}、
 * 定时任务里拿到的都是默认租户。项目里 {@code ThreadPoolUtil} 已经在做 MDC 快照透传，
 * 真要多租户异步时把租户一并透传进去（同一个位置，改一处）。
 *
 * @Date 2026-08-22
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 取当前租户。未显式设置时返回默认租户，<b>不会返回 null</b> ——
     * 返回 null 会让拦截器拼出 {@code tenant_id = null}，那条件恒不成立，
     * 表现是「查询突然什么都查不到」，而且不报错。
     */
    public static String get() {
        String t = CURRENT.get();
        return t == null ? TenantConst.DEFAULT_TENANT_ID : t;
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 在指定租户下执行一段逻辑，结束后<b>恢复原值</b>（而不是简单清空）。
     *
     * <p>恢复原值而非 clear 是有意的：嵌套调用时 clear 会把外层的租户也抹掉，
     * 于是外层后续的查询悄悄落回默认租户 —— 这种 bug 只在嵌套路径上出现，极难复现。
     */
    public static <T> T runAs(String tenantId, Supplier<T> action) {
        String prev = CURRENT.get();
        try {
            CURRENT.set(tenantId);
            return action.get();
        } finally {
            if (prev == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(prev);
            }
        }
    }
}
