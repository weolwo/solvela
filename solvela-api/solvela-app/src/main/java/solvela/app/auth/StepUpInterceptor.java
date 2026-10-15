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
import solvela.member.api.AssetOutVerdict;
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
 * <h3>🔴 它还守着「这不是我」造成的资产出口冻结</h3>
 * 冻结判断与信任判断<b>合成同一次下游调用</b>（{@link MemberStepUpApi#assetOut}）——
 * 分成两次的话，每一个加地址 / 充话费请求都要跨进程两趟。
 *
 * <p>冻结<b>压倒信任</b>，而且<b>连观察档也拦</b>：
 * <ul>
 *   <li>压倒信任 —— 举报的人很可能正拿着那台「受信任的老设备」，
 *       先判信任的话，会话被偷的盗号者照样搬得走东西；</li>
 *   <li>观察档也拦 —— 观察档的承诺是「二次验证不影响任何请求」，
 *       而冻结不是二次验证，它是用户<b>自己要求</b>的保护。
 *       因为观察档而放过，等于用户点了「这不是我」却什么也没发生。</li>
 * </ul>
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
        /*
         * 🔴 OFF 档也要拦冻结。那个开关管的是「二次验证」这件事，
         *    而冻结是用户自己要求的保护 —— 把它和二次验证绑在一个开关上，
         *    意味着关掉二次验证顺带把「这不是我」也作废了。
         */
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(StepUpRequired.class)) {
            return true;
        }
        if (!CurrentMember.isBound()) {
            return true;
        }

        Long memberId = CurrentMember.require().memberId();
        String deviceId = CurrentDevice.deviceIdOrNull();
        StepUpCmd cmd = StepUpCmd.of(memberId, deviceId, ClientIp.of(request));

        if (properties.mode() == StepUpProperties.Mode.OFF) {
            return !frozen(cmd, request);
        }
        if (properties.mode() == StepUpProperties.Mode.OBSERVE) {
            return !observe(cmd, request);
        }

        AssetOutVerdict verdict = stepUpApi.assetOut(cmd);
        if (verdict.frozen()) {
            throw assetOutFrozen(memberId, request);
        }
        DeviceTrust trust = verdict.trust();
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
     * 观察档：<b>信任</b>的判断照做、结果只进日志；<b>冻结</b>照常拦。
     *
     * <p>日志用 WARN 而不是 INFO：它是切 enforce 前校准门槛的全部依据，必须捞得出来。
     *
     * @return 是否要拦下这个请求（只有冻结会返回 true）
     */
    private boolean observe(StepUpCmd cmd, HttpServletRequest request) {
        try {
            AssetOutVerdict verdict = stepUpApi.assetOut(cmd);
            if (verdict.frozen()) {
                throw assetOutFrozen(cmd.memberId(), request);
            }
            if (!verdict.trust().trusted()) {
                log.warn("【二次验证】命中但放行[observe], memberId: {}, deviceId: {}, 信任: {}, 接口: {} {}",
                        cmd.memberId(), cmd.deviceId(), verdict.trust(),
                        request.getMethod(), request.getRequestURI());
            }
            return false;
        } catch (ApiException e) {
            // 冻结那一条要原样抛出去 —— 它不是「判断失败」
            throw e;
        } catch (RuntimeException e) {
            log.error("【二次验证】观察档判断信任失败，已放行, memberId: {}, 接口: {} {}",
                    cmd.memberId(), request.getMethod(), request.getRequestURI(), e);
            return false;
        }
    }

    /**
     * 二次验证整体关掉时，只判冻结。
     *
     * <p>🔴 判断失败时<b>放行</b>：这一档下连二次验证都不要，为了一次下游抖动
     * 把所有人的加地址、充话费挡住，代价远大于让一个被冻结的账号漏过一次。
     * enforce 档的取舍正好相反（见类注释）。
     *
     * @return 是否要拦下这个请求
     */
    private boolean frozen(StepUpCmd cmd, HttpServletRequest request) {
        try {
            if (stepUpApi.assetOut(cmd).frozen()) {
                throw assetOutFrozen(cmd.memberId(), request);
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("【资产出口】判冻结失败，已放行[off], memberId: {}, 接口: {} {}",
                    cmd.memberId(), request.getMethod(), request.getRequestURI(), e);
        }
        return false;
    }

    /**
     * 🔴 不复用 {@code STEP_UP_REQUIRED}：那个码会让客户端去弹验证码框，
     * 而这一档验过也过不去 —— 用户会验一次、失败，再验一次、再失败，完全不知道为什么。
     */
    private static ApiException assetOutFrozen(Long memberId, HttpServletRequest request) {
        log.warn("【资产出口】冻结中，拦下, memberId: {}, 接口: {} {}",
                memberId, request.getMethod(), request.getRequestURI());
        return new ApiException(ApiErrors.ASSET_OUT_FROZEN, ApiErrors.ASSET_OUT_FROZEN.defaultMessage(),
                // 事实，不是 UI：前端据它决定那颗按钮跳哪儿（方案 §6.4 约定 2）
                java.util.Map.of("unlock", "PASSWORD_RESET"));
    }
}
