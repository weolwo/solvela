package net.lab1024.sa.admin.config;

import jakarta.annotation.Resource;
import net.lab1024.sa.admin.interceptor.AdminInterceptor;
import net.lab1024.sa.base.config.FileConfig;
import net.lab1024.sa.base.config.SwaggerConfig;
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
     * <p>公开文件的读取口必须在这里 —— <b>C 端用户是匿名的，永远带不上 token</b>，
     * 活动展示图走的就是这条路。它自己会查 {@code visibility}，私有文件一律 404，
     * 所以放行的只是"本来就该给所有人看的那些字节"，见 {@code FileController#publicAccess}。
     *
     * <p>⚠️ 往这个数组里加东西之前想清楚：这里的每一条都是**完全无鉴权**的。
     * 上一版把整个上传目录挂成静态资源，就是从"少想了一步"开始的。
     */
    private static final String[] PUBLIC_FILE_WHITELIST = {FileConfig.PUBLIC_FILE_MAPPING + "/**"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] whitelist = Stream.concat(
                        Arrays.stream(SwaggerConfig.SWAGGER_WHITELIST),
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
