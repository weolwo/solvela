package solvela.app.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.auth.device.DeviceIdentity;
import solvela.member.api.DeviceTrust;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.StepUpCmd;
import solvela.member.api.StepUpResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二次验证拦截器：三个档位 × 四种信任结果。纯单元测试，下游用手写的假实现。
 *
 * <p>最要紧的几条：
 * <ul>
 *   <li>没标注的方法<b>不调下游</b> —— 否则每个请求都多一次跨进程调用；</li>
 *   <li>observe 档<b>永不拦截</b>，下游挂了也放行；</li>
 *   <li>🔴 enforce 档下游挂了<b>不放行</b> —— 守资产出口的闸，判断不了不能默认开门。</li>
 * </ul>
 *
 * @Date 2026-09-26
 */
class StepUpInterceptorTest {

    private static final String DEVICE_ID = "c3".repeat(16);

    private static final MemberPrincipal MEMBER = new MemberPrincipal(1001L, "m1001", "n", null, null);

    /** 可控的下游：返回给定的信任结果，或抛异常；记下被调了几次、收到的入参 */
    private static final class FakeStepUpApi implements MemberStepUpApi {

        private final DeviceTrust trust;

        private final RuntimeException failure;

        int checkCalls;

        StepUpCmd lastCmd;

        FakeStepUpApi(DeviceTrust trust, RuntimeException failure) {
            this.trust = trust;
            this.failure = failure;
        }

        @Override
        public DeviceTrust check(StepUpCmd cmd) {
            checkCalls++;
            lastCmd = cmd;
            if (failure != null) {
                throw failure;
            }
            return trust;
        }

        @Override
        public StepUpResult sendCode(StepUpCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StepUpResult verify(StepUpCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean revokeOthers(StepUpCmd cmd) {
            throw new UnsupportedOperationException();
        }
    }

    static class Handlers {

        @StepUpRequired
        public void guarded() {
        }

        public void plain() {
        }
    }

    private static HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Handlers(), Handlers.class.getMethod(method));
    }

    private static StepUpInterceptor interceptor(StepUpProperties.Mode mode, FakeStepUpApi api) {
        return new StepUpInterceptor(new StepUpProperties(mode), api);
    }

    /** 在「已登录 + 有设备」的作用域里跑一次 preHandle */
    private static boolean run(StepUpInterceptor i, String method) throws Exception {
        DeviceIdentity device = new DeviceIdentity(DEVICE_ID, "H5", 1, Instant.now());
        return ScopedValue.where(CurrentMember.MEMBER, MEMBER)
                .where(CurrentDevice.DEVICE, device)
                .call(() -> i.preHandle(
                        new MockHttpServletRequest("POST", "/address"), new MockHttpServletResponse(), handler(method)));
    }

    /** 已登录但没有设备 */
    private static boolean runWithoutDevice(StepUpInterceptor i) throws Exception {
        return ScopedValue.where(CurrentMember.MEMBER, MEMBER)
                .call(() -> i.preHandle(
                        new MockHttpServletRequest("POST", "/address"), new MockHttpServletResponse(), handler("guarded")));
    }

    // ============================== off ==============================

    @Test
    @DisplayName("off 档：什么都不做，连下游都不调")
    void off档不调下游() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(DeviceTrust.NEW_DEVICE, null);
        assertTrue(run(interceptor(StepUpProperties.Mode.OFF, api), "guarded"));
        assertEquals(0, api.checkCalls);
    }

    // ============================== 注解 ==============================

    @Test
    @DisplayName("没标 @StepUpRequired 的方法：放行，且不调下游")
    void 未标注不调下游() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(DeviceTrust.NEW_DEVICE, null);
        assertTrue(run(interceptor(StepUpProperties.Mode.ENFORCE, api), "plain"));
        assertEquals(0, api.checkCalls, "每个请求都调一次会员服务的话，整站多一跳跨进程调用");
    }

    @Test
    @DisplayName("没登录：放行，交给授权拦截器 —— 不替它做判断")
    void 未登录放行() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(DeviceTrust.NEW_DEVICE, null);
        StepUpInterceptor i = interceptor(StepUpProperties.Mode.ENFORCE, api);
        assertTrue(i.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler("guarded")));
        assertEquals(0, api.checkCalls);
    }

    // ============================== enforce ==============================

    @Test
    @DisplayName("enforce：受信任（验证过 / 老交情）→ 放行，入参是登录态里的会员与验签过的设备")
    void enforce受信任放行() throws Exception {
        for (DeviceTrust trust : new DeviceTrust[]{DeviceTrust.VERIFIED, DeviceTrust.HISTORY}) {
            FakeStepUpApi api = new FakeStepUpApi(trust, null);
            assertTrue(run(interceptor(StepUpProperties.Mode.ENFORCE, api), "guarded"), trust.name());
            assertEquals(MEMBER.memberId(), api.lastCmd.memberId());
            assertEquals(DEVICE_ID, api.lastCmd.deviceId());
        }
    }

    @Test
    @DisplayName("enforce：新设备 → STEP_UP_REQUIRED")
    void enforce新设备要验证() {
        FakeStepUpApi api = new FakeStepUpApi(DeviceTrust.NEW_DEVICE, null);
        ApiException e = assertThrows(ApiException.class,
                () -> run(interceptor(StepUpProperties.Mode.ENFORCE, api), "guarded"));
        assertEquals(ApiErrors.STEP_UP_REQUIRED, e.error());
    }

    @Test
    @DisplayName("enforce：没有设备 → DEVICE_REQUIRED，而不是弹验证码（验了也没处记住，会死循环）")
    void enforce无设备要设备() {
        FakeStepUpApi api = new FakeStepUpApi(DeviceTrust.NO_DEVICE, null);
        ApiException e = assertThrows(ApiException.class,
                () -> runWithoutDevice(interceptor(StepUpProperties.Mode.ENFORCE, api)));
        assertEquals(ApiErrors.DEVICE_REQUIRED, e.error());
    }

    @Test
    @DisplayName("🔴 enforce：下游挂了 → 不放行（异常冒出去），守资产出口的闸不能默认开门")
    void enforce下游失败不放行() {
        FakeStepUpApi api = new FakeStepUpApi(null, new IllegalStateException("member service down"));
        assertThrows(IllegalStateException.class,
                () -> run(interceptor(StepUpProperties.Mode.ENFORCE, api), "guarded"));
    }

    // ============================== observe ==============================

    @Test
    @DisplayName("observe：新设备也放行（只记日志），但判断确实做了")
    void observe放行但判断() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(DeviceTrust.NEW_DEVICE, null);
        assertTrue(run(interceptor(StepUpProperties.Mode.OBSERVE, api), "guarded"));
        assertEquals(1, api.checkCalls, "observe 的价值全在那条日志，不判断就没有日志");
    }

    @Test
    @DisplayName("observe：下游挂了也放行 —— 观察档的承诺是不影响任何请求")
    void observe下游失败放行() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(null, new IllegalStateException("member service down"));
        assertTrue(run(interceptor(StepUpProperties.Mode.OBSERVE, api), "guarded"));
    }

    // ============================== 标注清单 ==============================

    @Test
    @DisplayName("🔴 资产出口三个接口都标了 @StepUpRequired —— 漏标一个，这道闸就有一个后门")
    void 出口接口都已标注() throws Exception {
        assertTrue(solvela.app.controller.AddressController.class
                .getMethod("create", solvela.app.domain.AddressRequest.class)
                .isAnnotationPresent(StepUpRequired.class), "POST /address");
        assertTrue(solvela.app.controller.AddressController.class
                .getMethod("update", Long.class, solvela.app.domain.AddressRequest.class)
                .isAnnotationPresent(StepUpRequired.class), "PUT /address/{id}");
        assertTrue(solvela.app.controller.RechargeController.class
                .getMethod("create", solvela.app.domain.RechargeRequest.class)
                .isAnnotationPresent(StepUpRequired.class), "POST /recharge/order");
    }
}
