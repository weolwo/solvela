package solvela.app.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 走一遍真的 HTTP 路径：拦截器 → 请求体绑定 → 判定 → 统一的错误出口。
 *
 * <p>用的是本机快照（或内置默认值）里的 {@code LOGIN} 场景：账号维度突发 5。
 * 密码登录不带滑块通行票会在 Controller 里被 403 挡下 —— 所以前 5 次拿到 403、不会去打下游，
 * 第 6 次在进 Controller 之前就被限流挡下。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class RateLimitHttpTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("🔴 同一个邮箱换着 IP 打登录：账号维度在请求体读完之后照样数到，第 6 次 429 + Retry-After")
    void accountDimensionCountedFromBody() throws Exception {
        String email = "http-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            int status = login(email, freshIp()).getResponse().getStatus();
            assertNotEquals(429, status, "前 5 次不该被限流（第 " + (i + 1) + " 次）");
        }
        MvcResult limited = login(email, freshIp());
        assertEquals(429, limited.getResponse().getStatus());
        assertNotNull(limited.getResponse().getHeader("Retry-After"), "429 必须带 Retry-After，前端靠它做倒计时");
        assertTrue(limited.getResponse().getContentAsString().contains("OPERATION_LIMITED"));
    }

    @Test
    @DisplayName("不限流的接口（领设备令牌）怎么打都不会 429")
    void exemptEndpointNeverLimited() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 15; i++) {
            int status = mvc.perform(post("/device/register").header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
            assertNotEquals(429, status);
        }
    }

    private MvcResult login(String email, String ip) throws Exception {
        String body = "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"" + email + "\",\"credential\":\"wrong-password\"}";
        return mvc.perform(post("/auth/login").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    /** 198.18.0.0/15：测试专用地址段 */
    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.19." + r.nextInt(256) + "." + (1 + r.nextInt(254));
    }
}
