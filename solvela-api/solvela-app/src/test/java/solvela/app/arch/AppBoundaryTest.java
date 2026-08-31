package solvela.app.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 守住 C 端进程的四条边界。
 *
 * <p>这三条都属于「坏掉的时候不会有任何报错」的那一类 —— 应用照常启动，
 * 接口照常响应，只是行为悄悄变了。所以必须有会失败的测试盯着。
 */
@SpringBootTest
class AppBoundaryTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("🔴 mysql 驱动不在网关的 classpath 上")
    void 没有数据库驱动() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.mysql.cj.jdbc.Driver"),
                """
                        网关的 classpath 上又出现了 mysql 驱动。
                        多半是有人给 solvela-app 加了一个带 Dao 的模块 —— 常见的是某个域实现模块，
                        或者往 solvela-member-session 里加了 Dao。

                        网关只做转发与组装，域数据一律经 HTTP 向 member(1027) / marketing(1026) 要。
                        它一旦能直连数据库，「绕过下游服务直接查一下」就会变成随手可做的事，
                        而那正是把刚拆开的两个服务重新粘回去的方式 —— 且不会有任何报错。

                        这条断言是「物理上不具备」而不是「约定不要那么做」：
                        前者坏掉时构建就红了，后者要等到 code review 有人注意到。
                        """);
    }

    @Test
    @DisplayName("sa-token 不在 C 端的 classpath 上")
    void 没有satoken() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("cn.dev33.satoken.stp.StpUtil"),
                """
                        sa-token 又回到了 C 端的 classpath 上。
                        多半是有人给 solvela-app 加回了 solvela-web 依赖 —— 那个模块是管理端的 HTTP 层。
                        C 端的令牌由 TokenStore 自己签发，不需要也不该看得见 sa-token：
                        两套并存时，「当前登录的是谁」就有了两个来源。
                        """);
    }

    @Test
    @DisplayName("本进程只有一个错误出口")
    void 只有一个异常处理器() {
        // 框架自带的 advice（springdoc 的 OpenApiResourceAdvice）不算 —— 它不碰业务异常。
        // 要防的是<b>本项目自己的</b>第二个错误出口。
        var ours = context.getBeansWithAnnotation(RestControllerAdvice.class).entrySet().stream()
                .filter(e -> org.springframework.aop.support.AopUtils.getTargetClass(e.getValue())
                        .getName().startsWith("solvela."))
                .map(Map.Entry::getKey)
                .toList();
        assertEquals(1, ours.size(),
                () -> """
                        C 端出现了多个 solvela 自己的 @RestControllerAdvice：%s

                        最可能的原因是组件扫描把 solvela.web 包了进来 —— 那里的
                        GlobalExceptionHandler 是<b>管理端</b>的错误出口。两边现在都返回
                        真实 HTTP 状态码，但错误码表不同：管理端用 solvela.code.ErrorCode
                        （NO_PERMISSION / LOGIN_ACTIVE_TIMEOUT…），C 端用 ApiErrors
                        （LOGIN_REQUIRED / BAD_CREDENTIALS…）。
                        两个 advice 同时在场时，返回哪套 code 取决于哪个先匹配上，
                        表现是「同一个异常，客户端有时认得有时不认得」—— 最难查的一类问题。
                        """.formatted(ours));
        assertEquals("apiExceptionHandler", ours.getFirst());
    }

    @Test
    @DisplayName("共享模块的 controller 一个都没被装配")
    void 没有外来的接口() {
        Map<String, Object> controllers =
                context.getBeansWithAnnotation(org.springframework.stereotype.Controller.class);
        var foreign = controllers.values().stream()
                .map(bean -> org.springframework.aop.support.AopUtils.getTargetClass(bean).getName())
                // 只看 solvela 自己的类：Spring Boot 的 BasicErrorController、
                // springdoc 的文档端点都是框架该有的，不在讨论范围内
                .filter(name -> name.startsWith("solvela.") && !name.startsWith("solvela.app."))
                .toList();
        assertTrue(foreign.isEmpty(),
                () -> """
                        本进程装配了不属于自己的 controller：%s

                        上一版是靠 SharedControllerExcludeFilter「扫全世界再减掉」来防这件事，
                        漏一个就是把后台接口摆到公网。现在改成只扫 solvela.app / solvela.base /
                        solvela.member 三个包，前提是后两个包里没有 controller。
                        这条断言就是那个前提。
                        """.formatted(foreign));
    }
}
