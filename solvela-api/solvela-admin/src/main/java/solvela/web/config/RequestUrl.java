package solvela.web.config;

/**
 * 一条需要做权限校验的请求路径，由 {@link UrlConfig} 在启动时扫描 Controller 得出。
 *
 * <p>住在 solvela-web 而不是 base：它描述的是「HTTP 路径 ↔ Controller 方法」，
 * 只有认识 Spring MVC 的那一层才产生得出来，也只有它才需要。
 *
 * @param comment Swagger {@code @Operation#summary}，给菜单配置界面看的说明
 * @param name    {@code 控制器名.方法名}
 * @param url     请求路径
 */
public record RequestUrl(String comment, String name, String url) {
}
