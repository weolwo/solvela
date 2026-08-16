package sa.admin.config;

import jakarta.annotation.Resource;
import sa.admin.interceptor.AdminInterceptor;
import sa.base.config.FileConfig;
import sa.base.constant.SwaggerWhitelistConst;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * web相关配置
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021-09-02 20:21:10
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private AdminInterceptor adminInterceptor;



    /**
     * 免登录路径。
     *
     * <p>文件读取口必须在这里 —— <b>C 端用户是匿名的，永远带不上 token</b>，
     * 活动展示图和富文本正文里的图走的都是这条路，见 {@code FileController#publicAccess}。
     * 本模块的文件一律公开（服务的是活动/任务配置，不是网盘），所以放行的是全部素材。
     *
     * <p>⚠️ 往这个数组里加东西之前想清楚：这里的每一条都是**完全无鉴权**的。
     * 将来若开放 C 端用户上传，那条链路要单独设计（配额/审核），别顺手挂到这里。
     */
    private static final String[] PUBLIC_FILE_WHITELIST = {FileConfig.PUBLIC_FILE_MAPPING + "/**"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] whitelist = Stream.concat(
                        Arrays.stream(SwaggerWhitelistConst.SWAGGER_WHITELIST),
                        Arrays.stream(PUBLIC_FILE_WHITELIST))
                .toArray(String[]::new);
        registry.addInterceptor(adminInterceptor)
                .excludePathPatterns(whitelist)
                .addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
