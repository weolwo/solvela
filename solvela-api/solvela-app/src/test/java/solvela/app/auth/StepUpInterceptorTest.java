package solvela.app.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.auth.device.DeviceIdentity;
import solvela.member.api.AssetOutRisk;
import solvela.member.api.AssetOutVerdict;
import solvela.member.api.DeviceTrust;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.SessionReportCmd;
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
 *   <li>observe 档对<b>信任</b>永不拦截，下游挂了也放行；</li>
 *   <li>🔴 enforce 档下游挂了<b>不放行</b> —— 守资产出口的闸，判断不了不能默认开门；</li>
 *   <li>🔴 「这不是我」造成的<b>冻结</b>连 off / observe 档也拦，而且压倒设备信任 ——
 *       那个开关管的是二次验证，而冻结是用户自己要求的保护。</li>
 * </ul>
 *
 * @Date 2026-09-26
 */
class StepUpInterceptorTest {

    private static final String DEVICE_ID = "c3".repeat(16);

    private static final MemberPrincipal MEMBER = new MemberPrincipal(1001L, "m1001", "n", null, null);

/** 可控的下游：返回给定的「冻结 + 信任」，或抛异常；记下被调了几次、收到的入参 */
    private static final class FakeStepUpApi implements MemberStepUpApi {

        private final boolean frozen;

        private final DeviceTrust trust;

        private final RuntimeException failure;

        /** 拦截器问下游的次数。它只该通过 {@link #assetOut} 问，一次拿到两个答案 */
        int checkCalls;

        StepUpCmd lastCmd;

        FakeStepUpApi(DeviceTrust trust, RuntimeException failure) {
            this(false, trust, failure);
        }

        FakeStepUpApi(boolean frozen, DeviceTrust trust, RuntimeException failure) {
            this.frozen = frozen;
            this.trust = trust;
            this.failure = failure;
        }

        /** 风控结论。默认没有意见 —— 既有用例一个都不用改 */
        AssetOutRisk risk = AssetOutRisk.PASS;

        @Override
        public DeviceTrust check(StepUpCmd cmd) {
            throw new UnsupportedOperationException(
                    "拦截器要一次拿到「冻结 + 信任」两个答案，所以只能走 assetOut");
        }

        @Override
        public AssetOutVerdict assetOut(StepUpCmd cmd) {
            checkCalls++;
            lastCmd = cmd;
            if (failure != null) {
                throw failure;
            }
            return frozen
                    ? AssetOutVerdict.ofFrozen()
                    : AssetOutVerdict.of(trust, risk, risk == AssetOutRisk.BLOCKED ? 600L : 0L);
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

        @Override
        public boolean reportNotMe(SessionReportCmd cmd) {
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
    @DisplayName("off 档：不看信任（新设备照样放行），但仍然问一次下游 —— 冻结要拦")
    void off档只看冻结() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(DeviceTrust.NEW_DEVICE, null);
        assertTrue(run(interceptor(StepUpProperties.Mode.OFF, api), "guarded"));
        assertEquals(1, api.checkCalls,
                "🔴 这个开关管的是二次验证，而冻结是用户自己要求的保护 —— "
                        + "绑在一个开关上，意味着关掉二次验证顺带把「这不是我」作废了");
    }

    @Test
    @DisplayName("🔴 off 档下游挂了 → 放行。这一档连二次验证都不要，为一次抖动挡住所有人代价更大")
    void off档下游失败放行() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(null, new IllegalStateException("member service down"));
        assertTrue(run(interceptor(StepUpProperties.Mode.OFF, api), "guarded"));
    }

    // ============================== 资产出口冻结（「这不是我」） ==============================

    @Test
    @DisplayName("🔴 冻结压倒信任：受信任的老设备上也拦 —— 举报的人很可能正拿着它")
    void 冻结压倒信任() {
        FakeStepUpApi api = new FakeStepUpApi(true, DeviceTrust.VERIFIED, null);
        ApiException e = assertThrows(ApiException.class,
                () -> run(interceptor(StepUpProperties.Mode.ENFORCE, api), "guarded"));
        assertEquals(ApiErrors.ASSET_OUT_FROZEN, e.error(),
                "🔴 不能复用 STEP_UP_REQUIRED：那个码会让客户端弹验证码框，而这一档验过也过不去");
        assertEquals("PASSWORD_RESET", e.details().get("unlock"),
                "前端要据它决定那颗按钮跳哪儿");
    }

    @Test
    @DisplayName("🔴 observe 档也拦冻结 —— 否则用户点了「这不是我」却什么也没发生")
    void observe档也拦冻结() {
        FakeStepUpApi api = new FakeStepUpApi(true, DeviceTrust.VERIFIED, null);
        assertThrows(ApiException.class,
                () -> run(interceptor(StepUpProperties.Mode.OBSERVE, api), "guarded"));
    }

    @Test
    @DisplayName("🔴 off 档也拦冻结")
    void off档也拦冻结() {
        FakeStepUpApi api = new FakeStepUpApi(true, DeviceTrust.VERIFIED, null);
        ApiException e = assertThrows(ApiException.class,
                () -> run(interceptor(StepUpProperties.Mode.OFF, api), "guarded"));
        assertEquals(ApiErrors.ASSET_OUT_FROZEN, e.error());
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

    // ============================== 风控（2026-10-16） ==============================

    @Test
    @DisplayName("🔴 风控要求核身时，【受信任的老设备】也要验 —— 整个改造的落点就在这里")
    void 风控压倒信任() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(false, DeviceTrust.VERIFIED, null);
        api.risk = AssetOutRisk.STEP_UP;

        ApiException e = assertThrows(ApiException.class,
                () -> run(interceptor(StepUpProperties.Mode.ENFORCE, api), "guarded"));

        // 复用 STEP_UP_REQUIRED：对客户端与「新设备要验码」完全一样，前端一行都不用改
        assertEquals(ApiErrors.STEP_UP_REQUIRED, e.error(),
                "check() 回答的是「这台设备可信吗」，它不回答「这一次操作像不像出事了」—— "
                        + "一台验证过的老设备被偷了会话之后照样可信");
    }

    @Test
    @DisplayName("风控没意见时，受信任的设备照常放行（没有顺手变严）")
    void 风控没意见不改变现状() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(false, DeviceTrust.VERIFIED, null);
        assertTrue(run(interceptor(StepUpProperties.Mode.ENFORCE, api), "guarded"));
    }

    @Test
    @DisplayName("🔴 风控硬拦 → 429，而且【不】用 ASSET_OUT_FROZEN（改密码也不会恢复）")
    void 风控硬拦() throws Exception {
        FakeStepUpApi api = new FakeStepUpApi(false, DeviceTrust.VERIFIED, null);
        api.risk = AssetOutRisk.BLOCKED;

        ApiException e = assertThrows(ApiException.class,
                () -> run(interceptor(StepUpProperties.Mode.ENFORCE, api), "guarded"));

        assertEquals(ApiErrors.OPERATION_LIMITED, e.error(),
                "带「重置密码后恢复」提示的那个码会把用户支到一条走不通的路上");
    }

    @Test
    @DisplayName("🔴 硬拦不是二次验证：off / observe 档照拦；要求核身则不（observe 只记日志）")
    void 两档开关下的风控() throws Exception {
        for (StepUpProperties.Mode mode : new StepUpProperties.Mode[]{
                StepUpProperties.Mode.OFF, StepUpProperties.Mode.OBSERVE}) {
            FakeStepUpApi blocked = new FakeStepUpApi(false, DeviceTrust.NEW_DEVICE, null);
            blocked.risk = AssetOutRisk.BLOCKED;
            ApiException e = assertThrows(ApiException.class,
                    () -> run(interceptor(mode, blocked), "guarded"), mode + " 档该拦住硬拦");
            assertEquals(ApiErrors.OPERATION_LIMITED, e.error());

            // 「要求核身」就是二次验证本身，所以这两档不该要求它 ——
            // 否则 observe 那句「不影响任何请求」就是假的
            FakeStepUpApi stepUp = new FakeStepUpApi(false, DeviceTrust.NEW_DEVICE, null);
            stepUp.risk = AssetOutRisk.STEP_UP;
            assertTrue(run(interceptor(mode, stepUp), "guarded"), mode + " 档不该要求核身");
        }
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
