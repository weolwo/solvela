package solvela.admin.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「没有返回值就回 204」这条规则的边界。
 *
 * <p>三条断言对应三种真实存在的接口形态，其中第二条是<b>会造成数据丢失</b>的那种：
 * 文件下载也返回 void，但它有 body。
 */
class NoContentInterceptorTest {

    private final NoContentInterceptor interceptor = new NoContentInterceptor();

    /** 被反射当成 handler 用的样板方法，签名就是它们各自代表的那类接口 */
    @SuppressWarnings("unused")
    static class Handlers {
        public void delete(Long id) {
        }

        public void download(HttpServletResponse response) {
        }

        public List<String> query() {
            return List.of();
        }
    }

    private int statusAfterHandling(String methodName, MockHttpServletResponse response) throws Exception {
        Method method = null;
        for (Method m : Handlers.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                method = m;
            }
        }
        interceptor.postHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(new Handlers(), method), null);
        return response.getStatus();
    }

    @Test
    @DisplayName("void 接口回 204")
    void 无返回值回204() throws Exception {
        assertEquals(HttpStatus.NO_CONTENT.value(), statusAfterHandling("delete", new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("🔴 自己写响应的 void 接口不许改 —— 下载接口也是 void，但它有 body")
    void 下载接口不受影响() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/octet-stream");
        assertEquals(HttpStatus.OK.value(), statusAfterHandling("download", response),
                "把下载接口改成 204，客户端会按「无内容」处理，几百 KB 的文件当场丢掉");
    }

    @Test
    @DisplayName("有返回值的接口保持 200")
    void 有返回值保持200() throws Exception {
        assertEquals(HttpStatus.OK.value(), statusAfterHandling("query", new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("接口自己设过状态码就不覆盖")
    void 自定状态码不被覆盖() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(HttpStatus.ACCEPTED.value());
        assertEquals(HttpStatus.ACCEPTED.value(), statusAfterHandling("delete", response));
    }
}
