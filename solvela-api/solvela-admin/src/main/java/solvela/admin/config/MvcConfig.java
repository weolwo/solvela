package solvela.admin.config;

import jakarta.annotation.Resource;
import solvela.admin.auth.AdminAuthorizationInterceptor;
import solvela.base.config.FileConfig;
import solvela.base.constant.SwaggerWhitelistConst;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.StringHttpMessageConverter;
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
    private AdminAuthorizationInterceptor authorizationInterceptor;

    @Resource
    private NoContentInterceptor noContentInterceptor;



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
        registry.addInterceptor(authorizationInterceptor)
                .excludePathPatterns(whitelist)
                .addPathPatterns("/**");
        // 没有返回值的接口回 204，见 NoContentInterceptor
        registry.addInterceptor(noContentInterceptor).addPathPatterns("/**");
    }

    /**
     * 去掉 {@link StringHttpMessageConverter}，让返回 {@code String} 的接口也走 JSON。
     *
     * <h3>为什么必须动这个</h3>
     * 去掉 {@code ResponseDTO} 之后，「生成一个编码」「批量下线的汇总文案」这类接口的返回类型
     * 自然变成了 {@code String}。而 Spring 默认用 {@code StringHttpMessageConverter} 处理它 ——
     * 浏览器发来的 {@code Accept: application/json, text/plain, *&#47;*} 里有 text/plain，
     * 于是这十九个接口会以 <b>text/plain</b> 返回裸文本，而同一套 API 的其余接口是 JSON。
     *
     * <p>后果不是「不好看」：前端拦截器要按 content-type 分流，一套 API 里两种格式意味着
     * 每个调用点都得知道自己属于哪一类。去掉这个转换器之后，{@code String} 由 Jackson 序列化成
     * 一个 JSON 字符串（带引号），整套 API 的响应格式收敛成一种。
     *
     * <p>不影响文件下载：那条路径是直接往 {@code HttpServletResponse} 的输出流里写字节，
     * 根本不经过消息转换器。
     */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(
                converters -> converters.removeIf(StringHttpMessageConverter.class::isInstance));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
