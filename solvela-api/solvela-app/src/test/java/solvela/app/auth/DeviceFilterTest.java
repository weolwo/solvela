package solvela.app.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import solvela.auth.device.DeviceIdentity;
import solvela.auth.device.DeviceTokenCodec;
import solvela.auth.device.DeviceTokenProperties;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备识别过滤器与「要不要强制」拦截器。
 *
 * <p>这两件事<b>刻意分在两个类</b>里（过滤器只识别、拦截器才拒绝），
 * 所以也分开验：下面前半段钉住「不管怎样都放行」，后半段钉住三个档位的差别。
 *
 * <p>不起 Spring 上下文 —— 这两个类都只依赖 codec 和一份配置，
 * 直接 new 出来比 {@code @SpringBootTest} 快两个数量级，而且能逐档切换 mode。
 *
 * @Date 2026-09-08
 */
class DeviceFilterTest {

    private static final String KEY = "gateway-test-key-0123456789abcdef0123";

    private static final String DEVICE_ID = "0123456789abcdef0123456789abcdef";

    private static DeviceTokenCodec codec() {
        DeviceTokenProperties props = new DeviceTokenProperties();
        props.setKeyVersion(1);
        props.setKeys(new LinkedHashMap<>(Map.of(1, KEY)));
        return new DeviceTokenCodec(props);
    }

    private static DeviceFilter newFilter(DeviceTokenCodec codec, DeviceAuthProperties properties) {
        RequestCredentials credentials = new RequestCredentials(
                new AuthProperties(null, null), properties, new WebCookieProperties(true, null, null, null));
        return new DeviceFilter(codec, properties, credentials);
    }

    private static DeviceFilter filter(DeviceAuthProperties.Mode mode) {
        return newFilter(codec(), new DeviceAuthProperties(null, mode));
    }

    /** 跑一次过滤器，返回链路内部看到的设备身份（没有则 null）。 */
    private static DeviceIdentity runFilter(DeviceFilter f, MockHttpServletRequest request) throws Exception {
        AtomicReference<DeviceIdentity> seen = new AtomicReference<>();
        AtomicReference<Boolean> reached = new AtomicReference<>(false);
        f.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            reached.set(true);
            seen.set(CurrentDevice.find().orElse(null));
        });
        assertTrue(reached.get(), "🔴 过滤器必须放行 —— 拒绝是拦截器的事，不是它的");
        return seen.get();
    }

    // ============================== 识别 ==============================
    @Test
    @DisplayName("Web 端：设备令牌在 HttpOnly cookie 里也认")
    void cookie也认() throws Exception {
        DeviceTokenCodec codec = codec();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("__Host-sv_dv", codec.issue(DEVICE_ID, "H5")));

        DeviceIdentity identity = runFilter(
                newFilter(codec, new DeviceAuthProperties(null, DeviceAuthProperties.Mode.OFF)), request);

        assertNotNull(identity, "Web 端改用 cookie 之后，只读请求头的话所有 Web 请求都会变成「没有设备」");
        assertEquals(DEVICE_ID, identity.deviceId());
    }

    @Test
    @DisplayName("请求头与 cookie 都有时，请求头优先 —— 明确写在头里的是调用方的本意")
    void 请求头优先() throws Exception {
        DeviceTokenCodec codec = codec();
        String other = "fedcba9876543210fedcba9876543210";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceAuthProperties.DEFAULT_HEADER, codec.issue(DEVICE_ID, "APP"));
        request.setCookies(new jakarta.servlet.http.Cookie("__Host-sv_dv", codec.issue(other, "H5")));

        DeviceIdentity identity = runFilter(
                newFilter(codec, new DeviceAuthProperties(null, DeviceAuthProperties.Mode.OFF)), request);

        assertEquals(DEVICE_ID, identity.deviceId());
    }


    @Test
    @DisplayName("带有效令牌 → 身份绑进作用域，字段与签发时一致")
    void 识别成功() throws Exception {
        DeviceTokenCodec codec = codec();
        String token = codec.issue(DEVICE_ID, "APP");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceAuthProperties.DEFAULT_HEADER, token);

        DeviceIdentity identity = runFilter(
                newFilter(codec, new DeviceAuthProperties(null, DeviceAuthProperties.Mode.OFF)), request);

        assertNotNull(identity);
        assertEquals(DEVICE_ID, identity.deviceId());
        assertEquals("APP", identity.deviceType());
    }

    @Test
    @DisplayName("没带头 / 头是空白 / 令牌验不过 → 不绑定，但一律放行")
    void 识别失败也放行() throws Exception {
        DeviceFilter f = filter(DeviceAuthProperties.Mode.OFF);

        assertNull(runFilter(f, new MockHttpServletRequest()), "没带头");

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader(DeviceAuthProperties.DEFAULT_HEADER, "   ");
        assertNull(runFilter(f, blank), "头是空白");

        MockHttpServletRequest bad = new MockHttpServletRequest();
        bad.addHeader(DeviceAuthProperties.DEFAULT_HEADER, "dv_1.forged.forged");
        assertNull(runFilter(f, bad), "令牌验不过");

        MockHttpServletRequest wrongKind = new MockHttpServletRequest();
        wrongKind.addHeader(DeviceAuthProperties.DEFAULT_HEADER, "mb_someMemberToken");
        assertNull(runFilter(f, wrongKind), "会员令牌不该被当成设备令牌");
    }

    @Test
    @DisplayName("🔴 别人签的令牌验不过 —— 换把密钥就该失效")
    void 别人签的令牌() throws Exception {
        DeviceTokenProperties other = new DeviceTokenProperties();
        other.setKeyVersion(1);
        other.setKeys(new LinkedHashMap<>(Map.of(1, "another-gateway-key-0000000000000000")));
        String foreign = new DeviceTokenCodec(other).issue(DEVICE_ID, "APP");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceAuthProperties.DEFAULT_HEADER, foreign);

        assertNull(runFilter(filter(DeviceAuthProperties.Mode.OFF), request));
    }

    @Test
    @DisplayName("头名可配，令牌前后的空白会被吃掉")
    void 自定义头名() throws Exception {
        DeviceTokenCodec codec = codec();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Sv-Dev", "  " + codec.issue(DEVICE_ID, "H5") + "  ");

        DeviceIdentity identity = runFilter(
                newFilter(codec, new DeviceAuthProperties("X-Sv-Dev", DeviceAuthProperties.Mode.OFF)), request);

        assertNotNull(identity, "配了别的头名就该读别的头");
        assertEquals("H5", identity.deviceType());
    }

    @Test
    @DisplayName("作用域随请求结束而失效 —— 不会漏到下一个请求")
    void 作用域不外泄() throws Exception {
        DeviceTokenCodec codec = codec();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceAuthProperties.DEFAULT_HEADER, codec.issue(DEVICE_ID, "APP"));

        runFilter(newFilter(codec, new DeviceAuthProperties(null, DeviceAuthProperties.Mode.OFF)), request);

        assertFalse(CurrentDevice.isBound(),
                "过滤器返回之后作用域必须已经失效 —— ThreadLocal 忘了清的那类事故就是这么来的");
    }

    @Test
    @DisplayName("enforce 档下过滤器【仍然】放行 —— 拒绝不是它的职责")
    void enforce档也放行() throws Exception {
        assertNull(runFilter(filter(DeviceAuthProperties.Mode.ENFORCE), new MockHttpServletRequest()));
    }

    // ============================== 强制 ==============================

    private static DeviceRequirementInterceptor interceptor(DeviceAuthProperties.Mode mode) {
        return new DeviceRequirementInterceptor(new DeviceAuthProperties(null, mode));
    }

    /** 一个标了 @DeviceExempt 的方法，和一个没标的。 */
    @SuppressWarnings("unused")
    static class Handlers {
        @DeviceExempt
        public void exempt() {
        }

        public void normal() {
        }
    }

    private static HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Handlers(), Handlers.class.getMethod(method));
    }

    private static boolean preHandle(DeviceRequirementInterceptor i, String method) throws Exception {
        return i.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler(method));
    }

    @Test
    @DisplayName("off / observe 档：什么都不判断，一律放行")
    void 前两档不拦() throws Exception {
        assertTrue(preHandle(interceptor(DeviceAuthProperties.Mode.OFF), "normal"));
        assertTrue(preHandle(interceptor(DeviceAuthProperties.Mode.OBSERVE), "normal"));
    }

    @Test
    @DisplayName("🔴 enforce 档：没有设备身份 → 401 DEVICE_REQUIRED，不是 LOGIN_REQUIRED")
    void enforce拦截() {
        ApiException e = assertThrows(ApiException.class,
                () -> preHandle(interceptor(DeviceAuthProperties.Mode.ENFORCE), "normal"));

        assertEquals(ApiErrors.DEVICE_REQUIRED, e.error(),
                "让一个客户端太旧的用户去登录，解决不了他的问题 —— 两个 401 必须分得开");
    }

    @Test
    @DisplayName("enforce 档：@DeviceExempt 放行 —— 否则领设备身份这件事就成了死循环")
    void 豁免() throws Exception {
        assertTrue(preHandle(interceptor(DeviceAuthProperties.Mode.ENFORCE), "exempt"));
    }

    @Test
    @DisplayName("enforce 档：有设备身份就放行")
    void 有设备就放行() throws Exception {
        DeviceIdentity identity = codec().verify(codec().issue(DEVICE_ID, "APP"));
        DeviceRequirementInterceptor i = interceptor(DeviceAuthProperties.Mode.ENFORCE);

        // 令牌是另一个 codec 实例签的，但两者密钥相同，所以验得过
        assertNotNull(identity);
        boolean passed = ScopedValue.where(CurrentDevice.DEVICE, identity)
                .call(() -> preHandle(i, "normal"));

        assertTrue(passed);
    }

    @Test
    @DisplayName("不是 HandlerMethod（静态资源、错误转发）→ 放行，交给后面的环节")
    void 非处理器方法() {
        DeviceRequirementInterceptor i = interceptor(DeviceAuthProperties.Mode.ENFORCE);
        assertTrue(i.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
    }
}
