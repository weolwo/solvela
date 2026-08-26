package solvela.base.web;

import org.junit.jupiter.api.Test;
import solvela.base.domain.RequestUser;
import solvela.base.enumeration.UserTypeEnum;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CurrentUser} 的语义约定。
 *
 * <p>这些用例锁的不是「功能」，是<b>失败方式</b>：作用域外读要返回 null 而不是脏数据、
 * 作用域外写要当场抛而不是静默生效、异步线程里要读不到而不是读到上一个请求的人。
 * 换实现（比如哪天回退成 ThreadLocal 或改用别的机制）时，这几条必须仍然成立。
 */
class CurrentUserTest {

    private static RequestUser user(long id, String name) {
        return new RequestUser() {
            @Override
            public Long getUserId() {
                return id;
            }

            @Override
            public String getUserName() {
                return name;
            }

            @Override
            public UserTypeEnum getUserType() {
                return UserTypeEnum.ADMIN_EMPLOYEE;
            }

            @Override
            public String getIp() {
                return "127.0.0.1";
            }

            @Override
            public String getUserAgent() {
                return "junit";
            }
        };
    }

    @Test
    void 作用域外读取返回null而不是抛异常() {
        // 定时任务、启动期初始化都会走到这里。这类地方「没有登录人」是正常的，
        // 不该炸；要求必须有人的地方用 require()
        assertNull(CurrentUser.orNull());
        assertNull(CurrentUser.idOrNull());
    }

    @Test
    void 作用域外写入必须当场抛出() {
        // 作用域没开却能写成功，等于身份被写进了一个没人读得到的地方，
        // 症状是「接口里拿不到当前用户」，排查要从头翻整条链路
        assertThrows(IllegalStateException.class, () -> CurrentUser.bind(user(1L, "alice")));
    }

    @Test
    void 作用域内绑定后可读取() throws Exception {
        CurrentUser.openScope(() -> {
            assertNull(CurrentUser.orNull(), "刚开作用域时还没有身份");
            CurrentUser.bind(user(7L, "alice"));
            assertEquals(7L, CurrentUser.idOrNull());
            assertEquals("alice", CurrentUser.require().getUserName());
        });
    }

    @Test
    void 作用域退出后自动失效() throws Exception {
        CurrentUser.openScope(() -> CurrentUser.bind(user(7L, "alice")));
        // 关键：没有任何清理代码，退出即失效。ThreadLocal 时代这里要靠
        // afterCompletion 手动 remove，漏一次就是跨请求身份泄漏
        assertNull(CurrentUser.orNull());
    }

    @Test
    void 抛异常退出作用域同样失效() {
        assertThrows(RuntimeException.class, () -> CurrentUser.openScope(() -> {
            CurrentUser.bind(user(7L, "alice"));
            throw new RuntimeException("业务异常");
        }));
        // 异常路径是手动清理最容易漏掉的地方，这里由结构保证
        assertNull(CurrentUser.orNull());
    }

    @Test
    void require在无身份时抛出() {
        assertThrows(IllegalStateException.class, CurrentUser::require);
    }

    @Test
    void 普通线程池不继承身份() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicBoolean seen = new AtomicBoolean(true);
        try {
            CurrentUser.openScope(() -> {
                CurrentUser.bind(user(7L, "alice"));
                seen.set(pool.submit(() -> CurrentUser.orNull() != null).get());
            });
        } finally {
            pool.shutdown();
        }
        // 这是特性不是缺陷：异步里要用身份，必须在请求线程上先取出来闭包带过去
        // （OperateLogAspect 就是这么做的）。静默拿到 null 好过拿到别人的身份
        assertFalse(seen.get(), "线程池线程不应该继承请求身份");
    }
}
