package solarx.app.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import sa.base.constant.SwaggerWhitelistConst;
import solarx.app.interceptor.MemberInterceptor;

/**
 * 会员端 web 配置。
 *
 * @Date 2026-08-25
 */
@Configuration
public class AppMvcConfig implements WebMvcConfigurer {

    @Resource
    private MemberInterceptor memberInterceptor;

    /**
     * 拦截全部路径，只放行 API 文档。
     *
     * <p>⚠️ 与管理端的 {@code MvcConfig} 不同，这里<b>没有</b>放行文件读取路径
     * （{@code /support/file/public/**}）。原因是本进程压根没有 FileController ——
     * sa-base 的 controller 已被 {@code AppApplication} 的扫描规则整体排除，
     * 公开文件由管理端进程（1024，见 {@code file.storage.public-url-prefix}）或 CDN 提供。
     * 哪天要让本服务也发图，先想清楚「C 端上传」的配额与审核，别顺手加一条白名单。
     *
     * <p>🔴 免登录的<b>业务</b>接口不靠白名单，靠方法上的 {@code @NoNeedLogin} ——
     * 白名单是按路径前缀匹配的，加一条就可能连带放行未来新增的同前缀接口；
     * 注解则是一个方法一个决定，改错了 code review 看得见。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberInterceptor)
                .excludePathPatterns(SwaggerWhitelistConst.SWAGGER_WHITELIST)
                .addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
