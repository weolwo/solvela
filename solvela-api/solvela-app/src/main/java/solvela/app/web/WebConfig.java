package solvela.app.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import solvela.app.auth.AuthorizationInterceptor;
import solvela.app.auth.CrossOriginGuardInterceptor;
import solvela.app.auth.DeviceRequirementInterceptor;
import solvela.app.auth.StepUpInterceptor;

/**
 * C 端的 MVC 装配。
 *
 * <p>四个拦截器，<b>都没有路径白名单</b>。免登录靠方法上的 {@code @Anonymous}、
 * 免设备靠 {@code @DeviceExempt} —— 白名单按前缀匹配，加一条就可能连带放行
 * 未来新增的同前缀接口，而那件事不会有人收到通知。
 *
 * <p>顺序是<b>设备在前、会员在后</b>：设备是更外层的身份。实际差别只有一个 ——
 * 一个既没登录又没设备的请求，enforce 档下拿到的是 DEVICE_REQUIRED 而不是
 * LOGIN_REQUIRED，而让用户去登录解决不了他客户端太旧的问题。
 *
 * <p>接口文档（springdoc / knife4j）的路径由 {@code springdoc.*} 配置控制，
 * 在 prod 环境整体关闭（见 application.yaml），所以这里也不需要为它开口子 ——
 * 文档端点即使被拦截器挡住返回 401，也不影响任何业务。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CrossOriginGuardInterceptor crossOriginGuardInterceptor;

    private final DeviceRequirementInterceptor deviceRequirementInterceptor;

    private final AuthorizationInterceptor authorizationInterceptor;

    private final StepUpInterceptor stepUpInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 跨站写请求防护排最前：来源不对的请求，连「你是谁」都不必问。
        // 它对所有写请求生效（含登录、注册），理由见 CrossOriginGuardInterceptor 类注释
        registry.addInterceptor(crossOriginGuardInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");

        registry.addInterceptor(deviceRequirementInterceptor)
                .addPathPatterns("/**")
                // 同下：/error 必须放行，否则 404 会变成 401
                .excludePathPatterns("/error");

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/**")
                // 🔴 必须放行 /error。Spring Boot 把没有匹配到 handler 的请求转发到这里，
                // 而转发也会再走一遍拦截器 —— 不放行的话，匿名用户访问一个不存在的路径
                // 拿到的是 401 而不是 404，看起来像「这个接口需要登录」，
                // 排查时会往完全错误的方向找。
                .excludePathPatterns("/error");

        // 二次验证排在授权之后：信任是「会员 × 设备」的关系，先有会员才谈得上。
        // 只对标了 @StepUpRequired 的方法生效，其余请求在拦截器里第一行就放行，不调下游
        registry.addInterceptor(stepUpInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}
