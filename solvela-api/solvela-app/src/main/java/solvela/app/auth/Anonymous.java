package solvela.app.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在接口方法上：<b>不登录也能调</b>。
 *
 * <p>默认是「所有接口都要登录」，免登录必须逐个方法显式声明。反过来做（默认放行、
 * 要鉴权的才标注）看着更省事，但漏标的后果不对称：漏标一个 {@code @Anonymous}
 * 只是让用户多登录一次，漏标一个鉴权注解是把接口裸奔到公网。
 *
 * <p>🔴 <b>不要用路径白名单代替它。</b>白名单按前缀匹配，今天写下
 * {@code /activity/**} 是为了放行活动详情，明天有人在同一前缀下加了
 * {@code /activity/join}，就跟着一起放行了 —— 而这件事没有任何人会收到通知。
 * 注解是一个方法一个决定，加错了 code review 看得见。
 *
 * <p>标了本注解<b>不代表拿不到身份</b>：带了有效令牌的请求照样会被识别，
 * {@code CurrentMember.find()} 能取到人。「登录与否都能看，登录了多返回一点」
 * 是 C 端最常见的形态，这里刻意支持它。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Anonymous {
}
