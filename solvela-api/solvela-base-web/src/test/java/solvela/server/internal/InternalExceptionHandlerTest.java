package solvela.server.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import solvela.exception.BusinessException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务间调用的错误出口。**这里断言的是状态码，因为状态码是网关唯一会看的东西。**
 *
 * <p>网关侧的 {@code RestClient} 按状态码决定「这次调用成没成」，进而决定
 * 重试、熔断、APM 成功率。所以「哪一类失败对应哪个码」不是风格问题 ——
 * 归错了类，网关会对一个不该重试的错误反复重试。
 */
@DisplayName("服务间错误出口")
class InternalExceptionHandlerTest {

    @RestController
    static class ProbeController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("下游炸了");
        }

        @GetMapping("/rejected")
        String rejected() {
            throw new BusinessException("活动已结束");
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new InternalExceptionHandler())
            .build();

    @Test
    @DisplayName("🔴 路由不存在 → 404，不是 500")
    void 路由不存在是404() throws Exception {
        /*
         * 2026-09-12 部署时发现的：app-biz 对任意未知路径返回 500。
         *
         * 后果不只是难看 —— 网关拼错一个 @HttpExchange 路径时，表现会是
         * 「下游服务故障」：走重试、触发熔断、在 APM 里压低成功率，
         * 而真正该做的是改那行路径。查的人会去翻下游的日志和资源水位，
         * 方向完全反了。
         *
         * 顺带还有一条：每个 404 都往 error.log 里灌一条堆栈，
         * 扫描器一天几千个请求就能把真正的故障淹掉。
         */
        mvc.perform(get("/this/route/does/not/exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_SUCH_ROUTE"));
    }

    @Test
    @DisplayName("真正的意外 → 500")
    void 意外是500() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                // 调用方是网关不是浏览器：把原文给出去，省得两边对时间戳
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("下游炸了")));
    }

    @Test
    @DisplayName("域里的业务异常 → 409，让网关能把它和真故障分开")
    void 业务异常是409() throws Exception {
        mvc.perform(get("/rejected"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_REJECTED"));
    }
}
