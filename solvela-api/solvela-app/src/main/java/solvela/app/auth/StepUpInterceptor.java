package solvela.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.ClientIp;
import solvela.member.api.DeviceTrust;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.StepUpCmd;

/**
 * 不受信任的设备调用 {@link StepUpRequired} 接口时，要求先过二次验证。
 *
 * <h3>排在授权之后</h3>
 * 信任是「会员 × 设备」的关系，没有会员就无从谈起。授权拦截器已经保证了走到这里的请求有登录态；
 * 万一没有（将来有人给一个 {@code @StepUpRequired} 方法又标了 {@code @Anonymous}），这里放行，
 * 不替授权拦截器做判断。
 *
 * <h3>只有标注了的方法才会调下游</h3>
 * 判断信任要调一次会员服务（查登录日志），所以必须先看注解再调 —— 否则每个请求都多一次跨进程调用。
 *
 * <h3>下游挂了怎么办，按档位分</h3>
 * <ul>
 *   <li>observe：<b>放行</b>并打错误日志。观察档的承诺是「不影响任何请求」；</li>
 *   <li>enforce：<b>不放行</b>（异常冒到统一出口，500）。这道闸守的是资产出口，
 *       判断不了的时候默认放行，等于攻击者只要等会员服务抖一下就能过。</li>
 * </ul>
 *
 * @Date 2026-09-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StepUpInterceptor implements HandlerInterceptor {

    private final StepUpProperties properties;

    private final MemberStepUpApi stepUpApi;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (properties.mode() == StepUpProperties.Mode.OFF) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(StepUpRequired.class)) {
            return true;
        }
        if (!CurrentMember.isBound()) {
            return true;
        }

        Long memberId = CurrentMember.require().memberId();
        String deviceId = CurrentDevice.deviceIdOrNull();
        StepUpCmd cmd = StepUpCmd.of(memberId, deviceId, ClientIp.of(request));

        if (properties.mode() == StepUpProperties.Mode.OBSERVE) {
            observe(cmd, request);
            return true;
        }

        DeviceTrust trust = stepUpApi.check(cmd);
        if (trust.trusted()) {
            return true;
        }
        log.info("【二次验证】拦截, memberId: {}, deviceId: {}, 信任: {}, 接口: {} {}",
                memberId, deviceId, trust, request.getMethod(), request.getRequestURI());
        // 没有设备号时弹验证码没有用：验过了也没处记住，下一次还要验。
        // 让客户端先补设备身份（与设备令牌 enforce 档同一个码、同一套客户端处理）
        throw new ApiException(trust == DeviceTrust.NO_DEVICE ? ApiErrors.DEVICE_REQUIRED : ApiErrors.STEP_UP_REQUIRED);
    }

    /**
     * 观察档：判断照做，结果只进日志。
     *
     * <p>日志用 WARN 而不是 INFO：它是切 enforce 前校准门槛的全部依据，必须捞得出来。
     */
    private void observe(StepUpCmd cmd, HttpServletRequest request) {
        try {
            DeviceTrust trust = stepUpApi.check(cmd);
            if (!trust.trusted()) {
                log.warn("【二次验证】命中但放行[observe], memberId: {}, deviceId: {}, 信任: {}, 接口: {} {}",
                        cmd.memberId(), cmd.deviceId(), trust, request.getMethod(), request.getRequestURI());
            }
        } catch (RuntimeException e) {
            log.error("【二次验证】观察档判断信任失败，已放行, memberId: {}, 接口: {} {}",
                    cmd.memberId(), request.getMethod(), request.getRequestURI(), e);
        }
    }
}
