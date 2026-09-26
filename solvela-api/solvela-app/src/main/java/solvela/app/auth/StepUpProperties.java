package solvela.app.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关<b>要不要执行</b>二次验证。
 *
 * <pre>
 * solvela:
 *   app:
 *     step-up:
 *       mode: off        # off | observe | enforce
 * </pre>
 *
 * <p>与 {@link DeviceAuthProperties} 划的是同一条线：属于端的「拦不拦」在这里，
 * 属于域的「什么叫受信任的设备」在 {@code solvela.member.step-up.*}。
 *
 * @param mode 见 {@link Mode}
 *
 * @Date 2026-09-26
 */
@ConfigurationProperties(prefix = "solvela.app.step-up")
public record StepUpProperties(Mode mode) {

    public StepUpProperties {
        // 🔴 默认必须是 OFF。漏配的后果不对称：默认 OFF 只是「保护还没生效」，
        //    默认 ENFORCE 是「前端还没接弹窗时，所有新设备用户都加不了地址」
        mode = mode == null ? Mode.OFF : mode;
    }

    public enum Mode {

        /** 什么都不做，连下游都不调。 */
        OFF,

        /**
         * 照常判断信任，<b>放行</b>，把「本来会被拦下的请求」打成 WARN。
         *
         * <p>那条日志就是切 enforce 之前唯一的依据：新设备用户里有多少人会被打扰、
         * 集中在哪个接口。门槛 7 天是拍出来的，要先拿真实流量看一眼。
         */
        OBSERVE,

        /**
         * 不受信任的设备调标注了 {@link StepUpRequired} 的接口，返回 {@code STEP_UP_REQUIRED}。
         *
         * <p>⚠️ 切到这一档之前，客户端必须已经会处理这个错误码（弹验证码、验证后重试），
         * 否则表现是「新设备上加不了地址」且没有任何出路。
         */
        ENFORCE,
    }
}
