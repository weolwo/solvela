package solvela.admin.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.admin.constant.UserTypeEnum;
import solvela.admin.module.system.login.domain.RequestEmployee;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CurrentEmployee} 的语义约定。
 *
 * <p>这些用例锁的不是「功能」，是<b>失败方式</b>：作用域外读要返回 null 而不是脏数据、
 * 退出作用域要自动失效而不是靠人清理、异步线程里要读不到而不是读到上一个请求的人。
 * 换实现（比如哪天回退成 ThreadLocal 或改用别的机制）时，这几条必须仍然成立。
 */
class CurrentEmployeeTest {

    private static RequestEmployee employee(long id, String name) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(id);
        employee.setActualName(name);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setIp("127.0.0.1");
        employee.setUserAgent("junit");
        return employee;
    }

    /** 模拟 {@code AdminAuthenticationFilter} 里那次绑定 */
    private static void inScope(long id, String name, String token, Runnable body) {
        inScope(id, name, new Credential(token, false), body);
    }

    private static void inScope(long id, String name, Credential credential, Runnable body) {
        ScopedValue.where(CurrentEmployee.EMPLOYEE, employee(id, name))
                .where(CurrentEmployee.CREDENTIAL, credential)
                .run(body);
    }

    @Test
    @DisplayName("作用域外读取返回 null 而不是抛异常")
    void 作用域外读取返回null() {
        // 定时任务、启动期初始化都会走到这里。这类地方「没有登录人」是正常的，不该炸；
        // 要求必须有人的地方用 require()
        assertFalse(CurrentEmployee.isBound());
        assertNull(CurrentEmployee.orNull());
        assertNull(CurrentEmployee.idOrNull());
        assertNull(CurrentEmployee.nameOrNull());
        assertNull(CurrentEmployee.tokenOrNull());
    }

    @Test
    @DisplayName("作用域内能读到身份与令牌")
    void 作用域内可读取() {
        inScope(7L, "alice", "ad_test", () -> {
            assertEquals(7L, CurrentEmployee.idOrNull());
            assertEquals("alice", CurrentEmployee.nameOrNull());
            assertEquals("alice", CurrentEmployee.require().getUserName());
            assertEquals("ad_test", CurrentEmployee.tokenOrNull());
        });
    }

    @Test
    @DisplayName("作用域退出后自动失效 —— 没有任何清理代码")
    void 作用域退出后自动失效() {
        inScope(7L, "alice", "ad_test", () -> {
        });
        // 关键：没有 remove。ThreadLocal 时代这里要靠 afterCompletion 手动清，
        // 漏一次就是跨请求身份泄漏
        assertNull(CurrentEmployee.orNull());
    }

    @Test
    @DisplayName("抛异常退出作用域同样失效")
    void 抛异常退出同样失效() {
        assertThrows(RuntimeException.class,
                () -> inScope(7L, "alice", "ad_test", () -> {
                    throw new RuntimeException("业务异常");
                }));
        // 异常路径是手动清理最容易漏掉的地方，这里由结构保证
        assertNull(CurrentEmployee.orNull());
    }

    @Test
    @DisplayName("万能密码会话可被识别 —— 这类会话不提示强制改密")
    void 万能密码标记() {
        inScope(7L, "alice", new Credential("ad_test", true),
                () -> assertTrue(CurrentEmployee.isSuperPassword()));
        inScope(7L, "alice", "ad_test",
                () -> assertFalse(CurrentEmployee.isSuperPassword()));
        // 作用域外一律 false，而不是抛异常：定时任务问「这是不是万能密码会话」是合理的
        assertFalse(CurrentEmployee.isSuperPassword());
    }

    @Test
    @DisplayName("require 在无身份时抛出，而不是返回 null")
    void require在无身份时抛出() {
        assertThrows(IllegalStateException.class, CurrentEmployee::require);
    }

    @Test
    @DisplayName("🔴 普通线程池不继承身份 —— 这是特性不是缺陷")
    void 线程池不继承身份() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicBoolean seen = new AtomicBoolean(true);
        try {
            inScope(7L, "alice", "ad_test", () -> {
                try {
                    seen.set(pool.submit(CurrentEmployee::isBound).get());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            pool.shutdown();
        }
        // 异步里要用身份，必须在请求线程上先取出来闭包带过去（OperateLogAspect 就是这么做的）。
        // 静默拿到 null 好过拿到别人的身份
        assertFalse(seen.get(), "线程池线程不应该继承请求身份");
    }
}
