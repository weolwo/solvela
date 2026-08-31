package solvela.base.web;

import solvela.base.trace.Trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TraceFilter} 的行为。
 *
 * <h3>这个测试针对的是「过滤器返回后线程上不该有残留」</h3>
 * MDC 是 ThreadLocal，容器线程会被复用。过滤器绑了值不清，值就留在那根线程上。
 *
 * <p>⚠️ 值得说清楚残留的<b>真实</b>危害，因为它比想当然的要小：
 * 下一个请求进来时 {@code TraceFilter} 第一件事就是覆盖这个值，
 * 所以「下一个请求打出上一个请求的 traceId」<b>不会发生</b> ——
 * 这一条是实测出来的（把 try-with-resources 换回手写 put 且不清，
 * 连打 40 次请求，40 个 traceId 依然各不相同）。
 *
 * <p>真正会出问题的是<b>请求之间</b>那段时间：容器线程回到池里之后，
 * 如果有任何东西在这根线程上打日志（容器自己的、连接池的、异步 dispatch 收尾的），
 * 打出来的就是上一个请求的 traceId，指向一个毫不相干的调用链。
 * 这种日志比没有日志更误导人 —— 它看起来完全正常。
 *
 * <p>所以断言写成「过滤器返回后当前线程必须干净」，直接对着这个契约，
 * 而不是绕一圈去测那个其实不成立的场景。
 */
class TraceFilterTest {

    private final TraceFilter filter = new TraceFilter();

    @AfterEach
    void clean() {
        MDC.clear();
    }

    @Test
    @DisplayName("过滤器返回后，线程上不留任何 traceId")
    void 返回后线程是干净的() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertNull(MDC.get("traceId"), "开始前线程就该是干净的");

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(MDC.get("traceId"),
                """
                        过滤器返回后线程上还留着 traceId。
                        多半是 try-with-resources 被改回了手写 MDC.put 且漏了清理。
                        后果不是「下一个请求读到旧值」（那个会被覆盖），而是这根线程
                        回到池里之后，任何在它上面打的日志都会挂着一个不相干的调用链 id。
                        """);
    }

    @Test
    @DisplayName("抛异常时也要清干净 —— 出错的那次请求才是最需要日志准确的时候")
    void 异常路径也清理() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    // 处理过程中炸了
                    throw new IllegalStateException("boom");
                }));

        assertNull(MDC.get("traceId"), "异常路径漏了清理");
    }

    @Test
    @DisplayName("过滤器执行期间，链路 id 是可读的，且与响应头一致")
    void 执行期间可读() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seen = new String[1];
        filter.doFilter(request, response, (req, res) -> seen[0] = Trace.id());

        assertNotNull(seen[0], "链路里读不到 traceId");
        assertEquals(response.getHeader("traceId"), seen[0],
                "响应头和链路内读到的必须是同一个");
    }

    @Test
    @DisplayName("合法的客户端 traceId 沿用，畸形的丢弃重生成")
    void 校验客户端传入的id() throws Exception {
        assertEquals("client-abc-123", run("client-abc-123"));

        // 下面这些都不合规，必须被换掉
        for (String bad : new String[]{
                "abc%0d%0a[ERROR]:injected",   // 编码过的换行 —— 日志注入
                "a".repeat(33),                // 超长
                "中文",                         // 非 ASCII
                "has space",
                ""}) {
            String actual = run(bad);
            assertNotEquals(bad, actual, "畸形值被原样接受了：" + bad);
            assertTrue(actual.matches("[A-Za-z0-9-]{1,32}"), "重新生成的值不合规：" + actual);
        }
    }

    private String run(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/me");
        if (header != null) {
            request.addHeader("traceId", header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getHeader("traceId");
    }
}
