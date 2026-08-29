package solvela.admin.auth;

/**
 * 本次请求赖以通过认证的凭证。绑在 {@link CurrentEmployee} 的作用域上。
 *
 * <p>和 {@link AdminSession} 的区别：那个是<b>存在 Redis 里的</b>，所以不能带令牌原文；
 * 这个只活在一次请求的作用域内，可以带。
 *
 * <p>为什么不把这两个字段塞进 {@code RequestEmployee}：那个对象会进缓存、进操作日志、
 * 进接口返回值 —— 令牌不该跟着去这些地方。
 *
 * @param token          令牌原文，退出登录时要凭它吊销，{@code /login/getLoginInfo} 要回显它
 * @param superPassword  本次会话是否由万能密码建立。<b>唯一的用途</b>是不给这类会话弹
 *                       「请修改密码」——用万能密码进来的人并不知道那个账号的原密码，
 *                       让他改密码等于让他把别人的账号改掉
 */
public record Credential(String token, boolean superPassword) {
}
