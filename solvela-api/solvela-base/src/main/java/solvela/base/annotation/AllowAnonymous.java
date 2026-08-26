package solvela.base.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记<b>允许匿名访问</b>的接口方法：带上它的方法不校验登录态。
 *
 * <p>两处在读它，改动前都要想到：
 * <ul>
 *   <li>拦截器（{@code AdminInterceptor} / {@code MemberInterceptor}）—— 决定这次请求放不放行；</li>
 *   <li>{@code UrlConfig} —— 启动时扫全部方法，汇总成白名单 URL 清单。</li>
 * </ul>
 *
 * <p>⚠️ 管理端与会员端<b>共用这一个注解</b>，各自的拦截器只管自己那条链路。
 * 加在方法上就是对所有拦截它的链路都免校验 —— 别指望「它只对某一端生效」。
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
