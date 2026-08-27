package solvela.app.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import solvela.app.auth.AuthorizationInterceptor;

/**
 * C 端的 MVC 装配。
 *
 * <p>只注册一个拦截器，<b>没有路径白名单</b>。
 * 免登录靠方法上的 {@code @Anonymous} —— 白名单按前缀匹配，
 * 加一条就可能连带放行未来新增的同前缀接口，而那件事不会有人收到通知。
 *
 * <p>接口文档（springdoc / knife4j）的路径由 {@code springdoc.*} 配置控制，
 * 在 prod 环境整体关闭（见 application.yaml），所以这里也不需要为它开口子 ——
 * 文档端点即使被拦截器挡住返回 401，也不影响任何业务。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthorizationInterceptor authorizationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/**")
                // 🔴 必须放行 /error。Spring Boot 把没有匹配到 handler 的请求转发到这里，
                // 而转发也会再走一遍拦截器 —— 不放行的话，匿名用户访问一个不存在的路径
                // 拿到的是 401 而不是 404，看起来像「这个接口需要登录」，
                // 排查时会往完全错误的方向找。
                .excludePathPatterns("/error");
    }
}
