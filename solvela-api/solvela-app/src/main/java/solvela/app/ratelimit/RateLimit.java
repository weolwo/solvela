package solvela.app.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 这个接口归哪个限流场景（docs/架构/网关阶梯限流-技术方案.md §2.1）。
 *
 * <h3>🔴 每个 Controller 方法都必须标</h3>
 * 没有「没标就落进默认场景」的兜底：{@code RateLimitAnnotationTest} 扫描全部接口，漏标的让测试失败。
 * 「新增接口必须配限流」由这个测试保证，而不是由后台配置保证 —— 后台忘了配只能到线上才暴露，
 * 而且结果不是故障（没配就拒）就是漏洞（没配就放）。默认场景也要显式写，逼新接口的作者想一下它归哪类。
 *
 * <p>标注跟着方法走，URL 改了它不丢；后台还可以按接口单独覆盖阈值，不需要改这里。
 *
 * @Date 2026-10-17
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    RateLimitScene value();

    /** 只有 {@link RateLimitScene#EXEMPT} 需要：为什么这个接口不限流。测试会检查它不为空 */
    String reason() default "";
}
