package solvela.member.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 会员认证契约。
 *
 * <h3>同一个接口，两种形态</h3>
 * <ul>
 *   <li><b>今天</b>：{@code solvela.member.auth.MemberAuthService} 实现它，网关直接注入，
 *       同 JVM 一次方法调用，{@code @HttpExchange} 这些注解运行期完全不起作用；</li>
 *   <li><b>拆成 app-member 服务后</b>：服务端套一个 {@code @RestController implements MemberAuthApi}
 *       的薄壳（Spring MVC 认得接口上的 {@code @HttpExchange}），网关侧把注入源换成
 *       {@code HttpServiceProxyFactory} 生成的代理 —— <b>调用方代码一行不改</b>。</li>
 * </ul>
 * 这就是本模块存在的全部理由。
 *
 * <h3>路径前缀 /internal 是有意的</h3>
 * 这些端点服务于服务间调用，<b>永远不该暴露到公网</b>：{@code verify} 收的是明文密码，
 * {@code identity} 用会员号直接换身份。将来网关/入口层要按前缀把 {@code /internal/**} 挡在外面。
 *
 * <h3>失败不抛异常</h3>
 * 认证失败是最常见的正常情况，用 {@link MemberAuthResult} 的 reason 表达。
 * 抛异常留给意外（库挂了、代码 bug）—— 那些跨进程后就是 5xx，本来也该是 5xx。
 */
@HttpExchange("/internal/member/auth")
public interface MemberAuthApi {

    /**
     * 手机号 + 密码认证。<b>只验身份，不发令牌。</b>
     *
     * <p>成功返回带 {@link MemberIdentity} 的结果；失败带 {@link AuthFailReason}，
     * 「告诉用户多少」由调用方决定（同一个 BAD_CREDENTIALS，C 端要含糊、客服后台要具体）。
     */
    @PostExchange("/verify")
    MemberAuthResult authenticate(@RequestBody MemberAuthCmd cmd);

    /**
     * 按会员号取<b>可用身份</b>；会员不存在或状态不正常返回 null。
     *
     * <p>网关每个请求都会（经缓存）走一次这里，把令牌解析出的会员号还原成身份。
     * 调用方务必带缓存 —— 这是全站最热的一次调用。
     */
    @GetExchange("/identity/{memberId}")
    MemberIdentity getAuthIdentity(@PathVariable Long memberId);

    /**
     * 记一次退出登录。吊销令牌是调用方的事，这里只留痕。
     */
    @PostExchange("/logout-log")
    void recordLogout(@RequestBody MemberLogoutCmd cmd);
}
