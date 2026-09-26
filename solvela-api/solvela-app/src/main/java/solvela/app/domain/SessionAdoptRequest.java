package solvela.app.domain;

/**
 * 把请求头里的旧令牌搬进 HttpOnly cookie（Web 端从 localStorage 迁移时调一次）。
 *
 * @param remember 旧令牌原来存在 localStorage（true，记住我）还是 sessionStorage（false）。
 *                 决定下发持久 cookie 还是会话 cookie，让迁移前后「关浏览器会不会掉线」保持一致
 */
public record SessionAdoptRequest(Boolean remember) {

    public boolean rememberMe() {
        return Boolean.TRUE.equals(remember);
    }
}
