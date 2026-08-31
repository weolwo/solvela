package solvela.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记这个接口需要哪个权限点，例如 {@code @RequiresPermission("mallCategory:update")}。
 *
 * <h3>它取代的是什么</h3>
 * 原先用的是 sa-token 的 {@code @SaCheckPermission}，校验由框架的
 * {@code SaAnnotationStrategy.checkMethodAnnotation} 完成 —— 一个静态门面，
 * 内部再回调项目自己实现的 {@code StpInterface} 去取权限列表。
 * 也就是说：<b>权限数据是我们的、判定规则是我们的，中间那一跳是框架的</b>。
 * 代价是整条链路不可读、不可测（每个测试都要起一整套 sa-token 上下文），
 * 而且 226 个接口的鉴权行为取决于一个我们没写过的类。
 *
 * <p>现在判定就在 {@code AuthorizationInterceptor} 里，六行，可以直接读完。
 *
 * <h3>没标注解 = 需要登录，但不要求具体权限点</h3>
 * 这正是原先 {@code @SaIgnore} 表达的意思，所以那个注解没有对应物 —— 不写就是了。
 * 完全不需要登录的接口用 {@link AllowAnonymous}。
 *
 * <h3>只支持单个权限点，这是刻意的</h3>
 * sa-token 的版本支持数组 + {@code SaMode.AND/OR}。全项目 226 处用法<b>无一使用</b>，
 * 而「这个接口要 A 且 B、那个要 C 或 D」是一种一旦开口就会到处蔓延的复杂度：
 * 权限配置界面表达不了它，排查越权时也没人能一眼算出布尔表达式的值。
 * 真需要组合时，正确做法是在权限表里定义一个新的权限点。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

    /** 权限点编码，与 {@code t_menu.api_perms} 中的取值一致 */
    String value();
}
