package solvela.app.config;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 会员端的 sa-token 入口。<b>本模块一律用它，不要用 {@code StpUtil}。</b>
 *
 * <h3>为什么不能直接用 StpUtil</h3>
 * 管理端（1024）与本服务（1025）连的是<b>同一个 Redis</b>。{@code StpUtil} 用的是默认
 * loginType {@code "login"}，两边都用它的话，token 存的是同一批 key：
 * <pre>
 *   satoken:login:token:{tokenValue}  ->  loginId
 * </pre>
 * 于是<b>员工的 token 能直接通过会员端的登录校验</b>——{@code getLoginIdByToken} 查得到值、
 * 不抛异常，接下来只剩「loginId 前缀是不是 2:」这一道字符串判断在挡着。
 * 把跨系统的身份隔离建立在一次前缀比较上，是那种平时看不出问题、
 * 出问题时是越权访问的设计。
 *
 * <p>换成独立 loginType 之后，key 变成 {@code satoken:member:token:{tokenValue}}，
 * 两套令牌在存储层就是不同的命名空间：拿员工 token 打会员接口，
 * 查不到 → 直接 NotLogin，根本走不到业务代码。反之亦然。
 *
 * <h3>配置怎么走</h3>
 * 不另配一套。{@code StpLogic} 未单独指定配置时读全局的 {@code sa-token.*}
 * （见 solvela-base.yaml：token-name=Authorization、Bearer 前缀、30 天有效期），
 * 会员端与管理端的令牌参数保持一致。哪天会员端要单独设有效期，
 * 在这里给 {@code stpLogic} 塞一份独立的 {@code SaTokenConfig} 即可，调用方不用改。
 *
 * @Date 2026-08-25
 */
public class StpMemberUtil {

    /**
     * 账号体系标识。
     *
     * <p>🔴 这个字符串是 Redis key 的一部分（{@code satoken:member:*}）。
     * 上线后改它 = 所有在线会员当场掉线（老 key 再也不会被查到，且不会被清理，
     * 直到各自过期为止）。不是不能改，是改之前要知道代价。
     */
    public static final String TYPE = "member";

    private static final StpLogic STP_LOGIC = new StpLogic(TYPE);

    private StpMemberUtil() {
    }

    /**
     * 登录，签发 token。
     *
     * @param loginId 约定为 {@code UserTypeEnum.MEMBER.getValue() + ":" + memberId}，
     *                由 {@code MemberLoginService} 统一拼装 —— 别在别处手拼
     * @param device  设备标识，用于「按设备下线」和登录日志归因
     */
    public static void login(String loginId, String device) {
        STP_LOGIC.login(loginId, device);
    }

    /**
     * 取当前请求携带的 token 原文；没带返回 null。
     */
    public static String getTokenValue() {
        return STP_LOGIC.getTokenValue();
    }

    /**
     * token -> loginId。token 无效/过期/被顶下线时抛 {@code SaTokenException}，
     * 由 {@code MemberInterceptor} 统一翻译成业务错误码，不要在调用处各自 catch。
     */
    public static Object getLoginIdByToken(String tokenValue) {
        return STP_LOGIC.getLoginIdByToken(tokenValue);
    }

    /**
     * 续期：把 token 的「最后活跃时间」刷到当前。
     * 只有配置了 {@code active-timeout} 才有意义（当前全局是 -1，即不冻结）。
     */
    public static void updateLastActiveToNow() {
        STP_LOGIC.updateLastActiveToNow();
    }

    /**
     * 注销当前 token。
     */
    public static void logout() {
        STP_LOGIC.logout();
    }
}
