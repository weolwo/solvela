package sa.base.constant;

/**
 * 文档相关的免鉴权路径白名单。
 *
 * <p>⚠️ 这个数组<b>必须放在一个不引用任何 springdoc / swagger 类型的类里</b>。
 * 它原本挂在 {@code SwaggerConfig} 上，而 {@code MvcConfig#addInterceptors} 要读它，
 * 于是把 {@code SwaggerConfig} 一起加载了 —— 该类的方法签名上有 {@code OperationCustomizer}、
 * {@code GroupedOpenApi} 等 springdoc 类型。生产包已把整套 springdoc / swagger 排除
 * （见 sa-admin/pom.xml 的 prod profile），结果就是启动期
 * {@code NoClassDefFoundError: org/springdoc/core/customizers/OperationCustomizer}。
 * 拆到这里之后，MvcConfig 与 springdoc 彻底解耦。
 *
 * <p>注意：这些路径在 prod 包里其实已经无人响应（类都不在），白名单留着只是让
 * dev / test 环境的拦截器配置保持同一份代码。
 */
public class SwaggerWhitelistConst {

    public static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-ui/index.html",
            "/swagger-ui.html",
            "/swagger-ui.html/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**",
            "/favicon.ico"
    };

    private SwaggerWhitelistConst() {
    }
}
