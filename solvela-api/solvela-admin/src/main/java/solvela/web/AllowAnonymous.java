package solvela.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记<b>允许匿名访问</b>的接口方法：带上它的方法不校验登录态。
 *
 * <p>两处在读它，改动前都要想到：
 * <ul>
 *   <li>{@code AuthorizationInterceptor} —— 决定这次请求放不放行；</li>
 *   <li>{@code UrlConfig} —— 启动时扫全部方法，汇总成白名单 URL 清单。</li>
 * </ul>
 *
 * <p>它<b>不是</b>「不要权限点」的意思 —— 那个意思是「不写 {@link RequiresPermission}」。
 * 这里说的是「连登录都不要」，整个后台只有登录、发验证码、查双因子开关三个接口配得上。
 * 加一个之前先问一句：这个接口暴露在公网上、任何人都能打，可以吗？
 *
 * <p>住在 solvela-web 而不是 solvela-base：它是 HTTP 授权模型的一部分，
 * 只有认识 handler 的那一层读得懂它。C 端有自己的 {@code @Anonymous}，两端互不迁就。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2022-05-30 21:22:12
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AllowAnonymous {
}
