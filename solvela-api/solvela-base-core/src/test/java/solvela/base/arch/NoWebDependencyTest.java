package solvela.base.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 守住「solvela-base 不认识 HTTP」这条线。
 *
 * <p>这条线不是风格偏好，是<b>模块边界的实现方式</b>：所有业务域模块都依赖 solvela-base，
 * 只要它的 pom 里有 spring-boot-starter-web，servlet 和 spring-web 就会传递给每一个域，
 * 于是任何一个域随手写 {@code import HttpServletRequest} 都能编过 —— 那条「共享层写一套、
 * 多端都能用」的规矩就只剩口头约定。把 web 依赖挪进 solvela-web 之后，同样一行 import
 * 会直接编译失败，这才叫强制。
 *
 * <p>但 pom 是可以被改回去的，而且改回去<b>不会有任何报错</b>：多一个依赖从来不会让代码编不过，
 * 只会悄悄把边界拆掉。所以需要这个测试 —— 它是唯一会因为「依赖变多」而变红的东西。
 *
 * <p>真要在共享层用某个 web 能力时，正确做法不是加依赖，是像
 * {@code UploadSource} / {@code SolvelaExcelUtil} 那样：在 base 定领域侧的抽象，
 * 在 solvela-web 写适配器。
 */
class NoWebDependencyTest {

    private static void assertAbsent(String className, String what) {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(className),
                () -> "solvela-base 的 classpath 上出现了 " + what + "（" + className + "）。\n"
                        + "多半是有人往 solvela-base/pom.xml 里加了 spring-boot-starter-web、\n"
                        + "sa-token 或 knife4j —— 这会让每一个业务域模块都重新看得见 HTTP。\n"
                        + "需要 web 能力时请加到 solvela-web，并在 base 侧留一个领域抽象。");
    }

    @Test
    @DisplayName("servlet 不在 base 的 classpath 上")
    void 没有servlet() {
        assertAbsent("jakarta.servlet.http.HttpServletRequest", "servlet API");
    }

    @Test
    @DisplayName("spring-web 不在 base 的 classpath 上")
    void 没有springweb() {
        assertAbsent("org.springframework.web.multipart.MultipartFile", "spring-web");
        assertAbsent("org.springframework.web.bind.annotation.RestController", "spring-web");
    }

    @Test
    @DisplayName("sa-token 与 swagger 不在 base 的 classpath 上")
    void 没有satoken与swagger() {
        assertAbsent("cn.dev33.satoken.stp.StpUtil", "sa-token");
        assertAbsent("io.swagger.v3.oas.annotations.media.Schema", "swagger 注解");
    }
}
