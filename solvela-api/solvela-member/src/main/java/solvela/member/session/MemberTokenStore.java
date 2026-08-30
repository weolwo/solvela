package solvela.member.session;

/**
 * <b>会员</b>令牌的签发、解析与吊销。
 *
 * <p>类名带 Member 前缀不是啰嗦：管理端有一套自己的
 * {@code solvela.admin.auth.TokenStore}（员工令牌，另一套 key、另一套 TTL、另一套并发策略），
 * 而两者在同一个进程里共存 —— admin 进程既装配员工会话，也装配会员域。
 * 不加前缀时两个 {@code RedisTokenStore} 的默认 bean 名会撞车，启动即失败；
 * 更糟的是<b>类型名一样</b>，写代码时很容易注错一个。
 * 2026-08-30 搬到会员域时正好撞上过这个错，索性让名字自己说清楚是谁的会话。
 *
 * <p><b>存在的意义是让「用什么做令牌」成为一个可以改的决定。</b>
 * 上一版直接把 sa-token 的静态 {@code StpLogic} 铺在业务代码里，
 * 结果是：换不掉、测不了（每个测试都要起一整个 sa-token 上下文）、
 * 也看不出这个系统到底需要令牌做哪几件事 —— 那个类有两百多个方法，
 * 实际用到的只有下面这四个。
 *
 * <p>接口只留这四件事。多一个方法，就多一分让实现细节渗进业务代码的机会。
 */
public interface MemberTokenStore {

    /**
     * 为会员签发一个新令牌。同一会员可以有多个有效令牌（多设备）。
     */
    MemberAccessToken issue(Long memberId);

    /**
     * 令牌 → 会员号；无效、过期、已吊销都返回 null。
     *
     * <p>🔴 <b>不抛异常</b>。「令牌无效」是 C 端最常见的正常情况
     * （用户几周没打开、换了设备、主动退出过），不是异常事件。
     * 用异常表达它，会让调用方在最热的那条路径上写 try-catch，
     * 也会让监控里的异常率永远是噪声。
     */
    Long resolve(String tokenValue);

    /**
     * 吊销一个令牌（退出登录）。令牌不存在时静默返回 —— 退出是幂等的。
     */
    void revoke(String tokenValue);

    /**
     * 吊销该会员的<b>全部</b>令牌（改密码、被冻结、「退出所有设备」）。
     *
     * @return 实际吊销的数量
     */
    int revokeAll(Long memberId);
}
