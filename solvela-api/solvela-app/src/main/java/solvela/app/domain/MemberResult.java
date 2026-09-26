package solvela.app.domain;

import solvela.app.auth.MemberPrincipal;

/**
 * 登录成功的返回。
 *
 * <p>把令牌和会员信息<b>分成两层</b>，而不是像上一版那样平铺成一个大对象：
 * 令牌是凭证，会员信息是数据，客户端对它们的处理完全不同 ——
 * 前者进安全存储，后者进内存或界面。平铺的结果是客户端得自己知道哪几个字段要保密。
 *
 * <h3>🔴 Web 端（useCookie）拿到的 accessToken 是 null</h3>
 * 令牌只经 HttpOnly cookie 下发。响应体里再带一份的话，混进页面的脚本包一层 fetch
 * 就能把它截走，HttpOnly 等于白做（知识库《Web鉴权》§5.1）。
 *
 * @param accessToken 访问令牌，放进后续请求的 {@code Authorization: Bearer} 头；cookie 模式下为 null
 * @param expiresIn   有效期秒数。给客户端用来提前续期，而不是等到 401 才反应
 * @param member      当前会员的公开信息
 */
public record MemberResult(String accessToken, long expiresIn, MemberPrincipal member) {

    /** 同一个结果，去掉令牌原文 —— 令牌已经写进 cookie 了 */
    public MemberResult withoutToken() {
        return new MemberResult(null, expiresIn, member);
    }
}
