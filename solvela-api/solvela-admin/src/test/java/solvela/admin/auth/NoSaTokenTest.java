package solvela.admin.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 守住「整个后端不再依赖 sa-token」这条线。
 *
 * <p>solvela-admin 依赖了全部业务模块，classpath 是整个工程里最宽的一份 ——
 * 在这里断言不存在，等于在任何模块里都不存在。
 *
 * <p>为什么需要一个测试：pom 是可以被加回去的，而且<b>加回去不会有任何报错</b>。
 * 多一个依赖从来不会让代码编不过，只会悄悄把边界拆掉 ——
 * 下一个人遇到鉴权需求时，搜到的第一条中文资料就是 sa-token。
 *
 * <p>真要重新引入，请先回答：本系统需要令牌做的是
 * {@code issue / lookup / revoke / revokeAll / setActiveTimeoutSeconds} 五件事
 * （见 {@link TokenStore}），一个两百多个方法的静态门面能多解决哪一件？
 */
class NoSaTokenTest {

    @Test
    @DisplayName("sa-token 不在 classpath 上")
    void 没有satoken() {
        assertAbsent("cn.dev33.satoken.stp.StpUtil", "sa-token 核心");
        assertAbsent("cn.dev33.satoken.annotation.SaCheckPermission", "sa-token 注解");
    }

    private static void assertAbsent(String className, String what) {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(className),
                () -> "classpath 上出现了 " + what + "（" + className + "）。\n"
                        + "管理端的认证与鉴权已经收敛到 solvela.admin.auth 包里：\n"
                        + "  令牌 -> TokenStore / RedisTokenStore\n"
                        + "  认证 -> AdminAuthenticationFilter\n"
                        + "  鉴权 -> AdminAuthorizationInterceptor + @RequiresPermission\n"
                        + "新需求请加在那里，不要把框架请回来。");
    }
}
