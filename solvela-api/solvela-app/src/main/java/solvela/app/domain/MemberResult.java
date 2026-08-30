package solvela.app.domain;

import solvela.app.auth.MemberPrincipal;

/**
 * 登录成功的返回。
 *
 * <p>把令牌和会员信息<b>分成两层</b>，而不是像上一版那样平铺成一个大对象：
 * 令牌是凭证，会员信息是数据，客户端对它们的处理完全不同 ——
 * 前者进安全存储，后者进内存或界面。平铺的结果是客户端得自己知道哪几个字段要保密。
 *
 * @param accessToken 访问令牌，放进后续请求的 {@code Authorization: Bearer} 头
 * @param expiresIn   有效期秒数。给客户端用来提前续期，而不是等到 401 才反应
 * @param member      当前会员的公开信息
 */
public record MemberResult(String accessToken, long expiresIn, MemberPrincipal member) {
}
