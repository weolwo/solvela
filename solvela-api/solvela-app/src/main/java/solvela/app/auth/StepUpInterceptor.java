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
import solvela.member.api.AssetOutRisk;
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
 * <h3>🔴 顺序：冻结 → 风控 → 信任（2026-10-16 中间那一档是新加的）</h3>
 * 风控<b>压倒信任</b>：{@link MemberStepUpApi#check} 回答「这台设备可信吗」，
 * 它不回答「这一次操作像不像出事了」—— 一台「验证过」的老设备被偷了会话之后照样可信。
 * 所以风控说要核身时，<b>可信设备也要验</b>（{@link AssetOutRisk#STEP_UP}）。
 *
 * <p>这一档刻意复用 {@code STEP_UP_REQUIRED}：对客户端与「新设备要验码」完全一样，
 * 前端一行都不用改，而误判的代价也停在「多收一封信、多输六位数」。
 *
 * <p>⚠️ 风控的<b>硬拦</b>（黑名单、设备封禁）与<b>要求核身</b>在三个档位下不一样：
 * <ul>
 *   <li><b>硬拦</b>：和冻结同一类 —— 它不是「二次验证」，所以 off / observe 档也照拦；</li>
 *   <li><b>要求核身</b>：它<b>就是</b>二次验证，所以 off 档不要求、observe 档只记日志。
 *       否则 observe 那句「不影响任何请求」就是假的。</li>
 * </ul>
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
            return !hardGates(cmd, request);
        }
        if (properties.mode() == StepUpProperties.Mode.OBSERVE) {
            return !observe(cmd, request);
        }

        AssetOutVerdict verdict = stepUpApi.assetOut(cmd);
        if (verdict.frozen()) {
            throw assetOutFrozen(memberId, request);
        }
        if (verdict.risk() == AssetOutRisk.BLOCKED) {
            throw riskBlocked(cmd, request, verdict.retryAfterSeconds());
        }
        DeviceTrust trust = verdict.trust();
        // 🔴 风控要核身时【不看信任】—— 这就是整个改造的落点
        if (trust.trusted() && verdict.risk() != AssetOutRisk.STEP_UP) {
            return true;
        }
        if (verdict.risk() == AssetOutRisk.STEP_UP && trust.trusted()) {
            log.info("【二次验证】风控要求可信设备也核身, memberId: {}, deviceId: {}, 信任: {}, 接口: {} {}",
                    memberId, deviceId, trust, request.getMethod(), request.getRequestURI());
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
            // 硬拦不是「二次验证」，所以观察档也照拦（与冻结同一类）
            if (verdict.risk() == AssetOutRisk.BLOCKED) {
                throw riskBlocked(cmd, request, verdict.retryAfterSeconds());
            }
            if (verdict.risk() == AssetOutRisk.STEP_UP) {
                log.warn("【二次验证】风控要求核身但放行[observe], memberId: {}, deviceId: {}, 信任: {}, 接口: {} {}",
                        cmd.memberId(), cmd.deviceId(), verdict.trust(),
                        request.getMethod(), request.getRequestURI());
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
     * 二次验证整体关掉时，只判<b>两道硬闸</b>：冻结、风控硬拦。
     *
     * <p>🔴 这两道都不是「二次验证」，所以那个开关管不着它们 ——
     * 冻结是用户<b>自己要求</b>的保护，风控硬拦来自人工签过字的黑名单与设备封禁。
     * 把它们也关掉，意味着关掉二次验证顺带把那两件事一起作废。
     *
     * <p>🔴 判断失败时<b>放行</b>：这一档下连二次验证都不要，为了一次下游抖动
     * 把所有人的加地址、充话费挡住，代价远大于让一个被冻结的账号漏过一次。
     * enforce 档的取舍正好相反（见类注释）。
     *
     * @return 是否要拦下这个请求
     */
    private boolean hardGates(StepUpCmd cmd, HttpServletRequest request) {
        try {
            AssetOutVerdict verdict = stepUpApi.assetOut(cmd);
            if (verdict.frozen()) {
                throw assetOutFrozen(cmd.memberId(), request);
            }
            if (verdict.risk() == AssetOutRisk.BLOCKED) {
                throw riskBlocked(cmd, request, verdict.retryAfterSeconds());
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("【资产出口】判冻结 / 风控失败，已放行[off], memberId: {}, 接口: {} {}",
                    cmd.memberId(), request.getMethod(), request.getRequestURI(), e);
        }
        return false;
    }

    /**
     * 风控硬拦。
     *
     * <p>🔴 对外与限流<b>同一句话</b>，不说是不是被拉黑了 ——
     * 告诉对方具体原因等于给他一个精确的调试反馈，他换个 IP 就继续。
     * 也<b>不复用</b> {@code ASSET_OUT_FROZEN}：那个码带着「重置密码后恢复」的提示，
     * 而这一档改密码也不会恢复 —— 把用户支到一条走不通的路上，比不解释更糟。
     */
    private static ApiException riskBlocked(StepUpCmd cmd, HttpServletRequest request, long retryAfterSeconds) {
        log.warn("【资产出口】风控硬拦, memberId: {}, deviceId: {}, 还需等待 {} 秒, 接口: {} {}",
                cmd.memberId(), cmd.deviceId(), retryAfterSeconds,
                request.getMethod(), request.getRequestURI());
        return new ApiException(ApiErrors.OPERATION_LIMITED);
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
