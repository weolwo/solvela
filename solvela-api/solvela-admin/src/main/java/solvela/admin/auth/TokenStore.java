package solvela.admin.auth;

import java.time.Duration;

/**
 * 管理端令牌的签发、查询与吊销。
 *
 * <p><b>存在的意义是让「用什么做令牌」成为一个可以改的决定。</b>
 * 上一版把 sa-token 的静态 {@code StpUtil} 直接铺在业务代码里，结果是：
 * 换不掉、测不了（每个测试要起一整套 sa-token 上下文）、也看不出这个系统到底
 * 需要令牌做哪几件事 —— 那个门面有两百多个方法，实际用到的只有下面这五个。
 *
 * <p>接口只留这五件事。多一个方法，就多一分让实现细节渗进业务代码的机会。
 */
public interface TokenStore {

    /**
     * 为员工签发一个新令牌。
     *
     * <p>{@code singleSession} 打开时会先吊销该员工的全部旧令牌（挤号）。
     */
    AccessToken issue(Long employeeId, boolean superFlag, String device);

    /**
     * 用指定有效期签发。目前只有万能密码登录用它 —— 那种会话必须短。
     */
    AccessToken issue(Long employeeId, boolean superFlag, String device, Duration ttl);

    /**
     * 查询令牌，<b>并顺带刷新活跃时间</b>。
     *
     * <p>刷新放在这里而不是让调用方再调一次，是因为「查了但忘了刷新」的后果是
     * 用户在正常操作中被判定为长时间未操作 —— 这种 bug 只在配了活跃超时的环境上出现，
     * 而那通常是生产。让它没有被忘记的机会。
     */
    TokenLookup lookup(String tokenValue);

    /**
     * 吊销一个令牌（退出登录）。令牌不存在时静默返回 —— 退出是幂等的。
     */
    void revoke(String tokenValue);

    /**
     * 吊销该员工的<b>全部</b>令牌：禁用账号、改密码、强制下线。
     *
     * <p>🔴 原先这件事是 {@code StpUtil.logout(loginId)}，按拼出来的 loginId 字符串踢人，
     * <b>踢不掉万能密码登录的那条会话</b>（它的 loginId 是另一个格式）。
     * 也就是说：禁用一个员工之后，用万能密码登进去的会话还能继续用。
     * 改成按员工 id 反查全部令牌之后，这个口子没了。
     *
     * @return 实际吊销的数量
     */
    int revokeAll(Long employeeId);

    /**
     * 当前生效的「最低活跃频率」（秒），{@code <= 0} 表示不限制。
     *
     * <p>由 {@code Level3ProtectConfigService} 在等保配置变更时写入 —— 它是数据库配置，
     * 不是启动参数，改完要立刻生效。
     */
    void setActiveTimeoutSeconds(int seconds);
}
